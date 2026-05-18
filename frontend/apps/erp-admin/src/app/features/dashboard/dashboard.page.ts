import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AUTH_SERVICE } from '@minierp/shared-auth';
import { MoneyFormatService } from '@minierp/shared-i18n';

interface DashboardKpis {
  salesToday: number;
  salesCountToday: number;
  salesYesterday: number;
  expensesMonth: number;
  pendingExpenseApprovals: number;
  expiringLots30: number;
  expiredLots: number;
  activeUsers: number;
  unpaidInvoicesCount: number;
  unpaidInvoicesAmount: number;
}

interface PhaseItem { key: string; }
interface Phase {
  key: string;
  state: 'delivered' | 'next';
  items: string[];
}

@Component({
  selector: 'erp-admin-dashboard-page',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    <div class="space-y-6">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'dashboard.title' | translate }}</h1>
        <p class="text-gray-600 mt-1">{{ 'dashboard.welcome' | translate: { name: user()?.email } }}</p>
      </header>

      <!-- KPI grid -->
      <div class="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
        @for (kpi of kpiCards(); track kpi.label) {
          <div class="bg-white rounded-lg border border-gray-200 p-5"
               [class.border-red-200]="kpi.severity === 'danger'"
               [class.border-orange-200]="kpi.severity === 'warning'"
               [class.border-green-200]="kpi.severity === 'success'">
            <div class="flex items-center justify-between">
              <span class="text-sm text-gray-500">{{ kpi.label | translate }}</span>
              <i [class]="kpi.icon"
                 [class.text-red-500]="kpi.severity === 'danger'"
                 [class.text-orange-500]="kpi.severity === 'warning'"
                 [class.text-green-500]="kpi.severity === 'success'"
                 [class.text-primary-500]="!kpi.severity"
                 class="text-xl"></i>
            </div>
            <div class="mt-3 text-3xl font-bold text-gray-800">
              {{ loading() ? '—' : kpi.value }}
            </div>
            @if (kpi.hint) {
              <div class="mt-1 text-xs text-gray-500">{{ kpi.hint | translate: kpi.hintArgs }}</div>
            }
          </div>
        }
      </div>

      <!-- Phase progress -->
      <div class="grid gap-4 grid-cols-1 lg:grid-cols-2">
        @for (phase of phases; track phase.key) {
          <div class="bg-white rounded-lg border p-6"
               [class.border-green-200]="phase.state === 'delivered'"
               [class.border-blue-200]="phase.state === 'next'">
            <div class="flex items-center gap-3 mb-3">
              <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold"
                    [class.bg-green-100]="phase.state === 'delivered'"
                    [class.text-green-800]="phase.state === 'delivered'"
                    [class.bg-blue-100]="phase.state === 'next'"
                    [class.text-blue-800]="phase.state === 'next'">
                <i class="text-xs"
                   [class.pi-check-circle]="phase.state === 'delivered'"
                   [class.pi-arrow-right]="phase.state === 'next'"
                   [class.pi]="true"></i>
                {{ 'dashboard.phase.' + phase.state | translate }}
              </span>
              <h2 class="text-base font-semibold text-gray-800">
                {{ 'dashboard.' + phase.key + '.title' | translate }}
              </h2>
            </div>
            <ul class="space-y-1.5 text-sm"
                [class.text-gray-700]="phase.state === 'delivered'"
                [class.text-gray-500]="phase.state === 'next'">
              @for (key of phase.items; track key) {
                <li class="flex items-start gap-2">
                  <i class="mt-0.5 shrink-0 pi"
                     [class.pi-check-circle]="phase.state === 'delivered'"
                     [class.text-green-500]="phase.state === 'delivered'"
                     [class.pi-circle]="phase.state === 'next'"
                     [class.text-blue-300]="phase.state === 'next'"></i>
                  <span>{{ 'dashboard.' + phase.key + '.' + key | translate }}</span>
                </li>
              }
            </ul>
          </div>
        }
      </div>
    </div>
  `,
})
export class DashboardPage implements OnInit {
  private readonly auth = inject(AUTH_SERVICE);
  private readonly http = inject(HttpClient);
  private readonly moneyFormat = inject(MoneyFormatService);

  protected readonly loading = signal(true);
  protected readonly kpis = signal<DashboardKpis | null>(null);

  protected user() {
    return this.auth.getCurrentUser();
  }

  async ngOnInit(): Promise<void> {
    try {
      const res = await firstValueFrom(
        this.http.get<DashboardKpis>('/api/v1/reports/dashboard')
      );
      this.kpis.set(res);
    } catch {
      this.kpis.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  protected readonly kpiCards = computed(() => {
    const k = this.kpis();
    const fmt = (n: number) => new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n);
    const fmtM = (n: number) => this.moneyFormat.format(n) + ' MRU';
    const delta = k && k.salesYesterday > 0
      ? Math.round(((k.salesToday - k.salesYesterday) / k.salesYesterday) * 100)
      : null;

    return [
      {
        label: 'dashboard.kpi.sales_today',
        icon: 'pi pi-shopping-cart',
        value: k ? fmtM(k.salesToday) : '—',
        hint: 'dashboard.kpi.sales_count_and_delta',
        hintArgs: {
          count: k?.salesCountToday ?? 0,
          delta: delta === null ? '—' : (delta >= 0 ? `+${delta}` : `${delta}`),
        },
      },
      {
        label: 'dashboard.kpi.unpaid_invoices',
        icon: 'pi pi-receipt',
        severity: k && k.unpaidInvoicesCount > 0 ? 'warning' : null,
        value: k ? fmtM(k.unpaidInvoicesAmount) : '—',
        hint: 'dashboard.kpi.invoice_count',
        hintArgs: { count: k?.unpaidInvoicesCount ?? 0 },
      },
      {
        label: 'dashboard.kpi.expenses_month',
        icon: 'pi pi-wallet',
        severity: k && k.pendingExpenseApprovals > 0 ? 'warning' : null,
        value: k ? fmtM(k.expensesMonth) : '—',
        hint: 'dashboard.kpi.pending_approvals',
        hintArgs: { count: k?.pendingExpenseApprovals ?? 0 },
      },
      {
        label: 'dashboard.kpi.lots_expiring',
        icon: 'pi pi-tag',
        severity: k && k.expiredLots > 0 ? 'danger' : (k && k.expiringLots30 > 0 ? 'warning' : null),
        value: k ? fmt(k.expiringLots30) : '—',
        hint: 'dashboard.kpi.lots_expired_count',
        hintArgs: { count: k?.expiredLots ?? 0 },
      },
    ] as Array<{
      label: string;
      icon: string;
      value: string;
      hint?: string;
      hintArgs?: Record<string, unknown>;
      severity?: 'danger' | 'warning' | 'success' | null;
    }>;
  });

  protected readonly phases: Phase[] = [
    {
      key: 'phase1a',
      state: 'delivered',
      items: ['catalog', 'uom', 'pricing', 'inventory', 'pos', 'offline', 'receipt', 'notifications', 'settings'],
    },
    {
      key: 'phase1b',
      state: 'delivered',
      items: ['sales', 'customers', 'payments', 'pdf', 'vat'],
    },
    {
      key: 'phase1c',
      state: 'delivered',
      items: ['multiWarehouse', 'lotExpiry', 'expenses', 'reporting', 'notifConfig'],
    },
    {
      key: 'phase2',
      state: 'next',
      items: ['purchases', 'employees', 'integrations', 'advancedReports'],
    },
  ];
}
