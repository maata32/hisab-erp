import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { LoadingSpinnerComponent } from '@minierp/shared-ui';
import { isApiError } from '@minierp/shared-api';
import { firstValueFrom } from 'rxjs';

interface Payment {
  id: string;
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

/** Super-admin ledger of a tenant's subscription payments (records extend the subscription). */
@Component({
  selector: 'erp-admin-organization-payments',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, TranslateModule,
    TableModule, TagModule, ButtonModule, InputTextModule, DialogModule, TooltipModule,
    LoadingSpinnerComponent,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div class="flex items-center gap-3">
          <a routerLink="/organizations" pButton icon="pi pi-arrow-left"
             class="p-button-text p-button-sm" [pTooltip]="'common.back' | translate"></a>
          <div>
            <h1 class="text-2xl font-bold text-gray-800">{{ 'subPayments.title' | translate }}</h1>
            <p class="text-gray-500 text-sm mt-1">
              <span class="font-medium">{{ orgName() || orgId }}</span>
              @if (orgCode()) { <span class="font-mono text-xs">({{ orgCode() }})</span> }
            </p>
          </div>
        </div>
        <button pButton icon="pi pi-plus" [label]="'subPayments.record' | translate"
                (click)="openRecord()" class="p-button-sm"></button>
      </header>

      @if (loading()) {
        <me-loading-spinner />
      } @else {
        <div class="bg-white rounded-lg border border-gray-200">
          <p-table [value]="rows()" styleClass="p-datatable-sm" responsiveLayout="scroll">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'subPayments.paidAt' | translate }}</th>
                <th>{{ 'subPayments.duration' | translate }}</th>
                <th class="text-right">{{ 'subPayments.amount' | translate }}</th>
                <th>{{ 'subPayments.period' | translate }}</th>
                <th class="text-center">{{ 'subPayments.justification' | translate }}</th>
                <th class="text-right">{{ 'common.actions' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-p>
              <tr [class.opacity-50]="p.cancelled">
                <td [class.line-through]="p.cancelled">{{ p.paidAt | date: 'mediumDate' }}</td>
                <td [class.line-through]="p.cancelled">{{ durationLabel(p) }}</td>
                <td class="text-right font-medium" [class.line-through]="p.cancelled">{{ p.amount }} {{ p.currency }}</td>
                <td class="text-sm text-gray-600">
                  {{ p.periodStart | date: 'shortDate' }} → {{ p.periodEnd | date: 'shortDate' }}
                </td>
                <td class="text-center">
                  @if (p.attachmentUrl) {
                    <a [href]="p.attachmentUrl" target="_blank" rel="noopener"
                       pButton icon="pi pi-paperclip" class="p-button-text p-button-sm"
                       [pTooltip]="'subPayments.view' | translate"></a>
                  } @else {
                    <span class="text-gray-300">—</span>
                  }
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
              <tr><td colspan="6" class="text-center text-gray-400 py-8">
                {{ 'subPayments.empty' | translate }}
              </td></tr>
            </ng-template>
          </p-table>
        </div>
      }
    </div>

    <!-- Record a payment -->
    <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '480px' }"
              [header]="'subPayments.record' | translate" [closable]="!saving()">
      <div class="space-y-3">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'subPayments.years' | translate }}</label>
            <input pInputText type="number" min="0" [(ngModel)]="form.years" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'subPayments.months' | translate }}</label>
            <input pInputText type="number" min="0" [(ngModel)]="form.months" class="w-full" />
          </div>
        </div>
        @if (submitted() && !durationValid()) {
          <small class="text-red-600">{{ 'subPayments.durationRequired' | translate }}</small>
        }
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'subPayments.amount' | translate }} *</label>
            <input pInputText type="number" min="0" [(ngModel)]="form.amount" class="w-full"
                   [class.ng-invalid]="submitted() && form.amount == null"
                   [class.ng-dirty]="submitted() && form.amount == null" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'subPayments.currency' | translate }}</label>
            <input pInputText [(ngModel)]="form.currency" class="w-full uppercase" maxlength="3" />
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'subPayments.paidAt' | translate }}</label>
          <input pInputText type="date" [(ngModel)]="form.paidAt" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'subPayments.file' | translate }}</label>
          <input type="file" accept="image/*,application/pdf" (change)="onFile($event)" class="w-full text-sm" />
          <small class="text-gray-400">{{ 'subPayments.fileHint' | translate }}</small>
          @if (fileName()) { <div class="text-xs text-gray-600 mt-1">{{ fileName() }}</div> }
        </div>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                (click)="dialogOpen = false" [disabled]="saving()"></button>
        <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                (click)="save()" [loading]="saving()"></button>
      </ng-template>
    </p-dialog>
  `,
})
export class OrganizationPaymentsPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly toast = inject(MessageService);
  private readonly confirm = inject(ConfirmationService);
  private readonly translate = inject(TranslateService);

  protected readonly orgId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly orgName = signal<string | null>(null);
  protected readonly orgCode = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly rows = signal<Payment[]>([]);
  protected readonly fileName = signal<string | null>(null);

  protected dialogOpen = false;
  private file: File | null = null;
  protected form: { years: number; months: number; amount: number | null; currency: string; paidAt: string } =
    this.emptyForm();

  ngOnInit(): void {
    void this.loadOrg();
    void this.load();
  }

  protected durationLabel(p: Payment): string {
    const parts: string[] = [];
    if (p.years) parts.push(`${p.years} ${this.t('subPayments.unit.years')}`);
    if (p.months) parts.push(`${p.months} ${this.t('subPayments.unit.months')}`);
    return parts.join(' ') || '—';
  }

  protected durationValid(): boolean {
    return (Number(this.form.years) || 0) + (Number(this.form.months) || 0) > 0;
  }

  protected onFile(e: Event): void {
    const f = (e.target as HTMLInputElement).files?.[0] ?? null;
    this.file = f;
    this.fileName.set(f?.name ?? null);
  }

  private emptyForm() {
    return { years: 0, months: 0, amount: null as number | null, currency: 'MRU', paidAt: today() };
  }

  private async loadOrg(): Promise<void> {
    try {
      const org = await firstValueFrom(
        this.http.get<{ name: string; code: string; currency: string }>(`/api/v1/organizations/${this.orgId}`),
      );
      this.orgName.set(org?.name ?? null);
      this.orgCode.set(org?.code ?? null);
      if (org?.currency) this.form.currency = org.currency;
    } catch {
      /* header falls back to id */
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.rows.set(
        (await firstValueFrom(this.http.get<Payment[]>(`/api/v1/organizations/${this.orgId}/payments`))) ?? [],
      );
    } catch {
      this.rows.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  protected openRecord(): void {
    this.submitted.set(false);
    this.file = null;
    this.fileName.set(null);
    this.form = { ...this.emptyForm(), currency: this.form.currency };
    this.dialogOpen = true;
  }

  protected async save(): Promise<void> {
    this.submitted.set(true);
    if (!this.durationValid() || this.form.amount == null) return;
    const fd = new FormData();
    fd.set('years', String(Number(this.form.years) || 0));
    fd.set('months', String(Number(this.form.months) || 0));
    fd.set('amount', String(this.form.amount));
    if (this.form.currency.trim()) fd.set('currency', this.form.currency.trim().toUpperCase());
    if (this.form.paidAt) fd.set('paidAt', this.form.paidAt);
    if (this.file) fd.set('file', this.file);
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post(`/api/v1/organizations/${this.orgId}/payments`, fd));
      this.dialogOpen = false;
      this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t('common.success') });
      void this.load();
    } catch (err: unknown) {
      const body = (err as { error?: unknown })?.error;
      this.toast.add({
        severity: 'error',
        summary: this.t('common.error'),
        detail: isApiError(body) ? body.message : this.t('common.error_generic'),
      });
    } finally {
      this.saving.set(false);
    }
  }

  protected cancel(p: Payment): void {
    this.confirm.confirm({
      header: this.t('common.confirmation'),
      message: this.translate.instant('subPayments.confirmCancel'),
      icon: 'pi pi-times-circle',
      accept: async () => {
        try {
          await firstValueFrom(this.http.post(`/api/v1/subscription-payments/${p.id}/cancel`, {}));
          this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t('common.success') });
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

function today(): string {
  return new Date().toISOString().slice(0, 10);
}
