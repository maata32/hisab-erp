import Dexie, { Table } from 'dexie';
import { CachedProduct, CachedPriceTier, PendingSale } from '../models/pos.models';

export class PosDb extends Dexie {
  products!: Table<CachedProduct, string>;
  priceTiers!: Table<CachedPriceTier, string>;
  pendingSales!: Table<PendingSale, number>;

  constructor() {
    super('minierp-pos-v1');
    this.version(1).stores({
      products: 'id, sku, barcode, name, cachedAt',
      priceTiers: 'id, code',
      pendingSales: '++localId, idempotencyKey, status, sessionId, createdAt',
    });
  }
}

export const posDb = new PosDb();
