package com.micamara.app

import android.content.Context
import android.net.Uri

data class CameraConfig(
    val host: String = "",
    val port: String = "554",
    val user: String = "",
    val password: String = "",
    val path: String = "live/ch00_0",
) {
    fun isValid() = host.isNotBlank() && port.toIntOrNull() != null

    /** Construye la URL RTSP, codificando usuario y contraseña. */
    fun rtspUrl(): String {
        val auth = if (user.isNotBlank()) {
            Uri.encode(user) + (if (password.isNotEmpty()) ":" + Uri.encode(password) else "") + "@"
        } else ""
        return "rtsp://$auth${host.trim()}:${port.trim()}/${path.trim().trimStart('/')}"
    }
}

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("camara", Context.MODE_PRIVATE)

    fun load() = CameraConfig(
        host = prefs.getString("host", "") ?: "",
        port = prefs.getString("port", "554") ?: "554",
        user = prefs.getString("user", "") ?: "",
        password = prefs.getString("password", "") ?: "",
        path = prefs.getString("path", "live/ch00_0") ?: "live/ch00_0",
    )

    fun save(c: CameraConfig) {
        prefs.edit()
            .putString("host", c.host)
            .putString("port", c.port)
            .putString("user", c.user)
            .putString("password", c.password)
            .putString("path", c.path)
            .apply()
    }
}
