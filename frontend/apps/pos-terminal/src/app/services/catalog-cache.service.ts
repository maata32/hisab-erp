import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { posDb } from '../db/pos.db';
import { CachedProduct, CachedPriceTier } from '../models/pos.models';
import { PosApiService } from './pos-api.service';

const CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

@Injectable({ providedIn: 'root' })
export class CatalogCacheService {
  private readonly api = inject(PosApiService);

  async refreshIfStale(): Promise<void> {
    const oldest = await posDb.products.orderBy('cachedAt').first();
    const now = Date.now();
    if (!oldest || now - oldest.cachedAt > CACHE_TTL_MS) {
      await this.refresh();
    }
  }

  async refresh(): Promise<void> {
    await Promise.all([this.refreshProducts(), this.refreshPriceTiers()]);
  }

  async refreshProducts(): Promise<void> {
    const now = Date.now();
    const resp = await firstValueFrom(this.api.listAllSellableProducts(0, 500));
    const rows: CachedProduct[] = resp.content
      .filter((p) => p.sellable)
      .map((p) => ({
        id: p.id,
        sku: p.sku,
        barcode: p.barcode,
        name: p.name,
        baseUomId: p.baseUomId,
        defaultTaxRate: p.defaultTaxRate,
        sellable: p.sellable,
        imageUrl: p.imageUrl,
        price: 0,
        priceTierId: null,
        currency: 'MRU',
        taxInclusive: false,
        cachedAt: now,
      }));
    await posDb.products.clear();
    await posDb.products.bulkPut(rows);
  }

  async refreshPriceTiers(): Promise<void> {
    const tiers = await firstValueFrom(this.api.listPriceTiers());
    const rows: CachedPriceTier[] = tiers.map((t) => ({
      id: t.id,
      code: t.code,
      name: t.name,
      isDefault: (t as any).defaultTier ?? false,
    }));
    await posDb.priceTiers.clear();
    await posDb.priceTiers.bulkPut(rows);
  }

  async searchProducts(query: string): Promise<CachedProduct[]> {
    if (!query) return posDb.products.toArray();
    const q = query.toLowerCase();
    return posDb.products
      .filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.sku.toLowerCase().includes(q) ||
          (p.barcode != null && p.barcode.includes(q)),
      )
      .toArray();
  }

  async getProduct(id: string): Promise<CachedProduct | undefined> {
    return posDb.products.get(id);
  }

  async getAllProducts(): Promise<CachedProduct[]> {
    return posDb.products.toArray();
  }

  async getDefaultPriceTier(): Promise<CachedPriceTier | undefined> {
    return posDb.priceTiers.filter((t) => t.isDefault).first();
  }
}
