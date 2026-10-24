package com.micamara.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controla el PTZ de forma segura:
 *  - Modo continuo: repite el movimiento con un tiempo máximo corto mientras se mantiene
 *    presionado; si se pierde la orden de parar, la cámara se detiene sola en ~1 s.
 *  - Modo por pasos: cada toque mueve un trozo corto y para.
 *  - Siempre se envía Stop al terminar, aunque la corrutina se cancele.
 */
class PtzController(
    private val onvif: OnvifClient,
    private val onStatus: (Boolean, String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun press(pan: Float, tilt: Float, zoom: Float, step: Boolean) {
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin() // el Stop anterior termina antes de empezar otro movimiento
            try {
                if (step) {
                    onvif.move(pan, tilt, zoom, STEP_MS + 300)
                    delay(STEP_MS.toLong())
                } else {
                    while (isActive) {
                        onvif.move(pan, tilt, zoom, 1200)
                        delay(500)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStatus(false, "Error PTZ: ${e.message}")
            } finally {
                withContext(NonCancellable) {
                    try {
                        onvif.stop()
                    } catch (e: Exception) {
                        onStatus(false, "No se pudo detener: ${e.message}")
                    }
                }
            }
        }
    }

    /** Al soltar: en modo continuo detiene; en modo pasos el paso termina solo. */
    fun release(step: Boolean) {
        if (!step) job?.cancel()
    }

    /** Parada de emergencia (botón central). */
    fun stopNow() {
        val j = job
        job = null
        if (j != null && j.isActive) {
            j.cancel()
        } else {
            scope.launch {
                try {
                    onvif.stop()
                    onStatus(true, "Detenido")
                } catch (e: Exception) {
                    onStatus(false, "No se pudo detener: ${e.message}")
                }
            }
        }
    }

    /** Al salir de la pantalla: para la cámara y cierra la conexión. */
    fun shutdown() {
        job?.cancel()
        job = null
        onvif.closeAfterStop()
        scope.cancel()
    }

    companion object {
        const val STEP_MS = 350
    }
}
