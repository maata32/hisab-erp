import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '@minierp/shared-api';
import { CashRegister, CashSession, SyncedSale } from '../models/pos.models';

export interface OpenSessionRequest {
  registerId: string;
  openingFloat: number;
}

export interface CloseSessionRequest {
  countedClosing: number;
  note?: string;
}

export interface CashMovementRequest {
  amount: number;
  reason?: string;
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
  productId: string;
  uomId: string;
  quantity: number;
  unitDiscount?: number | null;
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

export interface ProductPageItem {
  id: string;
  sku: string;
  barcode: string | null;
  name: string;
  baseUomId: string;
  defaultTaxRate: number;
  sellable: boolean;
  imageUrl: string | null;
}

export interface ResolvedPrice {
  productId: string;
  uomId: string;
  priceTierId: string;
  amount: number;
  currency: string;
  taxInclusive: boolean;
  taxRate: number;
  unitNet: number;
  unitGross: number;
}

export interface PriceTier {
  id: string;
  code: string;
  name: string;
  isDefault: boolean;
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

  cashIn(sessionId: string, req: CashMovementRequest): Observable<unknown> {
    return this.http.post(`/api/v1/pos/sessions/${sessionId}/cash-in`, req);
  }

  cashOut(sessionId: string, req: CashMovementRequest): Observable<unknown> {
    return this.http.post(`/api/v1/pos/sessions/${sessionId}/cash-out`, req);
  }

  createSale(req: CreateSaleRequest): Observable<SyncedSale> {
    return this.http.post<SyncedSale>('/api/v1/pos/sales', req);
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
    productId: string,
    uomId: string,
    priceTierId: string | null,
    quantity: number,
  ): Observable<ResolvedPrice> {
    let params = new HttpParams()
      .set('productId', productId)
      .set('uomId', uomId)
      .set('quantity', quantity);
    if (priceTierId) params = params.set('priceTierId', priceTierId);
    return this.http.get<ResolvedPrice>('/api/v1/pricing/resolve', { params });
  }
}
