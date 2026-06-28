import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { CalendarModule } from 'primeng/calendar';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { DropdownModule } from 'primeng/dropdown';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

import { SessionService } from '../services/session.service';
import { SyncService } from '../services/sync.service';
import { PosSettingsService } from '../services/pos-settings.service';
import { PosApiService } from '../services/pos-api.service';
import { ReceiptService } from '../services/receipt.service';
import { SyncedSale } from '../models/pos.models';

@Component({
  selector: 'pos-session-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule,
    ButtonModule, CalendarModule, DialogModule, InputNumberModule, InputTextModule, InputTextareaModule,
    DropdownModule, TableModule, TagModule, TooltipModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast position="top-center" />

    <!-- No active session -->
    @if (!sessionSvc.isOpen()) {
      <div class="max-w-md mx-auto mt-8 p-4">
        <h2 class="text-xl font-bold mb-4">{{ 'pos.session.open.title' | translate }}</h2>

        @if (loading()) {
          <div class="text-center py-8">
            <i class="pi pi-spin pi-spinner text-3xl text-primary-600"></i>
          </div>
        } @else if (registers().length === 0) {
          <div class="bg-yellow-50 border border-yellow-200 rounded p-4 text-yellow-800">
            {{ 'pos.session.no_registers' | translate }}
          </div>
        } @else {
          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                {{ 'pos.session.register' | translate }}
              </label>
              <p-dropdown
                [options]="registers()"
                [(ngModel)]="selectedRegisterId"
                optionLabel="name"
                optionValue="id"
                [placeholder]="'pos.session.select_register' | translate"
                styleClass="w-full"
              />
            </div>

            <button
              pButton
              type="button"
              [label]="'pos.session.open.submit' | translate"
              icon="pi pi-play"
              [disabled]="!selectedRegisterId || saving()"
              [loading]="saving()"
              (click)="openSession()"
              class="w-full"
            ></button>
          </div>
        }

        <!-- Pending deposits — shown before opening a new session so the cashier
             always sees how much they still owe the vault before starting fresh. -->
        @if (pendingSessions().length > 0) {
          <div class="bg-amber-50 border border-amber-200 rounded-lg mt-6">
            <div class="flex items-center justify-between p-3 border-b border-amber-200 flex-wrap gap-2">
              <h3 class="font-semibold text-sm text-amber-900">
                <i class="pi pi-clock me-1"></i>
                {{ 'pos.session.pending.title' | translate }}
                <span class="ml-2 px-2 py-0.5 bg-amber-200 text-amber-900 rounded-full text-xs">
                  {{ pendingSessions().length }}
                </span>
              </h3>
              <div class="text-sm text-amber-900">
                {{ 'pos.session.pending.totalToDeposit' | translate }}:
                <span class="font-bold">{{ fmtSvc.format(pendingTotal()) }}</span>
              </div>
            </div>
            <p-table
              [value]="pendingSessions()"
              [loading]="loadingPending()"
              stripedRows
              responsiveLayout="scroll"
              responsiveLayout="scroll"
              styleClass="p-datatable-sm"
            >
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'pos.session.history.closedAt' | translate }}</th>
                  <th class="text-right">{{ 'pos.session.history.counted' | translate }}</th>
                  <th class="text-right">{{ 'pos.session.history.difference' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-s>
                <tr>
                  <td class="text-sm whitespace-nowrap">{{ s.closedAt | date:'short' }}</td>
                  <td class="text-right font-medium">{{ fmtSvc.format(s.countedClosing) }}</td>
                  <td class="text-right"
                      [class.text-red-600]="(s.difference || 0) < 0"
                      [class.text-green-600]="(s.difference || 0) > 0">
                    {{ fmtSvc.format(s.difference || 0) }}
                  </td>
                </tr>
              </ng-template>
            </p-table>
          </div>
        }
      </div>
    }

    <!-- Active session -->
    @if (sessionSvc.isOpen()) {
      <div class="p-4 space-y-4">
        <!-- Session header with close button on the right -->
        <div class="bg-white rounded-lg shadow p-4">
          <div class="flex items-center justify-between gap-3 flex-wrap">
            <div>
              <div class="flex items-center gap-2">
                <h2 class="font-bold text-lg">{{ sessionSvc.currentRegister()?.name }}</h2>
                <span class="px-2 py-0.5 bg-green-100 text-green-800 rounded-full text-xs font-medium">
                  {{ 'pos.session.status.open' | translate }}
                </span>
              </div>
              <p class="text-sm text-gray-500 mt-0.5">
                {{ 'pos.session.opened_at' | translate }}
                {{ sessionSvc.currentSession()?.openedAt | date: 'short' }}
              </p>
            </div>
            <button
              pButton
              type="button"
              [label]="'pos.session.close.button' | translate"
              icon="pi pi-stop-circle"
              severity="danger"
              outlined
              (click)="openCloseDialog()"
            ></button>
          </div>
          @if (pendingCount() > 0) {
            <div class="mt-2 bg-yellow-50 border border-yellow-200 rounded p-2 text-sm text-yellow-800">
              <i class="pi pi-clock me-1"></i>
              {{ pendingCount() }} {{ 'pos.session.pending_sales' | translate }}
            </div>
          }
        </div>

        <!-- KPIs: Total ventes + Solde attendu -->
        <div class="grid grid-cols-2 gap-3">
          <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-xs text-gray-500">{{ 'pos.session.kpi.total_sales' | translate }}</p>
            <p class="text-lg font-bold text-green-600">{{ fmtSvc.format(sessionSvc.currentSession()?.totalSales ?? 0) }}</p>
          </div>
          <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-xs text-gray-500">{{ 'pos.session.kpi.expected' | translate }}</p>
            <p class="text-lg font-bold">{{ fmtSvc.format(sessionSvc.currentSession()?.expectedClosing ?? 0) }}</p>
          </div>
        </div>

        <!-- Current session sales history -->
        <div class="bg-white rounded-lg border border-gray-200">
          <div class="flex items-center justify-between p-3 border-b border-gray-100">
            <h3 class="font-semibold text-sm text-gray-700">{{ 'pos.session.sales_title' | translate }}</h3>
            <button pButton icon="pi pi-refresh" class="p-button-sm p-button-text"
                    [pTooltip]="'common.refresh' | translate"
                    [loading]="loadingSales()"
                    (click)="loadSessionSales()"></button>
          </div>
          <p-table
            [value]="sales()"
            [loading]="loadingSales()"
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
                <th>{{ 'pos.history.status' | translate }}</th>
                <th class="text-right">{{ 'pos.history.total' | translate }}</th>
                <th></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-s>
              <tr>
                <td><span class="font-mono text-sm">{{ s.number }}</span></td>
                <td>{{ s.completedAt | date:'HH:mm' }}</td>
                <td>
                  <p-tag [value]="statusLabel(s.status) | translate" [severity]="statusSeverity(s.status)" />
                </td>
                <td class="text-right font-medium">{{ fmtSvc.format(s.total) }} {{ s.currency }}</td>
                <td class="text-right whitespace-nowrap">
                  <button pButton icon="pi pi-print" class="p-button-sm p-button-text"
                          [pTooltip]="'pos.history.reprint' | translate"
                          [loading]="printingId() === s.id"
                          (click)="reprint(s)"></button>
                  @if (canVoid(s)) {
                    <button pButton icon="pi pi-times-circle"
                            class="p-button-sm p-button-text p-button-danger"
                            [pTooltip]="'pos.history.void' | translate"
                            (click)="askVoid(s)"></button>
                  }
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr>
                <td colspan="5" class="text-center text-gray-400 py-6">
                  {{ 'pos.session.no_sales_yet' | translate }}
                </td>
              </tr>
            </ng-template>
          </p-table>
        </div>
      </div>
    }

    <!-- Close session dialog -->
    <p-dialog
      [header]="'pos.session.close.title' | translate"
      [(visible)]="showCloseDialogVisible"
      [modal]="true"
      [style]="{ width: '22rem' }"
    >
      <div class="space-y-3 pt-2">
        <p class="text-sm text-gray-600">{{ 'pos.session.close.hint' | translate }}</p>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'pos.session.counted_closing' | translate }}</label>
          <p-inputNumber
            [(ngModel)]="countedClosing"
            [min]="0"
            [minFractionDigits]="fmtSvc.decimalPlaces()"
            [maxFractionDigits]="fmtSvc.decimalPlaces()"
            styleClass="w-full"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'pos.session.close_note' | translate }}</label>
          <textarea
            pInputTextarea
            [(ngModel)]="closeNote"
            rows="2"
            class="w-full"
          ></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" severity="secondary" text (click)="showCloseDialog.set(false)"></button>
        <button pButton [label]="'pos.session.close.confirm' | translate" severity="danger" [loading]="saving()" (click)="closeSession()"></button>
      </ng-template>
    </p-dialog>

    <!-- Void confirm dialog -->
    <p-dialog
      [(visible)]="voidDialogOpen"
      [modal]="true"
      [style]="{ width: '22rem' }"
      [header]="'pos.history.void' | translate"
      [closable]="!busy()"
    >
      <div class="space-y-3">
        <p class="text-sm text-gray-600">
          {{ 'pos.history.void_confirm' | translate:{ number: voidTarget()?.number } }}
        </p>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'pos.history.reason' | translate }}</label>
          <input pInputText [(ngModel)]="voidReason" class="w-full" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <div class="flex flex-col gap-2">
          <button pButton [label]="'pos.history.void_confirm_btn' | translate"
                  icon="pi pi-times-circle" severity="danger"
                  class="w-full" [loading]="busy()" (click)="confirmVoid()"></button>
          <button pButton [label]="'common.cancel' | translate" icon="pi pi-times"
                  severity="secondary" text class="w-full" [disabled]="busy()"
                  (click)="voidDialogOpen = false"></button>
        </div>
      </ng-template>
    </p-dialog>
  `,
})
export class SessionPage implements OnInit {
  protected readonly sessionSvc = inject(SessionService);
  protected readonly fmtSvc = inject(PosSettingsService);
  private readonly sync = inject(SyncService);
  private readonly msg = inject(MessageService);
  private readonly i18n = inject(TranslateService);
  private readonly api = inject(PosApiService);
  private readonly receiptSvc = inject(ReceiptService);

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly busy = signal(false);
  protected readonly loadingSales = signal(false);
  protected readonly printingId = signal<string | null>(null);
  protected readonly registers = this.sessionSvc.registers;
  protected readonly pendingCount = signal(0);
  protected readonly showCloseDialog = signal(false);
  protected readonly sales = signal<SyncedSale[]>([]);

  protected selectedRegisterId: string | null = null;
  protected countedClosing = 0;
  protected closeNote = '';

  protected voidDialogOpen = false;
  protected voidReason = '';
  protected readonly voidTarget = signal<SyncedSale | null>(null);

  // Pending deposits — shown before opening a new session.
  protected readonly pendingSessions = signal<import('../models/pos.models').CashSession[]>([]);
  protected readonly loadingPending = signal(false);

  protected readonly pendingTotal = computed(() =>
    this.pendingSessions().reduce((sum, s) => sum + Number(s.countedClosing ?? 0), 0),
  );

  get showCloseDialogVisible(): boolean {
    return this.showCloseDialog();
  }
  set showCloseDialogVisible(v: boolean) {
    this.showCloseDialog.set(v);
  }

  async ngOnInit(): Promise<void> {
    await this.fmtSvc.load();
    this.loading.set(true);
    await this.sessionSvc.loadRegisters();
    this.loading.set(false);
    this.pendingCount.set(await this.sync.getPendingCount());
    if (this.sessionSvc.isOpen()) await this.loadSessionSales();
    await this.loadPendingSessions();
  }

  async loadPendingSessions(): Promise<void> {
    this.loadingPending.set(true);
    try {
      this.pendingSessions.set(await firstValueFrom(this.api.listMyPendingSessions()));
    } catch {
      this.pendingSessions.set([]);
    } finally {
      this.loadingPending.set(false);
    }
  }

  async loadSessionSales(): Promise<void> {
    const session = this.sessionSvc.currentSession();
    if (!session) return;
    this.loadingSales.set(true);
    try {
      const res = await firstValueFrom(this.api.listSalesBySession(session.id));
      this.sales.set(res.content ?? []);
    } catch {
      this.sales.set([]);
    } finally {
      this.loadingSales.set(false);
    }
  }

  async openSession(): Promise<void> {
    if (!this.selectedRegisterId) return;
    this.saving.set(true);
    try {
      // Sessions always start at zero — opening float is no longer transferred from the vault.
      await this.sessionSvc.openSession(this.selectedRegisterId, 0);
      this.msg.add({ severity: 'success', summary: this.i18n.instant('pos.session.opened'), life: 3000 });
      await this.loadSessionSales();
    } catch {
      this.msg.add({ severity: 'error', summary: this.i18n.instant('common.error'), detail: this.i18n.instant('pos.session.openFailed'), life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  protected openCloseDialog(): void {
    this.countedClosing = Number(this.sessionSvc.currentSession()?.expectedClosing ?? 0);
    this.closeNote = '';
    this.showCloseDialog.set(true);
  }

  async closeSession(): Promise<void> {
    this.saving.set(true);
    try {
      await this.sessionSvc.closeSession(this.countedClosing, this.closeNote || undefined);
      this.showCloseDialog.set(false);
      this.sales.set([]);
      this.msg.add({ severity: 'success', summary: this.i18n.instant('pos.session.closed'), life: 3000 });
      await this.loadPendingSessions();
    } catch {
      this.msg.add({ severity: 'error', summary: this.i18n.instant('common.error'), detail: this.i18n.instant('pos.session.closeFailed'), life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  protected canVoid(s: SyncedSale): boolean {
    if (s.status !== 'COMPLETED') return false;
    return this.sessionSvc.currentSession()?.id === s.sessionId;
  }

  protected statusLabel(status: string): string {
    return 'pos.history.status_' + status.toLowerCase();
  }

  protected statusSeverity(status: string): 'success' | 'danger' | 'warning' | 'info' {
    if (status === 'COMPLETED') return 'success';
    if (status === 'CANCELLED') return 'danger';
    return 'info';
  }

  protected askVoid(sale: SyncedSale): void {
    this.voidTarget.set(sale);
    this.voidReason = '';
    this.voidDialogOpen = true;
  }

  async confirmVoid(): Promise<void> {
    const target = this.voidTarget();
    if (!target) return;
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.voidSale(target.id, this.voidReason || null));
      this.msg.add({ severity: 'success', summary: this.i18n.instant('pos.session.voided'), life: 2500 });
      this.voidDialogOpen = false;
      await this.loadSessionSales();
      await this.sessionSvc.refreshSession();
    } catch (e: unknown) {
      this.msg.add({
        severity: 'error',
        summary: 'Erreur',
        detail:
          (e as { error?: { message?: string } } | null)?.error?.message ||
          'Annulation impossible',
        life: 4000,
      });
    } finally {
      this.busy.set(false);
    }
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
