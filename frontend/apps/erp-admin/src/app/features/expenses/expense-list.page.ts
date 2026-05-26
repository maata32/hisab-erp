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
        <p-table [value]="expenses()" [loading]="loading()" stripedRows responsiveLayout="scroll"
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
    </div>
  `,
})
export class ExpenseListPage implements OnInit {
  private http = inject(HttpClient);

  protected expenses = signal<Expense[]>([]);
  protected categories = signal<Category[]>([]);
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

  protected form = this.emptyForm();

  ngOnInit() {
    this.loadCategories();
    this.load();
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
      this.load();
    } finally { this.saving.set(false); }
  }

  protected async approve(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/expenses/${id}/approve`, {}));
    this.load();
  }

  protected async reject(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/expenses/${id}/reject`, {}));
    this.load();
  }

  private async load() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Expense[] }>('/api/v1/expenses')
      );
      this.expenses.set(res.content ?? []);
    } catch { this.expenses.set([]); }
    finally { this.loading.set(false); }
  }

  private async loadCategories() {
    try {
      const list = await firstValueFrom(
        this.http.get<Category[]>('/api/v1/expense-categories')
      );
      this.categories.set(list ?? []);
    } catch { this.categories.set([]); }
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
