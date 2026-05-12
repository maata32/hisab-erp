import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface Payment {
  id: string;
  number: string;
  type: string;
  partyId: string;
  partyName: string;
  paymentDate: string;
  amount: number;
  currency: string;
  method: string;
  reference: string | null;
  status: string;
  notes: string | null;
}

interface CustomerLite { id: string; code: string; name: string; }

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-payment-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule, ButtonModule,
    DialogModule, DropdownModule, InputTextModule, InputNumberModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'payments.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'payments.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'payments.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <div class="flex flex-wrap gap-3 mb-3 items-end">
          <div>
            <label class="block text-xs text-gray-500 mb-1">{{ 'payments.filterParty' | translate }}</label>
            <p-dropdown [(ngModel)]="filterPartyId" [options]="customers()"
                        optionLabel="name" optionValue="id"
                        [showClear]="true"
                        [placeholder]="'payments.allParties' | translate"
                        (onChange)="load()" styleClass="w-64" />
          </div>
        </div>

        <p-table [value]="payments()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'payments.number' | translate }}</th>
              <th>{{ 'payments.party' | translate }}</th>
              <th>{{ 'payments.date' | translate }}</th>
              <th class="text-right">{{ 'payments.amount' | translate }}</th>
              <th>{{ 'payments.method' | translate }}</th>
              <th>{{ 'payments.reference' | translate }}</th>
              <th>{{ 'payments.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-p>
            <tr>
              <td><span class="font-mono text-sm">{{ p.number }}</span></td>
              <td>{{ p.partyName }}</td>
              <td>{{ p.paymentDate | date:'mediumDate' }}</td>
              <td class="text-right font-medium">{{ p.amount | number:'1.2-2' }} {{ p.currency }}</td>
              <td>{{ 'payments.methods.' + p.method | translate }}</td>
              <td class="text-sm text-gray-500">{{ p.reference || '—' }}</td>
              <td>
                <p-tag [value]="'payments.statuses.' + p.status | translate"
                       [severity]="statusSeverity(p.status)" />
              </td>
              <td class="whitespace-nowrap">
                @if (p.status === 'DRAFT') {
                  <button pButton icon="pi pi-check" class="p-button-sm p-button-text p-button-success"
                          [pTooltip]="'payments.confirm' | translate"
                          (click)="confirmPayment(p)"></button>
                }
                @if (p.status !== 'CANCELLED') {
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.cancel' | translate"
                          (click)="cancelPayment(p)"></button>
                }
                <a [href]="'/api/v1/payments/' + p.id + '/receipt.pdf'" target="_blank"
                   class="p-button p-button-sm p-button-text" [title]="'payments.receipt' | translate">
                  <i class="pi pi-file-pdf"></i>
                </a>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">
              {{ 'payments.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="'payments.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.kind' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.type" [options]="typeOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.method' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.method" [options]="methodOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'payments.party' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.partyId" [options]="customers()"
                        optionLabel="name" optionValue="id" [filter]="true" filterBy="name,code"
                        styleClass="w-full" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.date' | translate }} *</label>
              <input pInputText type="date" [(ngModel)]="form.paymentDate" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.amount' | translate }} *</label>
              <p-inputNumber [(ngModel)]="form.amount" mode="decimal" [maxFractionDigits]="2"
                             styleClass="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'payments.reference' | translate }}</label>
            <input pInputText [(ngModel)]="form.reference" class="w-full" />
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
export class PaymentListPage implements OnInit {
  private http = inject(HttpClient);

  protected payments = signal<Payment[]>([]);
  protected customers = signal<CustomerLite[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected dialogOpen = false;
  protected filterPartyId: string | null = null;

  protected readonly typeOptions = [
    { value: 'CUSTOMER', label: 'Encaissement client' },
    { value: 'SUPPLIER', label: 'Décaissement fournisseur' },
  ];

  protected readonly methodOptions = [
    { value: 'CASH', label: 'Espèces' },
    { value: 'BANK_TRANSFER', label: 'Virement' },
    { value: 'CHECK', label: 'Chèque' },
    { value: 'MOBILE_MONEY', label: 'Mobile Money' },
    { value: 'CARD', label: 'Carte' },
  ];

  protected form = this.emptyForm();

  ngOnInit() {
    this.loadCustomers();
    this.load();
  }

  protected statusSeverity(s: string): Severity {
    return ({
      DRAFT: 'secondary', CONFIRMED: 'success', CANCELLED: 'danger',
    } as Record<string, Severity>)[s] ?? 'secondary';
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.form.paymentDate = new Date().toISOString().slice(0, 10);
    this.dialogOpen = true;
  }

  protected async save() {
    if (!this.form.partyId || !this.form.amount) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/payments', {
        type: this.form.type,
        partyId: this.form.partyId,
        amount: this.form.amount,
        currency: 'MRU',
        paymentDate: this.form.paymentDate || null,
        method: this.form.method,
        reference: this.form.reference || null,
        bankAccount: null,
        notes: this.form.notes || null,
        allocations: [],
      }));
      this.dialogOpen = false;
      this.load();
    } finally {
      this.saving.set(false);
    }
  }

  protected async confirmPayment(p: Payment) {
    await firstValueFrom(this.http.post(`/api/v1/payments/${p.id}/confirm`, {}));
    this.load();
  }

  protected async cancelPayment(p: Payment) {
    if (!confirm(`Annuler le paiement ${p.number} ?`)) return;
    await firstValueFrom(this.http.post(`/api/v1/payments/${p.id}/cancel`, {}));
    this.load();
  }

  protected async load() {
    this.loading.set(true);
    try {
      const params = this.filterPartyId ? `?partyId=${this.filterPartyId}` : '';
      const res = await firstValueFrom(
        this.http.get<{ content: Payment[] }>(`/api/v1/payments${params}`)
      );
      this.payments.set(res.content ?? []);
    } catch {
      this.payments.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadCustomers() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: CustomerLite[] }>('/api/v1/customers?size=500')
      );
      this.customers.set(res.content ?? []);
    } catch {
      this.customers.set([]);
    }
  }

  private emptyForm() {
    return {
      type: 'CUSTOMER',
      partyId: null as string | null,
      amount: 0,
      paymentDate: '',
      method: 'CASH',
      reference: '',
      notes: '',
    };
  }
}
