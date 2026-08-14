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

Consulta `.env.example` y [la guía de operación](../docs/OPERACION_Y_DESPLIEGUE.md). No copies
`target/`, credenciales, direcciones reales de PLC ni archivos `.env` al repositorio.

Para instalar o actualizar el backend como servicio automático de Windows se incluyen scripts
WinSW en [`deployment/windows`](deployment/windows). El servicio usa una copia local de `.env`,
registra logs rotativos y reinicia Java ante fallos.
