import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
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
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    InputTextModule, InputNumberModule, ButtonModule, DialogModule, DropdownModule, TooltipModule,
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
              <td class="text-right">{{ c.creditLimit | number:'1.0-0' }} {{ c.currency }}</td>
              <td>
                <p-tag [value]="(c.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="c.active ? 'success' : 'secondary'" />
              </td>
              <td class="whitespace-nowrap">
                <a [href]="'/api/v1/customers/' + c.id + '/statement.pdf'" target="_blank"
                   class="p-button p-button-sm p-button-text" [title]="'customers.statement' | translate">
                  <i class="pi pi-file-pdf"></i>
                </a>
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
            <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'customers.empty' | translate }}</td></tr>
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
              <input pInputText [(ngModel)]="form.code" [disabled]="!!editing()" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.type' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.type" [options]="typeOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full" />
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
    </div>
  `,
})
export class CustomerListPage implements OnInit {
  private http = inject(HttpClient);

  protected customers = signal<Customer[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected dialogOpen = false;
  protected editing = signal<Customer | null>(null);
  protected form: CustomerForm = this.emptyForm();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly typeOptions = [
    { value: 'INDIVIDUAL', label: 'Particulier' },
    { value: 'BUSINESS', label: 'Entreprise' },
  ];

  ngOnInit() { this.load(); }

  protected onSearch(e: Event) {
    const q = (e.target as HTMLInputElement).value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(q), 300);
  }

  protected openCreate() {
    this.editing.set(null);
    this.form = this.emptyForm();
    this.dialogOpen = true;
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
    this.dialogOpen = true;
  }

  protected async confirmDelete(c: Customer) {
    if (!confirm(`Désactiver le client « ${c.name} » ?`)) return;
    await firstValueFrom(this.http.delete(`/api/v1/customers/${c.id}`));
    this.load();
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
