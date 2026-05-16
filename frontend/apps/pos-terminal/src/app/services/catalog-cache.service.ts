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
    const stale = !oldest || now - oldest.cachedAt > CACHE_TTL_MS;
    // Legacy records cached before the images field was introduced lack it — force refresh.
    const schemaOutdated = !!oldest && !Array.isArray((oldest as any).images);
    if (stale || schemaOutdated) {
      await this.refresh();
    }
  }

  async refresh(): Promise<void> {
    await Promise.all([this.refreshProducts(), this.refreshPriceTiers()]);
  }

  async refreshProducts(): Promise<void> {
    const now = Date.now();
    const [resp, tiers] = await Promise.all([
      firstValueFrom(this.api.listAllSellableProducts(0, 500)),
      firstValueFrom(this.api.listPriceTiers()),
    ]);

    const sellable = resp.content.filter((p) => p.sellable);
    const defaultTier = tiers.find((t) => (t as any).defaultTier) ?? tiers[0] ?? null;

    const priceMap = new Map<string, { amount: number; currency: string; taxInclusive: boolean; taxRate: number }>();
    if (defaultTier && sellable.length > 0) {
      try {
        const prices = await firstValueFrom(
          this.api.bulkResolvePrice(
            defaultTier.id,
            sellable.map((p) => ({ productId: p.id, uomId: p.baseUomId })),
          ),
        );
        for (const rp of prices) {
          priceMap.set(rp.productId, {
            amount: Number(rp.unitPrice),
            currency: rp.currency,
            taxInclusive: rp.taxInclusive,
            taxRate: Number(rp.taxRate),
          });
        }
      } catch { /* no prices configured yet */ }
    }

    const rows: CachedProduct[] = sellable.map((p) => {
      const rp = priceMap.get(p.id);
      return {
        id: p.id,
        sku: p.sku,
        barcode: p.barcode,
        name: p.name,
        baseUomId: p.baseUomId,
        defaultTaxRate: rp?.taxRate ?? p.defaultTaxRate,
        sellable: p.sellable,
        imageUrl: p.imageUrl,
        images: (p.images ?? []).map((img) => ({ id: img.id, url: img.url })),
        price: rp?.amount ?? 0,
        priceTierId: defaultTier?.id ?? null,
        currency: rp?.currency ?? 'MRU',
        taxInclusive: rp?.taxInclusive ?? false,
        cachedAt: now,
      };
    });
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
