package com.micamara.app

import android.util.Base64
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

data class RtspStream(val port: Int, val path: String)

sealed interface ProbeResult {
    data class Found(val stream: RtspStream) : ProbeResult
    data object BadCredentials : ProbeResult
    data class NotFound(val portOpen: Boolean) : ProbeResult
}

/**
 * Detecta el canal (ruta) RTSP de una cámara probando las rutas más comunes
 * con una petición DESCRIBE, con autenticación Digest o Basic.
 * Es bloqueante: llamar desde un hilo de fondo.
 */
object RtspProbe {

    private val PORTS = listOf(554, 8554, 10554, 5554)

    private val PATHS = listOf(
        "live/ch00_0", "live/ch00_1", "live/ch0", "live/ch1", "live/ch01_0",
        "stream1", "stream0", "stream2", "onvif1", "onvif2",
        "11", "12", "1", "0", "h264", "live", "av0_0", "tcp/av0_0",
        "ch0_0.h264", "ch1.h264", "h264_stream", "video1", "videoMain", "mpeg4",
        "Streaming/Channels/101", "Streaming/Channels/102",
        "h264/ch1/main/av_stream", "h264/ch1/sub/av_stream",
        "cam/realmonitor?channel=1&subtype=0", "cam/realmonitor?channel=1&subtype=1",
        "user=admin_password=_channel=1_stream=0.sdp?real_stream",
        "user=admin&password=&channel=1&stream=0.sdp?real_stream",
        "MediaInput/h264", "media/video1", "live/main", "live/sub", "stream",
        "",
    )

    fun find(
        host: String,
        user: String,
        pass: String,
        preferredPort: Int,
        preferredPath: String,
        onTry: (String) -> Unit = {},
    ): ProbeResult {
        val ports = (listOf(preferredPort) + PORTS).distinct().filter { tcpOpen(host, it, 600) }
        if (ports.isEmpty()) return ProbeResult.NotFound(portOpen = false)

        val paths = (listOf(preferredPath.trim().trimStart('/')) + PATHS).distinct()
        for (port in ports) {
            for (path in paths) {
                onTry("rtsp://$host:$port/$path")
                when (describe(host, port, path, user, pass)) {
                    200 -> return ProbeResult.Found(RtspStream(port, path))
                    401 -> return ProbeResult.BadCredentials
                    else -> {}
                }
            }
        }
        return ProbeResult.NotFound(portOpen = true)
    }

    private class Resp(val code: Int, val challenges: List<String>)

    private fun describe(host: String, port: Int, path: String, user: String, pass: String): Int {
        val uri = "rtsp://$host:$port/$path"
        val first = exchange(host, port, uri, null)
        if (first.code != 401) return first.code
        if (user.isBlank()) return 401
        val auth = authHeader(first.challenges, user, pass, uri) ?: return 401
        return exchange(host, port, uri, auth).code
    }

    private fun exchange(host: String, port: Int, uri: String, auth: String?): Resp {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, port), 2000)
            socket.soTimeout = 3000
            val req = StringBuilder()
                .append("DESCRIBE ").append(uri).append(" RTSP/1.0\r\n")
                .append("CSeq: 1\r\n")
                .append("Accept: application/sdp\r\n")
                .append("User-Agent: MiCamara\r\n")
            if (auth != null) req.append("Authorization: ").append(auth).append("\r\n")
            req.append("\r\n")
            socket.getOutputStream().write(req.toString().toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            readResponse(socket.getInputStream())
        } catch (e: Exception) {
            Resp(-1, emptyList())
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignorar
            }
        }
    }

    private fun readResponse(input: InputStream): Resp {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) break
            sb.append(b.toChar())
            if (sb.length > 8192) break
        }
        val lines = sb.toString().split("\r\n")
        val code = Regex("RTSP/\\d\\.\\d\\s+(\\d{3})").find(lines.firstOrNull() ?: "")
            ?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val challenges = lines.drop(1)
            .filter { it.startsWith("WWW-Authenticate", ignoreCase = true) }
            .map { it.substringAfter(":").trim() }
        return Resp(code, challenges)
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun authHeader(challenges: List<String>, user: String, pass: String, uri: String): String? {
        val digest = challenges.firstOrNull { it.startsWith("Digest", ignoreCase = true) }
        if (digest != null) {
            fun param(key: String): String? =
                Regex("$key=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(digest)?.groupValues?.get(1)

            val realm = param("realm") ?: return null
            val nonce = param("nonce") ?: return null
            val qop = param("qop")
            val ha1 = md5("$user:$realm:$pass")
            val ha2 = md5("DESCRIBE:$uri")
            return if (qop != null && qop.contains("auth")) {
                val nc = "00000001"
                val cnonce = "6d6963616d617261"
                val response = md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
                "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", " +
                    "response=\"$response\", qop=auth, nc=$nc, cnonce=\"$cnonce\""
            } else {
                val response = md5("$ha1:$nonce:$ha2")
                "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$response\""
            }
        }
        if (challenges.any { it.startsWith("Basic", ignoreCase = true) }) {
            val token = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "Basic $token"
        }
        return null
    }
}
