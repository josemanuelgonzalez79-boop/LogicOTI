# Plantilla Spring Boot industrial

Base reutilizable para APIs con PostgreSQL y comunicación industrial.

## Versiones

- Java 21
- Spring Boot 4.1.0
- Maven 3.9.16 mediante Maven Wrapper
- SpringDoc OpenAPI 3.0.3
- Apache PLC4X 0.13.1
- PostgreSQL 18 para desarrollo con Docker

## Incluye

- Spring Web MVC modular de Spring Boot 4.
- Validación Jakarta.
- JPA/Hibernate y PostgreSQL.
- Flyway para versionar la base de datos.
- Actuator.
- Swagger/OpenAPI.
- PLC4X con driver EtherNet/IP.
- Perfiles `dev`, `prod` y `test`.
- CORS configurable.
- Errores en formato `ProblemDetail`.
- Ejemplo CRUD de equipos.
- Dockerfile y `compose.yaml`.

## Iniciar PostgreSQL

```powershell
docker compose up -d
```

## Ejecutar backend

```powershell
.\mvnw.cmd spring-boot:run
```

La API inicia en `http://localhost:3210`.

- Estado: `GET http://localhost:3210/api/system/status`
- Equipos: `GET http://localhost:3210/api/equipment`
- Swagger: `http://localhost:3210/swagger-ui.html`
- Health: `http://localhost:3210/actuator/health`

## Compilar y probar

```powershell
.\mvnw.cmd clean verify
```

## Seguridad

La plantilla no impone un método de autenticación. Agrega Spring Security cuando el proyecto defina si utilizará JWT, Microsoft Entra ID, sesiones u otro proveedor. Esto evita dejar una configuración de seguridad falsa o incompleta como base.

## Producción

No guardes contraseñas en `application-prod.yml`. Configura al menos:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://servidor:5432/base
DB_USERNAME=usuario
DB_PASSWORD=contraseña
FRONTEND_ORIGIN=https://tu-frontend
```

Consulta `.env.example` para ver todas las variables disponibles.

## Cambiar el nombre para un proyecto nuevo

1. Cambia `artifactId`, `name` y `description` en `pom.xml`.
2. Renombra `IndustrialBackendApplication` si lo deseas.
3. Refactoriza el paquete `com.icap.template` desde el IDE.
4. Cambia `APP_NAME` y el nombre de la base de datos.
5. Elimina el módulo de ejemplo `equipment` cuando ya no lo necesites.

No copies `target/`, credenciales, direcciones reales de PLC ni archivos `.env` al repositorio.
