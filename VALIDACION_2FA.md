# Configuración y validación de autenticación en dos pasos

LogicOTI admite TOTP opcional por usuario. Funciona con Google Authenticator, Microsoft Authenticator,
1Password y aplicaciones compatibles con RFC 6238. Las cuentas existentes continúan accediendo solo
con contraseña hasta que cada usuario active 2FA desde **Mi seguridad**.

## Preparación del servidor

Backend y frontend deben actualizarse juntos. Al iniciar, Flyway aplica
`V29__create_user_two_factor_authentication.sql`.

En PowerShell genera una clave independiente:

```powershell
$twoFactorBytes = New-Object byte[] 64
$twoFactorGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
$twoFactorGenerator.GetBytes($twoFactorBytes)
$twoFactorKey = [Convert]::ToBase64String($twoFactorBytes)
$twoFactorGenerator.Dispose()
$twoFactorKey
```

Guarda el resultado únicamente en el `.env` del backend:

```properties
TWO_FACTOR_ENCRYPTION_KEY=<CLAVE_GENERADA>
TWO_FACTOR_ISSUER=LogicOTI
TWO_FACTOR_CHALLENGE_EXPIRATION_SECONDS=300
TWO_FACTOR_CHALLENGE_MAX_ATTEMPTS=5
TWO_FACTOR_ACCOUNT_MAX_FAILURES=10
TWO_FACTOR_ACCOUNT_LOCK_SECONDS=900
```

No cambies ni pierdas `TWO_FACTOR_ENCRYPTION_KEY` después de que un usuario active 2FA. No debe
subirse a GitHub, copiarse al frontend ni incluirse en capturas. Reinicia `LogicOTIBackend` después
de modificar el `.env`.

## Activación por usuario

1. Inicia sesión normalmente.
2. Abre **Mi seguridad** en el menú.
3. Confirma la contraseña actual.
4. Escanea el QR en la aplicación autenticadora.
5. Escribe el código temporal de seis dígitos.
6. Guarda los ocho códigos de recuperación fuera del servidor y del teléfono.
7. Cierra sesión.

## Casos de aceptación

| Prueba | Resultado esperado |
| --- | --- |
| Contraseña correcta con 2FA activo | No abre la aplicación; solicita el segundo factor. |
| Código TOTP vigente | Inicia sesión y crea el JWT. |
| Reutilizar inmediatamente el mismo TOTP | Rechazo; cada paso temporal se acepta una sola vez. |
| Código incorrecto | Rechazo sin crear sesión; el desafío permite cinco intentos. |
| Esperar más de cinco minutos | El desafío vence y obliga a escribir usuario y contraseña otra vez. |
| Código de recuperación | Inicia sesión y reduce en uno el contador disponible. |
| Reutilizar el código de recuperación | Rechazo; funciona una sola vez. |
| Desactivar 2FA | Exige contraseña actual y TOTP o código de recuperación. |
| Usuario sin 2FA | Mantiene el inicio de sesión actual con usuario y contraseña. |

La hora del servidor y del teléfono debe estar sincronizada automáticamente. TOTP usa intervalos de
30 segundos y tolera un intervalo anterior o posterior para desfases pequeños.
