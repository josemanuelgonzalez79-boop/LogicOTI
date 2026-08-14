# Historial de cambios

## Próxima versión candidata

### Incorporado

- Supervisión del edificio, control PLC y actualización por WebSocket.
- Cámaras mediante MediaMTX, incluido enlace temporal desde alertas de movimiento.
- PWA instalable con aviso de nueva versión.
- Web Push con prueba por dispositivo, persistencia de entregas y reintentos.
- Reconocimiento y comentarios de alarmas.
- Diagnóstico y omisiones temporales de sensores.
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
