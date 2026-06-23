import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '@minierp/shared-i18n';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CalendarModule } from 'primeng/calendar';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

interface Invoice {
  id: string;
  number: string;
  customerId: string;
  customerName: string;
  issueDate: string;
  dueDate: string;
  status: string;
  deliveryStatus: string;
  currency: string;
  total: number;
  balance: number;
  quoteId: string | null;
  quoteNumber: string | null;
  quoteStatus: string | null;
  creditNoteCount: number;
}

interface InvoiceDelivery {
  id: string;
  number: string;
}

interface InvoiceCreditNote {
  id: string;
  number: string;
  total: number;
  currency: string;
  issueDate: string;
}

interface InvoiceLine {
  id: string;
  lineNumber: number;
  productId: string;
  uomId: string;
  productName: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  taxRate: number;
  lineTotal: number;
}

interface InvoiceDetail extends Invoice {
  paidAmount: number;
  subtotal: number;
  taxAmount: number;
  paymentTerms: string | null;
  notes: string | null;
  lines: InvoiceLine[];
}

interface CreditNoteReturnLine {
  productId: string;
  productName: string;
  sku: string;
  uomId: string;
  quantityToReturn: number;
}

interface CreditNotePreview {
  invoiceId: string;
  invoiceNumber: string;
  customerId: string;
  customerName: string;
  currency: string;
  invoiceTotal: number;
  alreadyPaidAmount: number;
  invoiceBalance: number;
  creditNoteAmount: number;
  amountToCustomerCredit: number;
  willCreateReturnBl: boolean;
  returnLines: CreditNoteReturnLine[];
  blockReason: string | null;
}

/** A net-position open item (credit or opposite-chain invoice) the user can
 *  offset against the invoice being created. */
interface CompensItem {
  sourceType: string;
  sourceId: string;
  label: string;
  amountOpen: number;
  selected: boolean;
  amount: number;
}

interface CustomerOpt {
  id: string;
  code: string;
  name: string;
  currency: string;
  defaultPriceTierId: string | null;
}
interface ProductVariantOpt { id: string; defaultVariant: boolean; active: boolean; }
interface ProductOpt {
  id: string;
  sku: string;
  name: string;
  baseUomId: string;
  defaultTaxRate: number;
  variants: ProductVariantOpt[];
}

interface ProductStockBreakdown {
  productId: string;
  warehouses: { warehouseId: string; warehouseCode: string; warehouseName: string; isDefault: boolean; qtyAvailable: number }[];
}

interface WarehouseOpt {
  id: string;
  code: string;
  name: string;
  defaultWarehouse: boolean;
}

interface LineForm {
  productId: string | null;
  uomId: string | null;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  taxRate: number;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-invoice-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule, ButtonModule, TooltipModule,
    DialogModule, DropdownModule, InputTextModule, InputNumberModule, CalendarModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'invoices.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'invoices.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'invoices.createDirect' | translate"
                class="p-button-sm p-button-outlined"
                [pTooltip]="'invoices.createDirectHint' | translate"
                (click)="openCreate()"></button>
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
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'sales.issueDate' | translate }}</th>
              <th>{{ 'invoices.dueDate' | translate }}</th>
              <th class="text-right">{{ 'sales.total' | translate }}</th>
              <th class="text-right">{{ 'invoices.balance' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-inv>
            <tr>
              <td>
                <span class="font-mono text-sm">{{ inv.number }}</span>
                @if (inv.creditNoteCount > 0) {
                  <span class="ml-2 inline-flex items-center justify-center w-5 h-5 rounded-full bg-red-600 text-white text-xs font-bold"
                        [pTooltip]="('invoices.hasCreditNotes' | translate:{ count: inv.creditNoteCount })">A</span>
                }
              </td>
              <td>{{ inv.customerName }}</td>
              <td>{{ inv.issueDate | date:'mediumDate' }}</td>
              <td [class.text-red-600]="isOverdue(inv)">{{ inv.dueDate | date:'mediumDate' }}</td>
              <td class="text-right font-medium">{{ inv.total | money }} {{ inv.currency }}</td>
              <td class="text-right" [class.text-red-600]="inv.balance > 0">
                {{ inv.balance | money }} {{ inv.currency }}
              </td>
              <td>
                <p-tag [value]="'sales.statuses.' + inv.status | translate" [severity]="statusSeverity(inv.status)" />
                @if (inv.deliveryStatus && inv.deliveryStatus !== 'NONE') {
                  <p-tag [value]="'invoices.deliveryStatuses.' + inv.deliveryStatus | translate"
                         [severity]="deliveryStatusSeverity(inv.deliveryStatus)"
                         styleClass="ml-1 text-[10px] py-0 px-1" />
                }
                @if (inv.quoteNumber) {
                  <div class="text-xs text-gray-500 mt-1 flex items-center gap-1">
                    <span>&larr;</span>
                    <span class="font-mono">{{ inv.quoteNumber }}</span>
                  </div>
                }
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-eye" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'invoices.view' | translate"
                        (click)="openDetail(inv)"></button>
                @if (inv.status === 'DRAFT') {
                  <button pButton icon="pi pi-send" class="p-button-sm p-button-text mr-1"
                          [pTooltip]="'invoices.issue' | translate"
                          (click)="issueInvoice(inv)"></button>
                  <button pButton icon="pi pi-ban" class="p-button-sm p-button-text p-button-danger mr-1"
                          [pTooltip]="'invoices.cancel' | translate"
                          (click)="cancelInvoice(inv)"></button>
                }
                @if (canCreateDelivery(inv)) {
                  <button pButton icon="pi pi-truck" class="p-button-sm p-button-text mr-1"
                          [pTooltip]="'invoices.createDelivery' | translate"
                          (click)="goToCreateDelivery(inv.id)"></button>
                }
                @if (canCreatePayment(inv)) {
                  <button pButton icon="pi pi-wallet" class="p-button-sm p-button-text p-button-success mr-1"
                          [pTooltip]="'invoices.createPayment' | translate"
                          (click)="goToCreatePayment(inv.id)"></button>
                }
                <button pButton icon="pi pi-print" class="p-button-sm p-button-text"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/invoices/' + inv.id + '/pdf')"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'invoices.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Create invoice modal -->
      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="'invoices.createTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-3 gap-3">
            <div class="col-span-1">
              <label class="block text-sm font-medium mb-1">{{ 'sales.customer' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.customerId" [options]="customers()"
                          optionLabel="name" optionValue="id"
                          [filter]="true" filterBy="name,code"
                          (onChange)="onCustomerChange()" appendTo="body"
                          [styleClass]="'w-full' + (customerInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (customerInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'sales.issueDate' | translate }} *</label>
              <p-calendar [(ngModel)]="form.issueDate" dateFormat="dd/mm/yy" appendTo="body"
                          [styleClass]="'w-full' + (issueDateInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (issueDateInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'invoices.dueDate' | translate }} *</label>
              <p-calendar [(ngModel)]="form.dueDate" dateFormat="dd/mm/yy" appendTo="body"
                          [styleClass]="'w-full' + (dueDateInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (dueDateInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
          </div>
          <div class="grid grid-cols-3 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.currency' | translate }}</label>
              <input pInputText [(ngModel)]="form.currency" class="w-full" />
            </div>
            <div class="col-span-2">
              <label class="block text-sm font-medium mb-1">{{ 'invoices.paymentTerms' | translate }}</label>
              <input pInputText [(ngModel)]="form.paymentTerms" class="w-full"
                     [placeholder]="'invoices.paymentTermsHint' | translate" />
            </div>
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
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="form.notes" class="w-full" />
          </div>

          <div class="border rounded">
            <div class="flex items-center justify-between p-2 bg-gray-50 border-b">
              <span class="font-medium text-sm">{{ 'sales.lines' | translate }}</span>
              <button pButton icon="pi pi-plus" [label]="'sales.addLine' | translate"
                      class="p-button-sm p-button-text" (click)="addLine()"></button>
            </div>
            <table class="w-full text-sm">
              <thead class="bg-gray-50 text-gray-600">
                <tr>
                  <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                  <th class="text-right p-2 w-24">{{ 'sales.quantity' | translate }}</th>
                  <th class="text-right p-2 w-28">{{ 'sales.unitPrice' | translate }}</th>
                  <th class="text-right p-2 w-20">{{ 'sales.discount' | translate }}%</th>
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
                      <p-inputNumber [(ngModel)]="line.unitPrice" [minFractionDigits]="0" [maxFractionDigits]="2"
                                     inputStyleClass="w-full text-right" styleClass="w-full" />
                    </td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="line.discountPercent" [min]="0" [max]="100"
                                     [minFractionDigits]="0" [maxFractionDigits]="2"
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
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Delivery choice dialog (opens after Enregistrer) -->
      <p-dialog [(visible)]="deliveryDialogOpen" [modal]="true" [style]="{ width: '480px' }"
                [header]="'invoices.deliveryChoice.title' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <p class="text-sm text-gray-700">{{ 'invoices.deliveryChoice.question' | translate }}</p>
          <div class="space-y-2">
            <label class="flex items-start gap-2 cursor-pointer">
              <input type="radio" name="deliveryMode" [(ngModel)]="deliveryMode" value="immediate"
                     class="mt-1" />
              <span>
                <span class="font-medium">{{ 'invoices.deliveryChoice.immediate' | translate }}</span>
                <span class="block text-xs text-gray-500">{{ 'invoices.deliveryChoice.immediateHint' | translate }}</span>
              </span>
            </label>
            <label class="flex items-start gap-2 cursor-pointer">
              <input type="radio" name="deliveryMode" [(ngModel)]="deliveryMode" value="deferred"
                     class="mt-1" />
              <span>
                <span class="font-medium">{{ 'invoices.deliveryChoice.deferred' | translate }}</span>
                <span class="block text-xs text-gray-500">{{ 'invoices.deliveryChoice.deferredHint' | translate }}</span>
              </span>
            </label>
          </div>
          @if (deliveryMode === 'immediate' && warehouses().length > 1) {
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'invoices.deliveryChoice.warehouse' | translate }} *</label>
              <p-dropdown [(ngModel)]="deliveryWarehouseId" [options]="warehouses()"
                          optionLabel="name" optionValue="id" appendTo="body"
                          styleClass="w-full" />
            </div>
          }
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="cancelDeliveryDialog()" [disabled]="saving()"></button>
          <button pButton [label]="'common.confirm' | translate" icon="pi pi-check"
                  (click)="confirmDeliveryChoice()" [loading]="saving()"
                  [disabled]="deliveryMode === 'immediate' && !deliveryWarehouseId"></button>
        </ng-template>
      </p-dialog>

      <!-- Invoice detail modal -->
      <p-dialog [(visible)]="detailOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="('invoices.detailTitle' | translate:{ number: detail()?.number || '' })">
        @if (detail(); as inv) {
          <div class="space-y-3">
            <div class="grid grid-cols-3 gap-3 text-sm">
              <div>
                <span class="text-gray-500">{{ 'sales.customer' | translate }} :</span>
                <div class="font-medium">{{ inv.customerName }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'sales.issueDate' | translate }} :</span>
                <div class="font-medium">{{ inv.issueDate | date:'mediumDate' }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'invoices.dueDate' | translate }} :</span>
                <div class="font-medium" [class.text-red-600]="isOverdue(inv)">{{ inv.dueDate | date:'mediumDate' }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'sales.status' | translate }} :</span>
                <div><p-tag [value]="'sales.statuses.' + inv.status | translate" [severity]="statusSeverity(inv.status)" /></div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'sales.total' | translate }} :</span>
                <div class="font-bold">{{ inv.total | money }} {{ inv.currency }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'invoices.balance' | translate }} :</span>
                <div class="font-bold" [class.text-red-600]="inv.balance > 0" [class.text-green-700]="inv.balance === 0">
                  {{ inv.balance | money }} {{ inv.currency }}
                </div>
              </div>
              @if (inv.quoteNumber) {
                <div class="col-span-3">
                  <span class="text-gray-500">{{ 'invoices.linkedQuote' | translate }} :</span>
                  <span class="font-mono ml-1">{{ inv.quoteNumber }}</span>
                </div>
              }
              @if (inv.status !== 'DRAFT' && inv.status !== 'CANCELLED') {
                <div class="col-span-3">
                  <span class="text-gray-500">{{ 'invoices.deliveryStatus' | translate }} :</span>
                  <p-tag [value]="'invoices.deliveryStatuses.' + inv.deliveryStatus | translate"
                         [severity]="deliveryStatusSeverity(inv.deliveryStatus)"
                         styleClass="ml-2 text-xs" />
                </div>
              }
            </div>

            @if (inv.status !== 'DRAFT' && inv.status !== 'CANCELLED') {
              @if (deliveries().length > 0) {
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="text-gray-500 text-sm">{{ 'invoices.deliveries' | translate }} :</span>
                  @for (d of deliveries(); track d.id) {
                    <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text"
                            [pTooltip]="d.number"
                            (click)="printPdf('/api/v1/deliveries/' + d.id + '/pdf')"></button>
                  }
                </div>
              }

              @if (creditNotes().length > 0) {
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="text-gray-500 text-sm">{{ 'invoices.creditNotes' | translate }} :</span>
                  @for (c of creditNotes(); track c.id) {
                    <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text text-red-700"
                            [label]="c.number + ' (' + (c.total | money) + ' ' + c.currency + ')'"
                            (click)="printPdf('/api/v1/credit-notes/' + c.id + '/pdf')"></button>
                  }
                </div>
              }
            }

            <div class="border rounded">
              <table class="w-full text-sm">
                <thead class="bg-gray-50 text-gray-600">
                  <tr>
                    <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                    <th class="text-right p-2 w-24">{{ 'sales.quantity' | translate }}</th>
                    <th class="text-right p-2 w-28">{{ 'sales.unitPrice' | translate }}</th>
                    <th class="text-right p-2 w-20">{{ 'sales.discount' | translate }}%</th>
                    @if (taxEnabled()) {
                      <th class="text-right p-2 w-20">TVA</th>
                    }
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
                      <td class="p-2 text-right">{{ l.unitPrice | money }}</td>
                      <td class="p-2 text-right">{{ l.discountPercent > 0 ? (l.discountPercent + '%') : '—' }}</td>
                      @if (taxEnabled()) {
                        <td class="p-2 text-right">{{ (l.taxRate * 100) | number:'1.0-0' }}%</td>
                      }
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
                        (click)="printPdf('/api/v1/invoices/' + inv.id + '/pdf')"></button>
                <button pButton icon="pi pi-copy" [label]="'invoices.duplicate' | translate"
                        class="p-button-text"
                        (click)="duplicateInvoice(inv)"></button>
                @if (canCreateDelivery(inv)) {
                  <button pButton icon="pi pi-truck" [label]="'invoices.createDelivery' | translate"
                          class="p-button-info"
                          (click)="goToCreateDelivery(inv.id)"></button>
                }
                @if (canCreatePayment(inv)) {
                  <button pButton icon="pi pi-wallet" [label]="'invoices.createPayment' | translate"
                          class="p-button-success"
                          (click)="goToCreatePayment(inv.id)"></button>
                }
                @if (canCreateCreditNote(inv)) {
                  <button pButton icon="pi pi-undo" [label]="'invoices.createCreditNote' | translate"
                          class="p-button-warning"
                          (click)="openCreateCreditNote(inv)"></button>
                }
              </div>
            }
          </div>
        </ng-template>
      </p-dialog>

      <!-- Create credit note modal (total avoir, preview-only) -->
      <p-dialog [(visible)]="creditOpen" [modal]="true" [style]="{ width: '640px' }"
                [header]="('creditNotes.createTitle' | translate:{ number: creditPreview()?.invoiceNumber || '' })"
                [closable]="!savingCredit()">
        @if (creditPreview(); as cp) {
          <div class="space-y-3">
            @if (cp.blockReason) {
              <div class="p-3 rounded border border-red-300 bg-red-50 text-sm text-red-800">
                <i class="pi pi-ban mr-1"></i>
                {{ ('creditNotes.blocked.' + cp.blockReason) | translate }}
              </div>
            } @else {
              <div class="text-sm space-y-1">
                <div class="flex justify-between"><span class="text-gray-500">{{ 'creditNotes.preview.invoiceTotal' | translate }}</span>
                  <span class="font-medium">{{ cp.invoiceTotal | money }} {{ cp.currency }}</span></div>
                <div class="flex justify-between"><span class="text-gray-500">{{ 'creditNotes.preview.alreadyPaid' | translate }}</span>
                  <span>{{ cp.alreadyPaidAmount | money }} {{ cp.currency }}</span></div>
                <div class="flex justify-between"><span class="text-gray-500">{{ 'creditNotes.preview.balance' | translate }}</span>
                  <span>{{ cp.invoiceBalance | money }} {{ cp.currency }}</span></div>
                <div class="flex justify-between border-t pt-2 mt-1">
                  <span class="font-medium">{{ 'creditNotes.preview.creditAmount' | translate }}</span>
                  <span class="font-bold text-red-700">{{ cp.creditNoteAmount | money }} {{ cp.currency }}</span></div>
              </div>

              @if (cp.amountToCustomerCredit > 0) {
                <div class="p-3 rounded border border-amber-300 bg-amber-50 text-sm text-amber-900">
                  <i class="pi pi-exclamation-triangle mr-1"></i>
                  {{ 'creditNotes.preview.paidWarning' | translate:{
                      amount: cp.amountToCustomerCredit, currency: cp.currency
                  } }}
                </div>
              }

              @if (cp.willCreateReturnBl) {
                <div class="p-3 rounded border border-amber-300 bg-amber-50 text-sm text-amber-900">
                  <div class="flex items-center gap-1 mb-1">
                    <i class="pi pi-truck"></i>
                    <strong>{{ 'creditNotes.preview.brWarningHeader' | translate }}</strong>
                  </div>
                  <ul class="list-disc ml-5">
                    @for (rl of cp.returnLines; track rl.productId) {
                      <li>{{ rl.productName }} <span class="font-mono text-xs">({{ rl.sku }})</span> — {{ rl.quantityToReturn }}</li>
                    }
                  </ul>
                </div>
              }

              <div>
                <label class="block text-sm font-medium mb-1">{{ 'creditNotes.reason' | translate }}</label>
                <input pInputText [(ngModel)]="creditForm.reason" class="w-full"
                       [placeholder]="'creditNotes.reasonPlaceholder' | translate" />
              </div>
            }
          </div>
        }
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="creditOpen.set(false)" [disabled]="savingCredit()"></button>
          <button pButton [label]="'creditNotes.confirm' | translate" icon="pi pi-check"
                  class="p-button-warning"
                  (click)="saveCreditNote()"
                  [disabled]="!creditPreview() || !!creditPreview()?.blockReason"
                  [loading]="savingCredit()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class InvoiceListPage implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  private translate = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected invoices = signal<Invoice[]>([]);
  protected customers = signal<CustomerOpt[]>([]);
  protected products = signal<ProductOpt[]>([]);

  /** Resolve the variant to invoice for a product: its default (or first active) variant. */
  protected variantIdFor(productId: string | null): string | null {
    if (!productId) return null;
    const vs = this.products().find(p => p.id === productId)?.variants ?? [];
    return (vs.find(v => v.defaultVariant && v.active)
      ?? vs.find(v => v.active) ?? vs[0])?.id ?? null;
  }
  protected stockBreakdown = signal<Record<string, number[]>>({});

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
  protected dialogOpen = false;

  // Post-save delivery choice dialog
  protected warehouses = signal<WarehouseOpt[]>([]);
  protected deliveryDialogOpen = signal(false);
  protected deliveryMode: 'deferred' | 'immediate' = 'immediate';
  protected deliveryWarehouseId: string | null = null;
  private pendingInvoicePayload: Record<string, unknown> | null = null;

  // Net-position offset menu (unified AllocationEngine). Populated when the user
  // picks a customer: the customer's NEGATIVE open items — credits (CUSTOMER_CREDIT)
  // and, when the party is also a supplier, our open purchase invoices
  // (PURCHASE_INVOICE) — which can be netted against the sale invoice (POSITIVE)
  // being created. Credits are pre-checked to preserve the prior auto-apply UX.
  protected compensItems = signal<CompensItem[]>([]);

  protected detailOpen = signal(false);
  protected detail = signal<InvoiceDetail | null>(null);
  protected deliveries = signal<InvoiceDelivery[]>([]);
  protected creditNotes = signal<InvoiceCreditNote[]>([]);
  protected taxEnabled = signal(true);

  protected creditOpen = signal(false);
  protected creditPreview = signal<CreditNotePreview | null>(null);
  protected savingCredit = signal(false);
  protected creditForm: { reason: string } = { reason: '' };

  protected customerInvalid(): boolean { return this.submitted() && !this.form.customerId; }
  protected issueDateInvalid(): boolean { return this.submitted() && !this.form.issueDate; }
  protected dueDateInvalid(): boolean { return this.submitted() && !this.form.dueDate; }
  protected noLinesInvalid(): boolean { return this.submitted() && this.form.lines.length === 0; }
  protected lineProductInvalid(line: LineForm): boolean { return this.submitted() && !line.productId; }
  protected lineQtyInvalid(line: LineForm): boolean {
    return this.submitted() && (line.quantity == null || line.quantity <= 0);
  }

  protected form: {
    customerId: string | null;
    issueDate: Date;
    dueDate: Date;
    currency: string;
    paymentTerms: string;
    notes: string;
    lines: LineForm[];
  } = this.emptyForm();

  ngOnInit() {
    // Invoices are fetched on demand via the p-table's onLazyLoad.
    this.loadCustomers();
    this.loadProducts();
    this.loadSettings();
    this.loadWarehouses();
  }

  private async loadWarehouses() {
    try {
      const list = await firstValueFrom(this.http.get<WarehouseOpt[]>('/api/v1/inventory/warehouses'));
      this.warehouses.set(list ?? []);
    } catch {
      this.warehouses.set([]);
    }
  }

  private async loadSettings() {
    try {
      const s = await firstValueFrom(this.http.get<{ invoiceSettings?: { taxEnabled?: boolean } }>('/api/v1/settings'));
      const enabled = s?.invoiceSettings?.taxEnabled;
      if (typeof enabled === 'boolean') this.taxEnabled.set(enabled);
    } catch { /* keep default true */ }
  }

  protected isOverdue(inv: Invoice): boolean {
    return inv.status !== 'PAID' && inv.status !== 'CANCELLED'
        && new Date(inv.dueDate) < new Date();
  }

  protected statusSeverity(status: string): Severity {
    return ({ DRAFT: 'secondary', ISSUED: 'info', PARTIAL: 'warning', PAID: 'success', OVERDUE: 'danger', CANCELLED: 'secondary' } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected deliveryStatusSeverity(status: string): Severity {
    return ({
      NONE: 'secondary', PARTIALLY_DELIVERED: 'warning', DELIVERED: 'success', RETURNED: 'danger',
    } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected async issueInvoice(inv: Invoice) {
    try {
      await firstValueFrom(this.http.post(`/api/v1/invoices/${inv.id}/issue`, {}));
      this.reload();
    } catch { /* errors handled globally */ }
  }

  protected cancelInvoice(inv: Invoice) {
    this.confirmation.confirm({
      message: this.translate.instant('invoices.cancelConfirm'),
      header: this.translate.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      accept: async () => {
        try {
          await firstValueFrom(this.http.post(`/api/v1/invoices/${inv.id}/cancel`, {}));
          this.reload();
        } catch { /* errors handled globally */ }
      },
    });
  }

  protected canCreateCreditNote(inv: { status: string; creditNoteCount: number }): boolean {
    // An avoir is total-only and one per invoice: hide the action once the
    // invoice already carries a non-draft credit note (it is then settled).
    return inv.status !== 'CANCELLED' && inv.status !== 'DRAFT' && inv.creditNoteCount === 0;
  }

  protected canCreateDelivery(inv: { status: string; deliveryStatus: string; creditNoteCount: number }): boolean {
    return inv.status !== 'DRAFT' && inv.status !== 'CANCELLED' && inv.creditNoteCount === 0
        && inv.deliveryStatus !== 'DELIVERED' && inv.deliveryStatus !== 'RETURNED';
  }

  protected goToCreateDelivery(invoiceId: string) {
    this.router.navigate(['/deliveries'], { queryParams: { createForInvoice: invoiceId } });
  }

  protected canCreatePayment(inv: { status: string; balance: number }): boolean {
    return inv.status !== 'DRAFT' && inv.status !== 'CANCELLED'
        && Number(inv.balance) > 0;
  }

  protected goToCreatePayment(invoiceId: string) {
    this.router.navigate(['/payments'], { queryParams: { createForInvoice: invoiceId } });
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

  protected async openDetail(inv: Invoice) {
    this.detail.set(null);
    this.deliveries.set([]);
    this.creditNotes.set([]);
    this.detailOpen.set(true);
    try {
      const full = await firstValueFrom(this.http.get<InvoiceDetail>(`/api/v1/invoices/${inv.id}`));
      this.detail.set(full);
    } catch {
      this.detail.set(null);
    }
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: InvoiceDelivery[] }>(`/api/v1/deliveries?invoiceId=${inv.id}&size=200`)
      );
      this.deliveries.set(res.content ?? []);
    } catch {
      this.deliveries.set([]);
    }
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: InvoiceCreditNote[] }>(`/api/v1/credit-notes?invoiceId=${inv.id}&size=200`)
      );
      this.creditNotes.set(res.content ?? []);
    } catch {
      this.creditNotes.set([]);
    }
  }

  protected async openCreateCreditNote(inv: InvoiceDetail) {
    this.creditPreview.set(null);
    this.creditForm = { reason: '' };
    this.creditOpen.set(true);
    try {
      const cp = await firstValueFrom(
        this.http.get<CreditNotePreview>(`/api/v1/invoices/${inv.id}/credit-note-preview`)
      );
      this.creditPreview.set(cp);
    } catch {
      this.creditPreview.set(null);
    }
  }

  protected duplicateInvoice(inv: InvoiceDetail) {
    // "Duplicate" is a data-entry aid, not a server-side copy: open the create
    // dialog pre-filled identically to the source invoice so the user can review
    // and adjust before saving. Nothing is persisted until they hit Save.
    this.form = {
      customerId: inv.customerId,
      issueDate: new Date(inv.issueDate),
      dueDate: new Date(inv.dueDate),
      currency: inv.currency || '',
      paymentTerms: inv.paymentTerms || '',
      notes: inv.notes || '',
      lines: inv.lines.map(l => ({
        productId: l.productId,
        uomId: l.uomId,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        discountPercent: l.discountPercent,
        taxRate: l.taxRate,
      })),
    };
    this.submitted.set(false);
    this.refreshCompensItems();
    this.detailOpen.set(false);
    this.dialogOpen = true;
  }

  protected async saveCreditNote() {
    const cp = this.creditPreview();
    if (!cp || cp.blockReason) return;
    this.savingCredit.set(true);
    try {
      const payload = { reason: this.creditForm.reason || null };
      await firstValueFrom(
        this.http.post(`/api/v1/invoices/${cp.invoiceId}/credit-notes`, payload)
      );
      this.creditOpen.set(false);
      this.detailOpen.set(false);
      this.reload();
    } finally {
      this.savingCredit.set(false);
    }
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.compensItems.set([]);
    this.dialogOpen = true;
  }

  protected onCustomerChange() {
    const c = this.customers().find(x => x.id === this.form.customerId);
    if (c && !this.form.currency) this.form.currency = c.currency;
    this.refreshCompensItems();
  }

  private async refreshCompensItems() {
    if (!this.form.customerId) {
      this.compensItems.set([]);
      return;
    }
    try {
      const items = await firstValueFrom(
        this.http.get<{ sourceType: string; sourceId: string; sign: string; amountOpen: number; label: string }[]>(
          `/api/v1/allocations/open-items?partyId=${this.form.customerId}`)
      );
      // A sale invoice is POSITIVE, so it nets against the party's NEGATIVE items:
      // credits and — when the party is also a supplier — our purchase invoices.
      const negatives = (items ?? [])
        .filter(i => i.sign === 'NEGATIVE'
          && (i.sourceType === 'CUSTOMER_CREDIT' || i.sourceType === 'PURCHASE_INVOICE'))
        .map(i => ({
          sourceType: i.sourceType,
          sourceId: i.sourceId,
          label: i.label,
          amountOpen: Number(i.amountOpen),
          // Pre-check credits (prior auto-apply UX); leave purchase invoices opt-in.
          selected: i.sourceType === 'CUSTOMER_CREDIT',
          amount: Number(i.amountOpen),
        }));
      this.compensItems.set(negatives);
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
    this.form.lines.push({
      productId: null, uomId: null,
      quantity: 1, unitPrice: 0, discountPercent: 0, taxRate: 0,
    });
  }

  protected removeLine(i: number) { this.form.lines.splice(i, 1); }

  protected async onProductChange(line: LineForm) {
    const p = this.products().find(x => x.id === line.productId);
    if (!p) return;
    line.uomId = p.baseUomId;
    line.taxRate = p.defaultTaxRate ?? 0;
    await this.refreshLinePrice(line);
  }

  private async refreshLinePrice(line: LineForm) {
    if (!line.productId || !line.uomId) return;
    const params = new URLSearchParams({
      productId: line.productId,
      uomId: line.uomId,
      quantity: String(line.quantity || 1),
    });
    const tierId = this.customers().find(c => c.id === this.form.customerId)?.defaultPriceTierId;
    if (tierId) params.set('priceTierId', tierId);
    try {
      const res = await firstValueFrom(
        this.http.get<{ unitPrice: number }>(`/api/v1/pricing/resolve?${params}`)
      );
      if (res?.unitPrice != null) line.unitPrice = res.unitPrice;
    } catch {
      /* no tier configured — leave manual */
    }
  }

  protected lineTotal(line: LineForm): number {
    const qty = line.quantity || 0;
    const price = line.unitPrice || 0;
    const disc = line.discountPercent || 0;
    return +(qty * price * (1 - disc / 100)).toFixed(2);
  }

  protected grandTotal(): number {
    return this.form.lines.reduce((s, l) => s + this.lineTotal(l), 0);
  }

  protected canSave(): boolean {
    return !!this.form.customerId
        && this.form.lines.length > 0
        && this.form.lines.every(l => !!l.productId && (l.quantity || 0) > 0);
  }

  protected save() {
    this.submitted.set(true);
    if (!this.canSave()) return;
    this.pendingInvoicePayload = {
      customerId: this.form.customerId,
      quoteId: null,
      issueDate: this.toIsoDate(this.form.issueDate),
      dueDate: this.toIsoDate(this.form.dueDate),
      paymentTerms: this.form.paymentTerms || null,
      currency: this.form.currency || null,
      notes: this.form.notes || null,
      lines: this.form.lines.map(l => ({
        variantId: this.variantIdFor(l.productId),
        uomId: l.uomId,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        discountPercent: l.discountPercent,
      })),
    };
    // Default delivery mode: immediate. Preselect the default warehouse when
    // there are multiple warehouses so the dropdown is never empty.
    this.deliveryMode = 'immediate';
    const ws = this.warehouses();
    this.deliveryWarehouseId = ws.length > 0
        ? (ws.find(w => w.defaultWarehouse) ?? ws[0]).id
        : null;
    this.deliveryDialogOpen.set(true);
  }

  protected async confirmDeliveryChoice() {
    if (!this.pendingInvoicePayload) return;
    this.saving.set(true);
    try {
      let created: { id: string };
      if (this.deliveryMode === 'immediate') {
        if (!this.deliveryWarehouseId) return;
        created = await firstValueFrom(this.http.post<{ id: string }>(
          '/api/v1/invoices/with-immediate-delivery', {
            invoice: this.pendingInvoicePayload,
            warehouseId: this.deliveryWarehouseId,
          }));
      } else {
        created = await firstValueFrom(this.http.post<{ id: string }>(
          '/api/v1/invoices', this.pendingInvoicePayload));
      }
      if (created?.id && this.compensTotal() > 0) {
        await this.applyCompensationsToCreatedInvoice(created.id);
      }
      this.deliveryDialogOpen.set(false);
      this.dialogOpen = false;
      this.pendingInvoicePayload = null;
      this.compensItems.set([]);
      this.reload();
    } catch (e) {
      // Immediate delivery rolls back atomically when stock is short (or any
      // other reason). Surface why instead of failing silently; the dialog
      // stays open so the user can switch to deferred delivery or fix stock.
      this.showError(e);
    } finally {
      this.saving.set(false);
    }
  }

  private showError(err: unknown) {
    let detail = this.translate.instant('common.error_generic');
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (body?.code) {
        const translated = this.translate.instant(body.code, body.details ?? {});
        detail = translated !== body.code ? translated : (body.message ?? body.code);
      } else if (body?.message) {
        detail = body.message;
      }
    }
    this.messages.add({
      severity: 'error',
      summary: this.translate.instant('common.error'),
      detail,
      life: 5000,
    });
  }

  private async applyCompensationsToCreatedInvoice(invoiceId: string) {
    // The new sale invoice is the POSITIVE side; each selected item (credit or
    // purchase invoice) is NEGATIVE. The engine caps every call to
    // min(invoice.balance, item.open, amount), so once the invoice is fully
    // covered later rows net to 0 — sequential best-effort is safe.
    for (const item of this.compensItems()) {
      if (!item.selected || (item.amount || 0) <= 0) continue;
      try {
        await firstValueFrom(this.http.post(
          `/api/v1/allocations/apply?partyId=${this.form.customerId}`, {
            positiveType: 'INVOICE',
            positiveId: invoiceId,
            negativeType: item.sourceType,
            negativeId: item.sourceId,
            amount: item.amount,
          }));
      } catch {
        // Best-effort: a single failure shouldn't block the invoice from
        // existing. The user can compensate the rest manually later.
      }
    }
  }

  protected cancelDeliveryDialog() {
    this.deliveryDialogOpen.set(false);
    this.pendingInvoicePayload = null;
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
        this.http.get<{ content: Invoice[]; totalElements: number }>(
          `/api/v1/invoices?page=${page}&size=${rows}`
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

  private async loadCustomers() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: CustomerOpt[] }>('/api/v1/partners?role=CUSTOMER&size=200')
      );
      this.customers.set(res.content ?? []);
    } catch {
      this.customers.set([]);
    }
  }

  private async loadProducts() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: ProductOpt[] }>('/api/v1/products?size=500')
      );
      this.products.set((res.content ?? []).filter((p: ProductOpt & { active?: boolean; sellable?: boolean }) => p.active !== false && p.sellable !== false));
    } catch {
      this.products.set([]);
    }
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
    const today = new Date();
    const due = new Date(today);
    due.setDate(today.getDate() + 30);
    return {
      customerId: null as string | null,
      issueDate: today,
      dueDate: due,
      currency: '',
      paymentTerms: '',
      notes: '',
      lines: [] as LineForm[],
    };
  }
}
