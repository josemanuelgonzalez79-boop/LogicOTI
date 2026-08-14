# Operación y despliegue de LogicOTI

## Dependencias de producción

1. PostgreSQL conserva configuración, usuarios, eventos e históricos.
2. El backend Spring Boot se conecta a PostgreSQL y al PLC.
3. Tomcat entrega el build Angular bajo `/LogicOTI/`.
4. MediaMTX convierte los streams RTSP del NVR a WebRTC/WHEP.
5. Caddy entrega HTTPS y funciona como punto de entrada para frontend, API, WebSocket y video.

Cuando todos están instalados como servicios automáticos de Windows, el orden normal de arranque no
requiere intervención. Si se levantan manualmente para diagnóstico, usa: PostgreSQL, backend,
Tomcat, MediaMTX y Caddy. MediaMTX puede iniciar en paralelo; Caddy conviene dejarlo al final porque
depende de los destinos anteriores.

## Variables de producción

Copia `LogicOTIBack/.env.example` como `LogicOTIBack/.env` y configura localmente, sin subirlo a Git:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`
- `PLC_ENABLED=true`, `PLC_CONNECTION_STRING` y `PLC_TIMEOUT`
- `FRONTEND_ORIGIN` y `FRONTEND_ORIGIN_ALT`
- `CAMERAS_ENABLED`, `CAMERA_PLAYBACK_BASE_URL` y `CAMERA_AVAILABLE_STREAMS`
- `WEB_PUSH_ENABLED`, claves VAPID y `WEB_PUSH_SUBJECT`
- `SWAGGER_ENABLED=false`
- `JWT_SECRET`

Para generar un secreto JWT en Windows PowerShell:

```powershell
$bytes = New-Object byte[] 64
$generator = [Security.Cryptography.RandomNumberGenerator]::Create()
$generator.GetBytes($bytes)
$secret = [Convert]::ToBase64String($bytes)
$generator.Dispose()
$secret
```

Copia el resultado completo en `JWT_SECRET`. No reutilices la clave VAPID, una contraseña ni el
valor predeterminado de desarrollo.

## Actualizar backend

Antes de actualizar, respalda PostgreSQL. Después:

```powershell
Set-Location 'C:\ruta\LogicOTI\LogicOTIBack'
.\mvnw.cmd clean verify
.\mvnw.cmd clean package -DskipTests
```

Detén el proceso o servicio del backend, reemplaza el JAR por el nuevo artefacto de `target`, inicia
el servicio y revisa sus logs. Flyway aplica automáticamente las migraciones pendientes; nunca
edites una migración que ya fue aplicada en producción.

## Actualizar frontend

```powershell
Set-Location 'C:\ruta\LogicOTI\LogicOTIFront'
npm ci
npx ng test --watch=false
npx ng build --configuration production --base-href /LogicOTI/
```

1. Detén Tomcat.
2. Conserva una copia de la carpeta desplegada actualmente.
3. Sustituye el contenido de `webapps\LogicOTI` por el contenido de
   `dist\industrial-frontend-template\browser`.
4. Inicia Tomcat.
5. Abre `https://servidor/LogicOTI/` y acepta la actualización cuando la PWA la anuncie.

No uses el puerto `8080` desde el celular: omitir Caddy elimina el HTTPS requerido por Web Push y
puede provocar contenido mixto.

## Comprobaciones después de desplegar

- `https://servidor/LogicOTI/` responde y permite iniciar sesión.
- La petición `/api/auth/login` pasa por HTTPS y no devuelve `502`/`504`.
- El indicador WebSocket cambia a conectado.
- Control muestra lectura PLC y confirma órdenes.
- Cámaras abre un stream y no genera errores WHEP `502`, `405` o `501`.
- “Enviar notificación de prueba” registra una entrega `ACCEPTED` con HTTP `201`.
- GitHub Actions termina correctamente para backend y frontend.

Un `401` en un endpoint protegido antes de iniciar sesión puede ser normal. Un `502` o `504` indica
que Caddy no puede alcanzar el backend, Tomcat o MediaMTX.

## Instalar la PWA en iOS

1. Conecta el teléfono a la red permitida o VPN.
2. Instala y marca como confiable el certificado raíz de Caddy cuando se utilice `tls internal`.
3. Abre `https://servidor/LogicOTI/` en un navegador compatible.
4. Usa **Compartir > Añadir a pantalla de inicio**.
5. Abre LogicOTI desde el icono instalado, inicia sesión y pulsa **Activar en este equipo**.
6. Autoriza notificaciones para LogicOTI y habilita pantalla bloqueada, centro de notificaciones,
   tiras y sonidos.

La suscripción pertenece a la PWA instalada. Habilitar notificaciones solamente para la pestaña del
navegador no sustituye la activación dentro de LogicOTI.

## Instalar la PWA en Android

1. Abre `https://servidor/LogicOTI/` en Chrome.
2. En el menú selecciona **Instalar aplicación** o **Añadir a pantalla principal**.
3. Abre LogicOTI desde su icono, inicia sesión y pulsa **Activar en este equipo**.
4. Permite notificaciones y excluye LogicOTI del ahorro de batería si el fabricante restringe la
   recepción en segundo plano.

## Respaldo y retención

- Ejecuta `pg_dump` periódicamente y conserva al menos una copia fuera de la computadora del
  servidor.
- Prueba la restauración; un archivo no verificado no constituye un respaldo confiable.
- La retención de históricos se ejecuta diariamente a las 03:30 cuando está habilitada.
- La limpieza protege diagnósticos en ejecución, omisiones activas y notificaciones pendientes.
- “Limpiar ahora” elimina inmediatamente los candidatos mostrados, usando las mismas reglas.
