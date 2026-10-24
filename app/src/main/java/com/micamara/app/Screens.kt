package com.micamara.app

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

// ───────────────────────── Inicio: lista de cámaras ─────────────────────────

@Composable
fun HomeScreen(
    cameras: List<Camera>,
    onOpen: (Camera) -> Unit,
    onEdit: (Camera) -> Unit,
    onAdd: () -> Unit,
    onGallery: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Mi Cámara",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onGallery) { Text("🖼 Capturas") }
            }
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
    var stepMode by remember { mutableStateOf(camera?.stepMode ?: false) }
    var remoteHost by remember { mutableStateOf(camera?.remoteHost ?: "") }
    var remoteRtsp by remember { mutableStateOf(if ((camera?.remoteRtspPort ?: 0) > 0) camera!!.remoteRtspPort.toString() else "") }
    var remoteOnvif by remember { mutableStateOf(if ((camera?.remoteOnvifPort ?: 0) > 0) camera!!.remoteOnvifPort.toString() else "") }
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
            SwitchRow("Movimiento por pasos (si la cámara no se detiene)", stepMode) { stepMode = it }

            Surface(shape = RoundedCornerShape(18.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🌐  Acceso remoto (fuera de casa)", fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                    Text(
                        "Escribe la IP de tu VPN (por ejemplo Tailscale) o tu dominio. La app usa la dirección " +
                            "de casa si la cámara responde y esta si no. No abras puertos del router a Internet.",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                    Field(remoteHost, { remoteHost = it }, "IP o dominio remoto")
                    Field(remoteRtsp, { remoteRtsp = it }, "Puerto RTSP remoto (vacío = el mismo)", KeyboardType.Number)
                    Field(remoteOnvif, { remoteOnvif = it }, "Puerto ONVIF remoto (vacío = el mismo)", KeyboardType.Number)
                }
            }
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
                        stepMode = stepMode,
                        remoteHost = remoteHost.trim(),
                        remoteRtspPort = remoteRtsp.toIntOrNull() ?: 0,
                        remoteOnvifPort = remoteOnvif.toIntOrNull() ?: 0,
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
