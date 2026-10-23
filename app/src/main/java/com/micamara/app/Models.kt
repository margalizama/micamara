package com.micamara.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Camera(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val rtspPort: Int = 554,
    val rtspPath: String = "live/ch00_0",
    val user: String = "",
    val password: String = "",
    val onvifPort: Int = 80,
    val lightOnCmd: String = "tt:IRLamp|On",
    val lightOffCmd: String = "tt:IRLamp|Off",
    val invertPan: Boolean = false,
    val invertTilt: Boolean = false,
) {
    /** URL RTSP con usuario y contraseña codificados. */
    fun rtspUrl(): String {
        val auth = if (user.isNotBlank()) {
            Uri.encode(user) + (if (password.isNotEmpty()) ":" + Uri.encode(password) else "") + "@"
        } else ""
        return "rtsp://$auth${host.trim()}:$rtspPort/${rtspPath.trim().trimStart('/')}"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("host", host)
        .put("rtspPort", rtspPort)
        .put("rtspPath", rtspPath)
        .put("user", user)
        .put("password", password)
        .put("onvifPort", onvifPort)
        .put("lightOnCmd", lightOnCmd)
        .put("lightOffCmd", lightOffCmd)
        .put("invertPan", invertPan)
        .put("invertTilt", invertTilt)

    companion object {
        fun fromJson(o: JSONObject) = Camera(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            host = o.optString("host"),
            rtspPort = o.optInt("rtspPort", 554),
            rtspPath = o.optString("rtspPath", "live/ch00_0"),
            user = o.optString("user"),
            password = o.optString("password"),
            onvifPort = o.optInt("onvifPort", 80),
            lightOnCmd = o.optString("lightOnCmd", "tt:IRLamp|On"),
            lightOffCmd = o.optString("lightOffCmd", "tt:IRLamp|Off"),
            invertPan = o.optBoolean("invertPan", false),
            invertTilt = o.optBoolean("invertTilt", false),
        )
    }
}

/** Guarda la lista de cámaras en el almacenamiento privado de la app. */
class CameraRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("camaras", Context.MODE_PRIVATE)

    fun load(): List<Camera> {
        val raw = prefs.getString("lista", null) ?: return migrateOld()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Camera.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Camera>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("lista", arr.toString()).apply()
    }

    /** Si venías de la versión 1 (una sola cámara), la conserva. */
    private fun migrateOld(): List<Camera> {
        val old = context.getSharedPreferences("camara", Context.MODE_PRIVATE)
        val host = old.getString("host", "") ?: ""
        if (host.isBlank()) return emptyList()
        val cam = Camera(
            name = "Mi cámara",
            host = host,
            rtspPort = old.getString("port", "554")?.toIntOrNull() ?: 554,
            rtspPath = old.getString("path", "live/ch00_0") ?: "live/ch00_0",
            user = old.getString("user", "") ?: "",
            password = old.getString("password", "") ?: "",
        )
        val list = listOf(cam)
        save(list)
        return list
    }
}
