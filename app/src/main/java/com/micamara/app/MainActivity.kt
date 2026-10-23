package com.micamara.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { ConfigStore(context) }
    var config by remember { mutableStateOf(store.load()) }
    var watching by remember { mutableStateOf(false) }

    if (watching) {
        BackHandler { watching = false }
        LiveView(url = config.rtspUrl(), onClose = { watching = false })
    } else {
        SettingsForm(
            config = config,
            onChange = { config = it },
            onWatch = {
                store.save(config)
                watching = true
            },
        )
    }
}

@Composable
fun SettingsForm(
    config: CameraConfig,
    onChange: (CameraConfig) -> Unit,
    onWatch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mi Cámara", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Conexión directa por RTSP en tu red local. Sin anuncios, sin cuentas, sin nube.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = config.host,
            onValueChange = { onChange(config.copy(host = it)) },
            label = { Text("IP de la cámara (ej. 192.168.1.50)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = config.port,
            onValueChange = { onChange(config.copy(port = it)) },
            label = { Text("Puerto RTSP") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = config.user,
            onValueChange = { onChange(config.copy(user = it)) },
            label = { Text("Usuario (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = config.password,
            onValueChange = { onChange(config.copy(password = it)) },
            label = { Text("Contraseña (opcional)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = config.path,
            onValueChange = { onChange(config.copy(path = it)) },
            label = { Text("Ruta del stream (ej. live/ch00_0)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onWatch,
            enabled = config.isValid(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ver en vivo") }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LiveView(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val source = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true) // TCP: más fiable en WiFi doméstico
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(source)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "No se pudo conectar (${e.errorCodeName}). " +
                    "Revisa IP, puerto, credenciales y ruta del stream."
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Mantener la pantalla encendida mientras se ve la cámara
    val activity = context as? ComponentActivity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) { Text("Volver") }
    }
}
