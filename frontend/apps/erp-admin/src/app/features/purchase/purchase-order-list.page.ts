import { Component, OnInit, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '@hisaberp/shared-i18n';
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

interface PurchaseOrder {
  id: string;
  number: string;
  supplierId: string;
  supplierName: string;
  warehouseId: string | null;
  orderDate: string;
  expectedDate: string | null;
  status: string;
  currency: string;
  subtotal: number;
  taxAmount: number;
  total: number;
  notes: string | null;
  convertedToInvoiceId: string | null;
  lines: PurchaseOrderLine[];
}

interface PurchaseOrderLine {
  id: string;
  lineNumber: number;
  productId: string;
  uomId: string;
  quantity: number;
  unitCost: number;
  taxRate: number;
  lineTotal: number;
  productName: string;
  sku: string;
}

interface SupplierOpt { id: string; code: string; name: string; currency: string; }
interface WarehouseOpt { id: string; code: string; name: string; active: boolean; }
interface ProductVariantOpt { id: string; defaultVariant: boolean; active: boolean; }
interface ProductOpt { id: string; sku: string; name: string; baseUomId: string; defaultTaxRate: number; trackExpiry: boolean; variants: ProductVariantOpt[]; }
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
  trackExpiry: boolean;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-purchase-order-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule, ButtonModule,
    DialogModule, DropdownModule, CalendarModule, InputTextModule, InputNumberModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'purchaseOrders.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'purchaseOrders.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'purchaseOrders.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="orders()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'purchaseOrders.number' | translate }}</th>
              <th>{{ 'purchaseOrders.supplier' | translate }}</th>
              <th>{{ 'purchaseOrders.orderDate' | translate }}</th>
              <th>{{ 'purchaseOrders.expectedDate' | translate }}</th>
              <th class="text-right">{{ 'purchaseOrders.total' | translate }}</th>
              <th>{{ 'purchaseOrders.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-o>
            <tr>
              <td><span class="font-mono text-sm">{{ o.number }}</span></td>
              <td>{{ o.supplierName }}</td>
              <td>{{ o.orderDate | date:'mediumDate' }}</td>
              <td>{{ o.expectedDate ? (o.expectedDate | date:'mediumDate') : '—' }}</td>
              <td class="text-right font-medium">{{ o.total | money }} {{ o.currency }}</td>
              <td><p-tag [value]="'purchaseOrders.statuses.' + o.status | translate" [severity]="statusSeverity(o.status)" /></td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-print" class="p-button-sm p-button-text"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/purchase-orders/' + o.id + '/pdf')"></button>
                @if (o.status === 'DRAFT') {
                  <button pButton icon="pi pi-check" class="p-button-sm p-button-text p-button-info"
                          [pTooltip]="'purchaseOrders.confirm' | translate"
                          (click)="confirmOrder(o)"></button>
                }
                @if (canConvert(o.status)) {
                  <button pButton icon="pi pi-file-import" class="p-button-sm p-button-text p-button-success"
                          [pTooltip]="'purchaseOrders.convert' | translate"
                          (click)="openConvert(o)"></button>
                }
                @if (o.status === 'CONVERTED' && o.convertedToInvoiceId) {
                  <button pButton icon="pi pi-arrow-right" class="p-button-sm p-button-text"
                          [pTooltip]="'purchaseOrders.openInvoice' | translate"
                          (click)="goToInvoices()"></button>
                }
                @if (canCancel(o.status)) {
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'purchaseOrders.cancel' | translate"
                          (click)="cancelOrder(o)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">{{ 'purchaseOrders.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Create dialog -->
      <p-dialog [(visible)]="createOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="'purchaseOrders.createTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-3 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'purchaseOrders.supplier' | translate }} *</label>
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
              <label class="block text-sm font-medium mb-1">{{ 'purchaseOrders.warehouse' | translate }}</label>
              <p-dropdown [(ngModel)]="form.warehouseId" [options]="warehouses()"
                          optionLabel="name" optionValue="id" [showClear]="true" appendTo="body"
                          styleClass="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'purchaseOrders.orderDate' | translate }} *</label>
              <p-calendar [(ngModel)]="form.orderDate" dateFormat="dd/mm/yy" appendTo="body"
                          [styleClass]="'w-full' + (orderDateInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (orderDateInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'purchaseOrders.expectedDate' | translate }}</label>
              <p-calendar [(ngModel)]="form.expectedDate" dateFormat="dd/mm/yy"
                          styleClass="w-full" appendTo="body" />
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
                                  [attr.aria-label]="'sales.product' | translate"
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
                                     [attr.aria-label]="'sales.quantity' | translate"
                                     inputStyleClass="w-full text-right"
                                     [styleClass]="'w-full' + (lineQtyInvalid(line) ? ' ng-invalid ng-dirty' : '')" />
                    </td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="line.unitCost" [minFractionDigits]="0" [maxFractionDigits]="2"
                                     [attr.aria-label]="'purchaseOrders.unitCost' | translate"
                                     inputStyleClass="w-full text-right"
                                     [styleClass]="'w-full' + (lineCostInvalid(line) ? ' ng-invalid ng-dirty' : '')" />
                    </td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="line.taxRate" [min]="0" [max]="1"
                                     [attr.aria-label]="'sales.tax' | translate"
                                     [minFractionDigits]="0" [maxFractionDigits]="4"
                                     inputStyleClass="w-full text-right" styleClass="w-full" />
                    </td>
                    <td class="p-2 text-right">{{ lineTotal(line) | money }}</td>
                    <td class="p-1 text-center">
                      <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                              [attr.aria-label]="'common.delete' | translate"
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

      <!-- Convert dialog -->
      <p-dialog [(visible)]="convertOpen" [modal]="true" [style]="{ width: '480px' }"
                [header]="('purchaseOrders.convert' | translate) + ' — ' + (convertOrderRef()?.number ?? '')"
                [closable]="!converting()">
        <p class="text-sm text-gray-600 mb-3">{{ 'purchaseOrders.convertHint' | translate }}</p>
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'purchaseInvoices.supplierReference' | translate }}</label>
            <input pInputText [(ngModel)]="convertForm.supplierReference" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'purchaseInvoices.dueDate' | translate }}</label>
            <p-calendar [(ngModel)]="convertForm.dueDate" dateFormat="dd/mm/yy" styleClass="w-full" appendTo="body" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="convertOpen = false" [disabled]="converting()"></button>
          <button pButton [label]="'purchaseOrders.convertAction' | translate" icon="pi pi-check"
                  (click)="confirmConvert()" [loading]="converting()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class PurchaseOrderListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private router = inject(Router);

  protected orders = signal<PurchaseOrder[]>([]);
  protected suppliers = signal<SupplierOpt[]>([]);
  protected warehouses = signal<WarehouseOpt[]>([]);
  protected products = signal<ProductOpt[]>([]);

  /** Resolve the variant to order for a product: its default (or first active) variant. */
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
  protected createOpen = false;

  protected supplierInvalid(): boolean { return this.submitted() && !this.form.supplierId; }
  protected orderDateInvalid(): boolean { return this.submitted() && !this.form.orderDate; }
  protected noLinesInvalid(): boolean { return this.submitted() && this.form.lines.length === 0; }
  protected lineProductInvalid(line: LineForm): boolean { return this.submitted() && !line.productId; }
  protected lineQtyInvalid(line: LineForm): boolean {
    return this.submitted() && (line.quantity == null || line.quantity <= 0);
  }
  protected lineCostInvalid(line: LineForm): boolean {
    return this.submitted() && (line.unitCost == null || line.unitCost < 0);
  }

  protected convertOpen = false;
  protected converting = signal(false);
  protected convertOrderRef = signal<PurchaseOrder | null>(null);
  protected convertForm: { supplierReference: string; dueDate: Date | null } = { supplierReference: '', dueDate: null };

  protected form: {
    supplierId: string | null;
    warehouseId: string | null;
    orderDate: Date;
    expectedDate: Date | null;
    currency: string;
    notes: string;
    lines: LineForm[];
  } = this.emptyForm();

  ngOnInit() {
    // auto-loaded by p-table lazy
    this.loadSuppliers();
    this.loadWarehouses();
    this.loadProducts();
  }

  protected statusSeverity(status: string): Severity {
    return ({
      DRAFT: 'secondary', CONFIRMED: 'info',
      CONVERTED: 'success', CANCELLED: 'secondary',
    } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected canConvert(status: string): boolean {
    return status === 'DRAFT' || status === 'CONFIRMED';
  }

  protected canCancel(status: string): boolean {
    return status === 'DRAFT' || status === 'CONFIRMED';
  }

  protected goToInvoices() {
    this.router.navigate(['/purchase-invoices']);
  }

  protected async printPdf(url: string) {
    try {
      const blob = await firstValueFrom(this.http.get(url, { responseType: 'blob' }));
      const blobUrl = URL.createObjectURL(blob);
      window.open(blobUrl, '_blank');
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
    } catch (e) { console.error('PDF fetch failed', e); }
  }

  protected confirmOrder(o: PurchaseOrder) {
    this.confirmation.confirm({
      message: this.i18n.instant('purchaseOrders.confirmHint', { number: o.number }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-check',
      accept: async () => {
        await firstValueFrom(this.http.post(`/api/v1/purchase-orders/${o.id}/confirm`, {}));
        this.reload();
      },
    });
  }

  protected cancelOrder(o: PurchaseOrder) {
    this.confirmation.confirm({
      message: this.i18n.instant('purchaseOrders.cancelHint', { number: o.number }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.post(`/api/v1/purchase-orders/${o.id}/cancel`, {}));
        this.reload();
      },
    });
  }

  protected openConvert(o: PurchaseOrder) {
    this.convertOrderRef.set(o);
    this.convertForm = { supplierReference: '', dueDate: null };
    this.convertOpen = true;
  }

  protected async confirmConvert() {
    const o = this.convertOrderRef();
    if (!o) return;
    this.converting.set(true);
    try {
      const payload = {
        supplierReference: this.convertForm.supplierReference || null,
        dueDate: this.convertForm.dueDate ? this.toIsoDate(this.convertForm.dueDate) : null,
      };
      await firstValueFrom(this.http.post(`/api/v1/purchase-orders/${o.id}/convert`, payload));
      this.convertOpen = false;
      this.reload();
      this.router.navigate(['/purchase-invoices']);
    } finally {
      this.converting.set(false);
    }
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.createOpen = true;
    const wDefault = this.warehouses().find(w => (w as { defaultWarehouse?: boolean }).defaultWarehouse) ?? this.warehouses()[0];
    if (wDefault) this.form.warehouseId = wDefault.id;
  }

  protected onSupplierChange() {
    const s = this.suppliers().find(x => x.id === this.form.supplierId);
    if (s && !this.form.currency) this.form.currency = s.currency;
  }

  protected addLine() {
    this.form.lines.push({
      productId: null, uomId: null, quantity: 1, unitCost: 0, taxRate: 0, trackExpiry: false,
    });
  }

  protected removeLine(i: number) { this.form.lines.splice(i, 1); }

  protected onProductChange(line: LineForm) {
    const p = this.products().find(x => x.id === line.productId);
    if (!p) return;
    line.uomId = p.baseUomId;
    line.taxRate = p.defaultTaxRate ?? 0;
    line.trackExpiry = !!p.trackExpiry;
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
        && this.form.lines.every(l => !!l.productId && (l.quantity || 0) > 0 && (l.unitCost || 0) >= 0);
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.canSave()) return;
    this.saving.set(true);
    try {
      const payload = {
        supplierId: this.form.supplierId,
        warehouseId: this.form.warehouseId || null,
        orderDate: this.toIsoDate(this.form.orderDate),
        expectedDate: this.form.expectedDate ? this.toIsoDate(this.form.expectedDate) : null,
        currency: this.form.currency || null,
        notes: this.form.notes || null,
        lines: this.form.lines.map(l => ({
          variantId: this.variantIdFor(l.productId),
          uomId: l.uomId,
          quantity: l.quantity,
          unitCost: l.unitCost,
          taxRate: l.taxRate,
        })),
      };
      await firstValueFrom(this.http.post('/api/v1/purchase-orders', payload));
      this.createOpen = false;
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
        this.http.get<{ content: PurchaseOrder[]; totalElements: number }>(
          `/api/v1/purchase-orders?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.orders()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.orders.set(arr);
      this.total.set(totalElements);
    } catch {
      this.orders.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.orders.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadSuppliers() {
    try {
      const res = await firstValueFrom(this.http.get<{ content: SupplierOpt[] }>('/api/v1/partners?role=SUPPLIER&size=200'));
      this.suppliers.set(res.content ?? []);
    } catch { this.suppliers.set([]); }
  }

  private async loadWarehouses() {
    try {
      const res = await firstValueFrom(this.http.get<WarehouseOpt[]>('/api/v1/inventory/warehouses'));
      this.warehouses.set((res ?? []).filter(w => w.active !== false));
    } catch { this.warehouses.set([]); }
  }

  private async loadProducts() {
    try {
      const res = await firstValueFrom(this.http.get<{ content: ProductOpt[] }>('/api/v1/products?size=500'));
      this.products.set((res.content ?? []).filter(p => (p as { active?: boolean }).active !== false));
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
      warehouseId: null as string | null,
      orderDate: new Date(),
      expectedDate: null as Date | null,
      currency: '',
      notes: '',
      lines: [] as LineForm[],
    };
  }
}
