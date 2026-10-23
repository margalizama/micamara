# Mi Cámara

App Android mínima (Kotlin + Jetpack Compose + Media3) para ver tu cámara por **RTSP** en la red local.
Sin anuncios, sin rastreadores, sin cuentas. Solo pide permiso de Internet/red.

## Cómo compilar
1. Abre la carpeta en **Android Studio** (Ladybug o más nuevo). Aceptará crear el wrapper de Gradle y sincronizará solo.
2. Conecta tu teléfono (depuración USB) y pulsa **Run**, o usa *Build > Build APK(s)*.

## Cómo usarla
1. Teléfono y cámara en la **misma red WiFi**.
2. Introduce la IP de la cámara, puerto (554), usuario/contraseña (si los pide) y la ruta del stream.
3. Pulsa **Ver en vivo**.

## Rutas RTSP comunes en cámaras V380 / genéricas
Prueba estas (primero en VLC para verificar):
- `live/ch00_0`
- `live/ch00_1` (subflujo, menor calidad)
- `onvif1`
- `stream1`

## Si no conecta
- Muchas V380 vienen con RTSP **desactivado**. Hay que habilitarlo desde la app original (ajustes avanzados / ONVIF) o con firmware que lo soporte.
- Verifica con `nmap -p 554 IP_CAMARA` o con VLC (`Medio > Abrir ubicación de red`).
- Si el puerto 554 está cerrado, la cámara solo habla con la nube del fabricante y esta app no podrá verla.

## Nota
Usuario y contraseña se guardan en el almacenamiento privado de la app (SharedPreferences). Para uso doméstico basta; si quieres más seguridad, cámbialo por `EncryptedSharedPreferences`.
