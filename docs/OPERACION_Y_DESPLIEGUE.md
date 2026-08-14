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

## Instalar el backend como servicio de Windows

El repositorio incluye una plantilla WinSW y dos scripts en
`LogicOTIBack/deployment/windows`. El servicio se instala como `LogicOTIBackend`, inicia de forma
automática retrasada y WinSW intenta recuperarlo a los 10, 30 y 60 segundos si Java termina con
error.

Antes de instalar:

1. Confirma que `LogicOTIBack/.env` contiene la configuración real y no está versionado.
2. Confirma que Java está disponible con `java -version`.
3. Conserva `C:\Caddy\caddy-service.exe`; es el ejecutable WinSW que ya utiliza Caddy y se puede
   copiar con otro nombre para el backend.
4. Detén el backend que se esté ejecutando desde VS Code para liberar el puerto `3210`.

Abre PowerShell como administrador y ejecuta:

```powershell
Set-Location 'C:\Users\Erick Emerich\Desktop\LogicOTI\LogicOTIBack'

.\deployment\windows\install-backend-service.ps1
```

El instalador ejecuta `mvnw.cmd clean verify`, copia el JAR y una copia inicial de `.env` a
`C:\LogicOTI\Backend`, genera la configuración WinSW con la ruta real de Java e inicia el servicio.
No sobrescribe un servicio ya instalado.

Comprueba el resultado:

```powershell
Get-Service LogicOTIBackend

Invoke-RestMethod `
    -Uri 'http://127.0.0.1:3210/actuator/health'
```

Los logs quedan en `C:\LogicOTI\Backend\logs`. La configuración efectiva del servicio queda en
`C:\LogicOTI\Backend\.env`; edita esa copia cuando cambie una variable de producción y reinicia el
servicio:

```powershell
Restart-Service LogicOTIBackend
```

Para publicar una versión nueva del backend, después de actualizar el repositorio ejecuta:

```powershell
Set-Location 'C:\Users\Erick Emerich\Desktop\LogicOTI\LogicOTIBack'

.\deployment\windows\update-backend-service.ps1
```

Ese script prueba y compila el proyecto, detiene el servicio, reemplaza solamente el JAR y vuelve a
iniciarlo. La copia de `.env` instalada no se modifica.

## Crear administradores de forma segura

Las cuentas administrativas deben ser visibles en **Usuarios** y crearse desde una sesión `ADMIN`.
No se deben insertar usuarios ocultos mediante Flyway, código de arranque o SQL versionado: eso
dejaría una credencial permanente en todos los entornos y eliminaría la trazabilidad operativa.

Para dar de alta un administrador secundario:

1. Inicia sesión con el administrador actual.
2. Abre **Usuarios** y selecciona **Nuevo usuario**.
3. Captura el nombre de usuario, nombre completo, rol `ADMIN` y estado activo.
4. Usa una contraseña única de al menos 16 caracteres y guárdala en un administrador de
   contraseñas.
5. Cierra sesión e inicia con la cuenta nueva para comprobarla.

Una contraseña predecible o reutilizada no debe emplearse para una cuenta `ADMIN`, aunque cumpla el
mínimo técnico de ocho caracteres.

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
