# FRONTEND ARCHITECTURE
## LogicOTI - Sistema de Automatización del Edificio

---

# 1. Objetivo

El frontend de LogicOTI tiene como objetivo representar de manera gráfica el estado del edificio automatizado, permitiendo al usuario supervisar sensores, dispositivos y alarmas mediante una interfaz web desarrollada con Angular.

El sistema está diseñado para ser modular, escalable, mantenible y reutilizable, facilitando futuras ampliaciones sin afectar la arquitectura existente.

---

# 2. Tecnologías

El proyecto utiliza las siguientes tecnologías:

- Angular 21
- TypeScript
- PrimeNG
- PrimeIcons
- SCSS
- Spring Boot
- REST API
- PLC Rockwell
- OPC UA
- Git
- GitHub

---

# 3. Arquitectura General

```
Usuario
    │
    ▼
Angular Frontend
    │
    ▼
Spring Boot REST API
    │
    ▼
Gateway PLC
    │
    ▼
PLC Rockwell
```

El frontend consume los servicios REST proporcionados por Spring Boot, quien se encarga de la comunicación con el PLC mediante OPC UA.

---

# 4. Arquitectura del Frontend

```
App
│
└── Shell
      │
      ├── Navbar
      ├── Sidebar
      └── Router Outlet
              │
              ▼
      Dashboard
      Building
      Control
      Alarmas
      Históricos
      Diagnóstico
      Administración
```

El **Shell** funciona como contenedor principal de la aplicación. Todas las vistas se renderizan dentro del `router-outlet`, garantizando una experiencia de navegación uniforme.

---

# 5. Filosofía del Proyecto

El sistema representa el edificio y sus elementos físicos, no únicamente un conjunto de pantallas.

Cada elemento físico tiene una representación digital dentro de la aplicación.

Ejemplos:

- Edificio
- Piso
- Área
- Sensor
- Dispositivo
- Contacto eléctrico
- Luminaria
- Minisplit
- Cámara
- Usuario
- Alarma

Los componentes deberán ser reutilizables y orientados a datos.

No se crearán componentes específicos para cada oficina o dispositivo.

---

# 6. Flujo de Navegación

```
Dashboard
      │
      ▼
Building
      │
      ▼
Floor
      │
      ▼
Area
      │
      ▼
Dispositivos
```

La navegación refleja la estructura física del edificio para facilitar la supervisión y el control.

---

# 7. Estructura del Proyecto

```
src
│
└── app
      │
      ├── core
      ├── layout
      │      ├── shell
      │      ├── navbar
      │      └── sidebar
      │
      ├── shared
      │
      └── features
             ├── dashboard
             ├── building
             ├── control
             ├── alarms
             ├── history
             ├── diagnostics
             ├── administration
             └── auth
```

La aplicación sigue una arquitectura basada en funcionalidades (*Feature-Based Architecture*).

---

# 8. Rutas

```
/

↓

dashboard

building

building/floor/:floorId

building/floor/:floorId/area/:areaId

control

alarms

history

diagnostics

administration

**
```

Las rutas están implementadas mediante Angular Router utilizando Lazy Loading.

---

# 9. Modelo del Sistema

```
Building
│
├── Floor
│      ├── Planta Baja
│      ├── Piso 1
│      └── Piso 2
│
└── Area
        │
        ├── Sensores
        ├── Luminarias
        ├── Contactos
        ├── Minisplits
        ├── Cámaras
        ├── Alarmas
        └── Dispositivos
```

Este modelo representa la estructura lógica utilizada por el frontend para visualizar el edificio.

---

# 10. Convenciones

## Componentes

- DashboardComponent
- BuildingComponent
- FloorComponent
- AreaComponent

## Carpetas

- core
- layout
- shared
- features

## Servicios

Todos los servicios deberán terminar con el sufijo `Service`.

Ejemplo:

- AlarmService
- BuildingService
- AuthenticationService

## Componentes

Todos los componentes deberán terminar con el sufijo `Component`.

Ejemplo:

- DashboardComponent
- AreaComponent

## Interfaces

Las interfaces deberán ubicarse en una carpeta específica por módulo o dentro de `shared/interfaces`.

## Estilos

Cada componente deberá mantener sus propios archivos:

- HTML
- SCSS
- TypeScript

---

# 11. Principios Arquitectónicos

Durante el desarrollo del frontend deberán respetarse los siguientes principios:

- Arquitectura basada en componentes reutilizables.
- Componentes Standalone.
- Separación de responsabilidades.
- Comunicación mediante servicios.
- Uso de Lazy Loading.
- Organización por funcionalidades.
- Evitar duplicidad de código.
- Mantener una navegación consistente.
- Favorecer la escalabilidad del sistema.
- Documentar las decisiones arquitectónicas relevantes.

---

# 12. Decisiones Arquitectónicas

## ADR-001 - Arquitectura Standalone

**Fecha:** 16/07/2026

### Decisión

Se adopta Angular Standalone como base del proyecto.

### Justificación

Reduce la complejidad del proyecto, elimina la dependencia de NgModules y facilita la reutilización de componentes.

---

## ADR-002 - Modelo jerárquico del edificio

**Fecha:** 16/07/2026

### Decisión

El edificio se representará mediante una jerarquía:

```
Building
    ↓
Floor
    ↓
Area
```

### Justificación

Esta estructura refleja la organización física del edificio y evita crear componentes independientes para cada oficina.

---

## ADR-003 - Layout único

**Fecha:** 16/07/2026

### Decisión

Toda la aplicación utilizará un único Shell compuesto por:

- Navbar
- Sidebar
- Router Outlet

### Justificación

Mantener una experiencia uniforme de navegación y simplificar el mantenimiento del sistema.

---

# Documento Vivo

Este documento constituye la documentación técnica oficial de la arquitectura del frontend de LogicOTI.

Toda decisión que modifique la estructura, el funcionamiento o la organización del sistema deberá registrarse mediante una nueva **ADR (Architecture Decision Record)** para mantener un historial de la evolución arquitectónica del proyecto.