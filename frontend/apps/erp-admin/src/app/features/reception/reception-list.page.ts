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

interface GoodsReceiptLine {
  id: string;
  productId: string;
  uomId: string;
  quantityOrdered: number;
  quantityReceived: number;
  unitCost: number;
  lotId: string | null;
  status: string;
  productName: string;
  sku: string;
}

interface GoodsReceipt {
  id: string;
  number: string;
  supplierId: string;
  supplierName: string;
  purchaseInvoiceId: string;
  warehouseId: string | null;
  status: string;
  type: string;
  scheduledDate: string;
  receivedAt: string;
  notes: string;
  lines: GoodsReceiptLine[];
}

interface WarehouseLite { id: string; code: string; name: string; defaultWarehouse: boolean; }
interface ProductLite { id: string; trackExpiry: boolean; }

interface InvoiceLite {
  id: string;
  number: string;
  supplierId: string;
  supplierName: string;
  status: string;
  receptionStatus: string;
  creditNoteCount: number;
}

interface ReceptionLineForm {
  productId: string;
  uomId: string;
  productName: string;
  sku: string;
  unitCost: number;
  remaining: number;
  quantityOrdered: number;
}

interface RecordLineForm {
  lineId: string;
  productId: string;
  productName: string;
  sku: string;
  quantityPlanned: number;
  quantityReceived: number;
  trackExpiry: boolean;
  lotNumber: string;
  productionDate: Date | null;
  expirationDate: Date | null;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-reception-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule, ButtonModule,
    DialogModule, DropdownModule, InputTextModule, InputNumberModule, CalendarModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'reception.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'reception.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'reception.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="receipts()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'reception.number' | translate }}</th>
              <th>{{ 'reception.supplier' | translate }}</th>
              <th>{{ 'reception.scheduledDate' | translate }}</th>
              <th>{{ 'reception.receivedAt' | translate }}</th>
              <th>{{ 'reception.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-d>
            <tr>
              <td>
                <span class="font-mono text-sm">{{ d.number }}</span>
                @if (d.type === 'RETURN') {
                  <p-tag [value]="'reception.type.return' | translate" severity="warning" styleClass="ml-2" />
                }
              </td>
              <td>{{ d.supplierName }}</td>
              <td>{{ d.scheduledDate | date:'mediumDate' }}</td>
              <td>{{ d.receivedAt | date:'medium' }}</td>
              <td><p-tag [value]="'reception.statuses.' + d.status | translate" [severity]="statusSeverity(d.status)" /></td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-file-pdf" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/goods-receipts/' + d.id + '/pdf')"></button>
                @if (d.type !== 'RETURN' && (d.status === 'PENDING' || d.status === 'IN_PROGRESS')) {
                  <button pButton icon="pi pi-check-circle" class="p-button-sm p-button-text p-button-success"
                          [pTooltip]="'reception.record' | translate"
                          (click)="openRecord(d)"></button>
                }
                @if (d.type !== 'RETURN' && d.status !== 'RECEIVED' && d.status !== 'CANCELLED') {
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.cancel' | translate"
                          (click)="cancel(d)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">{{ 'reception.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Create dialog -->
      <p-dialog [(visible)]="createOpen" [modal]="true" [style]="{ width: '640px' }"
                [header]="'reception.createTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'purchaseInvoices.title' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.invoiceId" [options]="receivableInvoices()"
                        optionLabel="number" optionValue="id"
                        [filter]="true" filterBy="number,supplierName"
                        [placeholder]="'reception.pickInvoice' | translate"
                        appendTo="body" (onChange)="onInvoiceChange()"
                        [styleClass]="'w-full' + (invoiceInvalid() ? ' ng-invalid ng-dirty' : '')">
              <ng-template let-o pTemplate="item">
                <div class="flex flex-col">
                  <span class="font-mono text-sm">{{ o.number }}</span>
                  <span class="text-xs text-gray-500">{{ o.supplierName }}</span>
                </div>
              </ng-template>
            </p-dropdown>
            @if (invoiceInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'reception.warehouse' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.warehouseId" [options]="warehouses()"
                          optionLabel="name" optionValue="id"
                          [placeholder]="'reception.pickWarehouse' | translate" appendTo="body"
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
              <label class="block text-sm font-medium mb-1">{{ 'reception.scheduledDate' | translate }} *</label>
              <p-calendar [(ngModel)]="form.scheduledDate" dateFormat="dd/mm/yy" appendTo="body"
                          [styleClass]="'w-full' + (scheduledDateInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (scheduledDateInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="form.notes" class="w-full" />
          </div>

          <div class="border rounded">
            <div class="p-2 bg-gray-50 border-b font-medium text-sm">
              {{ 'reception.linesToReceive' | translate }}
            </div>
            <table class="w-full text-sm">
              <thead class="bg-gray-50 text-gray-600">
                <tr>
                  <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                  <th class="text-right p-2 w-32">{{ 'reception.remaining' | translate }}</th>
                  <th class="text-right p-2 w-32">{{ 'reception.quantityToReceive' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (l of form.lines; track l.productId) {
                  <tr class="border-t">
                    <td class="p-2">
                      <div class="font-medium">{{ l.productName }}</div>
                      <div class="text-xs text-gray-500 font-mono">{{ l.sku }}</div>
                    </td>
                    <td class="p-2 text-right text-gray-500">{{ l.remaining }}</td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="l.quantityOrdered" [min]="0" [max]="l.remaining"
                                     [minFractionDigits]="0" [maxFractionDigits]="3"
                                     inputStyleClass="w-full text-right"
                                     [styleClass]="'w-full' + (createLinesInvalid() ? ' ng-invalid ng-dirty' : '')" />
                    </td>
                  </tr>
                }
                @if (form.lines.length === 0) {
                  <tr><td colspan="3" class="p-4 text-center text-gray-400">{{ 'reception.pickInvoiceFirst' | translate }}</td></tr>
                }
              </tbody>
            </table>
            @if (createLinesInvalid()) {
              <div class="px-2 py-2 text-xs text-red-600 border-t">{{ 'reception.atLeastOneLineQty' | translate }}</div>
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
      <p-dialog [(visible)]="recordOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="'reception.recordTitle' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <table class="w-full text-sm border">
            <thead class="bg-gray-50 text-gray-600">
              <tr>
                <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                <th class="text-right p-2 w-28">{{ 'reception.receivingNow' | translate }}</th>
                <th class="p-2 w-40">{{ 'reception.lotNumber' | translate }}</th>
                <th class="p-2 w-36">{{ 'reception.expirationDate' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (l of recordForm.lines; track l.lineId) {
                <tr class="border-t">
                  <td class="p-2">
                    <div class="font-medium">{{ l.productName }}</div>
                    <div class="text-xs text-gray-500 font-mono">{{ l.sku }}</div>
                  </td>
                  <td class="p-2 text-right font-medium">{{ l.quantityReceived }}</td>
                  <td class="p-1">
                    @if (l.trackExpiry) {
                      <input pInputText [(ngModel)]="l.lotNumber" class="w-full"
                             [placeholder]="'reception.lotNumber' | translate" />
                    } @else {
                      <span class="text-gray-300">—</span>
                    }
                  </td>
                  <td class="p-1">
                    @if (l.trackExpiry) {
                      <p-calendar [(ngModel)]="l.expirationDate" dateFormat="dd/mm/yy"
                                  styleClass="w-full" appendTo="body" />
                    } @else {
                      <span class="text-gray-300">—</span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="recordForm.notes" class="w-full" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="recordOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'reception.confirmRecord' | translate" icon="pi pi-check-circle"
                  (click)="saveRecord()" [loading]="saving()" [disabled]="!canRecord()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class ReceptionListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private messages = inject(MessageService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected receipts = signal<GoodsReceipt[]>([]);
  protected invoicesList = signal<InvoiceLite[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  protected productsExpiry: Record<string, boolean> = {};
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submittedCreate = signal(false);
  protected createOpen = false;
  protected recordOpen = false;

  protected invoiceInvalid(): boolean { return this.submittedCreate() && !this.form.invoiceId; }
  protected scheduledDateInvalid(): boolean { return this.submittedCreate() && !this.form.scheduledDate; }
  protected warehouseInvalid(): boolean { return this.submittedCreate() && !this.form.warehouseId; }

  protected form: {
    invoiceId: string | null;
    warehouseId: string | null;
    scheduledDate: Date;
    notes: string;
    lines: ReceptionLineForm[];
  } = this.emptyForm();

  protected createLinesInvalid(): boolean {
    return this.submittedCreate() && !this.form.lines.some(l => (l.quantityOrdered ?? 0) > 0);
  }

  /** On invoice pick, prefill the lines with the invoice's outstanding (not-yet-
   *  received) quantities — mirroring the sales BL create dialog. */
  protected async onInvoiceChange() {
    this.form.lines = [];
    const invId = this.form.invoiceId;
    if (!invId) return;
    try {
      const lines = await firstValueFrom(
        this.http.get<{ productId: string; uomId: string; quantityOrdered: number;
          unitCost: number; productName: string; sku: string }[]>(
          `/api/v1/goods-receipts/outstanding-lines?invoiceId=${invId}`));
      this.form.lines = (lines ?? []).map(l => {
        const remaining = Number(l.quantityOrdered);
        return {
          productId: l.productId, uomId: l.uomId, productName: l.productName, sku: l.sku,
          unitCost: Number(l.unitCost ?? 0), remaining, quantityOrdered: remaining,
        };
      });
    } catch { this.form.lines = []; }
  }

  protected recordForm: {
    receiptId: string | null;
    notes: string;
    lines: RecordLineForm[];
  } = { receiptId: null, notes: '', lines: [] };

  ngOnInit() {
    Promise.all([this.loadInvoices(), this.loadWarehouses(), this.loadProducts()]).then(() => {
      const invoiceId = this.route.snapshot.queryParamMap.get('invoiceId');
      if (invoiceId && this.receivableInvoices().some(i => i.id === invoiceId)) {
        this.openCreate();
        this.form.invoiceId = invoiceId;
        this.onInvoiceChange();
        // Drop the query param so a refresh doesn't re-trigger the dialog.
        this.router.navigate([], { queryParams: {}, replaceUrl: true });
      }
    });
  }

  protected receivableInvoices(): InvoiceLite[] {
    return this.invoicesList().filter(inv =>
      inv.status !== 'DRAFT' && inv.status !== 'CANCELLED'
      && inv.creditNoteCount === 0
      && inv.receptionStatus !== 'RECEIVED' && inv.receptionStatus !== 'RETURNED');
  }

  protected statusSeverity(status: string): Severity {
    return ({
      PENDING: 'secondary', IN_PROGRESS: 'info',
      RECEIVED: 'success', CANCELLED: 'danger',
    } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected canRecord(): boolean {
    const lines = this.recordForm.lines;
    if (!lines.some(l => (l.quantityReceived ?? 0) > 0)) return false;
    return lines.every(l => {
      if ((l.quantityReceived ?? 0) <= 0) return true;
      if (l.trackExpiry) return !!l.lotNumber?.trim() && !!l.expirationDate;
      return true;
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
    this.submittedCreate.set(false);
    this.createOpen = true;
  }

  protected canSave(): boolean {
    return !!this.form.invoiceId && !!this.form.warehouseId && !!this.form.scheduledDate
        && this.form.lines.length > 0 && this.form.lines.some(l => (l.quantityOrdered ?? 0) > 0);
  }

  protected async save() {
    this.submittedCreate.set(true);
    if (!this.canSave()) return;
    this.saving.set(true);
    try {
      const inv = this.invoicesList().find(o => o.id === this.form.invoiceId);
      const payload = {
        supplierId: inv?.supplierId ?? null,
        purchaseInvoiceId: this.form.invoiceId,
        warehouseId: this.form.warehouseId,
        scheduledDate: this.toIsoDate(this.form.scheduledDate),
        notes: this.form.notes || null,
        lines: this.form.lines
          .filter(l => (l.quantityOrdered ?? 0) > 0)
          .map(l => ({
            productId: l.productId,
            uomId: l.uomId,
            quantityOrdered: l.quantityOrdered,
            unitCost: l.unitCost,
            productName: l.productName,
            sku: l.sku,
          })),
      };
      await firstValueFrom(this.http.post('/api/v1/goods-receipts', payload));
      this.createOpen = false;
      this.reload();
    } catch (e) {
      this.showError(e);
    } finally {
      this.saving.set(false);
    }
  }

  protected openRecord(d: GoodsReceipt) {
    this.recordForm = {
      receiptId: d.id,
      notes: '',
      lines: (d.lines ?? []).map(l => ({
        lineId: l.id,
        productId: l.productId,
        productName: l.productName,
        sku: l.sku,
        quantityPlanned: l.quantityOrdered - (l.quantityReceived ?? 0),
        quantityReceived: Math.max(0, l.quantityOrdered - (l.quantityReceived ?? 0)),
        trackExpiry: !!this.productsExpiry[l.productId],
        lotNumber: '',
        productionDate: null,
        expirationDate: null,
      })),
    };
    this.recordOpen = true;
  }

  protected async saveRecord() {
    if (!this.recordForm.receiptId || !this.canRecord()) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post(`/api/v1/goods-receipts/${this.recordForm.receiptId}/record`, {
        lines: this.recordForm.lines
          .filter(l => (l.quantityReceived ?? 0) > 0)
          .map(l => ({
            lineId: l.lineId,
            quantityReceived: l.quantityReceived,
            lotNumber: l.trackExpiry ? l.lotNumber : null,
            productionDate: l.trackExpiry && l.productionDate ? this.toIsoDate(l.productionDate) : null,
            expirationDate: l.trackExpiry && l.expirationDate ? this.toIsoDate(l.expirationDate) : null,
          })),
        notes: this.recordForm.notes || null,
      }));
      this.recordOpen = false;
      this.reload();
    } catch (e) {
      this.showError(e);
    } finally {
      this.saving.set(false);
    }
  }

  protected cancel(d: GoodsReceipt) {
    this.confirmation.confirm({
      message: this.i18n.instant('reception.confirmCancel', { number: d.number }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        try {
          await firstValueFrom(this.http.post(`/api/v1/goods-receipts/${d.id}/cancel`, {}));
        } finally {
          this.reload();
        }
      },
    });
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

  private toIsoDate(d: Date): string {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize;
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: GoodsReceipt[]; totalElements: number }>(
          `/api/v1/goods-receipts?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.receipts()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.receipts.set(arr);
      this.total.set(totalElements);
    } catch {
      this.receipts.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.receipts.set([]);
    this.total.set(0);
    this.table?.reset();
    this.loadInvoices();
  }

  private async loadInvoices() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: InvoiceLite[] }>('/api/v1/purchase-invoices?size=200')
      );
      this.invoicesList.set(res.content ?? []);
    } catch {
      this.invoicesList.set([]);
    }
  }

  private async loadWarehouses() {
    try {
      const list = await firstValueFrom(this.http.get<WarehouseLite[]>('/api/v1/inventory/warehouses'));
      this.warehouses.set(list ?? []);
    } catch {
      this.warehouses.set([]);
    }
  }

  private async loadProducts() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: ProductLite[] }>('/api/v1/products?size=500'));
      const map: Record<string, boolean> = {};
      for (const p of res.content ?? []) map[p.id] = !!p.trackExpiry;
      this.productsExpiry = map;
    } catch {
      this.productsExpiry = {};
    }
  }

  private emptyForm() {
    return {
      invoiceId: null as string | null,
      warehouseId: this.warehouses().find(w => w.defaultWarehouse)?.id
                   ?? this.warehouses()[0]?.id
                   ?? null as string | null,
      scheduledDate: new Date(),
      notes: '',
      lines: [] as ReceptionLineForm[],
    };
  }
}
