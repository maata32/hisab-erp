import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface StockTransfer {
  id: string;
  transferNumber: string;
  fromWarehouseId: string;
  toWarehouseId: string;
  status: 'DRAFT' | 'IN_TRANSIT' | 'COMPLETED' | 'CANCELLED';
  scheduledDate: string | null;
  completedAt: string | null;
  notes: string | null;
}

interface WarehouseLite { id: string; code: string; name: string; }

interface ProductOpt {
  id: string;
  name: string;
  sku: string;
  baseUomId: string;
  variants?: { id: string; defaultVariant?: boolean; active?: boolean }[];
}

interface TransferLineForm { productId: string | null; uomId: string | null; quantity: number; }

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-stock-transfer-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, DialogModule, DropdownModule, InputTextModule, InputNumberModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'stockTransfers.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'stockTransfers.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'stockTransfers.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="transfers()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'stockTransfers.number' | translate }}</th>
              <th>{{ 'stockTransfers.from' | translate }}</th>
              <th>{{ 'stockTransfers.to' | translate }}</th>
              <th>{{ 'stockTransfers.scheduled' | translate }}</th>
              <th>{{ 'stockTransfers.completed' | translate }}</th>
              <th>{{ 'stockTransfers.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-t>
            <tr>
              <td><span class="font-mono text-sm">{{ t.transferNumber }}</span></td>
              <td>{{ warehouseName(t.fromWarehouseId) }}</td>
              <td>{{ warehouseName(t.toWarehouseId) }}</td>
              <td>{{ t.scheduledDate || '—' }}</td>
              <td>{{ t.completedAt ? (t.completedAt | date:'short') : '—' }}</td>
              <td>
                <p-tag [value]="'stockTransfers.statuses.' + t.status | translate"
                       [severity]="statusSeverity(t.status)" />
              </td>
              <td>
                @if (t.status === 'DRAFT' || t.status === 'IN_TRANSIT') {
                  <button pButton icon="pi pi-check" class="p-button-sm p-button-text"
                          [pTooltip]="'stockTransfers.execute' | translate"
                          (click)="execute(t.id)"></button>
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.cancel' | translate"
                          (click)="cancelTransfer(t.id)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">
              {{ 'stockTransfers.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '720px' }"
                [header]="'stockTransfers.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'stockTransfers.from' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.fromWarehouseId" [options]="warehouses()"
                          optionLabel="name" optionValue="id" appendTo="body"
                          [styleClass]="'w-full' + (fromInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (fromInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'stockTransfers.to' | translate }} *</label>
              <p-dropdown [(ngModel)]="form.toWarehouseId" [options]="warehouses()"
                          optionLabel="name" optionValue="id" appendTo="body"
                          [styleClass]="'w-full' + (toInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (toInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'stockTransfers.scheduled' | translate }}</label>
              <input pInputText type="date" [(ngModel)]="form.scheduledDate" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
              <input pInputText [(ngModel)]="form.notes" class="w-full" />
            </div>
          </div>

          <div class="border rounded">
            <div class="flex items-center justify-between p-2 bg-gray-50 border-b">
              <span class="font-medium text-sm">{{ 'stockTransfers.lines' | translate }}</span>
              <button pButton icon="pi pi-plus" [label]="'stockTransfers.addLine' | translate"
                      class="p-button-sm p-button-text" (click)="addLine()"></button>
            </div>
            <table class="w-full text-sm">
              <thead class="bg-gray-50 text-gray-600">
                <tr>
                  <th class="text-left p-2">{{ 'stockTransfers.product' | translate }}</th>
                  <th class="text-right p-2 w-32">{{ 'stockTransfers.quantity' | translate }}</th>
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
                          <span>{{ product.name }} <span class="text-gray-400">({{ product.sku }})</span></span>
                        </ng-template>
                      </p-dropdown>
                    </td>
                    <td class="p-1">
                      <p-inputNumber [(ngModel)]="line.quantity" [minFractionDigits]="0" [maxFractionDigits]="3"
                                     inputStyleClass="w-full text-right"
                                     [styleClass]="'w-full' + (lineQtyInvalid(line) ? ' ng-invalid ng-dirty' : '')" />
                    </td>
                    <td class="p-1 text-center">
                      <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                              [attr.aria-label]="'common.delete' | translate"
                              (click)="removeLine(i)"></button>
                    </td>
                  </tr>
                }
                @if (form.lines.length === 0) {
                  <tr><td colspan="3" class="p-4 text-center"
                          [class.text-gray-400]="!noLinesInvalid()" [class.text-red-600]="noLinesInvalid()">
                    {{ 'stockTransfers.noLines' | translate }}
                  </td></tr>
                }
              </tbody>
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
export class StockTransferListPage implements OnInit {
  private http = inject(HttpClient);

  protected transfers = signal<StockTransfer[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  protected products = signal<ProductOpt[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;

  protected form = this.emptyForm();

  protected fromInvalid(): boolean { return this.submitted() && !this.form.fromWarehouseId; }
  protected toInvalid(): boolean { return this.submitted() && !this.form.toWarehouseId; }
  protected noLinesInvalid(): boolean { return this.submitted() && this.form.lines.length === 0; }
  protected lineProductInvalid(line: TransferLineForm): boolean { return this.submitted() && !line.productId; }
  protected lineQtyInvalid(line: TransferLineForm): boolean {
    return this.submitted() && (line.quantity == null || line.quantity <= 0);
  }

  ngOnInit() {
    this.loadWarehouses();
    this.loadProducts();
    // Transfers are fetched on demand via the p-table's onLazyLoad.
  }

  protected statusSeverity(s: string): Severity {
    switch (s) {
      case 'COMPLETED': return 'success';
      case 'IN_TRANSIT': return 'info';
      case 'CANCELLED': return 'danger';
      default: return 'secondary';
    }
  }

  protected warehouseName(id: string): string {
    const w = this.warehouses().find((x) => x.id === id);
    return w ? w.name : '—';
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected addLine() {
    this.form.lines.push({ productId: null, uomId: null, quantity: 1 });
  }

  protected removeLine(i: number) { this.form.lines.splice(i, 1); }

  protected onProductChange(line: TransferLineForm) {
    const p = this.products().find((x) => x.id === line.productId);
    if (p) line.uomId = p.baseUomId;
  }

  /** Resolve the variant to transfer for a product: its default (or first active) variant. */
  protected variantIdFor(productId: string | null): string | null {
    if (!productId) return null;
    const vs = this.products().find((p) => p.id === productId)?.variants ?? [];
    return (vs.find((v) => v.defaultVariant && v.active)
      ?? vs.find((v) => v.active) ?? vs[0])?.id ?? null;
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.fromWarehouseId || !this.form.toWarehouseId) return;
    if (this.form.lines.length === 0) return;
    if (!this.form.lines.every((l) => !!l.productId && (l.quantity || 0) > 0)) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/inventory/transfers', {
        fromWarehouseId: this.form.fromWarehouseId,
        toWarehouseId: this.form.toWarehouseId,
        scheduledDate: this.form.scheduledDate || null,
        notes: this.form.notes || null,
        lines: this.form.lines.map((l) => ({
          variantId: this.variantIdFor(l.productId),
          lotId: null,
          uomId: l.uomId,
          quantityRequested: l.quantity,
        })),
      }));
      this.dialogOpen = false;
      this.reload();
    } finally { this.saving.set(false); }
  }

  protected async execute(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/inventory/transfers/${id}/execute`, {}));
    this.reload();
  }

  protected async cancelTransfer(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/inventory/transfers/${id}/cancel`, {}));
    this.reload();
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: StockTransfer[]; totalElements: number }>(
          `/api/v1/inventory/transfers?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.transfers()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.transfers.set(arr);
      this.total.set(totalElements);
    } catch {
      this.transfers.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.transfers.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadWarehouses() {
    try {
      const list = await firstValueFrom(
        this.http.get<WarehouseLite[]>('/api/v1/inventory/warehouses')
      );
      this.warehouses.set(list ?? []);
    } catch { this.warehouses.set([]); }
  }

  private async loadProducts() {
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: ProductOpt[] }>('/api/v1/products?size=500')
      );
      this.products.set((res.content ?? []).filter((p) => (p as { active?: boolean }).active !== false));
    } catch { this.products.set([]); }
  }

  private emptyForm() {
    return {
      fromWarehouseId: null as string | null,
      toWarehouseId: null as string | null,
      scheduledDate: '',
      notes: '',
      lines: [] as TransferLineForm[],
    };
  }
}
