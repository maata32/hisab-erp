import { Component, OnInit, ViewChild, ElementRef, inject, signal } from '@angular/core';
import { Table, TableLazyLoadEvent } from 'primeng/table';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { AUTH_SERVICE } from '@hisaberp/shared-auth';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { DropdownModule } from 'primeng/dropdown';
import { MultiSelectModule } from 'primeng/multiselect';
import { TooltipModule } from 'primeng/tooltip';
import { OverlayPanelModule, OverlayPanel } from 'primeng/overlaypanel';
import { MasterDataNamePipe } from '@hisaberp/shared-i18n';
import { firstValueFrom } from 'rxjs';

interface ProductImageDto {
  id: string;
  url: string;
  position: number;
  altText: string | null;
}

interface ProductVariantLite {
  id: string;
  sku: string;
  barcode: string | null;
  attributes: string | null;
  attributeValueIds: string[];
  defaultVariant: boolean;
  active: boolean;
  price?: number | null;   // UI-only: per-variant selling price (default tier + base UoM)
}

/** One editable opening-stock line, one per variant that will be generated on create. */
interface OpeningRow {
  key: string;            // sorted attribute-value ids ('' for the default variant)
  label: string;          // human-readable combination, e.g. "Rouge / M"
  qty: number | null;
  unitCost: number | null;
}

/** One editable per-variant selling-price line, used on create when pricing is not uniform. */
interface PriceRow {
  key: string;
  label: string;
  price: number | null;
}

interface AttributeValueLite {
  id: string;
  attributeId: string;
  value: string;
  active: boolean;
}

interface AttributeLite {
  id: string;
  name: string;
  active: boolean;
  values: AttributeValueLite[];
}

interface Product {
  id: string;
  sku: string;
  barcode: string | null;
  name: string;
  description: string | null;
  baseUomId: string;
  defaultTaxRate: number;
  tracksLots: boolean;
  trackExpiry: boolean;
  shelfLifeDays: number | null;
  sellable: boolean;
  purchasable: boolean;
  active: boolean;
  uniformPricing: boolean;
  attributeValueIds: string[];
  variants: ProductVariantLite[];
  images: ProductImageDto[];
}

interface UomLite { id: string; code: string; name: string; }
interface WarehouseLite { id: string; code: string; name: string; defaultWarehouse: boolean; }
interface PriceTier { id: string; code: string; name: string; defaultTier: boolean; active: boolean; }
interface ProductPrice {
  id: string;
  variantId: string;
  productId: string;
  uomId: string;
  priceTierId: string;
  amount: number;
  currency: string;
  taxInclusive: boolean;
}

interface PlanLimits {
  maxUsers: number | null;
  maxProducts: number | null;
  maxCashRegisters: number | null;
  maxProductImages: number | null;
}

interface PendingImage {
  id: string;
  file: File;
  previewUrl: string;
}

const CURRENCY = 'MRU';

@Component({
  selector: 'erp-admin-product-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, InputTextModule, DialogModule, InputNumberModule,
    CheckboxModule, DropdownModule, MultiSelectModule, TooltipModule, OverlayPanelModule,
    MasterDataNamePipe,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'products.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'products.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'products.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <div class="mb-3 flex items-center justify-between gap-4 flex-wrap">
          <span class="relative block w-full sm:w-72">
            <i class="pi pi-search absolute start-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"></i>
            <input pInputText type="text" [placeholder]="'common.search' | translate"
                   [attr.aria-label]="'common.search' | translate"
                   (input)="onSearch($event)" class="w-full !ps-9" />
          </span>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <p-checkbox [(ngModel)]="showInactive" [binary]="true"
                        (onChange)="reload()" />
            {{ 'products.showInactive' | translate }}
          </label>
        </div>

        <p-table #table [value]="products()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th style="width:48px"></th>
              <th>{{ 'products.sku' | translate }}</th>
              <th>{{ 'products.name' | translate }}</th>
              <th>{{ 'products.barcode' | translate }}</th>
              @if (taxEnabled()) {
                <th>{{ 'products.tax' | translate }}</th>
              }
              <th>{{ 'products.expiry' | translate }}</th>
              <th>{{ 'products.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-p>
            <tr>
              <td>
                @if (p.images?.length) {
                  <button type="button"
                          class="relative inline-flex items-center justify-center w-8 h-8 rounded
                                 text-gray-600 hover:bg-gray-100 hover:text-primary-600"
                          [pTooltip]="'products.viewImages' | translate"
                          (click)="showImages($event, p, imagesOp)">
                    <i class="pi pi-images text-base"></i>
                    <span class="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 rounded-full
                                 bg-primary-500 text-white text-[10px] font-semibold
                                 flex items-center justify-center">
                      {{ p.images.length }}
                    </span>
                  </button>
                }
              </td>
              <td><span class="font-mono text-sm">{{ p.sku }}</span></td>
              <td class="font-medium">{{ p.name }}</td>
              <td>{{ p.barcode || '—' }}</td>
              @if (taxEnabled()) {
                <td class="text-right">{{ p.defaultTaxRate * 100 | number:'1.0-1' }} %</td>
              }
              <td>
                @if (p.trackExpiry) {
                  <p-tag [value]="'products.tracksExpiry' | translate" severity="warning" />
                  <span class="text-xs text-gray-500 ml-2">
                    @if (p.shelfLifeDays) { {{ p.shelfLifeDays }} j }
                  </span>
                } @else { <span class="text-gray-300">—</span> }
              </td>
              <td>
                <p-tag [value]="(p.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="p.active ? 'success' : 'secondary'" />
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                        [pTooltip]="'common.edit' | translate"
                        (click)="openEdit(p)"></button>
                @if (p.active) {
                  <button pButton icon="pi pi-ban" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.deactivate' | translate"
                          (click)="toggleActive(p)"></button>
                } @else {
                  <button pButton icon="pi pi-check-circle" class="p-button-sm p-button-text p-button-success"
                          [pTooltip]="'common.activate' | translate"
                          (click)="toggleActive(p)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td [attr.colspan]="taxEnabled() ? 8 : 7" class="text-center text-gray-400 py-8">
              {{ 'products.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-overlayPanel #imagesOp [showCloseIcon]="false" [dismissable]="true" styleClass="!p-2">
        <div class="grid grid-cols-3 gap-2 max-w-[280px]">
          @for (img of popoverImages(); track img.id) {
            <a [href]="img.url" target="_blank" rel="noopener" class="block">
              <img [src]="img.url" [alt]="img.altText || ''"
                   class="w-20 h-20 object-cover rounded border border-gray-200 hover:border-primary-400" />
            </a>
          }
        </div>
      </p-overlayPanel>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '600px' }"
                [header]="(editingId() ? 'products.editTitle' : 'products.createTitle') | translate"
                [closable]="!saving()">
        <div class="space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'products.sku' | translate }} *</label>
              <input pInputText [(ngModel)]="form.sku" [disabled]="!!editingId()"
                     class="w-full" [class.ng-invalid]="skuInvalid()" [class.ng-dirty]="skuInvalid()" />
              @if (skuInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'products.errors.skuRequired' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'products.barcode' | translate }}</label>
              <input pInputText [(ngModel)]="form.barcode" class="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full"
                   [class.ng-invalid]="nameInvalid()" [class.ng-dirty]="nameInvalid()" />
            @if (nameInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'products.errors.nameRequired' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.baseUom' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.baseUomId" [options]="uoms()" optionLabel="name"
                        optionValue="id" [filter]="true" filterBy="name,code"
                        [placeholder]="'common.select' | translate"
                        [styleClass]="'w-full' + (baseUomInvalid() ? ' ng-invalid ng-dirty' : '')">
              <ng-template let-u pTemplate="selectedItem">{{ u.code | mdName:'uoms.unit':u.name }}</ng-template>
              <ng-template let-u pTemplate="item">{{ u.code | mdName:'uoms.unit':u.name }}</ng-template>
            </p-dropdown>
            @if (baseUomInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'products.errors.baseUomRequired' | translate }}</p>
            }
          </div>
          <div class="grid gap-3" [ngClass]="priceGridCols()">
            @if (form.uniformPricing) {
              <div>
                <label class="block text-sm font-medium mb-1">{{ 'products.price' | translate }}</label>
                <p-inputNumber [(ngModel)]="form.sellingPrice" mode="decimal" [minFractionDigits]="0"
                               [maxFractionDigits]="2" [suffix]="' ' + currencyLabel" styleClass="w-full" />
                @if (defaultTierName()) {
                  <p class="text-xs text-gray-400 mt-1">
                    {{ 'products.priceHint' | translate:{ tier: defaultTierName() } }}
                  </p>
                }
              </div>
            }
            @if (taxEnabled()) {
              <div>
                <label class="block text-sm font-medium mb-1">{{ 'products.tax' | translate }}</label>
                <p-inputNumber [(ngModel)]="form.taxRatePercent" mode="decimal" [maxFractionDigits]="2"
                               suffix=" %" styleClass="w-full" />
              </div>
            }
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'products.shelfLifeDays' | translate }}</label>
              <p-inputNumber [(ngModel)]="form.shelfLifeDays" styleClass="w-full" />
            </div>
          </div>
          <div class="flex items-center gap-4 pt-1 flex-wrap">
            <p-checkbox [(ngModel)]="form.trackExpiry" [binary]="true"
                        [label]="'products.trackExpiry' | translate" />
            <p-checkbox [(ngModel)]="form.sellable" [binary]="true"
                        [label]="'products.sellable' | translate" />
            <p-checkbox [(ngModel)]="form.purchasable" [binary]="true"
                        [label]="'products.purchasable' | translate" />
          </div>

          <div class="pt-2 border-t border-gray-100 space-y-3">
            <div class="flex items-center justify-between">
              <label class="block text-sm font-medium">{{ 'products.variants.title' | translate }}</label>
              <p-checkbox [(ngModel)]="form.uniformPricing" [binary]="true"
                          [label]="'products.variants.uniformPricing' | translate" />
            </div>
            <p class="text-xs text-gray-500">{{ 'products.variants.help' | translate }}</p>
            <div class="flex items-end gap-2">
              <div class="flex-1">
                <label class="block text-xs text-gray-600 mb-1">{{ 'products.variants.attributes' | translate }}</label>
                <p-multiSelect [(ngModel)]="form.attributeValueIds" [options]="attributeValueOptions()"
                               [group]="true" optionGroupLabel="label" optionGroupChildren="items"
                               optionLabel="label" optionValue="value" appendTo="body"
                               [placeholder]="'products.variants.selectValues' | translate"
                               styleClass="w-full" [filter]="true" display="chip"
                               (onChange)="onAttributeSelectionChange()" />
              </div>
              @if (editingId() && generatingVariants()) {
                <i class="pi pi-spin pi-spinner text-gray-400 mb-3"></i>
              }
            </div>

            @if (editingId()) {
              @if (dialogVariants().length) {
                <div class="border rounded overflow-hidden">
                  <table class="w-full text-sm">
                    <thead class="bg-gray-50 text-gray-500 text-xs">
                      <tr>
                        <th class="text-left p-2">{{ 'products.variants.combination' | translate }}</th>
                        <th class="text-left p-2">{{ 'products.sku' | translate }}</th>
                        <th class="text-left p-2">{{ 'products.barcode' | translate }}</th>
                        @if (!form.uniformPricing) {
                          <th class="text-left p-2">{{ 'products.price' | translate }}</th>
                        }
                        <th class="p-2">{{ 'common.active' | translate }}</th>
                        <th class="p-2"></th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (v of dialogVariants(); track v.id) {
                        <tr class="border-t" [class.opacity-50]="!v.active">
                          <td class="p-2 whitespace-nowrap">{{ variantAttrLabel(v) }}</td>
                          <td class="p-2"><input pInputText [(ngModel)]="v.sku" class="w-full p-inputtext-sm" /></td>
                          <td class="p-2"><input pInputText [(ngModel)]="v.barcode" class="w-full p-inputtext-sm" /></td>
                          @if (!form.uniformPricing) {
                            <td class="p-2">
                              <p-inputNumber [(ngModel)]="v.price" mode="decimal" [minFractionDigits]="0"
                                             [maxFractionDigits]="2" [min]="0" [suffix]="' ' + currencyLabel"
                                             styleClass="w-full p-inputtext-sm" />
                            </td>
                          }
                          <td class="p-2 text-center"><p-checkbox [(ngModel)]="v.active" [binary]="true" /></td>
                          <td class="p-2 text-right">
                            <button pButton type="button" icon="pi pi-check"
                                    class="p-button-xs p-button-text"
                                    [attr.aria-label]="'common.confirm' | translate"
                                    [loading]="savingVariantId() === v.id"
                                    (click)="saveVariantRow(v)"></button>
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              } @else {
                <p class="text-xs text-gray-400">{{ 'products.variants.none' | translate }}</p>
              }
            } @else {
              @if (!form.uniformPricing) {
                <label class="block text-xs text-gray-600 mb-1">{{ 'products.variants.perVariantPricing' | translate }}</label>
                <div class="border rounded overflow-hidden">
                  <table class="w-full text-sm">
                    <thead class="bg-gray-50 text-gray-500 text-xs">
                      <tr>
                        <th class="text-left p-2">{{ 'products.variants.combination' | translate }}</th>
                        <th class="text-left p-2">{{ 'products.price' | translate }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (row of priceRows(); track row.key) {
                        <tr class="border-t">
                          <td class="p-2 whitespace-nowrap">{{ row.label }}</td>
                          <td class="p-2">
                            <p-inputNumber [(ngModel)]="row.price" mode="decimal" [minFractionDigits]="0"
                                           [maxFractionDigits]="2" [min]="0" [suffix]="' ' + currencyLabel"
                                           [styleClass]="'w-full p-inputtext-sm' + (priceRowInvalid(row) ? ' ng-invalid ng-dirty' : '')" />
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
                @if (anyPriceInvalid()) {
                  <p class="text-xs text-red-600 mt-1">{{ 'products.errors.priceRequired' | translate }}</p>
                }
              }
              <p class="text-xs text-gray-400 mt-2">{{ 'products.variants.createHint' | translate }}</p>
            }
          </div>

          @if (!editingId() && canAdjustStock) {
            <div class="pt-2 border-t border-gray-100">
              <p-checkbox [(ngModel)]="form.openingStockEnabled" [binary]="true"
                          [label]="'products.openingStock.toggle' | translate" />
              @if (form.openingStockEnabled) {
                <p class="text-xs text-gray-500 mt-2 mb-2">{{ 'products.openingStock.help' | translate }}</p>
                <div class="grid gap-3 mb-3" [ngClass]="form.uniformPricing ? 'grid-cols-2' : 'grid-cols-1'">
                  <div class="max-w-xs">
                    <label class="block text-xs text-gray-600 mb-1">{{ 'stock.fields.warehouse' | translate }} *</label>
                    <p-dropdown [(ngModel)]="form.openingWarehouseId" [options]="warehouses()"
                                optionLabel="name" optionValue="id" appendTo="body"
                                [styleClass]="'w-full' + (openingWarehouseInvalid() ? ' ng-invalid ng-dirty' : '')" />
                    @if (openingWarehouseInvalid()) {
                      <p class="text-xs text-red-600 mt-1">{{ 'products.openingStock.errors.warehouseRequired' | translate }}</p>
                    }
                  </div>
                  @if (form.uniformPricing) {
                    <div class="max-w-xs">
                      <label class="block text-xs text-gray-600 mb-1">{{ 'stock.fields.unitCost' | translate }} *</label>
                      <p-inputNumber [(ngModel)]="form.openingSharedCost" mode="decimal" [minFractionDigits]="0"
                                     [maxFractionDigits]="2" [min]="0" [suffix]="' ' + currencyLabel"
                                     [styleClass]="'w-full' + (openingSharedCostInvalid() ? ' ng-invalid ng-dirty' : '')" />
                      @if (openingSharedCostInvalid()) {
                        <p class="text-xs text-red-600 mt-1">{{ 'products.openingStock.errors.costRequired' | translate }}</p>
                      }
                    </div>
                  }
                </div>
                <div class="border rounded overflow-hidden">
                  <table class="w-full text-sm">
                    <thead class="bg-gray-50 text-gray-500 text-xs">
                      <tr>
                        <th class="text-left p-2">{{ 'products.variants.combination' | translate }}</th>
                        <th class="text-left p-2">{{ 'stock.fields.qty' | translate }}</th>
                        @if (!form.uniformPricing) {
                          <th class="text-left p-2">{{ 'stock.fields.unitCost' | translate }}</th>
                        }
                      </tr>
                    </thead>
                    <tbody>
                      @for (row of openingRows(); track row.key) {
                        <tr class="border-t">
                          <td class="p-2 whitespace-nowrap">{{ row.label }}</td>
                          <td class="p-2">
                            <p-inputNumber [(ngModel)]="row.qty" mode="decimal" [minFractionDigits]="0"
                                           [maxFractionDigits]="3" [min]="0" styleClass="w-full p-inputtext-sm" />
                          </td>
                          @if (!form.uniformPricing) {
                            <td class="p-2">
                              <p-inputNumber [(ngModel)]="row.unitCost" mode="decimal" [minFractionDigits]="0"
                                             [maxFractionDigits]="2" [min]="0"
                                             [styleClass]="'w-full p-inputtext-sm' + (rowCostInvalid(row) ? ' ng-invalid ng-dirty' : '')" />
                            </td>
                          }
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
                @if (openingNoRowsInvalid()) {
                  <p class="text-xs text-red-600 mt-1">{{ 'products.openingStock.errors.qtyRequired' | translate }}</p>
                }
                @if (openingAnyCostInvalid()) {
                  <p class="text-xs text-red-600 mt-1">{{ 'products.openingStock.errors.costRequired' | translate }}</p>
                }
              }
            </div>
          }

          <div class="pt-2 border-t border-gray-100">
            <div class="flex items-center justify-between mb-2">
              <label class="block text-sm font-medium">{{ 'products.images' | translate }}</label>
              <span class="text-xs text-gray-500">{{ imageCountLabel() }}</span>
            </div>
            <div class="flex items-center gap-2 flex-wrap">
              @for (img of dialogImages(); track img.id) {
                <div class="relative group">
                  <img [src]="img.url" [alt]="img.altText || ''"
                       class="w-20 h-20 object-cover rounded border border-gray-200" />
                  <button type="button"
                          class="absolute -top-2 -right-2 w-6 h-6 rounded-full bg-red-500 text-white
                                 text-xs opacity-0 group-hover:opacity-100 transition"
                          [title]="'common.delete' | translate"
                          [attr.aria-label]="'common.remove' | translate"
                          (click)="removeImage(img)">
                    <i class="pi pi-times"></i>
                  </button>
                </div>
              }
              @for (queued of pendingImages(); track queued.id) {
                <div class="relative group">
                  <img [src]="queued.previewUrl" [alt]="queued.file.name"
                       class="w-20 h-20 object-cover rounded border border-dashed border-primary-300" />
                  <button type="button"
                          class="absolute -top-2 -right-2 w-6 h-6 rounded-full bg-red-500 text-white
                                 text-xs opacity-0 group-hover:opacity-100 transition"
                          [title]="'common.delete' | translate"
                          [attr.aria-label]="'common.remove' | translate"
                          (click)="removePending(queued)">
                    <i class="pi pi-times"></i>
                  </button>
                </div>
              }
              @if (canAddMoreImages()) {
                <button type="button"
                        class="w-20 h-20 rounded border-2 border-dashed border-gray-300 text-gray-400
                               hover:border-primary-400 hover:text-primary-500 flex items-center justify-center"
                        [disabled]="uploading()"
                        (click)="fileInput.click()">
                  @if (uploading()) {
                    <i class="pi pi-spin pi-spinner"></i>
                  } @else {
                    <i class="pi pi-plus text-xl"></i>
                  }
                </button>
              }
            </div>
            @if (!canAddMoreImages() && maxImages() != null) {
              <p class="text-xs text-amber-600 mt-2">
                {{ 'products.imagesQuotaReached' | translate:{ max: maxImages() } }}
              </p>
            }
            <input #fileInput type="file" accept="image/*" multiple hidden
                   (change)="onPickFiles($event)" />
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
export class ProductListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private auth = inject(AUTH_SERVICE);

  protected readonly canAdjustStock = this.auth.hasPermission('stock:adjust');

  protected products = signal<Product[]>([]);
  protected uoms = signal<UomLite[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  protected submitted = signal(false);
  @ViewChild('table') private productTable?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected uploading = signal(false);
  protected dialogOpen = false;
  protected showInactive = false;
  protected editingId = signal<string | null>(null);
  protected taxEnabled = signal(true);
  protected dialogImages = signal<ProductImageDto[]>([]);
  protected pendingImages = signal<PendingImage[]>([]);
  protected maxImages = signal<number | null>(null);
  protected popoverImages = signal<ProductImageDto[]>([]);

  protected defaultTierId: string | null = null;
  protected defaultTierName = signal<string | null>(null);
  protected attributes = signal<AttributeLite[]>([]);
  protected dialogVariants = signal<ProductVariantLite[]>([]);
  protected openingRows = signal<OpeningRow[]>([]);
  protected priceRows = signal<PriceRow[]>([]);
  protected generatingVariants = signal(false);
  protected savingVariantId = signal<string | null>(null);
  protected readonly currencyLabel = CURRENCY;
  protected lastQuery: string | undefined;
  private pendingSeq = 0;

  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

  protected form: {
    sku: string;
    name: string;
    barcode: string;
    baseUomId: string | null;
    taxRatePercent: number;
    shelfLifeDays: number | null;
    sellingPrice: number | null;
    trackExpiry: boolean;
    sellable: boolean;
    purchasable: boolean;
    uniformPricing: boolean;
    attributeValueIds: string[];
    openingStockEnabled: boolean;
    openingWarehouseId: string | null;
    openingSharedCost: number | null;
  } = this.emptyForm();

  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private regenTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    // Products are fetched on demand via the p-table's onLazyLoad.
    this.loadUoms();
    this.loadSettings();
    this.loadDefaultTier();
    this.loadLimits();
    this.loadAttributes();
    if (this.canAdjustStock) this.loadWarehouses();
  }

  protected canAddMoreImages(): boolean {
    const max = this.maxImages();
    if (max == null) return true;
    return this.dialogImages().length + this.pendingImages().length < max;
  }

  protected imageCountLabel(): string {
    const cur = this.dialogImages().length + this.pendingImages().length;
    const max = this.maxImages();
    return max == null ? `${cur}` : `${cur} / ${max}`;
  }

  protected onSearch(e: Event) {
    const q = (e.target as HTMLInputElement).value;
    this.lastQuery = q;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.reload(), 300);
  }

  protected async openCreate() {
    if (this.regenTimer) clearTimeout(this.regenTimer);
    this.editingId.set(null);
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.dialogImages.set([]);
    this.dialogVariants.set([]);
    this.rebuildVariantRows();
    this.clearPendingImages();
    this.dialogOpen = true;
    if (this.canAdjustStock) {
      if (this.warehouses().length === 0) await this.loadWarehouses();
      this.form.openingWarehouseId =
        (this.warehouses().find((w) => w.defaultWarehouse) ?? this.warehouses()[0])?.id ?? null;
    }
  }

  protected async openEdit(p: Product) {
    if (this.regenTimer) clearTimeout(this.regenTimer);
    this.editingId.set(p.id);
    this.form = {
      sku: p.sku,
      name: p.name,
      barcode: p.barcode ?? '',
      baseUomId: p.baseUomId,
      taxRatePercent: (p.defaultTaxRate ?? 0) * 100,
      shelfLifeDays: p.shelfLifeDays,
      sellingPrice: null,
      trackExpiry: p.trackExpiry,
      sellable: p.sellable,
      purchasable: p.purchasable,
      uniformPricing: p.uniformPricing ?? true,
      attributeValueIds: [...(p.attributeValueIds ?? [])],
      openingStockEnabled: false,
      openingWarehouseId: null,
      openingSharedCost: null,
    };
    this.submitted.set(false);
    this.dialogImages.set(p.images ?? []);
    this.dialogVariants.set([...(p.variants ?? [])]);
    this.openingRows.set([]);
    this.priceRows.set([]);
    this.clearPendingImages();
    this.dialogOpen = true;
    await this.loadCurrentPrice(p);
  }

  protected showImages(event: Event, p: Product, op: OverlayPanel) {
    this.popoverImages.set(p.images ?? []);
    op.toggle(event);
  }

  protected toggleActive(p: Product) {
    const verb = p.active ? 'Désactiver' : 'Activer';
    this.confirmation.confirm({
      message: `${verb} le produit « ${p.name} » ?`,
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: p.active ? 'p-button-sm p-button-danger' : 'p-button-sm',
      accept: async () => {
        await firstValueFrom(this.http.patch(`/api/v1/products/${p.id}`, { active: !p.active }));
        this.reload();
      },
    });
  }

  protected async save() {
    this.submitted.set(true);
    if (this.regenTimer) clearTimeout(this.regenTimer);
    if (!this.form.sku || !this.form.name || !this.form.baseUomId) return;
    if (!this.editingId() && this.form.openingStockEnabled && !this.validateOpeningStockFields()) return;
    if (!this.editingId() && !this.form.uniformPricing && !this.validateVariantPrices()) return;
    this.saving.set(true);
    try {
      const taxRate = this.taxEnabled() ? (this.form.taxRatePercent ?? 0) / 100 : 0;
      const id = this.editingId();
      let productId: string;
      let createdVariants: ProductVariantLite[] = [];
      if (id) {
        await firstValueFrom(this.http.patch(`/api/v1/products/${id}`, {
          name: this.form.name,
          barcode: this.form.barcode || null,
          baseUomId: this.form.baseUomId,
          defaultTaxRate: taxRate,
          trackExpiry: this.form.trackExpiry,
          shelfLifeDays: this.form.shelfLifeDays,
          sellable: this.form.sellable,
          purchasable: this.form.purchasable,
          uniformPricing: this.form.uniformPricing,
        }));
        // Regenerate the variant matrix from the current attribute-value selection.
        await firstValueFrom(this.http.put(`/api/v1/products/${id}/attributes`,
          { attributeValueIds: this.form.attributeValueIds }));
        productId = id;
      } else {
        const created = await firstValueFrom(this.http.post<Product>('/api/v1/products', {
          sku: this.form.sku,
          name: this.form.name,
          barcode: this.form.barcode || null,
          baseUomId: this.form.baseUomId,
          defaultTaxRate: taxRate,
          trackExpiry: this.form.trackExpiry,
          shelfLifeDays: this.form.shelfLifeDays,
          sellable: this.form.sellable,
          purchasable: this.form.purchasable,
          uniformPricing: this.form.uniformPricing,
          attributeValueIds: this.form.attributeValueIds,
        }));
        productId = created.id;
        createdVariants = created.variants ?? [];
      }
      if (this.form.uniformPricing) {
        await this.savePriceIfChanged(productId);
      } else if (!id) {
        await this.saveVariantPrices(createdVariants);
      }
      if (!id) await this.saveOpeningStockRows(createdVariants);
      await this.uploadPendingImages(productId);
      this.dialogOpen = false;
      this.clearPendingImages();
      this.reload();
    } finally {
      this.saving.set(false);
    }
  }

  protected onPickFiles(e: Event) {
    const input = e.target as HTMLInputElement;
    if (!input.files?.length) return;
    const id = this.editingId();
    for (const file of Array.from(input.files)) {
      if (!this.canAddMoreImages()) break;
      if (id) {
        // edit mode: upload immediately
        this.uploadOne(id, file);
      } else {
        // create mode: queue locally, upload after POST
        this.pendingImages.update(list => [...list, {
          id: `pending-${++this.pendingSeq}`,
          file,
          previewUrl: URL.createObjectURL(file),
        }]);
      }
    }
    input.value = '';
  }

  private async uploadOne(productId: string, file: File): Promise<ProductImageDto | null> {
    this.uploading.set(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const img = await firstValueFrom(
        this.http.post<ProductImageDto>(`/api/v1/products/${productId}/images/upload`, fd)
      );
      this.dialogImages.update(list => [...list, img]);
      this.reload();
      return img;
    } catch {
      return null;
    } finally {
      this.uploading.set(false);
    }
  }

  private async uploadPendingImages(productId: string) {
    const pending = this.pendingImages();
    if (!pending.length) return;
    this.uploading.set(true);
    try {
      for (const queued of pending) {
        const fd = new FormData();
        fd.append('file', queued.file);
        try {
          await firstValueFrom(
            this.http.post<ProductImageDto>(`/api/v1/products/${productId}/images/upload`, fd)
          );
        } catch {
          // skip failures (quota etc.) — server-side validation will return error to surface elsewhere
        }
      }
    } finally {
      this.uploading.set(false);
    }
  }

  protected removePending(queued: PendingImage) {
    URL.revokeObjectURL(queued.previewUrl);
    this.pendingImages.update(list => list.filter(p => p.id !== queued.id));
  }

  private clearPendingImages() {
    for (const p of this.pendingImages()) URL.revokeObjectURL(p.previewUrl);
    this.pendingImages.set([]);
  }

  protected removeImage(img: ProductImageDto) {
    const id = this.editingId();
    if (!id) return;
    this.confirmation.confirm({
      message: 'Supprimer cette image ?',
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.delete(`/api/v1/products/${id}/images/${img.id}`));
        this.dialogImages.update(list => list.filter(i => i.id !== img.id));
        this.reload();
      },
    });
  }

  private async savePriceIfChanged(productId: string) {
    const price = this.form.sellingPrice;
    if (price == null || !this.defaultTierId || !this.form.baseUomId) return;
    if (price < 0) return;
    await firstValueFrom(this.http.put(`/api/v1/pricing/products/${productId}`, {
      uomId: this.form.baseUomId,
      priceTierId: this.defaultTierId,
      amount: price,
      currency: CURRENCY,
      taxInclusive: false,
    }));
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(rows));
    if (this.lastQuery) params.set('q', this.lastQuery);
    if (this.showInactive) params.set('includeInactive', 'true');
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Product[]; totalElements: number }>(`/api/v1/products?${params}`)
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.products()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.products.set(arr);
      this.total.set(totalElements);
    } catch {
      this.products.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.products.set([]);
    this.total.set(0);
    this.productTable?.reset();
  }

  private async loadUoms() {
    try {
      const list = await firstValueFrom(this.http.get<UomLite[]>('/api/v1/uoms'));
      this.uoms.set(list ?? []);
    } catch { this.uoms.set([]); }
  }

  private async loadSettings() {
    try {
      const settings = await firstValueFrom(
        this.http.get<{ invoiceSettings?: { taxEnabled?: boolean } }>('/api/v1/settings')
      );
      const enabled = settings?.invoiceSettings?.taxEnabled;
      if (typeof enabled === 'boolean') this.taxEnabled.set(enabled);
    } catch {
      // keep default (true) on failure
    }
  }

  private async loadLimits() {
    try {
      const limits = await firstValueFrom(
        this.http.get<PlanLimits>('/api/v1/organizations/me/limits')
      );
      this.maxImages.set(limits?.maxProductImages ?? null);
    } catch {
      this.maxImages.set(null);
    }
  }

  private async loadDefaultTier() {
    try {
      const tiers = await firstValueFrom(this.http.get<PriceTier[]>('/api/v1/pricing/tiers'));
      const def = tiers.find(t => t.defaultTier && t.active) ?? tiers.find(t => t.active);
      if (def) {
        this.defaultTierId = def.id;
        const key = `priceTiers.label.${def.code}`;
        const label = this.i18n.instant(key);
        this.defaultTierName.set(label === key ? def.name : label);
      }
    } catch {
      this.defaultTierId = null;
      this.defaultTierName.set(null);
    }
  }

  private async loadCurrentPrice(p: Product) {
    if (!this.defaultTierId) return;
    try {
      const prices = await firstValueFrom(
        this.http.get<ProductPrice[]>(`/api/v1/pricing/products/${p.id}`)
      );
      const forDefault = prices.filter(
        pr => pr.priceTierId === this.defaultTierId && pr.uomId === p.baseUomId);
      if (forDefault.length) this.form.sellingPrice = forDefault[0].amount;
      this.mapVariantPrices(forDefault);
    } catch {
      // no prices yet — leave fields empty
    }
  }

  /** Re-read per-variant prices into the variant table (after the matrix is regenerated). */
  private async reloadVariantPrices(productId: string) {
    if (!this.defaultTierId || !this.form.baseUomId) return;
    try {
      const prices = await firstValueFrom(
        this.http.get<ProductPrice[]>(`/api/v1/pricing/products/${productId}`)
      );
      this.mapVariantPrices(prices.filter(
        pr => pr.priceTierId === this.defaultTierId && pr.uomId === this.form.baseUomId));
    } catch {
      // ignore
    }
  }

  private mapVariantPrices(forDefault: ProductPrice[]) {
    const byVariant = new Map(forDefault.map(pr => [pr.variantId, pr.amount]));
    this.dialogVariants.update(list =>
      list.map(v => ({ ...v, price: byVariant.get(v.id) ?? null })));
  }

  private emptyForm() {
    return {
      sku: '', name: '', barcode: '', baseUomId: null,
      taxRatePercent: 16, shelfLifeDays: null, sellingPrice: null,
      trackExpiry: false, sellable: true, purchasable: true,
      uniformPricing: true, attributeValueIds: [] as string[],
      openingStockEnabled: false,
      openingWarehouseId: null, openingSharedCost: null,
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

  /** Post one OPENING_BALANCE movement per variant that was given a positive quantity. */
  private async saveOpeningStockRows(variants: ProductVariantLite[]) {
    if (!this.form.openingStockEnabled) return;
    const byKey = new Map(variants.map((v) => [this.variantKey(v), v]));
    for (const row of this.openingRows()) {
      if (row.qty == null || row.qty <= 0) continue;
      const variant = byKey.get(row.key);
      if (!variant) continue;
      // Uniform pricing → one shared cost for every variant; otherwise the row's own cost.
      const unitCost = this.form.uniformPricing ? this.form.openingSharedCost : row.unitCost;
      await firstValueFrom(this.http.post('/api/v1/inventory/stocks/receive', {
        warehouseId: this.form.openingWarehouseId,
        variantId: variant.id,
        qty: row.qty,
        unitCost,
        type: 'OPENING_BALANCE',
        note: 'Stock initial saisi à la création du produit',
      }));
    }
  }

  /** Attribute selection changed: refresh the create-mode rows, and in edit mode
   *  auto-regenerate the variant matrix (debounced) so the table never goes stale. */
  protected onAttributeSelectionChange() {
    this.rebuildVariantRows();
    if (this.editingId()) {
      if (this.regenTimer) clearTimeout(this.regenTimer);
      this.regenTimer = setTimeout(() => this.generateVariants(), 500);
    }
  }

  /** Recompute the per-variant opening-stock AND price rows from the current attribute
   *  selection, preserving any quantities/costs/prices already typed. */
  protected rebuildVariantRows() {
    const combos = this.buildVariantCombos();
    const prevOpen = new Map(this.openingRows().map((r) => [r.key, r]));
    this.openingRows.set(combos.map((c) => ({
      key: c.key,
      label: c.label,
      qty: prevOpen.get(c.key)?.qty ?? null,
      unitCost: prevOpen.get(c.key)?.unitCost ?? null,
    })));
    const prevPrice = new Map(this.priceRows().map((r) => [r.key, r]));
    this.priceRows.set(combos.map((c) => ({
      key: c.key,
      label: c.label,
      price: prevPrice.get(c.key)?.price ?? null,
    })));
  }

  /** Per-variant pricing on create: write each variant's price to the default tier + base UoM. */
  private async saveVariantPrices(variants: ProductVariantLite[]) {
    if (!this.defaultTierId || !this.form.baseUomId) return;
    const byKey = new Map(variants.map((v) => [this.variantKey(v), v]));
    for (const row of this.priceRows()) {
      if (row.price == null || row.price < 0) continue;
      const variant = byKey.get(row.key);
      if (!variant) continue;
      await firstValueFrom(this.http.put(`/api/v1/pricing/variants/${variant.id}`, {
        uomId: this.form.baseUomId,
        priceTierId: this.defaultTierId,
        amount: row.price,
        currency: CURRENCY,
        taxInclusive: false,
      }));
    }
  }

  /** Cartesian product of the selected attribute values, mirroring backend generation.
   *  With no attribute selected the product keeps a single default variant. */
  private buildVariantCombos(): { key: string; label: string }[] {
    const selected = new Set(this.form.attributeValueIds);
    if (selected.size === 0) {
      return [{ key: '', label: this.i18n.instant('products.variants.defaultVariant') }];
    }
    const axes = this.attributes()
      .map((a) => ({ name: a.name, values: (a.values ?? []).filter((v) => v.active && selected.has(v.id)) }))
      .filter((axis) => axis.values.length > 0);
    let combos: { id: string; label: string }[][] = [[]];
    for (const axis of axes) {
      const next: { id: string; label: string }[][] = [];
      for (const prefix of combos) {
        for (const v of axis.values) next.push([...prefix, { id: v.id, label: `${axis.name}: ${v.value}` }]);
      }
      combos = next;
    }
    return combos.map((combo) => ({
      key: combo.map((c) => c.id).slice().sort().join(','),
      label: combo.map((c) => c.label).join(' · '),
    }));
  }

  /** Stable key for a generated variant: its sorted attribute-value ids ('' for default). */
  private variantKey(v: ProductVariantLite): string {
    return (v.attributeValueIds ?? []).slice().sort().join(',');
  }

  private async loadAttributes() {
    try {
      const list = await firstValueFrom(this.http.get<AttributeLite[]>('/api/v1/attributes'));
      this.attributes.set((list ?? []).filter((a) => a.active));
    } catch {
      this.attributes.set([]);
    }
  }

  /** Flat "Attribute: Value" options for the attribute-value multiselect, grouped by attribute. */
  protected attributeValueOptions() {
    return this.attributes().map((a) => ({
      label: a.name,
      value: a.id,
      items: (a.values ?? []).filter((v) => v.active).map((v) => ({
        label: `${a.name}: ${v.value}`,
        value: v.id,
      })),
    }));
  }

  protected variantAttrLabel(v: ProductVariantLite): string {
    if (!v.attributes) return this.i18n.instant('products.variants.defaultVariant');
    try {
      const map = JSON.parse(v.attributes) as Record<string, string>;
      return Object.entries(map).map(([name, value]) => `${name}: ${value}`).join(' · ');
    } catch {
      return '—';
    }
  }

  /** Persist the current attribute selection and refresh the generated variant matrix. */
  protected async generateVariants() {
    const id = this.editingId();
    if (!id) return;
    this.generatingVariants.set(true);
    try {
      const updated = await firstValueFrom(this.http.put<Product>(
        `/api/v1/products/${id}/attributes`, { attributeValueIds: this.form.attributeValueIds }));
      this.dialogVariants.set([...(updated.variants ?? [])]);
      if (!this.form.uniformPricing) await this.reloadVariantPrices(id);
    } finally {
      this.generatingVariants.set(false);
    }
  }

  protected async saveVariantRow(v: ProductVariantLite) {
    const id = this.editingId();
    if (!id) return;
    this.savingVariantId.set(v.id);
    try {
      await firstValueFrom(this.http.patch(`/api/v1/products/${id}/variants/${v.id}`, {
        sku: v.sku, barcode: v.barcode || null, active: v.active,
      }));
      if (!this.form.uniformPricing && v.price != null && v.price >= 0
          && this.defaultTierId && this.form.baseUomId) {
        await firstValueFrom(this.http.put(`/api/v1/pricing/variants/${v.id}`, {
          uomId: this.form.baseUomId,
          priceTierId: this.defaultTierId,
          amount: v.price,
          currency: CURRENCY,
          taxInclusive: false,
        }));
      }
    } finally {
      this.savingVariantId.set(null);
    }
  }

  private validateOpeningStockFields(): boolean {
    if (this.openingWarehouseInvalid() || this.openingNoRowsInvalid()) return false;
    return this.form.uniformPricing ? !this.openingSharedCostInvalid() : !this.openingAnyCostInvalid();
  }

  private hasOpeningQty(): boolean {
    return this.openingRows().some((r) => (r.qty ?? 0) > 0);
  }

  protected skuInvalid(): boolean {
    return this.submitted() && !this.form.sku;
  }

  protected nameInvalid(): boolean {
    return this.submitted() && !this.form.name;
  }

  protected baseUomInvalid(): boolean {
    return this.submitted() && !this.form.baseUomId;
  }

  protected openingWarehouseInvalid(): boolean {
    return this.submitted() && this.form.openingStockEnabled && !this.form.openingWarehouseId;
  }

  /** Per-variant cost (non-uniform only): a positive-quantity row without a valid cost. */
  protected rowCostInvalid(row: OpeningRow): boolean {
    if (!this.submitted() || !this.form.openingStockEnabled || this.form.uniformPricing) return false;
    return (row.qty ?? 0) > 0 && (row.unitCost == null || row.unitCost < 0);
  }

  /** At least one variant must carry a positive opening quantity. */
  protected openingNoRowsInvalid(): boolean {
    if (!this.submitted() || !this.form.openingStockEnabled) return false;
    return !this.hasOpeningQty();
  }

  /** Non-uniform: any positive-quantity row missing a valid unit cost. */
  protected openingAnyCostInvalid(): boolean {
    if (!this.submitted() || !this.form.openingStockEnabled || this.form.uniformPricing) return false;
    return this.openingRows().some((r) => (r.qty ?? 0) > 0 && (r.unitCost == null || r.unitCost < 0));
  }

  /** Uniform: the single shared cost is required once any variant has a positive quantity. */
  protected openingSharedCostInvalid(): boolean {
    if (!this.submitted() || !this.form.openingStockEnabled || !this.form.uniformPricing) return false;
    return this.hasOpeningQty() && (this.form.openingSharedCost == null || this.form.openingSharedCost < 0);
  }

  /** Non-uniform pricing (create): every variant must carry a valid selling price. */
  private validateVariantPrices(): boolean {
    return !this.anyPriceInvalid();
  }

  protected priceRowInvalid(row: PriceRow): boolean {
    if (!this.submitted() || this.form.uniformPricing) return false;
    return row.price == null || row.price < 0;
  }

  protected anyPriceInvalid(): boolean {
    if (!this.submitted() || this.form.uniformPricing) return false;
    return this.priceRows().some((r) => r.price == null || r.price < 0);
  }

  /** Number of columns in the price/tax/shelf-life grid, depending on what is shown. */
  protected priceGridCols(): string {
    const n = (this.form.uniformPricing ? 1 : 0) + (this.taxEnabled() ? 1 : 0) + 1;
    return n >= 3 ? 'grid-cols-3' : n === 2 ? 'grid-cols-2' : 'grid-cols-1';
  }

}
