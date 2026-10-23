# Mi Cámara

App Android (Kotlin + Jetpack Compose + Media3) para ver cámaras IP por **RTSP** y controlarlas por **ONVIF**.
Sin anuncios, sin rastreadores, sin cuentas ni nube. Solo permisos de red.

## Funciones
- **Búsqueda automática**: encuentra la IP de la cámara en tu WiFi (ONVIF WS-Discovery + escaneo del puerto RTSP 554) y detecta el puerto ONVIF.
- **Varias cámaras**: lista con alta, edición y borrado.
- **PTZ**: joystick de giro/inclinación, zoom y control de velocidad (mantén presionado para mover).
- **Luz LED**: interruptor por comando auxiliar ONVIF (configurable por cámara).
- Tema oscuro, pantalla encendida mientras ves el video.

## Compilar
- Con Android Studio: abre la carpeta y pulsa Run.
- Sin PC: el workflow `.github/workflows/build.yml` compila el APK en GitHub (pestaña Actions > Run workflow > Artifacts).

## Notas importantes
- Teléfono y cámara deben estar en la **misma WiFi**.
- Muchas cámaras V380 traen RTSP/ONVIF desactivado o solo hablan con la nube del fabricante. Si la búsqueda no encuentra nada, la cámara no expone esos servicios.
- La luz LED no tiene comando estándar en ONVIF: por defecto se usa `tt:IRLamp|On/Off`. Si tu modelo usa otro, cámbialo en Editar > Ajustes avanzados.
- Si la cámara se mueve al revés, activa "Invertir giro" o "Invertir inclinación" en Ajustes avanzados.
- Las contraseñas se guardan en el almacenamiento privado de la app.
