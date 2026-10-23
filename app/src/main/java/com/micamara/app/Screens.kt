package com.micamara.app

import android.app.Activity
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ───────────────────────── Inicio: lista de cámaras ─────────────────────────

@Composable
fun HomeScreen(
    cameras: List<Camera>,
    onOpen: (Camera) -> Unit,
    onEdit: (Camera) -> Unit,
    onAdd: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(28.dp))
            Text("Mi Cámara", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
            Text(
                if (cameras.isEmpty()) "Sin anuncios · sin cuentas" else "${cameras.size} cámara(s) · sin anuncios",
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            if (cameras.isEmpty()) {
                EmptyState(onAdd)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    items(cameras, key = { it.id }) { cam -> CameraCard(cam, onOpen, onEdit) }
                }
            }
        }
        if (cameras.isNotEmpty()) {
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
                    .fillMaxWidth()
                    .height(54.dp),
            ) { Text("＋  Agregar cámara", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("📡", fontSize = 56.sp)
            Text("Aún no hay cámaras", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Text(
                "Conecta el teléfono a la misma WiFi que tu cámara y búscala automáticamente.",
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Buscar mi cámara", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CameraCard(cam: Camera, onOpen: (Camera) -> Unit, onEdit: (Camera) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = cs.surface,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable { onOpen(cam) },
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Text("📹", fontSize = 26.sp) }
            Column(Modifier.weight(1f)) {
                Text(
                    cam.name.ifBlank { cam.host },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
                Text("${cam.host}  ·  ONVIF ${cam.onvifPort}", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
            TextButton(onClick = { onEdit(cam) }) { Text("Editar") }
        }
    }
}

// ───────────────────────── Agregar / editar cámara ─────────────────────────

@Composable
fun EditScreen(
    camera: Camera?,
    onSave: (Camera) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(camera?.name ?: "") }
    var host by remember { mutableStateOf(camera?.host ?: "") }
    var user by remember { mutableStateOf(camera?.user ?: "") }
    var pass by remember { mutableStateOf(camera?.password ?: "") }
    var rtspPort by remember { mutableStateOf((camera?.rtspPort ?: 554).toString()) }
    var path by remember { mutableStateOf(camera?.rtspPath ?: "live/ch00_0") }
    var onvifPort by remember { mutableStateOf((camera?.onvifPort ?: 80).toString()) }
    var lightOn by remember { mutableStateOf(camera?.lightOnCmd ?: "tt:IRLamp|On") }
    var lightOff by remember { mutableStateOf(camera?.lightOffCmd ?: "tt:IRLamp|Off") }
    var invertPan by remember { mutableStateOf(camera?.invertPan ?: false) }
    var invertTilt by remember { mutableStateOf(camera?.invertTilt ?: false) }
    var advanced by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }
    var probeTry by remember { mutableStateOf("") }
    var probeMsg by remember { mutableStateOf<String?>(null) }

    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FoundCamera>>(emptyList()) }

    fun pick(f: FoundCamera) {
        host = f.host
        f.onvifPort?.let { onvifPort = it.toString() }
    }

    fun startScan() {
        if (scanning) return
        scope.launch {
            scanning = true
            results = try {
                Discovery.scan(context)
            } catch (e: Exception) {
                emptyList()
            }
            scanning = false
            scanned = true
            if (results.size == 1 && host.isBlank()) pick(results[0])
        }
    }

    fun detectChannel() {
        if (probing || host.isBlank()) return
        scope.launch {
            probing = true
            probeMsg = null
            val res = withContext(Dispatchers.IO) {
                RtspProbe.find(host.trim(), user.trim(), pass, rtspPort.toIntOrNull() ?: 554, path) {
                    probeTry = it
                }
            }
            when (res) {
                is ProbeResult.Found -> {
                    path = res.stream.path
                    rtspPort = res.stream.port.toString()
                    probeMsg = "✓ Canal encontrado: /${res.stream.path} (puerto ${res.stream.port})"
                }

                ProbeResult.BadCredentials ->
                    probeMsg = "La cámara pide usuario y contraseña, o no son correctos."

                is ProbeResult.NotFound ->
                    probeMsg = if (res.portOpen) {
                        "No encontré el canal de video. Revisa que RTSP esté activo en la cámara."
                    } else {
                        "La cámara no responde por RTSP. Revisa la IP y que RTSP esté activo."
                    }
            }
            probing = false
        }
    }

    LaunchedEffect(Unit) { if (camera == null) startScan() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Volver") }
            Spacer(Modifier.weight(1f))
        }
        Text(
            if (camera == null) "Nueva cámara" else "Editar cámara",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground,
        )

        // Búsqueda automática
        Surface(shape = RoundedCornerShape(22.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "📡  Buscar en mi red",
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (scanning) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { startScan() }) { Text(if (scanned) "Repetir" else "Buscar") }
                    }
                }
                if (scanning) {
                    Text("Buscando cámaras… tarda unos segundos.", color = cs.onSurfaceVariant, fontSize = 13.sp)
                } else if (scanned && results.isEmpty()) {
                    Text(
                        "No encontré cámaras. Revisa que el teléfono y la cámara estén en la misma WiFi " +
                            "o escribe la IP a mano.",
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                results.forEach { f ->
                    val selected = f.host == host.trim()
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(if (selected) cs.primary.copy(alpha = 0.18f) else cs.surfaceVariant)
                            .clickable { pick(f) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📹", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.host, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                            Text(
                                listOfNotNull(
                                    if (f.rtsp) "RTSP 554" else null,
                                    f.onvifPort?.let { "ONVIF $it" },
                                ).joinToString("  ·  "),
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        if (selected) Text("✓", color = cs.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Field(name, { name = it }, "Nombre (ej. Sala)")
        Field(host, { host = it }, "IP de la cámara")
        Field(user, { user = it }, "Usuario")
        Field(pass, { pass = it }, "Contraseña", secret = true)

        OutlinedButton(
            onClick = { detectChannel() },
            enabled = host.isNotBlank() && !probing,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(if (probing) "Buscando canal…" else "🔎  Detectar canal del video") }
        if (probing && probeTry.isNotBlank()) {
            Text(probeTry, fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
        val pm = probeMsg
        if (pm != null) {
            Text(pm, fontSize = 13.sp, color = if (pm.startsWith("✓")) cs.primary else cs.error)
        }

        TextButton(onClick = { advanced = !advanced }) {
            Text(if (advanced) "Ocultar ajustes avanzados" else "Ajustes avanzados")
        }
        if (advanced) {
            Field(rtspPort, { rtspPort = it }, "Puerto RTSP", KeyboardType.Number)
            Field(path, { path = it }, "Ruta del stream (ej. live/ch00_0)")
            Field(onvifPort, { onvifPort = it }, "Puerto ONVIF (para PTZ y luz)", KeyboardType.Number)
            Field(lightOn, { lightOn = it }, "Comando para encender la luz")
            Field(lightOff, { lightOff = it }, "Comando para apagar la luz")
            SwitchRow("Invertir giro izquierda/derecha", invertPan) { invertPan = it }
            SwitchRow("Invertir inclinación arriba/abajo", invertTilt) { invertTilt = it }
        }

        Button(
            enabled = host.isNotBlank(),
            onClick = {
                val base = camera ?: Camera()
                onSave(
                    base.copy(
                        name = name.ifBlank { "Cámara ${host.trim()}" },
                        host = host.trim(),
                        user = user.trim(),
                        password = pass,
                        rtspPort = rtspPort.toIntOrNull() ?: 554,
                        rtspPath = path.trim(),
                        onvifPort = onvifPort.toIntOrNull() ?: 80,
                        lightOnCmd = lightOn.trim(),
                        lightOffCmd = lightOff.trim(),
                        invertPan = invertPan,
                        invertTilt = invertTilt,
                    ),
                )
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Guardar", fontWeight = FontWeight.Bold) }

        if (onDelete != null) {
            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Eliminar cámara") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ───────────────────────── Vista en vivo + PTZ ─────────────────────────

@OptIn(UnstableApi::class)
@Composable
fun LiveScreen(camera: Camera, onBack: () -> Unit, onCameraUpdated: (Camera) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playerState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var ptzOk by remember { mutableStateOf<Boolean?>(null) }
    var ptzStatus by remember { mutableStateOf("Conectando PTZ…") }
    var speed by remember { mutableFloatStateOf(0.5f) }
    var lightIsOn by remember { mutableStateOf(false) }
    var lightError by remember { mutableStateOf<String?>(null) }
    var rawError by remember { mutableStateOf("") }
    var detecting by remember { mutableStateOf(false) }
    var detectNote by remember { mutableStateOf("") }
    var autoTried by remember(camera.id) { mutableStateOf(false) }

    val exo = remember(camera.rtspUrl()) {
        ExoPlayer.Builder(context).build().apply {
            val source = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true) // TCP: más estable en WiFi doméstica
                .createMediaSource(MediaItem.fromUri(camera.rtspUrl()))
            setMediaSource(source)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exo) {
        videoError = null
        playerState = Player.STATE_IDLE
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playerState = state
                if (state == Player.STATE_READY) videoError = null
            }

            override fun onPlayerError(error: PlaybackException) {
                rawError = "${error.errorCodeName}: ${error.cause?.message ?: error.message ?: ""}"
                videoError = "No se pudo abrir el video. Revisa la IP, usuario/contraseña y el canal."
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Si el video falla, se busca solo el canal correcto (una vez por pantalla)
    val hasError = videoError != null
    LaunchedEffect(hasError) {
        if (hasError && !autoTried) {
            autoTried = true
            detecting = true
            val res = withContext(Dispatchers.IO) {
                RtspProbe.find(camera.host, camera.user, camera.password, camera.rtspPort, camera.rtspPath) {
                    detectNote = it
                }
            }
            detecting = false
            when (res) {
                is ProbeResult.Found ->
                    if (res.stream.path != camera.rtspPath || res.stream.port != camera.rtspPort) {
                        onCameraUpdated(camera.copy(rtspPath = res.stream.path, rtspPort = res.stream.port))
                    } else {
                        videoError = "La cámara responde en /${camera.rtspPath}, pero el reproductor no pudo " +
                            "abrir el video.\nDetalle: $rawError"
                    }

                ProbeResult.BadCredentials ->
                    videoError = "La cámara pide usuario y contraseña, o no son correctos. Revísalos en Editar."

                is ProbeResult.NotFound ->
                    videoError = "No encontré el canal de video. Verifica la IP y que RTSP esté activo en la cámara."
            }
        }
    }

    val onvif = remember(camera.id) {
        OnvifClient(camera.host, camera.onvifPort, camera.user, camera.password)
    }
    DisposableEffect(onvif) { onDispose { onvif.close() } }

    LaunchedEffect(onvif) {
        try {
            onvif.prepare()
            ptzOk = true
            ptzStatus = "PTZ listo"
        } catch (e: Exception) {
            ptzOk = false
            ptzStatus = "PTZ no disponible (${e.message}). Revisa el puerto ONVIF " +
                "(${camera.onvifPort}) y la contraseña en Editar > Ajustes avanzados."
        }
    }

    // Pantalla encendida mientras se ve la cámara
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    fun send(pan: Float, tilt: Float, zoom: Float) {
        val p = if (camera.invertPan) -pan else pan
        val t = if (camera.invertTilt) -tilt else tilt
        scope.launch {
            try {
                onvif.move(p, t, zoom)
            } catch (e: Exception) {
                ptzOk = false
                ptzStatus = "Error PTZ: ${e.message}"
            }
        }
    }

    fun halt() {
        scope.launch {
            try {
                onvif.stop()
            } catch (e: Exception) {
                // el siguiente movimiento reintenta
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Volver") }
            Spacer(Modifier.weight(1f))
            Text(
                camera.name.ifBlank { camera.host },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = cs.onBackground,
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        // Video
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exo
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            val err = videoError
            if (detecting) {
                Column(
                    Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                    Text("Buscando el canal del video…", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    Text(
                        detectNote,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (err != null) {
                Text(
                    err,
                    color = cs.error,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                )
            } else if (playerState != Player.STATE_READY) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                    Text("Conectando…", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                ) {
                    Text(
                        "● EN VIVO",
                        color = Color(0xFFFF5C5C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // Control PTZ
        Surface(shape = RoundedCornerShape(26.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Control PTZ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    val dot = when (ptzOk) {
                        true -> Color(0xFF4ADE80)
                        false -> cs.error
                        null -> Color(0xFFFACC15)
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                }

                PtzPad(
                    onMove = { px, py -> send(px * speed, py * speed, 0f) },
                    onStop = { halt() },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoldButton(
                        label = "－  Zoom",
                        modifier = Modifier.width(140.dp).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        textSize = 15.sp,
                        onDown = { send(0f, 0f, -speed) },
                        onUp = { halt() },
                    )
                    HoldButton(
                        label = "＋  Zoom",
                        modifier = Modifier.width(140.dp).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        textSize = 15.sp,
                        onDown = { send(0f, 0f, speed) },
                        onUp = { halt() },
                    )
                }

                Column(Modifier.fillMaxWidth()) {
                    Text("Velocidad", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.1f..1f)
                }

                Text(
                    ptzStatus,
                    fontSize = 12.sp,
                    color = if (ptzOk == false) cs.error else cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Luz LED
        Surface(shape = RoundedCornerShape(22.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Luz LED", fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                        Text(
                            if (lightIsOn) "Encendida" else "Apagada",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = lightIsOn,
                        onCheckedChange = { on ->
                            lightIsOn = on
                            scope.launch {
                                try {
                                    onvif.aux(if (on) camera.lightOnCmd else camera.lightOffCmd)
                                    lightError = null
                                } catch (e: Exception) {
                                    lightIsOn = !on
                                    lightError = "La cámara no aceptó el comando de luz (${e.message}). " +
                                        "Depende del modelo: puedes cambiar el comando en Editar > Ajustes avanzados."
                                }
                            }
                        },
                    )
                }
                val le = lightError
                if (le != null) Text(le, fontSize = 12.sp, color = cs.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PtzPad(onMove: (Float, Float) -> Unit, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(240.dp).clip(CircleShape).background(cs.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        PadKey("▲", Alignment.TopCenter, { onMove(0f, 1f) }, onStop)
        PadKey("▼", Alignment.BottomCenter, { onMove(0f, -1f) }, onStop)
        PadKey("◀", Alignment.CenterStart, { onMove(-1f, 0f) }, onStop)
        PadKey("▶", Alignment.CenterEnd, { onMove(1f, 0f) }, onStop)
        Box(
            Modifier.size(70.dp).clip(CircleShape).background(cs.background),
            contentAlignment = Alignment.Center,
        ) { Text("PTZ", color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}

@Composable
private fun BoxScope.PadKey(label: String, alignment: Alignment, onDown: () -> Unit, onUp: () -> Unit) {
    HoldButton(
        label = label,
        modifier = Modifier.align(alignment).padding(10.dp).size(68.dp),
        shape = CircleShape,
        onDown = onDown,
        onUp = onUp,
    )
}

/** Botón que actúa mientras lo mantienes presionado y se detiene al soltarlo. */
@Composable
private fun HoldButton(
    label: String,
    modifier: Modifier,
    shape: Shape,
    onDown: () -> Unit,
    onUp: () -> Unit,
    textSize: TextUnit = 24.sp,
) {
    val cs = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (pressed) cs.primary else cs.outline)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        down()
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                            up()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) cs.onPrimary else cs.onSurface,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
        )
    }
}
