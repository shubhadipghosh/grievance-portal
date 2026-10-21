import { Routes } from '@angular/router';
import { staffGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'queue' },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/staff-login/staff-login').then((m) => m.StaffLogin),
  },
  {
    path: 'queue',
    canActivate: [staffGuard],
    loadComponent: () => import('./features/queue/queue').then((m) => m.Queue),
  },
];
