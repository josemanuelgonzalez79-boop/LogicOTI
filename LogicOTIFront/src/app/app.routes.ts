import { Routes } from '@angular/router';

import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },

  // Autenticación
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((c) => c.Login),
    title: 'Iniciar sesión | LogicOTI',
  },

  // Dashboard
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((c) => c.Dashboard),
    title: 'Dashboard | OTI',
  },

  // UI Showcase (Desarrollo)
  {
    path: 'ui-showcase',
    canActivate: [authGuard],
    loadComponent: () => import('./features/ui-showcase/ui-showcase').then((c) => c.UiShowcase),
    title: 'UI Showcase | OTI',
  },

  // Building
  {
    path: 'building',
    canActivate: [authGuard],
    loadComponent: () => import('./features/building/building').then((c) => c.Building),
    title: 'Edificio | OTI',
  },
  {
    path: 'building/floor/:floorId',
    canActivate: [authGuard],
    loadComponent: () => import('./features/building/floor/floor').then((c) => c.Floor),
    title: 'Plano del piso | OTI',
  },
  {
    path: 'building/floor/:floorId/area/:areaId',
    canActivate: [authGuard],
    loadComponent: () => import('./features/building/area/area').then((c) => c.Area),
    title: 'Detalle del área | OTI',
  },

  // Control
  {
    path: 'control',
    canActivate: [authGuard],
    loadComponent: () => import('./features/control/control').then((c) => c.Control),
    title: 'Control | OTI',
  },

  // Cámaras
  {
    path: 'cameras',
    canActivate: [authGuard],
    loadComponent: () => import('./features/cameras/cameras').then((c) => c.Cameras),
    title: 'Cámaras | OTI',
  },

  // Alarmas
  {
    path: 'alarms',
    canActivate: [authGuard],
    loadComponent: () => import('./features/alarms/alarms').then((c) => c.Alarms),
    title: 'Alarmas | OTI',
  },

  // Históricos
  {
    path: 'history',
    canActivate: [authGuard],
    loadComponent: () => import('./features/history/history').then((c) => c.History),
    title: 'Históricos | OTI',
  },

  // Diagnóstico
  {
    path: 'diagnostics',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./features/diagnostics/diagnostics').then((c) => c.Diagnostics),
    title: 'Diagnóstico | OTI',
  },

  // Administración
  {
    path: 'administration',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/administration/administration').then((c) => c.Administration),
    title: 'Usuarios | OTI',
  },

  // Página no encontrada
  {
    path: '**',
    loadComponent: () => import('./features/not-found/not-found').then((c) => c.NotFound),
    title: 'Página no encontrada',
  },
];
