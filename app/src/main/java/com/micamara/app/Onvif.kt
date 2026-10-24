package com.micamara.app

import android.util.Base64
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

private const val NS_DEVICE = "http://www.onvif.org/ver10/device/wsdl"
private const val NS_MEDIA = "http://www.onvif.org/ver10/media/wsdl"
private const val NS_PTZ = "http://www.onvif.org/ver20/ptz/wsdl"
private const val NS_SCHEMA = "http://www.onvif.org/ver10/schema"
private const val NS_SEARCH = "http://www.onvif.org/ver10/search/wsdl"
private const val NS_REPLAY = "http://www.onvif.org/ver10/replay/wsdl"

/** Una grabación de la tarjeta SD, tal como la informa la cámara. */
data class Recording(val token: String, val startMs: Long?, val endMs: Long?)

/** Prueba si un puerto TCP responde. */
internal fun tcpOpen(host: String, port: Int, timeoutMs: Int): Boolean =
    try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        true
    } catch (e: Exception) {
        false
    }

internal fun parseIso(s: String?): Long? {
    if (s == null) return null
    return try {
        java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            java.time.Instant.parse(s).toEpochMilli()
        } catch (e2: Exception) {
            null
        }
    }
}

private fun xmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

private fun bareEnvelope(body: String): String =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">" +
        "<s:Body>" + body + "</s:Body></s:Envelope>"

private fun rawPost(url: String, xml: String, readTimeoutMs: Int = 4000): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 3000
        conn.readTimeout = readTimeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
        conn.outputStream.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IOException("HTTP $code")
        return text
    } finally {
        conn.disconnect()
    }
}

/**
 * Cliente ONVIF mínimo: PTZ, luz (comandos auxiliares), URL del stream y grabaciones de la SD.
 * Todas las llamadas se ejecutan en orden, en un solo hilo.
 */
class OnvifClient(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val pass: String,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private var deviceUrl: String = "http://$host:$port/onvif/device_service"
    private var ptzUrl: String? = null
    private var profileToken: String? = null
    private var mediaEndpoint: String? = null
    private var capsXml: String? = null

    /** null = aún no se sabe qué tipo de contraseña acepta la cámara. */
    @Volatile private var digestOk: Boolean? = null

    /** Diferencia entre el reloj de la cámara y el del teléfono (para la firma digest). */
    @Volatile private var timeOffsetMs = 0L

    @Volatile private var timeoutSupported = true

    fun close() {
        executor.shutdownNow()
    }

    /** Cierra mandando antes una orden de parar, para no dejar la cámara en movimiento. */
    fun closeAfterStop() {
        try {
            executor.execute {
                try {
                    if (ptzUrl != null) stopInternal()
                } catch (e: Exception) {
                    // nada más que hacer
                }
            }
        } catch (e: RejectedExecutionException) {
            // ya estaba cerrado
        }
        executor.shutdown()
    }

    /** Descubre los servicios y el perfil de la cámara. Lanza IOException si falla. */
    suspend fun prepare() {
        withContext(dispatcher) { ensureReady() }
    }

    /** Pregunta a la cámara cuál es su URL RTSP (puerto y canal). Devuelve null si no la informa. */
    suspend fun streamUri(): RtspStream? = withContext(dispatcher) {
        ensureReady()
        val r = call(
            mediaEndpoint!!,
            "<trt:GetStreamUri xmlns:trt=\"$NS_MEDIA\" xmlns:tt=\"$NS_SCHEMA\">" +
                "<trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream>" +
                "<tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trt:StreamSetup>" +
                "<trt:ProfileToken>${profileToken}</trt:ProfileToken></trt:GetStreamUri>",
        )
        parseRtspUri(r)
    }

    /**
     * Movimiento continuo. Lleva un tiempo máximo (Timeout) para que la cámara se detenga sola
     * aunque se pierda la orden de parar.
     */
    suspend fun move(pan: Float, tilt: Float, zoom: Float, timeoutMs: Int = 1000) {
        withContext(dispatcher) {
            ensureReady()
            if (timeoutSupported) {
                try {
                    call(ptzUrl!!, moveBody(pan, tilt, zoom, timeoutMs))
                    return@withContext
                } catch (e: IOException) {
                    timeoutSupported = false // algunas cámaras no aceptan Timeout
                }
            }
            call(ptzUrl!!, moveBody(pan, tilt, zoom, null))
        }
    }

    suspend fun stop() {
        withContext(dispatcher) { stopInternal() }
    }

    /** Envía un comando auxiliar ONVIF, por ejemplo "tt:IRLamp|On". */
    suspend fun aux(command: String) {
        withContext(dispatcher) {
            ensureReady()
            call(
                ptzUrl!!,
                "<tptz:SendAuxiliaryCommand xmlns:tptz=\"$NS_PTZ\">" +
                    "<tptz:ProfileToken>${profileToken}</tptz:ProfileToken>" +
                    "<tptz:AuxiliaryData>${xmlEscape(command)}</tptz:AuxiliaryData>" +
                    "</tptz:SendAuxiliaryCommand>",
            )
        }
    }

    /** Lista las grabaciones de la SD (ONVIF Profile G). */
    suspend fun listRecordings(): List<Recording> = withContext(dispatcher) {
        ensureReady()
        val search = serviceUrl("Search")
            ?: throw IOException("la cámara no ofrece búsqueda de grabaciones por ONVIF")
        val find = call(
            search,
            "<tse:FindRecordings xmlns:tse=\"$NS_SEARCH\"><tse:Scope/>" +
                "<tse:KeepAliveTime>PT30S</tse:KeepAliveTime></tse:FindRecordings>",
        )
        val token = Regex("SearchToken>\\s*([^<\\s]+)\\s*<").find(find)?.groupValues?.get(1)
            ?: throw IOException("la cámara no inició la búsqueda")

        val out = LinkedHashMap<String, Recording>()
        val blockRe = Regex(
            "<(?:\\w+:)?RecordingInformation>(.*?)</(?:\\w+:)?RecordingInformation>",
            RegexOption.DOT_MATCHES_ALL,
        )
        for (i in 0 until 6) {
            val r = call(
                search,
                "<tse:GetRecordingSearchResults xmlns:tse=\"$NS_SEARCH\">" +
                    "<tse:SearchToken>$token</tse:SearchToken>" +
                    "<tse:MinResults>1</tse:MinResults><tse:MaxResults>200</tse:MaxResults>" +
                    "<tse:WaitTime>PT2S</tse:WaitTime></tse:GetRecordingSearchResults>",
            )
            for (m in blockRe.findAll(r)) {
                val b = m.groupValues[1]
                val tok = Regex("RecordingToken>\\s*([^<]+?)\\s*<").find(b)?.groupValues?.get(1) ?: continue
                val s = Regex("EarliestRecording>\\s*([^<]+?)\\s*<").find(b)?.groupValues?.get(1)
                val e = Regex("LatestRecording>\\s*([^<]+?)\\s*<").find(b)?.groupValues?.get(1)
                out[tok] = Recording(tok, parseIso(s), parseIso(e))
            }
            if (Regex("SearchState>\\s*Completed").containsMatchIn(r)) break
        }
        try {
            call(
                search,
                "<tse:EndSearch xmlns:tse=\"$NS_SEARCH\"><tse:SearchToken>$token</tse:SearchToken></tse:EndSearch>",
            )
        } catch (e: IOException) {
            // no es importante
        }
        out.values.sortedByDescending { it.startMs ?: 0L }
    }

    /** Pide la URL RTSP para reproducir una grabación de la SD. */
    suspend fun replayUri(recordingToken: String): RtspStream = withContext(dispatcher) {
        ensureReady()
        val replay = serviceUrl("Replay")
            ?: throw IOException("la cámara no ofrece reproducción de grabaciones por ONVIF")
        val r = call(
            replay,
            "<trp:GetReplayUri xmlns:trp=\"$NS_REPLAY\" xmlns:tt=\"$NS_SCHEMA\">" +
                "<trp:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream>" +
                "<tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trp:StreamSetup>" +
                "<trp:RecordingToken>${xmlEscape(recordingToken)}</trp:RecordingToken></trp:GetReplayUri>",
        )
        parseRtspUri(r) ?: throw IOException("la cámara no devolvió la dirección de reproducción")
    }

    // ───────────── internos ─────────────

    private fun parseRtspUri(r: String): RtspStream? {
        val uri = Regex("Uri>\\s*([^<\\s]+)\\s*<").find(r)?.groupValues?.get(1)
            ?.replace("&amp;", "&") ?: return null
        val u = URL(uri.replaceFirst("rtsp://", "http://"))
        return RtspStream(if (u.port > 0) u.port else 554, u.file.trimStart('/'))
    }

    private fun moveBody(pan: Float, tilt: Float, zoom: Float, timeoutMs: Int?): String {
        val timeout = if (timeoutMs != null) {
            "<tptz:Timeout>" + String.format(Locale.US, "PT%.1fS", timeoutMs / 1000.0) + "</tptz:Timeout>"
        } else ""
        return "<tptz:ContinuousMove xmlns:tptz=\"$NS_PTZ\" xmlns:tt=\"$NS_SCHEMA\">" +
            "<tptz:ProfileToken>${profileToken}</tptz:ProfileToken>" +
            "<tptz:Velocity><tt:PanTilt x=\"$pan\" y=\"$tilt\"/><tt:Zoom x=\"$zoom\"/></tptz:Velocity>" +
            timeout + "</tptz:ContinuousMove>"
    }

    /** Para el movimiento con la orden Stop y, por si la cámara la ignora, con velocidad cero. */
    private fun stopInternal() {
        ensureReady()
        var ok = false
        try {
            call(
                ptzUrl!!,
                "<tptz:Stop xmlns:tptz=\"$NS_PTZ\">" +
                    "<tptz:ProfileToken>${profileToken}</tptz:ProfileToken>" +
                    "<tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom></tptz:Stop>",
            )
            ok = true
        } catch (e: IOException) {
            // se intenta el refuerzo
        }
        try {
            call(ptzUrl!!, moveBody(0f, 0f, 0f, null))
            ok = true
        } catch (e: IOException) {
            // idem
        }
        if (!ok) throw IOException("no se pudo detener la cámara")
    }

    private fun ensureReady() {
        if (ptzUrl != null && profileToken != null) return
        val base = "http://$host:$port"
        deviceUrl = "$base/onvif/device_service"
        syncTime(deviceUrl)
        var mediaUrl = "$base/onvif/media_service"
        var ptz = "$base/onvif/ptz_service"
        xaddr(deviceUrl, "Media")?.let { mediaUrl = it }
        xaddr(deviceUrl, "PTZ")?.let { ptz = it }

        val profiles = call(mediaUrl, "<trt:GetProfiles xmlns:trt=\"$NS_MEDIA\"/>")
        val token = Regex("Profiles[^>]*\\btoken=\"([^\"]+)\"").find(profiles)?.groupValues?.get(1)
            ?: throw IOException("la cámara no devolvió perfiles ONVIF")
        profileToken = token
        mediaEndpoint = mediaUrl
        ptzUrl = ptz
    }

    /** Lee la hora de la cámara (no pide contraseña) para firmar bien las peticiones. */
    private fun syncTime(deviceUrl: String) {
        try {
            val r = rawPost(deviceUrl, bareEnvelope("<tds:GetSystemDateAndTime xmlns:tds=\"$NS_DEVICE\"/>"))
            val utc = Regex("UTCDateTime.*?</[^>]*UTCDateTime>", RegexOption.DOT_MATCHES_ALL)
                .find(r)?.value ?: return
            fun n(tag: String): Int? =
                Regex("<(?:\\w+:)?$tag>\\s*(\\d+)\\s*<").find(utc)?.groupValues?.get(1)?.toIntOrNull()
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(
                n("Year") ?: return,
                (n("Month") ?: return) - 1,
                n("Day") ?: return,
                n("Hour") ?: return,
                n("Minute") ?: return,
                n("Second") ?: return,
            )
            timeOffsetMs = cal.timeInMillis - System.currentTimeMillis()
        } catch (e: Exception) {
            // si falla, se usa la hora del teléfono
        }
    }

    private fun xaddr(deviceUrl: String, category: String): String? =
        try {
            val r = call(
                deviceUrl,
                "<tds:GetCapabilities xmlns:tds=\"$NS_DEVICE\"><tds:Category>$category</tds:Category></tds:GetCapabilities>",
            )
            Regex("XAddr[^>]*>\\s*([^<\\s]+)\\s*<").find(r)?.groupValues?.get(1)?.let { rewriteHost(it) }
        } catch (e: IOException) {
            null
        }

    private fun caps(): String {
        capsXml?.let { return it }
        val r = call(
            deviceUrl,
            "<tds:GetCapabilities xmlns:tds=\"$NS_DEVICE\"><tds:Category>All</tds:Category></tds:GetCapabilities>",
        )
        capsXml = r
        return r
    }

    private fun serviceUrl(name: String): String? =
        Regex("<(?:\\w+:)?$name>\\s*<(?:\\w+:)?XAddr>\\s*([^<\\s]+)\\s*<")
            .find(caps())?.groupValues?.get(1)?.let { rewriteHost(it) }

    /** Algunas cámaras devuelven su IP interna; usamos siempre la que escribió el usuario. */
    private fun rewriteHost(url: String): String {
        val u = URL(url)
        val p = if (u.port == -1) 80 else u.port
        return "http://$host:$p${u.path}"
    }

    /** Llama con el tipo de contraseña que ya funcionó; si falla prueba el otro y lo recuerda. */
    private fun call(url: String, body: String): String {
        val first = digestOk ?: true
        return try {
            val r = rawPost(url, envelope(body, first))
            if (digestOk == null) digestOk = first
            r
        } catch (e: IOException) {
            if (user.isBlank()) throw e
            val r = rawPost(url, envelope(body, !first))
            digestOk = !first
            r
        }
    }

    private fun envelope(body: String, digest: Boolean): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">" +
            security(digest) +
            "<s:Body>" + body + "</s:Body></s:Envelope>"

    /** Cabecera WS-Security (UsernameToken con digest SHA-1, o contraseña en texto). */
    private fun security(digest: Boolean): String {
        if (user.isBlank()) return ""
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val created = fmt.format(Date(System.currentTimeMillis() + timeOffsetMs))
        val u = xmlEscape(user)
        val wsse = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
        val wsu = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
        val profile = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0"
        return if (digest) {
            val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val sha = MessageDigest.getInstance("SHA-1")
            sha.update(nonce)
            sha.update(created.toByteArray(Charsets.UTF_8))
            sha.update(pass.toByteArray(Charsets.UTF_8))
            val d = Base64.encodeToString(sha.digest(), Base64.NO_WRAP)
            val n = Base64.encodeToString(nonce, Base64.NO_WRAP)
            "<s:Header><Security s:mustUnderstand=\"1\" xmlns=\"$wsse\"><UsernameToken>" +
                "<Username>$u</Username>" +
                "<Password Type=\"$profile#PasswordDigest\">$d</Password>" +
                "<Nonce>$n</Nonce>" +
                "<Created xmlns=\"$wsu\">$created</Created>" +
                "</UsernameToken></Security></s:Header>"
        } else {
            "<s:Header><Security s:mustUnderstand=\"1\" xmlns=\"$wsse\"><UsernameToken>" +
                "<Username>$u</Username>" +
                "<Password Type=\"$profile#PasswordText\">${xmlEscape(pass)}</Password>" +
                "</UsernameToken></Security></s:Header>"
        }
    }

    companion object {
        private val CANDIDATE_PORTS = listOf(80, 8899, 8080, 5000, 2020, 8000, 8090)

        /** Busca en qué puerto responde ONVIF una cámara (no requiere contraseña). */
        fun probePort(host: String): Int? {
            val xml = bareEnvelope("<tds:GetSystemDateAndTime xmlns:tds=\"$NS_DEVICE\"/>")
            for (p in CANDIDATE_PORTS) {
                if (!tcpOpen(host, p, 400)) continue
                try {
                    val r = rawPost("http://$host:$p/onvif/device_service", xml)
                    if (r.contains("SystemDateAndTime")) return p
                } catch (e: IOException) {
                    // siguiente puerto
                }
            }
            return null
        }
    }
}
