package com.micamara.app

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(ms: Long?): String =
    if (ms == null) "?" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

private fun durationText(r: Recording): String {
    val s = r.startMs
    val e = r.endMs
    if (s == null || e == null || e <= s) return ""
    val min = (e - s) / 60000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

/** Muestra las grabaciones de la tarjeta SD que la cámara comparte por ONVIF. */
@Composable
fun SdScreen(camera: Camera, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val onvif = remember(camera.id, camera.host, camera.onvifPort) {
        OnvifClient(camera.host, camera.onvifPort, camera.user, camera.password)
    }
    DisposableEffect(onvif) { onDispose { onvif.close() } }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var selected by remember { mutableStateOf<Recording?>(null) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var playUrl by remember { mutableStateOf<String?>(null) }
    var startSec by remember { mutableStateOf(0) }
    var playError by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                items = onvif.listRecordings()
            } catch (e: Exception) {
                error = e.message ?: "error desconocido"
            }
            loading = false
        }
    }
    LaunchedEffect(onvif) { load() }

    BackHandler(enabled = playUrl != null || selected != null) {
        if (playUrl != null) playUrl = null else selected = null
    }

    val url = playUrl
    val sel = selected

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (url != null) playUrl = null else if (sel != null) selected = null else onBack()
            }) { Text("‹ Volver") }
            Spacer(Modifier.weight(1f))
        }

        if (url != null) {
            // ───── Reproduciendo una grabación ─────
            Text("Grabación de la SD", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
            val pe = playError
            if (pe != null) Text(pe, color = cs.error, fontSize = 13.sp)
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                VideoPlayer(
                    source = Uri.parse(url),
                    modifier = Modifier.fillMaxSize(),
                    options = listOf(":rtsp-tcp", ":network-caching=800"),
                    startSeconds = startSec,
                    onError = { playError = "$it Puede que la cámara no permita reproducir esta grabación." },
                )
            }
            Spacer(Modifier.height(12.dp))
        } else if (sel != null) {
            // ───── Elegir desde qué punto reproducir ─────
            Text("Reproducir grabación", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(22.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Desde: ${fmt(sel.startMs)}", color = cs.onSurface)
                    Text("Hasta: ${fmt(sel.endMs)}", color = cs.onSurface)
                    val s = sel.startMs
                    val e = sel.endMs
                    if (s != null && e != null && e > s) {
                        val durSec = ((e - s) / 1000).toInt()
                        val offset = (fraction * durSec).toInt()
                        Text("Empezar en: ${fmt(s + offset * 1000L)}", fontWeight = FontWeight.SemiBold, color = cs.primary)
                        Slider(value = fraction, onValueChange = { fraction = it })
                    }
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                playError = null
                                try {
                                    val st = onvif.replayUri(sel.token)
                                    val s2 = sel.startMs
                                    val e2 = sel.endMs
                                    startSec = if (s2 != null && e2 != null && e2 > s2) {
                                        (fraction * ((e2 - s2) / 1000)).toInt()
                                    } else 0
                                    playUrl = camera.rtspUrl(st.path, st.port)
                                } catch (ex: Exception) {
                                    playError = "No se pudo pedir la grabación: ${ex.message}"
                                }
                                busy = false
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (busy) "Pidiendo…" else "▶  Reproducir", fontWeight = FontWeight.Bold) }
                    val pe = playError
                    if (pe != null) Text(pe, color = cs.error, fontSize = 13.sp)
                }
            }
        } else {
            // ───── Lista de grabaciones ─────
            Text("Tarjeta SD", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
            Text(
                camera.name.ifBlank { camera.host },
                color = cs.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            when {
                loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("Consultando la cámara…", color = cs.onSurfaceVariant)
                }

                error != null -> Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cs.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No pude leer la SD", fontWeight = FontWeight.Bold, color = cs.onSurface)
                        Text(
                            "La cámara no entregó su lista de grabaciones por ONVIF (${error}). " +
                                "Muchas cámaras V380 solo comparten la SD con su app original.",
                            color = cs.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        OutlinedButton(onClick = { load() }, shape = RoundedCornerShape(14.dp)) { Text("Reintentar") }
                    }
                }

                items.isEmpty() -> Text(
                    "La cámara respondió, pero no informa grabaciones. Revisa que haya SD y que grabe.",
                    color = cs.onSurfaceVariant,
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(items, key = { it.token }) { r ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cs.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    fraction = 0f
                                    playError = null
                                    selected = r
                                },
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("🎞  ${fmt(r.startMs)}", fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                Text(
                                    "hasta ${fmt(r.endMs)}  ·  ${durationText(r)}",
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
