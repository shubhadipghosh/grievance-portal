import { Routes } from '@angular/router';
import { authGuard, citizenGuard, staffGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'queue' },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/staff-login/staff-login').then((m) => m.StaffLogin),
  },
  {
    path: 'citizen/login',
    loadComponent: () =>
      import('./features/auth/citizen-login/citizen-login').then((m) => m.CitizenLogin),
  },
  {
    path: 'citizen',
    pathMatch: 'full',
    canActivate: [citizenGuard],
    loadComponent: () => import('./features/citizen/citizen-home').then((m) => m.CitizenHome),
  },
  {
    path: 'queue',
    canActivate: [staffGuard],
    loadComponent: () => import('./features/queue/queue').then((m) => m.Queue),
  },
  {
    // Citizens will view their own grievances here too; the backend decides which.
    path: 'grievances/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/grievances/grievance-detail').then((m) => m.GrievanceDetail),
  },
];
