import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CashRegister, CashSession } from '../models/pos.models';
import { PosApiService } from './pos-api.service';

const SESSION_KEY = 'hisaberp.pos.session.v1';

interface PersistedState {
  session: CashSession | null;
  register: CashRegister | null;
}

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly api = inject(PosApiService);

  readonly currentSession = signal<CashSession | null>(null);
  readonly currentRegister = signal<CashRegister | null>(null);
  readonly registers = signal<CashRegister[]>([]);

  constructor() {
    this.loadFromStorage();
  }

  async loadRegisters(): Promise<void> {
    try {
      const list = await firstValueFrom(this.api.listRegisters());
      this.registers.set(list);
    } catch {
      // offline — use cached
    }
  }

  async openSession(registerId: string, openingFloat: number): Promise<CashSession> {
    const session = await firstValueFrom(this.api.openSession({ registerId, openingFloat }));
    const register = this.registers().find((r) => r.id === registerId) ?? null;
    this.currentSession.set(session);
    this.currentRegister.set(register);
    this.persist();
    return session;
  }

  async closeSession(countedClosing: number, note?: string): Promise<CashSession> {
    const session = this.currentSession();
    if (!session) throw new Error('No active session');
    const closed = await firstValueFrom(this.api.closeSession(session.id, { countedClosing, note }));
    this.currentSession.set(null);
    this.currentRegister.set(null);
    this.persist();
    return closed;
  }

  async refreshSession(): Promise<void> {
    const session = this.currentSession();
    if (!session) return;
    try {
      const fresh = await firstValueFrom(this.api.getSession(session.id));
      this.currentSession.set(fresh);
      this.persist();
    } catch {
      // offline
    }
  }

  isOpen(): boolean {
    return this.currentSession()?.status === 'OPEN';
  }

  getReceiptWidth(): number {
    return this.currentRegister()?.receiptWidthMm ?? 80;
  }

  getDefaultPriceTierId(): string | null {
    return this.currentRegister()?.defaultPriceTierId ?? null;
  }

  private persist(): void {
    const state: PersistedState = {
      session: this.currentSession(),
      register: this.currentRegister(),
    };
    localStorage.setItem(SESSION_KEY, JSON.stringify(state));
  }

  private loadFromStorage(): void {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return;
    try {
      const state: PersistedState = JSON.parse(raw);
      if (state.session?.status === 'OPEN') {
        this.currentSession.set(state.session);
        this.currentRegister.set(state.register);
      }
    } catch {
      localStorage.removeItem(SESSION_KEY);
    }
  }
}
