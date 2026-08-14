# Alcance y levantamiento de LogicOTI

## Fuente física original

El archivo `Levantamiento_Proyecto_Automatizacion_OTI(2).xlsx` registra la infraestructura física
del edificio. Sus cantidades originales son:

| Concepto               | Cantidad |
| ---------------------- | -------: |
| Áreas con inventario   |       36 |
| Lámparas               |      170 |
| Sensores de movimiento |       28 |
| Sensores de puerta     |       12 |
| Sensores de humo       |       28 |
| Contactos eléctricos   |       99 |
| Apagadores             |       42 |
| Minisplits             |       26 |

La hoja `EXTERIOR` no contiene cantidades; las cámaras exteriores pertenecen al catálogo CCTV y no
se deducen de esta hoja.

## Ajustes operativos posteriores

El levantamiento es una referencia física, no una lista automática de tags PLC. Las decisiones
posteriores del proyecto prevalecen:

- `P1_A07` (Carlos), `P1_A08` (César) y `P1_A09` (Hernán) están desactivadas.
- Los sensores de puerta quedaron fuera del alcance y no se muestran ni monitorean.
- Los contactos y apagadores se muestran solamente como cantidades de inventario. No se generan
  dispositivos, órdenes ni tags PLC para ellos.
- Las 170 lámparas son unidades físicas; no equivalen a 170 salidas PLC. LogicOTI controla los
  circuitos de iluminación que fueron definidos expresamente.
- Los minisplits solo permiten encendido y apagado. Temperatura, modo y velocidad quedan pendientes
  hasta contar con infraestructura compatible.
- WhatsApp no forma parte del proyecto; los avisos remotos se realizan mediante Web Push.

Después de desactivar las tres oficinas, el catálogo operativo esperado contiene 33 áreas, 25
sensores de movimiento, 25 sensores de humo y 23 minisplits. Por eso el diagnóstico muestra 50
sensores monitoreados: 25 de movimiento más 25 de humo.

## Funciones dentro del alcance

- Control manual de circuitos de iluminación y encendido/apagado de minisplits.
- Automatización nocturna de las luces seleccionadas y apagado por inactividad.
- Alarmas de humo y movimiento con armado manual o programado.
- Reconocimiento, comentarios y trazabilidad de alarmas.
- Omisiones temporales y diagnóstico periódico de sensores.
- Calidad individual de señal `GOOD`, `BAD` y `STALE`.
- Cámaras en vivo y enlace temporal asociado a alertas de movimiento.
- WebSocket para actualización en tiempo real.
- PWA y Web Push para avisos aun cuando la aplicación no está abierta.
- Históricos, reporte ejecutivo mensual, exportación PDF y retención automática.
- Administración de usuarios con roles `ADMIN`, `OPERATOR` y `MONITORING`.

## Fuera del alcance actual

- Control de contactos eléctricos o apagadores físicos.
- Sensores de puerta.
- Ajuste de temperatura, modo o velocidad de minisplits.
- Grabación o almacenamiento de video dentro de LogicOTI.
- Acceso desde Internet sin la red/VPN y las medidas de seguridad correspondientes.
