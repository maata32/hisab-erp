import { Routes } from '@angular/router';
import { authGuard } from '@minierp/shared-auth';

export const appRoutes: Routes = [
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.authRoutes),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/main-layout.component').then((m) => m.MainLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'organizations',
        loadComponent: () =>
          import('./features/organizations/organization-list.page').then((m) => m.OrganizationListPage),
      },
      {
        path: 'users',
        loadComponent: () => import('./features/users/user-list.page').then((m) => m.UserListPage),
      },
      {
        path: 'audit',
        loadComponent: () => import('./features/audit/audit-log.page').then((m) => m.AuditLogPage),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.page').then((m) => m.SettingsPage),
      },
    ],
  },
  {
    path: 'forbidden',
    loadComponent: () =>
      import('./features/errors/forbidden.page').then((m) => m.ForbiddenPage),
  },
  { path: '**', redirectTo: '' },
];
