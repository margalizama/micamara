# Mi Cámara

App Android (Kotlin + Jetpack Compose + VLC) para ver cámaras IP por **RTSP** y controlarlas por **ONVIF**.
Sin anuncios, sin rastreadores, sin cuentas ni nube. Solo permisos de red.

## Funciones
- **Búsqueda automática** de la IP (ONVIF WS-Discovery + escaneo del puerto 554) y **detección del canal** RTSP.
- **Varias cámaras**: lista con alta, edición y borrado.
- **PTZ**: joystick (mantén presionado), zoom, velocidad y botón central ■ de parada de emergencia.
  - Cada orden de movimiento lleva un tiempo máximo, así la cámara se detiene sola aunque se pierda el "parar".
  - *Movimiento por pasos* (Editar > Ajustes avanzados): cada toque mueve un trozo corto; úsalo si tu cámara no se detiene al soltar.
- **Luz LED** por comando auxiliar ONVIF (configurable por cámara).
- **Modo apaisado**: botón ⛶ Completa, o gira el teléfono. Video a pantalla completa con controles encima (toca la pantalla para ocultarlos).
- **Capturas**: 📷 foto y ⏺ grabación en el teléfono, con galería interna (🖼 Capturas) y exportación a la galería de Android (10+).
- **Tarjeta SD**: lista y reproduce las grabaciones de la SD por ONVIF (Profile G), eligiendo desde qué minuto empezar.
- **Acceso remoto**: dirección alternativa por cámara; la app usa la de casa si responde y la remota si no.

## Ver la cámara desde fuera de casa (recomendado: VPN)
No abras el puerto 554 ni el de ONVIF en el router: cualquiera en Internet podría ver tu cámara.

1. Instala **Tailscale** (gratis) en el teléfono y en un equipo que esté siempre encendido en tu casa
   (una PC, una Raspberry Pi, un teléfono viejo o un router compatible).
2. En ese equipo activa "subnet router" para tu red local (por ejemplo `192.168.1.0/24`) y aprueba la ruta en el panel de Tailscale.
3. En la app: Editar cámara > Ajustes avanzados > Acceso remoto, y escribe la **misma IP local de la cámara**
   (o la IP de Tailscale del equipo si la cámara está en otra red).
4. Fuera de casa, con la VPN activa, la app detecta que la cámara local no responde y usa la dirección remota
   (verás la etiqueta 🌐 REMOTO en el video).

Alternativa: WireGuard/OpenVPN en tu router.

## Compilar
- Con Android Studio: abre la carpeta y pulsa Run.
- Sin PC: el workflow `.github/workflows/build.yml` compila el APK en GitHub (Actions > Run workflow > Artifacts).

## Notas importantes
- Muchas cámaras V380 traen RTSP/ONVIF desactivado o solo hablan con la nube del fabricante.
- **Tarjeta SD:** solo aparece si la cámara expone sus grabaciones por ONVIF. Muchas V380 las guardan en formato propio y solo las muestran en su app original.
- La luz LED no tiene comando estándar en ONVIF: por defecto se usa `tt:IRLamp|On/Off`. Si tu modelo usa otro, cámbialo en Ajustes avanzados.
- Si la cámara se mueve al revés, activa "Invertir giro" o "Invertir inclinación".
- Las contraseñas se guardan en el almacenamiento privado de la app.
