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
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface Supplier {
  id: string;
  code: string;
  type: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  taxId: string | null;
  paymentTerms: string | null;
  currency: string;
  notes: string | null;
  creditLimit: number;
  active: boolean;
  balance: number;
}

interface SupplierForm {
  code: string;
  type: string;
  name: string;
  email: string;
  phone: string;
  address: string;
  taxId: string;
  paymentTerms: string;
  currency: string;
  notes: string;
  creditLimit: number;
}

@Component({
  selector: 'erp-admin-supplier-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule,
    InputTextModule, InputNumberModule, ButtonModule, DialogModule, DropdownModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'suppliers.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'suppliers.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'suppliers.create' | translate"
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

        <p-table [value]="suppliers()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'suppliers.code' | translate }}</th>
              <th>{{ 'suppliers.name' | translate }}</th>
              <th>{{ 'suppliers.phone' | translate }}</th>
              <th>{{ 'suppliers.email' | translate }}</th>
              <th>{{ 'suppliers.paymentTerms' | translate }}</th>
              <th class="text-right">{{ 'suppliers.balance' | translate }}</th>
              <th>{{ 'suppliers.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-s>
            <tr>
              <td><span class="font-mono text-sm">{{ s.code }}</span></td>
              <td class="font-medium">{{ s.name }}</td>
              <td>{{ s.phone || '—' }}</td>
              <td>{{ s.email || '—' }}</td>
              <td>{{ s.paymentTerms || '—' }}</td>
              <td class="text-right font-medium"
                  [class.text-red-600]="s.balance > 0"
                  [class.text-green-600]="s.balance < 0">
                {{ s.balance | money }} {{ s.currency }}
              </td>
              <td>
                <p-tag [value]="(s.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="s.active ? 'success' : 'secondary'" />
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                        [pTooltip]="'common.edit' | translate"
                        (click)="openEdit(s)"></button>
                @if (s.active) {
                  <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.deactivate' | translate"
                          (click)="confirmDelete(s)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'suppliers.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="(editing() ? 'suppliers.editTitle' : 'suppliers.createTitle') | translate"
                [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'suppliers.code' | translate }} *</label>
              <input pInputText [(ngModel)]="form.code" [disabled]="!!editing()"
                     (input)="onCodeInput()" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'suppliers.type' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.type" [options]="typeOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'suppliers.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'suppliers.email' | translate }}</label>
              <input pInputText type="email" [(ngModel)]="form.email" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'suppliers.phone' | translate }}</label>
              <input pInputText [(ngModel)]="form.phone" class="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'suppliers.address' | translate }}</label>
            <input pInputText [(ngModel)]="form.address" class="w-full" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'suppliers.taxId' | translate }}</label>
              <input pInputText [(ngModel)]="form.taxId" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'suppliers.paymentTerms' | translate }}</label>
              <input pInputText [(ngModel)]="form.paymentTerms" class="w-full" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'suppliers.currency' | translate }}</label>
              <input pInputText [(ngModel)]="form.currency" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'suppliers.creditLimit' | translate }}</label>
              <p-inputNumber [(ngModel)]="form.creditLimit" mode="decimal" [maxFractionDigits]="2"
                             styleClass="w-full" />
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
    </div>
  `,
})
export class SupplierListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);

  protected suppliers = signal<Supplier[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected dialogOpen = false;
  protected editing = signal<Supplier | null>(null);
  protected form: SupplierForm = this.emptyForm();
  protected codeAutoFilled = false;
  protected typeOptions: Array<{ value: string; label: string }> = [];
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    this.refreshTypeOptions();
    this.i18n.onLangChange.subscribe(() => this.refreshTypeOptions());
    this.load();
  }

  private refreshTypeOptions() {
    this.typeOptions = ['COMPANY', 'INDIVIDUAL'].map(v => ({
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

  protected onCodeInput() {
    this.codeAutoFilled = false;
  }

  private async fetchSuggestedCode() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ code: string }>(`/api/v1/suppliers/next-code`)
      );
      if (this.codeAutoFilled) this.form.code = res.code;
    } catch {
      /* silent */
    }
  }

  protected openEdit(s: Supplier) {
    this.editing.set(s);
    this.form = {
      code: s.code,
      type: s.type,
      name: s.name,
      email: s.email ?? '',
      phone: s.phone ?? '',
      address: s.address ?? '',
      taxId: s.taxId ?? '',
      paymentTerms: s.paymentTerms ?? '',
      currency: s.currency ?? 'MRU',
      notes: s.notes ?? '',
      creditLimit: s.creditLimit ?? 0,
    };
    this.codeAutoFilled = false;
    this.dialogOpen = true;
  }

  protected confirmDelete(s: Supplier) {
    this.confirmation.confirm({
      message: `Désactiver le fournisseur « ${s.name} » ?`,
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.delete(`/api/v1/suppliers/${s.id}`));
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
        type: this.form.type || 'COMPANY',
        name: this.form.name.trim(),
        email: this.form.email || null,
        phone: this.form.phone || null,
        address: this.form.address || null,
        taxId: this.form.taxId || null,
        paymentTerms: this.form.paymentTerms || null,
        currency: this.form.currency || 'MRU',
        notes: this.form.notes || null,
        creditLimit: this.form.creditLimit || 0,
      };
      const current = this.editing();
      if (current) {
        await firstValueFrom(this.http.put(`/api/v1/suppliers/${current.id}`, payload));
      } else {
        await firstValueFrom(this.http.post('/api/v1/suppliers', payload));
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
        this.http.get<{ content: Supplier[] }>(`/api/v1/suppliers${params}`)
      );
      this.suppliers.set(res.content ?? []);
    } catch {
      this.suppliers.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private emptyForm(): SupplierForm {
    return {
      code: '', type: 'COMPANY', name: '', email: '', phone: '', address: '',
      taxId: '', paymentTerms: '', currency: 'MRU', notes: '', creditLimit: 0,
    };
  }
}
