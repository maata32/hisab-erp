import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '@minierp/shared-i18n';
import { HttpClient } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CalendarModule } from 'primeng/calendar';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

interface Quote {
  id: string;
  number: string;
  customerId: string;
  customerName: string;
  issueDate: string;
  validUntil: string;
  status: string;
  currency: string;
  total: number;
  linkedInvoiceId: string | null;
  linkedInvoiceNumber: string | null;
  linkedInvoiceStatus: string | null;
}

interface CustomerOpt {
  id: string;
  code: string;
  name: string;
  currency: string;
  defaultPriceTierId: string | null;
}
interface ProductOpt {
  id: string;
  sku: string;
  name: string;
  baseUomId: string;
  defaultTaxRate: number;
}

interface ProductStockBreakdown {
  productId: string;
  warehouses: { warehouseId: string; warehouseCode: string; warehouseName: string; isDefault: boolean; qtyAvailable: number }[];
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
  selector: 'erp-admin-quote-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule, ButtonModule,
    DialogModule, DropdownModule, InputTextModule, InputNumberModule, CalendarModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'quotes.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'quotes.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'quotes.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="quotes()" [loading]="loading()" stripedRows
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
              <th>{{ 'sales.validUntil' | translate }}</th>
              <th class="text-right">{{ 'sales.total' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-q>
            <tr>
              <td><span class="font-mono text-sm">{{ q.number }}</span></td>
              <td>{{ q.customerName }}</td>
              <td>{{ q.issueDate | date:'mediumDate' }}</td>
              <td>{{ q.validUntil | date:'mediumDate' }}</td>
              <td class="text-right font-medium">{{ q.total | money }} {{ q.currency }}</td>
              <td>
                <p-tag [value]="'sales.statuses.' + q.status | translate" [severity]="statusSeverity(q.status)" />
                @if (q.linkedInvoiceNumber) {
                  <div class="text-xs text-gray-500 mt-1 flex items-center gap-1">
                    <span>&rarr;</span>
                    <span class="font-mono">{{ q.linkedInvoiceNumber }}</span>
                    <p-tag [value]="'sales.statuses.' + q.linkedInvoiceStatus | translate"
                           [severity]="invoiceStatusSeverity(q.linkedInvoiceStatus!)"
                           styleClass="text-[10px] py-0 px-1" />
                  </div>
                }
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-print" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/quotes/' + q.id + '/pdf')"></button>
                <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'common.edit' | translate"
                        [disabled]="!canEdit(q.status)"
                        (click)="openEdit(q)"></button>
                <p-dropdown [ngModel]="q.status" [options]="nextStatuses(q.status)"
                            optionLabel="label" optionValue="value"
                            [placeholder]="'common.action' | translate"
                            [showClear]="false" [disabled]="nextStatuses(q.status).length === 0"
                            (onChange)="onStatusChange(q, $event.value)"
                            styleClass="text-xs" appendTo="body" />
                <button pButton icon="pi pi-receipt" class="p-button-sm p-button-text ml-1"
                        [pTooltip]="'quotes.convertToInvoice' | translate"
                        [disabled]="!canConvert(q.status)"
                        (click)="convertToInvoice(q)"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">{{ 'quotes.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="(editingQuoteId() ? 'quotes.editTitle' : 'quotes.createTitle') | translate:{ number: editingNumber() }"
                [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-3 gap-3">
            <div class="col-span-1">
              <label class="block text-sm font-medium mb-1">{{ 'sales.customer' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.customerId" [options]="customers()"
                          optionLabel="name" optionValue="id"
                          [filter]="true" filterBy="name,code"
                          (onChange)="onCustomerChange()" appendTo="body"
                          [disabled]="!!editingQuoteId()"
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
              <label class="block text-sm font-medium mb-1">{{ 'sales.validUntil' | translate }}</label>
              <p-calendar [(ngModel)]="form.validUntil" dateFormat="dd/mm/yy"
                          styleClass="w-full" appendTo="body" />
            </div>
          </div>
          <div class="grid grid-cols-3 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.currency' | translate }}</label>
              <input pInputText [(ngModel)]="form.currency" class="w-full" />
            </div>
            <div class="col-span-2">
              <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
              <input pInputText [(ngModel)]="form.notes" class="w-full" />
            </div>
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
    </div>
  `,
})
export class QuoteListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);

  protected quotes = signal<Quote[]>([]);
  protected customers = signal<CustomerOpt[]>([]);
  protected products = signal<ProductOpt[]>([]);
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
  protected editingQuoteId = signal<string | null>(null);
  protected editingNumber = signal<string>('');
  protected form: { customerId: string | null; issueDate: Date; validUntil: Date; currency: string; notes: string; lines: LineForm[] } = this.emptyForm();

  protected customerInvalid(): boolean { return this.submitted() && !this.form.customerId; }
  protected issueDateInvalid(): boolean { return this.submitted() && !this.form.issueDate; }
  protected noLinesInvalid(): boolean { return this.submitted() && this.form.lines.length === 0; }
  protected lineProductInvalid(line: LineForm): boolean { return this.submitted() && !line.productId; }
  protected lineQtyInvalid(line: LineForm): boolean {
    return this.submitted() && (line.quantity == null || line.quantity <= 0);
  }

  private static readonly TRANSITIONS: Record<string, string[]> = {
    DRAFT: ['SENT', 'CANCELLED'],
    SENT: ['ACCEPTED', 'REJECTED', 'EXPIRED'],
    ACCEPTED: ['CANCELLED'],
    REJECTED: [],
    EXPIRED: [],
    CONVERTED: [],
    CANCELLED: [],
  };

  ngOnInit() {
    // Quotes are loaded on demand by the p-table's lazy load (onLazyLoad).
    this.loadCustomers();
    this.loadProducts();
  }

  protected statusSeverity(status: string): Severity {
    return ({ DRAFT: 'secondary', SENT: 'info', ACCEPTED: 'success', REJECTED: 'danger', EXPIRED: 'warning', CONVERTED: 'success', CANCELLED: 'secondary' } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected invoiceStatusSeverity(status: string): Severity {
    return ({
      DRAFT: 'secondary', ISSUED: 'info', PARTIAL: 'warning',
      PAID: 'success', OVERDUE: 'danger', CANCELLED: 'secondary',
    } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected nextStatuses(current: string): Array<{ value: string; label: string }> {
    return (QuoteListPage.TRANSITIONS[current] ?? []).map(v => ({
      value: v, label: this.i18n.instant('sales.statuses.' + v),
    }));
  }

  protected canConvert(status: string): boolean {
    return status === 'DRAFT' || status === 'SENT' || status === 'ACCEPTED';
  }

  protected canEdit(status: string): boolean {
    return status !== 'CONVERTED' && status !== 'REJECTED' && status !== 'CANCELLED';
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

  protected onStatusChange(q: Quote, status: string) {
    if (!status || status === q.status) return;
    this.confirmation.confirm({
      message: this.i18n.instant('sales.confirmStatusChange', {
        number: q.number,
        status: this.i18n.instant('sales.statuses.' + status),
      }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      accept: async () => {
        try {
          await firstValueFrom(this.http.patch(`/api/v1/quotes/${q.id}/status?status=${status}`, {}));
        } finally {
          this.reload();
        }
      },
      reject: () => this.reload(),
    });
  }

  protected convertToInvoice(q: Quote) {
    this.confirmation.confirm({
      message: this.i18n.instant('quotes.confirmConvert', { number: q.number }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-arrow-right',
      accept: async () => {
        try {
          const due = new Date();
          due.setDate(due.getDate() + 30);
          const dueIso = `${due.getFullYear()}-${String(due.getMonth() + 1).padStart(2, '0')}-${String(due.getDate()).padStart(2, '0')}`;
          await firstValueFrom(this.http.post(
              `/api/v1/quotes/${q.id}/convert-to-invoice`,
              { dueDate: dueIso, paymentTerms: null }));
          this.reload();
        } catch {
          /* error reported by global handler */
        }
      },
    });
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.editingQuoteId.set(null);
    this.editingNumber.set('');
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected async openEdit(q: Quote) {
    try {
      const full = await firstValueFrom(this.http.get<any>(`/api/v1/quotes/${q.id}`));
      this.form = {
        customerId: full.customerId,
        issueDate: full.issueDate ? new Date(full.issueDate) : new Date(),
        validUntil: full.validUntil ? new Date(full.validUntil) : new Date(),
        currency: full.currency ?? '',
        notes: full.notes ?? '',
        lines: (full.lines ?? []).map((l: any) => ({
          productId: l.productId,
          uomId: l.uomId,
          quantity: Number(l.quantity),
          unitPrice: Number(l.unitPrice),
          discountPercent: Number(l.discountPercent),
          taxRate: Number(l.taxRate),
        })),
      };
      this.editingQuoteId.set(q.id);
      this.editingNumber.set(q.number);
      this.submitted.set(false);
      this.dialogOpen = true;
    } catch { /* error reported by global handler */ }
  }

  protected onCustomerChange() {
    const c = this.customers().find(x => x.id === this.form.customerId);
    if (c && !this.form.currency) this.form.currency = c.currency;
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
      // no price configured — leave whatever the user typed (default 0)
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

  protected async save() {
    this.submitted.set(true);
    if (!this.canSave()) return;
    this.saving.set(true);
    try {
      const editingId = this.editingQuoteId();
      const lines = this.form.lines.map(l => ({
        productId: l.productId,
        uomId: l.uomId,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        discountPercent: l.discountPercent,
      }));
      if (editingId) {
        await firstValueFrom(this.http.put(`/api/v1/quotes/${editingId}`, {
          issueDate: this.toIsoDate(this.form.issueDate),
          validUntil: this.toIsoDate(this.form.validUntil),
          currency: this.form.currency || null,
          notes: this.form.notes || null,
          lines,
        }));
      } else {
        await firstValueFrom(this.http.post('/api/v1/quotes', {
          customerId: this.form.customerId,
          issueDate: this.toIsoDate(this.form.issueDate),
          validUntil: this.toIsoDate(this.form.validUntil),
          currency: this.form.currency || null,
          notes: this.form.notes || null,
          lines,
        }));
      }
      this.dialogOpen = false;
      this.reload();
    } finally {
      this.saving.set(false);
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
        this.http.get<{ content: Quote[]; totalElements: number }>(
          `/api/v1/quotes?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.quotes()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.quotes.set(arr);
      this.total.set(totalElements);
    } catch {
      this.quotes.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  /** Reset the list so the next onLazyLoad refetches from offset 0. */
  protected reload() {
    this.quotes.set([]);
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
      this.products.set((res.content ?? []).filter((p: any) => p.active !== false && p.sellable !== false));
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
    const validUntil = new Date(today);
    validUntil.setDate(today.getDate() + 30);
    return {
      customerId: null as string | null,
      issueDate: today,
      validUntil,
      currency: '',
      notes: '',
      lines: [] as LineForm[],
    };
  }
}
