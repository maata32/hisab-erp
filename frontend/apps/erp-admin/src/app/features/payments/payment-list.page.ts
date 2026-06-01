import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe, MoneyFormatService } from '@minierp/shared-i18n';
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

interface PartnerLite {
  id: string;
  code: string;
  name: string;
  isCustomer?: boolean;
  isSupplier?: boolean;
  customerCreditBalance?: number;
  currency?: string;
}

/** Cash direction from the operator's till: IN = money received, OUT = money paid. */
type Direction = 'IN' | 'OUT';

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
    DialogModule, DropdownModule, InputTextModule, InputNumberModule,
    CheckboxModule, TooltipModule,
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
            <p-dropdown [(ngModel)]="filterPartyId" [options]="parties()"
                        optionLabel="name" optionValue="id"
                        [showClear]="true" [filter]="true" filterBy="name,code"
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
              <th>{{ 'payments.kind' | translate }}</th>
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
              <td>
                <p-tag [value]="'payments.types.' + p.type | translate"
                       [severity]="typeSeverity(p.type)" />
              </td>
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
                @if (p.status === 'DRAFT') {
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
            <tr><td colspan="9" class="text-center text-gray-400 py-8">
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
              <label class="block text-sm font-medium mb-1">{{ 'payments.directionLabel' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.direction" [options]="directionOptions"
                          optionLabel="label" optionValue="value"
                          [disabled]="directionLocked"
                          (onChange)="onTypeChange()" styleClass="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.method' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.method" [options]="methodOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'payments.party' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.partyId" [options]="parties()"
                        optionLabel="name" optionValue="id" [filter]="true" filterBy="name,code"
                        [disabled]="partyLocked"
                        (onChange)="onPartyChange()"
                        [styleClass]="'w-full' + (partyInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (partyInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
            @if (form.partyId) {
              <p class="text-xs text-gray-500 mt-1">
                {{ 'payments.derivedTypeHint' | translate }}
                <strong>{{ 'payments.types.' + derivedType() | translate }}</strong>
              </p>
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
              <p-inputNumber [(ngModel)]="form.amount" mode="decimal"
                             [minFractionDigits]="moneyFormat.decimalPlaces()"
                             [maxFractionDigits]="moneyFormat.decimalPlaces()"
                             [suffix]="' ' + selectedCustomerCurrency()"
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

          @if (isCustomerInvoiceFlow() && form.partyId && selectedCustomerCredit() > 0) {
            <div class="p-3 rounded border border-sky-300 bg-sky-50 text-sm text-sky-900">
              <i class="pi pi-info-circle mr-1"></i>
              {{ 'payments.customerHasCredit' | translate:{ amount: selectedCustomerCreditFormatted() } }}
            </div>
          }

          @if (isCustomerRefundFlow() && form.partyId && partyCredits.length > 0) {
            <div class="border rounded">
              <div class="flex items-center justify-between p-2 bg-sky-50 border-b">
                <span class="font-medium text-sm text-sky-900">{{ 'payments.imputeOnDeposits' | translate }}</span>
                <span class="text-xs text-sky-700">{{ 'payments.imputeHint' | translate }}</span>
              </div>
              <table class="w-full text-sm">
                <thead class="bg-gray-50 text-gray-600">
                  <tr>
                    <th class="w-10 p-2"></th>
                    <th class="text-left p-2">{{ 'payments.imputeSource' | translate }}</th>
                    <th class="text-right p-2 w-40">{{ 'payments.imputeOpen' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (c of partyCredits; track c.sourceId) {
                    <tr class="border-t">
                      <td class="p-2 text-center">
                        <input type="checkbox" [(ngModel)]="c.selected" />
                      </td>
                      <td class="p-2">{{ c.label }}</td>
                      <td class="p-2 text-right text-gray-700">{{ c.amountOpen | money }} {{ selectedCustomerCurrency() }}</td>
                    </tr>
                  }
                </tbody>
                <tfoot class="bg-gray-50 border-t">
                  <tr>
                    <td colspan="2" class="p-2 text-right font-medium">{{ 'payments.imputedTotal' | translate }}</td>
                    <td class="p-2 text-right font-bold text-sky-800">{{ imputedAmount() | money }} {{ selectedCustomerCurrency() }}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          }

          @if (isInvoiceFlow() && form.partyId) {
            <div class="border rounded">
              <div class="flex items-center justify-between p-2 bg-gray-50 border-b">
                <span class="font-medium text-sm">{{ 'payments.allocations' | translate }}</span>
                <span [pTooltip]="autoAllocateTooltip()" tooltipPosition="left">
                  <button pButton icon="pi pi-bolt" [label]="'payments.autoAllocate' | translate"
                          class="p-button-sm p-button-text"
                          [disabled]="!form.amount || openInvoices().length === 0"
                          (click)="autoAllocate()"></button>
                </span>
              </div>
              @if (openInvoices().length === 0) {
                <div class="p-4 text-center text-gray-400 text-sm">
                  {{ isSupplierInvoiceFlow() ? ('payments.noOpenSupplierInvoices' | translate)
                                             : ('payments.noOpenInvoices' | translate) }}
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
                                         [minFractionDigits]="moneyFormat.decimalPlaces()"
                                         [maxFractionDigits]="moneyFormat.decimalPlaces()"
                                         (onInput)="onAllocationEdit()"
                                         inputStyleClass="w-full text-right" styleClass="w-full" />
                        </td>
                      </tr>
                    }
                    @if (createSurplus() > 0) {
                      <tr class="border-t bg-amber-50">
                        <td class="p-2 italic text-amber-800" colspan="3">
                          {{ 'payments.surplusRowLabel' | translate }}
                        </td>
                        <td class="p-2 text-right font-bold text-amber-800">
                          {{ createSurplus() | money }}
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
                        {{ totalAllocated() | money }} / {{ form.amount > 0 ? (form.amount | money) : '—' }}
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
                  @if (allocateSurplus() > 0) {
                    <tr class="border-t bg-amber-50">
                      <td class="p-2 italic text-amber-800" colspan="3">
                        {{ 'payments.surplusRowLabel' | translate }}
                      </td>
                      <td class="p-2 text-right font-bold text-amber-800">
                        {{ allocateSurplus() | money }}
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
  protected moneyFormat = inject(MoneyFormatService);

  protected payments = signal<Payment[]>([]);
  protected parties = signal<PartnerLite[]>([]);
  protected openInvoices = signal<OpenInvoice[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;
  /** When true, the dialog was opened from a specific invoice: the cash direction
   * is forced to a customer payment and the party is the invoice's customer, so
   * both dropdowns are disabled. */
  protected directionLocked = false;
  protected partyLocked = false;
  protected filterPartyId: string | null = null;

  protected partyInvalid(): boolean { return this.submitted() && !this.form.partyId; }

  /** The currently selected party (in the create dialog), or undefined. */
  protected selectedParty(): PartnerLite | undefined {
    return this.parties().find(c => c.id === this.form.partyId);
  }

  /**
   * Backend payment type derived from the chosen cash direction and the party's
   * roles. The user never picks this directly — they pick "Versement/Retrait"
   * (direction) + a party, and the four enum values fall out:
   *   IN  + customer  → CUSTOMER_PAYMENT   (the customer pays what they owe)
   *   IN  + supplier  → SUPPLIER_REFUND    (a supplier refunds us)
   *   OUT + supplier  → SUPPLIER_PAYMENT   (we settle a supplier invoice)
   *   OUT + customer  → CUSTOMER_REFUND    (we refund a customer)
   * For a dual-role party the direction is the tie-breaker (IN favours the
   * customer side, OUT the supplier side).
   */
  protected derivedType(): string {
    const p = this.selectedParty();
    if (this.form.direction === 'IN') {
      return p?.isCustomer ? 'CUSTOMER_PAYMENT' : 'SUPPLIER_REFUND';
    }
    return p?.isSupplier ? 'SUPPLIER_PAYMENT' : 'CUSTOMER_REFUND';
  }

  /** True when the derived type settles invoices on the customer side. */
  protected isCustomerInvoiceFlow(): boolean { return this.derivedType() === 'CUSTOMER_PAYMENT'; }
  /** True when the derived type settles invoices on the supplier side. */
  protected isSupplierInvoiceFlow(): boolean { return this.derivedType() === 'SUPPLIER_PAYMENT'; }
  /** True for any flow that allocates against open invoices (customer or supplier). */
  protected isInvoiceFlow(): boolean { return this.isCustomerInvoiceFlow() || this.isSupplierInvoiceFlow(); }
  /** True when the derived type is a customer refund (can be settled from credit). */
  protected isCustomerRefundFlow(): boolean { return this.derivedType() === 'CUSTOMER_REFUND'; }

  protected selectedCustomerCredit(): number {
    return Number(this.selectedParty()?.customerCreditBalance ?? 0);
  }
  protected selectedCustomerCurrency(): string {
    return this.selectedParty()?.currency ?? 'MRU';
  }
  protected selectedCustomerCreditFormatted(): string {
    return `${this.moneyFormat.format(this.selectedCustomerCredit())} ${this.selectedCustomerCurrency()}`;
  }
  protected autoAllocateTooltip(): string {
    if (this.openInvoices().length === 0) return this.i18n.instant('payments.noOpenInvoices');
    if (!this.form.amount) return this.i18n.instant('payments.enterAmountFirst');
    return '';
  }

  // Imputation versement↔retrait: a customer Retrait (CUSTOMER_REFUND) can be
  // imputed on the party's open deposits/credits (CUSTOMER_CREDIT open items),
  // consuming them via the allocation engine instead of being a pure cash-out.
  // Each row carries a checkbox the user can untick to leave that credit alone.
  protected partyCredits: { sourceId: string; amountOpen: number; label: string; selected: boolean }[] = [];

  private async refreshPartyCredits() {
    this.partyCredits = [];
    if (!this.form.partyId) return;
    try {
      const items = await firstValueFrom(
        this.http.get<{ sourceType: string; sourceId: string; amountOpen: number; label: string }[]>(
          `/api/v1/allocations/open-items?partyId=${this.form.partyId}`));
      this.partyCredits = (items ?? [])
        .filter(i => i.sourceType === 'CUSTOMER_CREDIT')
        .map(i => ({ sourceId: i.sourceId, amountOpen: Number(i.amountOpen), label: i.label, selected: true }));
    } catch {
      this.partyCredits = [];
    }
  }

  /** Total that will be imputed (consumed) from the selected open credits as a
   *  traceability link — capped by the payment amount. Cash out is always the
   *  full payment amount; imputation never reduces it. */
  protected imputedAmount(): number {
    const selected = this.partyCredits.filter(c => c.selected)
      .reduce((s, c) => s + (c.amountOpen || 0), 0);
    return Math.min(this.form.amount || 0, +selected.toFixed(2));
  }

  // SUPPLIER_PAYMENT (retrait) ← open supplier "versements" (SUPPLIER_REFUND)
  // imputation — mirror of partyCredits. A versement surfaces in the engine as
  // a POSITIVE SUPPLIER_PAYMENT open item, so we keep only those whose payment
  // type is SUPPLIER_REFUND (cash the supplier actually gave back).
  protected supplierRefunds: { sourceId: string; amountOpen: number; label: string; selected: boolean }[] = [];

  private async refreshSupplierRefunds() {
    this.supplierRefunds = [];
    if (!this.form.partyId || !this.isSupplierInvoiceFlow()) return;
    try {
      const [items, pays] = await Promise.all([
        firstValueFrom(this.http.get<{ sourceType: string; sourceId: string; amountOpen: number; label: string }[]>(
          `/api/v1/allocations/open-items?partyId=${this.form.partyId}`)),
        firstValueFrom(this.http.get<{ content: Payment[] }>(
          `/api/v1/payments?partyId=${this.form.partyId}&size=200`)),
      ]);
      const refundIds = new Set((pays.content ?? [])
        .filter(p => p.type === 'SUPPLIER_REFUND')
        .map(p => p.id));
      this.supplierRefunds = (items ?? [])
        .filter(i => i.sourceType === 'SUPPLIER_PAYMENT' && refundIds.has(i.sourceId))
        .map(i => ({ sourceId: i.sourceId, amountOpen: Number(i.amountOpen), label: i.label, selected: true }));
    } catch {
      this.supplierRefunds = [];
    }
  }

  /** Total imputed from the selected supplier versements (traceability link,
   *  capped by the retrait amount). Cash out stays the full retrait amount. */
  protected supplierImputed(): number {
    const selected = this.supplierRefunds.filter(c => c.selected)
      .reduce((s, c) => s + (c.amountOpen || 0), 0);
    return Math.min(this.form.amount || 0, +selected.toFixed(2));
  }
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

  // The two cash directions the user picks. Labels are translated in the
  // template via the i18n keys below; the values drive derivedType().
  protected directionOptions: Array<{ value: Direction; label: string }> = [];
  private refreshDirectionOptions() {
    this.directionOptions = [
      { value: 'IN', label: this.i18n.instant('payments.direction.in') },
      { value: 'OUT', label: this.i18n.instant('payments.direction.out') },
    ];
  }

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
    this.refreshDirectionOptions();
    this.i18n.onLangChange.subscribe(() => this.refreshDirectionOptions());
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
      this.form.direction = 'IN'; // money in from the customer → CUSTOMER_PAYMENT
      this.directionLocked = true; // invoice-driven flow → user can't switch to Retrait
      this.partyLocked = true;     // … nor change the party (it's the invoice's customer)
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

  /** Green for cash IN (customer payment / supplier refund), red for cash OUT. */
  protected typeSeverity(t: string): Severity {
    return ({
      CUSTOMER_PAYMENT: 'success', SUPPLIER_REFUND: 'success',
      CUSTOMER_DEPOSIT: 'success', CUSTOMER_REFUND: 'danger',
      SUPPLIER_PAYMENT: 'danger', CUSTOMER_CREDIT_WITHDRAWAL: 'danger',
    } as Record<string, Severity>)[t] ?? 'secondary';
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.form.paymentDate = new Date().toISOString().slice(0, 10);
    this.openInvoices.set([]);
    this.allocationsUserEdited = false;
    this.directionLocked = false;
    this.partyLocked = false;
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected onTypeChange() {
    // Re-derive eligible invoices: direction change can flip customer↔supplier.
    this.form.allocations = [];
    this.openInvoices.set([]);
    this.allocationsUserEdited = false;
    if (this.form.partyId) {
      this.onPartyChange();
    }
  }

  protected async onPartyChange() {
    this.form.allocations = [];
    this.openInvoices.set([]);
    this.allocationsUserEdited = false;
    this.refreshPartyCredits();
    this.refreshSupplierRefunds();
    if (!this.isInvoiceFlow() || !this.form.partyId) return;
    // Customer flow → sales invoices; supplier flow → purchase invoices. Both
    // sit on the NEGATIVE side and are settled FIFO by the payment.
    try {
      const open = this.isSupplierInvoiceFlow()
        ? await this.fetchOpenSupplierInvoices(this.form.partyId)
        : await this.fetchOpenCustomerInvoices(this.form.partyId);
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

  /** Open customer (sales) invoices for a party, FIFO-sorted by due/issue date. */
  private async fetchOpenCustomerInvoices(partyId: string): Promise<OpenInvoice[]> {
    const res = await firstValueFrom(
      this.http.get<{ content: OpenInvoice[] }>(
        `/api/v1/invoices?customerId=${partyId}&size=200`
      )
    );
    return (res.content ?? [])
      .filter(i => Number(i.balance) > 0 && i.status !== 'CANCELLED')
      .sort((a, b) => (a.dueDate ?? a.issueDate).localeCompare(b.dueDate ?? b.issueDate));
  }

  /** Open supplier (purchase) invoices for a party, FIFO-sorted by due date. */
  private async fetchOpenSupplierInvoices(partyId: string): Promise<OpenInvoice[]> {
    const res = await firstValueFrom(
      this.http.get<{ content: Array<{ id: string; number: string; invoiceDate: string;
        dueDate: string; total: number; balance: number; currency: string; status: string }> }>(
        `/api/v1/purchase-invoices?supplierId=${partyId}&size=200`
      )
    );
    return (res.content ?? [])
      .filter(i => Number(i.balance) > 0 && (i.status === 'ISSUED' || i.status === 'PARTIAL'))
      .map(i => ({
        id: i.id, number: i.number, issueDate: i.invoiceDate, dueDate: i.dueDate,
        total: Number(i.total), balance: Number(i.balance), currency: i.currency, status: i.status,
      }))
      .sort((a, b) => (a.dueDate ?? a.issueDate).localeCompare(b.dueDate ?? b.issueDate));
  }

  protected onAmountChange() {
    // Re-run FIFO if allocations were never customized. If the user customized
    // the distribution, preserve it as long as it remains valid (totalAllocated
    // ≤ new amount). On overflow, force a FIFO redistribution so the dialog
    // never displays an invalid total like "15000 / 4000".
    if (!this.isInvoiceFlow() || this.form.allocations.length === 0) return;
    if (!this.allocationsUserEdited) {
      this.autoAllocate();
      return;
    }
    if (this.totalAllocated() > (this.form.amount || 0)) {
      this.autoAllocate();
    }
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
    if (!this.isInvoiceFlow()) return true;
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

  protected createSurplus(): number {
    // Only a customer payment mints an OVERPAYMENT credit from its surplus.
    // Supplier payments don't (grantCredit is customer-only); any unallocated
    // remainder there simply leaves the payment partly open.
    if (!this.isCustomerInvoiceFlow()) return 0;
    return Math.max(0, +((this.form.amount || 0) - this.totalAllocated()).toFixed(2));
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.canSave()) return;
    const type = this.derivedType();
    // Build the allocation targets from the derived type:
    //  - CUSTOMER_PAYMENT → SALE_INVOICE lines + surplus→CUSTOMER_CREDIT
    //  - SUPPLIER_PAYMENT → PURCHASE_INVOICE lines (no surplus credit)
    //  - CUSTOMER_REFUND / SUPPLIER_REFUND → no allocation (refund settled from
    //    credit afterwards for customers; supplier refund is a free positive
    //    item the engine can re-impute later).
    const allocations: Array<{ targetType: string; targetId: string; allocatedAmount: number }> = [];
    if (this.isInvoiceFlow()) {
      const invoiceTarget = this.isSupplierInvoiceFlow() ? 'PURCHASE_INVOICE' : 'SALE_INVOICE';
      for (const a of this.form.allocations) {
        if ((a.allocated || 0) > 0) {
          allocations.push({ targetType: invoiceTarget, targetId: a.invoiceId, allocatedAmount: a.allocated });
        }
      }
    }
    const surplus = this.createSurplus();
    if (surplus > 0 && this.form.partyId) {
      // createSurplus() is non-zero only for CUSTOMER_PAYMENT → mint OVERPAYMENT
      // credit explicitly rather than via the silent CUSTOMER_BALANCE shortcut.
      allocations.push({ targetType: 'CUSTOMER_CREDIT', targetId: this.form.partyId, allocatedAmount: surplus });
    }
    this.saving.set(true);
    try {
      const created = await firstValueFrom(this.http.post<{ id: string }>('/api/v1/payments', {
        type,
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
      // When the Retrait is imputed on open deposits/credits we auto-confirm +
      // consume the selected credits in the same action — leaving the payment
      // DRAFT would let the user cancel it and orphan the allocation rows.
      if (type === 'CUSTOMER_REFUND' && this.partyCredits.some(c => c.selected)) {
        await firstValueFrom(this.http.post(`/api/v1/payments/${created.id}/confirm`, {}));
        await this.settleRefundFromCustomerCredits(created.id);
      }
      // Supplier retrait imputed on open supplier versements: same pattern —
      // confirm then link each selected versement (traceability, no cash moved).
      if (type === 'SUPPLIER_PAYMENT' && this.supplierRefunds.some(c => c.selected)) {
        await firstValueFrom(this.http.post(`/api/v1/payments/${created.id}/confirm`, {}));
        await this.settleSupplierRetraitFromRefunds(created.id);
      }
      this.dialogOpen = false;
      this.reload();
    } finally {
      this.saving.set(false);
    }
  }

  private async settleRefundFromCustomerCredits(refundPaymentId: string) {
    // FIFO consume across the party's active credits, capping each call to
    // its remaining_amount. The backend also caps to the refund payment's
    // amount, so over-requesting is safe.
    let remaining = Number(this.form.amount) || 0;
    for (const credit of this.partyCredits.filter(c => c.selected)) {
      if (remaining <= 0) break;
      const take = Math.min(remaining, credit.amountOpen);
      if (take <= 0) continue;
      try {
        await firstValueFrom(this.http.post('/api/v1/allocations/credit-to-refund', {
          creditId: credit.sourceId,
          refundPaymentId,
          amount: take,
        }));
        remaining -= take;
      } catch {
        // Best-effort: a single failure shouldn't unwind the refund payment.
      }
    }
  }

  private async settleSupplierRetraitFromRefunds(retraitPaymentId: string) {
    // FIFO link the supplier's open versements (SUPPLIER_REFUND) to this retrait.
    // The backend caps each call to the versement residual and the retrait
    // amount, so over-requesting is safe. Traceability only — no cash moved.
    let remaining = Number(this.form.amount) || 0;
    for (const refund of this.supplierRefunds.filter(c => c.selected)) {
      if (remaining <= 0) break;
      const take = Math.min(remaining, refund.amountOpen);
      if (take <= 0) continue;
      try {
        await firstValueFrom(this.http.post('/api/v1/allocations/supplier-refund-to-retrait', {
          refundPaymentId: refund.sourceId,
          retraitPaymentId,
          amount: take,
        }));
        remaining -= take;
      } catch {
        // Best-effort: a single failure shouldn't unwind the retrait payment.
      }
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
      // Same explicit handling as the create flow: the surplus row shown in
      // the dialog (allocateSurplus()) becomes a real CUSTOMER_CREDIT
      // allocation, granting an OVERPAYMENT credit when applied.
      const allocations: Array<{ targetType: string; targetId: string; allocatedAmount: number }> =
        this.allocateForm.allocations
          .filter(a => (a.allocated || 0) > 0)
          .map(a => ({
            targetType: 'SALE_INVOICE',
            targetId: a.invoiceId,
            allocatedAmount: a.allocated,
          }));
      const surplus = this.allocateSurplus();
      if (surplus > 0) {
        allocations.push({
          targetType: 'CUSTOMER_CREDIT',
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

  protected allocateSurplus(): number {
    return Math.max(0, +(this.remainingToAllocate() - this.allocateTotal()).toFixed(2));
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
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
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
    // All active partners (customers AND suppliers) — the payment dialog derives
    // the backend type from the chosen direction + the party's roles, so both
    // sides must be selectable. A party can be both (dual-role).
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: PartnerLite[] }>('/api/v1/partners?size=500')
      );
      this.parties.set(res.content ?? []);
    } catch {
      this.parties.set([]);
    }
  }

  private emptyForm() {
    return {
      direction: 'IN' as Direction,
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
