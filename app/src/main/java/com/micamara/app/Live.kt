package com.micamara.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

// ───────────────────────── Elegir ruta: en casa o remota ─────────────────────────

/** true = usar la dirección remota. Bloqueante: llamar desde un hilo de fondo. */
private fun pickRoute(context: Context, cam: Camera): Boolean {
    WifiBinder.bind(context)
    if (!cam.hasRemote()) return false
    if (tcpOpen(cam.host, cam.rtspPort, 1200)) return false
    WifiBinder.unbind(context) // fuera de casa: que use datos móviles u otra WiFi
    return true
}

private fun findSurface(v: View): SurfaceView? {
    if (v is SurfaceView) return v
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            findSurface(v.getChildAt(i))?.let { return it }
        }
    }
    return null
}

@Composable
fun LiveScreen(
    camera: Camera,
    onBack: () -> Unit,
    onCameraUpdated: (Camera) -> Unit,
    onOpenSd: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var route by remember(camera.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(camera.id) {
        route = withContext(Dispatchers.IO) { pickRoute(context, camera) }
    }
    val remote = route
    if (remote == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("Buscando la cámara…", color = cs.onSurfaceVariant)
                TextButton(onClick = onBack) { Text("Cancelar") }
            }
        }
    } else {
        LiveContent(
            cam = camera.forRoute(remote),
            remote = remote,
            onBack = onBack,
            onChannelFound = { path, port -> onCameraUpdated(camera.withChannel(path, port, remote)) },
            onOpenSd = { onOpenSd(remote) },
        )
    }
}

// ───────────────────────── Vista en vivo ─────────────────────────

@Composable
private fun LiveContent(
    cam: Camera,
    remote: Boolean,
    onBack: () -> Unit,
    onChannelFound: (String, Int) -> Unit,
    onOpenSd: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current

    var playing by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var ptzOk by remember { mutableStateOf<Boolean?>(null) }
    var ptzStatus by remember { mutableStateOf("Conectando PTZ…") }
    var speed by remember { mutableFloatStateOf(0.5f) }
    var lightIsOn by remember { mutableStateOf(false) }
    var lightError by remember { mutableStateOf<String?>(null) }
    var rawError by remember { mutableStateOf("") }
    var detecting by remember { mutableStateOf(false) }
    var detectNote by remember { mutableStateOf("") }
    var autoTried by remember(cam.id) { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var layoutRef by remember { mutableStateOf<VLCVideoLayout?>(null) }

    val url = cam.rtspUrl()

    // ── Video (VLC) ──
    val vlc = rememberVlc()
    var attempt by remember(url) { mutableIntStateOf(0) } // 0 = TCP, 1 = UDP

    DisposableEffect(vlc.player) {
        vlc.player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing, MediaPlayer.Event.Vout -> {
                    playing = true
                    videoError = null
                }

                MediaPlayer.Event.EncounteredError -> {
                    if (!playing) {
                        rawError = "error de reproducción de VLC"
                        videoError = "No se pudo abrir el video. Revisa la IP, usuario/contraseña y el canal."
                    }
                }

                else -> {}
            }
        }
        onDispose {
            try {
                vlc.player.record(null)
            } catch (e: Throwable) {
                // no estaba grabando
            }
        }
    }

    LaunchedEffect(url, attempt) {
        playing = false
        videoError = null
        vlc.player.stop()
        val media = Media(vlc.lib, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=500")
        if (attempt == 0) media.addOption(":rtsp-tcp")
        vlc.player.media = media
        media.release()
        vlc.player.play()
        delay(8000)
        if (!playing && videoError == null) {
            if (attempt == 0) {
                attempt = 1 // reintenta con UDP
            } else {
                rawError = "tiempo de espera agotado"
                videoError = "La cámara no respondió a tiempo."
            }
        }
    }

    // ── PTZ / ONVIF ──
    val onvif = remember(cam.id, cam.host, cam.onvifPort) {
        OnvifClient(cam.host, cam.onvifPort, cam.user, cam.password)
    }
    val ptz = remember(onvif) {
        PtzController(onvif) { ok, msg ->
            ptzOk = ok
            ptzStatus = msg
        }
    }
    DisposableEffect(ptz) { onDispose { ptz.shutdown() } }

    LaunchedEffect(onvif) {
        try {
            onvif.prepare()
            ptzOk = true
            ptzStatus = "PTZ listo"
        } catch (e: Exception) {
            ptzOk = false
            ptzStatus = "PTZ no disponible (${e.message}). Revisa el puerto ONVIF " +
                "(${cam.onvifPort}) y la contraseña en Editar > Ajustes avanzados."
        }
    }

    // Si el video falla, se busca solo el canal correcto (una vez por pantalla)
    val hasError = videoError != null
    LaunchedEffect(hasError) {
        if (hasError && !autoTried) {
            autoTried = true
            detecting = true
            detectNote = "Preguntando a la cámara por ONVIF…"
            val fromOnvif = try {
                onvif.streamUri()
            } catch (e: Exception) {
                null
            }
            if (fromOnvif != null && (fromOnvif.path != cam.rtspPath || fromOnvif.port != cam.rtspPort)) {
                detecting = false
                onChannelFound(fromOnvif.path, fromOnvif.port)
                return@LaunchedEffect
            }
            val res = withContext(Dispatchers.IO) {
                RtspProbe.find(cam.host, cam.user, cam.password, cam.rtspPort, cam.rtspPath) {
                    detectNote = it
                }
            }
            detecting = false
            when (res) {
                is ProbeResult.Found ->
                    if (res.stream.path != cam.rtspPath || res.stream.port != cam.rtspPort) {
                        onChannelFound(res.stream.path, res.stream.port)
                    } else {
                        videoError = "La cámara responde en /${cam.rtspPath}, pero el video no abrió.\n" +
                            "Detalle: $rawError"
                    }

                ProbeResult.BadCredentials ->
                    videoError = "La cámara pide usuario y contraseña, o no son correctos. Revísalos en Editar."

                is ProbeResult.NotFound ->
                    videoError = "No encontré el canal de video. Verifica la IP y que RTSP esté activo en la cámara."
            }
        }
    }

    // Pantalla encendida y orientación libre al salir
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Pantalla completa (sin barras) en horizontal
    DisposableEffect(landscape) {
        val window = activity?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            if (landscape) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                c.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000)
            toast = null
        }
    }

    // ── Acciones ──
    fun enterLandscape() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun exitLandscape() {
        scope.launch {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            delay(1200)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun snapshot() {
        val sv = layoutRef?.let { findSurface(it) }
        if (sv == null || sv.width == 0 || sv.height == 0 || !playing) {
            toast = "Aún no hay video para capturar"
            return
        }
        val bmp = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
        val main = Handler(Looper.getMainLooper())
        try {
            PixelCopy.request(sv, bmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    Thread {
                        val ok = try {
                            Storage.photoFile(context, cam.name).outputStream().use {
                                bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                            }
                            true
                        } catch (e: Exception) {
                            false
                        }
                        main.post { toast = if (ok) "📷 Foto guardada en Capturas" else "No se pudo guardar la foto" }
                    }.start()
                } else {
                    main.post { toast = "No se pudo capturar la imagen" }
                }
            }, main)
        } catch (e: Exception) {
            toast = "No se pudo capturar la imagen"
        }
    }

    fun toggleRecord() {
        if (!recording) {
            if (!playing) {
                toast = "Espera a que haya video"
                return
            }
            val ok = try {
                vlc.player.record(Storage.dir(context).absolutePath)
            } catch (e: Throwable) {
                false
            }
            recording = ok
            toast = if (ok) "⏺ Grabando…" else "No se pudo iniciar la grabación"
        } else {
            try {
                vlc.player.record(null)
            } catch (e: Throwable) {
                // ignorar
            }
            recording = false
            toast = "Grabación guardada en Capturas"
        }
    }

    fun toggleLight(on: Boolean) {
        lightIsOn = on
        scope.launch {
            try {
                onvif.aux(if (on) cam.lightOnCmd else cam.lightOffCmd)
                lightError = null
            } catch (e: Exception) {
                lightIsOn = !on
                lightError = "La cámara no aceptó el comando de luz (${e.message}). " +
                    "Depende del modelo: puedes cambiar el comando en Editar > Ajustes avanzados."
            }
        }
    }

    val step = cam.stepMode

    Box(Modifier.fillMaxSize()) {
        if (landscape) {
            // ───── Horizontal: video a pantalla completa con controles encima ─────
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) { detectTapGestures(onTap = { showControls = !showControls }) },
            ) {
                VideoBox(
                    modifier = Modifier.fillMaxSize(),
                    vlc = vlc,
                    playing = playing,
                    detecting = detecting,
                    detectNote = detectNote,
                    videoError = videoError,
                    recording = recording,
                    remote = remote,
                    onLayout = { layoutRef = it },
                )
                if (showControls) {
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onBack) { Text("‹ Volver", color = Color.White) }
                        Text(
                            cam.name.ifBlank { cam.host },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { exitLandscape() }) { Text("✕ Salir", color = Color.White) }
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 16.dp)
                            .background(Color.Black.copy(alpha = 0.30f), CircleShape),
                    ) {
                        PtzPad(
                            size = 170.dp,
                            onPress = { px, py -> ptz.press(px * speed, py * speed, 0f, step) },
                            onRelease = { ptz.release(step) },
                            onCenter = { ptz.stopNow() },
                        )
                    }
                    Column(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RoundAction("📷") { snapshot() }
                        RoundAction(if (recording) "⏹" else "⏺") { toggleRecord() }
                        RoundAction(if (lightIsOn) "💡" else "🔦") { toggleLight(!lightIsOn) }
                        HoldButton(
                            label = "＋",
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            onDown = { ptz.press(0f, 0f, speed, step) },
                            onUp = { ptz.release(step) },
                        )
                        HoldButton(
                            label = "－",
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            onDown = { ptz.press(0f, 0f, -speed, step) },
                            onUp = { ptz.release(step) },
                        )
                    }
                }
            }
        } else {
            // ───── Vertical ─────
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Volver") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        cam.name.ifBlank { cam.host },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = cs.onBackground,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }

                VideoBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black),
                    vlc = vlc,
                    playing = playing,
                    detecting = detecting,
                    detectNote = detectNote,
                    videoError = videoError,
                    recording = recording,
                    remote = remote,
                    onLayout = { layoutRef = it },
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip("📷 Foto", Modifier.weight(1f)) { snapshot() }
                    ActionChip(if (recording) "⏹ Parar" else "⏺ Grabar", Modifier.weight(1f)) { toggleRecord() }
                    ActionChip("⛶ Completa", Modifier.weight(1f)) { enterLandscape() }
                    ActionChip("💾 SD", Modifier.weight(1f)) { onOpenSd() }
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
                                if (step) "Control PTZ · por pasos" else "Control PTZ",
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
                            size = 240.dp,
                            onPress = { px, py -> ptz.press(px * speed, py * speed, 0f, step) },
                            onRelease = { ptz.release(step) },
                            onCenter = { ptz.stopNow() },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HoldButton(
                                label = "－  Zoom",
                                modifier = Modifier.width(140.dp).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                textSize = 15.sp,
                                onDown = { ptz.press(0f, 0f, -speed, step) },
                                onUp = { ptz.release(step) },
                            )
                            HoldButton(
                                label = "＋  Zoom",
                                modifier = Modifier.width(140.dp).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                textSize = 15.sp,
                                onDown = { ptz.press(0f, 0f, speed, step) },
                                onUp = { ptz.release(step) },
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
                            Switch(checked = lightIsOn, onCheckedChange = { toggleLight(it) })
                        }
                        val le = lightError
                        if (le != null) Text(le, fontSize = 12.sp, color = cs.error)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // Aviso breve (foto guardada, etc.)
        val t = toast
        if (t != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.80f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            ) {
                Text(t, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

// ───────────────────────── Piezas de la pantalla ─────────────────────────

@Composable
private fun VideoBox(
    modifier: Modifier,
    vlc: VlcHandle,
    playing: Boolean,
    detecting: Boolean,
    detectNote: String,
    videoError: String?,
    recording: Boolean,
    remote: Boolean,
    onLayout: (VLCVideoLayout) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    vlc.player.detachViews()
                    vlc.player.attachViews(layout, null, false, false)
                    onLayout(layout)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
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
        } else if (videoError != null) {
            Text(
                videoError,
                color = cs.error,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
            )
        } else if (!playing) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                Text("Conectando…", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        } else {
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Badge("● EN VIVO", Color(0xFFFF5C5C))
                if (remote) Badge("🌐 REMOTO", Color(0xFF7C9CFF))
                if (recording) Badge("⏺ REC", Color(0xFFFF5C5C))
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.55f)) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ActionChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(cs.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
    }
}

@Composable
private fun RoundAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 22.sp) }
}

@Composable
private fun PtzPad(size: Dp, onPress: (Float, Float) -> Unit, onRelease: () -> Unit, onCenter: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val key = size * 0.30f
    Box(
        Modifier.size(size).clip(CircleShape).background(cs.surfaceVariant.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        PadKey("▲", Alignment.TopCenter, key, { onPress(0f, 1f) }, onRelease)
        PadKey("▼", Alignment.BottomCenter, key, { onPress(0f, -1f) }, onRelease)
        PadKey("◀", Alignment.CenterStart, key, { onPress(-1f, 0f) }, onRelease)
        PadKey("▶", Alignment.CenterEnd, key, { onPress(1f, 0f) }, onRelease)
        Box(
            Modifier
                .size(size * 0.28f)
                .clip(CircleShape)
                .background(cs.background)
                .clickable { onCenter() },
            contentAlignment = Alignment.Center,
        ) { Text("■", color = cs.error, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
private fun BoxScope.PadKey(
    label: String,
    alignment: Alignment,
    keySize: Dp,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    HoldButton(
        label = label,
        modifier = Modifier.align(alignment).padding(6.dp).size(keySize),
        shape = CircleShape,
        onDown = onDown,
        onUp = onUp,
    )
}

/** Botón que actúa mientras lo mantienes presionado y avisa al soltarlo. */
@Composable
internal fun HoldButton(
    label: String,
    modifier: Modifier,
    shape: Shape,
    onDown: () -> Unit,
    onUp: () -> Unit,
    textSize: TextUnit = 22.sp,
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
