# Comprobación de diagnósticos, zonas e iluminación común

Estas comprobaciones de instalación complementan las pruebas automáticas y requieren acceso al
PLC/Echo y a la aplicación instalada. La comprobación física y la actualización del servidor siguen
pendientes hasta que esta versión quede aprobada en GitHub Actions.

## Actualización

1. Actualizar backend y frontend juntos. Al arrancar, Flyway aplica V28; no editar migraciones anteriores.
2. Revisar en Seguridad los circuitos seleccionados en **Iluminación de áreas comunes** y guardar.
3. Comenzar con los circuitos de prueba apagados. La migración V30 elimina cualquier propiedad de
   luces conservada por la lógica anterior de alarmas por zona.
4. No hacen falta nuevos tags PLC ni cambios de `.env` para estas correcciones.

## Diagnóstico

Usar Echo o el procedimiento y medios de prueba indicados por el fabricante del sensor. La exclusión
de avisos aplica a LogicOTI y no desactiva una sirena o protección que opere directamente desde el PLC
o una central de incendio. Confirmar esa conexión antes de una prueba física de humo.

| Comprobación | Resultado esperado |
| --- | --- |
| Seleccionar un sensor que ya está activo e iniciar | Rechazo; primero debe estar en reposo. |
| Iniciar y activar un sensor seleccionado | Sigue en prueba, esperando restablecimiento. Eventos de diagnóstico en historial, sin alarma ni Web Push de LogicOTI. |
| Activar otro sensor no seleccionado | Vigilancia y avisos normales. El diagnóstico no afecta al edificio completo. |
| Restablecer el sensor de prueba | Aprobado; vigilancia normal reanudada para ese sensor. |
| Probar varios y restablecer solo uno | Ese sensor termina; los demás siguen en prueba hasta terminar o vencer su tiempo. |
| Cancelar o dejar vencer con sensor activo | En la siguiente lectura válida aparece alarma real. No queda una omisión permanente. |
| Reiniciar backend con una prueba vencida y sensor activo | Se recupera el estado y se genera la alarma real. |
| Diagnosticar movimiento | Sin alarma de intrusión ni encendido/renovación de automatismos por esa señal de prueba. |

## Iluminación

| Comprobación | Resultado esperado |
| --- | --- |
| Seleccionar una luz de Piso 1 y una del Patio y armar ambas zonas | Ningún encendido al armar ni al terminar el tiempo de salida. |
| Detectar movimiento en Piso 1 armado dentro del horario | Alarma de Piso 1 y encendido de todas las luces seleccionadas globalmente, incluidas las del Patio. |
| Armar solo Planta Baja y activar movimiento de Piso 2 dentro del horario | No se genera alarma de Piso 2; sí se encienden las luces seleccionadas globalmente. |
| Activar movimiento fuera del horario o con la automatización deshabilitada | No se encienden luces, sin importar qué zonas estén armadas. |
| Dejar una luz sin seleccionar | No recibe comandos de la automatización, aunque su zona esté armada. |
| Armar una zona sin luces seleccionadas | Armado permitido; la iluminación no forma parte del precheck. |
| Reconocer una alarma | La zona permanece armada y la iluminación no cambia por el reconocimiento. |
| Cumplir el tiempo sin actividad | Se apagan solo las luces que encendió la automatización. |
| Luz ya encendida manualmente antes del movimiento | No se toma bajo control automático ni se apaga al vencer el tiempo. |

El interruptor, horario, selección de circuitos y tiempo sin actividad son globales. El estado armado
o desarmado solo decide si el movimiento genera una alarma; no cambia el comportamiento de luces.

## Pendientes siguientes

- Validar estas dos correcciones en PLC/Echo y en la instalación.
- Validar el inicio de sesión TOTP y la recuperación de acceso descritos en `VALIDACION_2FA.md`.
- Identificar modelo y firmware de NVR/cámaras para evaluar histórico y eventos de movimiento.
- Confirmar modelo de UPS y tarjeta NMC compatible; definir acceso a red y datos disponibles.
- Definir router/SIM, cobertura y alimentación de respaldo para avisos durante cortes eléctricos.
- Instalar el sensor óptico del portón y confirmar su señal/tag antes de implementar avisos de llegada.
