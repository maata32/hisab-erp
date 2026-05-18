import { Component, OnInit, ViewChild, ElementRef, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { DropdownModule } from 'primeng/dropdown';
import { TooltipModule } from 'primeng/tooltip';
import { OverlayPanelModule, OverlayPanel } from 'primeng/overlaypanel';
import { firstValueFrom } from 'rxjs';

interface ProductImageDto {
  id: string;
  url: string;
  position: number;
  altText: string | null;
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
  images: ProductImageDto[];
}

interface UomLite { id: string; code: string; name: string; }
interface PriceTier { id: string; code: string; name: string; defaultTier: boolean; active: boolean; }
interface ProductPrice {
  id: string;
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
    CheckboxModule, DropdownModule, TooltipModule, OverlayPanelModule,
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
            <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"></i>
            <input pInputText type="text" [placeholder]="'common.search' | translate"
                   (input)="onSearch($event)" class="w-full !pl-9" />
          </span>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <p-checkbox [(ngModel)]="showInactive" [binary]="true"
                        (onChange)="load(lastQuery)" />
            {{ 'products.showInactive' | translate }}
          </label>
        </div>

        <p-table [value]="products()" [loading]="loading()" stripedRows responsiveLayout="scroll"
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
              <input pInputText [(ngModel)]="form.sku" [disabled]="!!editingId()" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'products.barcode' | translate }}</label>
              <input pInputText [(ngModel)]="form.barcode" class="w-full" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.baseUom' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.baseUomId" [options]="uoms()" optionLabel="name"
                        optionValue="id" [filter]="true" filterBy="name,code"
                        [placeholder]="'common.select' | translate" styleClass="w-full" />
          </div>
          <div class="grid grid-cols-2 gap-3">
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
            @if (taxEnabled()) {
              <div>
                <label class="block text-sm font-medium mb-1">{{ 'products.tax' | translate }}</label>
                <p-inputNumber [(ngModel)]="form.taxRatePercent" mode="decimal" [maxFractionDigits]="2"
                               suffix=" %" styleClass="w-full" />
              </div>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.shelfLifeDays' | translate }}</label>
            <p-inputNumber [(ngModel)]="form.shelfLifeDays" styleClass="w-full" />
          </div>
          <div class="flex items-center gap-4 pt-1 flex-wrap">
            <p-checkbox [(ngModel)]="form.trackExpiry" [binary]="true"
                        [label]="'products.trackExpiry' | translate" />
            <p-checkbox [(ngModel)]="form.sellable" [binary]="true"
                        [label]="'products.sellable' | translate" />
            <p-checkbox [(ngModel)]="form.purchasable" [binary]="true"
                        [label]="'products.purchasable' | translate" />
          </div>

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

  protected products = signal<Product[]>([]);
  protected uoms = signal<UomLite[]>([]);
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
  } = this.emptyForm();

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    this.load();
    this.loadUoms();
    this.loadSettings();
    this.loadDefaultTier();
    this.loadLimits();
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
    this.searchTimer = setTimeout(() => this.load(q), 300);
  }

  protected openCreate() {
    this.editingId.set(null);
    this.form = this.emptyForm();
    this.dialogImages.set([]);
    this.clearPendingImages();
    this.dialogOpen = true;
  }

  protected async openEdit(p: Product) {
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
    };
    this.dialogImages.set(p.images ?? []);
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
        this.load(this.lastQuery);
      },
    });
  }

  protected async save() {
    if (!this.form.sku || !this.form.name || !this.form.baseUomId) return;
    this.saving.set(true);
    try {
      const taxRate = this.taxEnabled() ? (this.form.taxRatePercent ?? 0) / 100 : 0;
      const id = this.editingId();
      let productId: string;
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
        }));
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
        }));
        productId = created.id;
      }
      await this.savePriceIfChanged(productId);
      await this.uploadPendingImages(productId);
      this.dialogOpen = false;
      this.clearPendingImages();
      this.load(this.lastQuery);
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
      this.load(this.lastQuery);
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
        this.load(this.lastQuery);
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

  protected load(q?: string) {
    this.lastQuery = q;
    this.loading.set(true);
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (this.showInactive) params.set('includeInactive', 'true');
    const qs = params.toString() ? `?${params.toString()}` : '';
    firstValueFrom(this.http.get<{ content: Product[] }>(`/api/v1/products${qs}`))
      .then(res => this.products.set(res.content ?? []))
      .catch(() => this.products.set([]))
      .finally(() => this.loading.set(false));
  }

  private async loadUoms() {
    try {
      const list = await firstValueFrom(this.http.get<UomLite[]>('/api/v1/uoms'));
      this.uoms.set(list ?? []);
    } catch { this.uoms.set([]); }
  }

  private async loadSettings() {
    try {
      const settings = await firstValueFrom(this.http.get<any>('/api/v1/settings'));
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
        this.defaultTierName.set(def.name);
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
      const match = prices.find(pr => pr.priceTierId === this.defaultTierId && pr.uomId === p.baseUomId);
      if (match) this.form.sellingPrice = match.amount;
    } catch {
      // no prices yet — leave field empty
    }
  }

  private emptyForm() {
    return {
      sku: '', name: '', barcode: '', baseUomId: null,
      taxRatePercent: 16, shelfLifeDays: null, sellingPrice: null,
      trackExpiry: false, sellable: true, purchasable: true,
    };
  }
}
