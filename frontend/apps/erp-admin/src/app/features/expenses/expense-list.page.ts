import { Component, OnInit, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface Expense {
  id: string;
  expenseNumber: string;
  categoryId: string;
  amount: number;
  expenseDate: string;
  description: string | null;
  paymentMethod: string;
  paymentStatus: string;
  paidAmount: number;
  balance: number;
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'NOT_REQUIRED';
  recurring: boolean;
}

interface Category { id: string; name: string; }

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-expense-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, DialogModule, DropdownModule, InputTextModule, InputNumberModule,
    TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'expenses.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'expenses.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'expenses.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="expenses()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'expenses.number' | translate }}</th>
              <th>{{ 'expenses.date' | translate }}</th>
              <th>{{ 'expenses.category' | translate }}</th>
              <th>{{ 'expenses.description' | translate }}</th>
              <th class="text-right">{{ 'expenses.amount' | translate }}</th>
              <th>{{ 'expenses.paymentStatus' | translate }}</th>
              <th>{{ 'expenses.approval' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-e>
            <tr>
              <td><span class="font-mono text-sm">{{ e.expenseNumber }}</span></td>
              <td>{{ e.expenseDate }}</td>
              <td>{{ categoryName(e.categoryId) }}</td>
              <td class="text-sm">{{ e.description || '—' }}</td>
              <td class="text-right font-semibold">{{ e.amount | number:'1.0-2' }}</td>
              <td><p-tag [value]="e.paymentStatus" [severity]="paymentSeverity(e.paymentStatus)" /></td>
              <td><p-tag [value]="e.approvalStatus" [severity]="approvalSeverity(e.approvalStatus)" /></td>
              <td>
                @if (e.approvalStatus === 'PENDING') {
                  <button pButton icon="pi pi-check" class="p-button-sm p-button-text p-button-success"
                          (click)="approve(e.id)" [pTooltip]="'common.approve' | translate"></button>
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          (click)="reject(e.id)" [pTooltip]="'common.reject' | translate"></button>
                }
                @if (canPay(e)) {
                  <button pButton icon="pi pi-wallet" class="p-button-sm p-button-text"
                          (click)="openPay(e)" [pTooltip]="'expenses.pay' | translate"></button>
                }
                <a [href]="'/api/v1/expenses/' + e.id + '/receipt.pdf'" target="_blank"
                   class="p-button p-button-sm p-button-text" [title]="'expenses.pdf' | translate">
                  <i class="pi pi-file-pdf"></i>
                </a>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">
              {{ 'expenses.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="'expenses.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'expenses.category' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.categoryId" [options]="categories()"
                        optionLabel="name" optionValue="id"
                        [styleClass]="'w-full' + (categoryInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (categoryInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'expenses.date' | translate }} *</label>
              <input pInputText type="date" [(ngModel)]="form.expenseDate" class="w-full"
                     [class.ng-invalid]="dateInvalid()" [class.ng-dirty]="dateInvalid()" />
              @if (dateInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'expenses.amount' | translate }} *</label>
              <p-inputNumber [(ngModel)]="form.amount" mode="decimal" [maxFractionDigits]="2"
                             [styleClass]="'w-full' + (amountInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (amountInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.mustBePositive' | translate }}</p>
              }
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'expenses.description' | translate }}</label>
            <input pInputText [(ngModel)]="form.description" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'expenses.paymentMethod' | translate }}</label>
            <p-dropdown [(ngModel)]="form.paymentMethod" [options]="paymentMethods"
                        optionLabel="label" optionValue="value" styleClass="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'expenses.recurrence' | translate }}</label>
            <input pInputText [(ngModel)]="form.recurrenceRule"
                   [placeholder]="'expenses.recurrenceHint' | translate" class="w-full" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Pay (settle) an expense via a party-less CASH_OUT payment -->
      <p-dialog [(visible)]="payOpen" [modal]="true" [style]="{ width: '440px' }"
                [header]="'expenses.payTitle' | translate" [closable]="!paying()">
        <div class="space-y-3">
          <div class="flex items-center justify-between bg-gray-50 border rounded p-3">
            <span class="text-sm text-gray-600">{{ 'expenses.payAmount' | translate }}</span>
            <span class="font-bold text-lg">{{ payAmount() | number:'1.0-2' }}</span>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'expenses.paymentMethod' | translate }}</label>
              <p-dropdown [(ngModel)]="payForm.method" [options]="payMethods"
                          optionLabel="label" optionValue="value" styleClass="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'expenses.date' | translate }}</label>
              <input pInputText type="date" [(ngModel)]="payForm.paymentDate" class="w-full" />
            </div>
          </div>
          @if (payNeedsBankAccount()) {
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'payments.bankAccount' | translate }} *</label>
              <p-dropdown [(ngModel)]="payForm.bankAccountId" [options]="bankAccounts()"
                          optionLabel="name" optionValue="id" styleClass="w-full" />
            </div>
          }
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'expenses.reference' | translate }}</label>
            <input pInputText [(ngModel)]="payForm.reference" class="w-full" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="payOpen = false" [disabled]="paying()"></button>
          <button pButton [label]="'expenses.pay' | translate" icon="pi pi-check"
                  (click)="confirmPay()" [loading]="paying()" [disabled]="!canConfirmPay()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class ExpenseListPage implements OnInit {
  private http = inject(HttpClient);

  protected expenses = signal<Expense[]>([]);
  protected categories = signal<Category[]>([]);
  protected bankAccounts = signal<{ id: string; name: string }[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;

  protected categoryInvalid(): boolean { return this.submitted() && !this.form.categoryId; }
  protected dateInvalid(): boolean { return this.submitted() && !this.form.expenseDate; }
  protected amountInvalid(): boolean {
    return this.submitted() && (this.form.amount == null || this.form.amount <= 0);
  }

  protected readonly paymentMethods = [
    { value: 'CASH', label: 'Espèces' },
    { value: 'BANK', label: 'Virement' },
    { value: 'CHECK', label: 'Chèque' },
    { value: 'MOBILE_MONEY', label: 'Mobile Money' },
    { value: 'UNPAID', label: 'Non payée' },
  ];

  // Cash methods for the settlement (Payer) dialog — these map to the payment
  // module's PaymentMethod enum (note BANK_TRANSFER, not BANK).
  protected readonly payMethods = [
    { value: 'CASH', label: 'Espèces' },
    { value: 'BANK_TRANSFER', label: 'Virement' },
    { value: 'CHECK', label: 'Chèque' },
    { value: 'MOBILE_MONEY', label: 'Mobile Money' },
    { value: 'CARD', label: 'Carte' },
  ];

  protected form = this.emptyForm();

  // ── Pay (settle) an expense ─────────────────────────────────────────────────
  protected payOpen = false;
  protected paying = signal(false);
  protected payExpense = signal<Expense | null>(null);
  protected payForm = { method: 'CASH', paymentDate: '', reference: '', bankAccountId: null as string | null };

  /** Non-cash methods settle on a bank account; cash hits the vault. */
  protected payNeedsBankAccount(): boolean { return this.payForm.method !== 'CASH'; }
  protected canConfirmPay(): boolean {
    return this.payAmount() > 0 && (!this.payNeedsBankAccount() || !!this.payForm.bankAccountId);
  }

  ngOnInit() {
    this.loadCategories();
    this.loadBankAccounts();
    // auto-loaded by p-table lazy
  }

  protected categoryName(id: string) { return this.categories().find(c => c.id === id)?.name ?? '—'; }

  protected paymentSeverity(s: string): Severity {
    return s === 'PAID' ? 'success' : s === 'PARTIAL' ? 'warning' : 'danger';
  }
  protected approvalSeverity(s: string): Severity {
    return s === 'APPROVED' ? 'success' : s === 'REJECTED' ? 'danger'
         : s === 'PENDING' ? 'warning' : 'secondary';
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.form.expenseDate = new Date().toISOString().slice(0, 10);
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.categoryId || !this.form.expenseDate) return;
    if (this.form.amount == null || this.form.amount <= 0) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/expenses', {
        categoryId: this.form.categoryId,
        amount: this.form.amount,
        expenseDate: this.form.expenseDate,
        description: this.form.description || null,
        paymentMethod: this.form.paymentMethod,
        recurrenceRule: this.form.recurrenceRule || null,
        notes: null,
      }));
      this.dialogOpen = false;
      this.reload();
    } finally { this.saving.set(false); }
  }

  protected async approve(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/expenses/${id}/approve`, {}));
    this.reload();
  }

  protected async reject(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/expenses/${id}/reject`, {}));
    this.reload();
  }

  /** An expense is payable once it's UNPAID and either needs no approval or is
   *  approved — PENDING/REJECTED expenses are not settleable. */
  protected canPay(e: Expense): boolean {
    return e.paymentStatus === 'UNPAID'
        && (e.approvalStatus === 'APPROVED' || e.approvalStatus === 'NOT_REQUIRED');
  }

  protected payAmount(): number {
    const e = this.payExpense();
    if (!e) return 0;
    return e.balance ?? (e.amount - (e.paidAmount || 0));
  }

  protected openPay(e: Expense) {
    this.payExpense.set(e);
    this.payForm = { method: 'CASH', paymentDate: new Date().toISOString().slice(0, 10), reference: '', bankAccountId: null };
    this.payOpen = true;
  }

  /** Settle the expense by creating + confirming a party-less CASH_OUT payment
   *  allocated to the expense. The confirm callback marks the expense PAID. */
  protected async confirmPay() {
    const e = this.payExpense();
    if (!e) return;
    const amount = this.payAmount();
    if (amount <= 0 || !this.canConfirmPay()) return;
    this.paying.set(true);
    try {
      const created = await firstValueFrom(this.http.post<{ id: string }>('/api/v1/payments', {
        type: 'CASH_OUT',
        partyId: null,
        amount,
        currency: 'MRU',
        paymentDate: this.payForm.paymentDate || null,
        method: this.payForm.method,
        reference: this.payForm.reference || null,
        bankAccount: null,
        bankAccountId: this.payNeedsBankAccount() ? this.payForm.bankAccountId : null,
        notes: null,
        allocations: [{ targetType: 'EXPENSE', targetId: e.id, allocatedAmount: amount }],
      }));
      await firstValueFrom(this.http.post(`/api/v1/payments/${created.id}/confirm`, {}));
      this.payOpen = false;
      this.reload();
    } finally {
      this.paying.set(false);
    }
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Expense[]; totalElements: number }>(
          `/api/v1/expenses?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.expenses()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.expenses.set(arr);
      this.total.set(totalElements);
    } catch {
      this.expenses.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.expenses.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadCategories() {
    try {
      const list = await firstValueFrom(
        this.http.get<Category[]>('/api/v1/expense-categories')
      );
      this.categories.set(list ?? []);
    } catch { this.categories.set([]); }
  }

  /** Active treasury bank accounts — target for non-cash expense settlement. */
  private async loadBankAccounts() {
    try {
      const list = await firstValueFrom(
        this.http.get<{ id: string; name: string }[]>('/api/v1/treasury/bank-accounts')
      );
      this.bankAccounts.set(list ?? []);
    } catch { this.bankAccounts.set([]); }
  }

  private emptyForm() {
    return {
      categoryId: null as string | null,
      amount: 0,
      expenseDate: '',
      description: '',
      paymentMethod: 'CASH',
      recurrenceRule: '',
    };
  }
}
