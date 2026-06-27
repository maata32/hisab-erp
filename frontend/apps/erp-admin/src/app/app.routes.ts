import { Routes } from '@angular/router';
import { authGuard, roleGuard } from '@hisaberp/shared-auth';

export const appRoutes: Routes = [
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.authRoutes),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/registration/register.page').then((m) => m.RegisterPage),
  },
  {
    // Platform (super-admin) sign-in — public, outside the tenant layout.
    path: 'platform/login',
    loadComponent: () =>
      import('./features/platform/platform-login.page').then((m) => m.PlatformLoginPage),
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
        // Platform-level resource: the backend returns 403 for non-SUPER_ADMIN. Guard the
        // route (not just the nav item) so direct-URL access is forbidden before any fetch.
        canActivate: [roleGuard('SUPER_ADMIN')],
        loadComponent: () =>
          import('./features/organizations/organization-list.page').then((m) => m.OrganizationListPage),
      },
      {
        // Platform console drill-down: a given organization's users (cross-tenant).
        path: 'organizations/:id/users',
        canActivate: [roleGuard('SUPER_ADMIN')],
        loadComponent: () =>
          import('./features/platform/organization-users.page').then((m) => m.OrganizationUsersPage),
      },
      {
        // Platform console drill-down: a tenant's subscription payment ledger.
        path: 'organizations/:id/payments',
        canActivate: [roleGuard('SUPER_ADMIN')],
        loadComponent: () =>
          import('./features/platform/organization-payments.page').then((m) => m.OrganizationPaymentsPage),
      },
      {
        // Configurable organization types (super-admin).
        path: 'organization-types',
        canActivate: [roleGuard('SUPER_ADMIN')],
        loadComponent: () =>
          import('./features/platform/organization-type-list.page').then((m) => m.OrganizationTypeListPage),
      },
      {
        // Subscription plans CRUD (super-admin).
        path: 'plans',
        canActivate: [roleGuard('SUPER_ADMIN')],
        loadComponent: () =>
          import('./features/platform/plan-list.page').then((m) => m.PlanListPage),
      },
      {
        // Cross-tenant subscription payment management (super-admin).
        path: 'subscription-payments',
        canActivate: [roleGuard('SUPER_ADMIN')],
        loadComponent: () =>
          import('./features/platform/payments-admin.page').then((m) => m.PaymentsAdminPage),
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
        path: 'partners',
        loadComponent: () =>
          import('./features/partners/partner-list.page').then((m) => m.PartnerListPage),
      },
      { path: 'customers', redirectTo: '/partners?role=customer', pathMatch: 'full' },
      {
        path: 'quotes',
        loadComponent: () =>
          import('./features/sales/quote-list.page').then((m) => m.QuoteListPage),
      },
      {
        path: 'invoices',
        loadComponent: () =>
          import('./features/sales/invoice-list.page').then((m) => m.InvoiceListPage),
      },
      {
        path: 'credit-notes',
        loadComponent: () =>
          import('./features/sales/credit-note-list.page').then((m) => m.CreditNoteListPage),
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
      { path: 'suppliers', redirectTo: '/partners?role=supplier', pathMatch: 'full' },
      {
        path: 'purchase-orders',
        loadComponent: () =>
          import('./features/purchase/purchase-order-list.page').then((m) => m.PurchaseOrderListPage),
      },
      {
        path: 'purchase-invoices',
        loadComponent: () =>
          import('./features/purchase/purchase-invoice-list.page').then((m) => m.PurchaseInvoiceListPage),
      },
      {
        path: 'goods-receipts',
        loadComponent: () =>
          import('./features/reception/reception-list.page').then((m) => m.ReceptionListPage),
      },
      {
        path: 'products',
        loadComponent: () =>
          import('./features/products/product-list.page').then((m) => m.ProductListPage),
      },
      {
        path: 'attributes',
        loadComponent: () =>
          import('./features/products/attribute-list.page').then((m) => m.AttributeListPage),
      },
      {
        path: 'warehouses',
        loadComponent: () =>
          import('./features/inventory/warehouse-list.page').then((m) => m.WarehouseListPage),
      },
      {
        path: 'stock',
        loadComponent: () =>
          import('./features/inventory/stock-list.page').then((m) => m.StockListPage),
      },
      {
        path: 'stock-transfers',
        loadComponent: () =>
          import('./features/inventory/stock-transfer-list.page').then((m) => m.StockTransferListPage),
      },
      {
        path: 'inventory-counts',
        loadComponent: () =>
          import('./features/inventory/inventory-count-list.page').then((m) => m.InventoryCountListPage),
      },
      {
        path: 'lots',
        loadComponent: () =>
          import('./features/lots/lot-list.page').then((m) => m.LotListPage),
      },
      {
        path: 'expenses',
        loadComponent: () =>
          import('./features/expenses/expense-list.page').then((m) => m.ExpenseListPage),
      },
      {
        path: 'expense-categories',
        loadComponent: () =>
          import('./features/expenses/expense-category-list.page').then((m) => m.ExpenseCategoryListPage),
      },
      {
        path: 'treasury',
        loadComponent: () =>
          import('./features/treasury/treasury.page').then((m) => m.TreasuryPage),
      },
      {
        path: 'reporting',
        loadComponent: () =>
          import('./features/reporting/reporting.page').then((m) => m.ReportingPage),
      },
      {
        path: 'uoms',
        loadComponent: () =>
          import('./features/uoms/uom-list.page').then((m) => m.UomListPage),
      },
      {
        path: 'price-tiers',
        loadComponent: () =>
          import('./features/pricing/price-tier-list.page').then((m) => m.PriceTierListPage),
      },
      {
        path: 'notifications-config',
        loadComponent: () =>
          import('./features/notifications/notifications-config.page').then((m) => m.NotificationsConfigPage),
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
