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
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

private const val NS_DEVICE = "http://www.onvif.org/ver10/device/wsdl"
private const val NS_MEDIA = "http://www.onvif.org/ver10/media/wsdl"
private const val NS_PTZ = "http://www.onvif.org/ver20/ptz/wsdl"
private const val NS_SCHEMA = "http://www.onvif.org/ver10/schema"

/** Prueba si un puerto TCP responde. */
internal fun tcpOpen(host: String, port: Int, timeoutMs: Int): Boolean =
    try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        true
    } catch (e: Exception) {
        false
    }

private fun xmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

private fun rawPost(url: String, xml: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 3000
        conn.readTimeout = 4000
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
 * Cliente ONVIF mínimo: movimiento PTZ continuo, parada y comandos auxiliares (luz).
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
    private var ptzUrl: String? = null
    private var profileToken: String? = null
    private var mediaEndpoint: String? = null

    fun close() {
        executor.shutdownNow()
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
        val uri = Regex("Uri>\\s*([^<\\s]+)\\s*<").find(r)?.groupValues?.get(1)
            ?.replace("&amp;", "&")
        if (uri == null) {
            null
        } else {
            val u = URL(uri.replaceFirst("rtsp://", "http://"))
            RtspStream(if (u.port > 0) u.port else 554, u.file.trimStart('/'))
        }
    }

    suspend fun move(pan: Float, tilt: Float, zoom: Float) {
        withContext(dispatcher) {
            ensureReady()
            call(
                ptzUrl!!,
                "<tptz:ContinuousMove xmlns:tptz=\"$NS_PTZ\" xmlns:tt=\"$NS_SCHEMA\">" +
                    "<tptz:ProfileToken>${profileToken}</tptz:ProfileToken>" +
                    "<tptz:Velocity><tt:PanTilt x=\"$pan\" y=\"$tilt\"/><tt:Zoom x=\"$zoom\"/></tptz:Velocity>" +
                    "</tptz:ContinuousMove>",
            )
        }
    }

    suspend fun stop() {
        withContext(dispatcher) {
            ensureReady()
            call(
                ptzUrl!!,
                "<tptz:Stop xmlns:tptz=\"$NS_PTZ\">" +
                    "<tptz:ProfileToken>${profileToken}</tptz:ProfileToken>" +
                    "<tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom></tptz:Stop>",
            )
        }
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

    private fun ensureReady() {
        if (ptzUrl != null && profileToken != null) return
        val base = "http://$host:$port"
        val device = "$base/onvif/device_service"
        var mediaUrl = "$base/onvif/media_service"
        var ptz = "$base/onvif/ptz_service"
        xaddr(device, "Media")?.let { mediaUrl = it }
        xaddr(device, "PTZ")?.let { ptz = it }

        val profiles = call(mediaUrl, "<trt:GetProfiles xmlns:trt=\"$NS_MEDIA\"/>")
        val token = Regex("Profiles[^>]*\\btoken=\"([^\"]+)\"").find(profiles)?.groupValues?.get(1)
            ?: throw IOException("la cámara no devolvió perfiles ONVIF")
        profileToken = token
        mediaEndpoint = mediaUrl
        ptzUrl = ptz
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

    /** Algunas cámaras devuelven su IP interna; usamos siempre la que escribió el usuario. */
    private fun rewriteHost(url: String): String {
        val u = URL(url)
        val p = if (u.port == -1) 80 else u.port
        return "http://$host:$p${u.path}"
    }

    private fun call(url: String, body: String): String =
        try {
            rawPost(url, envelope(body, digest = true))
        } catch (e: IOException) {
            rawPost(url, envelope(body, digest = false))
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
        val created = fmt.format(Date())
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
            val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body>" +
                "<tds:GetSystemDateAndTime xmlns:tds=\"$NS_DEVICE\"/></s:Body></s:Envelope>"
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
