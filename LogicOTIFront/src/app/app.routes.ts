import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },

  // ==========================
  // Autenticación
  // ==========================
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login').then((c) => c.Login),
    title: 'Iniciar sesión | LogicOTI',
  },

  // ==========================
  // Dashboard
  // ==========================
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/dashboard').then((c) => c.Dashboard),
    title: 'Dashboard | OTI',
  },

  // ==========================
  // UI Showcase (Desarrollo)
  // ==========================
  {
    path: 'ui-showcase',
    loadComponent: () =>
      import('./features/ui-showcase/ui-showcase').then(
        (c) => c.UiShowcase
      ),
    title: 'UI Showcase | OTI',
  },

  // ==========================
  // Building
  // ==========================
  {
    path: 'building',
    loadComponent: () =>
      import('./features/building/building').then((c) => c.Building),
    title: 'Edificio | OTI',
  },
  {
    path: 'building/floor/:floorId',
    loadComponent: () =>
      import('./features/building/floor/floor').then((c) => c.Floor),
    title: 'Plano del piso | OTI',
  },
  {
    path: 'building/floor/:floorId/area/:areaId',
    loadComponent: () =>
      import('./features/building/area/area').then((c) => c.Area),
    title: 'Detalle del área | OTI',
  },

  // ==========================
  // Control
  // ==========================
  {
    path: 'control',
    loadComponent: () =>
      import('./features/control/control').then((c) => c.Control),
    title: 'Control | OTI',
  },

  // ==========================
  // Alarmas
  // ==========================
  {
    path: 'alarms',
    loadComponent: () =>
      import('./features/alarms/alarms').then((c) => c.Alarms),
    title: 'Alarmas | OTI',
  },

  // ==========================
  // Históricos
  // ==========================
  {
    path: 'history',
    loadComponent: () =>
      import('./features/history/history').then((c) => c.History),
    title: 'Históricos | OTI',
  },

  // ==========================
  // Diagnóstico
  // ==========================
  {
    path: 'diagnostics',
    loadComponent: () =>
      import('./features/diagnostics/diagnostics').then(
        (c) => c.Diagnostics
      ),
    title: 'Diagnóstico | OTI',
  },

  // ==========================
  // Administración
  // ==========================
  {
    path: 'administration',
    loadComponent: () =>
      import('./features/administration/administration').then(
        (c) => c.Administration
      ),
    title: 'Administración | OTI',
  },

  // ==========================
  // Página no encontrada
  // ==========================
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found').then((c) => c.NotFound),
    title: 'Página no encontrada',
  },
];