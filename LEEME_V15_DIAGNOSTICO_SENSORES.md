# V15 - Diagnóstico y omisión temporal de sensores

Esta entrega agrega al backend:

- Diagnóstico de uno o varios sensores al mismo tiempo.
- Tiempo de diagnóstico configurable; actualmente 120 segundos.
- Resultado `PASSED`, `REJECTED` o `CANCELLED` guardado en PostgreSQL.
- Vigencia configurable; actualmente cuatro meses.
- Omisión temporal de sensores de movimiento por un administrador.
- Advertencia diaria mientras exista un sensor omitido.
- Armado con estado `ARMED_WITH_BYPASS` cuando hay omisiones activas.
- WebSocket para ver el avance del diagnóstico y las advertencias.

Los sensores de humo se pueden diagnosticar, pero nunca se pueden omitir.

## Instalación

1. Copiar el contenido del paquete sobre la raíz del repositorio.
2. Reiniciar el backend.
3. Flyway ejecutará automáticamente `V15__create_sensor_diagnostics_and_bypasses.sql` sobre la base indicada por `DB_URL`.
4. Verificar en PostgreSQL:

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version = '15';
```

## Prueba de diagnóstico

Crear una sesión como `ADMIN` u `OPERATOR`:

```http
POST /api/sensor-diagnostics
Authorization: Bearer TOKEN
Content-Type: application/json

{
  "sensorCodes": [
    "P1_A01_MOV01",
    "P1_A01_HUM01"
  ]
}
```

La respuesta incluye el `id` de la sesión y `expiresAt`. Durante los siguientes 120 segundos haga cambiar cada tag de estado en FactoryTalk Echo. Mantenga cada cambio por lo menos dos segundos para que el monitor alcance a leerlo.

Ejemplo:

- Movimiento: `OTI_P1_A01_MOV01_ST`.
- Humo: `OTI_P1_A01_HUM01_ALM`.

Al terminar la prueba, regrese los tags a `false`.

Consultar la sesión:

```http
GET /api/sensor-diagnostics/{id}
Authorization: Bearer TOKEN
```

Consultar sensores sin diagnóstico o con diagnóstico vencido:

```http
GET /api/sensor-diagnostics/due
Authorization: Bearer TOKEN
```

Cancelar una sesión como `ADMIN` u `OPERATOR`:

```http
POST /api/sensor-diagnostics/{id}/cancel
Authorization: Bearer TOKEN
```

El avance también se publica en:

```text
/topic/diagnostics/sensors/{id}
```

## Omisión temporal de un sensor

Sólo un `ADMIN` puede crear o retirar una omisión.

```http
POST /api/security/bypasses
Authorization: Bearer TOKEN_ADMIN
Content-Type: application/json

{
  "sensorCode": "P1_A01_MOV01",
  "reason": "Sensor pendiente de reemplazo por mantenimiento."
}
```

Consultar omisiones activas:

```http
GET /api/security/bypasses
Authorization: Bearer TOKEN
```

Retirar la omisión después de reparar y diagnosticar el sensor:

```http
DELETE /api/security/bypasses/P1_A01_MOV01
Authorization: Bearer TOKEN_ADMIN
```

Consultar advertencias guardadas:

```http
GET /api/security/warnings?limit=50
Authorization: Bearer TOKEN
```

Las advertencias nuevas también se publican en:

```text
/topic/security/warnings
```

## Reglas de armado

- Un sensor de movimiento sin diagnóstico aprobado bloquea el armado.
- Un diagnóstico vencido después de cuatro meses bloquea el armado.
- Un sensor de movimiento omitido no bloquea, pero genera una advertencia.
- Si existe una omisión, el modo será `ARMED_WITH_BYPASS`.
- El PLC deshabilitado o desconectado continúa bloqueando el armado.
- Los sensores de humo nunca se pueden omitir.
