# Historial de cambios

## Próxima versión candidata

### Incorporado

- Armado independiente o conjunto de Planta Baja, Piso 1, Piso 2 y Patio/Exterior.
- Encendido de iluminación por zona durante el armado y respuesta conjunta de todas las zonas
  armadas cuando se detecta movimiento.
- Reconocimiento de alarma sin desarmar las zonas y apagado exclusivo de las luces que fueron
  encendidas por seguridad.
- Protección de las luces bajo Seguridad frente al apagado automático por horario o inactividad.
- Inventario provisional del patio, entrada exterior y los dos circuitos de la entrada interior.
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

- Confirmar con el eléctrico/programador PLC los tags de comando y retorno de los cuatro circuitos
  nuevos; permanecen inactivos hasta completar esa validación.
- Validación final en el servidor y teléfono.
- Instalar el backend como servicio automático de Windows.
- Pull Request de `desarrollo` hacia `main`.
- Crear la etiqueta de versión después de aprobar el Pull Request.
