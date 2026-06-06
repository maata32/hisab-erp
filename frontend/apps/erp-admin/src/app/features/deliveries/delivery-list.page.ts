import { Component, OnInit, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
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
  invoiceId: string;
  status: string;
  type: string;
  scheduledDate: string;
  deliveredAt: string;
  address: string;
  contactPhone: string;
  signedBy: string;
  notes: string;
  lines: DeliveryLine[];
}

interface WarehouseLite { id: string; code: string; name: string; defaultWarehouse: boolean; }

interface InvoiceLite {
  id: string;
  number: string;
  customerId: string;
  customerName: string;
  status: string;
  deliveryStatus: string;
  creditNoteCount: number;
  lines: { id: string; variantId: string; productId: string; uomId: string; quantity: number; productName: string; sku: string }[];
}

interface DeliveryLineForm {
  variantId: string;
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
        <p-table #table [value]="deliveries()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'deliveries.scheduledDate' | translate }}</th>
              <th>{{ 'deliveries.deliveredAt' | translate }}</th>
              <th>{{ 'deliveries.address' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-d>
            <tr>
              <td>
                <span class="font-mono text-sm">{{ d.number }}</span>
                @if (d.type === 'RETURN') {
                  <p-tag [value]="'deliveries.type.return' | translate" severity="warning"
                         styleClass="ml-2" />
                }
              </td>
              <td>{{ d.customerName }}</td>
              <td>{{ d.scheduledDate | date:'mediumDate' }}</td>
              <td>{{ d.deliveredAt | date:'medium' }}</td>
              <td class="max-w-xs truncate text-sm text-gray-600">{{ d.address }}</td>
              <td><p-tag [value]="'deliveries.statuses.' + d.status | translate" [severity]="statusSeverity(d.status)" /></td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/deliveries/' + d.id + '/pdf')"></button>
                @if (d.type !== 'RETURN' && (d.status === 'PENDING' || d.status === 'IN_PROGRESS')) {
                  <button pButton icon="pi pi-check-circle" class="p-button-sm p-button-text p-button-success"
                          [pTooltip]="'deliveries.record' | translate"
                          (click)="openRecord(d)"></button>
                }
                @if (d.type !== 'RETURN' && d.status !== 'DELIVERED' && d.status !== 'CANCELLED') {
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.cancel' | translate"
                          (click)="cancel(d)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">{{ 'deliveries.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Create dialog -->
      <p-dialog [(visible)]="createOpen" [modal]="true" [style]="{ width: '850px' }"
                [header]="'deliveries.createTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'invoices.title' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.invoiceId" [options]="deliverableInvoices()"
                          optionLabel="number" optionValue="id"
                          [filter]="true" filterBy="number,customerName"
                          [placeholder]="'deliveries.pickInvoice' | translate"
                          (onChange)="onInvoiceChange()" appendTo="body"
                          [styleClass]="'w-full' + (invoiceInvalid() ? ' ng-invalid ng-dirty' : '')">
                <ng-template let-o pTemplate="item">
                  <div class="flex flex-col">
                    <span class="font-mono text-sm">{{ o.number }}</span>
                    <span class="text-xs text-gray-500">{{ o.customerName }}</span>
                  </div>
                </ng-template>
              </p-dropdown>
              @if (invoiceInvalid()) {
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
                          appendTo="body" (onChange)="onWarehouseChange()"
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
                                     inputStyleClass="w-full text-right"
                                     [styleClass]="'w-full' + (createLinesInvalid() ? ' ng-invalid ng-dirty' : '')" />
                    </td>
                  </tr>
                }
                @if (form.lines.length === 0) {
                  <tr><td colspan="3" class="p-4 text-center text-gray-400">{{ 'deliveries.pickInvoiceFirst' | translate }}</td></tr>
                }
              </tbody>
            </table>
            @if (createLinesInvalid()) {
              <div class="px-2 py-2 text-xs text-red-600 border-t">{{ 'deliveries.atLeastOneLineQty' | translate }}</div>
            }
            @if (anyShort()) {
              <div class="px-3 py-2 text-xs text-amber-800 bg-amber-50 border-t border-amber-200 flex items-start gap-2">
                <i class="pi pi-exclamation-triangle mt-0.5"></i>
                <div>
                  <div class="font-medium">{{ 'deliveries.stockWarningTitle' | translate }}</div>
                  <ul class="list-disc ml-4 mt-1">
                    @for (l of shortLines(); track l.productId) {
                      <li>{{ l.productName }} — {{ 'deliveries.stockWarningLine' | translate:{ available: lineAvailable(l.productId), requested: l.quantityOrdered } }}</li>
                    }
                  </ul>
                  <div class="mt-1">{{ 'deliveries.stockWarningHint' | translate }}</div>
                </div>
              </div>
            }
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
                    <td class="p-2 text-right font-medium">{{ l.quantityPlanned }}</td>
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
  private messages = inject(MessageService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected deliveries = signal<Delivery[]>([]);
  protected invoicesList = signal<InvoiceLite[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submittedCreate = signal(false);
  protected submittedRecord = signal(false);
  protected createOpen = false;
  protected recordOpen = false;

  protected invoiceInvalid(): boolean { return this.submittedCreate() && !this.form.invoiceId; }
  protected scheduledDateInvalid(): boolean { return this.submittedCreate() && !this.form.scheduledDate; }
  protected warehouseInvalid(): boolean { return this.submittedCreate() && !this.form.warehouseId; }
  protected createLinesInvalid(): boolean {
    return this.submittedCreate() && !this.form.lines.some(l => (l.quantityOrdered ?? 0) > 0);
  }
  protected signedByInvalid(): boolean { return this.submittedRecord() && !this.recordForm.signedBy?.trim(); }
  protected remainingByProduct: Record<string, number> = {};
  /** Available qty per product in the currently selected create-dialog warehouse. */
  protected stockByProduct: Record<string, number> = {};

  protected lineAvailable(productId: string): number {
    return this.stockByProduct[productId] ?? 0;
  }
  /** A line is short when its ship qty exceeds the selected warehouse's stock. */
  protected lineShort(l: DeliveryLineForm): boolean {
    return !!this.form.warehouseId && (l.quantityOrdered ?? 0) > this.lineAvailable(l.productId);
  }
  protected shortLines(): DeliveryLineForm[] {
    return this.form.lines.filter(l => this.lineShort(l));
  }
  protected anyShort(): boolean { return this.shortLines().length > 0; }

  private async loadWarehouseStock() {
    this.stockByProduct = {};
    const wid = this.form.warehouseId;
    if (!wid) return;
    try {
      const stocks = await firstValueFrom(
        this.http.get<{ productId: string; qtyAvailable: number }[]>(
          `/api/v1/inventory/stocks/by-warehouse/${wid}`)
      );
      const map: Record<string, number> = {};
      for (const s of stocks ?? []) map[s.productId] = Number(s.qtyAvailable);
      this.stockByProduct = map;
    } catch {
      this.stockByProduct = {};
    }
  }

  protected onWarehouseChange() {
    this.loadWarehouseStock();
  }

  protected form: {
    invoiceId: string | null;
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
    // auto-loaded by p-table lazy
    Promise.all([this.loadInvoices(), this.loadWarehouses()]).then(() => {
      const invoiceId = this.route.snapshot.queryParamMap.get('createForInvoice');
      if (invoiceId && this.deliverableInvoices().some(i => i.id === invoiceId)) {
        this.openCreate().then(() => {
          this.form.invoiceId = invoiceId;
          this.onInvoiceChange();
        });
        // Drop the query param so a page refresh doesn't re-trigger the dialog.
        this.router.navigate([], { queryParams: {}, replaceUrl: true });
      }
    });
  }

  protected deliverableInvoices(): InvoiceLite[] {
    // BL prereq, mirroring the backend SalesService.canReceiveDelivery: invoice
    // not DRAFT, not CANCELLED, not settled by an avoir (creditNoteCount === 0),
    // and not already fully delivered or returned.
    return this.invoicesList().filter(inv =>
      inv.status !== 'DRAFT'
      && inv.status !== 'CANCELLED'
      && inv.creditNoteCount === 0
      && inv.deliveryStatus !== 'DELIVERED'
      && inv.deliveryStatus !== 'RETURNED');
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
    await this.loadWarehouseStock(); // warehouse is preselected (default) in emptyForm
  }

  protected async onInvoiceChange() {
    const invoice = this.invoicesList().find(o => o.id === this.form.invoiceId);
    if (!invoice) { this.form.lines = []; return; }
    // Fetch all deliveries for this invoice to compute remaining qty per product.
    const allDeliveries = await this.fetchDeliveriesForInvoice(invoice.id);
    const deliveredByProduct: Record<string, number> = {};
    for (const d of allDeliveries) {
      if (d.status === 'CANCELLED') continue;
      for (const ln of d.lines ?? []) {
        deliveredByProduct[ln.productId] = (deliveredByProduct[ln.productId] ?? 0)
          + Math.max(ln.quantityDelivered ?? 0, ln.quantityOrdered ?? 0);
      }
    }
    this.remainingByProduct = {};
    const lines: DeliveryLineForm[] = [];
    for (const il of invoice.lines) {
      const remaining = il.quantity - (deliveredByProduct[il.productId] ?? 0);
      if (remaining <= 0) continue;
      this.remainingByProduct[il.productId] = remaining;
      lines.push({
        variantId: il.variantId,
        productId: il.productId,
        uomId: il.uomId,
        productName: il.productName,
        sku: il.sku,
        quantityOrdered: remaining,
      });
    }
    this.form.lines = lines;
    if (invoice.customerId) {
      // pull customer address into the form
      try {
        const cust = await firstValueFrom(
          this.http.get<{ address?: string; phone?: string }>(`/api/v1/partners/${invoice.customerId}`)
        );
        if (!this.form.address) this.form.address = cust.address ?? '';
        if (!this.form.contactPhone) this.form.contactPhone = cust.phone ?? '';
      } catch { /* ignore */ }
    }
  }

  protected canSave(): boolean {
    return !!this.form.invoiceId
        && !!this.form.warehouseId
        && this.form.lines.length > 0
        && this.form.lines.some(l => (l.quantityOrdered ?? 0) > 0);
  }

  protected async save() {
    this.submittedCreate.set(true);
    if (!this.canSave()) return;
    this.saving.set(true);
    try {
      const invoice = this.invoicesList().find(o => o.id === this.form.invoiceId);
      const payload = {
        customerId: invoice?.customerId,
        invoiceId: this.form.invoiceId,
        warehouseId: this.form.warehouseId,
        scheduledDate: this.toIsoDate(this.form.scheduledDate),
        address: this.form.address || null,
        contactPhone: this.form.contactPhone || null,
        notes: this.form.notes || null,
        lines: this.form.lines
          .filter(l => (l.quantityOrdered ?? 0) > 0)
          .map(l => ({
            variantId: l.variantId,
            uomId: l.uomId,
            quantityOrdered: l.quantityOrdered,
            productName: l.productName,
            sku: l.sku,
          })),
      };
      await firstValueFrom(this.http.post('/api/v1/deliveries', payload));
      this.createOpen = false;
      this.reload();
    } finally {
      this.saving.set(false);
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
    if (!this.recordForm.deliveryId || !this.recordForm.signedBy?.trim()) {
      return;
    }
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
      this.reload();
    } catch (e) {
      // Confirming a delivery issues stock and rolls back atomically when stock
      // is short. Surface why instead of failing silently; the dialog stays open
      // so the user can restock (or cancel the BL) and retry.
      this.showError(e);
    } finally {
      this.saving.set(false);
    }
  }

  private showError(err: unknown) {
    let detail = this.i18n.instant('common.error_generic');
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (body?.code) {
        const translated = this.i18n.instant(body.code, body.details ?? {});
        detail = translated !== body.code ? translated : (body.message ?? body.code);
      } else if (body?.message) {
        detail = body.message;
      }
    }
    this.messages.add({
      severity: 'error',
      summary: this.i18n.instant('common.error'),
      detail,
      life: 5000,
    });
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
          this.reload();
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

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Delivery[]; totalElements: number }>(
          `/api/v1/deliveries?size=200&page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.deliveries()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.deliveries.set(arr);
      this.total.set(totalElements);
    } catch {
      this.deliveries.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.deliveries.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadInvoices() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: InvoiceLite[] }>('/api/v1/invoices?size=200')
      );
      this.invoicesList.set(res.content ?? []);
    } catch {
      this.invoicesList.set([]);
    }
  }

  private async fetchDeliveriesForInvoice(invoiceId: string): Promise<Delivery[]> {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Delivery[] }>(
          `/api/v1/deliveries?invoiceId=${encodeURIComponent(invoiceId)}&size=200`
        )
      );
      return res.content ?? [];
    } catch {
      return [];
    }
  }

  private emptyForm() {
    return {
      invoiceId: null as string | null,
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
