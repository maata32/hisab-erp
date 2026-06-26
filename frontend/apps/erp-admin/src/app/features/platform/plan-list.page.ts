import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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

interface PlanRow {
  id: string;
  code: string;
  name: string;
  monthlyPrice: number;
  annualPrice: number | null;
  maxCashRegisters: number | null;
  maxUsers: number | null;
  maxProducts: number | null;
  maxProductImages: number | null;
  active: boolean;
}

/** Super-admin CRUD for subscription plans (formules). */
@Component({
  selector: 'erp-admin-plan-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule,
    TableModule, TagModule, ButtonModule, InputTextModule, DialogModule, TooltipModule,
    LoadingSpinnerComponent,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'plans.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'plans.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'plans.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      @if (loading()) {
        <me-loading-spinner />
      } @else {
        <div class="bg-white rounded-lg border border-gray-200">
          <p-table [value]="rows()" styleClass="p-datatable-sm" responsiveLayout="scroll">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'plans.code' | translate }}</th>
                <th>{{ 'plans.name' | translate }}</th>
                <th class="text-right">{{ 'plans.monthlyPrice' | translate }}</th>
                <th class="text-center">{{ 'plans.unit.cashRegisters' | translate }}</th>
                <th class="text-center">{{ 'plans.unit.users' | translate }}</th>
                <th class="text-center">{{ 'plans.unit.products' | translate }}</th>
                <th>{{ 'plans.status' | translate }}</th>
                <th class="text-right">{{ 'common.actions' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-row>
              <tr>
                <td class="font-mono text-xs">{{ row.code }}</td>
                <td class="font-medium">{{ row.name }}</td>
                <td class="text-right">{{ row.monthlyPrice }} MRU</td>
                <td class="text-center">{{ lim(row.maxCashRegisters) }}</td>
                <td class="text-center">{{ lim(row.maxUsers) }}</td>
                <td class="text-center">{{ lim(row.maxProducts) }}</td>
                <td>
                  @if (row.active) {
                    <p-tag [value]="'common.active' | translate" severity="success" />
                  } @else {
                    <p-tag [value]="'common.inactive' | translate" severity="secondary" />
                  }
                </td>
                <td class="text-right whitespace-nowrap">
                  <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                          [pTooltip]="'common.edit' | translate" (click)="openEdit(row)"></button>
                  <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.delete' | translate" (click)="remove(row)"></button>
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="8" class="text-center text-gray-400 py-8">
                {{ 'plans.empty' | translate }}
              </td></tr>
            </ng-template>
          </p-table>
        </div>
      }
    </div>

    <!-- Create / edit -->
    <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '480px' }"
              [header]="(editing() ? 'plans.edit' : 'plans.create') | translate" [closable]="!saving()">
      <div class="space-y-3">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'plans.code' | translate }} *</label>
            <input pInputText [(ngModel)]="form.code" class="w-full uppercase" [disabled]="editing()"
                   [class.ng-invalid]="submitted() && !form.code.trim()"
                   [class.ng-dirty]="submitted() && !form.code.trim()" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'plans.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full"
                   [class.ng-invalid]="submitted() && !form.name.trim()"
                   [class.ng-dirty]="submitted() && !form.name.trim()" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'plans.monthlyPrice' | translate }} *</label>
            <input pInputText type="number" [(ngModel)]="form.monthlyPrice" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'plans.annualPrice' | translate }}</label>
            <input pInputText type="number" [(ngModel)]="form.annualPrice" class="w-full" />
          </div>
        </div>
        <p class="text-xs text-gray-400">{{ 'plans.limitHint' | translate }}</p>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'plans.maxCashRegisters' | translate }}</label>
            <input pInputText type="number" [(ngModel)]="form.maxCashRegisters" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'plans.maxUsers' | translate }}</label>
            <input pInputText type="number" [(ngModel)]="form.maxUsers" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'plans.maxProducts' | translate }}</label>
            <input pInputText type="number" [(ngModel)]="form.maxProducts" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'plans.maxProductImages' | translate }}</label>
            <input pInputText type="number" [(ngModel)]="form.maxProductImages" class="w-full" />
          </div>
        </div>
        @if (editing()) {
          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" [(ngModel)]="form.active" />
            {{ 'plans.active' | translate }}
          </label>
        }
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
export class PlanListPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly confirm = inject(ConfirmationService);
  private readonly toast = inject(MessageService);
  private readonly translate = inject(TranslateService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<PlanRow[]>([]);
  protected readonly editing = signal(false);
  protected readonly submitted = signal(false);

  protected dialogOpen = false;
  protected form: {
    id: string | null; code: string; name: string;
    monthlyPrice: number | null; annualPrice: number | null;
    maxCashRegisters: number | null; maxUsers: number | null;
    maxProducts: number | null; maxProductImages: number | null; active: boolean;
  } = this.emptyForm();

  ngOnInit(): void {
    void this.load();
  }

  protected lim(v: number | null): string {
    return v == null ? '∞' : String(v);
  }

  private emptyForm() {
    return {
      id: null, code: '', name: '', monthlyPrice: 0, annualPrice: null,
      maxCashRegisters: null, maxUsers: null, maxProducts: null, maxProductImages: null, active: true,
    };
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.rows.set((await firstValueFrom(this.http.get<PlanRow[]>('/api/v1/plans/all'))) ?? []);
    } catch {
      this.rows.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  protected openCreate(): void {
    this.editing.set(false);
    this.submitted.set(false);
    this.form = this.emptyForm();
    this.dialogOpen = true;
  }

  protected openEdit(row: PlanRow): void {
    this.editing.set(true);
    this.submitted.set(false);
    this.form = { ...row };
    this.dialogOpen = true;
  }

  protected async save(): Promise<void> {
    this.submitted.set(true);
    if (!this.form.name.trim() || (!this.editing() && !this.form.code.trim())) return;
    const body = {
      name: this.form.name.trim(),
      monthlyPrice: num(this.form.monthlyPrice) ?? 0,
      annualPrice: num(this.form.annualPrice),
      maxCashRegisters: num(this.form.maxCashRegisters),
      maxUsers: num(this.form.maxUsers),
      maxProducts: num(this.form.maxProducts),
      maxProductImages: num(this.form.maxProductImages),
    };
    this.saving.set(true);
    try {
      if (this.editing()) {
        await firstValueFrom(this.http.put(`/api/v1/plans/${this.form.id}`, { ...body, active: this.form.active }));
      } else {
        await firstValueFrom(this.http.post('/api/v1/plans', { ...body, code: this.form.code.trim().toUpperCase() }));
      }
      this.dialogOpen = false;
      this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t('common.success') });
      void this.load();
    } catch (err: unknown) {
      this.toast.add({ severity: 'error', summary: this.t('common.error'), detail: this.apiMessage(err) });
    } finally {
      this.saving.set(false);
    }
  }

  protected remove(row: PlanRow): void {
    this.confirm.confirm({
      header: this.t('common.confirmation'),
      message: this.translate.instant('plans.confirmDelete', { name: row.name }),
      icon: 'pi pi-trash',
      accept: async () => {
        try {
          await firstValueFrom(this.http.delete(`/api/v1/plans/${row.id}`));
          this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t('common.success') });
          void this.load();
        } catch (err: unknown) {
          this.toast.add({ severity: 'error', summary: this.t('common.error'), detail: this.apiMessage(err) });
        }
      },
    });
  }

  private apiMessage(err: unknown): string {
    const body = (err as { error?: unknown })?.error;
    return isApiError(body) ? body.message : this.t('common.error_generic');
  }

  private t(key: string): string {
    return this.translate.instant(key);
  }
}

function num(v: number | null): number | null {
  return v === null || (v as unknown as string) === '' || Number.isNaN(v) ? null : Number(v);
}
