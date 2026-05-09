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
      {
        path: 'customers',
        loadComponent: () =>
          import('./features/customers/customer-list.page').then((m) => m.CustomerListPage),
      },
      {
        path: 'quotes',
        loadComponent: () =>
          import('./features/sales/quote-list.page').then((m) => m.QuoteListPage),
      },
      {
        path: 'orders',
        loadComponent: () =>
          import('./features/sales/order-list.page').then((m) => m.OrderListPage),
      },
      {
        path: 'invoices',
        loadComponent: () =>
          import('./features/sales/invoice-list.page').then((m) => m.InvoiceListPage),
      },
      {
        path: 'deliveries',
        loadComponent: () =>
          import('./features/deliveries/delivery-list.page').then((m) => m.DeliveryListPage),
      },
      {
        path: 'payments',
        loadComponent: () =>
          import('./features/payments/payment-list.page').then((m) => m.PaymentListPage),
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
