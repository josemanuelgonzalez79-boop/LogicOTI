# LogicOTI

Plataforma web para supervisar y operar la automatización del edificio OTI de ICAP.

## Funciones principales

- Dashboard e inventario por piso y área.
- Lectura y control de iluminación y encendido/apagado de minisplits mediante PLC EtherNet/IP.
- Monitoreo de sensores de movimiento y humo con calidad `GOOD`, `BAD` y `STALE`.
- Alarmas, reconocimiento, comentarios, armado, horarios y omisiones temporales.
- Cámaras mediante MediaMTX/WebRTC, incluidas vistas temporales desde alertas, búsqueda de
  grabaciones del NVR y reproducción histórica con sesiones temporales.
- Actualización en tiempo real con WebSocket/STOMP.
- PWA instalable y notificaciones Web Push con bitácora y reintentos.
- Diagnósticos de sensores, históricos, reporte ejecutivo mensual, PDF y retención automática.
- Administración de usuarios y roles, con autenticación TOTP en dos pasos opcional por cuenta.

Los contactos eléctricos y apagadores se conservan únicamente como inventario físico. No forman
parte del control PLC. Los minisplits solamente admiten energizar y desenergizar; temperatura y modo
quedan fuera del alcance actual.

## Arquitectura

| Componente    | Tecnología               | Uso                                            |
| ------------- | ------------------------ | ---------------------------------------------- |
| Frontend      | Angular 21, PrimeNG 21   | SPA/PWA desplegada bajo `/LogicOTI/`           |
| Backend       | Java 21, Spring Boot 4.1 | API REST, WebSocket, seguridad, PLC y Web Push |
| Base de datos | PostgreSQL               | Catálogo, configuración, eventos e históricos  |
| PLC           | Apache PLC4X EtherNet/IP | Lectura de señales y comandos BOOL             |
| Video         | MediaMTX                 | Conversión de RTSP a WebRTC/WHEP               |
| Servidor web  | Tomcat 9 y Caddy         | Archivos Angular, HTTPS y proxy inverso        |

En producción el navegador utiliza un solo origen HTTPS. Caddy recibe `/api`, `/ws`, las rutas de
cámara y la aplicación; así evita contenido mixto y problemas de CORS.

La búsqueda histórica se realiza desde el backend contra ISAPI con autenticación Digest. Las
credenciales y las URI RTSP nunca se entregan al navegador. Para reproducir, el backend crea una
ruta aleatoria y temporal en la API local de MediaMTX; esa API escucha únicamente en loopback.
El histórico permite saltar a una hora dentro del segmento: el backend verifica de nuevo que esa
hora esté grabada y crea otra ruta temporal desde el punto elegido. El stream WebRTC no dispone de
búsqueda continua mediante los controles nativos del video.

El control PTZ se habilita por separado con `CAMERA_PTZ_ENABLED=true` en el `.env` del backend.
Solo aparece en vivo para los canales indicados por `CAMERA_PTZ_CHANNELS` (por defecto 25–28)
y para usuarios ADMIN u OPERATOR. El backend envía una orden ISAPI al NVR, espera 350 ms y envía
una orden de parada; no transmite credenciales al navegador. Utiliza `NVR_BASE_URL` y, salvo
que se indiquen `CAMERA_PTZ_USERNAME` y `CAMERA_PTZ_PASSWORD`, las credenciales `NVR_USERNAME`
y `NVR_PASSWORD`. Si el firmware no admite la ruta directa, se puede configurar
`CAMERA_PTZ_ENDPOINT_MODE=proxy`. Requiere verificar en el NVR el permiso de movimiento de esa
cuenta y probar un pulso corto con visión directa de la cámara. Si no se detiene, deshabilita
PTZ y detén la cámara desde el NVR; la entrega de la orden de parada depende de la conexión.

## Carpetas

- `LogicOTIBack`: backend Spring Boot y migraciones Flyway.
- `LogicOTIFront`: frontend Angular/PWA.
- `MediaMTX`: archivos de ejemplo para el servicio de video. La configuración real y sus
  credenciales no se versionan.
- `docs`: alcance confirmado y guía de operación/despliegue.

## Desarrollo

Backend:

```powershell
Set-Location .\LogicOTIBack
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
Set-Location .\LogicOTIFront
npm ci
npm start
```

El frontend abre en `http://localhost:4200`; su proxy envía `/api` y `/ws` al backend en el puerto
`3210`.

## Validación

```powershell
Set-Location .\LogicOTIBack
.\mvnw.cmd clean verify

Set-Location ..\LogicOTIFront
npm ci
npx ng test --watch=false
npx ng build --configuration production --base-href /LogicOTI/
```

GitHub Actions ejecuta esas validaciones en cada actualización de `desarrollo` y en los Pull
Requests hacia `desarrollo` o `main`.

## Documentación

- [Alcance y levantamiento](docs/ALCANCE_Y_LEVANTAMIENTO.md)
- [Operación y despliegue](docs/OPERACION_Y_DESPLIEGUE.md)
- [Cambios de la versión](CHANGELOG.md)
- [Validación de autenticación en dos pasos](VALIDACION_2FA.md)

Nunca se deben subir `.env`, claves VAPID, secretos JWT, contraseñas, certificados privados ni
credenciales del NVR.
