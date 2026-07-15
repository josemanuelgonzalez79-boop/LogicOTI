# Plantilla Angular industrial

Base reutilizable para proyectos Angular conectados con un backend Spring Boot.

## Versiones

- Angular 21.2.x
- PrimeNG 21.1.9
- TypeScript 5.9.x
- RxJS 7.8.x
- Node.js 24 LTS recomendado (Node.js 22.12+ también es compatible)

Se eligió Angular 21 porque es la versión más reciente compatible con la última versión estable de PrimeNG. Angular 22 puede adoptarse cuando PrimeNG 22 tenga una versión estable.

## Requisitos

```powershell
node -v
npm -v
```

Para proyectos nuevos usa Node.js 24 LTS. La plantilla también admite Node.js 22.12 o superior dentro de la rama 22.

## Iniciar

```powershell
npm ci
npm start
```

La aplicación abre en `http://localhost:4200` y el proxy envía `/api` hacia `http://localhost:3210`.

## Compilar

```powershell
npm run build
```

El resultado queda en `dist/industrial-frontend-template/browser`.

## Antes de comenzar un proyecto real

1. Cambia `name` en `package.json`.
2. Cambia el nombre del proyecto dentro de `angular.json` solamente cuando necesites que el nombre del `dist` también cambie.
3. Cambia el título y la marca en `src/app/app.html`.
4. Agrega nuevas pantallas dentro de `src/app/features`.
5. Mantén servicios, interceptores y guardias dentro de `src/app/core`.

## Ambientes

- `src/environments/environment.ts`: desarrollo.
- `src/environments/environment.production.ts`: producción.

La URL recomendada es relativa (`/api`) y debe ser redirigida por el proxy en desarrollo y por IIS, Nginx o Apache en producción.
