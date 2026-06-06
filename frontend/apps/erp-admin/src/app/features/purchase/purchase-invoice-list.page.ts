import { Component, OnInit, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '@minierp/shared-i18n';
import { HttpClient } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { CalendarModule } from 'primeng/calendar';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

interface PurchaseInvoice {
  id: string;
  number: string;
  supplierId: string;
  supplierName: string;
  purchaseOrderId: string | null;
  supplierReference: string | null;
  invoiceDate: string;
  dueDate: string | null;
  status: string;
  receptionStatus: string;
  currency: string;
  subtotal: number;
  taxAmount: number;
  total: number;
  paidAmount: number;
  balance: number;
  notes: string | null;
  creditNoteCount: number;
}

interface CreditNotePreview {
  purchaseInvoiceId: string;
  invoiceNumber: string;
  total: number;
  blockReason: string | null;
  alreadyPaid: number;
  returnLines: { productId: string; productName: string; sku: string; returnQty: number }[];
}

interface InvoiceLineDetail {
  id: string;
  productName: string;
  sku: string;
  quantity: number;
  unitCost: number;
  taxRate: number;
  lineTotal: number;
}

interface PurchaseInvoiceDetail extends PurchaseInvoice {
  lines: InvoiceLineDetail[];
}

interface ReceptionLite { id: string; number: string; status: string; }
interface PurchaseCreditNoteLite { id: string; number: string; total: number; currency: string; }

/** A net-position open item (an opposite-chain sale invoice) the user can offset
 *  against the purchase invoice being created. */
interface CompensItem {
  sourceType: string;
  sourceId: string;
  label: string;
  amountOpen: number;
  selected: boolean;
  amount: number;
}

interface SupplierOpt { id: string; code: string; name: string; currency: string; }
interface ProductOpt { id: string; sku: string; name: string; baseUomId: string; defaultTaxRate: number; }
interface ProductStockBreakdown {
  productId: string;
  warehouses: { warehouseId: string; warehouseCode: string; warehouseName: string; isDefault: boolean; qtyAvailable: number }[];
}

interface LineForm {
  productId: string | null;
  uomId: string | null;
  quantity: number;
  unitCost: number;
  taxRate: number;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-purchase-invoice-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule, ButtonModule,
    DialogModule, DropdownModule, CalendarModule, InputTextModule, InputNumberModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'purchaseInvoices.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'purchaseInvoices.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'purchaseInvoices.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="invoices()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'purchaseInvoices.number' | translate }}</th>
              <th>{{ 'purchaseInvoices.supplier' | translate }}</th>
              <th>{{ 'purchaseInvoices.invoiceDate' | translate }}</th>
              <th>{{ 'purchaseInvoices.dueDate' | translate }}</th>
              <th class="text-right">{{ 'purchaseInvoices.total' | translate }}</th>
              <th class="text-right">{{ 'purchaseInvoices.balance' | translate }}</th>
              <th>{{ 'purchaseInvoices.status' | translate }}</th>
              <th>{{ 'purchaseInvoices.reception' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-inv>
            <tr>
              <td><span class="font-mono text-sm">{{ inv.number }}</span></td>
              <td>{{ inv.supplierName }}</td>
              <td>{{ inv.invoiceDate | date:'mediumDate' }}</td>
              <td>{{ inv.dueDate ? (inv.dueDate | date:'mediumDate') : '—' }}</td>
              <td class="text-right font-medium">{{ inv.total | money }} {{ inv.currency }}</td>
              <td class="text-right font-medium"
                  [class.text-red-600]="inv.balance > 0"
                  [class.text-green-600]="inv.balance === 0">
                {{ inv.balance | money }} {{ inv.currency }}
              </td>
              <td>
                <p-tag [value]="'purchaseInvoices.statuses.' + inv.status | translate"
                       [severity]="statusSeverity(inv.status)" />
                @if (inv.creditNoteCount > 0) {
                  <p-tag class="ml-1" [value]="'purchaseInvoices.credited' | translate" severity="danger" />
                }
              </td>
              <td>
                <p-tag [value]="'purchaseInvoices.receptionStatuses.' + inv.receptionStatus | translate"
                       [severity]="receptionSeverity(inv.receptionStatus)" />
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-eye" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'purchaseInvoices.view' | translate"
                        (click)="openDetail(inv)"></button>
                <button pButton icon="pi pi-print" class="p-button-sm p-button-text"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/purchase-invoices/' + inv.id + '/pdf')"></button>
                @if (inv.status === 'DRAFT') {
                  <button pButton icon="pi pi-check" class="p-button-sm p-button-text p-button-info"
                          [pTooltip]="'purchaseInvoices.issue' | translate"
                          (click)="issue(inv)"></button>
                }
                @if (canReceive(inv)) {
                  <button pButton icon="pi pi-inbox" class="p-button-sm p-button-text p-button-success"
                          [pTooltip]="'purchaseInvoices.receive' | translate"
                          (click)="goToReception(inv)"></button>
                }
                @if (canPay(inv)) {
                  <button pButton icon="pi pi-wallet" class="p-button-sm p-button-text"
                          [pTooltip]="'purchaseInvoices.pay' | translate"
                          (click)="goToPay(inv)"></button>
                }
                @if (canCancel(inv.status)) {
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.cancel' | translate"
                          (click)="cancel(inv)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="9" class="text-center text-gray-400 py-8">{{ 'purchaseInvoices.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Detail (voir) modal -->
      <p-dialog [(visible)]="detailOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="('purchaseInvoices.detailTitle' | translate:{ number: detail()?.number || '' })">
        @if (detail(); as inv) {
          <div class="space-y-3">
            <div class="grid grid-cols-3 gap-3 text-sm">
              <div>
                <span class="text-gray-500">{{ 'purchaseInvoices.supplier' | translate }} :</span>
                <div class="font-medium">{{ inv.supplierName }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'purchaseInvoices.invoiceDate' | translate }} :</span>
                <div class="font-medium">{{ inv.invoiceDate | date:'mediumDate' }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'purchaseInvoices.dueDate' | translate }} :</span>
                <div class="font-medium">{{ inv.dueDate ? (inv.dueDate | date:'mediumDate') : '—' }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'purchaseInvoices.status' | translate }} :</span>
                <div><p-tag [value]="'purchaseInvoices.statuses.' + inv.status | translate" [severity]="statusSeverity(inv.status)" /></div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'purchaseInvoices.reception' | translate }} :</span>
                <div><p-tag [value]="'purchaseInvoices.receptionStatuses.' + inv.receptionStatus | translate"
                            [severity]="receptionSeverity(inv.receptionStatus)" /></div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'purchaseInvoices.total' | translate }} :</span>
                <div class="font-bold">{{ inv.total | money }} {{ inv.currency }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'purchaseInvoices.balance' | translate }} :</span>
                <div class="font-bold" [class.text-red-600]="inv.balance > 0" [class.text-green-700]="inv.balance === 0">
                  {{ inv.balance | money }} {{ inv.currency }}
                </div>
              </div>
              @if (inv.supplierReference) {
                <div class="col-span-3">
                  <span class="text-gray-500">{{ 'purchaseInvoices.supplierReference' | translate }} :</span>
                  <span class="font-mono ml-1">{{ inv.supplierReference }}</span>
                </div>
              }
            </div>

            @if (receptions().length > 0) {
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-gray-500 text-sm">{{ 'purchaseInvoices.receptions' | translate }} :</span>
                @for (r of receptions(); track r.id) {
                  <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text"
                          [label]="r.number"
                          (click)="printPdf('/api/v1/goods-receipts/' + r.id + '/pdf')"></button>
                }
              </div>
            }

            @if (detailCreditNotes().length > 0) {
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-gray-500 text-sm">{{ 'purchaseInvoices.creditNotes' | translate }} :</span>
                @for (c of detailCreditNotes(); track c.id) {
                  <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text text-red-700"
                          [label]="c.number + ' (' + (c.total | money) + ' ' + c.currency + ')'"
                          (click)="printPdf('/api/v1/purchase-credit-notes/' + c.id + '/pdf')"></button>
                }
              </div>
            }

            <div class="border rounded">
              <table class="w-full text-sm">
                <thead class="bg-gray-50 text-gray-600">
                  <tr>
                    <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                    <th class="text-right p-2 w-24">{{ 'sales.quantity' | translate }}</th>
                    <th class="text-right p-2 w-28">{{ 'purchaseOrders.unitCost' | translate }}</th>
                    <th class="text-right p-2 w-20">{{ 'sales.tax' | translate }}%</th>
                    <th class="text-right p-2 w-28">{{ 'sales.lineTotal' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (l of inv.lines; track l.id) {
                    <tr class="border-t">
                      <td class="p-2">
                        <div class="font-medium">{{ l.productName }}</div>
                        <div class="text-xs text-gray-500 font-mono">{{ l.sku }}</div>
                      </td>
                      <td class="p-2 text-right">{{ l.quantity }}</td>
                      <td class="p-2 text-right">{{ l.unitCost | money }}</td>
                      <td class="p-2 text-right">{{ (l.taxRate * 100) | number:'1.0-0' }}%</td>
                      <td class="p-2 text-right font-medium">{{ l.lineTotal | money }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
        <ng-template pTemplate="footer">
          <div class="flex justify-between items-center gap-3 w-full">
            <button pButton [label]="'common.close' | translate" class="p-button-text"
                    (click)="detailOpen.set(false)"></button>
            @if (detail(); as inv) {
              <div class="flex items-center gap-2 flex-wrap justify-end">
                <button pButton icon="pi pi-print" [label]="'common.print' | translate"
                        class="p-button-text"
                        (click)="printPdf('/api/v1/purchase-invoices/' + inv.id + '/pdf')"></button>
                @if (canReceive(inv)) {
                  <button pButton icon="pi pi-inbox" [label]="'purchaseInvoices.receive' | translate"
                          class="p-button-success"
                          (click)="goToReception(inv)"></button>
                }
                @if (canPay(inv)) {
                  <button pButton icon="pi pi-wallet" [label]="'purchaseInvoices.pay' | translate"
                          class="p-button-info"
                          (click)="goToPay(inv)"></button>
                }
                @if (canCredit(inv)) {
                  <button pButton icon="pi pi-replay" [label]="'purchaseInvoices.creditNote' | translate"
                          class="p-button-warning"
                          (click)="detailOpen.set(false); openCreditNote(inv)"></button>
                }
              </div>
            }
          </div>
        </ng-template>
      </p-dialog>

      <!-- Create dialog -->
      <p-dialog [(visible)]="createOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="'purchaseInvoices.createTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-3 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'purchaseInvoices.supplier' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.supplierId" [options]="suppliers()"
                          optionLabel="name" optionValue="id"
                          [filter]="true" filterBy="name,code"
                          (onChange)="onSupplierChange()" appendTo="body"
                          [styleClass]="'w-full' + (supplierInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (supplierInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'purchaseInvoices.invoiceDate' | translate }} *</label>
              <p-calendar [(ngModel)]="form.invoiceDate" dateFormat="dd/mm/yy" appendTo="body"
                          [styleClass]="'w-full' + (invoiceDateInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (invoiceDateInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'purchaseInvoices.dueDate' | translate }}</label>
              <p-calendar [(ngModel)]="form.dueDate" dateFormat="dd/mm/yy"
                          styleClass="w-full" appendTo="body" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'purchaseInvoices.supplierReference' | translate }}</label>
              <input pInputText [(ngModel)]="form.supplierReference" class="w-full" />
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

          @if (compensItems().length > 0) {
            <div class="border border-sky-300 rounded bg-sky-50">
              <div class="flex items-center justify-between p-2 border-b border-sky-200">
                <span class="font-medium text-sm text-sky-900">{{ 'compensate.title' | translate }}</span>
                <span class="text-xs text-sky-800">{{ 'compensate.total' | translate }} :
                  <strong>{{ compensTotal() | money }} {{ form.currency }}</strong></span>
              </div>
              <table class="w-full text-sm">
                <thead class="text-sky-800">
                  <tr>
                    <th class="w-8 p-2"></th>
                    <th class="text-left p-2">{{ 'partners.openItemsCol.type' | translate }}</th>
                    <th class="text-left p-2">{{ 'partners.openItemsCol.ref' | translate }}</th>
                    <th class="text-right p-2 w-28">{{ 'partners.openItemsCol.open' | translate }}</th>
                    <th class="text-right p-2 w-32">{{ 'compensate.colImpute' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of compensItems(); track item.sourceId) {
                    <tr class="border-t border-sky-200">
                      <td class="p-2 text-center">
                        <input type="checkbox" [(ngModel)]="item.selected" />
                      </td>
                      <td class="p-2">{{ ('partners.openItemsType.' + item.sourceType) | translate }}</td>
                      <td class="p-2 font-mono text-xs">{{ item.label }}</td>
                      <td class="p-2 text-right text-gray-700">{{ item.amountOpen | money }}</td>
                      <td class="p-1">
                        <p-inputNumber [(ngModel)]="item.amount" [min]="0" [max]="item.amountOpen"
                                       [minFractionDigits]="2" [maxFractionDigits]="2"
                                       [disabled]="!item.selected"
                                       inputStyleClass="w-full text-right" styleClass="w-full" />
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
              <p class="text-xs text-sky-700 p-2 border-t border-sky-200">{{ 'compensate.hint' | translate }}</p>
            </div>
          }

          <div class="border rounded">
            <div class="flex items-center justify-between p-2 bg-gray-50 border-b">
              <span class="font-medium text-sm">{{ 'purchaseOrders.lines' | translate }}</span>
              <button pButton icon="pi pi-plus" [label]="'sales.addLine' | translate"
                      class="p-button-sm p-button-text" (click)="addLine()"></button>
            </div>
            <table class="w-full text-sm">
              <thead class="bg-gray-50 text-gray-600">
                <tr>
                  <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                  <th class="text-right p-2 w-24">{{ 'sales.quantity' | translate }}</th>
                  <th class="text-right p-2 w-28">{{ 'purchaseOrders.unitCost' | translate }}</th>
                  <th class="text-right p-2 w-20">{{ 'sales.tax' | translate }}%</th>
                  <th class="text-right p-2 w-28">{{ 'sales.lineTotal' | translate }}</th>
                  <th class="w-10"></th>
                </tr>
              </thead>
              <tbody>
                @for (line of form.lines; track $index; let i = $index) {
                  <tr class="border-t">
                    <td class="p-1">
                      <p-dropdown [(ngModel)]="line.productId" [options]="products()"
                                  optionLabel="name" optionValue="id"
                                  [filter]="true" filterBy="name,sku"
                                  (onChange)="onProductChange(line)" appendTo="body"
                                  [styleClass]="'w-full' + (lineProductInvalid(line) ? ' ng-invalid ng-dirty' : '')">
                        <ng-template let-product pTemplate="item">
                          <div class="flex items-center justify-between gap-3 w-full">
                            <span>{{ product.name }} <span class="text-gray-400">({{ product.sku }})</span></span>
                            @if (stockLabel(product.id); as s) {
                              <span class="text-xs whitespace-nowrap"
                                    [class.text-red-600]="isOutOfStock(product.id)"
                                    [class.text-gray-500]="!isOutOfStock(product.id)">
                                Stock: {{ s }}
                              </span>
                            }
                          </div>
                        </ng-template>
                      </p-dropdown>
                    </td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="line.quantity" [minFractionDigits]="0" [maxFractionDigits]="3"
                                     inputStyleClass="w-full text-right"
                                     [styleClass]="'w-full' + (lineQtyInvalid(line) ? ' ng-invalid ng-dirty' : '')" />
                    </td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="line.unitCost" [minFractionDigits]="0" [maxFractionDigits]="2"
                                     inputStyleClass="w-full text-right"
                                     [styleClass]="'w-full' + (lineCostInvalid(line) ? ' ng-invalid ng-dirty' : '')" />
                    </td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="line.taxRate" [min]="0" [max]="1"
                                     [minFractionDigits]="0" [maxFractionDigits]="4"
                                     inputStyleClass="w-full text-right" styleClass="w-full" />
                    </td>
                    <td class="p-2 text-right">{{ lineTotal(line) | money }}</td>
                    <td class="p-1 text-center">
                      <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                              (click)="removeLine(i)"></button>
                    </td>
                  </tr>
                }
                @if (form.lines.length === 0) {
                  <tr><td colspan="6" class="p-4 text-center"
                          [class.text-gray-400]="!noLinesInvalid()" [class.text-red-600]="noLinesInvalid()">
                    @if (noLinesInvalid()) {
                      {{ 'common.atLeastOneLine' | translate }}
                    } @else {
                      {{ 'sales.noLines' | translate }}
                    }
                  </td></tr>
                }
              </tbody>
              <tfoot class="bg-gray-50 border-t">
                <tr>
                  <td colspan="4" class="p-2 text-right font-medium">{{ 'sales.total' | translate }}</td>
                  <td class="p-2 text-right font-bold">{{ grandTotal() | money }} {{ form.currency }}</td>
                  <td></td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="createOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Credit note (avoir) dialog -->
      <p-dialog [(visible)]="creditOpen" [modal]="true" [style]="{ width: '640px' }"
                [header]="('purchaseInvoices.creditNoteTitle' | translate) + ' — ' + (creditInvoice()?.number ?? '')"
                [closable]="!crediting()">
        @if (preview(); as p) {
          <div class="space-y-3">
            @if (p.blockReason) {
              <div class="p-3 rounded border border-red-300 bg-red-50 text-sm text-red-800">
                <i class="pi pi-ban mr-1"></i>
                {{ ('purchaseInvoices.creditNoteBlocked.' + p.blockReason) | translate }}
              </div>
            } @else {
              <div class="text-sm space-y-1">
                <div class="flex justify-between"><span class="text-gray-500">{{ 'creditNotes.preview.invoiceTotal' | translate }}</span>
                  <span class="font-medium">{{ creditInvoice()?.total | money }} {{ creditInvoice()?.currency }}</span></div>
                <div class="flex justify-between"><span class="text-gray-500">{{ 'creditNotes.preview.alreadyPaid' | translate }}</span>
                  <span>{{ p.alreadyPaid | money }} {{ creditInvoice()?.currency }}</span></div>
                <div class="flex justify-between"><span class="text-gray-500">{{ 'creditNotes.preview.balance' | translate }}</span>
                  <span>{{ creditInvoice()?.balance | money }} {{ creditInvoice()?.currency }}</span></div>
                <div class="flex justify-between border-t pt-2 mt-1">
                  <span class="font-medium">{{ 'creditNotes.preview.creditAmount' | translate }}</span>
                  <span class="font-bold text-red-700">{{ p.total | money }} {{ creditInvoice()?.currency }}</span></div>
              </div>

              @if (p.alreadyPaid > 0) {
                <div class="p-3 rounded border border-amber-300 bg-amber-50 text-sm text-amber-900">
                  <i class="pi pi-exclamation-triangle mr-1"></i>
                  {{ 'purchaseInvoices.creditNotePaidWarning' | translate:{ amount: (p.alreadyPaid | money) } }}
                </div>
              }

              @if (p.returnLines.length > 0) {
                <div class="p-3 rounded border border-amber-300 bg-amber-50 text-sm text-amber-900">
                  <div class="flex items-center gap-1 mb-1">
                    <i class="pi pi-truck"></i>
                    <strong>{{ 'purchaseInvoices.returnWarningHeader' | translate }}</strong>
                  </div>
                  <ul class="list-disc ml-5">
                    @for (rl of p.returnLines; track rl.productId) {
                      <li>{{ rl.productName }} <span class="font-mono text-xs">({{ rl.sku }})</span> — {{ rl.returnQty }}</li>
                    }
                  </ul>
                </div>
              } @else {
                <div class="text-xs text-gray-500">{{ 'purchaseInvoices.noReturnLines' | translate }}</div>
              }

              <div>
                <label class="block text-sm font-medium mb-1">{{ 'purchaseInvoices.reason' | translate }}</label>
                <input pInputText [(ngModel)]="creditReason" class="w-full" />
              </div>
            }
          </div>
        }
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="creditOpen = false" [disabled]="crediting()"></button>
          <button pButton [label]="'purchaseInvoices.creditNoteAction' | translate" icon="pi pi-check"
                  class="p-button-warning"
                  (click)="confirmCreditNote()" [loading]="crediting()"
                  [disabled]="!preview() || !!preview()?.blockReason"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class PurchaseInvoiceListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private router = inject(Router);

  protected invoices = signal<PurchaseInvoice[]>([]);
  protected suppliers = signal<SupplierOpt[]>([]);
  protected products = signal<ProductOpt[]>([]);
  protected stockBreakdown = signal<Record<string, number[]>>({});

  // Net-position offset menu: the supplier's POSITIVE open items — our open sale
  // invoices (INVOICE) when the party is also a customer — which can be netted
  // against the purchase invoice (NEGATIVE) being created.
  protected compensItems = signal<CompensItem[]>([]);

  protected stockLabel(productId: string): string {
    const arr = this.stockBreakdown()[productId];
    if (!arr || arr.length === 0) return '';
    return arr.map(n => String(n)).join(',');
  }

  protected isOutOfStock(productId: string): boolean {
    const arr = this.stockBreakdown()[productId];
    if (!arr || arr.length === 0) return false;
    return arr.every(n => n <= 0);
  }
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected createOpen = false;

  protected supplierInvalid(): boolean { return this.submitted() && !this.form.supplierId; }
  protected invoiceDateInvalid(): boolean { return this.submitted() && !this.form.invoiceDate; }
  protected noLinesInvalid(): boolean { return this.submitted() && this.form.lines.length === 0; }
  protected lineProductInvalid(line: LineForm): boolean { return this.submitted() && !line.productId; }
  protected lineQtyInvalid(line: LineForm): boolean {
    return this.submitted() && (line.quantity == null || line.quantity <= 0);
  }
  protected lineCostInvalid(line: LineForm): boolean {
    return this.submitted() && (line.unitCost == null || line.unitCost < 0);
  }

  // Detail (voir) modal state
  protected detailOpen = signal(false);
  protected detail = signal<PurchaseInvoiceDetail | null>(null);
  protected receptions = signal<ReceptionLite[]>([]);
  protected detailCreditNotes = signal<PurchaseCreditNoteLite[]>([]);

  // Avoir (credit note) dialog state
  protected creditOpen = false;
  protected crediting = signal(false);
  protected creditInvoice = signal<PurchaseInvoice | null>(null);
  protected preview = signal<CreditNotePreview | null>(null);
  protected creditReason = '';

  protected form: {
    supplierId: string | null;
    invoiceDate: Date;
    dueDate: Date | null;
    supplierReference: string;
    currency: string;
    notes: string;
    lines: LineForm[];
  } = this.emptyForm();

  ngOnInit() {
    // auto-loaded by p-table lazy
    this.loadSuppliers();
    this.loadProducts();
  }

  protected statusSeverity(status: string): Severity {
    return ({
      DRAFT: 'secondary', ISSUED: 'info',
      PARTIAL: 'warning', PAID: 'success', CANCELLED: 'secondary',
    } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected receptionSeverity(status: string): Severity {
    return ({
      NONE: 'secondary', PARTIALLY_RECEIVED: 'warning',
      RECEIVED: 'success', RETURNED: 'danger',
    } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected canReceive(inv: PurchaseInvoice): boolean {
    return inv.status !== 'DRAFT' && inv.status !== 'CANCELLED'
        && inv.creditNoteCount === 0
        && inv.receptionStatus !== 'RECEIVED' && inv.receptionStatus !== 'RETURNED';
  }

  protected canPay(inv: PurchaseInvoice): boolean {
    return (inv.status === 'ISSUED' || inv.status === 'PARTIAL') && inv.balance > 0;
  }

  protected canCredit(inv: PurchaseInvoice): boolean {
    return inv.status !== 'DRAFT' && inv.status !== 'CANCELLED' && inv.creditNoteCount === 0;
  }

  protected canCancel(status: string): boolean {
    return status === 'DRAFT';
  }

  protected issue(inv: PurchaseInvoice) {
    this.confirmation.confirm({
      message: this.i18n.instant('purchaseInvoices.issueHint', { number: inv.number }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-check',
      accept: async () => {
        await firstValueFrom(this.http.post(`/api/v1/purchase-invoices/${inv.id}/issue`, {}));
        this.reload();
      },
    });
  }

  protected goToReception(inv: PurchaseInvoice) {
    this.router.navigate(['/goods-receipts'], { queryParams: { invoiceId: inv.id } });
  }

  protected goToPay(inv: PurchaseInvoice) {
    this.router.navigate(['/payments'], { queryParams: { createForPurchaseInvoice: inv.id } });
  }

  protected async openDetail(inv: PurchaseInvoice) {
    this.detail.set(null);
    this.receptions.set([]);
    this.detailCreditNotes.set([]);
    this.detailOpen.set(true);
    try {
      const full = await firstValueFrom(
        this.http.get<PurchaseInvoiceDetail>(`/api/v1/purchase-invoices/${inv.id}`));
      this.detail.set(full);
    } catch { this.detail.set(null); }
    // Linked receptions (BRC) + avoirs — best-effort, mirrors the sales detail.
    try {
      const r = await firstValueFrom(
        this.http.get<{ content: ReceptionLite[] }>(`/api/v1/goods-receipts?invoiceId=${inv.id}&size=100`));
      this.receptions.set(r.content ?? []);
    } catch { this.receptions.set([]); }
    try {
      const c = await firstValueFrom(
        this.http.get<{ content: PurchaseCreditNoteLite[] }>(`/api/v1/purchase-credit-notes?invoiceId=${inv.id}&size=100`));
      this.detailCreditNotes.set(c.content ?? []);
    } catch { this.detailCreditNotes.set([]); }
  }

  protected async openCreditNote(inv: PurchaseInvoice) {
    this.creditInvoice.set(inv);
    this.preview.set(null);
    this.creditReason = '';
    this.creditOpen = true;
    try {
      const p = await firstValueFrom(
        this.http.get<CreditNotePreview>(`/api/v1/purchase-invoices/${inv.id}/credit-note-preview`));
      this.preview.set(p);
    } catch { this.preview.set(null); }
  }

  protected async confirmCreditNote() {
    const inv = this.creditInvoice();
    if (!inv) return;
    this.crediting.set(true);
    try {
      await firstValueFrom(this.http.post(`/api/v1/purchase-invoices/${inv.id}/credit-notes`,
        { reason: this.creditReason || null }));
      this.creditOpen = false;
      this.reload();
    } finally {
      this.crediting.set(false);
    }
  }

  protected cancel(inv: PurchaseInvoice) {
    this.confirmation.confirm({
      message: this.i18n.instant('purchaseInvoices.cancelHint', { number: inv.number }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.post(`/api/v1/purchase-invoices/${inv.id}/cancel`, {}));
        this.reload();
      },
    });
  }

  protected async printPdf(url: string) {
    try {
      const blob = await firstValueFrom(this.http.get(url, { responseType: 'blob' }));
      const blobUrl = URL.createObjectURL(blob);
      window.open(blobUrl, '_blank');
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
    } catch (e) { console.error('PDF fetch failed', e); }
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.compensItems.set([]);
    this.createOpen = true;
  }

  protected onSupplierChange() {
    const s = this.suppliers().find(x => x.id === this.form.supplierId);
    if (s && !this.form.currency) this.form.currency = s.currency;
    this.refreshCompensItems();
  }

  private async refreshCompensItems() {
    if (!this.form.supplierId) {
      this.compensItems.set([]);
      return;
    }
    try {
      const items = await firstValueFrom(
        this.http.get<{ sourceType: string; sourceId: string; sign: string; amountOpen: number; label: string }[]>(
          `/api/v1/allocations/open-items?partyId=${this.form.supplierId}`));
      // A purchase invoice is NEGATIVE, so it nets against the party's POSITIVE
      // items: our open sale invoices (when the supplier is also a customer).
      const positives = (items ?? [])
        .filter(i => i.sign === 'POSITIVE' && i.sourceType === 'INVOICE')
        .map(i => ({
          sourceType: i.sourceType,
          sourceId: i.sourceId,
          label: i.label,
          amountOpen: Number(i.amountOpen),
          selected: false,
          amount: Number(i.amountOpen),
        }));
      this.compensItems.set(positives);
    } catch {
      this.compensItems.set([]);
    }
  }

  protected compensTotal(): number {
    return +this.compensItems()
      .filter(i => i.selected)
      .reduce((s, i) => s + (i.amount || 0), 0)
      .toFixed(2);
  }

  protected addLine() {
    this.form.lines.push({ productId: null, uomId: null, quantity: 1, unitCost: 0, taxRate: 0 });
  }

  protected removeLine(i: number) { this.form.lines.splice(i, 1); }

  protected onProductChange(line: LineForm) {
    const p = this.products().find(x => x.id === line.productId);
    if (!p) return;
    line.uomId = p.baseUomId;
    line.taxRate = p.defaultTaxRate ?? 0;
  }

  protected lineTotal(line: LineForm): number {
    return +((line.quantity || 0) * (line.unitCost || 0)).toFixed(2);
  }

  protected grandTotal(): number {
    return this.form.lines.reduce((s, l) => s + this.lineTotal(l), 0);
  }

  protected canSave(): boolean {
    return !!this.form.supplierId
        && this.form.lines.length > 0
        && this.form.lines.every(l => !!l.productId && (l.quantity || 0) > 0);
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.canSave()) return;
    this.saving.set(true);
    try {
      const payload = {
        supplierId: this.form.supplierId,
        purchaseOrderId: null,
        supplierReference: this.form.supplierReference || null,
        invoiceDate: this.toIsoDate(this.form.invoiceDate),
        dueDate: this.form.dueDate ? this.toIsoDate(this.form.dueDate) : null,
        currency: this.form.currency || null,
        notes: this.form.notes || null,
        lines: this.form.lines.map(l => ({
          productId: l.productId,
          uomId: l.uomId,
          quantity: l.quantity,
          unitCost: l.unitCost,
          taxRate: l.taxRate,
        })),
      };
      const created = await firstValueFrom(
        this.http.post<{ id: string }>('/api/v1/purchase-invoices', payload));
      if (created?.id && this.compensTotal() > 0) {
        await this.applyCompensationsToCreatedInvoice(created.id);
      }
      this.createOpen = false;
      this.compensItems.set([]);
      this.reload();
    } finally {
      this.saving.set(false);
    }
  }

  private async applyCompensationsToCreatedInvoice(purchaseInvoiceId: string) {
    // The new purchase invoice is the NEGATIVE side; each selected sale invoice
    // is POSITIVE. The engine caps every call to min(open balances, amount), so
    // sequential best-effort is safe (later rows net to 0 once covered).
    for (const item of this.compensItems()) {
      if (!item.selected || (item.amount || 0) <= 0) continue;
      try {
        await firstValueFrom(this.http.post(
          `/api/v1/allocations/apply?partyId=${this.form.supplierId}`, {
            positiveType: item.sourceType,
            positiveId: item.sourceId,
            negativeType: 'PURCHASE_INVOICE',
            negativeId: purchaseInvoiceId,
            amount: item.amount,
          }));
      } catch {
        // Best-effort: a single failure shouldn't block the invoice from
        // existing. The user can compensate the rest manually later.
      }
    }
  }

  private toIsoDate(d: Date): string {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: PurchaseInvoice[]; totalElements: number }>(
          `/api/v1/purchase-invoices?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.invoices()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.invoices.set(arr);
      this.total.set(totalElements);
    } catch {
      this.invoices.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.invoices.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadSuppliers() {
    try {
      const res = await firstValueFrom(this.http.get<{ content: SupplierOpt[] }>('/api/v1/partners?role=SUPPLIER&size=200'));
      this.suppliers.set(res.content ?? []);
    } catch { this.suppliers.set([]); }
  }

  private async loadProducts() {
    try {
      const res = await firstValueFrom(this.http.get<{ content: ProductOpt[] }>('/api/v1/products?size=500'));
      this.products.set((res.content ?? []).filter((p: any) => p.active !== false));
    } catch { this.products.set([]); }
    this.loadStockBreakdown();
  }

  private async loadStockBreakdown() {
    try {
      const res = await firstValueFrom(
        this.http.get<ProductStockBreakdown[]>('/api/v1/inventory/stocks/by-product')
      );
      const map: Record<string, number[]> = {};
      for (const it of res ?? []) {
        map[it.productId] = it.warehouses.map(w => Number(w.qtyAvailable));
      }
      this.stockBreakdown.set(map);
    } catch {
      this.stockBreakdown.set({});
    }
  }

  private emptyForm() {
    return {
      supplierId: null as string | null,
      invoiceDate: new Date(),
      dueDate: new Date(new Date().setDate(new Date().getDate() + 30)) as Date | null,
      supplierReference: '',
      currency: '',
      notes: '',
      lines: [] as LineForm[],
    };
  }
}
