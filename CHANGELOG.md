# Historial de cambios

## Próxima versión candidata

### Incorporado

- Línea de tiempo interactiva del histórico con segmentos de movimiento, grabaciones sin marca y
  periodos sin segmento devuelto; salto a la hora seleccionada.

- Control PTZ opcional para las cámaras 25–28: movimientos breves, parada desde el backend,
  roles ADMIN/OPERATOR y conexión ISAPI directa o mediante proxy del NVR.

- Armado independiente o conjunto de Planta Baja, Piso 1, Piso 2 y Patio/Exterior.
- Armado por zonas limitado a habilitar alarmas; al completar el armado apaga las luces y los
  minisplits de las zonas seleccionadas para dejar el edificio sin consumo innecesario.
- Ahorro de energía independiente por área: con la zona desarmada, el movimiento enciende solo
  las luces de esa misma área y los tiempos configurados apagan sus luces y minisplits.
- Selección de iluminación de emergencia utilizada exclusivamente cuando una zona armada detecta
  movimiento y dispara la alarma.
- Reconocimiento de alarma sin desarmar las zonas ni modificar la iluminación automática.
- Inventario y activación de los circuitos del patio, entrada exterior y los dos circuitos de la
  entrada interior, con tags de comando y retroalimentación confirmados en el PLC.
- Suscripción WebSocket de las zonas de seguridad autorizada y validada.
- Supervisión del edificio, control PLC y actualización por WebSocket.
- Cámaras mediante MediaMTX, incluido enlace temporal desde alertas de movimiento.
- Primera etapa del histórico de cámaras: consulta autenticada al NVR Hikvision por cámara, fecha y
  horario, listado de segmentos sin exponer credenciales ni URI RTSP al navegador.
- Reproducción histórica protegida mediante rutas WebRTC temporales de MediaMTX, con validación de
  host y track, vencimiento configurable y limpieza automática.
- Selección de hora dentro de un segmento histórico para avanzar o retroceder; cada salto valida
  nuevamente la grabación en el NVR y abre la transmisión desde la hora elegida.
- PWA instalable con aviso de nueva versión.
- Web Push con prueba por dispositivo, persistencia de entregas y reintentos.
- Reconocimiento y comentarios de alarmas.
- Diagnóstico y omisiones temporales de sensores.
- Diagnóstico con ciclo reposo → activación → reposo y eventos de prueba identificados en historial.
  Solo los sensores en prueba dejan de generar alarmas y avisos de LogicOTI; al finalizar o vencer
  el diagnóstico vuelven a vigilancia normal, incluso si permanecen activos.
- Autenticación TOTP en dos pasos opcional por usuario, con QR, códigos de recuperación de un solo
  uso, secretos cifrados, prevención de reutilización y límite de intentos. La desactivación
  acepta el código vigente junto con la contraseña actual, incluso después de activarlo o iniciar
  sesión en el mismo intervalo TOTP.
- Cámara `Acceso puerta principal` clasificada en Exterior sin cambiar su canal ni transmisión.
- Calidad individual `GOOD`, `BAD` y `STALE` con última actualización.
- Reporte ejecutivo mensual, tendencias y exportación PDF.
- Política configurable de retención y limpieza histórica.
- Servicios automáticos de Caddy y MediaMTX.
- GitHub Actions para pruebas y build de backend/frontend.

### Alcance confirmado

- Contactos eléctricos y apagadores permanecen únicamente como inventario físico.
- Sensores de puerta fuera de alcance.
- Minisplits limitados a encendido y apagado.
- Web Push sustituye la propuesta de avisos por WhatsApp.

### Pendiente para liberar

- Validación final en el servidor y teléfono.
- Instalar el backend como servicio automático de Windows.
- Pull Request de `desarrollo` hacia `main`.
- Crear la etiqueta de versión después de aprobar el Pull Request.
