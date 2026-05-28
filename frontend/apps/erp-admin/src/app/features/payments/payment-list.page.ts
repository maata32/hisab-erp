import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '@minierp/shared-i18n';
import { ConfirmationService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface PaymentAllocation {
  id: string;
  targetType: string;
  targetId: string;
  allocatedAmount: number;
  notes: string | null;
}

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
  allocations: PaymentAllocation[];
}

interface CustomerLite { id: string; code: string; name: string; }

interface OpenInvoice {
  id: string;
  number: string;
  issueDate: string;
  dueDate: string;
  total: number;
  balance: number;
  currency: string;
  status: string;
}

interface AllocationForm {
  invoiceId: string;
  number: string;
  dueDate: string;
  balance: number;
  allocated: number;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-payment-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule, ButtonModule,
    DialogModule, DropdownModule, InputTextModule, InputNumberModule, CheckboxModule, TooltipModule,
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
                        (onChange)="reload()" styleClass="w-64" />
          </div>
        </div>

        <p-table #table [value]="payments()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
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
              <td class="text-right font-medium">{{ p.amount | money }} {{ p.currency }}</td>
              <td>{{ 'payments.methods.' + p.method | translate }}</td>
              <td class="text-sm text-gray-500">{{ p.reference || '—' }}</td>
              <td>
                <p-tag [value]="'payments.statuses.' + p.status | translate"
                       [severity]="statusSeverity(p.status)" />
              </td>
              <td class="whitespace-nowrap">
                @if (remaining(p) > 0 && p.status !== 'CANCELLED') {
                  <button pButton icon="pi pi-sitemap" class="p-button-sm p-button-text"
                          [pTooltip]="('payments.allocateTooltip' | translate) + ' ' + (remaining(p) | money) + ' ' + p.currency"
                          (click)="openAllocate(p)"></button>
                }
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
                <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text"
                        [pTooltip]="'payments.receipt' | translate"
                        (click)="printPdf('/api/v1/payments/' + p.id + '/receipt.pdf')"></button>
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

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '780px' }"
                [header]="'payments.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.kind' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.type" [options]="typeOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full"
                          (onChange)="onTypeChange()" />
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
                        (onChange)="onPartyChange()"
                        [styleClass]="'w-full' + (partyInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (partyInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.date' | translate }} *</label>
              <input pInputText type="date" [(ngModel)]="form.paymentDate" class="w-full"
                     [class.ng-invalid]="paymentDateInvalid()" [class.ng-dirty]="paymentDateInvalid()" />
              @if (paymentDateInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.amount' | translate }} *</label>
              <p-inputNumber [(ngModel)]="form.amount" mode="decimal" [maxFractionDigits]="2"
                             (onInput)="onAmountChange()"
                             [styleClass]="'w-full' + (amountInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (amountInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.mustBePositive' | translate }}</p>
              }
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

          @if (form.type === 'CUSTOMER_PAYMENT' && form.partyId) {
            <div class="border rounded">
              <div class="flex items-center justify-between p-2 bg-gray-50 border-b">
                <span class="font-medium text-sm">{{ 'payments.allocations' | translate }}</span>
                <button pButton icon="pi pi-bolt" [label]="'payments.autoAllocate' | translate"
                        class="p-button-sm p-button-text"
                        [disabled]="!form.amount || openInvoices().length === 0"
                        (click)="autoAllocate()"></button>
              </div>
              @if (openInvoices().length === 0) {
                <div class="p-4 text-center text-gray-400 text-sm">
                  {{ 'payments.noOpenInvoices' | translate }}
                </div>
              } @else {
                <table class="w-full text-sm">
                  <thead class="bg-gray-50 text-gray-600">
                    <tr>
                      <th class="text-left p-2">{{ 'sales.number' | translate }}</th>
                      <th class="text-left p-2 w-32">{{ 'invoices.dueDate' | translate }}</th>
                      <th class="text-right p-2 w-32">{{ 'invoices.balance' | translate }}</th>
                      <th class="text-right p-2 w-32">{{ 'payments.allocated' | translate }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (a of form.allocations; track a.invoiceId) {
                      <tr class="border-t">
                        <td class="p-2 font-mono text-xs">{{ a.number }}</td>
                        <td class="p-2 text-gray-600">{{ a.dueDate | date:'mediumDate' }}</td>
                        <td class="p-2 text-right text-gray-700">{{ a.balance | money }}</td>
                        <td class="p-1">
                          <p-inputNumber [(ngModel)]="a.allocated" [min]="0" [max]="a.balance"
                                         [minFractionDigits]="2" [maxFractionDigits]="2"
                                         (onInput)="onAllocationEdit()"
                                         inputStyleClass="w-full text-right" styleClass="w-full" />
                        </td>
                      </tr>
                    }
                  </tbody>
                  <tfoot class="bg-gray-50 border-t">
                    <tr>
                      <td colspan="3" class="p-2 text-right font-medium">{{ 'payments.totalAllocated' | translate }}</td>
                      <td class="p-2 text-right font-bold"
                          [class.text-red-600]="!allocationValid()"
                          [class.text-green-600]="allocationValid() && totalAllocated() > 0">
                        {{ totalAllocated() | money }} / {{ form.amount | money }}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              }
            </div>
          }
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Post-hoc allocation dialog -->
      <p-dialog [(visible)]="allocateOpen" [modal]="true" [style]="{ width: '780px' }"
                [header]="('payments.allocateTitle' | translate) + ' — ' + (allocatePayment()?.number ?? '')"
                [closable]="!savingAllocate()">
        <div class="space-y-3">
          <div class="grid grid-cols-3 gap-3 bg-gray-50 p-3 rounded border">
            <div>
              <div class="text-xs text-gray-500">{{ 'payments.amount' | translate }}</div>
              <div class="font-bold">{{ allocatePayment()?.amount | money }} {{ allocatePayment()?.currency }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-500">{{ 'payments.alreadyAllocated' | translate }}</div>
              <div class="font-bold text-gray-700">{{ alreadyAllocated() | money }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-500">{{ 'payments.remaining' | translate }}</div>
              <div class="font-bold text-blue-600">{{ remainingToAllocate() | money }}</div>
            </div>
          </div>

          <div class="border rounded">
            <div class="flex items-center justify-between p-2 bg-gray-50 border-b">
              <span class="font-medium text-sm">{{ 'payments.allocations' | translate }}</span>
              <button pButton icon="pi pi-bolt" [label]="'payments.autoAllocate' | translate"
                      class="p-button-sm p-button-text"
                      [disabled]="allocateInvoices().length === 0 || remainingToAllocate() <= 0"
                      (click)="autoAllocateExisting()"></button>
            </div>
            @if (allocateInvoices().length === 0) {
              <div class="p-4 text-center text-gray-400 text-sm">
                {{ 'payments.noOpenInvoices' | translate }}
              </div>
            } @else {
              <table class="w-full text-sm">
                <thead class="bg-gray-50 text-gray-600">
                  <tr>
                    <th class="text-left p-2">{{ 'sales.number' | translate }}</th>
                    <th class="text-left p-2 w-32">{{ 'invoices.dueDate' | translate }}</th>
                    <th class="text-right p-2 w-32">{{ 'invoices.balance' | translate }}</th>
                    <th class="text-right p-2 w-32">{{ 'payments.allocated' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (a of allocateForm.allocations; track a.invoiceId) {
                    <tr class="border-t">
                      <td class="p-2 font-mono text-xs">{{ a.number }}</td>
                      <td class="p-2 text-gray-600">{{ a.dueDate | date:'mediumDate' }}</td>
                      <td class="p-2 text-right text-gray-700">{{ a.balance | money }}</td>
                      <td class="p-1">
                        <p-inputNumber [(ngModel)]="a.allocated" [min]="0" [max]="a.balance"
                                       [minFractionDigits]="2" [maxFractionDigits]="2"
                                       inputStyleClass="w-full text-right" styleClass="w-full" />
                      </td>
                    </tr>
                  }
                </tbody>
                <tfoot class="bg-gray-50 border-t">
                  <tr>
                    <td colspan="3" class="p-2 text-right font-medium">{{ 'payments.totalAllocated' | translate }}</td>
                    <td class="p-2 text-right font-bold"
                        [class.text-red-600]="allocateOver()"
                        [class.text-green-600]="!allocateOver() && allocateTotal() > 0">
                      {{ allocateTotal() | money }} / {{ remainingToAllocate() | money }}
                    </td>
                  </tr>
                </tfoot>
              </table>
            }
          </div>

          <div class="text-xs text-gray-500 italic">
            {{ 'payments.surplusInfo' | translate }}
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="allocateOpen = false" [disabled]="savingAllocate()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="saveAllocate()" [loading]="savingAllocate()"
                  [disabled]="!canSaveAllocate()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class PaymentListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected payments = signal<Payment[]>([]);
  protected customers = signal<CustomerLite[]>([]);
  protected openInvoices = signal<OpenInvoice[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;
  protected filterPartyId: string | null = null;

  protected partyInvalid(): boolean { return this.submitted() && !this.form.partyId; }
  protected paymentDateInvalid(): boolean { return this.submitted() && !this.form.paymentDate; }
  protected amountInvalid(): boolean {
    return this.submitted() && (this.form.amount == null || this.form.amount <= 0);
  }

  // Post-hoc allocation dialog state
  protected allocateOpen = false;
  protected savingAllocate = signal(false);
  protected allocatePayment = signal<Payment | null>(null);
  protected allocateInvoices = signal<OpenInvoice[]>([]);
  protected allocateForm: {
    allocations: AllocationForm[];
  } = { allocations: [] };

  protected readonly typeOptions = [
    { value: 'CUSTOMER_PAYMENT', label: 'Encaissement client' },
    { value: 'CUSTOMER_REFUND', label: 'Retrait client' },
  ];

  protected readonly methodOptions = [
    { value: 'CASH', label: 'Espèces' },
    { value: 'BANK_TRANSFER', label: 'Virement' },
    { value: 'CHECK', label: 'Chèque' },
    { value: 'MOBILE_MONEY', label: 'Mobile Money' },
    { value: 'CARD', label: 'Carte' },
  ];

  protected form = this.emptyForm();
  private allocationsUserEdited = false;

  ngOnInit() {
    this.loadCustomers();
    // Payments are fetched on demand via the p-table's onLazyLoad.
    const invoiceId = this.route.snapshot.queryParamMap.get('createForInvoice');
    if (invoiceId) {
      this.openCreateForInvoice(invoiceId);
      this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
  }

  private async openCreateForInvoice(invoiceId: string) {
    try {
      const inv = await firstValueFrom(
        this.http.get<{ id: string; customerId: string; balance: number }>(`/api/v1/invoices/${invoiceId}`)
      );
      this.openCreate();
      this.form.type = 'CUSTOMER_PAYMENT';
      this.form.partyId = inv.customerId;
      this.form.amount = Number(inv.balance);
      await this.onPartyChange();
      // Override the FIFO distribution: allocate only to the target invoice,
      // matching the user's clear intent ("paye cette facture").
      const target = this.form.allocations.find(a => a.invoiceId === invoiceId);
      if (target) {
        for (const a of this.form.allocations) a.allocated = 0;
        target.allocated = Math.min(this.form.amount, target.balance);
        this.allocationsUserEdited = true;
      }
    } catch {
      // Fall back to a blank create dialog if the invoice fetch fails.
      this.openCreate();
    }
  }

  protected statusSeverity(s: string): Severity {
    return ({
      DRAFT: 'secondary', CONFIRMED: 'success', CANCELLED: 'danger',
    } as Record<string, Severity>)[s] ?? 'secondary';
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.form.paymentDate = new Date().toISOString().slice(0, 10);
    this.openInvoices.set([]);
    this.allocationsUserEdited = false;
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected onTypeChange() {
    // Allocations only apply to incoming customer payments (CUSTOMER_PAYMENT).
    this.form.allocations = [];
    this.openInvoices.set([]);
    this.allocationsUserEdited = false;
    if (this.form.type === 'CUSTOMER_PAYMENT' && this.form.partyId) {
      this.onPartyChange();
    }
  }

  protected async onPartyChange() {
    this.form.allocations = [];
    this.openInvoices.set([]);
    this.allocationsUserEdited = false;
    if (this.form.type !== 'CUSTOMER_PAYMENT' || !this.form.partyId) return;
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: OpenInvoice[] }>(
          `/api/v1/invoices?customerId=${this.form.partyId}&size=200`
        )
      );
      const open = (res.content ?? [])
        .filter(i => Number(i.balance) > 0 && i.status !== 'CANCELLED')
        .sort((a, b) => (a.dueDate ?? a.issueDate).localeCompare(b.dueDate ?? b.issueDate));
      this.openInvoices.set(open);
      this.form.allocations = open.map(i => ({
        invoiceId: i.id,
        number: i.number,
        dueDate: i.dueDate,
        balance: Number(i.balance),
        allocated: 0,
      }));
      // If an amount is already entered, auto-allocate FIFO so the user sees
      // the distribution immediately instead of an all-zero table.
      if ((this.form.amount || 0) > 0 && open.length > 0) this.autoAllocate();
    } catch {
      this.openInvoices.set([]);
    }
  }

  protected onAmountChange() {
    // Re-run auto-allocate FIFO as long as the user hasn't directly typed in
    // an allocation cell. Once they manually edit a row, we leave their
    // distribution alone — they can re-trigger via the "Auto-allouer" button.
    if (this.form.type !== 'CUSTOMER_PAYMENT' || this.form.allocations.length === 0) return;
    if (!this.allocationsUserEdited) this.autoAllocate();
  }

  protected onAllocationEdit() {
    this.allocationsUserEdited = true;
  }

  protected autoAllocate() {
    this.allocationsUserEdited = false;
    let remaining = this.form.amount || 0;
    for (const a of this.form.allocations) {
      const take = Math.min(remaining, a.balance);
      a.allocated = +take.toFixed(2);
      remaining = +(remaining - take).toFixed(2);
      if (remaining <= 0) {
        // zero out the rest
        for (const b of this.form.allocations) {
          if (b !== a && remaining <= 0 && this.form.allocations.indexOf(b) > this.form.allocations.indexOf(a)) {
            b.allocated = 0;
          }
        }
        return;
      }
    }
  }

  protected totalAllocated(): number {
    return +this.form.allocations.reduce((s, a) => s + (a.allocated || 0), 0).toFixed(2);
  }

  protected allocationValid(): boolean {
    if (this.form.type !== 'CUSTOMER_PAYMENT') return true;
    if (this.openInvoices().length === 0) return true; // no open invoices = unallocated payment is fine
    const sum = this.totalAllocated();
    const amount = +Number(this.form.amount || 0).toFixed(2);
    return sum <= amount;
  }

  protected allocationSurplus(): number {
    const amount = +Number(this.form.amount || 0).toFixed(2);
    return +(amount - this.totalAllocated()).toFixed(2);
  }

  protected canSave(): boolean {
    return !!this.form.partyId
        && !!this.form.amount && this.form.amount > 0
        && this.allocationValid();
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.canSave()) return;
    this.saving.set(true);
    try {
      const allocations: Array<{ targetType: string; targetId: string; allocatedAmount: number }> =
        this.form.allocations
          .filter(a => (a.allocated || 0) > 0)
          .map(a => ({
            targetType: 'SALE_INVOICE',
            targetId: a.invoiceId,
            allocatedAmount: a.allocated,
          }));
      // Any unspent portion is parked on the customer's running balance as
      // an advance/credit, consistent with the post-hoc allocate flow.
      if (this.form.type === 'CUSTOMER_PAYMENT' && this.form.partyId) {
        const allocatedSum = allocations.reduce((s, a) => s + a.allocatedAmount, 0);
        const surplus = +((this.form.amount || 0) - allocatedSum).toFixed(2);
        if (surplus > 0) {
          allocations.push({
            targetType: 'CUSTOMER_BALANCE',
            targetId: this.form.partyId,
            allocatedAmount: surplus,
          });
        }
      }
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
        allocations,
      }));
      this.dialogOpen = false;
      this.reload();
    } finally {
      this.saving.set(false);
    }
  }

  protected async confirmPayment(p: Payment) {
    await firstValueFrom(this.http.post(`/api/v1/payments/${p.id}/confirm`, {}));
    this.reload();
  }

  // ── Post-hoc allocation ───────────────────────────────────────────────────

  protected remaining(p: Payment): number {
    const allocated = (p.allocations ?? []).reduce((s, a) => s + Number(a.allocatedAmount || 0), 0);
    return +(Number(p.amount) - allocated).toFixed(2);
  }

  protected alreadyAllocated(): number {
    const p = this.allocatePayment();
    if (!p) return 0;
    return +(Number(p.amount) - this.remaining(p)).toFixed(2);
  }

  protected remainingToAllocate(): number {
    const p = this.allocatePayment();
    return p ? this.remaining(p) : 0;
  }

  protected allocateTotal(): number {
    return +this.allocateForm.allocations.reduce((s, a) => s + (a.allocated || 0), 0).toFixed(2);
  }

  protected allocateOver(): boolean {
    return this.allocateTotal() > this.remainingToAllocate();
  }

  protected canSaveAllocate(): boolean {
    // Always valid unless over-allocated — any unspent remainder will be parked
    // on the customer credit balance.
    return !this.allocateOver() && this.remainingToAllocate() > 0;
  }

  protected async openAllocate(p: Payment) {
    this.allocatePayment.set(p);
    this.allocateForm = { allocations: [] };
    this.allocateInvoices.set([]);
    this.allocateOpen = true;
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: OpenInvoice[] }>(
          `/api/v1/invoices?customerId=${p.partyId}&size=200`
        )
      );
      const open = (res.content ?? [])
        .filter(i => Number(i.balance) > 0 && i.status !== 'CANCELLED')
        .sort((a, b) => (a.dueDate ?? a.issueDate).localeCompare(b.dueDate ?? b.issueDate));
      this.allocateInvoices.set(open);
      this.allocateForm.allocations = open.map(i => ({
        invoiceId: i.id,
        number: i.number,
        dueDate: i.dueDate,
        balance: Number(i.balance),
        allocated: 0,
      }));
      // Pre-fill FIFO with the remaining-to-allocate amount.
      if (this.remainingToAllocate() > 0 && open.length > 0) this.autoAllocateExisting();
    } catch {
      this.allocateInvoices.set([]);
    }
  }

  protected autoAllocateExisting() {
    let remaining = this.remainingToAllocate();
    for (const a of this.allocateForm.allocations) {
      const take = Math.min(remaining, a.balance);
      a.allocated = +take.toFixed(2);
      remaining = +(remaining - take).toFixed(2);
      if (remaining <= 0) {
        // zero out subsequent rows
        const idx = this.allocateForm.allocations.indexOf(a);
        for (let i = idx + 1; i < this.allocateForm.allocations.length; i++) {
          this.allocateForm.allocations[i].allocated = 0;
        }
        return;
      }
    }
  }

  protected async saveAllocate() {
    if (!this.canSaveAllocate()) return;
    const p = this.allocatePayment();
    if (!p) return;
    this.savingAllocate.set(true);
    try {
      const allocations = this.allocateForm.allocations
        .filter(a => (a.allocated || 0) > 0)
        .map(a => ({
          targetType: 'SALE_INVOICE',
          targetId: a.invoiceId,
          allocatedAmount: a.allocated,
        }));
      // Any unspent portion is always parked on the customer's running balance
      // (advance/credit). User can later use it to pay future invoices.
      const surplus = +(this.remainingToAllocate() - this.allocateTotal()).toFixed(2);
      if (surplus > 0) {
        allocations.push({
          targetType: 'CUSTOMER_BALANCE',
          targetId: p.partyId,
          allocatedAmount: surplus,
        });
      }
      await firstValueFrom(this.http.post(`/api/v1/payments/${p.id}/allocate`, { allocations }));
      this.allocateOpen = false;
      this.reload();
    } finally {
      this.savingAllocate.set(false);
    }
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

  protected cancelPayment(p: Payment) {
    this.confirmation.confirm({
      message: `Annuler le paiement ${p.number} ?`,
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.post(`/api/v1/payments/${p.id}/cancel`, {}));
        this.reload();
      },
    });
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    const filter = this.filterPartyId ? `&partyId=${this.filterPartyId}` : '';
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Payment[]; totalElements: number }>(
          `/api/v1/payments?page=${page}&size=${rows}${filter}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.payments()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.payments.set(arr);
      this.total.set(totalElements);
    } catch {
      this.payments.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.payments.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadCustomers() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: CustomerLite[] }>('/api/v1/partners?role=CUSTOMER&size=500')
      );
      this.customers.set(res.content ?? []);
    } catch {
      this.customers.set([]);
    }
  }

  private emptyForm() {
    return {
      type: 'CUSTOMER_PAYMENT',
      partyId: null as string | null,
      amount: 0,
      paymentDate: '',
      method: 'CASH',
      reference: '',
      notes: '',
      allocations: [] as AllocationForm[],
    };
  }
}
