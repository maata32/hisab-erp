import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { LocaleSwitcherComponent } from '@minierp/shared-ui';
import { AUTH_SERVICE } from '@minierp/shared-auth';

interface NavItem {
  label: string;
  icon: string;
  path: string;
  permission?: string;
  role?: string;
}

@Component({
  selector: 'erp-admin-main-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    TranslateModule,
    ButtonModule,
    MenuModule,
    LocaleSwitcherComponent,
  ],
  template: `
    <div class="min-h-screen flex flex-col bg-gray-50">
      <!-- Top bar -->
      <header
        class="sticky top-0 z-30 bg-white border-b border-gray-200 px-4 sm:px-6 h-14 flex items-center justify-between"
      >
        <div class="flex items-center gap-3">
          <button
            type="button"
            class="md:hidden btn-touch flex items-center justify-center text-gray-700"
            (click)="toggleSidebar()"
            aria-label="Open menu"
          >
            <i class="pi pi-bars text-xl"></i>
          </button>
          <span class="font-bold text-primary-700 text-lg">Mini-ERP</span>
        </div>
        <div class="flex items-center gap-3">
          <me-locale-switcher />
          <span class="hidden sm:block text-sm text-gray-600">{{ user()?.email }}</span>
          <button
            type="button"
            class="btn-touch flex items-center justify-center text-gray-700"
            (click)="logout()"
            [attr.aria-label]="'common.logout' | translate"
          >
            <i class="pi pi-sign-out"></i>
          </button>
        </div>
      </header>

      <div class="flex-1 flex">
        <!-- Sidebar -->
        <nav
          [class.hidden]="!sidebarOpen()"
          [class.flex]="sidebarOpen()"
          class="md:flex flex-col w-64 bg-white border-r border-gray-200 fixed md:sticky top-14 bottom-0 md:h-[calc(100vh-3.5rem)] z-20"
          aria-label="Main navigation"
        >
          <ul class="flex-1 overflow-y-auto py-2">
            @for (item of visibleItems(); track item.path) {
              <li>
                <a
                  [routerLink]="item.path"
                  routerLinkActive="bg-primary-50 text-primary-700 border-r-2 border-primary-600"
                  (click)="closeSidebarMobile()"
                  class="flex items-center gap-3 px-4 py-3 min-h-touch text-gray-700 hover:bg-gray-50"
                >
                  <i [class]="item.icon" class="text-lg"></i>
                  <span>{{ item.label | translate }}</span>
                </a>
              </li>
            }
          </ul>
          <footer class="p-3 text-xs text-gray-400 border-t">v{{ version }}</footer>
        </nav>

        <!-- Backdrop on mobile -->
        @if (sidebarOpen()) {
          <div
            class="md:hidden fixed inset-0 top-14 bg-black/30 z-10"
            (click)="closeSidebarMobile()"
            aria-hidden="true"
          ></div>
        }

        <main class="flex-1 p-4 sm:p-6 lg:p-8 min-w-0">
          <router-outlet />
        </main>
      </div>

      <!-- Mobile bottom nav -->
      <nav
        class="md:hidden bg-white border-t border-gray-200 grid grid-cols-5 sticky bottom-0 z-20"
        aria-label="Quick navigation"
      >
        @for (item of visibleItems().slice(0, 5); track item.path) {
          <a
            [routerLink]="item.path"
            routerLinkActive="text-primary-600"
            class="flex flex-col items-center justify-center py-2 min-h-touch text-gray-600 text-xs"
          >
            <i [class]="item.icon"></i>
            <span class="mt-1 truncate w-full text-center px-1">{{ item.label | translate }}</span>
          </a>
        }
      </nav>
    </div>
  `,
})
export class MainLayoutComponent {
  private readonly auth = inject(AUTH_SERVICE);
  private readonly router = inject(Router);
  protected readonly version = '0.1.0';
  protected readonly sidebarOpen = signal(false);

  protected readonly items: NavItem[] = [
    // Overview
    { label: 'nav.dashboard', icon: 'pi pi-home', path: '/dashboard' },

    // Sales cycle (Partner → Quote → Invoice → Delivery → Payment)
    { label: 'nav.partners', icon: 'pi pi-users', path: '/partners', permission: 'customer:read' },
    { label: 'nav.quotes', icon: 'pi pi-file', path: '/quotes', permission: 'sales:read' },
    { label: 'nav.invoices', icon: 'pi pi-receipt', path: '/invoices', permission: 'sales:read' },
    { label: 'nav.creditNotes', icon: 'pi pi-replay', path: '/credit-notes', permission: 'sales:read' },
    { label: 'nav.deliveries', icon: 'pi pi-truck', path: '/deliveries', permission: 'delivery:read' },
    { label: 'nav.payments', icon: 'pi pi-credit-card', path: '/payments', permission: 'payment:read' },

    // Purchase cycle
    { label: 'nav.purchaseOrders', icon: 'pi pi-shopping-bag', path: '/purchase-orders', permission: 'purchase:read' },
    { label: 'nav.purchaseInvoices', icon: 'pi pi-file-edit', path: '/purchase-invoices', permission: 'purchase:read' },
    { label: 'nav.goodsReceipts', icon: 'pi pi-inbox', path: '/goods-receipts', permission: 'purchase:read' },

    // Inventory & catalog
    { label: 'nav.products', icon: 'pi pi-box', path: '/products', permission: 'product:read' },
    { label: 'nav.attributes', icon: 'pi pi-th-large', path: '/attributes', permission: 'product:read' },
    { label: 'nav.stock', icon: 'pi pi-database', path: '/stock', permission: 'stock:read' },
    { label: 'nav.warehouses', icon: 'pi pi-warehouse', path: '/warehouses', permission: 'stock:read' },
    { label: 'nav.stockTransfers', icon: 'pi pi-arrow-right-arrow-left', path: '/stock-transfers', permission: 'inventory:transfer' },
    { label: 'nav.inventoryCounts', icon: 'pi pi-list-check', path: '/inventory-counts', permission: 'inventory:count' },
    { label: 'nav.lots', icon: 'pi pi-tag', path: '/lots', permission: 'lot:read' },

    // Finance
    { label: 'nav.treasury', icon: 'pi pi-money-bill', path: '/treasury', permission: 'treasury:read' },
    { label: 'nav.expenses', icon: 'pi pi-wallet', path: '/expenses', permission: 'expense:read' },
    { label: 'nav.expenseCategories', icon: 'pi pi-tags', path: '/expense-categories', permission: 'expense:read' },

    // Reports
    { label: 'nav.reporting', icon: 'pi pi-chart-bar', path: '/reporting', permission: 'reporting:read' },

    // Configuration
    { label: 'nav.priceTiers', icon: 'pi pi-percentage', path: '/price-tiers', permission: 'price:update' },
    { label: 'nav.uoms', icon: 'pi pi-sliders-h', path: '/uoms', permission: 'uom:read' },
    { label: 'nav.notifConfig', icon: 'pi pi-bell', path: '/notifications-config', permission: 'tenant_settings:read' },
    { label: 'nav.settings', icon: 'pi pi-cog', path: '/settings', permission: 'tenant_settings:read' },

    // Administration
    { label: 'nav.users', icon: 'pi pi-id-card', path: '/users', permission: 'user:read' },
    { label: 'nav.audit', icon: 'pi pi-shield', path: '/audit', permission: 'audit:read' },
    { label: 'nav.organizations', icon: 'pi pi-building', path: '/organizations', role: 'SUPER_ADMIN' },
    { label: 'nav.orgTypes', icon: 'pi pi-tags', path: '/organization-types', role: 'SUPER_ADMIN' },
    { label: 'nav.plans', icon: 'pi pi-money-bill', path: '/plans', role: 'SUPER_ADMIN' },
    { label: 'nav.subPaymentsAdmin', icon: 'pi pi-credit-card', path: '/subscription-payments', role: 'SUPER_ADMIN' },
  ];

  protected user() {
    return this.auth.getCurrentUser();
  }

  protected visibleItems(): NavItem[] {
    return this.items.filter((it) => {
      if (it.role && !this.auth.hasAnyRole(it.role)) return false;
      if (it.permission && !this.auth.hasPermission(it.permission)) return false;
      return true;
    });
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((v) => !v);
  }

  closeSidebarMobile(): void {
    if (window.innerWidth < 768) this.sidebarOpen.set(false);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/auth/login');
  }
}
