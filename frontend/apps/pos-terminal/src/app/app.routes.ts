import { Routes } from '@angular/router';
import { authGuard } from '@minierp/shared-auth';

export const appRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login.page').then((m) => m.PosLoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/pos-shell.component').then((m) => m.PosShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'sale' },
      {
        path: 'sale',
        loadComponent: () => import('./features/sale.page').then((m) => m.SalePage),
      },
      {
        path: 'session',
        loadComponent: () => import('./features/session.page').then((m) => m.SessionPage),
      },
      {
        path: 'history',
        loadComponent: () => import('./features/history.page').then((m) => m.HistoryPage),
      },
      {
        path: 'session-history',
        loadComponent: () => import('./features/session-history.page').then((m) => m.SessionHistoryPage),
      },
      {
        path: 'forbidden',
        loadComponent: () => import('./features/forbidden.page').then((m) => m.ForbiddenPage),
      },
    ],
  },
  { path: 'auth/login', redirectTo: 'login' },
  { path: '**', redirectTo: '' },
];
