import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AUTH_SERVICE } from '@minierp/shared-auth';
import { MoneyFormatService, MoneyPipe } from '@minierp/shared-i18n';

interface TopProduct {
  productId: string;
  productName: string;
  sku: string;
  qtySold: number;
  revenue: number;
}
interface DailyAmount { day: string; amount: number; }
interface PaymentMethodAmount { method: string; count: number; amount: number; }

interface DashboardKpis {
  // Finance — today
  salesToday: number;
  salesCountToday: number;
  salesYesterday: number;
  avgTicketToday: number;
  // Finance — month
  salesMonth: number;
  salesCountMonth: number;
  avgTicketMonth: number;
  expensesMonth: number;
  pendingExpenseApprovals: number;
  // Invoices / cash
  unpaidInvoicesCount: number;
  unpaidInvoicesAmount: number;
  overdueInvoicesCount: number;
  overdueInvoicesAmount: number;
  cashReceivedToday: number;
  // Stock & ops
  stockValueTotal: number;
  lowStockCount: number;
  pendingDeliveriesCount: number;
  openCashierSessionsCount: number;
  expiringLots30: number;
  expiredLots: number;
  // Customers
  activeUsers: number;
  activeCustomersCount: number;
  customersOverCreditLimit: number;
  totalCustomerBalance: number;
  agingCurrent: number;
  aging1to30: number;
  aging31to60: number;
  aging61to90: number;
  aging90plus: number;
  // Invoices backlog
  invoicesDraftCount: number;
  invoicesNotFullyDeliveredCount: number;
  creditNotesCountMonth: number;
  creditNotesAmountMonth: number;
  // Lists & trends
  topProductsMonth: TopProduct[];
  sales7Days: DailyAmount[];
  paymentMethodsToday: PaymentMethodAmount[];
}

type Severity = 'danger' | 'warning' | 'success' | null;
interface KpiCard {
  label: string;
  icon: string;
  value: string;
  hint?: string;
  hintArgs?: Record<string, unknown>;
  severity?: Severity;
}
interface KpiGroup {
  titleKey: string;
  visible: boolean;
  cards: KpiCard[];
}

interface Phase {
  key: string;
  state: 'delivered' | 'next';
  items: string[];
}

@Component({
  selector: 'erp-admin-dashboard-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, MoneyPipe],
  template: `
    <div class="space-y-6">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'dashboard.title' | translate }}</h1>
        <p class="text-gray-600 mt-1">{{ 'dashboard.welcome' | translate: { name: user()?.email } }}</p>
      </header>

      <!-- Primary 4 KPIs -->
      @if (canSeeFinance()) {
        <div class="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
          @for (kpi of primaryCards(); track kpi.label) {
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
              <div class="mt-3 text-2xl font-bold text-gray-800">
                {{ loading() ? '—' : kpi.value }}
              </div>
              @if (kpi.hint) {
                <div class="mt-1 text-xs text-gray-500">{{ kpi.hint | translate: kpi.hintArgs }}</div>
              }
            </div>
          }
        </div>
      }

      <!-- More KPIs toggle -->
      @if (visibleSecondaryGroups().length > 0) {
        <div>
          <button type="button" (click)="showMore.set(!showMore())"
                  class="text-sm text-primary-700 hover:text-primary-900 font-medium inline-flex items-center gap-1.5">
            <i class="pi" [class.pi-chevron-down]="!showMore()" [class.pi-chevron-up]="showMore()"></i>
            {{ (showMore() ? 'dashboard.toggle.less' : 'dashboard.toggle.more') | translate }}
          </button>
        </div>

        @if (showMore()) {
          @for (group of visibleSecondaryGroups(); track group.titleKey) {
            <section>
              <h2 class="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-2">
                {{ group.titleKey | translate }}
              </h2>
              <div class="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
                @for (kpi of group.cards; track kpi.label) {
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
                    <div class="mt-3 text-2xl font-bold text-gray-800">
                      {{ loading() ? '—' : kpi.value }}
                    </div>
                    @if (kpi.hint) {
                      <div class="mt-1 text-xs text-gray-500">{{ kpi.hint | translate: kpi.hintArgs }}</div>
                    }
                  </div>
                }
              </div>
            </section>
          }
        }
      }

      <!-- Aging summary -->
      @if (canSeeCustomers() && hasAgingData()) {
        <section class="bg-white rounded-lg border border-gray-200 p-5">
          <h2 class="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-3">
            {{ 'dashboard.aging.title' | translate }}
          </h2>
          <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
            @for (b of agingBuckets(); track b.key) {
              <div class="text-center">
                <div class="text-xs text-gray-500 mb-1">{{ b.key | translate }}</div>
                <div class="font-bold text-lg"
                     [class.text-gray-700]="b.tone === 'neutral'"
                     [class.text-orange-600]="b.tone === 'warning'"
                     [class.text-red-600]="b.tone === 'danger'">
                  {{ b.value | money }}
                </div>
                <div class="mt-2 h-1.5 bg-gray-100 rounded">
                  <div class="h-1.5 rounded"
                       [class.bg-gray-400]="b.tone === 'neutral'"
                       [class.bg-orange-400]="b.tone === 'warning'"
                       [class.bg-red-500]="b.tone === 'danger'"
                       [style.width.%]="(b.value / agingMax()) * 100"></div>
                </div>
              </div>
            }
          </div>
        </section>
      }

      <!-- 7-day sales (full width) -->
      @if (canSeeFinance() && kpis()?.sales7Days?.length) {
        <section class="bg-white rounded-lg border border-gray-200 p-5">
          <h2 class="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-3">
            {{ 'dashboard.trend.sales7' | translate }}
          </h2>
          <div class="flex items-end gap-2 h-32">
            @for (d of kpis()?.sales7Days; track d.day) {
              <div class="flex-1 flex flex-col items-center gap-1 justify-end h-full">
                <div class="text-[10px] text-gray-500 truncate">{{ d.amount | money }}</div>
                <div class="w-full bg-blue-500 rounded-t"
                     [style.height.%]="sparkBarHeight(d.amount)"
                     [style.min-height.px]="d.amount > 0 ? 2 : 0"></div>
                <div class="text-xs text-gray-600">{{ d.day | date:'EEE dd' }}</div>
              </div>
            }
          </div>
        </section>
      }

      <!-- Payments today (full width) -->
      @if (canSeePayments() && kpis()?.paymentMethodsToday?.length) {
        <section class="bg-white rounded-lg border border-gray-200 p-5">
          <h2 class="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-3">
            {{ 'dashboard.trend.paymentsToday' | translate }}
          </h2>
          <ul class="space-y-2 text-sm">
            @for (pm of kpis()?.paymentMethodsToday; track pm.method) {
              <li class="flex items-center justify-between">
                <span class="text-gray-700">{{ 'payments.methods.' + pm.method | translate }}</span>
                <span class="text-right">
                  <span class="font-medium">{{ pm.amount | money }}</span>
                  <span class="text-xs text-gray-500 ml-1">({{ pm.count }})</span>
                </span>
              </li>
            }
          </ul>
        </section>
      }

      <!-- Top products this month -->
      @if (canSeeFinance() && kpis()?.topProductsMonth?.length) {
        <section class="bg-white rounded-lg border border-gray-200 p-5">
          <h2 class="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-3">
            {{ 'dashboard.top.products' | translate }}
          </h2>
          <table class="w-full text-sm">
            <thead class="text-gray-500 text-xs uppercase">
              <tr>
                <th class="text-left py-1.5">{{ 'dashboard.top.product' | translate }}</th>
                <th class="text-left py-1.5">{{ 'dashboard.top.sku' | translate }}</th>
                <th class="text-right py-1.5">{{ 'dashboard.top.qty' | translate }}</th>
                <th class="text-right py-1.5">{{ 'dashboard.top.revenue' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (p of kpis()?.topProductsMonth; track p.productId) {
                <tr class="border-t border-gray-100">
                  <td class="py-1.5">{{ p.productName }}</td>
                  <td class="py-1.5 text-gray-500 font-mono text-xs">{{ p.sku }}</td>
                  <td class="py-1.5 text-right">{{ p.qtySold | number:'1.0-3' }}</td>
                  <td class="py-1.5 text-right font-medium">{{ p.revenue | money }}</td>
                </tr>
              }
            </tbody>
          </table>
        </section>
      }

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
  protected readonly showMore = signal(false);

  protected user() {
    return this.auth.getCurrentUser();
  }

  // ── permission gates ─────────────────────────────────────────────────────

  private readonly hasFinance = computed(() =>
    this.auth.hasPermission('sales:read') || this.auth.hasPermission('reporting:read')
  );
  private readonly hasStockOps = computed(() =>
    this.auth.hasPermission('stock:read') || this.auth.hasPermission('reporting:read')
    || this.auth.hasPermission('pos:operate')
  );
  private readonly hasCustomers = computed(() =>
    this.auth.hasPermission('customer:read') || this.auth.hasPermission('reporting:read')
  );
  private readonly hasPayments = computed(() =>
    this.auth.hasPermission('payment:read') || this.auth.hasPermission('reporting:read')
  );

  protected canSeeFinance() { return this.hasFinance(); }
  protected canSeeCustomers() { return this.hasCustomers(); }
  protected canSeePayments() { return this.hasPayments(); }

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

  // ── derived figures ──────────────────────────────────────────────────────

  private fmt(n: number | undefined | null): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(Number(n ?? 0));
  }
  private fmtM(n: number | undefined | null): string {
    return this.moneyFormat.format(Number(n ?? 0)) + ' MRU';
  }

  protected readonly primaryCards = computed<KpiCard[]>(() => {
    const k = this.kpis();
    const delta = k && k.salesYesterday > 0
      ? Math.round(((k.salesToday - k.salesYesterday) / k.salesYesterday) * 100)
      : null;
    return [
      {
        label: 'dashboard.kpi.sales_today',
        icon: 'pi pi-shopping-cart',
        value: k ? this.fmtM(k.salesToday) : '—',
        hint: 'dashboard.kpi.sales_count_and_delta',
        hintArgs: {
          count: k?.salesCountToday ?? 0,
          delta: delta === null ? '—' : (delta >= 0 ? `+${delta}` : `${delta}`),
        },
      },
      {
        label: 'dashboard.kpi.cash_received_today',
        icon: 'pi pi-money-bill',
        value: k ? this.fmtM(k.cashReceivedToday) : '—',
      },
      {
        label: 'dashboard.kpi.unpaid_invoices',
        icon: 'pi pi-receipt',
        severity: k && k.unpaidInvoicesCount > 0 ? 'warning' : null,
        value: k ? this.fmtM(k.unpaidInvoicesAmount) : '—',
        hint: 'dashboard.kpi.invoice_count',
        hintArgs: { count: k?.unpaidInvoicesCount ?? 0 },
      },
      {
        label: 'dashboard.kpi.overdue_invoices',
        icon: 'pi pi-exclamation-triangle',
        severity: k && k.overdueInvoicesCount > 0 ? 'danger' : null,
        value: k ? this.fmtM(k.overdueInvoicesAmount) : '—',
        hint: 'dashboard.kpi.invoice_count',
        hintArgs: { count: k?.overdueInvoicesCount ?? 0 },
      },
    ];
  });

  protected readonly visibleSecondaryGroups = computed<KpiGroup[]>(() => {
    const k = this.kpis();
    const groups: KpiGroup[] = [
      {
        titleKey: 'dashboard.group.activite',
        visible: this.hasFinance(),
        cards: [
          {
            label: 'dashboard.kpi.sales_month',
            icon: 'pi pi-calendar',
            value: k ? this.fmtM(k.salesMonth) : '—',
            hint: 'dashboard.kpi.invoice_count',
            hintArgs: { count: k?.salesCountMonth ?? 0 },
          },
          {
            label: 'dashboard.kpi.avg_ticket_today',
            icon: 'pi pi-chart-bar',
            value: k ? this.fmtM(k.avgTicketToday) : '—',
            hint: 'dashboard.kpi.avg_ticket_month_hint',
            hintArgs: { value: k ? this.fmtM(k.avgTicketMonth) : '—' },
          },
          {
            label: 'dashboard.kpi.expenses_month',
            icon: 'pi pi-wallet',
            severity: k && k.pendingExpenseApprovals > 0 ? 'warning' : null,
            value: k ? this.fmtM(k.expensesMonth) : '—',
            hint: 'dashboard.kpi.pending_approvals',
            hintArgs: { count: k?.pendingExpenseApprovals ?? 0 },
          },
          {
            label: 'dashboard.kpi.lots_expiring',
            icon: 'pi pi-tag',
            severity: k && k.expiredLots > 0 ? 'danger' : (k && k.expiringLots30 > 0 ? 'warning' : null),
            value: k ? this.fmt(k.expiringLots30) : '—',
            hint: 'dashboard.kpi.lots_expired_count',
            hintArgs: { count: k?.expiredLots ?? 0 },
          },
        ],
      },
      {
        titleKey: 'dashboard.group.invoices_backlog',
        visible: this.hasFinance(),
        cards: [
          {
            label: 'dashboard.kpi.invoices_draft',
            icon: 'pi pi-file-edit',
            value: k ? this.fmt(k.invoicesDraftCount) : '—',
          },
          {
            label: 'dashboard.kpi.invoices_not_fully_delivered',
            icon: 'pi pi-truck',
            severity: k && k.invoicesNotFullyDeliveredCount > 0 ? 'warning' : null,
            value: k ? this.fmt(k.invoicesNotFullyDeliveredCount) : '—',
          },
          {
            label: 'dashboard.kpi.credit_notes_month',
            icon: 'pi pi-undo',
            severity: k && k.creditNotesCountMonth > 0 ? 'warning' : null,
            value: k ? this.fmt(k.creditNotesCountMonth) : '—',
            hint: 'dashboard.kpi.credit_notes_month_hint',
            hintArgs: { amount: k ? this.fmtM(k.creditNotesAmountMonth) : '0' },
          },
        ],
      },
      {
        titleKey: 'dashboard.group.stock',
        visible: this.hasStockOps(),
        cards: [
          {
            label: 'dashboard.kpi.stock_value',
            icon: 'pi pi-box',
            value: k ? this.fmtM(k.stockValueTotal) : '—',
          },
          {
            label: 'dashboard.kpi.low_stock',
            icon: 'pi pi-arrow-down',
            severity: k && k.lowStockCount > 0 ? 'warning' : null,
            value: k ? this.fmt(k.lowStockCount) : '—',
            hint: 'dashboard.kpi.low_stock_hint',
          },
          {
            label: 'dashboard.kpi.pending_deliveries',
            icon: 'pi pi-truck',
            value: k ? this.fmt(k.pendingDeliveriesCount) : '—',
          },
          {
            label: 'dashboard.kpi.open_sessions',
            icon: 'pi pi-desktop',
            value: k ? this.fmt(k.openCashierSessionsCount) : '—',
          },
        ],
      },
      {
        titleKey: 'dashboard.group.clients',
        visible: this.hasCustomers(),
        cards: [
          {
            label: 'dashboard.kpi.active_customers',
            icon: 'pi pi-users',
            value: k ? this.fmt(k.activeCustomersCount) : '—',
          },
          {
            label: 'dashboard.kpi.over_credit_limit',
            icon: 'pi pi-flag',
            severity: k && k.customersOverCreditLimit > 0 ? 'danger' : null,
            value: k ? this.fmt(k.customersOverCreditLimit) : '—',
            hint: 'dashboard.kpi.over_credit_limit_hint',
          },
          {
            label: 'dashboard.kpi.total_customer_balance',
            icon: 'pi pi-credit-card',
            value: k ? this.fmtM(k.totalCustomerBalance) : '—',
          },
          {
            label: 'dashboard.kpi.active_users',
            icon: 'pi pi-user',
            value: k ? this.fmt(k.activeUsers) : '—',
          },
        ],
      },
    ];
    return groups.filter(g => g.visible);
  });

  // ── aging visualization ──────────────────────────────────────────────────

  protected readonly agingBuckets = computed(() => {
    const k = this.kpis();
    if (!k) return [];
    return [
      { key: 'dashboard.aging.current', value: k.agingCurrent, tone: 'neutral' as const },
      { key: 'dashboard.aging.d1_30',   value: k.aging1to30,   tone: 'neutral' as const },
      { key: 'dashboard.aging.d31_60',  value: k.aging31to60,  tone: 'warning' as const },
      { key: 'dashboard.aging.d61_90',  value: k.aging61to90,  tone: 'warning' as const },
      { key: 'dashboard.aging.d90plus', value: k.aging90plus,  tone: 'danger'  as const },
    ];
  });

  protected agingMax(): number {
    const max = Math.max(...this.agingBuckets().map(b => b.value || 0), 1);
    return max;
  }

  protected hasAgingData(): boolean {
    return this.agingBuckets().some(b => (b.value || 0) > 0);
  }

  // ── sparkline ────────────────────────────────────────────────────────────

  protected sparkBarHeight(amount: number): number {
    const max = Math.max(...(this.kpis()?.sales7Days ?? []).map(d => d.amount || 0), 1);
    return (amount / max) * 80;
  }

  // ── phases (project status) ──────────────────────────────────────────────

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
