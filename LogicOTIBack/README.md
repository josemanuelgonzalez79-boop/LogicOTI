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
TWO_FACTOR_ENCRYPTION_KEY=OTRO_SECRETO_ALEATORIO_DE_64_BYTES_O_MAS
```

Para Web Push también se requieren `WEB_PUSH_ENABLED`, las dos claves VAPID y
`WEB_PUSH_SUBJECT`. Para cámaras se configuran `CAMERAS_ENABLED`, `CAMERA_PLAYBACK_BASE_URL` y
`CAMERA_AVAILABLE_STREAMS`. La búsqueda de grabaciones del NVR se habilita por separado con
`CAMERA_HISTORY_ENABLED` y las variables `NVR_*`; sus credenciales permanecen exclusivamente en el
backend.

### Búsqueda de grabaciones Hikvision

El endpoint autenticado `POST /api/cameras/{cameraCode}/recordings/search` consulta ISAPI mediante
Digest y recibe fechas locales sin zona horaria. Para el NVR DS-7632NXI-K2/16P validado se conserva
`NVR_LOCAL_TIME_AS_UTC=true`, ya que su firmware V4.83.005 espera la hora local con sufijo `Z`.

```text
CAMERA_HISTORY_ENABLED=true
NVR_BASE_URL=http://direccion-del-nvr
NVR_USERNAME=cuenta-exclusiva-de-solo-lectura
NVR_PASSWORD=CAMBIAR_LOCALMENTE
NVR_TIME_ZONE=America/Mazatlan
NVR_LOCAL_TIME_AS_UTC=true
NVR_CONNECT_TIMEOUT=5s
NVR_RESPONSE_TIMEOUT=15s
NVR_MAX_SEARCH_HOURS=24
NVR_MAX_RESULTS=100
CAMERA_MOTION_SDK_ENABLED=false
CAMERA_MOTION_SDK_DLL=
CAMERA_MOTION_SDK_SCRIPT=
CAMERA_HISTORY_PLAYBACK_ENABLED=true
MEDIAMTX_CONTROL_URL=http://127.0.0.1:9997
MEDIAMTX_CONTROL_TIMEOUT=5s
CAMERA_HISTORY_PLAYBACK_SESSION_TTL=2h
```

Para mostrar las franjas rojas de actividad en Windows, instale el SDK Hikvision x64
en el mismo equipo donde corre el backend y configure, por ejemplo:

```text
CAMERA_MOTION_SDK_ENABLED=true
CAMERA_MOTION_SDK_DLL=C:/ruta/a/LogicOTI/LogicOTIBack/vendor/hikvision-sdk/lib/HCNetSDK.dll
CAMERA_MOTION_SDK_SCRIPT=C:/ruta/a/LogicOTI/scripts/diagnosticar-eventos-sdk-nvr.ps1
CAMERA_MOTION_SDK_PORT=8000
CAMERA_MOTION_SDK_TIMEOUT=65s
```

Para conservar el SDK junto al proyecto, copie **todo el contenido de la carpeta `lib`**
del paquete Win64, incluyendo sus subcarpetas y las DLL auxiliares, a
`LogicOTIBack/vendor/hikvision-sdk/lib/`. No basta con copiar `HCNetSDK.dll`:
el cargador necesita las dependencias que vienen con el SDK. Esta carpeta está
ignorada por Git. Actualice `CAMERA_MOTION_SDK_DLL` a la nueva ruta absoluta;
`CAMERA_MOTION_SDK_SCRIPT` continúa apuntando al script del repositorio.

La búsqueda histórica crea una ruta temporal de MediaMTX para cada salto. El
backend envía la misma hora al NVR en `starttime` y en el encabezado RTSP
`Range: clock`. Conviene comprobar la hora superpuesta en la imagen en una
grabación real: el momento efectivo de inicio depende del firmware del NVR.

El backend usa `NVR_USERNAME` y `NVR_PASSWORD` en el proceso auxiliar de PowerShell,
sin incluirlos en la línea de comandos ni enviar las DLL al navegador. Para una
búsqueda de grabaciones consulta también los eventos locales del canal digital que
informa el SDK. Los intervalos rojos se recortan a vídeo disponible; los segmentos
reproducibles siguen viniendo de ISAPI. Si la consulta SDK falla, la reproducción
continúa y la pantalla indica que faltan las marcas. Para intervalos muy extensos
el SDK puede tardar hasta el límite configurado; reduzca la ventana si aparece
ese aviso. Tras modificar `.env`, reinicie el backend y reconstruya el frontend.

El navegador recibe únicamente horario, duración, códec y tipo de grabación. La URI RTSP devuelta
por el NVR no se expone en la respuesta pública. Al solicitar reproducción, el backend valida de
nuevo cámara, track, host y periodo, y crea una ruta aleatoria de MediaMTX que vence automáticamente.
La API de control debe permanecer en `127.0.0.1:9997`; no debe publicarse en Caddy ni en el firewall.

MediaMTX requiere estas líneas en su archivo local `mediamtx.yml`:

```yaml
api: yes
apiAddress: 127.0.0.1:9997
```

`TWO_FACTOR_ENCRYPTION_KEY` protege los secretos TOTP de los usuarios. Si se omite, el backend
deriva una clave separada desde `JWT_SECRET`, pero en producción se recomienda configurarla
explícitamente y conservarla sin cambios. Perderla impide validar los códigos temporales ya inscritos.

Consulta `.env.example`. No copies `target/`, credenciales, direcciones reales de PLC ni archivos
`.env` al repositorio.

Para instalar o actualizar el backend como servicio automático de Windows se incluyen scripts
WinSW en [`deployment/windows`](deployment/windows). El servicio usa una copia local de `.env`,
registra logs rotativos y reinicia Java ante fallos.

## Seguridad por zonas e iluminación de áreas comunes

Las zonas `PB`, `P1`, `P2` y `PATIO` pueden armarse individualmente o en conjunto. El armado no
enciende luces: únicamente habilita las alarmas de movimiento de la zona. La automatización
**Encendido automático por movimiento** es global e independiente. Cualquier sensor de movimiento
válido, pertenezca a una zona armada o desarmada, puede encender exclusivamente los circuitos
seleccionados en **Iluminación de áreas comunes**, siempre que la automatización esté habilitada y
dentro de su horario. Esos circuitos se apagan al cumplirse el tiempo configurado sin actividad.

## Autenticación en dos pasos

Cada usuario puede activar TOTP desde **Mi seguridad** usando una aplicación autenticadora compatible.
La contraseña correcta no crea una sesión para cuentas con 2FA: primero se emite un desafío opaco de
cinco minutos y el JWT se entrega solo después de validar el segundo factor. Los códigos temporales son
de un solo uso por ventana, cada desafío admite cinco intentos y diez fallos consecutivos bloquean la
verificación durante quince minutos.

Al activar 2FA se entregan ocho códigos de recuperación de un solo uso. El backend guarda esos códigos
con BCrypt y cifra el secreto TOTP con AES-GCM. La migración `V29__create_user_two_factor_authentication.sql`
crea las tablas necesarias. Backend y frontend deben actualizarse juntos; véase
[la validación de 2FA](../VALIDACION_2FA.md).

El reconocimiento conserva armada la zona y no modifica la iluminación. La selección o disponibilidad
de circuitos de luz tampoco forma parte del precheck de armado.

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

La revisión de armado valida únicamente los sensores de movimiento de las zonas seleccionadas. El
patio no tiene sensores de movimiento, por lo que puede armarse pero no origina alarmas de movimiento.
Sus circuitos pueden seleccionarse de forma independiente en **Iluminación de áreas comunes**.
