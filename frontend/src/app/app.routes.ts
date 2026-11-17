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
    path: 'citizen/file',
    canActivate: [citizenGuard],
    loadComponent: () =>
      import('./features/citizen/file-grievance/file-grievance').then((m) => m.FileGrievance),
  },
  {
    path: 'citizen/grievances/:id',
    canActivate: [citizenGuard],
    loadComponent: () =>
      import('./features/grievances/grievance-detail').then((m) => m.GrievanceDetail),
  },
  {
    path: 'queue',
    canActivate: [staffGuard],
    loadComponent: () => import('./features/queue/queue').then((m) => m.Queue),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
  },
  {
    // The same ticket page as citizen/grievances/:id; the backend decides what each viewer sees.
    path: 'grievances/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/grievances/grievance-detail').then((m) => m.GrievanceDetail),
  },
];
