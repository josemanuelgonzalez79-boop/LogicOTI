# LogicOTI

Plataforma web para supervisar y operar la automatización del edificio OTI de ICAP.

## Funciones principales

- Dashboard e inventario por piso y área.
- Lectura y control de iluminación y encendido/apagado de minisplits mediante PLC EtherNet/IP.
- Monitoreo de sensores de movimiento y humo con calidad `GOOD`, `BAD` y `STALE`.
- Alarmas, reconocimiento, comentarios, armado, horarios y omisiones temporales.
- Cámaras mediante MediaMTX/WebRTC, incluidas vistas temporales desde alertas.
- Actualización en tiempo real con WebSocket/STOMP.
- PWA instalable y notificaciones Web Push con bitácora y reintentos.
- Diagnósticos de sensores, históricos, reporte ejecutivo mensual, PDF y retención automática.
- Administración de usuarios y roles.

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

Nunca se deben subir `.env`, claves VAPID, secretos JWT, contraseñas, certificados privados ni
credenciales del NVR.
