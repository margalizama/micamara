package com.micamara.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

class VlcHandle(val lib: LibVLC, val player: MediaPlayer)

/** Crea un reproductor VLC y lo libera al salir de la pantalla. */
@Composable
fun rememberVlc(): VlcHandle {
    val context = LocalContext.current
    val handle = remember {
        val lib = LibVLC(context.applicationContext, ArrayList<String>())
        VlcHandle(lib, MediaPlayer(lib))
    }
    DisposableEffect(handle) {
        onDispose {
            try {
                handle.player.setEventListener(null)
                handle.player.stop()
                handle.player.detachViews()
                handle.player.release()
                handle.lib.release()
            } catch (e: Exception) {
                // ya estaba liberado
            }
        }
    }
    return handle
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Reproductor con barra de avance, para archivos locales o grabaciones de la SD. */
@Composable
fun VideoPlayer(
    source: Uri,
    modifier: Modifier = Modifier,
    options: List<String> = emptyList(),
    startSeconds: Int = 0,
    onError: (String) -> Unit = {},
) {
    val vlc = rememberVlc()
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var length by remember { mutableLongStateOf(0L) }
    var time by remember { mutableLongStateOf(0L) }

    DisposableEffect(vlc.player) {
        vlc.player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> playing = true
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> playing = false
                MediaPlayer.Event.EndReached -> {
                    playing = false
                    pos = 0f
                }

                MediaPlayer.Event.EncounteredError -> onError("No se pudo reproducir el video.")
                MediaPlayer.Event.LengthChanged -> length = e.lengthChanged
                MediaPlayer.Event.TimeChanged -> time = e.timeChanged
                MediaPlayer.Event.PositionChanged -> if (!dragging) pos = e.positionChanged
                else -> {}
            }
        }
        onDispose { }
    }

    LaunchedEffect(source) {
        val media = Media(vlc.lib, source)
        options.forEach { media.addOption(it) }
        if (startSeconds > 0) media.addOption(":start-time=$startSeconds")
        vlc.player.media = media
        media.release()
        vlc.player.play()
    }

    Column(modifier) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout -> vlc.player.attachViews(layout, null, false, false) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                if (vlc.player.isPlaying) vlc.player.pause() else vlc.player.play()
            }) { Text(if (playing) "⏸" else "▶") }
            Slider(
                value = pos,
                onValueChange = {
                    dragging = true
                    pos = it
                },
                onValueChangeFinished = {
                    vlc.player.position = pos
                    dragging = false
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                if (length > 0) "${clock(time)} / ${clock(length)}" else clock(time),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
