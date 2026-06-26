import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { LoadingSpinnerComponent } from '@minierp/shared-ui';
import { PageResponse } from '@minierp/shared-api';
import { firstValueFrom } from 'rxjs';

interface OrganizationRow {
  id: string;
  code: string;
  name: string;
  type: string;
  status: string;
  currency: string;
  locale: string;
  trialEndsAt: string | null;
  pastDueSince: string | null;
  planCode: string | null;
  subscriptionStatus: string | null;
}

interface Plan { code: string; name: string; monthlyPrice: number; }

@Component({
  selector: 'erp-admin-organization-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink, FormsModule, TranslateModule,
    TableModule, TagModule, ButtonModule, InputTextModule, DialogModule, DropdownModule, TooltipModule,
    LoadingSpinnerComponent,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'organizations.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'organizations.subtitle' | translate }}</p>
        </div>
        <div class="flex items-center gap-2 flex-wrap justify-end">
          <span class="p-input-icon-left">
            <i class="pi pi-search"></i>
            <input pInputText [(ngModel)]="search" (ngModelChange)="onSearch()"
                   [placeholder]="'organizations.searchPlaceholder' | translate" class="w-56 pl-8" />
          </span>
          <p-dropdown [(ngModel)]="typeFilter" [options]="typeFilterOptions()"
                      optionLabel="label" optionValue="value" styleClass="w-44"
                      (onChange)="reload()" appendTo="body" />
          <p-dropdown [(ngModel)]="planFilter" [options]="planFilterOptions()"
                      optionLabel="label" optionValue="value" styleClass="w-44"
                      (onChange)="reload()" appendTo="body" />
          <p-dropdown [(ngModel)]="statusFilter" [options]="statusFilterOptions()"
                      optionLabel="label" optionValue="value" styleClass="w-44"
                      (onChange)="reload()" appendTo="body" />
          <button pButton icon="pi pi-plus" [label]="'organizations.create' | translate"
                  (click)="openCreate()" class="p-button-sm"></button>
        </div>
      </header>

      <div class="bg-white rounded-lg border border-gray-200">
        <p-table [value]="rows()" [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [loading]="loading()" [paginator]="true" [rows]="pageSize"
                 [totalRecords]="total()" [first]="first()"
                 [rowsPerPageOptions]="[25, 50, 100]" styleClass="p-datatable-sm" responsiveLayout="scroll">
          <ng-template pTemplate="header">
            <tr>
              <th pSortableColumn="code">{{ 'organizations.code' | translate }} <p-sortIcon field="code" /></th>
              <th pSortableColumn="name">{{ 'organizations.name' | translate }} <p-sortIcon field="name" /></th>
              <th pSortableColumn="status">{{ 'organizations.status' | translate }} <p-sortIcon field="status" /></th>
              <th class="text-center">{{ 'platform.users.count' | translate }}</th>
              <th>{{ 'organizations.plan' | translate }}</th>
              <th pSortableColumn="trialEndsAt">{{ 'organizations.trialEnds' | translate }} <p-sortIcon field="trialEndsAt" /></th>
              <th class="text-right">{{ 'common.actions' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-row>
            <tr>
              <td><span class="font-mono text-xs">{{ row.code }}</span></td>
              <td class="font-medium">{{ row.name }}</td>
              <td><p-tag [value]="('organizations.statuses.' + row.status) | translate"
                         [severity]="severity(row.status)" /></td>
              <td class="text-center">
                <a [routerLink]="['/organizations', row.id, 'users']"
                   class="text-primary-600 hover:underline font-medium">{{ countOf(row.id) }}</a>
              </td>
              <td class="text-sm">{{ row.planCode || '—' }}</td>
              <td class="text-sm text-gray-600">{{ row.trialEndsAt ? (row.trialEndsAt | date: 'shortDate') : '—' }}</td>
              <td class="text-right whitespace-nowrap">
                <button pButton icon="pi pi-users" class="p-button-sm p-button-text"
                        [pTooltip]="'platform.users.view' | translate"
                        [routerLink]="['/organizations', row.id, 'users']"></button>
                @if (row.status === 'PENDING') {
                  <button pButton icon="pi pi-check" class="p-button-sm p-button-success p-button-text"
                          [pTooltip]="'organizations.actions.approve' | translate"
                          (click)="approve(row)"></button>
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-danger p-button-text"
                          [pTooltip]="'organizations.actions.reject' | translate"
                          (click)="openReason('reject', row)"></button>
                }
                @if (row.status === 'TRIAL' || row.status === 'PAST_DUE') {
                  <button pButton icon="pi pi-credit-card" class="p-button-sm p-button-text"
                          [pTooltip]="'organizations.actions.activate' | translate"
                          (click)="openActivate(row)"></button>
                }
                @if (row.status === 'ACTIVE' || row.status === 'TRIAL') {
                  <button pButton icon="pi pi-ban" class="p-button-sm p-button-warning p-button-text"
                          [pTooltip]="'organizations.actions.suspend' | translate"
                          (click)="openReason('suspend', row)"></button>
                }
                @if (row.status === 'SUSPENDED') {
                  <button pButton icon="pi pi-refresh" class="p-button-sm p-button-success p-button-text"
                          [pTooltip]="'organizations.actions.reactivate' | translate"
                          (click)="reactivate(row)"></button>
                }
                @if (row.status !== 'ARCHIVED') {
                  <button pButton icon="pi pi-inbox" class="p-button-sm p-button-secondary p-button-text"
                          [pTooltip]="'organizations.actions.archive' | translate"
                          (click)="openReason('archive', row)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">
              {{ 'organizations.empty.title' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>

    <!-- Create tenant -->
    <p-dialog [(visible)]="createOpen" [modal]="true" [style]="{ width: '480px' }"
              [header]="'organizations.create' | translate" [closable]="!saving()">
      <div class="space-y-3">
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'organizations.code' | translate }} *</label>
          <input pInputText [(ngModel)]="createForm.code" class="w-full"
                 [class.ng-invalid]="createSubmitted() && !createForm.code.trim()"
                 [class.ng-dirty]="createSubmitted() && !createForm.code.trim()" />
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'organizations.name' | translate }} *</label>
          <input pInputText [(ngModel)]="createForm.name" class="w-full"
                 [class.ng-invalid]="createSubmitted() && !createForm.name.trim()"
                 [class.ng-dirty]="createSubmitted() && !createForm.name.trim()" />
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'organizations.type' | translate }}</label>
          <p-dropdown [(ngModel)]="createForm.type" [options]="typeOptions"
                      optionLabel="label" optionValue="value" styleClass="w-full" appendTo="body" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                (click)="createOpen = false" [disabled]="saving()"></button>
        <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                (click)="create()" [loading]="saving()"></button>
      </ng-template>
    </p-dialog>

    <!-- Reason (reject / suspend / archive) -->
    <p-dialog [(visible)]="reasonOpen" [modal]="true" [style]="{ width: '440px' }"
              [header]="reasonTitle() | translate" [closable]="!saving()">
      <div class="space-y-2">
        <label class="block text-sm font-medium">{{ 'organizations.reason' | translate }}</label>
        <textarea pInputText [(ngModel)]="reasonText" rows="3" class="w-full"
                  [placeholder]="'organizations.reasonPlaceholder' | translate"></textarea>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                (click)="reasonOpen = false" [disabled]="saving()"></button>
        <button pButton [label]="'common.confirm' | translate" icon="pi pi-check"
                (click)="confirmReason()" [loading]="saving()"></button>
      </ng-template>
    </p-dialog>

    <!-- Activate subscription -->
    <p-dialog [(visible)]="activateOpen" [modal]="true" [style]="{ width: '440px' }"
              [header]="'organizations.actions.activate' | translate" [closable]="!saving()">
      <div class="space-y-3">
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'organizations.plan' | translate }}</label>
          <p-dropdown [(ngModel)]="activateForm.planCode" [options]="planOptions()"
                      optionLabel="label" optionValue="value" styleClass="w-full" appendTo="body"
                      [placeholder]="'organizations.keepCurrentPlan' | translate" [showClear]="true" />
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'organizations.billingCycle' | translate }}</label>
          <p-dropdown [(ngModel)]="activateForm.billingCycle" [options]="cycleOptions"
                      optionLabel="label" optionValue="value" styleClass="w-full" appendTo="body" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                (click)="activateOpen = false" [disabled]="saving()"></button>
        <button pButton [label]="'organizations.actions.activate' | translate" icon="pi pi-check"
                (click)="activate()" [loading]="saving()"></button>
      </ng-template>
    </p-dialog>
  `,
})
export class OrganizationListPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly confirm = inject(ConfirmationService);
  private readonly toast = inject(MessageService);
  private readonly translate = inject(TranslateService);

  protected readonly pageSize = 50;
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<OrganizationRow[]>([]);
  protected readonly plans = signal<Plan[]>([]);
  protected readonly counts = signal<Record<string, number>>({});

  protected statusFilter = '';
  protected typeFilter = '';
  protected planFilter = '';
  protected search = '';
  private sortParam = '';
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private currentRows = this.pageSize;

  protected readonly typeOptions = [
    { value: 'BOUTIQUE', label: 'Boutique' },
    { value: 'SUPERMARCHE', label: 'Supermarché' },
    { value: 'GROSSISTE', label: 'Grossiste' },
    { value: 'MIXTE', label: 'Mixte' },
  ];
  protected readonly cycleOptions = [
    { value: 'MONTHLY', label: this.t('organizations.cycles.MONTHLY') },
    { value: 'ANNUAL', label: this.t('organizations.cycles.ANNUAL') },
  ];

  protected createOpen = false;
  protected createSubmitted = signal(false);
  protected createForm = { code: '', name: '', type: 'BOUTIQUE' };

  protected reasonOpen = false;
  protected reasonText = '';
  private reasonAction: 'reject' | 'suspend' | 'archive' = 'reject';
  private reasonRow: OrganizationRow | null = null;

  protected activateOpen = false;
  protected activateForm: { planCode: string | null; billingCycle: string } = { planCode: null, billingCycle: 'MONTHLY' };
  private activateRow: OrganizationRow | null = null;

  ngOnInit(): void {
    void this.loadPlans();
    void this.loadCounts();
    void this.loadChunk({ first: 0, rows: this.pageSize });
  }

  protected countOf(id: string): number {
    return this.counts()[id] ?? 0;
  }

  private async loadCounts(): Promise<void> {
    try {
      const list = await firstValueFrom(
        this.http.get<{ organizationId: string; userCount: number }[]>('/api/v1/platform/user-counts'),
      );
      const map: Record<string, number> = {};
      for (const c of list ?? []) map[c.organizationId] = c.userCount;
      this.counts.set(map);
    } catch {
      this.counts.set({});
    }
  }

  protected statusFilterOptions() {
    return [
      { value: '', label: this.t('organizations.filter.all') },
      ...['PENDING', 'TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'ARCHIVED']
        .map((s) => ({ value: s, label: this.t('organizations.statuses.' + s) })),
    ];
  }

  protected planOptions() {
    return this.plans().map((p) => ({ value: p.code, label: `${p.name} (${p.monthlyPrice} MRU)` }));
  }

  protected typeFilterOptions() {
    return [{ value: '', label: this.t('organizations.filter.allTypes') }, ...this.typeOptions];
  }

  protected planFilterOptions() {
    return [
      { value: '', label: this.t('organizations.filter.allPlans') },
      ...this.plans().map((p) => ({ value: p.code, label: p.name || p.code })),
    ];
  }

  protected onSearch(): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.reload(), 300);
  }

  protected reasonTitle(): string {
    return this.reasonAction === 'reject' ? 'organizations.actions.reject'
      : this.reasonAction === 'suspend' ? 'organizations.actions.suspend'
      : 'organizations.actions.archive';
  }

  protected reload(): void {
    this.first.set(0);
    void this.loadChunk({ first: 0, rows: this.currentRows });
  }

  protected async loadChunk(event: TableLazyLoadEvent): Promise<void> {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize;
    this.currentRows = rows;
    this.first.set(first);
    const page = Math.floor(first / rows);
    // Preserve the active sort across filter-triggered reloads (synthetic events carry no sortField).
    const sf = Array.isArray(event.sortField) ? event.sortField[0] : event.sortField;
    if (sf) this.sortParam = `${sf},${event.sortOrder === -1 ? 'desc' : 'asc'}`;
    this.loading.set(true);
    try {
      const params = new URLSearchParams({ page: String(page), size: String(rows) });
      if (this.search.trim()) params.set('q', this.search.trim());
      if (this.statusFilter) params.set('status', this.statusFilter);
      if (this.typeFilter) params.set('type', this.typeFilter);
      if (this.planFilter) params.set('plan', this.planFilter);
      if (this.sortParam) params.set('sort', this.sortParam);
      const res = await firstValueFrom(
        this.http.get<PageResponse<OrganizationRow>>(`/api/v1/organizations?${params.toString()}`),
      );
      this.rows.set(res.content ?? []);
      this.total.set(res.totalElements ?? 0);
    } catch {
      this.rows.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  // ── Actions ────────────────────────────────────────────────────────────────

  protected approve(row: OrganizationRow): void {
    this.confirm.confirm({
      header: this.t('common.confirmation'),
      message: this.translate.instant('organizations.confirm.approve', { name: row.name }),
      icon: 'pi pi-check-circle',
      accept: () => this.run(`/api/v1/organizations/${row.id}/approve`, {}, 'organizations.toast.approved'),
    });
  }

  protected reactivate(row: OrganizationRow): void {
    this.confirm.confirm({
      header: this.t('common.confirmation'),
      message: this.translate.instant('organizations.confirm.reactivate', { name: row.name }),
      icon: 'pi pi-refresh',
      accept: () => this.run(`/api/v1/organizations/${row.id}/reactivate`, {}, 'organizations.toast.reactivated'),
    });
  }

  protected openReason(action: 'reject' | 'suspend' | 'archive', row: OrganizationRow): void {
    this.reasonAction = action;
    this.reasonRow = row;
    this.reasonText = '';
    this.reasonOpen = true;
  }

  protected confirmReason(): void {
    if (!this.reasonRow) return;
    const id = this.reasonRow.id;
    const body = { reason: this.reasonText?.trim() || null };
    const toast =
      this.reasonAction === 'reject' ? 'organizations.toast.rejected'
      : this.reasonAction === 'suspend' ? 'organizations.toast.suspended'
      : 'organizations.toast.archived';
    void this.run(`/api/v1/organizations/${id}/${this.reasonAction}`, body, toast, () => (this.reasonOpen = false));
  }

  protected openActivate(row: OrganizationRow): void {
    this.activateRow = row;
    this.activateForm = { planCode: null, billingCycle: 'MONTHLY' };
    this.activateOpen = true;
  }

  protected activate(): void {
    if (!this.activateRow) return;
    const id = this.activateRow.id;
    void this.run(`/api/v1/organizations/${id}/activate`,
      { planCode: this.activateForm.planCode || null, billingCycle: this.activateForm.billingCycle },
      'organizations.toast.activated', () => (this.activateOpen = false));
  }

  protected openCreate(): void {
    this.createForm = { code: '', name: '', type: 'BOUTIQUE' };
    this.createSubmitted.set(false);
    this.createOpen = true;
  }

  protected create(): void {
    this.createSubmitted.set(true);
    if (!this.createForm.code.trim() || !this.createForm.name.trim()) return;
    void this.run('/api/v1/organizations', { ...this.createForm }, 'organizations.toast.created',
      () => (this.createOpen = false));
  }

  private async run(url: string, body: unknown, successKey: string, onOk?: () => void): Promise<void> {
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post(url, body));
      onOk?.();
      this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t(successKey) });
      void this.loadChunk({ first: this.first(), rows: this.currentRows });
    } catch {
      // 4xx/5xx surfaced by the global handler; show a generic failure toast.
      this.toast.add({ severity: 'error', summary: this.t('common.error'), detail: this.t('organizations.toast.failed') });
    } finally {
      this.saving.set(false);
    }
  }

  private async loadPlans(): Promise<void> {
    try {
      this.plans.set((await firstValueFrom(this.http.get<Plan[]>('/api/v1/plans'))) ?? []);
    } catch {
      this.plans.set([]);
    }
  }

  protected severity(status: string): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'TRIAL': return 'info';
      case 'PENDING': return 'warning';
      case 'PAST_DUE': return 'warning';
      case 'SUSPENDED':
      case 'ARCHIVED': return 'danger';
      default: return 'secondary';
    }
  }

  private t(key: string): string {
    return this.translate.instant(key);
  }
}
