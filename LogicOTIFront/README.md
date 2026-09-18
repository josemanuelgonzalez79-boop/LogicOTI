# Frontend LogicOTI

SPA/PWA de supervisión y operación del edificio OTI.

## Versiones

- Angular 21.2.x
- PrimeNG 21.1.9
- TypeScript 5.9.x
- RxJS 7.8.x
- Node.js 22.12 o 24

Se eligió Angular 21 porque es la versión más reciente compatible con la última versión estable de PrimeNG. Angular 22 puede adoptarse cuando PrimeNG 22 tenga una versión estable.

## Requisitos

```powershell
node -v
npm -v
```

## Iniciar

```powershell
npm ci
npm start
```

La aplicación abre en `http://localhost:4200` y el proxy envía `/api` hacia `http://localhost:3210`.

## Pruebas y build de producción

```powershell
npm ci
npx ng test --watch=false
npx ng build --configuration production --base-href /LogicOTI/
```

El resultado queda en `dist/industrial-frontend-template/browser`.

## Ambientes

- `src/environments/environment.ts`: desarrollo.
- `src/environments/environment.production.ts`: producción.

Producción usa rutas relativas: `/api` para REST y `/ws` para WebSocket. Caddy debe redirigirlas al
backend; el navegador nunca debe comunicarse directamente con `http://servidor:3210` desde una
página HTTPS.

El build queda en `dist/industrial-frontend-template/browser` y se publica en el contexto Tomcat
`/LogicOTI/`. El Service Worker solamente se genera con la configuración `production`.

Consulta [la guía de operación y despliegue](../docs/OPERACION_Y_DESPLIEGUE.md) para instalar la
PWA, renovar el frontend y comprobar HTTPS, API, WebSocket, cámaras y notificaciones.
