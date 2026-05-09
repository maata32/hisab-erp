import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { CardModule } from 'primeng/card';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
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

      <!-- KPI cards -->
      <div class="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
        @for (kpi of kpis; track kpi.key) {
          <div class="bg-white rounded-lg border border-gray-200 p-5">
            <div class="flex items-center justify-between">
              <span class="text-sm text-gray-500">{{ kpi.label | translate }}</span>
              <i [class]="kpi.icon" class="text-primary-500 text-xl"></i>
            </div>
            <div class="mt-3 text-3xl font-bold text-gray-800">{{ kpi.key === 'sales' ? salesToday() : kpi.value }}</div>
            <div class="mt-1 text-xs text-gray-500">{{ kpi.hint | translate }}</div>
          </div>
        }
      </div>

      <!-- Phase progress -->
      <div class="grid gap-4 grid-cols-1 lg:grid-cols-2">

        <!-- Phase 1A — delivered -->
        <div class="bg-white rounded-lg border border-green-200 p-6">
          <div class="flex items-center gap-3 mb-3">
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-green-100 text-green-800">
              <i class="pi pi-check-circle text-xs"></i>
              {{ 'dashboard.phase.delivered' | translate }}
            </span>
            <h2 class="text-base font-semibold text-gray-800">{{ 'dashboard.phase1a.title' | translate }}</h2>
          </div>
          <ul class="space-y-1.5 text-sm text-gray-700">
            @for (item of phase1aItems; track item) {
              <li class="flex items-start gap-2">
                <i class="pi pi-check-circle text-green-500 mt-0.5 shrink-0"></i>
                <span>{{ item | translate }}</span>
              </li>
            }
          </ul>
        </div>

        <!-- Phase 1B — next -->
        <div class="bg-white rounded-lg border border-blue-200 p-6">
          <div class="flex items-center gap-3 mb-3">
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">
              <i class="pi pi-arrow-right text-xs"></i>
              {{ 'dashboard.phase.next' | translate }}
            </span>
            <h2 class="text-base font-semibold text-gray-800">{{ 'dashboard.phase1b.title' | translate }}</h2>
          </div>
          <ul class="space-y-1.5 text-sm text-gray-500">
            @for (item of phase1bItems; track item) {
              <li class="flex items-start gap-2">
                <i class="pi pi-circle text-blue-300 mt-0.5 shrink-0"></i>
                <span>{{ item | translate }}</span>
              </li>
            }
          </ul>
        </div>
      </div>
    </div>
  `,
})
export class DashboardPage implements OnInit {
  private readonly auth = inject(AUTH_SERVICE);
  private readonly http = inject(HttpClient);

  protected readonly salesToday = signal<string>('—');

  protected user() {
    return this.auth.getCurrentUser();
  }

  async ngOnInit(): Promise<void> {
    try {
      const res = await firstValueFrom(
        this.http.get<{ salesToday: number }>('/api/v1/pos/stats/today')
      );
      this.salesToday.set(String(res.salesToday));
    } catch { /* not critical — leave as '—' */ }
  }

  protected readonly kpis = [
    { key: 'tenants',  label: 'dashboard.kpi.tenants',     value: '—', hint: 'dashboard.kpi.placeholder', icon: 'pi pi-building' },
    { key: 'users',    label: 'dashboard.kpi.users',       value: '—', hint: 'dashboard.kpi.placeholder', icon: 'pi pi-users' },
    { key: 'sales',    label: 'dashboard.kpi.sales_today', value: null, hint: 'dashboard.kpi.sales_hint',  icon: 'pi pi-shopping-cart' },
    { key: 'audits',   label: 'dashboard.kpi.audit_events',value: '—', hint: 'dashboard.kpi.placeholder', icon: 'pi pi-shield' },
  ];

  protected readonly phase1aItems = [
    'dashboard.phase1a.catalog',
    'dashboard.phase1a.uom',
    'dashboard.phase1a.pricing',
    'dashboard.phase1a.inventory',
    'dashboard.phase1a.pos',
    'dashboard.phase1a.offline',
    'dashboard.phase1a.receipt',
    'dashboard.phase1a.notifications',
    'dashboard.phase1a.settings',
  ];

  protected readonly phase1bItems = [
    'dashboard.phase1b.sales',
    'dashboard.phase1b.customers',
    'dashboard.phase1b.payments',
    'dashboard.phase1b.pdf',
    'dashboard.phase1b.vat',
  ];
}
