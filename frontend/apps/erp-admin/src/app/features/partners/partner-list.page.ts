import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '@minierp/shared-i18n';
import { ConfirmationService, MessageService } from 'primeng/api';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { CalendarModule } from 'primeng/calendar';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { firstValueFrom } from 'rxjs';

type Role = 'all' | 'customer' | 'supplier';

interface Partner {
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
  customerCreditLimit: number | null;
  supplierCreditLimit: number | null;
  isCustomer: boolean;
  isSupplier: boolean;
  active: boolean;
  customerBalance: number;
  supplierBalance: number;
  customerCreditBalance: number;
}

interface PartnerForm {
  code: string;
  isCustomer: boolean;
  isSupplier: boolean;
  type: string;
  name: string;
  email: string;
  phone: string;
  address: string;
  taxId: string;
  paymentTerms: string;
  currency: string;
  notes: string;
  customerCreditLimit: number;
  supplierCreditLimit: number;
}

@Component({
  selector: 'erp-admin-partner-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule,
    InputTextModule, InputNumberModule, CheckboxModule, ButtonModule,
    DialogModule, DropdownModule, CalendarModule, TooltipModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'partners.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'partners.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'partners.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200">
        <div class="border-b border-gray-200 flex flex-wrap items-center justify-between gap-3 px-4 pt-2">
          <nav role="tablist" class="flex -mb-px">
            @for (opt of roleOptions; track opt.value) {
              <button type="button" role="tab"
                      [attr.aria-selected]="currentRole === opt.value"
                      (click)="currentRole = opt.value; onRoleChange()"
                      class="relative px-4 py-3 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded-t inline-flex items-center gap-2"
                      [class.text-primary-700]="currentRole === opt.value"
                      [class.text-gray-500]="currentRole !== opt.value"
                      [class.hover:text-gray-700]="currentRole !== opt.value">
                <span>{{ opt.label }}</span>
                <span class="text-xs px-1.5 py-0.5 rounded-full font-semibold tabular-nums"
                      [class.bg-primary-100]="currentRole === opt.value"
                      [class.text-primary-700]="currentRole === opt.value"
                      [class.bg-gray-100]="currentRole !== opt.value"
                      [class.text-gray-600]="currentRole !== opt.value">
                  {{ counts()[opt.value] }}
                </span>
                <span class="absolute left-0 right-0 -bottom-px h-0.5 transition-colors"
                      [class.bg-primary-600]="currentRole === opt.value"
                      [class.bg-transparent]="currentRole !== opt.value"></span>
              </button>
            }
          </nav>
          <span class="relative block w-full sm:w-72 mb-2">
            <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"></i>
            <input pInputText type="text" [placeholder]="'common.search' | translate"
                   (input)="onSearch($event)" class="w-full !pl-9" />
          </span>
        </div>
        <div class="p-4">

          <p-table #table [value]="partners()" [loading]="loading()" stripedRows
                   [lazy]="true" (onLazyLoad)="loadChunk($event)"
                   [totalRecords]="total()" [rows]="pageSize"
                   [scrollable]="true" scrollHeight="600px"
                   [virtualScroll]="true" [virtualScrollItemSize]="48"
                   [rowHover]="true" styleClass="p-datatable-sm">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'partners.code' | translate }}</th>
                <th>{{ 'partners.name' | translate }}</th>
                <th>{{ 'partners.roles' | translate }}</th>
                <th>{{ 'partners.phone' | translate }}</th>
                <th>{{ 'partners.email' | translate }}</th>
                <th class="text-right">{{ 'partners.debts_col' | translate }}</th>
                <th class="text-right">{{ 'partners.credit_col' | translate }}</th>
                <th>{{ 'partners.status' | translate }}</th>
                <th></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-p>
              <tr>
                <td class="font-mono text-sm">{{ p.code }}</td>
                <td class="font-medium">{{ p.name }}</td>
                <td>
                  @if (p.isCustomer) {
                    <p-tag severity="info" [value]="'partners.role_customer' | translate" class="mr-1" />
                  }
                  @if (p.isSupplier) {
                    <p-tag severity="warning" [value]="'partners.role_supplier' | translate" />
                  }
                </td>
                <td>{{ p.phone || '—' }}</td>
                <td>{{ p.email || '—' }}</td>
                <td class="text-right font-medium"
                    [class.text-red-600]="debts(p) > 0">
                  @if (debts(p) > 0) {
                    {{ debts(p) | money }} {{ p.currency }}
                  } @else { — }
                </td>
                <td class="text-right font-medium"
                    [class.text-amber-700]="creditOwed(p) > 0">
                  @if (creditOwed(p) > 0) {
                    {{ creditOwed(p) | money }} {{ p.currency }}
                  } @else { — }
                </td>
                <td>
                  <p-tag [value]="(p.active ? 'common.active' : 'common.inactive') | translate"
                         [severity]="p.active ? 'success' : 'secondary'" />
                </td>
                <td class="whitespace-nowrap">
                  <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text"
                          [pTooltip]="'partners.statement' | translate"
                          (click)="openStatementDialog(p)"></button>
                  <button pButton icon="pi pi-wallet" class="p-button-sm p-button-text"
                          [pTooltip]="'partners.openItems' | translate"
                          (click)="openOpenItemsDialog(p)"></button>
                  <button pButton icon="pi pi-history" class="p-button-sm p-button-text"
                          [pTooltip]="'partners.allocHistory' | translate"
                          (click)="openHistoryDialog(p)"></button>
                  <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                          [pTooltip]="'common.edit' | translate"
                          (click)="openEdit(p)"></button>
                  @if (!p.isSupplier) {
                    <button pButton icon="pi pi-briefcase" class="p-button-sm p-button-text"
                            [pTooltip]="'partners.activate_supplier' | translate"
                            (click)="openActivateSupplier(p)"></button>
                  }
                  @if (!p.isCustomer) {
                    <button pButton icon="pi pi-user-plus" class="p-button-sm p-button-text"
                            [pTooltip]="'partners.activate_customer' | translate"
                            (click)="openActivateCustomer(p)"></button>
                  }
                  @if (p.isCustomer && p.isSupplier) {
                    <button pButton icon="pi pi-briefcase" class="p-button-sm p-button-text p-button-warning"
                            [pTooltip]="'partners.deactivate_supplier' | translate"
                            style="text-decoration: line-through"
                            (click)="confirmDeactivateSupplier(p)"></button>
                    <button pButton icon="pi pi-user-minus" class="p-button-sm p-button-text p-button-warning"
                            [pTooltip]="'partners.deactivate_customer' | translate"
                            (click)="confirmDeactivateCustomer(p)"></button>
                  }
                  @if (p.active) {
                    <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                            [pTooltip]="'common.deactivate' | translate"
                            (click)="confirmDelete(p)"></button>
                  }
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="9" class="text-center text-gray-400 py-8">{{ 'partners.empty' | translate }}</td></tr>
            </ng-template>
          </p-table>
        </div>
      </div>

      <!-- Create / edit dialog -->
      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '560px' }"
                [header]="(editing() ? 'partners.editTitle' : 'partners.createTitle') | translate"
                [closable]="!saving()">
        <div class="space-y-3">
          @if (!editing()) {
            <div>
              <div class="flex gap-4 items-center bg-gray-50 p-3 rounded">
                <label class="flex items-center gap-2 cursor-pointer">
                  <p-checkbox [(ngModel)]="form.isCustomer" [binary]="true"/>
                  <span class="font-medium">{{ 'partners.role_customer' | translate }}</span>
                </label>
                <label class="flex items-center gap-2 cursor-pointer">
                  <p-checkbox [(ngModel)]="form.isSupplier" [binary]="true"/>
                  <span class="font-medium">{{ 'partners.role_supplier' | translate }}</span>
                </label>
              </div>
              @if (rolesInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'partners.errors.roleRequired' | translate }}</p>
              }
            </div>
          }
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'partners.code' | translate }} *</label>
              <input pInputText [(ngModel)]="form.code" [disabled]="!!editing()" class="w-full"
                     [class.ng-invalid]="codeInvalid()" [class.ng-dirty]="codeInvalid()"/>
              @if (codeInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'partners.type' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.type" [options]="typeOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full"
                          (onChange)="onTypeChange()"/>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'partners.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full"
                   [class.ng-invalid]="nameInvalid()" [class.ng-dirty]="nameInvalid()"/>
            @if (nameInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'partners.email' | translate }}</label>
              <input pInputText type="email" [(ngModel)]="form.email" class="w-full"/>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'partners.phone' | translate }}</label>
              <input pInputText [(ngModel)]="form.phone" class="w-full"/>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'partners.address' | translate }}</label>
            <input pInputText [(ngModel)]="form.address" class="w-full"/>
          </div>
          @if (form.isSupplier) {
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-sm font-medium mb-1">{{ 'partners.taxId' | translate }}</label>
                <input pInputText [(ngModel)]="form.taxId" class="w-full"/>
              </div>
              <div>
                <label class="block text-sm font-medium mb-1">{{ 'partners.paymentTerms' | translate }}</label>
                <input pInputText [(ngModel)]="form.paymentTerms" class="w-full"/>
              </div>
            </div>
          }
          <div class="grid grid-cols-2 gap-3">
            @if (form.isCustomer) {
              <div>
                <label class="block text-sm font-medium mb-1">{{ 'partners.customer_credit_limit' | translate }}</label>
                <p-inputNumber [(ngModel)]="form.customerCreditLimit" mode="decimal"
                               [maxFractionDigits]="2" styleClass="w-full"/>
              </div>
            }
            @if (form.isSupplier) {
              <div>
                <label class="block text-sm font-medium mb-1">{{ 'partners.supplier_credit_limit' | translate }}</label>
                <p-inputNumber [(ngModel)]="form.supplierCreditLimit" mode="decimal"
                               [maxFractionDigits]="2" styleClass="w-full"/>
              </div>
            }
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'partners.currency' | translate }}</label>
              <input pInputText [(ngModel)]="form.currency" class="w-full"/>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="form.notes" class="w-full"/>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Activate supplier role dialog -->
      <p-dialog [(visible)]="activateSupplierOpen" [modal]="true" [style]="{ width: '450px' }"
                [header]="'partners.activate_supplier' | translate"
                [closable]="!activatingRole()">
        <div class="space-y-3">
          <p class="text-sm text-gray-600">{{ 'partners.activate_supplier_hint' | translate }}</p>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'partners.taxId' | translate }}</label>
            <input pInputText [(ngModel)]="activateSupplierForm.taxId" class="w-full"/>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'partners.paymentTerms' | translate }}</label>
            <input pInputText [(ngModel)]="activateSupplierForm.paymentTerms" class="w-full"/>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'partners.supplier_credit_limit' | translate }}</label>
            <p-inputNumber [(ngModel)]="activateSupplierForm.supplierCreditLimit" mode="decimal"
                           [maxFractionDigits]="2" styleClass="w-full"/>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="activateSupplierOpen = false" [disabled]="activatingRole()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="submitActivateSupplier()" [loading]="activatingRole()"></button>
        </ng-template>
      </p-dialog>

      <!-- Activate customer role dialog -->
      <p-dialog [(visible)]="activateCustomerOpen" [modal]="true" [style]="{ width: '450px' }"
                [header]="'partners.activate_customer' | translate"
                [closable]="!activatingRole()">
        <div class="space-y-3">
          <p class="text-sm text-gray-600">{{ 'partners.activate_customer_hint' | translate }}</p>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'partners.customer_credit_limit' | translate }}</label>
            <p-inputNumber [(ngModel)]="activateCustomerForm.customerCreditLimit" mode="decimal"
                           [maxFractionDigits]="2" styleClass="w-full"/>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="activateCustomerOpen = false" [disabled]="activatingRole()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="submitActivateCustomer()" [loading]="activatingRole()"></button>
        </ng-template>
      </p-dialog>

      <!-- Statement dialog -->
      <p-dialog [(visible)]="statementDialogOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="('partners.statementDialogTitle' | translate) + ' — ' + (statementCustomer()?.name ?? '')"
                [closable]="!fetchingStatement()">
        <div class="space-y-3">
          <p class="text-sm text-gray-600">{{ 'partners.statementDialogHint' | translate }}</p>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'partners.statementFrom' | translate }}</label>
              <p-calendar [(ngModel)]="statementFrom" dateFormat="dd/mm/yy"
                          styleClass="w-full" appendTo="body" [showClear]="true"/>
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'partners.statementTo' | translate }}</label>
              <p-calendar [(ngModel)]="statementTo" dateFormat="dd/mm/yy"
                          styleClass="w-full" appendTo="body" [showClear]="true"/>
            </div>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <div class="flex flex-wrap gap-2 justify-end">
            <button pButton [label]="'common.cancel' | translate" class="p-button-text p-button-sm"
                    (click)="statementDialogOpen = false" [disabled]="fetchingStatement()"></button>
            <button pButton icon="pi pi-list" [label]="'partners.statementFull' | translate"
                    class="p-button-sm p-button-outlined"
                    (click)="downloadStatement('full')" [loading]="fetchingStatement()"></button>
            <button pButton icon="pi pi-table" [label]="'partners.statementDetailed' | translate"
                    class="p-button-sm p-button-outlined"
                    (click)="downloadStatement('detailed')" [loading]="fetchingStatement()"></button>
            <button pButton icon="pi pi-exclamation-circle" [label]="'partners.statementOutstanding' | translate"
                    class="p-button-sm"
                    (click)="downloadStatement('outstanding')" [loading]="fetchingStatement()"></button>
          </div>
        </ng-template>
      </p-dialog>

      <p-dialog [(visible)]="openItemsDialogOpen" [modal]="true" [style]="{ width: '720px' }"
                [header]="('partners.openItemsTitle' | translate) + ' — ' + (openItemsPartner()?.name ?? '')">
        @if (openItemsLoading()) {
          <p class="text-sm text-gray-500 p-4 text-center">{{ 'common.loading' | translate }}</p>
        } @else if (openItems().length === 0) {
          <p class="text-sm text-gray-500 p-4 text-center">{{ 'partners.openItemsEmpty' | translate }}</p>
        } @else {
          <table class="w-full text-sm">
            <thead class="bg-gray-50 text-gray-600">
              <tr>
                <th class="text-left p-2">{{ 'partners.openItemsCol.type' | translate }}</th>
                <th class="text-left p-2">{{ 'partners.openItemsCol.ref' | translate }}</th>
                <th class="text-left p-2 w-28">{{ 'partners.openItemsCol.date' | translate }}</th>
                <th class="text-right p-2 w-28">{{ 'partners.openItemsCol.open' | translate }}</th>
                <th class="text-center p-2 w-20">{{ 'partners.openItemsCol.sign' | translate }}</th>
                <th class="text-center p-2 w-12">{{ 'partners.openItemsCol.select' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (item of openItems(); track item.sourceId) {
                <tr class="border-t">
                  <td class="p-2">{{ ('partners.openItemsType.' + item.sourceType) | translate }}</td>
                  <td class="p-2 font-mono text-xs">{{ item.label }}</td>
                  <td class="p-2 text-gray-600">{{ item.dateRef | date:'mediumDate' }}</td>
                  <td class="p-2 text-right font-medium">{{ item.amountOpen | money }}</td>
                  <td class="p-2 text-center">
                    @if (item.sign === 'POSITIVE') {
                      <p-tag severity="success" [value]="'+'" />
                    } @else {
                      <p-tag severity="danger" [value]="'−'" />
                    }
                  </td>
                  <td class="p-2 text-center">
                    @if (item.sourceType === 'CUSTOMER_CREDIT' && item.sign === 'POSITIVE') {
                      <input type="radio" name="impPos" [value]="item.sourceId" [(ngModel)]="impPositiveId" />
                    } @else if (item.sourceType === 'INVOICE' && item.sign === 'NEGATIVE') {
                      <input type="radio" name="impNeg" [value]="item.sourceId" [(ngModel)]="impNegativeId" />
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
        <ng-template pTemplate="footer">
          <div class="flex items-center justify-between w-full gap-3">
            <span class="text-sm text-gray-700">
              @if (imputePairSupported()) {
                {{ 'partners.imputePreview' | translate }} <strong>{{ imputeAmount() | money }}</strong>
              }
            </span>
            <div class="flex gap-2">
              <button pButton icon="pi pi-link" [label]="'partners.impute' | translate"
                      class="p-button-sm"
                      [pTooltip]="canImpute() ? '' : ('partners.imputeUnsupported' | translate)"
                      [disabled]="!canImpute()" [loading]="imputing()"
                      (click)="impute()"></button>
              <button pButton [label]="'common.close' | translate" class="p-button-text"
                      (click)="openItemsDialogOpen = false"></button>
            </div>
          </div>
        </ng-template>
      </p-dialog>

      <!-- Allocation history dialog: full audit of the unified allocations table,
           including reversed (soft-void) rows shown struck through. -->
      <p-dialog [(visible)]="historyDialogOpen" [modal]="true" [style]="{ width: '820px' }"
                [header]="('partners.allocHistoryTitle' | translate) + ' — ' + (historyPartner()?.name ?? '')">
        @if (historyLoading()) {
          <p class="text-sm text-gray-500 p-4 text-center">{{ 'common.loading' | translate }}</p>
        } @else if (history().length === 0) {
          <p class="text-sm text-gray-500 p-4 text-center">{{ 'partners.allocHistoryEmpty' | translate }}</p>
        } @else {
          <table class="w-full text-sm">
            <thead class="bg-gray-50 text-gray-600">
              <tr>
                <th class="text-left p-2 w-28">{{ 'partners.allocHistoryCol.date' | translate }}</th>
                <th class="text-left p-2">{{ 'partners.allocHistoryCol.from' | translate }}</th>
                <th class="text-left p-2">{{ 'partners.allocHistoryCol.to' | translate }}</th>
                <th class="text-right p-2 w-28">{{ 'partners.allocHistoryCol.amount' | translate }}</th>
                <th class="text-center p-2 w-24">{{ 'partners.allocHistoryCol.status' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (row of history(); track row.id) {
                <tr class="border-t" [class.text-gray-400]="row.reversedAt">
                  <td class="p-2 text-gray-600">{{ row.allocatedAt | date:'mediumDate' }}</td>
                  <td class="p-2" [class.line-through]="row.reversedAt">
                    <span class="text-xs text-gray-500">{{ ('partners.openItemsType.' + row.positiveType) | translate }}</span>
                    <span class="font-mono text-xs ml-1">{{ row.positiveLabel }}</span>
                  </td>
                  <td class="p-2" [class.line-through]="row.reversedAt">
                    <span class="text-xs text-gray-500">{{ ('partners.openItemsType.' + row.negativeType) | translate }}</span>
                    <span class="font-mono text-xs ml-1">{{ row.negativeLabel }}</span>
                  </td>
                  <td class="p-2 text-right font-medium" [class.line-through]="row.reversedAt">{{ row.amount | money }}</td>
                  <td class="p-2 text-center">
                    @if (row.reversedAt) {
                      <p-tag severity="secondary" [value]="'partners.allocReversed' | translate"
                             [pTooltip]="row.reversalReason ?? ''"></p-tag>
                    } @else {
                      <p-tag severity="success" [value]="'partners.allocActive' | translate" />
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.close' | translate" class="p-button-text"
                  (click)="historyDialogOpen = false"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class PartnerListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private toast = inject(MessageService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected partners = signal<Partner[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected counts = signal<{ all: number; customer: number; supplier: number }>({ all: 0, customer: 0, supplier: 0 });
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;
  protected editing = signal<Partner | null>(null);
  protected form: PartnerForm = this.emptyForm();

  protected codeInvalid(): boolean { return this.submitted() && !this.form.code?.trim(); }
  protected nameInvalid(): boolean { return this.submitted() && !this.form.name?.trim(); }
  protected rolesInvalid(): boolean {
    return this.submitted() && !this.editing() && !this.form.isCustomer && !this.form.isSupplier;
  }

  /** Dettes = ce que le partenaire nous doit (factures clients impayées + trop-perçu fournisseur si négatif). */
  protected debts(p: Partner): number {
    const customerOwes = p.isCustomer ? Math.max(0, Number(p.customerBalance) || 0) : 0;
    const supplierOwes = p.isSupplier ? Math.max(0, -(Number(p.supplierBalance) || 0)) : 0;
    return +(customerOwes + supplierOwes).toFixed(2);
  }
  /** Crédit en faveur du partenaire = ce qu'on lui doit (AP fournisseur + crédits client OVERPAYMENT/DEPOSIT). */
  protected creditOwed(p: Partner): number {
    const supplierOwed = p.isSupplier ? Math.max(0, Number(p.supplierBalance) || 0) : 0;
    const customerCredit = p.isCustomer ? Math.max(0, Number(p.customerCreditBalance) || 0) : 0;
    return +(supplierOwed + customerCredit).toFixed(2);
  }
  protected typeOptions: Array<{ value: string; label: string }> = [];
  protected roleOptions: Array<{ value: Role; label: string }> = [];
  protected currentRole: Role = 'all';
  protected codeAutoFilled = false;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private lastQuery = '';

  protected activateSupplierOpen = false;
  protected activateSupplierForm = { taxId: '', paymentTerms: '', supplierCreditLimit: 0 };
  protected activateCustomerOpen = false;
  protected activateCustomerForm = { customerCreditLimit: 0 };
  protected activatingRole = signal(false);
  protected targetPartner = signal<Partner | null>(null);

  protected statementDialogOpen = false;
  protected statementCustomer = signal<Partner | null>(null);
  protected fetchingStatement = signal(false);
  protected statementFrom: Date | null = new Date(new Date().getFullYear(), 0, 1);
  protected statementTo: Date | null = new Date();

  // AllocationEngine "Open items" dialog (Phase 4): a read-only window on the
  // engine's findOpenItemsByParty output.
  protected openItemsDialogOpen = false;
  protected openItemsPartner = signal<Partner | null>(null);
  protected openItemsLoading = signal(false);
  protected openItems = signal<{
    sourceType: string;
    sourceId: string;
    sign: 'POSITIVE' | 'NEGATIVE';
    amountOpen: number;
    dateRef: string;
    label: string;
  }[]>([]);

  // Manual imputation from the open-items dialog: pick one positive credit and
  // one negative invoice, then net them via credit-to-invoice. Only that pair
  // is reachable through the allocation controller, so radios are shown only on
  // CUSTOMER_CREDIT (+) and INVOICE (−) rows.
  protected impPositiveId: string | null = null;
  protected impNegativeId: string | null = null;
  protected imputing = signal(false);

  // Allocation history dialog (#2): full audit of the unified allocations table
  // for a party, including reversed (soft-void) rows.
  protected historyDialogOpen = false;
  protected historyPartner = signal<Partner | null>(null);
  protected historyLoading = signal(false);
  protected history = signal<{
    id: string;
    positiveType: string;
    positiveLabel: string;
    negativeType: string;
    negativeLabel: string;
    amount: number;
    allocatedAt: string;
    reversedAt: string | null;
    reversalReason: string | null;
  }[]>([]);

  ngOnInit() {
    this.refreshOptions();
    this.i18n.onLangChange.subscribe(() => this.refreshOptions());
    this.route.queryParamMap.subscribe(params => {
      this.currentRole = (params.get('role') ?? 'all') as Role;
      this.reload();
    });
  }

  protected onRoleChange() {
    this.router.navigate([], {
      queryParams: { role: this.currentRole === 'all' ? null : this.currentRole },
      queryParamsHandling: 'merge',
    });
    this.reload();
  }

  private refreshOptions() {
    this.typeOptions = ['INDIVIDUAL', 'COMPANY'].map(v => ({
      value: v,
      label: this.i18n.instant('partners.types.' + v),
    }));
    this.roleOptions = [
      { value: 'all', label: this.i18n.instant('partners.tab_all') },
      { value: 'customer', label: this.i18n.instant('partners.tab_customers') },
      { value: 'supplier', label: this.i18n.instant('partners.tab_suppliers') },
    ];
  }

  protected onSearch(e: Event) {
    this.lastQuery = (e.target as HTMLInputElement).value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.reload(), 300);
  }

  protected openCreate() {
    this.editing.set(null);
    this.form = this.emptyForm();
    if (this.currentRole === 'customer') this.form.isCustomer = true;
    else if (this.currentRole === 'supplier') this.form.isSupplier = true;
    else this.form.isCustomer = true;
    this.codeAutoFilled = true;
    this.submitted.set(false);
    this.dialogOpen = true;
    this.fetchSuggestedCode();
  }

  protected onTypeChange() {
    if (!this.editing() && this.codeAutoFilled) this.fetchSuggestedCode();
  }

  private async fetchSuggestedCode() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ code: string }>(`/api/v1/partners/next-code?type=${encodeURIComponent(this.form.type)}`)
      );
      if (this.codeAutoFilled) this.form.code = res.code;
    } catch { /* silent */ }
  }

  protected openEdit(p: Partner) {
    this.editing.set(p);
    this.form = {
      code: p.code,
      isCustomer: p.isCustomer,
      isSupplier: p.isSupplier,
      type: p.type,
      name: p.name,
      email: p.email ?? '',
      phone: p.phone ?? '',
      address: p.address ?? '',
      taxId: p.taxId ?? '',
      paymentTerms: p.paymentTerms ?? '',
      currency: p.currency ?? 'MRU',
      notes: p.notes ?? '',
      customerCreditLimit: p.customerCreditLimit ?? 0,
      supplierCreditLimit: p.supplierCreditLimit ?? 0,
    };
    this.codeAutoFilled = false;
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected openActivateSupplier(p: Partner) {
    this.targetPartner.set(p);
    this.activateSupplierForm = { taxId: '', paymentTerms: '', supplierCreditLimit: 0 };
    this.activateSupplierOpen = true;
  }

  protected openActivateCustomer(p: Partner) {
    this.targetPartner.set(p);
    this.activateCustomerForm = { customerCreditLimit: 0 };
    this.activateCustomerOpen = true;
  }

  protected async submitActivateSupplier() {
    const p = this.targetPartner();
    if (!p) return;
    this.activatingRole.set(true);
    try {
      await firstValueFrom(this.http.post(
        `/api/v1/partners/${p.id}/activate-supplier-role`,
        {
          taxId: this.activateSupplierForm.taxId || null,
          paymentTerms: this.activateSupplierForm.paymentTerms || null,
          supplierCreditLimit: this.activateSupplierForm.supplierCreditLimit || null,
        }));
      this.activateSupplierOpen = false;
      this.reload();
    } catch (e) {
      this.showError(e);
    } finally {
      this.activatingRole.set(false);
    }
  }

  protected async submitActivateCustomer() {
    const p = this.targetPartner();
    if (!p) return;
    this.activatingRole.set(true);
    try {
      await firstValueFrom(this.http.post(
        `/api/v1/partners/${p.id}/activate-customer-role`,
        { customerCreditLimit: this.activateCustomerForm.customerCreditLimit || null }));
      this.activateCustomerOpen = false;
      this.reload();
    } catch (e) {
      this.showError(e);
    } finally {
      this.activatingRole.set(false);
    }
  }

  protected confirmDeactivateSupplier(p: Partner) {
    this.confirmation.confirm({
      message: this.i18n.instant('partners.confirm_deactivate_supplier', { name: p.name }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-warning',
      accept: async () => {
        try {
          await firstValueFrom(this.http.post(`/api/v1/partners/${p.id}/deactivate-supplier-role`, {}));
          this.reload();
        } catch (e) {
          this.showError(e);
        }
      },
    });
  }

  protected confirmDeactivateCustomer(p: Partner) {
    this.confirmation.confirm({
      message: this.i18n.instant('partners.confirm_deactivate_customer', { name: p.name }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-warning',
      accept: async () => {
        try {
          await firstValueFrom(this.http.post(`/api/v1/partners/${p.id}/deactivate-customer-role`, {}));
          this.reload();
        } catch (e) {
          this.showError(e);
        }
      },
    });
  }

  protected openStatementDialog(p: Partner) {
    this.statementCustomer.set(p);
    this.statementFrom = new Date(new Date().getFullYear(), 0, 1);
    this.statementTo = new Date();
    this.statementDialogOpen = true;
  }

  protected async openOpenItemsDialog(p: Partner) {
    this.openItemsPartner.set(p);
    this.openItems.set([]);
    this.impPositiveId = null;
    this.impNegativeId = null;
    this.openItemsDialogOpen = true;
    this.openItemsLoading.set(true);
    try {
      type Row = {
        sourceType: string;
        sourceId: string;
        sign: 'POSITIVE' | 'NEGATIVE';
        amountOpen: number;
        dateRef: string;
        label: string;
      };
      const items = await firstValueFrom(
        this.http.get<Row[]>(`/api/v1/allocations/open-items?partyId=${p.id}`)
      );
      this.openItems.set(items ?? []);
    } catch {
      this.openItems.set([]);
    } finally {
      this.openItemsLoading.set(false);
    }
  }

  private impSelectedPositive() {
    return this.openItems().find(i => i.sourceId === this.impPositiveId && i.sign === 'POSITIVE');
  }
  private impSelectedNegative() {
    return this.openItems().find(i => i.sourceId === this.impNegativeId && i.sign === 'NEGATIVE');
  }

  /** Amount that will be netted: the smaller of the two open residuals. */
  protected imputeAmount(): number {
    const pos = this.impSelectedPositive();
    const neg = this.impSelectedNegative();
    if (!pos || !neg) return 0;
    return +Math.min(Number(pos.amountOpen), Number(neg.amountOpen)).toFixed(2);
  }

  /** Only CUSTOMER_CREDIT (+) → INVOICE (−) is reachable via the allocation
   *  controller, so that is the one supported pair from this dialog. */
  protected imputePairSupported(): boolean {
    const pos = this.impSelectedPositive();
    const neg = this.impSelectedNegative();
    return pos?.sourceType === 'CUSTOMER_CREDIT' && neg?.sourceType === 'INVOICE';
  }

  protected canImpute(): boolean {
    return !this.imputing() && this.imputePairSupported() && this.imputeAmount() > 0;
  }

  protected async impute() {
    const pos = this.impSelectedPositive();
    const neg = this.impSelectedNegative();
    const amount = this.imputeAmount();
    if (!pos || !neg || !this.imputePairSupported() || amount <= 0) return;
    this.imputing.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/allocations/credit-to-invoice', {
        creditId: pos.sourceId,
        invoiceId: neg.sourceId,
        amount,
      }));
      this.toast.add({
        severity: 'success',
        summary: this.i18n.instant('common.success'),
        detail: this.i18n.instant('partners.imputeDone'),
        life: 4000,
      });
      const p = this.openItemsPartner();
      if (p) await this.openOpenItemsDialog(p); // refresh residuals, keep dialog open
      this.reload(); // partner balances may have changed
    } catch (e) {
      this.showError(e);
    } finally {
      this.imputing.set(false);
    }
  }

  protected async openHistoryDialog(p: Partner) {
    this.historyPartner.set(p);
    this.history.set([]);
    this.historyDialogOpen = true;
    this.historyLoading.set(true);
    try {
      type Row = {
        id: string;
        positiveType: string;
        positiveLabel: string;
        negativeType: string;
        negativeLabel: string;
        amount: number;
        allocatedAt: string;
        reversedAt: string | null;
        reversalReason: string | null;
      };
      const rows = await firstValueFrom(
        this.http.get<Row[]>(`/api/v1/allocations/history?partyId=${p.id}`)
      );
      this.history.set(rows ?? []);
    } catch {
      this.history.set([]);
    } finally {
      this.historyLoading.set(false);
    }
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
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
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

  protected confirmDelete(p: Partner) {
    this.confirmation.confirm({
      message: this.i18n.instant('partners.confirm_deactivate', { name: p.name }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.delete(`/api/v1/partners/${p.id}`));
        this.reload();
      },
    });
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.name?.trim() || !this.form.code?.trim()) return;
    if (!this.editing() && !this.form.isCustomer && !this.form.isSupplier) return;
    this.saving.set(true);
    try {
      const payload = {
        code: this.form.code.trim(),
        isCustomer: this.form.isCustomer,
        isSupplier: this.form.isSupplier,
        type: this.form.type || 'INDIVIDUAL',
        name: this.form.name.trim(),
        email: this.form.email || null,
        phone: this.form.phone || null,
        address: this.form.address || null,
        taxId: this.form.taxId || null,
        paymentTerms: this.form.paymentTerms || null,
        currency: this.form.currency || 'MRU',
        notes: this.form.notes || null,
        defaultPriceTierId: null,
        notificationPreferences: null,
        customerCreditLimit: this.form.isCustomer ? (this.form.customerCreditLimit || 0) : null,
        supplierCreditLimit: this.form.isSupplier ? (this.form.supplierCreditLimit || 0) : null,
      };
      const current = this.editing();
      if (current) {
        await firstValueFrom(this.http.put(`/api/v1/partners/${current.id}`, payload));
      } else {
        await firstValueFrom(this.http.post('/api/v1/partners', payload));
      }
      this.dialogOpen = false;
      this.reload();
    } catch (e) {
      this.showError(e);
    } finally {
      this.saving.set(false);
    }
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(rows));
    if (this.currentRole !== 'all') params.set('role', this.currentRole.toUpperCase());
    if (this.lastQuery) params.set('q', this.lastQuery);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Partner[]; totalElements: number }>(`/api/v1/partners?${params}`)
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.partners()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.partners.set(arr);
      this.total.set(totalElements);
    } catch {
      this.partners.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
    this.loadCounts();
  }

  protected reload() {
    this.partners.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadCounts() {
    const search = this.lastQuery ? `&q=${encodeURIComponent(this.lastQuery)}` : '';
    const u = (role?: string) =>
      `/api/v1/partners?size=1${role ? `&role=${role}` : ''}${search}`;
    try {
      const [all, customer, supplier] = await Promise.all([
        firstValueFrom(this.http.get<{ totalElements: number }>(u())),
        firstValueFrom(this.http.get<{ totalElements: number }>(u('CUSTOMER'))),
        firstValueFrom(this.http.get<{ totalElements: number }>(u('SUPPLIER'))),
      ]);
      this.counts.set({
        all: all.totalElements ?? 0,
        customer: customer.totalElements ?? 0,
        supplier: supplier.totalElements ?? 0,
      });
    } catch { /* silent — tab labels just won't show counts */ }
  }

  private showError(err: unknown) {
    let detail = this.i18n.instant('common.error_generic');
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (body?.code) {
        const translated = this.i18n.instant(body.code);
        detail = translated !== body.code ? translated : (body.message ?? body.code);
      } else if (body?.message) {
        detail = body.message;
      }
    }
    this.toast.add({ severity: 'error', summary: this.i18n.instant('common.error'), detail, life: 5000 });
  }

  private emptyForm(): PartnerForm {
    return {
      code: '',
      isCustomer: false,
      isSupplier: false,
      type: 'INDIVIDUAL',
      name: '',
      email: '',
      phone: '',
      address: '',
      taxId: '',
      paymentTerms: '',
      currency: 'MRU',
      notes: '',
      customerCreditLimit: 0,
      supplierCreditLimit: 0,
    };
  }
}
