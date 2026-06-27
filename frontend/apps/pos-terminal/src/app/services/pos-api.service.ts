import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { PageResponse } from '@hisaberp/shared-api';
import { CashRegister, CashSession, SyncedSale } from '../models/pos.models';

export interface OpenSessionRequest {
  registerId: string;
  openingFloat: number;
}

export interface CloseSessionRequest {
  countedClosing: number;
  note?: string;
}

export interface CreateSaleRequest {
  idempotencyKey: string;
  registerId: string;
  sessionId: string;
  customerId?: string | null;
  priceTierId?: string | null;
  occurredAt?: string;
  note?: string | null;
  lines: SaleLineRequest[];
  payment?: PaymentRequest;
}

export interface SaleLineRequest {
  variantId: string;
  uomId: string;
  quantity: number;
  unitDiscount?: number | null;
  lotAllocations?: { lotId: string; quantity: number }[] | null;
}

export interface PosLot {
  id: string;
  lotNumber: string;
  expirationDate: string | null;
  quantityRemaining: number;
  status: string;
}

export interface PaymentRequest {
  cash?: number | null;
  card?: number | null;
  mobile?: number | null;
  credit?: number | null;
}

export interface SyncRequest {
  deviceId?: string;
  sales: CreateSaleRequest[];
}

export interface SyncResult {
  idempotencyKey: string;
  status: 'ACCEPTED' | 'DUPLICATE' | 'ERROR';
  saleId: string | null;
  saleNumber: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface SyncSalesResponse {
  results: SyncResult[];
}

export interface ProductImageItem {
  id: string;
  url: string;
  position: number;
  altText: string | null;
}

export interface ProductVariantItem {
  id: string;
  defaultVariant: boolean;
  active: boolean;
}

export interface ProductPageItem {
  id: string;
  sku: string;
  barcode: string | null;
  name: string;
  baseUomId: string;
  defaultTaxRate: number;
  sellable: boolean;
  imageUrl: string | null;
  images: ProductImageItem[];
  variants: ProductVariantItem[];
}

export interface ResolvedPrice {
  productId: string;
  uomId: string;
  priceTierId: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  total: number;
  currency: string;
  taxInclusive: boolean;
}

export interface PriceTier {
  id: string;
  code: string;
  name: string;
  isDefault: boolean;
}

export interface StockSnapshotItem {
  id: string;
  warehouseId: string;
  productId: string;
  qtyOnHand: number;
  qtyReserved: number;
  qtyAvailable: number;
  averageCost: number;
}

@Injectable({ providedIn: 'root' })
export class PosApiService {
  private readonly http = inject(HttpClient);

  listRegisters(): Observable<CashRegister[]> {
    return this.http.get<CashRegister[]>('/api/v1/pos/registers');
  }

  openSession(req: OpenSessionRequest): Observable<CashSession> {
    return this.http.post<CashSession>('/api/v1/pos/sessions', req);
  }

  closeSession(id: string, req: CloseSessionRequest): Observable<CashSession> {
    return this.http.post<CashSession>(`/api/v1/pos/sessions/${id}/close`, req);
  }

  getSession(id: string): Observable<CashSession> {
    return this.http.get<CashSession>(`/api/v1/pos/sessions/${id}`);
  }

  /** Closed sessions belonging to the current cashier, awaiting vault validation. */
  listMyPendingSessions(): Observable<CashSession[]> {
    return this.http.get<CashSession[]>('/api/v1/pos/my-sessions/pending');
  }

  /** Validated sessions belonging to the current cashier on a given ISO date (YYYY-MM-DD). */
  listMyValidatedSessions(date: string): Observable<CashSession[]> {
    return this.http.get<CashSession[]>(`/api/v1/pos/my-sessions/validated?date=${date}`);
  }

  createSale(req: CreateSaleRequest): Observable<SyncedSale> {
    return this.http.post<SyncedSale>('/api/v1/pos/sales', req);
  }

  getSale(saleId: string): Observable<SyncedSale> {
    return this.http.get<SyncedSale>(`/api/v1/pos/sales/${saleId}`);
  }

  voidSale(saleId: string, reason: string | null): Observable<SyncedSale> {
    return this.http.post<SyncedSale>(`/api/v1/pos/sales/${saleId}/void`, { reason });
  }

  listSalesByRegister(
    registerId: string,
    from: string,
    to: string,
    page = 0,
    size = 50,
  ): Observable<PageResponse<SyncedSale>> {
    const params = new HttpParams()
      .set('from', from)
      .set('to', to)
      .set('page', page)
      .set('size', size)
      .set('sort', 'completedAt,desc');
    return this.http.get<PageResponse<SyncedSale>>(
      `/api/v1/pos/registers/${registerId}/sales`,
      { params },
    );
  }

  listSalesBySession(sessionId: string, page = 0, size = 200): Observable<PageResponse<SyncedSale>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'completedAt,desc');
    return this.http.get<PageResponse<SyncedSale>>(
      `/api/v1/pos/sessions/${sessionId}/sales`,
      { params },
    );
  }

  syncSales(req: SyncRequest): Observable<SyncSalesResponse> {
    return this.http.post<SyncSalesResponse>('/api/v1/pos/sales/sync', req);
  }

  searchProducts(q: string, page = 0, size = 100): Observable<PageResponse<ProductPageItem>> {
    const params = new HttpParams()
      .set('q', q)
      .set('page', page)
      .set('size', size);
    return this.http.get<PageResponse<ProductPageItem>>('/api/v1/products', { params });
  }

  listAllSellableProducts(page = 0, size = 200): Observable<PageResponse<ProductPageItem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ProductPageItem>>('/api/v1/products', { params });
  }

  listPriceTiers(): Observable<PriceTier[]> {
    return this.http.get<PriceTier[]>('/api/v1/pricing/tiers');
  }

  resolvePrice(
    variantId: string,
    uomId: string,
    priceTierId: string | null,
    quantity: number,
  ): Observable<ResolvedPrice> {
    let params = new HttpParams()
      .set('variantId', variantId)
      .set('uomId', uomId)
      .set('quantity', quantity);
    if (priceTierId) params = params.set('priceTierId', priceTierId);
    return this.http.get<ResolvedPrice>('/api/v1/pricing/resolve', { params });
  }

  bulkResolvePrice(
    priceTierId: string | null,
    items: { variantId: string; uomId: string }[],
  ): Observable<ResolvedPrice[]> {
    return this.http.post<ResolvedPrice[]>('/api/v1/pricing/resolve/bulk', {
      priceTierId,
      items,
    });
  }

  getSettings(): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>('/api/v1/settings');
  }

  listStocksByWarehouse(warehouseId: string): Observable<StockSnapshotItem[]> {
    return this.http.get<StockSnapshotItem[]>(`/api/v1/inventory/stocks/by-warehouse/${warehouseId}`);
  }

  /** Active lots for a variant in a warehouse, for manual lot selection (online only). */
  listLots(variantId: string, warehouseId: string): Observable<PosLot[]> {
    const params = new HttpParams()
      .set('variantId', variantId)
      .set('warehouseId', warehouseId)
      .set('size', '100');
    return this.http
      .get<PageResponse<PosLot>>('/api/v1/lots', { params })
      .pipe(map((p) => (p.content ?? []).filter((l) => l.status === 'ACTIVE' && l.quantityRemaining > 0)));
  }
}
