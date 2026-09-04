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

## Seguridad por zonas y circuitos pendientes

Las zonas `PB`, `P1`, `P2` y `PATIO` pueden armarse individualmente o en conjunto. Al completar el
armado se encienden las luces de las zonas elegidas. Un movimiento en cualquier zona armada activa
la alarma de esa zona y ordena encender la iluminación de todas las zonas que estén armadas. El
reconocimiento conserva las zonas armadas y apaga únicamente luces que el módulo de seguridad
encendió; una luz que ya estaba encendida manualmente no se toma bajo su control.

La migración `V26__create_zoned_intrusion_security.sql` incorpora cuatro circuitos nuevos:

| Código | Circuito físico | Estado inicial |
| --- | --- | --- |
| `EXT_A01_LUZ01` | Patio, 7 luminarias consideradas como un circuito | Inactivo |
| `EXT_A02_LUZ01` | Entrada exterior, 4 luminarias consideradas como un circuito | Inactivo |
| `PB_A06_LUZ01` | Entrada interior, circuito A | Inactivo |
| `PB_A06_LUZ02` | Entrada interior, circuito B | Inactivo |

Los tags incluidos son nombres provisionales. No se debe cambiar `active` a `TRUE` hasta que el
eléctrico o programador PLC confirme para cada circuito el cableado, el tag BOOL de comando y el tag
BOOL de retorno. Una vez confirmados, el administrador actualiza cada dispositivo en PostgreSQL:

```sql
UPDATE building_device
SET plc_command_tag = '<TAG_CMD_REAL>',
    plc_state_tag = '<TAG_FB_REAL>',
    active = TRUE
WHERE code = '<CODIGO_DEL_CIRCUITO>';
```

Después se reinicia el backend y se ejecuta el precheck de la zona correspondiente. Mientras un
circuito permanezca pendiente, el sistema mostrará `ZONE_LIGHT_PENDING` y no permitirá armar esa
zona. El patio no necesita sensores de movimiento: participa como iluminación de respuesta, pero no
origina alarmas de movimiento.
