import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '@minierp/shared-i18n';
import { ConfirmationService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { CalendarModule } from 'primeng/calendar';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface Customer {
  id: string;
  code: string;
  type: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  creditLimit: number;
  currency: string;
  notes: string | null;
  active: boolean;
  balance: number;
}

interface CustomerForm {
  code: string;
  type: string;
  name: string;
  email: string;
  phone: string;
  address: string;
  creditLimit: number;
  currency: string;
  notes: string;
}

@Component({
  selector: 'erp-admin-customer-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule,
    InputTextModule, InputNumberModule, ButtonModule, DialogModule, DropdownModule, CalendarModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'customers.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'customers.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'customers.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <div class="mb-3">
          <span class="relative block w-full sm:w-72">
            <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"></i>
            <input pInputText type="text" [placeholder]="'common.search' | translate"
                   (input)="onSearch($event)" class="w-full !pl-9" />
          </span>
        </div>

        <p-table [value]="customers()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'customers.code' | translate }}</th>
              <th>{{ 'customers.name' | translate }}</th>
              <th>{{ 'customers.type' | translate }}</th>
              <th>{{ 'customers.email' | translate }}</th>
              <th>{{ 'customers.phone' | translate }}</th>
              <th class="text-right">{{ 'customers.creditLimit' | translate }}</th>
              <th class="text-right">{{ 'customers.balance' | translate }}</th>
              <th>{{ 'customers.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-c>
            <tr>
              <td><span class="font-mono text-sm">{{ c.code }}</span></td>
              <td class="font-medium">{{ c.name }}</td>
              <td>{{ 'customers.types.' + c.type | translate }}</td>
              <td>{{ c.email || '—' }}</td>
              <td>{{ c.phone || '—' }}</td>
              <td class="text-right">{{ c.creditLimit | money }} {{ c.currency }}</td>
              <td class="text-right font-medium"
                  [class.text-red-600]="c.balance > 0"
                  [class.text-green-600]="c.balance < 0">
                {{ c.balance | money }} {{ c.currency }}
              </td>
              <td>
                <p-tag [value]="(c.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="c.active ? 'success' : 'secondary'" />
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text"
                        [pTooltip]="'customers.statement' | translate"
                        (click)="openStatementDialog(c)"></button>
                <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                        [pTooltip]="'common.edit' | translate"
                        (click)="openEdit(c)"></button>
                @if (c.active) {
                  <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.deactivate' | translate"
                          (click)="confirmDelete(c)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="9" class="text-center text-gray-400 py-8">{{ 'customers.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="(editing() ? 'customers.editTitle' : 'customers.createTitle') | translate"
                [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.code' | translate }} *</label>
              <input pInputText [(ngModel)]="form.code" [disabled]="!!editing()"
                     (input)="onCodeInput()" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.type' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.type" [options]="typeOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full"
                          (onChange)="onTypeChange()" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'customers.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.email' | translate }}</label>
              <input pInputText type="email" [(ngModel)]="form.email" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.phone' | translate }}</label>
              <input pInputText [(ngModel)]="form.phone" class="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'customers.address' | translate }}</label>
            <input pInputText [(ngModel)]="form.address" class="w-full" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.creditLimit' | translate }}</label>
              <p-inputNumber [(ngModel)]="form.creditLimit" mode="decimal" [maxFractionDigits]="2"
                             styleClass="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.currency' | translate }}</label>
              <input pInputText [(ngModel)]="form.currency" class="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="form.notes" class="w-full" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Statement dialog -->
      <p-dialog [(visible)]="statementDialogOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="('customers.statementDialogTitle' | translate) + ' — ' + (statementCustomer()?.name ?? '')"
                [closable]="!fetchingStatement()">
        <div class="space-y-3">
          <p class="text-sm text-gray-600">{{ 'customers.statementDialogHint' | translate }}</p>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.statementFrom' | translate }}</label>
              <p-calendar [(ngModel)]="statementFrom" dateFormat="dd/mm/yy"
                          styleClass="w-full" appendTo="body" [showClear]="true" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.statementTo' | translate }}</label>
              <p-calendar [(ngModel)]="statementTo" dateFormat="dd/mm/yy"
                          styleClass="w-full" appendTo="body" [showClear]="true" />
            </div>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <div class="flex flex-wrap gap-2 justify-end">
            <button pButton [label]="'common.cancel' | translate" class="p-button-text p-button-sm"
                    (click)="statementDialogOpen = false" [disabled]="fetchingStatement()"></button>
            <button pButton icon="pi pi-list" [label]="'customers.statementFull' | translate"
                    class="p-button-sm p-button-outlined"
                    (click)="downloadStatement('full')" [loading]="fetchingStatement()"></button>
            <button pButton icon="pi pi-table" [label]="'customers.statementDetailed' | translate"
                    class="p-button-sm p-button-outlined"
                    (click)="downloadStatement('detailed')" [loading]="fetchingStatement()"></button>
            <button pButton icon="pi pi-exclamation-circle" [label]="'customers.statementOutstanding' | translate"
                    class="p-button-sm"
                    (click)="downloadStatement('outstanding')" [loading]="fetchingStatement()"></button>
          </div>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class CustomerListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);

  protected customers = signal<Customer[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected dialogOpen = false;
  protected editing = signal<Customer | null>(null);
  protected form: CustomerForm = this.emptyForm();
  protected codeAutoFilled = false;
  protected typeOptions: Array<{ value: string; label: string }> = [];
  protected statementDialogOpen = false;
  protected statementCustomer = signal<Customer | null>(null);
  protected fetchingStatement = signal(false);
  protected statementFrom: Date | null = new Date(new Date().getFullYear(), 0, 1);
  protected statementTo: Date | null = new Date();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    this.refreshTypeOptions();
    this.i18n.onLangChange.subscribe(() => this.refreshTypeOptions());
    this.load();
  }

  private refreshTypeOptions() {
    this.typeOptions = ['INDIVIDUAL', 'COMPANY'].map(v => ({
      value: v,
      label: this.i18n.instant('customers.types.' + v),
    }));
  }

  protected onSearch(e: Event) {
    const q = (e.target as HTMLInputElement).value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(q), 300);
  }

  protected openCreate() {
    this.editing.set(null);
    this.form = this.emptyForm();
    this.codeAutoFilled = true;
    this.dialogOpen = true;
    this.fetchSuggestedCode();
  }

  protected onTypeChange() {
    if (!this.editing() && this.codeAutoFilled) {
      this.fetchSuggestedCode();
    }
  }

  protected onCodeInput() {
    this.codeAutoFilled = false;
  }

  private async fetchSuggestedCode() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ code: string }>(
          `/api/v1/customers/next-code?type=${encodeURIComponent(this.form.type)}`
        )
      );
      if (this.codeAutoFilled) {
        this.form.code = res.code;
      }
    } catch {
      // silent — user can still type a code manually
    }
  }

  protected openEdit(c: Customer) {
    this.editing.set(c);
    this.form = {
      code: c.code,
      type: c.type,
      name: c.name,
      email: c.email ?? '',
      phone: c.phone ?? '',
      address: c.address ?? '',
      creditLimit: c.creditLimit ?? 0,
      currency: c.currency ?? 'MRU',
      notes: c.notes ?? '',
    };
    this.codeAutoFilled = false;
    this.dialogOpen = true;
  }

  protected openStatementDialog(c: Customer) {
    this.statementCustomer.set(c);
    this.statementFrom = new Date(new Date().getFullYear(), 0, 1);
    this.statementTo = new Date();
    this.statementDialogOpen = true;
  }

  protected async downloadStatement(type: 'full' | 'detailed' | 'outstanding') {
    const c = this.statementCustomer();
    if (!c) return;
    this.fetchingStatement.set(true);
    try {
      const params = new URLSearchParams({ type });
      if (this.statementFrom) params.set('from', this.toIsoDate(this.statementFrom));
      if (this.statementTo) params.set('to', this.toIsoDate(this.statementTo));
      await this.printPdf(`/api/v1/customers/${c.id}/statement.pdf?${params}`);
      this.statementDialogOpen = false;
    } finally {
      this.fetchingStatement.set(false);
    }
  }

  private toIsoDate(d: Date): string {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  protected async printPdf(url: string) {
    try {
      const blob = await firstValueFrom(this.http.get(url, { responseType: 'blob' }));
      const blobUrl = URL.createObjectURL(blob);
      window.open(blobUrl, '_blank');
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
    } catch (e) {
      console.error('PDF fetch failed', e);
    }
  }

  protected confirmDelete(c: Customer) {
    this.confirmation.confirm({
      message: `Désactiver le client « ${c.name} » ?`,
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.delete(`/api/v1/customers/${c.id}`));
        this.load();
      },
    });
  }

  protected async save() {
    if (!this.form.code?.trim() || !this.form.name?.trim()) return;
    this.saving.set(true);
    try {
      const payload = {
        code: this.form.code.trim(),
        type: this.form.type || 'INDIVIDUAL',
        name: this.form.name.trim(),
        email: this.form.email || null,
        phone: this.form.phone || null,
        address: this.form.address || null,
        creditLimit: this.form.creditLimit || 0,
        currency: this.form.currency || 'MRU',
        notes: this.form.notes || null,
        defaultPriceTierId: null,
        notificationPreferences: null,
      };
      const current = this.editing();
      if (current) {
        await firstValueFrom(this.http.put(`/api/v1/customers/${current.id}`, payload));
      } else {
        await firstValueFrom(this.http.post('/api/v1/customers', payload));
      }
      this.dialogOpen = false;
      this.load();
    } finally {
      this.saving.set(false);
    }
  }

  private async load(q?: string) {
    this.loading.set(true);
    try {
      const params = q ? `?q=${encodeURIComponent(q)}` : '';
      const res = await firstValueFrom(
        this.http.get<{ content: Customer[] }>(`/api/v1/customers${params}`)
      );
      this.customers.set(res.content ?? []);
    } catch {
      this.customers.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private emptyForm(): CustomerForm {
    return {
      code: '',
      type: 'INDIVIDUAL',
      name: '',
      email: '',
      phone: '',
      address: '',
      creditLimit: 0,
      currency: 'MRU',
      notes: '',
    };
  }
}
