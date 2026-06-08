import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MoneyPipe } from '@minierp/shared-i18n';
import { AUTH_SERVICE } from '@minierp/shared-auth';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

interface StockRow {
  warehouseId: string;
  warehouseName: string | null;
  productId: string;
  productName: string;
  sku: string;
  variantId: string | null;
  variantSku: string | null;
  attributes: string | null;
  qtyOnHand: number;
  qtyReserved: number;
  qtyAvailable: number;
  averageCost: number;
  stockValue: number;
  isLowStock: boolean;
}

interface WarehouseLite { id: string; code: string; name: string; defaultWarehouse: boolean; }
interface ProductVariantOpt { id: string; sku: string; attributes: string | null; defaultVariant: boolean; active: boolean; }
interface ProductOpt { id: string; sku: string; name: string; trackExpiry: boolean; variants: ProductVariantOpt[]; }
interface StockMovement {
  id: string;
  warehouseId: string;
  productId: string;
  type: string;
  qtySigned: number;
  unitCost: number | null;
  referenceType: string | null;
  referenceId: string | null;
  referenceNumber: string | null;
  note: string | null;
  occurredAt: string;
  userId: string | null;
}

type TagSeverity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-stock-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe,
    TableModule, DropdownModule, TagModule, ButtonModule, DialogModule,
    AutoCompleteModule, InputTextModule, InputNumberModule, TooltipModule,
  ],
  providers: [MessageService],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'stock.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'stock.subtitle' | translate }}</p>
        </div>
        @if (canAdjust) {
          <button pButton icon="pi pi-plus" [label]="'stock.openOpeningBalance' | translate"
                  (click)="openOpening()" class="p-button-sm"></button>
        }
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <div class="mb-3 flex items-center gap-3 flex-wrap">
          <label class="text-sm font-medium">{{ 'stock.filterByWarehouse' | translate }}</label>
          <p-dropdown [(ngModel)]="selectedWarehouseId" [options]="warehouses()"
                      optionLabel="name" optionValue="id" [showClear]="true"
                      [placeholder]="'stock.allWarehouses' | translate"
                      (onChange)="load()" styleClass="w-64" />
        </div>

        <p-table [value]="rows()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'stock.sku' | translate }}</th>
              <th>{{ 'stock.product' | translate }}</th>
              @if (!selectedWarehouseId) {
                <th>{{ 'stock.fields.warehouse' | translate }}</th>
              }
              <th class="text-right">{{ 'stock.qtyOnHand' | translate }}</th>
              <th class="text-right">{{ 'stock.qtyReserved' | translate }}</th>
              <th class="text-right">{{ 'stock.qtyAvailable' | translate }}</th>
              <th class="text-right">{{ 'stock.avgCost' | translate }}</th>
              <th class="text-right">{{ 'stock.value' | translate }}</th>
              <th>{{ 'stock.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-r>
            <tr>
              <td><span class="font-mono text-sm">{{ r.variantSku || r.sku }}</span></td>
              <td class="font-medium">
                {{ r.productName }}
                @if (variantLabel(r)) {
                  <div class="text-xs text-gray-500 font-normal">{{ variantLabel(r) }}</div>
                }
              </td>
              @if (!selectedWarehouseId) {
                <td class="text-gray-600">{{ r.warehouseName }}</td>
              }
              <td class="text-right">{{ r.qtyOnHand | number:'1.0-3' }}</td>
              <td class="text-right text-gray-500">{{ r.qtyReserved | number:'1.0-3' }}</td>
              <td class="text-right font-semibold">{{ r.qtyAvailable | number:'1.0-3' }}</td>
              <td class="text-right text-gray-600">{{ r.averageCost | number:'1.0-2' }}</td>
              <td class="text-right font-semibold">{{ r.stockValue | money }}</td>
              <td>
                @if (r.isLowStock) {
                  <p-tag [value]="'stock.low' | translate" severity="danger" icon="pi pi-exclamation-triangle" />
                } @else {
                  <p-tag [value]="'stock.ok' | translate" severity="success" />
                }
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-history" class="p-button-sm p-button-text"
                        [pTooltip]="'stock.history' | translate"
                        (click)="openHistory(r)"></button>
                @if (canAdjust) {
                  <button pButton icon="pi pi-sliders-h" class="p-button-sm p-button-text"
                          [pTooltip]="'stock.adjust' | translate"
                          (click)="openAdjust(r)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td [attr.colspan]="selectedWarehouseId ? 9 : 10" class="text-center text-gray-400 py-8">
              {{ 'stock.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Stock initial dialog -->
      <p-dialog [(visible)]="openingOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="'stock.openingDialog.title' | translate" [closable]="!saving()">
        <p class="text-sm text-gray-500 mb-4">{{ 'stock.openingDialog.help' | translate }}</p>
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'stock.fields.warehouse' | translate }} *</label>
            <p-dropdown [(ngModel)]="opening.warehouseId" [options]="warehouses()"
                        optionLabel="name" optionValue="id" appendTo="body"
                        [styleClass]="'w-full' + (openingWarehouseInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (openingWarehouseInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'stock.fields.product' | translate }} *</label>
            <p-autoComplete [(ngModel)]="opening.product" [suggestions]="productSugg()"
                            (completeMethod)="searchProducts($event)" field="name"
                            [forceSelection]="true" [minLength]="1"
                            (onSelect)="onOpeningProductSelect()" (onClear)="opening.variantId = null"
                            [placeholder]="'stock.searchPlaceholder' | translate"
                            appendTo="body" inputStyleClass="w-full"
                            [styleClass]="'w-full' + (openingProductInvalid() ? ' ng-invalid ng-dirty' : '')">
              <ng-template let-p pTemplate="item">
                <div class="flex justify-between items-center gap-3">
                  <span>{{ p.name }}</span>
                  <span class="font-mono text-xs text-gray-500">{{ p.sku }}</span>
                </div>
              </ng-template>
            </p-autoComplete>
            @if (openingProductInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          @if (openingVariantOptions().length > 1) {
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'stock.fields.variant' | translate }} *</label>
              <p-dropdown [(ngModel)]="opening.variantId" [options]="openingVariantOptions()"
                          optionLabel="label" optionValue="id" appendTo="body"
                          [styleClass]="'w-full' + (openingVariantInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (openingVariantInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
          }
          @if (opening.product?.trackExpiry) {
            <div class="p-3 bg-amber-50 border border-amber-200 rounded space-y-3">
              <p class="text-xs text-amber-700">{{ 'stock.openingDialog.lotHelp' | translate }}</p>
              <div>
                <label class="block text-sm font-medium mb-1">{{ 'stock.fields.lotNumber' | translate }} *</label>
                <input pInputText [(ngModel)]="opening.lotNumber" class="w-full"
                       [class.ng-invalid]="openingLotInvalid()" [class.ng-dirty]="openingLotInvalid()" />
                @if (openingLotInvalid()) {
                  <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
                }
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-sm font-medium mb-1">{{ 'stock.fields.expirationDate' | translate }} *</label>
                  <input pInputText type="date" [(ngModel)]="opening.expirationDate" class="w-full"
                         [class.ng-invalid]="openingExpiryInvalid()" [class.ng-dirty]="openingExpiryInvalid()" />
                  @if (openingExpiryInvalid()) {
                    <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
                  }
                </div>
                <div>
                  <label class="block text-sm font-medium mb-1">{{ 'stock.fields.productionDate' | translate }}</label>
                  <input pInputText type="date" [(ngModel)]="opening.productionDate" class="w-full" />
                </div>
              </div>
            </div>
          }
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'stock.fields.qty' | translate }} *</label>
              <p-inputNumber [(ngModel)]="opening.qty" mode="decimal" [minFractionDigits]="0"
                             [maxFractionDigits]="3" [min]="0.000001"
                             [styleClass]="'w-full' + (openingQtyInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (openingQtyInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.mustBePositive' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'stock.fields.unitCost' | translate }} *</label>
              <p-inputNumber [(ngModel)]="opening.unitCost" mode="decimal" [minFractionDigits]="0"
                             [maxFractionDigits]="2" [min]="0"
                             [styleClass]="'w-full' + (openingCostInvalid() ? ' ng-invalid ng-dirty' : '')" />
              @if (openingCostInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.mustBeNonNegative' | translate }}</p>
              }
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'stock.fields.note' | translate }}</label>
            <input pInputText [(ngModel)]="opening.note" class="w-full" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="openingOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="saveOpening()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Ajustement dialog -->
      <p-dialog [(visible)]="adjustOpen" [modal]="true" [style]="{ width: '480px' }"
                [header]="'stock.adjustDialog.title' | translate" [closable]="!saving()">
        @if (adjustTarget()) {
          <div class="mb-3 p-3 bg-gray-50 rounded text-sm">
            <div><span class="text-gray-500">{{ 'stock.fields.product' | translate }} :</span>
              <span class="font-medium ml-2">{{ adjustTarget()!.productName }}</span>
              <span class="font-mono text-xs text-gray-500 ml-2">({{ adjustTarget()!.variantSku || adjustTarget()!.sku }})</span>
              @if (variantLabel(adjustTarget()!)) {
                <span class="text-xs text-gray-500 ml-2">· {{ variantLabel(adjustTarget()!) }}</span>
              }
            </div>
            <div class="mt-1"><span class="text-gray-500">{{ 'stock.qtyOnHand' | translate }} :</span>
              <span class="font-semibold ml-2">{{ adjustTarget()!.qtyOnHand | number:'1.0-3' }}</span>
            </div>
          </div>
        }
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'stock.fields.qtySigned' | translate }} *</label>
            <p-inputNumber [(ngModel)]="adjust.qtySigned" mode="decimal" [minFractionDigits]="0"
                           [maxFractionDigits]="3"
                           [styleClass]="'w-full' + (adjustQtyInvalid() ? ' ng-invalid ng-dirty' : '')"
                           [showButtons]="true" buttonLayout="horizontal"
                           incrementButtonIcon="pi pi-plus" decrementButtonIcon="pi pi-minus" />
            @if (adjustQtyInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'stock.errors.qtyMustBeNonZero' | translate }}</p>
            } @else {
              <p class="text-xs text-gray-500 mt-1">{{ 'stock.adjustDialog.help' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'stock.fields.note' | translate }} *</label>
            <input pInputText [(ngModel)]="adjust.note" class="w-full"
                   [placeholder]="'stock.adjustDialog.notePlaceholder' | translate"
                   [class.ng-invalid]="adjustNoteInvalid()" [class.ng-dirty]="adjustNoteInvalid()" />
            @if (adjustNoteInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="adjustOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="saveAdjust()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Historique des mouvements -->
      <p-dialog [(visible)]="historyOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="('stock.historyDialog.title' | translate) + (historyTarget() ? ' — ' + historyTarget()!.productName : '')">
        @if (historyTarget()) {
          <div class="mb-3 p-3 bg-gray-50 rounded text-sm flex gap-4 flex-wrap">
            <div><span class="text-gray-500">{{ 'stock.sku' | translate }} :</span>
              <span class="font-mono ml-2">{{ historyTarget()!.variantSku || historyTarget()!.sku }}</span>
              @if (variantLabel(historyTarget()!)) {
                <span class="text-gray-500 ml-2">· {{ variantLabel(historyTarget()!) }}</span>
              }
            </div>
            <div><span class="text-gray-500">{{ 'stock.qtyOnHand' | translate }} :</span>
              <span class="font-semibold ml-2">{{ historyTarget()!.qtyOnHand | number:'1.0-3' }}</span>
            </div>
            <div><span class="text-gray-500">{{ 'stock.avgCost' | translate }} :</span>
              <span class="font-semibold ml-2">{{ historyTarget()!.averageCost | number:'1.0-2' }}</span>
            </div>
          </div>
        }
        <p-table [value]="historyRows()" [loading]="historyLoading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'stock.historyDialog.date' | translate }}</th>
              <th>{{ 'stock.historyDialog.type' | translate }}</th>
              <th class="text-right">{{ 'stock.historyDialog.qty' | translate }}</th>
              <th class="text-right">{{ 'stock.historyDialog.unitCost' | translate }}</th>
              <th>{{ 'stock.historyDialog.reference' | translate }}</th>
              <th>{{ 'stock.fields.note' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-m>
            <tr>
              <td class="whitespace-nowrap text-sm">{{ m.occurredAt | date:'dd/MM/yyyy HH:mm' }}</td>
              <td>
                <p-tag [value]="'stock.movementType.' + m.type | translate"
                       [severity]="movementSeverity(m.type)" />
              </td>
              <td class="text-right font-semibold"
                  [class.text-green-600]="m.qtySigned > 0" [class.text-red-600]="m.qtySigned < 0">
                {{ m.qtySigned > 0 ? '+' : '' }}{{ m.qtySigned | number:'1.0-3' }}
              </td>
              <td class="text-right text-gray-600">
                {{ m.unitCost == null ? '—' : (m.unitCost | number:'1.0-2') }}
              </td>
              <td class="text-sm">
                @if (m.referenceNumber) {
                  <span class="font-mono">{{ m.referenceNumber }}</span>
                } @else { — }
              </td>
              <td class="text-sm text-gray-600">{{ m.note || '—' }}</td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">
              {{ 'stock.historyDialog.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.close' | translate" class="p-button-text"
                  (click)="historyOpen = false"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class StockListPage implements OnInit {
  private http = inject(HttpClient);
  private toast = inject(MessageService);
  private i18n = inject(TranslateService);
  private auth = inject(AUTH_SERVICE);

  protected rows = signal<StockRow[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  protected productSugg = signal<ProductOpt[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected selectedWarehouseId: string | null = null;

  protected openingOpen = false;
  protected adjustOpen = false;
  protected adjustTarget = signal<StockRow | null>(null);
  protected submittedOpening = signal(false);
  protected submittedAdjust = signal(false);

  protected historyOpen = false;
  protected historyTarget = signal<StockRow | null>(null);
  protected historyRows = signal<StockMovement[]>([]);
  protected historyLoading = signal(false);

  protected opening = this.emptyOpening();
  protected adjust = this.emptyAdjust();

  protected readonly canAdjust = this.auth.hasPermission('stock:adjust');

  protected openingWarehouseInvalid(): boolean { return this.submittedOpening() && !this.opening.warehouseId; }
  protected openingProductInvalid(): boolean { return this.submittedOpening() && !this.opening.product; }
  protected openingQtyInvalid(): boolean {
    return this.submittedOpening() && (this.opening.qty == null || this.opening.qty <= 0);
  }
  protected openingCostInvalid(): boolean {
    return this.submittedOpening() && (this.opening.unitCost == null || this.opening.unitCost < 0);
  }
  protected openingLotInvalid(): boolean {
    return this.submittedOpening() && !!this.opening.product?.trackExpiry && !this.opening.lotNumber?.trim();
  }
  protected openingExpiryInvalid(): boolean {
    return this.submittedOpening() && !!this.opening.product?.trackExpiry && !this.opening.expirationDate;
  }
  protected adjustQtyInvalid(): boolean {
    return this.submittedAdjust() && (this.adjust.qtySigned == null || this.adjust.qtySigned === 0);
  }
  protected adjustNoteInvalid(): boolean {
    return this.submittedAdjust() && !this.adjust.note?.trim();
  }

  ngOnInit() {
    this.loadWarehouses();
    this.load();
  }

  protected async load() {
    this.loading.set(true);
    try {
      const params = this.selectedWarehouseId
        ? `?warehouseId=${this.selectedWarehouseId}`
        : '';
      const list = await firstValueFrom(
        this.http.get<StockRow[]>(`/api/v1/reports/stock${params}`)
      );
      this.rows.set(list ?? []);
    } catch { this.rows.set([]); }
    finally { this.loading.set(false); }
  }

  protected async openOpening() {
    this.opening = this.emptyOpening();
    this.submittedOpening.set(false);
    this.openingOpen = true;
    if (this.warehouses().length === 0) await this.loadWarehouses();
    this.opening.warehouseId = this.selectedWarehouseId
      ?? (this.warehouses().find((w) => w.defaultWarehouse) ?? this.warehouses()[0])?.id
      ?? null;
  }

  protected openAdjust(row: StockRow) {
    this.adjustTarget.set(row);
    this.adjust = this.emptyAdjust();
    this.submittedAdjust.set(false);
    this.adjustOpen = true;
  }

  /** Resolve a product's default (or first active) variant — stock is kept per variant. */
  private async fetchDefaultVariantId(productId: string): Promise<string | null> {
    try {
      const p = await firstValueFrom(this.http.get<{ variants: ProductVariantOpt[] }>(`/api/v1/products/${productId}`));
      const vs = p.variants ?? [];
      return (vs.find(v => v.defaultVariant && v.active) ?? vs.find(v => v.active) ?? vs[0])?.id ?? null;
    } catch {
      return null;
    }
  }

  private variantIdOf(product: ProductOpt | null): string | null {
    const vs = product?.variants ?? [];
    return (vs.find(v => v.defaultVariant && v.active) ?? vs.find(v => v.active) ?? vs[0])?.id ?? null;
  }

  /** Default the variant to the product's default (or first active) one when a product is picked. */
  protected onOpeningProductSelect() {
    this.opening.variantId = this.variantIdOf(this.opening.product);
  }

  /** Active variants of the selected product, labelled by their attribute combination. */
  protected openingVariantOptions(): { id: string; label: string }[] {
    return (this.opening.product?.variants ?? [])
      .filter(v => v.active)
      .map(v => ({ id: v.id, label: this.variantOptLabel(v) }));
  }

  private variantOptLabel(v: ProductVariantOpt): string {
    return this.attributesLabel(v.attributes) || v.sku || this.i18n.instant('products.variants.defaultVariant');
  }

  /** Variant combination for a stock row, e.g. "Taille: L · Couleur: Rouge" ('' if none). */
  protected variantLabel(r: StockRow): string {
    return this.attributesLabel(r.attributes);
  }

  private attributesLabel(attributes: string | null): string {
    if (!attributes) return '';
    try {
      return Object.entries(JSON.parse(attributes) as Record<string, string>)
        .map(([name, value]) => `${name}: ${value}`).join(' · ');
    } catch {
      return '';
    }
  }

  protected openingVariantInvalid(): boolean {
    return this.submittedOpening() && this.openingVariantOptions().length > 1 && !this.opening.variantId;
  }

  protected async openHistory(row: StockRow) {
    this.historyTarget.set(row);
    this.historyRows.set([]);
    this.historyOpen = true;
    this.historyLoading.set(true);
    try {
      const variantId = row.variantId ?? await this.fetchDefaultVariantId(row.productId);
      const res = await firstValueFrom(
        this.http.get<{ content: StockMovement[] }>(
          `/api/v1/inventory/stocks/movements?variantId=${variantId}&warehouseId=${row.warehouseId}`
        )
      );
      this.historyRows.set(res?.content ?? []);
    } catch {
      this.historyRows.set([]);
    } finally {
      this.historyLoading.set(false);
    }
  }

  protected movementSeverity(type: string): TagSeverity {
    switch (type) {
      case 'PURCHASE_RECEIPT':
      case 'OPENING_BALANCE':
      case 'SALE_RETURN':
      case 'TRANSFER_IN':
        return 'success';
      case 'SALE':
      case 'TRANSFER_OUT':
      case 'EXPIRY_DESTRUCTION':
        return 'danger';
      case 'ADJUSTMENT':
      case 'INVENTORY_COUNT':
        return 'warning';
      default:
        return 'secondary';
    }
  }

  protected async searchProducts(event: { query: string }) {
    const q = (event.query ?? '').trim();
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: ProductOpt[] }>(
          `/api/v1/products?size=20${q ? `&q=${encodeURIComponent(q)}` : ''}`
        )
      );
      this.productSugg.set(res.content ?? []);
    } catch {
      this.productSugg.set([]);
    }
  }

  protected async saveOpening() {
    this.submittedOpening.set(true);
    if (this.openingWarehouseInvalid() || this.openingProductInvalid() || this.openingVariantInvalid()
        || this.openingQtyInvalid() || this.openingCostInvalid()
        || this.openingLotInvalid() || this.openingExpiryInvalid()) return;
    this.saving.set(true);
    try {
      const product = this.opening.product!;
      if (product.trackExpiry) {
        // Expiry-tracked: create the lot AND post the opening stock in one call so the
        // Stock row and the lot ledger stay in sync (otherwise FEFO has nothing to consume).
        await firstValueFrom(this.http.post('/api/v1/lots/opening-balance', {
          warehouseId: this.opening.warehouseId,
          variantId: this.opening.variantId ?? this.variantIdOf(product),
          quantity: this.opening.qty,
          unitCost: this.opening.unitCost,
          lotNumber: this.opening.lotNumber.trim(),
          expirationDate: this.opening.expirationDate,
          productionDate: this.opening.productionDate || null,
          notes: this.opening.note || null,
        }));
      } else {
        await firstValueFrom(this.http.post('/api/v1/inventory/stocks/receive', {
          warehouseId: this.opening.warehouseId,
          variantId: this.opening.variantId ?? this.variantIdOf(product),
          qty: this.opening.qty,
          unitCost: this.opening.unitCost,
          type: 'OPENING_BALANCE',
          note: this.opening.note || null,
        }));
      }
      this.toast.add({
        severity: 'success',
        summary: this.i18n.instant('common.success'),
        detail: this.i18n.instant('stock.savedOpening'),
      });
      this.openingOpen = false;
      this.load();
    } catch (e) {
      this.toastError(this.errorDetail(e));
    } finally {
      this.saving.set(false);
    }
  }

  protected async saveAdjust() {
    this.submittedAdjust.set(true);
    const target = this.adjustTarget();
    if (!target) return;
    if (this.adjustQtyInvalid() || this.adjustNoteInvalid()) return;
    this.saving.set(true);
    try {
      const adjustVariantId = target.variantId ?? await this.fetchDefaultVariantId(target.productId);
      await firstValueFrom(this.http.post('/api/v1/inventory/stocks/adjust', {
        warehouseId: target.warehouseId,
        variantId: adjustVariantId,
        qtySigned: this.adjust.qtySigned,
        type: 'ADJUSTMENT',
        note: this.adjust.note,
      }));
      this.toast.add({
        severity: 'success',
        summary: this.i18n.instant('common.success'),
        detail: this.i18n.instant('stock.savedAdjust'),
      });
      this.adjustOpen = false;
      this.load();
    } catch (e) {
      this.toastError(this.errorDetail(e));
    } finally {
      this.saving.set(false);
    }
  }

  private async loadWarehouses() {
    try {
      const list = await firstValueFrom(
        this.http.get<WarehouseLite[]>('/api/v1/inventory/warehouses')
      );
      this.warehouses.set(list ?? []);
    } catch { this.warehouses.set([]); }
  }

  private toastError(detail: string) {
    this.toast.add({
      severity: 'error',
      summary: this.i18n.instant('common.error'),
      detail,
      life: 5000,
    });
  }

  private errorDetail(e: unknown): string {
    if (e instanceof HttpErrorResponse && e.error?.message) return e.error.message;
    return this.i18n.instant('common.error_generic');
  }

  private emptyOpening() {
    return {
      warehouseId: null as string | null,
      product: null as ProductOpt | null,
      variantId: null as string | null,
      qty: null as number | null,
      unitCost: null as number | null,
      note: '',
      lotNumber: '',
      expirationDate: '',
      productionDate: '',
    };
  }

  private emptyAdjust() {
    return {
      qtySigned: null as number | null,
      note: '',
    };
  }
}
