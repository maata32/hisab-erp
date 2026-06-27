import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DropdownModule } from 'primeng/dropdown';
import { CheckboxModule } from 'primeng/checkbox';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { LoadingSpinnerComponent } from '@hisaberp/shared-ui';
import { isApiError } from '@hisaberp/shared-api';
import { firstValueFrom } from 'rxjs';

interface Row {
  id: string;
  organizationId: string;
  organizationCode: string | null;
  organizationName: string | null;
  planCode: string | null;
  years: number;
  months: number;
  amount: number;
  currency: string;
  paidAt: string;
  periodStart: string;
  periodEnd: string;
  attachmentUrl: string | null;
  cancelled: boolean;
}
interface Bucket { key: string; label: string; amount: number; }
interface Revenue { total: number; byMonth: Bucket[]; byPlan: Bucket[]; byTenant: Bucket[]; }

/** Cross-tenant subscription payment management (super-admin): global ledger + revenue + cancel. */
@Component({
  selector: 'erp-admin-payments-admin',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule,
    TableModule, TagModule, ButtonModule, InputTextModule, DropdownModule, CheckboxModule, TooltipModule,
    LoadingSpinnerComponent,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'subPaymentsAdmin.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'subPaymentsAdmin.subtitle' | translate }}</p>
        </div>
        <div class="flex gap-1 bg-gray-100 rounded-lg p-1">
          <button class="px-3 py-1.5 rounded text-sm font-medium"
                  [class.bg-white]="tab() === 'list'" [class.shadow-sm]="tab() === 'list'"
                  (click)="tab.set('list')">{{ 'subPaymentsAdmin.tabList' | translate }}</button>
          <button class="px-3 py-1.5 rounded text-sm font-medium"
                  [class.bg-white]="tab() === 'revenue'" [class.shadow-sm]="tab() === 'revenue'"
                  (click)="showRevenue()">{{ 'subPaymentsAdmin.tabRevenue' | translate }}</button>
        </div>
      </header>

      @if (loading()) {
        <me-loading-spinner />
      } @else if (tab() === 'list') {
        <!-- Filters -->
        <div class="bg-white rounded-lg border border-gray-200 p-3 flex flex-wrap items-end gap-3">
          <p-dropdown [(ngModel)]="tenantFilter" [options]="tenantOptions()" optionLabel="label" optionValue="value"
                      styleClass="w-56" appendTo="body" [filter]="true" />
          <p-dropdown [(ngModel)]="planFilter" [options]="planOptions()" optionLabel="label" optionValue="value"
                      styleClass="w-40" appendTo="body" />
          <div>
            <label class="block text-xs text-gray-500 mb-1">{{ 'subPaymentsAdmin.from' | translate }}</label>
            <input pInputText type="date" [(ngModel)]="fromDate" class="w-36" />
          </div>
          <div>
            <label class="block text-xs text-gray-500 mb-1">{{ 'subPaymentsAdmin.to' | translate }}</label>
            <input pInputText type="date" [(ngModel)]="toDate" class="w-36" />
          </div>
          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" [(ngModel)]="includeCancelled" /> {{ 'subPaymentsAdmin.includeCancelled' | translate }}
          </label>
          <div class="ml-auto text-right">
            <div class="text-xs text-gray-500">{{ 'subPaymentsAdmin.total' | translate }}</div>
            <div class="text-xl font-bold text-gray-800">{{ filteredTotal() | number: '1.0-2' }} MRU</div>
          </div>
        </div>

        <div class="bg-white rounded-lg border border-gray-200">
          <p-table [value]="filteredRows()" styleClass="p-datatable-sm" responsiveLayout="scroll"
                   [paginator]="filteredRows().length > 25" [rows]="25">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'subPaymentsAdmin.tenant' | translate }}</th>
                <th>{{ 'subPayments.paidAt' | translate }}</th>
                <th>{{ 'subPayments.duration' | translate }}</th>
                <th class="text-right">{{ 'subPayments.amount' | translate }}</th>
                <th>{{ 'subPaymentsAdmin.plan' | translate }}</th>
                <th>{{ 'subPayments.period' | translate }}</th>
                <th class="text-center">{{ 'subPayments.justification' | translate }}</th>
                <th class="text-right">{{ 'common.actions' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-p>
              <tr [class.opacity-50]="p.cancelled">
                <td>
                  <div class="font-medium" [class.line-through]="p.cancelled">{{ p.organizationName || '—' }}</div>
                  <div class="font-mono text-xs text-gray-400">{{ p.organizationCode }}</div>
                </td>
                <td>{{ p.paidAt | date: 'mediumDate' }}</td>
                <td>{{ durationLabel(p) }}</td>
                <td class="text-right font-medium" [class.line-through]="p.cancelled">{{ p.amount }} {{ p.currency }}</td>
                <td class="text-sm">{{ p.planCode || '—' }}</td>
                <td class="text-sm text-gray-600">{{ p.periodStart | date: 'shortDate' }} → {{ p.periodEnd | date: 'shortDate' }}</td>
                <td class="text-center">
                  @if (p.attachmentUrl) {
                    <a [href]="p.attachmentUrl" target="_blank" rel="noopener" pButton icon="pi pi-paperclip"
                       class="p-button-text p-button-sm" [pTooltip]="'subPayments.view' | translate"></a>
                  } @else { <span class="text-gray-300">—</span> }
                </td>
                <td class="text-right whitespace-nowrap">
                  @if (p.cancelled) {
                    <p-tag [value]="'subPayments.cancelled' | translate" severity="danger" />
                  } @else {
                    <button pButton icon="pi pi-times" class="p-button-text p-button-sm p-button-danger"
                            [pTooltip]="'subPayments.cancel' | translate" (click)="cancel(p)"></button>
                  }
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'subPayments.empty' | translate }}</td></tr>
            </ng-template>
          </p-table>
        </div>
      } @else {
        <!-- Revenue -->
        <div class="bg-white rounded-lg border border-gray-200 p-5">
          <div class="text-sm text-gray-500">{{ 'subPaymentsAdmin.totalRevenue' | translate }}</div>
          <div class="text-3xl font-bold text-gray-800">{{ (revenue()?.total || 0) | number: '1.0-2' }} MRU</div>
        </div>
        <div class="grid gap-4 grid-cols-1 lg:grid-cols-3">
          <div class="bg-white rounded-lg border border-gray-200 p-4">
            <h3 class="font-semibold text-gray-700 mb-2">{{ 'subPaymentsAdmin.byMonth' | translate }}</h3>
            @for (b of revenue()?.byMonth || []; track b.key) {
              <div class="flex justify-between text-sm py-1 border-b border-gray-50">
                <span>{{ b.label }}</span><span class="font-medium">{{ b.amount | number: '1.0-2' }}</span>
              </div>
            } @empty { <div class="text-gray-400 text-sm">—</div> }
          </div>
          <div class="bg-white rounded-lg border border-gray-200 p-4">
            <h3 class="font-semibold text-gray-700 mb-2">{{ 'subPaymentsAdmin.byPlan' | translate }}</h3>
            @for (b of revenue()?.byPlan || []; track b.key) {
              <div class="flex justify-between text-sm py-1 border-b border-gray-50">
                <span>{{ b.label }}</span><span class="font-medium">{{ b.amount | number: '1.0-2' }}</span>
              </div>
            } @empty { <div class="text-gray-400 text-sm">—</div> }
          </div>
          <div class="bg-white rounded-lg border border-gray-200 p-4">
            <h3 class="font-semibold text-gray-700 mb-2">{{ 'subPaymentsAdmin.byTenant' | translate }}</h3>
            @for (b of revenue()?.byTenant || []; track b.key) {
              <div class="flex justify-between text-sm py-1 border-b border-gray-50">
                <span class="truncate">{{ b.label }}</span><span class="font-medium ml-2">{{ b.amount | number: '1.0-2' }}</span>
              </div>
            } @empty { <div class="text-gray-400 text-sm">—</div> }
          </div>
        </div>
      }
    </div>
  `,
})
export class PaymentsAdminPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(MessageService);
  private readonly confirm = inject(ConfirmationService);
  private readonly translate = inject(TranslateService);

  protected readonly tab = signal<'list' | 'revenue'>('list');
  protected readonly loading = signal(true);
  protected readonly rows = signal<Row[]>([]);
  protected readonly revenue = signal<Revenue | null>(null);

  protected tenantFilter = '';
  protected planFilter = '';
  protected fromDate = '';
  protected toDate = '';
  protected includeCancelled = false;

  protected readonly tenantOptions = computed(() => {
    const m = new Map<string, string>();
    for (const r of this.rows()) m.set(r.organizationId, r.organizationName || r.organizationCode || r.organizationId);
    return [{ value: '', label: this.t('subPaymentsAdmin.allTenants') },
      ...[...m].map(([value, label]) => ({ value, label }))];
  });
  protected readonly planOptions = computed(() => {
    const s = new Set<string>();
    for (const r of this.rows()) if (r.planCode) s.add(r.planCode);
    return [{ value: '', label: this.t('subPaymentsAdmin.allPlans') }, ...[...s].map((p) => ({ value: p, label: p }))];
  });

  ngOnInit(): void {
    void this.load();
  }

  protected filteredRows(): Row[] {
    return this.rows().filter((r) => {
      if (!this.includeCancelled && r.cancelled) return false;
      if (this.tenantFilter && r.organizationId !== this.tenantFilter) return false;
      if (this.planFilter && r.planCode !== this.planFilter) return false;
      if (this.fromDate && r.paidAt < this.fromDate) return false;
      if (this.toDate && r.paidAt > this.toDate) return false;
      return true;
    });
  }

  protected filteredTotal(): number {
    return this.filteredRows().filter((r) => !r.cancelled).reduce((s, r) => s + Number(r.amount), 0);
  }

  protected durationLabel(p: Row): string {
    const parts: string[] = [];
    if (p.years) parts.push(`${p.years} ${this.t('subPayments.unit.years')}`);
    if (p.months) parts.push(`${p.months} ${this.t('subPayments.unit.months')}`);
    return parts.join(' ') || '—';
  }

  protected showRevenue(): void {
    this.tab.set('revenue');
    if (!this.revenue()) void this.loadRevenue();
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.rows.set((await firstValueFrom(this.http.get<Row[]>('/api/v1/subscription-payments'))) ?? []);
    } catch {
      this.rows.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadRevenue(): Promise<void> {
    try {
      this.revenue.set(await firstValueFrom(this.http.get<Revenue>('/api/v1/subscription-payments/revenue')));
    } catch {
      this.revenue.set(null);
    }
  }

  protected cancel(p: Row): void {
    this.confirm.confirm({
      header: this.t('common.confirmation'),
      message: this.translate.instant('subPayments.confirmCancel'),
      icon: 'pi pi-times-circle',
      accept: async () => {
        try {
          await firstValueFrom(this.http.post(`/api/v1/subscription-payments/${p.id}/cancel`, {}));
          this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t('common.success') });
          this.revenue.set(null);
          void this.load();
        } catch (err: unknown) {
          const body = (err as { error?: unknown })?.error;
          this.toast.add({
            severity: 'error', summary: this.t('common.error'),
            detail: isApiError(body) ? body.message : this.t('common.error_generic'),
          });
        }
      },
    });
  }

  private t(key: string): string {
    return this.translate.instant(key);
  }
}
