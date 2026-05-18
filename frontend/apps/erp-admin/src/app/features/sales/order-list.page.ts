import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { CalendarModule } from 'primeng/calendar';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

interface Order {
  id: string;
  number: string;
  customerName: string;
  orderDate: string;
  status: string;
  currency: string;
  total: number;
  deliveryRequired: boolean;
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
  selector: 'erp-admin-order-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule, ButtonModule,
    DialogModule, DropdownModule, CalendarModule, InputTextModule, InputNumberModule,
    CheckboxModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'orders.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'orders.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'orders.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="orders()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'sales.orderDate' | translate }}</th>
              <th>{{ 'sales.delivery' | translate }}</th>
              <th class="text-right">{{ 'sales.total' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-o>
            <tr>
              <td><span class="font-mono text-sm">{{ o.number }}</span></td>
              <td>{{ o.customerName }}</td>
              <td>{{ o.orderDate | date:'mediumDate' }}</td>
              <td>
                <i [class]="o.deliveryRequired ? 'pi pi-check text-green-500' : 'pi pi-minus text-gray-300'"></i>
              </td>
              <td class="text-right font-medium">{{ o.total | number:'1.2-2' }} {{ o.currency }}</td>
              <td><p-tag [value]="'sales.statuses.' + o.status | translate" [severity]="statusSeverity(o.status)" /></td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-print" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/orders/' + o.id + '/pdf')"></button>
                <p-dropdown [ngModel]="o.status" [options]="nextStatuses(o.status)"
                            optionLabel="label" optionValue="value"
                            [placeholder]="'common.action' | translate"
                            [showClear]="false" [disabled]="nextStatuses(o.status).length === 0"
                            (onChange)="onStatusChange(o, $event.value)"
                            styleClass="text-xs" appendTo="body" />
                <button pButton icon="pi pi-receipt" class="p-button-sm p-button-text ml-1"
                        [pTooltip]="'orders.convertToInvoice' | translate"
                        [disabled]="!canInvoice(o.status)"
                        (click)="openInvoiceDialog(o)"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">{{ 'orders.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Create order dialog -->
      <p-dialog [(visible)]="createOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="'orders.createTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-3 gap-3">
            <div class="col-span-1">
              <label class="block text-sm font-medium mb-1">{{ 'sales.customer' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.customerId" [options]="customers()"
                          optionLabel="name" optionValue="id"
                          [filter]="true" filterBy="name,code"
                          (onChange)="onCustomerChange()" styleClass="w-full" appendTo="body" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'sales.orderDate' | translate }} *</label>
              <p-calendar [(ngModel)]="form.orderDate" dateFormat="dd/mm/yy"
                          styleClass="w-full" appendTo="body" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'customers.currency' | translate }}</label>
              <input pInputText [(ngModel)]="form.currency" class="w-full" />
            </div>
          </div>
          <div class="flex items-center gap-2">
            <p-checkbox [(ngModel)]="form.deliveryRequired" [binary]="true" inputId="deliveryRequired" />
            <label for="deliveryRequired" class="text-sm">{{ 'orders.deliveryRequired' | translate }}</label>
          </div>
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
                                  (onChange)="onProductChange(line)"
                                  styleClass="w-full" appendTo="body" />
                    </td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="line.quantity" [minFractionDigits]="0" [maxFractionDigits]="3"
                                     inputStyleClass="w-full text-right" styleClass="w-full" />
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
                    <td class="p-2 text-right">{{ lineTotal(line) | number:'1.2-2' }}</td>
                    <td class="p-1 text-center">
                      <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                              (click)="removeLine(i)"></button>
                    </td>
                  </tr>
                }
                @if (form.lines.length === 0) {
                  <tr><td colspan="6" class="p-4 text-center text-gray-400">{{ 'sales.noLines' | translate }}</td></tr>
                }
              </tbody>
              <tfoot class="bg-gray-50 border-t">
                <tr>
                  <td colspan="4" class="p-2 text-right font-medium">{{ 'sales.total' | translate }}</td>
                  <td class="p-2 text-right font-bold">{{ grandTotal() | number:'1.2-2' }} {{ form.currency }}</td>
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
                  (click)="save()" [loading]="saving()" [disabled]="!canSave()"></button>
        </ng-template>
      </p-dialog>

      <!-- Convert-to-invoice dialog -->
      <p-dialog [(visible)]="invoiceDialogOpen" [modal]="true" [style]="{ width: '450px' }"
                [header]="'orders.convertToInvoice' | translate" [closable]="!converting()">
        <div class="space-y-3">
          <p class="text-sm text-gray-600">
            {{ 'orders.invoiceFromOrderHint' | translate }}
            <span class="font-mono ml-1">{{ pendingOrder()?.number }}</span>
          </p>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'invoices.dueDate' | translate }} *</label>
            <p-calendar [(ngModel)]="dueDate" dateFormat="dd/mm/yy" styleClass="w-full" appendTo="body" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'invoices.paymentTerms' | translate }}</label>
            <input pInputText [(ngModel)]="paymentTerms" class="w-full"
                   [placeholder]="'invoices.paymentTermsHint' | translate" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="invoiceDialogOpen = false" [disabled]="converting()"></button>
          <button pButton [label]="'common.confirm' | translate" icon="pi pi-check"
                  (click)="confirmInvoice()" [loading]="converting()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class OrderListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);

  protected orders = signal<Order[]>([]);
  protected customers = signal<CustomerOpt[]>([]);
  protected products = signal<ProductOpt[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected converting = signal(false);
  protected createOpen = false;
  protected invoiceDialogOpen = false;
  protected pendingOrder = signal<Order | null>(null);
  protected dueDate: Date = new Date(new Date().setDate(new Date().getDate() + 30));
  protected paymentTerms = '';

  protected form: {
    customerId: string | null;
    orderDate: Date;
    deliveryRequired: boolean;
    currency: string;
    notes: string;
    lines: LineForm[];
  } = this.emptyForm();

  private static readonly TRANSITIONS: Record<string, string[]> = {
    DRAFT: ['CONFIRMED', 'CANCELLED'],
    CONFIRMED: ['PARTIALLY_DELIVERED', 'DELIVERED', 'CANCELLED'],
    PARTIALLY_DELIVERED: ['DELIVERED', 'CANCELLED'],
    DELIVERED: [],
    PARTIALLY_INVOICED: ['INVOICED'],
    INVOICED: [],
    CANCELLED: [],
  };

  ngOnInit() {
    this.load();
    this.loadCustomers();
    this.loadProducts();
  }

  protected statusSeverity(status: string): Severity {
    return ({
      DRAFT: 'secondary', CONFIRMED: 'info',
      PARTIALLY_DELIVERED: 'warning', DELIVERED: 'success',
      PARTIALLY_INVOICED: 'warning', INVOICED: 'success', CANCELLED: 'secondary',
    } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected nextStatuses(current: string): Array<{ value: string; label: string }> {
    return (OrderListPage.TRANSITIONS[current] ?? []).map(v => ({
      value: v, label: this.i18n.instant('sales.statuses.' + v),
    }));
  }

  protected canInvoice(status: string): boolean {
    return status === 'CONFIRMED' || status === 'DELIVERED' || status === 'PARTIALLY_DELIVERED';
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

  protected onStatusChange(o: Order, status: string) {
    if (!status || status === o.status) return;
    this.confirmation.confirm({
      message: this.i18n.instant('sales.confirmStatusChange', {
        number: o.number,
        status: this.i18n.instant('sales.statuses.' + status),
      }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      accept: async () => {
        try {
          await firstValueFrom(this.http.patch(`/api/v1/orders/${o.id}/status?status=${status}`, {}));
        } finally {
          this.load();
        }
      },
      reject: () => this.load(),
    });
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.createOpen = true;
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

  protected async save() {
    if (!this.canSave()) return;
    this.saving.set(true);
    try {
      const payload = {
        customerId: this.form.customerId,
        quoteId: null,
        orderDate: this.toIsoDate(this.form.orderDate),
        deliveryRequired: this.form.deliveryRequired,
        currency: this.form.currency || null,
        notes: this.form.notes || null,
        lines: this.form.lines.map(l => ({
          productId: l.productId,
          uomId: l.uomId,
          quantity: l.quantity,
          unitPrice: l.unitPrice,
          discountPercent: l.discountPercent,
        })),
      };
      await firstValueFrom(this.http.post('/api/v1/orders', payload));
      this.createOpen = false;
      this.load();
    } finally {
      this.saving.set(false);
    }
  }

  protected openInvoiceDialog(o: Order) {
    this.pendingOrder.set(o);
    this.dueDate = new Date(new Date().setDate(new Date().getDate() + 30));
    this.paymentTerms = '';
    this.invoiceDialogOpen = true;
  }

  protected async confirmInvoice() {
    const o = this.pendingOrder();
    if (!o) return;
    this.converting.set(true);
    try {
      const params = new URLSearchParams();
      params.set('dueDate', this.toIsoDate(this.dueDate));
      if (this.paymentTerms) params.set('paymentTerms', this.paymentTerms);
      await firstValueFrom(this.http.post(`/api/v1/orders/${o.id}/convert-to-invoice?${params}`, {}));
      this.invoiceDialogOpen = false;
      this.load();
    } finally {
      this.converting.set(false);
    }
  }

  private toIsoDate(d: Date): string {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  private async load() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(this.http.get<{ content: Order[] }>('/api/v1/orders'));
      this.orders.set(res.content ?? []);
    } catch {
      this.orders.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadCustomers() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: CustomerOpt[] }>('/api/v1/customers?size=200')
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
  }

  private emptyForm() {
    return {
      customerId: null as string | null,
      orderDate: new Date(),
      deliveryRequired: true,
      currency: '',
      notes: '',
      lines: [] as LineForm[],
    };
  }
}
