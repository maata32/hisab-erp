import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { CardModule } from 'primeng/card';
import { AUTH_SERVICE } from '@minierp/shared-auth';

@Component({
  selector: 'erp-admin-dashboard-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, CardModule],
  template: `
    <div class="space-y-6">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'dashboard.title' | translate }}</h1>
        <p class="text-gray-600 mt-1">
          {{ 'dashboard.welcome' | translate: { name: user()?.email } }}
        </p>
      </header>

      <div class="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
        @for (kpi of kpis; track kpi.key) {
          <div class="bg-white rounded-lg border border-gray-200 p-5">
            <div class="flex items-center justify-between">
              <span class="text-sm text-gray-500">{{ kpi.label | translate }}</span>
              <i [class]="kpi.icon" class="text-primary-500 text-xl"></i>
            </div>
            <div class="mt-3 text-3xl font-bold text-gray-800">{{ kpi.value }}</div>
            <div class="mt-1 text-xs text-gray-500">{{ kpi.hint | translate }}</div>
          </div>
        }
      </div>

      <div class="bg-white rounded-lg border border-gray-200 p-6">
        <h2 class="text-lg font-semibold mb-2">{{ 'dashboard.foundation_status' | translate }}</h2>
        <p class="text-sm text-gray-600">
          {{ 'dashboard.foundation_message' | translate }}
        </p>
        <ul class="mt-4 grid gap-2 text-sm">
          <li class="flex items-center gap-2"><i class="pi pi-check-circle text-green-500"></i> Multi-tenant isolation (Hibernate filter + Postgres RLS)</li>
          <li class="flex items-center gap-2"><i class="pi pi-check-circle text-green-500"></i> JWT authentication with 9 roles</li>
          <li class="flex items-center gap-2"><i class="pi pi-check-circle text-green-500"></i> Audit logging (immutable)</li>
          <li class="flex items-center gap-2"><i class="pi pi-check-circle text-green-500"></i> i18n FR / AR (RTL) / EN</li>
          <li class="flex items-center gap-2"><i class="pi pi-check-circle text-green-500"></i> Liquibase migrations + tenant settings</li>
        </ul>
      </div>
    </div>
  `,
})
export class DashboardPage {
  private readonly auth = inject(AUTH_SERVICE);

  protected user() {
    return this.auth.getCurrentUser();
  }

  protected readonly kpis = [
    { key: 'tenants', label: 'dashboard.kpi.tenants', value: '—', hint: 'dashboard.kpi.placeholder', icon: 'pi pi-building' },
    { key: 'users', label: 'dashboard.kpi.users', value: '—', hint: 'dashboard.kpi.placeholder', icon: 'pi pi-users' },
    { key: 'sales', label: 'dashboard.kpi.sales_today', value: '—', hint: 'dashboard.kpi.phase_1a', icon: 'pi pi-shopping-cart' },
    { key: 'audits', label: 'dashboard.kpi.audit_events', value: '—', hint: 'dashboard.kpi.placeholder', icon: 'pi pi-shield' },
  ];
}
