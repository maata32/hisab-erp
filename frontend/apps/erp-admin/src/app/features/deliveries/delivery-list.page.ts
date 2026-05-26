import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CalendarModule } from 'primeng/calendar';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface DeliveryLine {
  id: string;
  productId: string;
  uomId: string;
  quantityOrdered: number;
  quantityDelivered: number;
  status: string;
  productName: string;
  sku: string;
}

interface Delivery {
  id: string;
  number: string;
  customerId: string;
  customerName: string;
  orderId: string;
  status: string;
  scheduledDate: string;
  deliveredAt: string;
  address: string;
  contactPhone: string;
  signedBy: string;
  notes: string;
  lines: DeliveryLine[];
}

interface WarehouseLite { id: string; code: string; name: string; defaultWarehouse: boolean; }

interface OrderLite {
  id: string;
  number: string;
  customerId: string;
  customerName: string;
  status: string;
  deliveryRequired: boolean;
  lines: { id: string; productId: string; uomId: string; quantity: number; productName: string; sku: string }[];
}

interface DeliveryLineForm {
  productId: string;
  uomId: string;
  productName: string;
  sku: string;
  quantityOrdered: number;
}

interface RecordLineForm {
  lineId: string;
  productName: string;
  sku: string;
  quantityPlanned: number;
  quantityAlreadyDelivered: number;
  quantityDelivered: number;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-delivery-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule, ButtonModule,
    DialogModule, DropdownModule, InputTextModule, InputNumberModule, CalendarModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'deliveries.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'deliveries.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'deliveries.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="deliveries()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'deliveries.scheduledDate' | translate }}</th>
              <th>{{ 'deliveries.deliveredAt' | translate }}</th>
              <th>{{ 'deliveries.progress' | translate }}</th>
              <th>{{ 'deliveries.address' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-d>
            <tr>
              <td><span class="font-mono text-sm">{{ d.number }}</span></td>
              <td>{{ d.customerName }}</td>
              <td>{{ d.scheduledDate | date:'mediumDate' }}</td>
              <td>{{ d.deliveredAt | date:'medium' }}</td>
              <td>
                <span class="text-sm text-gray-600">
                  {{ totalDelivered(d) }}/{{ totalOrdered(d) }}
                </span>
              </td>
              <td class="max-w-xs truncate text-sm text-gray-600">{{ d.address }}</td>
              <td><p-tag [value]="'deliveries.statuses.' + d.status | translate" [severity]="statusSeverity(d.status)" /></td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/deliveries/' + d.id + '/pdf')"></button>
                @if (d.status === 'PENDING') {
                  <button pButton icon="pi pi-play" class="p-button-sm p-button-text"
                          [pTooltip]="'deliveries.start' | translate"
                          (click)="start(d)"></button>
                }
                @if (d.status === 'IN_PROGRESS' || d.status === 'PENDING') {
                  <button pButton icon="pi pi-check-circle" class="p-button-sm p-button-text p-button-success"
                          [pTooltip]="'deliveries.record' | translate"
                          (click)="openRecord(d)"></button>
                }
                @if (d.status !== 'DELIVERED' && d.status !== 'CANCELLED') {
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.cancel' | translate"
                          (click)="cancel(d)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'deliveries.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Create dialog -->
      <p-dialog [(visible)]="createOpen" [modal]="true" [style]="{ width: '850px' }"
                [header]="'deliveries.createTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'orders.title' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.orderId" [options]="deliverableOrders()"
                          optionLabel="number" optionValue="id"
                          [filter]="true" filterBy="number,customerName"
                          [placeholder]="'deliveries.pickOrder' | translate"
                          (onChange)="onOrderChange()" appendTo="body"
                          [styleClass]="'w-full' + (orderInvalid() ? ' ng-invalid ng-dirty' : '')">
                <ng-template let-o pTemplate="item">
                  <div class="flex flex-col">
                    <span class="font-mono text-sm">{{ o.number }}</span>
                    <span class="text-xs text-gray-500">{{ o.customerName }}</span>
                  </div>
                </ng-template>
              </p-dropdown>
              @if (orderInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'deliveries.scheduledDate' | translate }} *</label>
              <p-calendar [(ngModel)]="form.scheduledDate" dateFormat="dd/mm/yy" appendTo="body"
                          [styleClass]="'w-full' + (scheduledDateInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (scheduledDateInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'deliveries.warehouse' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.warehouseId" [options]="warehouses()"
                          optionLabel="name" optionValue="id"
                          [placeholder]="'deliveries.pickWarehouse' | translate"
                          appendTo="body"
                          [styleClass]="'w-full' + (warehouseInvalid() ? ' ng-invalid ng-dirty' : '')">
                <ng-template let-w pTemplate="item">
                  <div class="flex items-center gap-2">
                    <span class="font-mono text-xs text-gray-500">{{ w.code }}</span>
                    <span>{{ w.name }}</span>
                    @if (w.defaultWarehouse) {
                      <span class="ml-auto text-[10px] uppercase text-primary-600 font-semibold">{{ 'common.default' | translate }}</span>
                    }
                  </div>
                </ng-template>
              </p-dropdown>
              @if (warehouseInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'deliveries.contactPhone' | translate }}</label>
              <input pInputText [(ngModel)]="form.contactPhone" class="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'deliveries.address' | translate }}</label>
            <input pInputText [(ngModel)]="form.address" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="form.notes" class="w-full" />
          </div>

          <div class="border rounded">
            <div class="p-2 bg-gray-50 border-b font-medium text-sm">
              {{ 'deliveries.linesToShip' | translate }}
            </div>
            <table class="w-full text-sm">
              <thead class="bg-gray-50 text-gray-600">
                <tr>
                  <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                  <th class="text-right p-2 w-32">{{ 'deliveries.remaining' | translate }}</th>
                  <th class="text-right p-2 w-32">{{ 'deliveries.quantityToShip' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (l of form.lines; track l.productId; let i = $index) {
                  <tr class="border-t">
                    <td class="p-2">
                      <div class="font-medium">{{ l.productName }}</div>
                      <div class="text-xs text-gray-500 font-mono">{{ l.sku }}</div>
                    </td>
                    <td class="p-2 text-right text-gray-500">{{ remainingByProduct[l.productId] ?? 0 }}</td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="l.quantityOrdered" [min]="0"
                                     [max]="remainingByProduct[l.productId] ?? 0"
                                     [minFractionDigits]="0" [maxFractionDigits]="3"
                                     inputStyleClass="w-full text-right" styleClass="w-full" />
                    </td>
                  </tr>
                }
                @if (form.lines.length === 0) {
                  <tr><td colspan="3" class="p-4 text-center text-gray-400">{{ 'deliveries.pickOrderFirst' | translate }}</td></tr>
                }
              </tbody>
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

      <!-- Record dialog -->
      <p-dialog [(visible)]="recordOpen" [modal]="true" [style]="{ width: '700px' }"
                [header]="'deliveries.recordTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'deliveries.signedBy' | translate }} *</label>
            <input pInputText [(ngModel)]="recordForm.signedBy" class="w-full"
                   [class.ng-invalid]="signedByInvalid()" [class.ng-dirty]="signedByInvalid()" />
            @if (signedByInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="recordForm.notes" class="w-full" />
          </div>
          <div class="border rounded">
            <table class="w-full text-sm">
              <thead class="bg-gray-50 text-gray-600">
                <tr>
                  <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                  <th class="text-right p-2 w-28">{{ 'deliveries.planned' | translate }}</th>
                  <th class="text-right p-2 w-32">{{ 'deliveries.alreadyDelivered' | translate }}</th>
                  <th class="text-right p-2 w-32">{{ 'deliveries.recordQty' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (l of recordForm.lines; track l.lineId) {
                  <tr class="border-t">
                    <td class="p-2">
                      <div class="font-medium">{{ l.productName }}</div>
                      <div class="text-xs text-gray-500 font-mono">{{ l.sku }}</div>
                    </td>
                    <td class="p-2 text-right text-gray-500">{{ l.quantityPlanned }}</td>
                    <td class="p-2 text-right text-gray-500">{{ l.quantityAlreadyDelivered }}</td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="l.quantityDelivered" [min]="0" [max]="l.quantityPlanned"
                                     [minFractionDigits]="0" [maxFractionDigits]="3"
                                     inputStyleClass="w-full text-right" styleClass="w-full" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="recordOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'deliveries.confirmRecord' | translate" icon="pi pi-check-circle"
                  (click)="saveRecord()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class DeliveryListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);

  protected deliveries = signal<Delivery[]>([]);
  protected orders = signal<OrderLite[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submittedCreate = signal(false);
  protected submittedRecord = signal(false);
  protected createOpen = false;
  protected recordOpen = false;

  protected orderInvalid(): boolean { return this.submittedCreate() && !this.form.orderId; }
  protected scheduledDateInvalid(): boolean { return this.submittedCreate() && !this.form.scheduledDate; }
  protected warehouseInvalid(): boolean { return this.submittedCreate() && !this.form.warehouseId; }
  protected signedByInvalid(): boolean { return this.submittedRecord() && !this.recordForm.signedBy?.trim(); }
  protected remainingByProduct: Record<string, number> = {};

  protected form: {
    orderId: string | null;
    warehouseId: string | null;
    scheduledDate: Date;
    address: string;
    contactPhone: string;
    notes: string;
    lines: DeliveryLineForm[];
  } = this.emptyForm();

  protected recordForm: {
    deliveryId: string | null;
    signedBy: string;
    notes: string;
    lines: RecordLineForm[];
  } = { deliveryId: null, signedBy: '', notes: '', lines: [] };

  ngOnInit() {
    this.load();
    this.loadOrders();
    this.loadWarehouses();
  }

  protected deliverableOrders(): OrderLite[] {
    // Lifecycle: DRAFT → CONFIRMED → INVOICED → PARTIALLY_DELIVERED → DELIVERED.
    // A BL is only creatable once the order has been invoiced — business rule:
    // no shipment without a prior non-cancelled invoice.
    const allowed = new Set(['INVOICED', 'PARTIALLY_DELIVERED']);
    return this.orders().filter(o => allowed.has(o.status));
  }

  protected totalOrdered(d: Delivery): number {
    return d.lines?.reduce((s, l) => s + l.quantityOrdered, 0) ?? 0;
  }

  protected totalDelivered(d: Delivery): number {
    return d.lines?.reduce((s, l) => s + l.quantityDelivered, 0) ?? 0;
  }

  protected statusSeverity(status: string): Severity {
    return ({
      PENDING: 'secondary', IN_PROGRESS: 'info', PARTIAL: 'warning',
      DELIVERED: 'success', CANCELLED: 'danger',
    } as Record<string, Severity>)[status] ?? 'secondary';
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

  protected async openCreate() {
    if (this.warehouses().length === 0) await this.loadWarehouses();
    this.form = this.emptyForm();
    this.remainingByProduct = {};
    this.submittedCreate.set(false);
    this.createOpen = true;
  }

  protected async onOrderChange() {
    const order = this.orders().find(o => o.id === this.form.orderId);
    if (!order) { this.form.lines = []; return; }
    // Fetch all deliveries for this order to compute remaining qty per product.
    const allDeliveries = await this.fetchAllDeliveriesForCustomer(order.customerId);
    const deliveredByProduct: Record<string, number> = {};
    for (const d of allDeliveries) {
      if (d.orderId !== order.id) continue;
      if (d.status === 'CANCELLED') continue;
      for (const ln of d.lines ?? []) {
        deliveredByProduct[ln.productId] = (deliveredByProduct[ln.productId] ?? 0)
          + Math.max(ln.quantityDelivered ?? 0, ln.quantityOrdered ?? 0);
      }
    }
    this.remainingByProduct = {};
    const lines: DeliveryLineForm[] = [];
    for (const ol of order.lines) {
      const remaining = ol.quantity - (deliveredByProduct[ol.productId] ?? 0);
      if (remaining <= 0) continue;
      this.remainingByProduct[ol.productId] = remaining;
      lines.push({
        productId: ol.productId,
        uomId: ol.uomId,
        productName: ol.productName,
        sku: ol.sku,
        quantityOrdered: remaining,
      });
    }
    this.form.lines = lines;
    if (order.customerId) {
      // pull customer address into the form
      try {
        const cust = await firstValueFrom(
          this.http.get<{ address?: string; phone?: string }>(`/api/v1/partners/${order.customerId}`)
        );
        if (!this.form.address) this.form.address = cust.address ?? '';
        if (!this.form.contactPhone) this.form.contactPhone = cust.phone ?? '';
      } catch { /* ignore */ }
    }
  }

  protected canSave(): boolean {
    return !!this.form.orderId
        && !!this.form.warehouseId
        && this.form.lines.length > 0
        && this.form.lines.some(l => (l.quantityOrdered ?? 0) > 0);
  }

  protected async save() {
    this.submittedCreate.set(true);
    if (!this.canSave()) return;
    this.saving.set(true);
    try {
      const order = this.orders().find(o => o.id === this.form.orderId);
      const payload = {
        customerId: order?.customerId,
        orderId: this.form.orderId,
        warehouseId: this.form.warehouseId,
        scheduledDate: this.toIsoDate(this.form.scheduledDate),
        address: this.form.address || null,
        contactPhone: this.form.contactPhone || null,
        notes: this.form.notes || null,
        lines: this.form.lines
          .filter(l => (l.quantityOrdered ?? 0) > 0)
          .map(l => ({
            productId: l.productId,
            uomId: l.uomId,
            quantityOrdered: l.quantityOrdered,
            productName: l.productName,
            sku: l.sku,
          })),
      };
      await firstValueFrom(this.http.post('/api/v1/deliveries', payload));
      this.createOpen = false;
      this.load();
    } finally {
      this.saving.set(false);
    }
  }

  protected async start(d: Delivery) {
    try {
      await firstValueFrom(this.http.post(`/api/v1/deliveries/${d.id}/start`, {}));
    } finally {
      this.load();
    }
  }

  protected openRecord(d: Delivery) {
    this.recordForm = {
      deliveryId: d.id,
      signedBy: d.signedBy ?? '',
      notes: '',
      lines: (d.lines ?? []).map(l => ({
        lineId: l.id,
        productName: l.productName,
        sku: l.sku,
        quantityPlanned: l.quantityOrdered,
        quantityAlreadyDelivered: l.quantityDelivered ?? 0,
        quantityDelivered: l.quantityOrdered - (l.quantityDelivered ?? 0),
      })),
    };
    this.submittedRecord.set(false);
    this.recordOpen = true;
  }

  protected async saveRecord() {
    this.submittedRecord.set(true);
    if (!this.recordForm.deliveryId || !this.recordForm.signedBy?.trim()) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post(`/api/v1/deliveries/${this.recordForm.deliveryId}/record`, {
        lines: this.recordForm.lines.map(l => ({
          lineId: l.lineId,
          quantityDelivered: l.quantityDelivered,
        })),
        signedBy: this.recordForm.signedBy.trim(),
        notes: this.recordForm.notes || null,
      }));
      this.recordOpen = false;
      this.load();
    } finally {
      this.saving.set(false);
    }
  }

  protected cancel(d: Delivery) {
    this.confirmation.confirm({
      message: this.i18n.instant('deliveries.confirmCancel', { number: d.number }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        try {
          await firstValueFrom(this.http.post(`/api/v1/deliveries/${d.id}/cancel`, {}));
        } finally {
          this.load();
        }
      },
    });
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
      const res = await firstValueFrom(this.http.get<{ content: Delivery[] }>('/api/v1/deliveries?size=200'));
      this.deliveries.set(res.content ?? []);
    } catch {
      this.deliveries.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadOrders() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: OrderLite[] }>('/api/v1/orders?size=200')
      );
      this.orders.set(res.content ?? []);
    } catch {
      this.orders.set([]);
    }
  }

  private async fetchAllDeliveriesForCustomer(customerId: string): Promise<Delivery[]> {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Delivery[] }>(
          `/api/v1/deliveries?customerId=${encodeURIComponent(customerId)}&size=200`
        )
      );
      return res.content ?? [];
    } catch {
      return [];
    }
  }

  private emptyForm() {
    return {
      orderId: null as string | null,
      warehouseId: this.warehouses().find(w => w.defaultWarehouse)?.id
                   ?? this.warehouses()[0]?.id
                   ?? null as string | null,
      scheduledDate: new Date(),
      address: '',
      contactPhone: '',
      notes: '',
      lines: [] as DeliveryLineForm[],
    };
  }

  private async loadWarehouses() {
    try {
      const list = await firstValueFrom(
        this.http.get<WarehouseLite[]>('/api/v1/inventory/warehouses')
      );
      this.warehouses.set(list ?? []);
    } catch {
      this.warehouses.set([]);
    }
  }
}
