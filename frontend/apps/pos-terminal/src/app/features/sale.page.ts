import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { OverlayPanelModule, OverlayPanel } from 'primeng/overlaypanel';
import { MessageService } from 'primeng/api';
import { Router } from '@angular/router';

import { CatalogCacheService } from '../services/catalog-cache.service';
import { SessionService } from '../services/session.service';
import { SyncService } from '../services/sync.service';
import { ReceiptService } from '../services/receipt.service';
import { PosApiService, PosLot } from '../services/pos-api.service';
import { OnlineStatusService } from '../services/online-status.service';
import { CachedProduct, CachedProductImage, CartLine, PendingSale, SyncedSale } from '../models/pos.models';
import { PosSettingsService } from '../services/pos-settings.service';

@Component({
  selector: 'pos-sale-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    ButtonModule,
    DialogModule,
    InputNumberModule,
    InputTextModule,
    ToastModule,
    OverlayPanelModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast position="top-center" />

    @if (!sessionSvc.isOpen()) {
      <div class="flex flex-col items-center justify-center h-full text-center p-8">
        <i class="pi pi-exclamation-circle text-5xl text-yellow-500 mb-4"></i>
        <h2 class="text-xl font-bold mb-2">{{ 'pos.sale.no_session.title' | translate }}</h2>
        <p class="text-gray-600 mb-4">{{ 'pos.sale.no_session.description' | translate }}</p>
        <button pButton [label]="'pos.sale.go_to_session' | translate" icon="pi pi-arrow-right"
          (click)="goToSession()"></button>
      </div>
    }

    @if (sessionSvc.isOpen()) {
      <div class="h-full flex flex-col overflow-hidden">

        @if (!online.isOnline()) {
          <div class="bg-yellow-500 text-white text-sm px-4 py-2 flex items-center gap-2 shrink-0">
            <i class="pi pi-wifi"></i>
            <span>{{ 'pos.offline_banner' | translate }}</span>
          </div>
        }

        <div class="flex-1 flex overflow-hidden">

          <!-- Products panel -->
          <div class="flex-1 flex flex-col overflow-hidden border-e border-gray-200">
            <div class="p-3 bg-white border-b flex gap-2 shrink-0">
              <input
                pInputText
                type="text"
                [(ngModel)]="searchQuery"
                (ngModelChange)="onSearch($event)"
                [placeholder]="'pos.sale.search_placeholder' | translate"
                class="flex-1"
              />
              @if (searchQuery) {
                <button pButton icon="pi pi-times" severity="secondary" text
                  [attr.aria-label]="'pos.sale.clear_search' | translate"
                  (click)="clearSearch()"></button>
              }
            </div>

            @if (loadingProducts()) {
              <div class="flex-1 flex items-center justify-center">
                <i class="pi pi-spin pi-spinner text-3xl text-primary-600"></i>
              </div>
            } @else if (filteredProducts().length === 0) {
              <div class="flex-1 flex items-center justify-center text-gray-400 text-center p-8">
                <div>
                  <i class="pi pi-search text-4xl mb-2 block"></i>
                  <p>{{ 'pos.sale.no_products' | translate }}</p>
                </div>
              </div>
            } @else {
              <div class="flex-1 overflow-y-auto p-3">
                <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                  @for (product of filteredProducts(); track product.id) {
                    <button
                      type="button"
                      (click)="addToCart(product)"
                      class="relative bg-white rounded-lg shadow p-3 text-left hover:bg-primary-50 border border-gray-200 hover:border-primary-400 transition-colors min-h-[80px] flex flex-col justify-between"
                    >
                      @if (product.images?.length) {
                        <span
                          role="button"
                          tabindex="0"
                          class="absolute top-1.5 right-1.5 inline-flex items-center justify-center w-7 h-7 rounded
                                 bg-white/90 text-gray-600 hover:bg-primary-100 hover:text-primary-700 shadow-sm"
                          [attr.aria-label]="'pos.sale.view_images' | translate"
                          [title]="'pos.sale.view_images' | translate"
                          (click)="showImages($event, product, imagesOp)"
                        >
                          <i class="pi pi-images text-sm"></i>
                          <span class="absolute -top-1 -right-1 min-w-[14px] h-3.5 px-1 rounded-full
                                       bg-primary-500 text-white text-[9px] font-semibold
                                       flex items-center justify-center">
                            {{ product.images.length }}
                          </span>
                        </span>
                      }
                      <div class="font-medium text-sm text-gray-800 line-clamp-2 leading-tight pr-8">{{ product.name }}</div>
                      <div class="mt-2 flex items-end justify-between">
                        <span class="text-xs text-gray-400">
                          {{ product.sku }}
                          <span class="ml-1"
                                [class.text-red-600]="stockOf(product.id) <= 0"
                                [class.font-semibold]="stockOf(product.id) <= 0">
                            · {{ 'pos.sale.stock' | translate }} {{ stockOf(product.id) }}
                          </span>
                        </span>
                        <span class="text-primary-700 font-bold text-sm">{{ fmtSvc.format(product.price) }}</span>
                      </div>
                    </button>
                  }
                </div>
              </div>
            }
          </div>

          <p-overlayPanel #imagesOp [showCloseIcon]="false" [dismissable]="true" styleClass="!p-2">
            <div class="grid grid-cols-3 gap-2 max-w-[280px]">
              @for (img of popoverImages(); track img.id) {
                <a [href]="img.url" target="_blank" rel="noopener" class="block">
                  <img [src]="img.url" alt=""
                       class="w-20 h-20 object-cover rounded border border-gray-200 hover:border-primary-400" />
                </a>
              }
            </div>
          </p-overlayPanel>

          <!-- Cart panel -->
          <div class="w-64 lg:w-80 flex flex-col bg-white shrink-0">
            <div class="p-3 border-b flex items-center justify-between shrink-0">
              <h3 class="font-bold text-gray-800">{{ 'pos.sale.cart' | translate }} ({{ cartLines().length }})</h3>
              @if (cartLines().length > 0) {
                <button pButton icon="pi pi-trash" severity="danger" text size="small"
                  [attr.aria-label]="'pos.sale.clear_cart' | translate"
                  (click)="clearCart()"></button>
              }
            </div>

            <div class="flex-1 overflow-y-auto">
              @if (cartLines().length === 0) {
                <div class="p-6 text-center text-gray-400">
                  <i class="pi pi-shopping-cart text-3xl mb-2 block"></i>
                  <p class="text-sm">{{ 'pos.sale.cart_empty' | translate }}</p>
                </div>
              }
              @for (line of cartLines(); track line.productId) {
                <div class="border-b p-3">
                  <div class="flex items-start justify-between gap-1 mb-2">
                    <p class="text-sm font-medium flex-1 leading-tight line-clamp-2">{{ line.productName }}</p>
                    <button pButton icon="pi pi-times" severity="danger" text size="small" class="shrink-0 -mt-1"
                      [attr.aria-label]="'pos.sale.remove_line' | translate"
                      (click)="removeLine(line)"></button>
                  </div>
                  <div class="flex items-center justify-between">
                    <div class="flex items-center gap-1">
                      <button pButton icon="pi pi-minus" severity="secondary" text size="small"
                        [attr.aria-label]="'pos.sale.decrease_qty' | translate"
                        (click)="changeQty(line, -1)"></button>
                      <span class="w-8 text-center text-sm font-bold">{{ line.quantity }}</span>
                      <button pButton icon="pi pi-plus" severity="secondary" text size="small"
                        [attr.aria-label]="'pos.sale.increase_qty' | translate"
                        (click)="changeQty(line, 1)"></button>
                    </div>
                    <span class="text-primary-700 font-bold text-sm">
                      {{ fmtSvc.format(lineTotal(line)) }}
                    </span>
                  </div>
                  @if (line.tracksLots && online.isOnline() && lotsFor(line.variantId).length) {
                    <div class="mt-2 flex items-center gap-1.5">
                      <i class="pi pi-tag text-xs text-gray-400 shrink-0"></i>
                      <select
                        class="flex-1 text-xs rounded border border-gray-300 px-2 py-1 bg-white"
                        [value]="line.lotId ?? ''"
                        (change)="onLotChange(line, $any($event.target).value)"
                      >
                        <option value="">{{ 'pos.sale.lot_fefo' | translate }}</option>
                        @for (lot of lotsFor(line.variantId); track lot.id) {
                          <option [value]="lot.id">
                            {{ lot.lotNumber }} · {{ lot.expirationDate }} · {{ lot.quantityRemaining }}
                          </option>
                        }
                      </select>
                    </div>
                  }
                </div>
              }
            </div>

            <div class="border-t p-3 bg-gray-50 shrink-0">
              <div class="flex justify-between text-sm text-gray-600 mb-1">
                <span>{{ 'pos.sale.subtotal' | translate }}</span>
                <span>{{ fmtSvc.format(cartSubtotal()) }}</span>
              </div>
              <div class="flex justify-between font-bold text-base mb-3">
                <span>{{ 'pos.sale.total' | translate }}</span>
                <span class="text-primary-700">{{ fmtSvc.format(cartTotal()) }} MRU</span>
              </div>
              <button
                pButton
                [label]="'pos.sale.pay' | translate"
                icon="pi pi-credit-card"
                [disabled]="cartLines().length === 0 || saving()"
                (click)="openPayment()"
                class="w-full"
                size="large"
              ></button>
            </div>
          </div>
        </div>
      </div>
    }

    <!-- Payment dialog -->
    <p-dialog
      [header]="'pos.sale.payment.title' | translate"
      [(visible)]="showPayment"
      [modal]="true"
      [style]="{ width: '26rem' }"
      [closable]="!saving()"
    >
      <div class="space-y-3 pt-2">
        <div class="bg-gray-50 rounded p-3 flex justify-between font-bold text-lg">
          <span>{{ 'pos.sale.total' | translate }}</span>
          <span class="text-primary-700">{{ fmtSvc.format(cartTotal()) }} MRU</span>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">{{ 'pos.sale.payment.cash' | translate }}</label>
            <p-inputNumber [ngModel]="payCash()" (ngModelChange)="payCash.set($event ?? 0)" [min]="0" [minFractionDigits]="fmtSvc.decimalPlaces()" [maxFractionDigits]="fmtSvc.decimalPlaces()" styleClass="w-full" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">{{ 'pos.sale.payment.card' | translate }}</label>
            <p-inputNumber [ngModel]="payCard()" (ngModelChange)="payCard.set($event ?? 0)" [min]="0" [minFractionDigits]="fmtSvc.decimalPlaces()" [maxFractionDigits]="fmtSvc.decimalPlaces()" styleClass="w-full" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">{{ 'pos.sale.payment.mobile' | translate }}</label>
            <p-inputNumber [ngModel]="payMobile()" (ngModelChange)="payMobile.set($event ?? 0)" [min]="0" [minFractionDigits]="fmtSvc.decimalPlaces()" [maxFractionDigits]="fmtSvc.decimalPlaces()" styleClass="w-full" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">{{ 'pos.sale.payment.credit' | translate }}</label>
            <p-inputNumber [ngModel]="payCredit()" (ngModelChange)="payCredit.set($event ?? 0)" [min]="0" [minFractionDigits]="fmtSvc.decimalPlaces()" [maxFractionDigits]="fmtSvc.decimalPlaces()" styleClass="w-full" />
          </div>
        </div>
        @if (totalPaid() > 0) {
          <div class="bg-blue-50 rounded p-3 text-sm">
            <div class="flex justify-between">
              <span>{{ 'pos.sale.payment.paid' | translate }}</span>
              <span class="font-medium">{{ fmtSvc.format(totalPaid()) }} MRU</span>
            </div>
            @if (changeDue() > 0) {
              <div class="flex justify-between text-green-700 font-bold mt-1">
                <span>{{ 'pos.sale.payment.change' | translate }}</span>
                <span>{{ fmtSvc.format(changeDue()) }} MRU</span>
              </div>
            }
          </div>
        }
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" severity="secondary" text
          (click)="showPayment = false"></button>
        <button pButton [label]="'pos.sale.payment.confirm' | translate" icon="pi pi-check"
          [disabled]="!canPay() || saving()" [loading]="saving()"
          (click)="confirmSale()"></button>
      </ng-template>
    </p-dialog>

    <!-- Receipt / success dialog -->
    <p-dialog
      [header]="'pos.sale.receipt.title' | translate"
      [(visible)]="showReceipt"
      [modal]="true"
      [style]="{ width: '22rem' }"
      [closable]="true"
      [dismissableMask]="true"
      (onHide)="onReceiptHide()"
    >
      <div class="text-center py-4">
        <i class="pi pi-check-circle text-5xl text-green-500 mb-3 block"></i>
        <p class="font-bold text-lg">{{ 'pos.sale.receipt.success' | translate }}</p>
        @if (lastSaleNumber()) {
          <p class="text-gray-500 text-sm mt-1">#{{ lastSaleNumber() }}</p>
        }
        @if (changeDueDisplay() > 0) {
          <div class="bg-green-50 rounded p-3 mt-3">
            <p class="text-sm text-gray-600">{{ 'pos.sale.payment.change' | translate }}</p>
            <p class="text-2xl font-bold text-green-700">{{ fmtSvc.format(changeDueDisplay()) }} MRU</p>
          </div>
        }
      </div>
      <ng-template pTemplate="footer">
        <div class="flex flex-col gap-2">
          <button pButton [label]="'pos.sale.receipt.new_sale' | translate" icon="pi pi-shopping-cart"
            class="w-full" (click)="newSale()"></button>
          <button pButton [label]="'pos.sale.receipt.print' | translate" icon="pi pi-print"
            severity="secondary" outlined class="w-full" (click)="printReceipt()"></button>
          <button pButton [label]="'common.close' | translate" icon="pi pi-times"
            severity="secondary" text class="w-full" (click)="closeReceipt()"></button>
        </div>
      </ng-template>
    </p-dialog>
  `,
})
export class SalePage implements OnInit {
  protected readonly catalogCache = inject(CatalogCacheService);
  protected readonly sessionSvc = inject(SessionService);
  private readonly sync = inject(SyncService);
  private readonly receiptSvc = inject(ReceiptService);
  private readonly api = inject(PosApiService);
  protected readonly online = inject(OnlineStatusService);
  protected readonly fmtSvc = inject(PosSettingsService);
  private readonly msg = inject(MessageService);
  private readonly i18n = inject(TranslateService);
  private readonly router = inject(Router);

  protected readonly loadingProducts = signal(false);
  protected readonly saving = signal(false);
  protected readonly allProducts = signal<CachedProduct[]>([]);
  protected readonly filteredProducts = signal<CachedProduct[]>([]);
  protected readonly cartLines = signal<CartLine[]>([]);
  protected readonly lastSaleNumber = signal<string | null>(null);
  protected readonly changeDueDisplay = signal(0);
  protected readonly popoverImages = signal<CachedProductImage[]>([]);
  protected readonly stockByProduct = signal<Record<string, number>>({});
  protected readonly lotsByVariant = signal<Record<string, PosLot[]>>({});

  protected showPayment = false;
  protected showReceipt = false;
  protected searchQuery = '';
  protected readonly payCash = signal(0);
  protected readonly payCard = signal(0);
  protected readonly payMobile = signal(0);
  protected readonly payCredit = signal(0);

  private lastSaleSynced: SyncedSale | null = null;
  private lastSalePending: PendingSale | null = null;

  protected readonly cartSubtotal = computed(() =>
    this.cartLines().reduce((s, l) => s + this.lineTotal(l), 0)
  );

  protected readonly cartTotal = computed(() => this.cartSubtotal());

  protected readonly totalPaid = computed(() =>
    (this.payCash() || 0) + (this.payCard() || 0) + (this.payMobile() || 0) + (this.payCredit() || 0)
  );

  protected readonly changeDue = computed(() =>
    Math.max(0, this.totalPaid() - this.cartTotal())
  );

  protected readonly canPay = computed(() => this.totalPaid() >= this.cartTotal() - 0.001);

  lineTotal(line: CartLine): number {
    return (line.unitPrice - (line.unitDiscount || 0)) * line.quantity;
  }

  async ngOnInit(): Promise<void> {
    await this.fmtSvc.load();
    if (!this.sessionSvc.isOpen()) return;
    this.loadingProducts.set(true);
    try {
      await this.catalogCache.refreshIfStale();
      const products = await this.catalogCache.getAllProducts();
      this.allProducts.set(products);
      this.filteredProducts.set(products);
    } finally {
      this.loadingProducts.set(false);
    }
    await this.refreshStocks();
  }

  stockOf(productId: string): number {
    return this.stockByProduct()[productId] ?? 0;
  }

  private async refreshStocks(): Promise<void> {
    const warehouseId = this.sessionSvc.currentRegister()?.warehouseId;
    if (!warehouseId) return;
    const cacheKey = `hisaberp.pos.stocks.${warehouseId}`;
    try {
      const items = await firstValueFrom(this.api.listStocksByWarehouse(warehouseId));
      const map: Record<string, number> = {};
      for (const it of items) map[it.productId] = Number(it.qtyAvailable);
      this.stockByProduct.set(map);
      try { localStorage.setItem(cacheKey, JSON.stringify(map)); } catch { /* quota */ }
    } catch {
      const raw = localStorage.getItem(cacheKey);
      if (raw) {
        try { this.stockByProduct.set(JSON.parse(raw)); } catch { /* corrupt */ }
      }
    }
  }

  onSearch(q: string): void {
    if (!q) { this.filteredProducts.set(this.allProducts()); return; }
    const lq = q.toLowerCase();
    this.filteredProducts.set(
      this.allProducts().filter(
        (p) =>
          p.name.toLowerCase().includes(lq) ||
          p.sku.toLowerCase().includes(lq) ||
          (p.barcode && p.barcode.includes(q))
      )
    );
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.filteredProducts.set(this.allProducts());
  }

  showImages(event: Event, product: CachedProduct, op: OverlayPanel): void {
    event.stopPropagation();
    this.popoverImages.set(product.images ?? []);
    op.toggle(event);
  }

  addToCart(product: CachedProduct): void {
    const lines = this.cartLines();
    const idx = lines.findIndex((l) => l.productId === product.id && l.uomId === product.baseUomId);
    if (idx >= 0) {
      const updated = [...lines];
      updated[idx] = { ...updated[idx], quantity: updated[idx].quantity + 1 };
      this.cartLines.set(updated);
    } else {
      this.cartLines.set([
        ...lines,
        {
          productId: product.id,
          variantId: product.variantId,
          productName: product.name,
          productSku: product.sku,
          uomId: product.baseUomId,
          quantity: 1,
          unitPrice: product.price,
          unitDiscount: 0,
          taxRate: product.defaultTaxRate,
          taxInclusive: product.taxInclusive,
          currency: product.currency,
          tracksLots: product.tracksLots,
          lotId: null,
          lotNumber: null,
        },
      ]);
      if (product.tracksLots) this.loadLots(product.variantId);
    }
  }

  /** Fetch ACTIVE lots for a variant (online only) so the cashier can pick one. */
  private async loadLots(variantId: string): Promise<void> {
    if (!this.online.isOnline() || this.lotsByVariant()[variantId]) return;
    try {
      const lots = await firstValueFrom(
        this.api.listLots(variantId, this.sessionSvc.currentRegister()?.warehouseId ?? ''),
      );
      this.lotsByVariant.set({ ...this.lotsByVariant(), [variantId]: lots });
    } catch { /* offline / no lots */ }
  }

  lotsFor(variantId: string): PosLot[] {
    return this.lotsByVariant()[variantId] ?? [];
  }

  onLotChange(line: CartLine, lotId: string): void {
    const lot = this.lotsFor(line.variantId).find((l) => l.id === lotId);
    this.cartLines.set(
      this.cartLines().map((l) =>
        l.productId === line.productId && l.uomId === line.uomId
          ? { ...l, lotId: lot ? lot.id : null, lotNumber: lot ? lot.lotNumber : null }
          : l,
      ),
    );
  }

  changeQty(line: CartLine, delta: number): void {
    const newQty = line.quantity + delta;
    if (newQty <= 0) { this.removeLine(line); return; }
    this.cartLines.set(
      this.cartLines().map((l) =>
        l.productId === line.productId && l.uomId === line.uomId
          ? { ...l, quantity: newQty }
          : l
      )
    );
  }

  removeLine(line: CartLine): void {
    this.cartLines.set(
      this.cartLines().filter(
        (l) => !(l.productId === line.productId && l.uomId === line.uomId)
      )
    );
  }

  clearCart(): void { this.cartLines.set([]); }

  openPayment(): void {
    this.payCash.set(this.cartTotal());
    this.payCard.set(0);
    this.payMobile.set(0);
    this.payCredit.set(0);
    this.showPayment = true;
  }

  goToSession(): void { this.router.navigateByUrl('/session'); }

  async confirmSale(): Promise<void> {
    const session = this.sessionSvc.currentSession();
    if (!session) return;
    this.saving.set(true);

    const idempotencyKey = crypto.randomUUID();
    const lines = this.cartLines().map((l) => ({
      variantId: l.variantId,
      uomId: l.uomId,
      quantity: l.quantity,
      unitDiscount: l.unitDiscount > 0 ? l.unitDiscount : null,
      lotAllocations: l.lotId ? [{ lotId: l.lotId, quantity: l.quantity }] : null,
    }));
    const payment = {
      cash: this.payCash() > 0 ? this.payCash() : null,
      card: this.payCard() > 0 ? this.payCard() : null,
      mobile: this.payMobile() > 0 ? this.payMobile() : null,
      credit: this.payCredit() > 0 ? this.payCredit() : null,
    };
    const savedChange = this.changeDue();

    try {
      if (this.online.isOnline()) {
        const sale = await firstValueFrom(this.api.createSale({
          idempotencyKey,
          registerId: session.registerId,
          sessionId: session.id,
          customerId: null,
          priceTierId: this.sessionSvc.getDefaultPriceTierId(),
          occurredAt: new Date().toISOString(),
          note: null,
          lines,
          payment,
        }));
        this.lastSaleSynced = sale;
        this.lastSaleNumber.set(sale?.number ?? null);
      } else {
        throw new Error('offline');
      }
    } catch {
      const pending: PendingSale = {
        idempotencyKey,
        registerId: session.registerId,
        sessionId: session.id,
        customerId: null,
        priceTierId: this.sessionSvc.getDefaultPriceTierId(),
        occurredAt: new Date().toISOString(),
        note: null,
        lines,
        payment,
        status: 'pending',
        syncError: null,
        createdAt: Date.now(),
        serverSaleId: null,
        serverSaleNumber: null,
      };
      const localId = await this.sync.enqueueSale(pending);
      this.lastSalePending = { ...pending, localId };
      this.lastSaleNumber.set(null);
      if (this.online.isOnline()) {
        this.msg.add({ severity: 'warn', summary: this.i18n.instant('pos.sale.networkError'), detail: this.i18n.instant('pos.sale.savedLocally'), life: 4000 });
      }
    } finally {
      this.saving.set(false);
      this.showPayment = false;
      this.clearCart();
      this.changeDueDisplay.set(savedChange);
      this.showReceipt = true;
      void this.sessionSvc.refreshSession();
    }
  }

  async printReceipt(): Promise<void> {
    const orgName = 'Hisab ERP';
    const widthMm = this.sessionSvc.getReceiptWidth();
    if (this.lastSaleSynced) {
      const data = this.receiptSvc.buildFromSynced(this.lastSaleSynced, orgName, widthMm);
      await this.receiptSvc.printEscPos(data);
    } else if (this.lastSalePending) {
      const data = this.receiptSvc.buildFromPending(this.lastSalePending, orgName, widthMm);
      this.receiptSvc.printHtml(data);
    }
  }

  newSale(): void {
    this.showReceipt = false;
    this.lastSaleSynced = null;
    this.lastSalePending = null;
    this.lastSaleNumber.set(null);
    this.changeDueDisplay.set(0);
  }

  closeReceipt(): void {
    this.showReceipt = false;
  }

  onReceiptHide(): void {
    // p-dialog (X / mask) closed: drop the last-sale handles so the next sale starts clean.
    this.lastSaleSynced = null;
    this.lastSalePending = null;
    this.lastSaleNumber.set(null);
    this.changeDueDisplay.set(0);
  }
}
