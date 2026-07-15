# Validación de la plantilla

Validada el 15 de julio de 2026 con:

- OpenJDK 21.0.10.
- Apache Maven 3.9.16.
- Compilación y generación del JAR ejecutable: correcta.
- Prueba de contexto Spring Boot con perfil `test` y H2: 1 prueba aprobada.

Comando recomendado en cada proyecto nuevo:

```powershell
.\mvnw.cmd clean verify
```
