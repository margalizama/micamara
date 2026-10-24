package com.micamara.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Capture(val file: File) {
    val isVideo: Boolean get() = file.extension.lowercase() in Storage.VIDEO_EXT
    val time: Long get() = file.lastModified()
}

/** Fotos y grabaciones hechas desde la app, guardadas en una carpeta propia. */
object Storage {
    val VIDEO_EXT = setOf("mp4", "ts", "mkv", "avi", "mov", "flv", "webm", "3gp")
    private val PHOTO_EXT = setOf("jpg", "jpeg", "png")

    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "capturas").also { it.mkdirs() }
    }

    fun photoFile(context: Context, camName: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = camName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(20).ifBlank { "cam" }
        return File(dir(context), "foto_${safe}_$stamp.jpg")
    }

    fun list(context: Context): List<Capture> =
        (dir(context).listFiles() ?: emptyArray())
            .filter { it.isFile && it.length() > 0 && it.extension.lowercase() in (VIDEO_EXT + PHOTO_EXT) }
            .sortedByDescending { it.lastModified() }
            .map { Capture(it) }

    /** Copia el archivo a la galería de Android (Android 10 o más nuevo). Devuelve un mensaje. */
    fun exportToGallery(context: Context, file: File): String {
        val ext = file.extension.lowercase()
        val video = ext in VIDEO_EXT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Archivo en: ${file.absolutePath}"
        val mime = when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "ts" -> "video/mp2t"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            else -> if (video) "video/mp4" else "image/jpeg"
        }
        val folder = (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/MiCamara"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return "No se pudo guardar en la galería"
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Guardado en la galería ($folder)"
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            "No se pudo guardar en la galería"
        }
    }
}
