import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home').then((component) => component.Home),
    title: 'Inicio | Plantilla industrial',
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found').then((component) => component.NotFound),
    title: 'Página no encontrada',
  },
];
