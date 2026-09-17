# Comprobación de diagnósticos e iluminación por zonas

Cambios preparados sobre `desarrollo` en `c715bfc`, conservando las correcciones anteriores de
WebSocket, tags exteriores y SonarQube. Estas comprobaciones de instalación complementan las pruebas
automáticas; requieren acceso al PLC/Echo y a la aplicación instalada.

## Actualización

1. Actualizar backend y frontend juntos. Al arrancar, Flyway aplica V28; no editar migraciones anteriores.
2. Revisar en Seguridad los circuitos seleccionados en **Encendido automático por movimiento** y guardar.
3. Si hay zonas armadas o luces bajo control de la versión anterior, desarmarlas antes de la prueba.
   Armar no apaga luces manuales; comenzar con los circuitos de prueba apagados.
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
| Detectar movimiento en Piso 1 armado | Se encienden únicamente las luces seleccionadas de Piso 1 y Patio. |
| Dejar una luz sin seleccionar o su zona desarmada | No recibe encendido por esa alarma. |
| Armar una zona sin luces seleccionadas | Aviso informativo; armado permitido si pasa el resto de la revisión. |
| Reconocer la única alarma | Apaga las luces encendidas por seguridad; las zonas permanecen armadas. |
| Mantener el sensor activo tras reconocer | El automatismo por horario no vuelve a encender las luces de las zonas armadas. |
| Restablecer el sensor y activarlo de nuevo | Nueva alarma y nuevo encendido de los circuitos seleccionados. |
| Tener varias zonas en alarma | Mantiene la iluminación de seguridad mientras quede una alarma sin reconocer. |
| Luz ya encendida manualmente antes de la alarma | No se toma bajo control de seguridad ni se apaga al reconocer. |

El interruptor y horario de encendido automático siguen disponibles para zonas desarmadas. Las
alarmas usan la misma lista de circuitos a cualquier hora. Para que el edificio permanezca apagado
también en zonas desarmadas, deshabilitar el encendido por horario cuando no se necesite.

## Pendientes siguientes

- Validar estas dos correcciones en PLC/Echo y en la instalación.
- Diseñar inicio de sesión en dos pasos: método, enrolamiento y recuperación de acceso.
- Identificar modelo y firmware de NVR/cámaras para evaluar histórico y eventos de movimiento.
- Confirmar modelo de UPS y tarjeta NMC compatible; definir acceso a red y datos disponibles.
- Definir router/SIM, cobertura y alimentación de respaldo para avisos durante cortes eléctricos.
- Instalar el sensor óptico del portón y confirmar su señal/tag antes de implementar avisos de llegada.
