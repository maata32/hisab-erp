import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TableModule } from 'primeng/table';
import { TabViewModule } from 'primeng/tabview';
import { CalendarModule } from 'primeng/calendar';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { firstValueFrom } from 'rxjs';

interface VaultDto {
  id: string;
  name: string;
  currency: string;
  balance: number;
}

interface BankAccountDto {
  id: string;
  name: string;
  bankName: string | null;
  accountNumber: string | null;
  currency: string;
  balance: number;
  active: boolean;
}

interface VaultMovementDto {
  id: string;
  type: string;
  amount: number;
  referenceType: string | null;
  referenceId: string | null;
  occurredAt: string;
  userId: string | null;
  note: string | null;
}

interface BankTxnDto {
  id: string;
  bankAccountId: string;
  type: string;
  amount: number;
  vaultMovementId: string | null;
  reference: string | null;
  occurredAt: string;
  userId: string | null;
  note: string | null;
}

interface PageResponse<T> { content: T[]; totalElements: number; }

interface CashSessionDto {
  id: string;
  registerId: string;
  cashierUserId: string;
  status: 'OPEN' | 'CLOSED' | 'VALIDATED';
  openedAt: string;
  closedAt: string | null;
  expectedClosing: number | null;
  countedClosing: number | null;
  difference: number | null;
  totalSales: number;
  totalCashIn: number;
  note: string | null;
}

@Component({
  selector: 'erp-admin-treasury',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule,
    ButtonModule, DialogModule, InputTextModule, InputNumberModule,
    TableModule, TabViewModule, CalendarModule, DropdownModule, TagModule, TooltipModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast position="top-right" />

    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'treasury.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'treasury.subtitle' | translate }}</p>
        </div>
        <div class="flex gap-2">
          <button pButton icon="pi pi-arrow-up-right"
                  [label]="'treasury.deposit' | translate"
                  severity="success" (click)="openDeposit()"
                  [disabled]="activeBanks().length === 0"></button>
          <button pButton icon="pi pi-arrow-down-left"
                  [label]="'treasury.withdraw' | translate"
                  severity="warning" (click)="openWithdraw()"
                  [disabled]="activeBanks().length === 0"></button>
        </div>
      </header>

      <!-- Balances -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <!-- Vault card -->
        <div class="bg-gradient-to-br from-primary-50 to-primary-100 border border-primary-200 rounded-lg shadow p-4">
          <div class="flex items-start justify-between">
            <div>
              <p class="text-xs uppercase text-primary-700 tracking-wide">{{ 'treasury.vault' | translate }}</p>
              <p class="text-sm font-medium text-primary-900 mt-1">{{ vault()?.name }}</p>
            </div>
            <button pButton icon="pi pi-pencil"
                    class="p-button-sm p-button-text"
                    [pTooltip]="'treasury.adjust' | translate"
                    (click)="openAdjustVault()"></button>
          </div>
          <p class="text-3xl font-bold text-primary-900 mt-3">
            {{ vault()?.balance | number:'1.0-2' }} <span class="text-base">{{ vault()?.currency }}</span>
          </p>
        </div>

        <!-- Bank accounts -->
        <div class="lg:col-span-2 bg-white border border-gray-200 rounded-lg shadow p-4">
          <div class="flex items-center justify-between mb-3">
            <h2 class="font-semibold text-gray-800">{{ 'treasury.banks' | translate }}</h2>
            <button pButton icon="pi pi-plus"
                    [label]="'treasury.newBank' | translate"
                    class="p-button-sm" (click)="openCreateBank()"></button>
          </div>

          @if (banks().length === 0) {
            <p class="text-sm text-gray-400 py-4 text-center">{{ 'treasury.noBanks' | translate }}</p>
          } @else {
            <div class="space-y-2">
              @for (b of banks(); track b.id) {
                <div class="flex items-center justify-between p-3 rounded border"
                     [class.bg-gray-50]="!b.active"
                     [class.border-gray-100]="b.active"
                     [class.border-gray-300]="!b.active">
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                      <p class="font-medium text-sm">{{ b.name }}</p>
                      @if (!b.active) {
                        <p-tag [value]="'common.inactive' | translate" severity="secondary" />
                      }
                    </div>
                    <p class="text-xs text-gray-500 mt-0.5">
                      {{ b.bankName || '—' }}
                      @if (b.accountNumber) { · {{ b.accountNumber }} }
                    </p>
                  </div>
                  <div class="text-right ml-3">
                    <p class="font-semibold">{{ b.balance | number:'1.0-2' }} <span class="text-xs">{{ b.currency }}</span></p>
                    <div class="flex gap-1 mt-1 justify-end">
                      <button pButton icon="pi pi-pencil"
                              class="p-button-sm p-button-text p-button-secondary"
                              [pTooltip]="'common.edit' | translate"
                              (click)="openEditBank(b)"></button>
                      <button pButton icon="pi pi-sliders-h"
                              class="p-button-sm p-button-text"
                              [pTooltip]="'treasury.adjust' | translate"
                              (click)="openAdjustBank(b)"></button>
                    </div>
                  </div>
                </div>
              }
            </div>
          }
        </div>
      </div>

      <!-- Pending session validations -->
      @if (pendingSessions().length > 0) {
        <div class="bg-amber-50 border border-amber-200 rounded-lg shadow p-4">
          <div class="flex items-center justify-between mb-3 flex-wrap gap-2">
            <div>
              <h2 class="font-semibold text-amber-900">
                <i class="pi pi-exclamation-triangle me-1"></i>
                {{ 'treasury.pendingSessions' | translate }}
                <span class="ml-2 px-2 py-0.5 bg-amber-200 text-amber-900 rounded-full text-xs">
                  {{ pendingSessions().length }}
                </span>
              </h2>
              <p class="text-xs text-amber-700 mt-0.5">{{ 'treasury.pendingHint' | translate }}</p>
            </div>
            <div class="flex items-center gap-2 text-sm">
              <label>
                <input type="checkbox" [(ngModel)]="onlyWithDiscrepancy" class="me-1" />
                {{ 'treasury.onlyWithDiscrepancy' | translate }}
              </label>
            </div>
          </div>
          <p-table [value]="filteredPendingSessions()" stripedRows responsiveLayout="scroll" styleClass="p-datatable-sm">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'treasury.closedAt' | translate }}</th>
                <th>{{ 'treasury.register' | translate }}</th>
                <th class="text-right">{{ 'treasury.expected' | translate }}</th>
                <th class="text-right">{{ 'treasury.counted' | translate }}</th>
                <th class="text-right">{{ 'treasury.discrepancy' | translate }}</th>
                <th>{{ 'treasury.note' | translate }}</th>
                <th></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-s>
              <tr>
                <td class="text-sm whitespace-nowrap">{{ s.closedAt | date:'short' }}</td>
                <td class="text-sm">{{ registerName(s.registerId) }}</td>
                <td class="text-right font-mono">{{ s.expectedClosing | number:'1.0-2' }}</td>
                <td class="text-right font-mono">{{ s.countedClosing | number:'1.0-2' }}</td>
                <td class="text-right">
                  @if (s.difference !== null && s.difference !== 0) {
                    <span class="font-mono font-semibold"
                          [class.text-red-700]="s.difference < 0"
                          [class.text-green-700]="s.difference > 0">
                      {{ (s.difference > 0 ? '+' : '') + (s.difference | number:'1.0-2') }}
                    </span>
                  } @else {
                    <span class="text-gray-400">—</span>
                  }
                </td>
                <td class="text-sm text-gray-500">{{ s.note || '—' }}</td>
                <td class="text-right">
                  <button pButton icon="pi pi-check"
                          [label]="'treasury.validate' | translate"
                          severity="success" class="p-button-sm"
                          [loading]="validatingId() === s.id"
                          (click)="confirmValidate(s)"></button>
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="7" class="text-center text-gray-400 py-4">{{ 'treasury.empty' | translate }}</td></tr>
            </ng-template>
          </p-table>
        </div>
      }

      <!-- History -->
      <p-tabView>
        <p-tabPanel [header]="'treasury.vaultHistory' | translate">
          <div class="flex items-end gap-2 flex-wrap mb-3">
            <div>
              <label class="block text-xs text-gray-500 mb-1">{{ 'treasury.from' | translate }}</label>
              <p-calendar [(ngModel)]="vaultFrom" dateFormat="dd/mm/yy"
                          [showButtonBar]="true" [readonlyInput]="true"
                          (onSelect)="loadVaultMovements()" (onClear)="loadVaultMovements()"
                          styleClass="w-40" inputStyleClass="w-full" />
            </div>
            <div>
              <label class="block text-xs text-gray-500 mb-1">{{ 'treasury.to' | translate }}</label>
              <p-calendar [(ngModel)]="vaultTo" dateFormat="dd/mm/yy"
                          [showButtonBar]="true" [readonlyInput]="true"
                          (onSelect)="loadVaultMovements()" (onClear)="loadVaultMovements()"
                          styleClass="w-40" inputStyleClass="w-full" />
            </div>
            @if (vaultFrom || vaultTo) {
              <button pButton icon="pi pi-filter-slash"
                      [label]="'common.clear' | translate"
                      class="p-button-sm p-button-text" (click)="clearVaultRange()"></button>
            }
          </div>
          <p-table [value]="vaultMovements()" stripedRows responsiveLayout="scroll"
                   styleClass="p-datatable-sm" [loading]="loading()">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'treasury.date' | translate }}</th>
                <th>{{ 'treasury.type' | translate }}</th>
                <th class="text-right">{{ 'treasury.amount' | translate }}</th>
                <th>{{ 'treasury.reference' | translate }}</th>
                <th>{{ 'treasury.note' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-m>
              <tr>
                <td class="whitespace-nowrap text-sm">{{ m.occurredAt | date:'short' }}</td>
                <td><p-tag [value]="('treasury.vaultType.' + m.type) | translate" [severity]="vaultTypeSeverity(m.type)" /></td>
                <td class="text-right font-mono"
                    [class.text-green-700]="m.amount > 0"
                    [class.text-red-700]="m.amount < 0">
                  {{ (m.amount > 0 ? '+' : '') + (m.amount | number:'1.0-2') }}
                </td>
                <td class="text-xs text-gray-500">{{ m.referenceType || '—' }}</td>
                <td class="text-sm">{{ m.note || '—' }}</td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="5" class="text-center text-gray-400 py-6">{{ 'treasury.empty' | translate }}</td></tr>
            </ng-template>
          </p-table>
        </p-tabPanel>

        <p-tabPanel [header]="'treasury.bankHistory' | translate">
          <div class="flex items-end gap-2 flex-wrap mb-3">
            <div class="grow md:grow-0">
              <label class="block text-xs text-gray-500 mb-1">{{ 'treasury.bankAccount.label' | translate }}</label>
              <p-dropdown [options]="banksForFilter()" [(ngModel)]="selectedBankIdForHistory"
                          optionLabel="name" optionValue="id"
                          [placeholder]="'treasury.selectBank' | translate"
                          (onChange)="loadBankTxns()" styleClass="w-full md:w-72" />
            </div>
            <div>
              <label class="block text-xs text-gray-500 mb-1">{{ 'treasury.from' | translate }}</label>
              <p-calendar [(ngModel)]="bankFrom" dateFormat="dd/mm/yy"
                          [showButtonBar]="true" [readonlyInput]="true"
                          (onSelect)="loadBankTxns()" (onClear)="loadBankTxns()"
                          styleClass="w-40" inputStyleClass="w-full" />
            </div>
            <div>
              <label class="block text-xs text-gray-500 mb-1">{{ 'treasury.to' | translate }}</label>
              <p-calendar [(ngModel)]="bankTo" dateFormat="dd/mm/yy"
                          [showButtonBar]="true" [readonlyInput]="true"
                          (onSelect)="loadBankTxns()" (onClear)="loadBankTxns()"
                          styleClass="w-40" inputStyleClass="w-full" />
            </div>
            @if (bankFrom || bankTo) {
              <button pButton icon="pi pi-filter-slash"
                      [label]="'common.clear' | translate"
                      class="p-button-sm p-button-text" (click)="clearBankRange()"></button>
            }
          </div>
          <p-table [value]="bankTxns()" stripedRows responsiveLayout="scroll"
                   styleClass="p-datatable-sm" [loading]="loading()">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'treasury.date' | translate }}</th>
                <th>{{ 'treasury.type' | translate }}</th>
                <th class="text-right">{{ 'treasury.amount' | translate }}</th>
                <th>{{ 'treasury.reference' | translate }}</th>
                <th>{{ 'treasury.note' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-t>
              <tr>
                <td class="whitespace-nowrap text-sm">{{ t.occurredAt | date:'short' }}</td>
                <td><p-tag [value]="('treasury.bankType.' + t.type) | translate" [severity]="bankTypeSeverity(t.type)" /></td>
                <td class="text-right font-mono"
                    [class.text-green-700]="t.amount > 0"
                    [class.text-red-700]="t.amount < 0">
                  {{ (t.amount > 0 ? '+' : '') + (t.amount | number:'1.0-2') }}
                </td>
                <td class="text-sm">{{ t.reference || '—' }}</td>
                <td class="text-sm">{{ t.note || '—' }}</td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="5" class="text-center text-gray-400 py-6">{{ 'treasury.empty' | translate }}</td></tr>
            </ng-template>
          </p-table>
        </p-tabPanel>
      </p-tabView>
    </div>

    <!-- Bank account dialog (create or edit) -->
    <p-dialog [(visible)]="bankDialogOpen" [modal]="true" [style]="{ width: '28rem' }"
              [header]="(editingBankId() ? 'treasury.editBank' : 'treasury.newBank') | translate">
      <div class="space-y-3 pt-2">
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.bankAccount.name' | translate }} *</label>
          <input pInputText [(ngModel)]="bankForm.name" class="w-full"
                 [class.ng-invalid]="bankNameInvalid()" [class.ng-dirty]="bankNameInvalid()" />
          @if (bankNameInvalid()) {
            <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
          }
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.bankAccount.bank' | translate }}</label>
          <input pInputText [(ngModel)]="bankForm.bankName" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.bankAccount.number' | translate }}</label>
          <input pInputText [(ngModel)]="bankForm.accountNumber" class="w-full" />
        </div>
        @if (!editingBankId()) {
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'treasury.bankAccount.opening' | translate }}</label>
            <p-inputNumber [(ngModel)]="bankForm.openingBalance" [minFractionDigits]="0" [maxFractionDigits]="2" styleClass="w-full" />
          </div>
        } @else {
          <div class="flex items-center gap-2">
            <input type="checkbox" [(ngModel)]="bankForm.active" id="bankActive" />
            <label for="bankActive" class="text-sm">{{ 'common.active' | translate }}</label>
          </div>
        }
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" severity="secondary" text (click)="bankDialogOpen = false"></button>
        <button pButton [label]="'common.save' | translate" [loading]="saving()" (click)="saveBank()"></button>
      </ng-template>
    </p-dialog>

    <!-- Deposit dialog (vault → bank) -->
    <p-dialog [(visible)]="depositDialogOpen" [modal]="true" [style]="{ width: '28rem' }"
              [header]="'treasury.deposit' | translate">
      <div class="space-y-3 pt-2">
        <p class="text-sm text-gray-600">{{ 'treasury.depositHint' | translate }}</p>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.bankAccount.label' | translate }} *</label>
          <p-dropdown [options]="activeBanks()" [(ngModel)]="transferForm.bankAccountId"
                      optionLabel="name" optionValue="id"
                      [styleClass]="'w-full' + (transferBankInvalid() ? ' ng-invalid ng-dirty' : '')" />
          @if (transferBankInvalid()) {
            <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
          }
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.amount' | translate }} *</label>
          <p-inputNumber [(ngModel)]="transferForm.amount" [min]="0.01" [maxFractionDigits]="2"
                         [styleClass]="'w-full' + (transferAmountInvalid() ? ' ng-invalid ng-dirty' : '')" />
          @if (transferAmountInvalid()) {
            <p class="text-xs text-red-600 mt-1">{{ 'common.mustBePositive' | translate }}</p>
          }
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.reference' | translate }}</label>
          <input pInputText [(ngModel)]="transferForm.reference" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.note' | translate }}</label>
          <input pInputText [(ngModel)]="transferForm.note" class="w-full" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" severity="secondary" text (click)="depositDialogOpen = false"></button>
        <button pButton [label]="'common.confirm' | translate" severity="success" [loading]="saving()" (click)="confirmDeposit()"
                ></button>
      </ng-template>
    </p-dialog>

    <!-- Withdraw dialog (bank → vault) -->
    <p-dialog [(visible)]="withdrawDialogOpen" [modal]="true" [style]="{ width: '28rem' }"
              [header]="'treasury.withdraw' | translate">
      <div class="space-y-3 pt-2">
        <p class="text-sm text-gray-600">{{ 'treasury.withdrawHint' | translate }}</p>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.bankAccount.label' | translate }} *</label>
          <p-dropdown [options]="activeBanks()" [(ngModel)]="transferForm.bankAccountId"
                      optionLabel="name" optionValue="id"
                      [styleClass]="'w-full' + (transferBankInvalid() ? ' ng-invalid ng-dirty' : '')" />
          @if (transferBankInvalid()) {
            <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
          }
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.amount' | translate }} *</label>
          <p-inputNumber [(ngModel)]="transferForm.amount" [min]="0.01" [maxFractionDigits]="2"
                         [styleClass]="'w-full' + (transferAmountInvalid() ? ' ng-invalid ng-dirty' : '')" />
          @if (transferAmountInvalid()) {
            <p class="text-xs text-red-600 mt-1">{{ 'common.mustBePositive' | translate }}</p>
          }
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.reference' | translate }}</label>
          <input pInputText [(ngModel)]="transferForm.reference" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.note' | translate }}</label>
          <input pInputText [(ngModel)]="transferForm.note" class="w-full" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" severity="secondary" text (click)="withdrawDialogOpen = false"></button>
        <button pButton [label]="'common.confirm' | translate" severity="warning" [loading]="saving()" (click)="confirmWithdraw()"
                ></button>
      </ng-template>
    </p-dialog>

    <!-- Adjust dialog (vault or bank account) -->
    <p-dialog [(visible)]="adjustDialogOpen" [modal]="true" [style]="{ width: '28rem' }"
              [header]="'treasury.adjustHeader' | translate">
      <div class="space-y-3 pt-2">
        <p class="text-sm text-gray-600">{{ 'treasury.adjustHint' | translate }}</p>
        <div>
          <label class="block text-sm font-medium mb-1">
            {{ 'treasury.adjustAmount' | translate }} *
            <span class="text-xs text-gray-500 ml-1">({{ 'treasury.signedHint' | translate }})</span>
          </label>
          <p-inputNumber [(ngModel)]="adjustForm.amountSigned" [maxFractionDigits]="2"
                         [styleClass]="'w-full' + (adjustAmountInvalid() ? ' ng-invalid ng-dirty' : '')" />
          @if (adjustAmountInvalid()) {
            <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
          }
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'treasury.note' | translate }}</label>
          <input pInputText [(ngModel)]="adjustForm.note" class="w-full" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" severity="secondary" text (click)="adjustDialogOpen = false"></button>
        <button pButton [label]="'common.confirm' | translate" [loading]="saving()" (click)="confirmAdjust()"></button>
      </ng-template>
    </p-dialog>
  `,
})
export class TreasuryPage implements OnInit {
  private http = inject(HttpClient);
  private msg = inject(MessageService);

  protected readonly vault = signal<VaultDto | null>(null);
  protected readonly banks = signal<BankAccountDto[]>([]);
  protected readonly vaultMovements = signal<VaultMovementDto[]>([]);
  protected readonly bankTxns = signal<BankTxnDto[]>([]);
  protected readonly pendingSessions = signal<CashSessionDto[]>([]);
  protected readonly registersById = signal<Record<string, string>>({});
  protected readonly validatingId = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly editingBankId = signal<string | null>(null);

  protected readonly activeBanks = computed(() => this.banks().filter(b => b.active));
  protected readonly banksForFilter = computed(() => this.banks());

  protected onlyWithDiscrepancy = false;
  protected readonly filteredPendingSessions = computed(() => {
    const all = this.pendingSessions();
    return this.onlyWithDiscrepancy ? all.filter(s => s.difference !== null && s.difference !== 0) : all;
  });

  protected selectedBankIdForHistory: string | null = null;

  // Date range filters for the history tabs (null = no bound on that side).
  protected vaultFrom: Date | null = null;
  protected vaultTo: Date | null = null;
  protected bankFrom: Date | null = null;
  protected bankTo: Date | null = null;

  protected bankDialogOpen = false;
  protected depositDialogOpen = false;
  protected withdrawDialogOpen = false;
  protected adjustDialogOpen = false;
  protected submittedBank = signal(false);
  protected submittedTransfer = signal(false);
  protected submittedAdjust = signal(false);

  protected bankNameInvalid(): boolean { return this.submittedBank() && !this.bankForm.name?.trim(); }
  protected transferBankInvalid(): boolean { return this.submittedTransfer() && !this.transferForm.bankAccountId; }
  protected transferAmountInvalid(): boolean {
    return this.submittedTransfer() && (this.transferForm.amount == null || this.transferForm.amount <= 0);
  }
  protected adjustAmountInvalid(): boolean {
    return this.submittedAdjust() && (this.adjustForm.amountSigned == null || this.adjustForm.amountSigned === 0);
  }

  /** 'vault' = adjust the central vault; otherwise the bankAccountId of the target. */
  protected adjustTarget: 'vault' | string = 'vault';

  protected bankForm: { name: string; bankName: string; accountNumber: string; openingBalance: number; active: boolean } = {
    name: '', bankName: '', accountNumber: '', openingBalance: 0, active: true,
  };
  protected transferForm: { bankAccountId: string | null; amount: number; reference: string; note: string } = {
    bankAccountId: null, amount: 0, reference: '', note: '',
  };
  protected adjustForm: { amountSigned: number; note: string } = { amountSigned: 0, note: '' };

  async ngOnInit(): Promise<void> {
    await Promise.all([
      this.loadVault(), this.loadBanks(), this.loadVaultMovements(),
      this.loadRegisters(), this.loadPendingSessions(),
    ]);
  }

  private async loadRegisters(): Promise<void> {
    try {
      const list = await firstValueFrom(this.http.get<Array<{ id: string; name: string }>>('/api/v1/pos/registers'));
      const map: Record<string, string> = {};
      for (const r of list) map[r.id] = r.name;
      this.registersById.set(map);
    } catch { /* ignore — fallback to id display */ }
  }

  private async loadPendingSessions(): Promise<void> {
    try {
      const list = await firstValueFrom(this.http.get<CashSessionDto[]>('/api/v1/pos/sessions/pending-validation'));
      this.pendingSessions.set(list);
    } catch { this.pendingSessions.set([]); }
  }

  protected registerName(id: string): string {
    return this.registersById()[id] ?? id;
  }

  protected async confirmValidate(s: CashSessionDto): Promise<void> {
    this.validatingId.set(s.id);
    try {
      await firstValueFrom(this.http.post(`/api/v1/pos/sessions/${s.id}/validate`, {}));
      this.msg.add({ severity: 'success', summary: 'Session validée', life: 2500 });
      await Promise.all([this.loadPendingSessions(), this.loadVault(), this.loadVaultMovements()]);
    } catch (e: any) {
      this.msg.add({ severity: 'error', summary: 'Erreur', detail: e?.error?.message ?? 'Échec', life: 4000 });
    } finally {
      this.validatingId.set(null);
    }
  }

  private async loadVault(): Promise<void> {
    this.vault.set(await firstValueFrom(this.http.get<VaultDto>('/api/v1/treasury/vault')));
  }

  private async loadBanks(): Promise<void> {
    this.banks.set(await firstValueFrom(this.http.get<BankAccountDto[]>('/api/v1/treasury/bank-accounts?includeInactive=true')));
  }

  protected async loadVaultMovements(): Promise<void> {
    this.loading.set(true);
    try {
      const params = this.buildRangeParams(this.vaultFrom, this.vaultTo);
      const res = await firstValueFrom(this.http.get<PageResponse<VaultMovementDto>>(
        '/api/v1/treasury/vault/movements?size=50' + params));
      this.vaultMovements.set(res.content ?? []);
    } finally {
      this.loading.set(false);
    }
  }

  protected async loadBankTxns(): Promise<void> {
    if (!this.selectedBankIdForHistory) { this.bankTxns.set([]); return; }
    this.loading.set(true);
    try {
      const params = this.buildRangeParams(this.bankFrom, this.bankTo);
      const res = await firstValueFrom(this.http.get<PageResponse<BankTxnDto>>(
        `/api/v1/treasury/bank-accounts/${this.selectedBankIdForHistory}/transactions?size=50` + params));
      this.bankTxns.set(res.content ?? []);
    } finally {
      this.loading.set(false);
    }
  }

  protected clearVaultRange(): void {
    this.vaultFrom = null;
    this.vaultTo = null;
    this.loadVaultMovements();
  }

  protected clearBankRange(): void {
    this.bankFrom = null;
    this.bankTo = null;
    this.loadBankTxns();
  }

  /** Build &from=&to= only when BOTH bounds are set, server requires the pair. */
  private buildRangeParams(from: Date | null, to: Date | null): string {
    if (!from || !to) return '';
    const start = new Date(from);
    start.setHours(0, 0, 0, 0);
    const end = new Date(to);
    end.setHours(23, 59, 59, 999);
    return `&from=${encodeURIComponent(start.toISOString())}&to=${encodeURIComponent(end.toISOString())}`;
  }

  // ── Bank account CRUD ─────────────────────────────────────────────────────

  protected openCreateBank(): void {
    this.editingBankId.set(null);
    this.bankForm = { name: '', bankName: '', accountNumber: '', openingBalance: 0, active: true };
    this.submittedBank.set(false);
    this.bankDialogOpen = true;
  }

  protected openEditBank(b: BankAccountDto): void {
    this.editingBankId.set(b.id);
    this.bankForm = {
      name: b.name, bankName: b.bankName ?? '', accountNumber: b.accountNumber ?? '',
      openingBalance: 0, active: b.active,
    };
    this.submittedBank.set(false);
    this.bankDialogOpen = true;
  }

  protected async saveBank(): Promise<void> {
    this.submittedBank.set(true);
    if (this.bankNameInvalid()) return;
    this.saving.set(true);
    try {
      const id = this.editingBankId();
      if (id) {
        await firstValueFrom(this.http.patch(`/api/v1/treasury/bank-accounts/${id}`, {
          name: this.bankForm.name,
          bankName: this.bankForm.bankName || null,
          accountNumber: this.bankForm.accountNumber || null,
          active: this.bankForm.active,
        }));
      } else {
        await firstValueFrom(this.http.post('/api/v1/treasury/bank-accounts', {
          name: this.bankForm.name,
          bankName: this.bankForm.bankName || null,
          accountNumber: this.bankForm.accountNumber || null,
          currency: 'MRU',
          openingBalance: this.bankForm.openingBalance,
        }));
      }
      this.msg.add({ severity: 'success', summary: 'OK', life: 2500 });
      this.bankDialogOpen = false;
      await this.loadBanks();
    } catch (e: any) {
      this.msg.add({ severity: 'error', summary: 'Erreur', detail: e?.error?.message ?? 'Échec', life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  // ── Transfers vault ↔ bank ────────────────────────────────────────────────

  protected openDeposit(): void {
    this.transferForm = { bankAccountId: this.activeBanks()[0]?.id ?? null, amount: 0, reference: '', note: '' };
    this.submittedTransfer.set(false);
    this.depositDialogOpen = true;
  }

  protected openWithdraw(): void {
    this.transferForm = { bankAccountId: this.activeBanks()[0]?.id ?? null, amount: 0, reference: '', note: '' };
    this.submittedTransfer.set(false);
    this.withdrawDialogOpen = true;
  }

  protected async confirmDeposit(): Promise<void> {
    this.submittedTransfer.set(true);
    if (this.transferBankInvalid() || this.transferAmountInvalid()) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/treasury/deposit', this.transferForm));
      this.msg.add({ severity: 'success', summary: 'Dépôt enregistré', life: 2500 });
      this.depositDialogOpen = false;
      await Promise.all([this.loadVault(), this.loadBanks(), this.loadVaultMovements()]);
    } catch (e: any) {
      this.msg.add({ severity: 'error', summary: 'Erreur', detail: e?.error?.message ?? 'Échec', life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  protected async confirmWithdraw(): Promise<void> {
    this.submittedTransfer.set(true);
    if (this.transferBankInvalid() || this.transferAmountInvalid()) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/treasury/withdraw', this.transferForm));
      this.msg.add({ severity: 'success', summary: 'Retrait enregistré', life: 2500 });
      this.withdrawDialogOpen = false;
      await Promise.all([this.loadVault(), this.loadBanks(), this.loadVaultMovements()]);
    } catch (e: any) {
      this.msg.add({ severity: 'error', summary: 'Erreur', detail: e?.error?.message ?? 'Échec', life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  // ── Adjustments ───────────────────────────────────────────────────────────

  protected openAdjustVault(): void {
    this.adjustTarget = 'vault';
    this.adjustForm = { amountSigned: 0, note: '' };
    this.submittedAdjust.set(false);
    this.adjustDialogOpen = true;
  }

  protected openAdjustBank(b: BankAccountDto): void {
    this.adjustTarget = b.id;
    this.adjustForm = { amountSigned: 0, note: '' };
    this.submittedAdjust.set(false);
    this.adjustDialogOpen = true;
  }

  protected async confirmAdjust(): Promise<void> {
    this.submittedAdjust.set(true);
    if (this.adjustAmountInvalid()) return;
    this.saving.set(true);
    try {
      if (this.adjustTarget === 'vault') {
        await firstValueFrom(this.http.post('/api/v1/treasury/vault/adjust', this.adjustForm));
      } else {
        await firstValueFrom(this.http.post(`/api/v1/treasury/bank-accounts/${this.adjustTarget}/adjust`, {
          bankAccountId: this.adjustTarget,
          amountSigned: this.adjustForm.amountSigned,
          note: this.adjustForm.note,
        }));
      }
      this.msg.add({ severity: 'success', summary: 'Ajustement enregistré', life: 2500 });
      this.adjustDialogOpen = false;
      await Promise.all([this.loadVault(), this.loadBanks(), this.loadVaultMovements()]);
      if (this.selectedBankIdForHistory) await this.loadBankTxns();
    } catch (e: any) {
      this.msg.add({ severity: 'error', summary: 'Erreur', detail: e?.error?.message ?? 'Échec', life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  protected vaultTypeSeverity(type: string): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    switch (type) {
      case 'FROM_POS_SESSION':
      case 'FROM_BANK':
        return 'success';
      case 'TO_POS_SESSION':
      case 'TO_BANK':
        return 'warning';
      case 'ADJUSTMENT':
        return 'info';
      default:
        return 'secondary';
    }
  }

  protected bankTypeSeverity(type: string): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    switch (type) {
      case 'DEPOSIT_FROM_VAULT':
        return 'success';
      case 'WITHDRAWAL_TO_VAULT':
        return 'warning';
      case 'ADJUSTMENT':
        return 'info';
      default:
        return 'secondary';
    }
  }
}
