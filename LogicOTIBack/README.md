# Backend LogicOTI

API de automatización, seguridad y supervisión del edificio OTI.

## Versiones

- Java 21
- Spring Boot 4.1.0
- Maven mediante Maven Wrapper
- SpringDoc OpenAPI 3.0.3
- Apache PLC4X 0.13.1
- PostgreSQL

## Responsabilidades

- Autenticación JWT y autorización por roles.
- Catálogo del edificio, áreas, cámaras y dispositivos.
- Lectura y escritura PLC mediante PLC4X EtherNet/IP.
- Alarmas de humo y movimiento, seguridad, horarios y automatización de iluminación.
- Armado por zonas para Planta Baja, Piso 1, Piso 2 y Patio/Exterior.
- WebSocket/STOMP para estados en tiempo real.
- Web Push con suscripciones, bitácora, reintentos y enlaces temporales de cámara.
- Diagnósticos, calidad de señales, históricos, reporte mensual y retención.
- PostgreSQL versionado exclusivamente mediante Flyway.

## Desarrollo

```powershell
docker compose up -d
```

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

La API inicia en `http://localhost:3210`.

- Estado del sistema: `GET http://localhost:3210/api/system/status`
- Swagger: `http://localhost:3210/swagger-ui.html`
- Health: `http://localhost:3210/actuator/health`

## Compilar y probar

```powershell
.\mvnw.cmd clean verify
```

## Producción

Activa el perfil `prod`, deshabilita Swagger y usa un secreto JWT aleatorio. No guardes credenciales
en archivos versionados. Configura al menos:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://servidor:5432/base
DB_USERNAME=usuario
DB_PASSWORD=contraseña
FRONTEND_ORIGIN=https://servidor
PLC_ENABLED=true
PLC_CONNECTION_STRING=logix:tcp://direccion-del-plc?backplane=1&slot=0
SWAGGER_ENABLED=false
JWT_SECRET=SECRETO_ALEATORIO_DE_64_BYTES_O_MAS
```

Para Web Push también se requieren `WEB_PUSH_ENABLED`, las dos claves VAPID y
`WEB_PUSH_SUBJECT`. Para cámaras se configuran `CAMERAS_ENABLED`, `CAMERA_PLAYBACK_BASE_URL` y
`CAMERA_AVAILABLE_STREAMS`.

Consulta `.env.example`. No copies `target/`, credenciales, direcciones reales de PLC ni archivos
`.env` al repositorio.

Para instalar o actualizar el backend como servicio automático de Windows se incluyen scripts
WinSW en [`deployment/windows`](deployment/windows). El servicio usa una copia local de `.env`,
registra logs rotativos y reinicia Java ante fallos.

## Seguridad por zonas y circuitos exteriores

Las zonas `PB`, `P1`, `P2` y `PATIO` pueden armarse individualmente o en conjunto. El armado no
enciende luces. Un movimiento en cualquier zona armada activa la alarma de esa zona y ordena
encender solo los circuitos seleccionados en **Encendido automático por movimiento** que pertenezcan
a las zonas armadas. La selección se usa a cualquier hora para alarmas; el interruptor y horario de
la automatización habitual solo controlan el movimiento en zonas desarmadas. El
reconocimiento conserva las zonas armadas y apaga únicamente luces que el módulo de seguridad
encendió; una luz que ya estaba encendida manualmente no se toma bajo su control.

Con varias alarmas simultáneas, las luces permanecen encendidas hasta reconocerlas todas o desarmar
la zona correspondiente. Sin luces seleccionadas se puede armar, con aviso de que no habrá iluminación
por alarma. Los contadores de circuitos en Seguridad muestran la selección configurada.

### Pruebas de sensores

Iniciar un diagnóstico exige que todos los sensores seleccionados estén en reposo. Se aprueba cada
uno tras observar reposo → activación → reposo; activar el sensor no termina la prueba. Durante ese
intervalo solo esos sensores generan `TEST_ACTIVATED` / `TEST_CLEARED` informativos, sin avisos Web Push,
alarmas de humo/intrusión ni acciones automáticas por movimiento. El historial permite filtrarlos.

La vigilancia normal vuelve por sensor al aprobar, cancelar o vencer el diagnóstico. Si queda activo
al cancelar/vencer, la siguiente lectura válida lo trata como alarma real aunque no haya cambiado el
BOOL. El estado persiste ante reinicios. Esta función no modifica sirenas ni protecciones físicas del PLC.

El backend aplica `V28__classify_sensor_diagnostic_events.sql` al actualizar. Debe actualizarse también
el frontend. Para validar la instalación véase [la guía de comprobación](../VALIDACION_DIAGNOSTICOS_Y_ZONAS.md).

La migración `V26__create_zoned_intrusion_security.sql` incorpora cuatro circuitos nuevos:

| Código | Circuito físico | Estado inicial |
| --- | --- | --- |
| `EXT_A01_LUZ01` | Patio, 7 luminarias consideradas como un circuito | Inactivo |
| `EXT_A02_LUZ01` | Entrada exterior, 4 luminarias consideradas como un circuito | Inactivo |
| `PB_A06_LUZ01` | Entrada interior, circuito A | Inactivo |
| `PB_A06_LUZ02` | Entrada interior, circuito B | Inactivo |

La migración V27 activa esos cuatro circuitos con los tags BOOL de comando y retorno confirmados
por el responsable del PLC. En una instalación ya actualizada no hay que volver a crear los tags ni
activar manualmente los circuitos. Si cambia el cableado o la nomenclatura del PLC, se debe actualizar
el catálogo para que el comando y el retorno correspondan al mismo circuito físico.

La revisión de armado valida los circuitos **seleccionados**. Si uno de esos circuitos está pendiente,
mostrará `ZONE_LIGHT_PENDING`; un circuito no seleccionado no impide armar. El patio no necesita
sensores de movimiento: participa como iluminación de respuesta de las zonas armadas, pero no
origina alarmas de movimiento.
