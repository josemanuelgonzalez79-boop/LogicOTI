# Historial de cambios

## Próxima versión candidata

### Incorporado

- Armado independiente o conjunto de Planta Baja, Piso 1, Piso 2 y Patio/Exterior.
- Armado sin encender luces y respuesta conjunta de las zonas armadas ante movimiento, limitada
  a los circuitos seleccionados en Encendido automático por movimiento.
- Reconocimiento de alarma sin desarmar las zonas y apagado exclusivo de las luces que fueron
  encendidas por seguridad.
- Protección de las luces bajo Seguridad frente al apagado automático por horario o inactividad.
- Inventario y activación de los circuitos del patio, entrada exterior y los dos circuitos de la
  entrada interior, con tags de comando y retroalimentación confirmados en el PLC.
- Suscripción WebSocket de las zonas de seguridad autorizada y validada.
- Supervisión del edificio, control PLC y actualización por WebSocket.
- Cámaras mediante MediaMTX, incluido enlace temporal desde alertas de movimiento.
- PWA instalable con aviso de nueva versión.
- Web Push con prueba por dispositivo, persistencia de entregas y reintentos.
- Reconocimiento y comentarios de alarmas.
- Diagnóstico y omisiones temporales de sensores.
- Diagnóstico con ciclo reposo → activación → reposo y eventos de prueba identificados en historial.
  Solo los sensores en prueba dejan de generar alarmas y avisos de LogicOTI; al finalizar o vencer
  el diagnóstico vuelven a vigilancia normal, incluso si permanecen activos.
- La iluminación por horario excluye zonas armadas para evitar que vuelva a encender sus luces
  después de reconocer una alarma sin una nueva activación.
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
