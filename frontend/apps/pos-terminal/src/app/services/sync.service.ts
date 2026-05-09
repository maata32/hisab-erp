import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { posDb } from '../db/pos.db';
import { PendingSale } from '../models/pos.models';
import { PosApiService, CreateSaleRequest } from './pos-api.service';
import { OnlineStatusService } from './online-status.service';

const DEVICE_ID = (() => {
  const k = 'minierp.pos.deviceId';
  let id = localStorage.getItem(k);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(k, id);
  }
  return id;
})();

@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly api = inject(PosApiService);
  private readonly online = inject(OnlineStatusService);
  private syncing = false;

  constructor() {
    this.online.online$.subscribe((isOnline) => {
      if (isOnline) this.syncPending();
    });
  }

  async enqueueSale(sale: Omit<PendingSale, 'localId' | 'status' | 'syncError' | 'createdAt' | 'serverSaleId' | 'serverSaleNumber'>): Promise<number> {
    const localId = await posDb.pendingSales.add({
      ...sale,
      status: 'pending',
      syncError: null,
      createdAt: Date.now(),
      serverSaleId: null,
      serverSaleNumber: null,
    } as PendingSale);
    if (this.online.isOnline()) {
      this.syncPending();
    }
    return localId;
  }

  async syncPending(): Promise<void> {
    if (this.syncing) return;
    this.syncing = true;
    try {
      const pending = await posDb.pendingSales.where('status').equals('pending').toArray();
      if (pending.length === 0) return;

      const requests: CreateSaleRequest[] = pending.map((s) => ({
        idempotencyKey: s.idempotencyKey,
        registerId: s.registerId,
        sessionId: s.sessionId,
        customerId: s.customerId,
        priceTierId: s.priceTierId,
        occurredAt: s.occurredAt,
        note: s.note,
        lines: s.lines,
        payment: s.payment,
      }));

      const resp = await firstValueFrom(this.api.syncSales({ deviceId: DEVICE_ID, sales: requests }));

      for (const result of resp.results) {
        const sale = pending.find((s) => s.idempotencyKey === result.idempotencyKey);
        if (!sale?.localId) continue;
        if (result.status === 'ACCEPTED' || result.status === 'DUPLICATE') {
          await posDb.pendingSales.update(sale.localId, {
            status: 'synced',
            serverSaleId: result.saleId,
            serverSaleNumber: result.saleNumber,
          });
        } else {
          await posDb.pendingSales.update(sale.localId, {
            status: 'failed',
            syncError: result.errorMessage ?? result.errorCode ?? 'UNKNOWN',
          });
        }
      }
    } catch {
      // Will retry next time online
    } finally {
      this.syncing = false;
    }
  }

  async getPendingCount(): Promise<number> {
    return posDb.pendingSales.where('status').equals('pending').count();
  }

  async getRecentSales(sessionId: string, limit = 20): Promise<PendingSale[]> {
    return posDb.pendingSales
      .where('sessionId')
      .equals(sessionId)
      .reverse()
      .limit(limit)
      .toArray();
  }
}
