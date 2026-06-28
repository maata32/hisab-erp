import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { CalendarModule } from 'primeng/calendar';
import { TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

import { PosApiService } from '../services/pos-api.service';
import { SessionService } from '../services/session.service';
import { ReceiptService } from '../services/receipt.service';
import { PosSettingsService } from '../services/pos-settings.service';
import { SyncedSale } from '../models/pos.models';

interface SessionOption {
  id: string;
  label: string;
  firstAt: number;
}

@Component({
  selector: 'pos-history-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule,
    ButtonModule, CalendarModule, TableModule, ToastModule, DropdownModule,
    TagModule, TooltipModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast position="top-center" />

    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-xl font-bold text-gray-800">{{ 'pos.history.title' | translate }}</h1>
          <p class="text-gray-500 text-sm">{{ 'pos.history.subtitle' | translate }}</p>
        </div>
        <div class="flex gap-2 items-center flex-wrap">
          <p-dropdown
            [options]="sessionSvc.registers()"
            [(ngModel)]="selectedRegisterId"
            optionLabel="name"
            optionValue="id"
            [placeholder]="'pos.session.select_register' | translate"
            [disabled]="!!sessionSvc.currentSession()"
            (onChange)="onRegisterChanged()"
            styleClass="w-48 history-filter"
          />
          <p-calendar
            [(ngModel)]="selectedDate"
            dateFormat="dd/mm/yy"
            [readonlyInput]="true"
            (onSelect)="onDateSelected()"
            styleClass="w-36 history-filter"
            inputStyleClass="w-full"
          />
          <p-dropdown
            [options]="sessionOptions()"
            [(ngModel)]="selectedSessionId"
            optionLabel="label"
            optionValue="id"
            [placeholder]="'pos.history.filterSession' | translate"
            [disabled]="!dateLoaded()"
            [showClear]="true"
            styleClass="w-64 history-filter session-filter"
          />
        </div>
      </header>

      @if (!selectedRegisterId) {
        <div class="bg-yellow-50 border border-yellow-200 rounded p-4 text-yellow-800 text-sm">
          {{ 'pos.history.requires_register' | translate }}
        </div>
      } @else {
        <div class="bg-white rounded-lg border border-gray-200">
          <p-table
            [value]="filteredSales()"
            [loading]="loading()"
            stripedRows
            responsiveLayout="scroll"
            responsiveLayout="scroll"
            [rowHover]="true"
            styleClass="p-datatable-sm"
          >
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'pos.history.number' | translate }}</th>
                <th>{{ 'pos.history.time' | translate }}</th>
                <th>{{ 'pos.history.session' | translate }}</th>
                <th>{{ 'pos.history.status' | translate }}</th>
                <th class="text-right">{{ 'pos.history.total' | translate }}</th>
                <th></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-s>
              <tr>
                <td>
                  <span class="font-mono text-sm">{{ s.number }}</span>
                </td>
                <td>{{ s.completedAt | date:'HH:mm' }}</td>
                <td class="text-xs text-gray-600 whitespace-nowrap">{{ sessionLabelOf(s.sessionId) }}</td>
                <td>
                  <p-tag
                    [value]="statusLabel(s.status) | translate"
                    [severity]="statusSeverity(s.status)"
                  />
                </td>
                <td class="text-right font-medium">{{ fmt.format(s.total) }} {{ s.currency }}</td>
                <td class="text-right whitespace-nowrap">
                  <button pButton icon="pi pi-print" class="p-button-sm p-button-text"
                          [pTooltip]="'pos.history.reprint' | translate"
                          [loading]="printingId() === s.id"
                          (click)="reprint(s)"></button>
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr>
                <td colspan="6" class="text-center text-gray-400 py-8">
                  {{ 'pos.history.empty' | translate }}
                </td>
              </tr>
            </ng-template>
          </p-table>
        </div>
      }
    </div>
  `,
})
export class HistoryPage implements OnInit {
  protected readonly sessionSvc = inject(SessionService);
  private readonly api = inject(PosApiService);
  private readonly receiptSvc = inject(ReceiptService);
  protected readonly fmt = inject(PosSettingsService);
  private readonly msg = inject(MessageService);
  private readonly i18n = inject(TranslateService);

  protected readonly sales = signal<SyncedSale[]>([]);
  protected readonly loading = signal(false);
  protected readonly printingId = signal<string | null>(null);
  protected readonly dateLoaded = signal(false);
  protected selectedDate: Date = new Date();
  protected selectedRegisterId: string | null = null;

  /** Backed by a signal so the {@link filteredSales} computed reacts to dropdown changes. */
  private readonly _selectedSessionId = signal<string | null>(null);
  get selectedSessionId(): string | null { return this._selectedSessionId(); }
  set selectedSessionId(v: string | null) { this._selectedSessionId.set(v); }

  /** Unique sessions extracted from currently loaded sales, most-recent first. */
  protected readonly sessionOptions = computed<SessionOption[]>(() => {
    const buckets = new Map<string, { first: number; last: number }>();
    for (const s of this.sales()) {
      const t = new Date(s.completedAt).getTime();
      const b = buckets.get(s.sessionId);
      if (!b) buckets.set(s.sessionId, { first: t, last: t });
      else {
        if (t < b.first) b.first = t;
        if (t > b.last) b.last = t;
      }
    }
    return Array.from(buckets.entries())
      .map(([id, b]) => ({
        id,
        firstAt: b.first,
        label: `Session ${this.formatTime(b.first)} – ${this.formatTime(b.last)}`,
      }))
      .sort((a, b) => b.firstAt - a.firstAt);
  });

  /** Quick lookup sessionId → label for the table column. */
  protected readonly sessionLabelMap = computed<Map<string, string>>(() => {
    const m = new Map<string, string>();
    for (const o of this.sessionOptions()) m.set(o.id, o.label);
    return m;
  });

  protected sessionLabelOf(sessionId: string): string {
    return this.sessionLabelMap().get(sessionId) ?? '—';
  }

  protected readonly filteredSales = computed<SyncedSale[]>(() => {
    const sid = this._selectedSessionId();
    if (!sid) return this.sales();
    return this.sales().filter(s => s.sessionId === sid);
  });

  async ngOnInit(): Promise<void> {
    await this.fmt.load();
    await this.sessionSvc.loadRegisters();

    // Default register: current open session's register, else first available.
    const fromSession = this.sessionSvc.currentSession()?.registerId;
    this.selectedRegisterId = fromSession
      ?? this.sessionSvc.registers()[0]?.id
      ?? null;

    if (this.selectedRegisterId) await this.load();
  }

  async onDateSelected(): Promise<void> {
    this.selectedSessionId = null;
    await this.load();
  }

  async onRegisterChanged(): Promise<void> {
    this.selectedSessionId = null;
    this.dateLoaded.set(false);
    await this.load();
  }

  async load(): Promise<void> {
    if (!this.selectedRegisterId) return;

    const start = new Date(this.selectedDate);
    start.setHours(0, 0, 0, 0);
    const end = new Date(start);
    end.setDate(end.getDate() + 1);

    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.api.listSalesByRegister(this.selectedRegisterId, start.toISOString(), end.toISOString(), 0, 200),
      );
      this.sales.set(res.content ?? []);
      this.dateLoaded.set(true);
    } catch {
      this.sales.set([]);
      this.msg.add({ severity: 'error', summary: this.i18n.instant('common.error'), detail: this.i18n.instant('pos.history.loadFailed'), life: 3000 });
    } finally {
      this.loading.set(false);
    }
  }

  protected statusLabel(status: string): string {
    return 'pos.history.status_' + status.toLowerCase();
  }

  protected statusSeverity(status: string): 'success' | 'danger' | 'warning' | 'info' {
    if (status === 'COMPLETED') return 'success';
    if (status === 'CANCELLED') return 'danger';
    return 'info';
  }

  private formatTime(ms: number): string {
    const d = new Date(ms);
    const hh = d.getHours().toString().padStart(2, '0');
    const mm = d.getMinutes().toString().padStart(2, '0');
    return `${hh}:${mm}`;
  }

  async reprint(sale: SyncedSale): Promise<void> {
    this.printingId.set(sale.id);
    try {
      const widthMm = this.sessionSvc.getReceiptWidth();
      const data = this.receiptSvc.buildFromSynced(sale, 'Hisab ERP', widthMm);
      await this.receiptSvc.printEscPos(data);
    } finally {
      this.printingId.set(null);
    }
  }
}
