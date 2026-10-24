package com.micamara.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}

private fun sizeText(bytes: Long): String =
    if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "${bytes / 1000} KB"

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var items by remember { mutableStateOf(Storage.list(context)) }
    var open by remember { mutableStateOf<Capture?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = open != null) { open = null }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            message = null
        }
    }

    val current = open
    if (current != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { open = null }) { Text("‹ Volver") }
                Spacer(Modifier.weight(1f))
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(current.time)),
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (current.isVideo) {
                    VideoPlayer(
                        source = Uri.fromFile(current.file),
                        modifier = Modifier.fillMaxSize(),
                        onError = { message = it },
                    )
                } else {
                    val bmp by produceState<Bitmap?>(null, current.file) {
                        value = withContext(Dispatchers.IO) { decodeSampled(current.file, 1600) }
                    }
                    val b = bmp
                    if (b != null) {
                        Image(
                            bitmap = b.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            val m = message
            if (m != null) Text(m, color = cs.primary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { message = Storage.exportToGallery(context, current.file) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Guardar en galería", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        current.file.delete()
                        items = Storage.list(context)
                        open = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Eliminar", fontSize = 13.sp) }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Volver") }
            Spacer(Modifier.weight(1f))
        }
        Text("Capturas", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
        Text(
            "Fotos y grabaciones hechas desde la app (${items.size}).",
            color = cs.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        if (items.isEmpty()) {
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🖼", fontSize = 48.sp)
                    Text("Todavía no hay capturas", fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text(
                        "En la vista en vivo usa 📷 Foto o ⏺ Grabar y aparecerán aquí.",
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(items, key = { it.file.absolutePath }) { c ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cs.surface,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { open = c },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (c.isVideo) {
                                    Text("🎬", fontSize = 26.sp)
                                } else {
                                    val thumb by produceState<Bitmap?>(null, c.file) {
                                        value = withContext(Dispatchers.IO) { decodeSampled(c.file, 160) }
                                    }
                                    val t = thumb
                                    if (t != null) {
                                        Image(
                                            bitmap = t.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (c.isVideo) "Grabación" else "Foto",
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface,
                                )
                                Text(
                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(c.time)) +
                                        "  ·  " + sizeText(c.file.length()),
                                    fontSize = 12.sp,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
