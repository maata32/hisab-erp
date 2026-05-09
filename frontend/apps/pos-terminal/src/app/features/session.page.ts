import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { SelectButtonModule } from 'primeng/selectbutton';
import { DropdownModule } from 'primeng/dropdown';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { SessionService } from '../services/session.service';
import { PosApiService } from '../services/pos-api.service';
import { SyncService } from '../services/sync.service';
import { CashRegister, CashSession } from '../models/pos.models';

@Component({
  selector: 'pos-session-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    ButtonModule,
    DialogModule,
    InputNumberModule,
    InputTextareaModule,
    SelectButtonModule,
    DropdownModule,
    ToastModule,
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

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                {{ 'pos.session.opening_float' | translate }}
              </label>
              <p-inputNumber
                [(ngModel)]="openingFloat"
                [min]="0"
                [minFractionDigits]="2"
                [maxFractionDigits]="2"
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
      </div>
    }

    <!-- Active session -->
    @if (sessionSvc.isOpen()) {
      <div class="p-4 space-y-4">
        <!-- Session header -->
        <div class="bg-white rounded-lg shadow p-4">
          <div class="flex items-center justify-between mb-2">
            <div>
              <h2 class="font-bold text-lg">{{ sessionSvc.currentRegister()?.name }}</h2>
              <p class="text-sm text-gray-500">
                {{ 'pos.session.opened_at' | translate }}
                {{ sessionSvc.currentSession()?.openedAt | date: 'short' }}
              </p>
            </div>
            <span class="px-3 py-1 bg-green-100 text-green-800 rounded-full text-sm font-medium">
              {{ 'pos.session.status.open' | translate }}
            </span>
          </div>
          @if (pendingCount() > 0) {
            <div class="bg-yellow-50 border border-yellow-200 rounded p-2 text-sm text-yellow-800">
              <i class="pi pi-clock me-1"></i>
              {{ pendingCount() }} {{ 'pos.session.pending_sales' | translate }}
            </div>
          }
        </div>

        <!-- KPIs -->
        <div class="grid grid-cols-2 gap-3">
          <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-xs text-gray-500">{{ 'pos.session.kpi.opening_float' | translate }}</p>
            <p class="text-lg font-bold">{{ sessionSvc.currentSession()?.openingFloat | number: '1.2-2' }}</p>
          </div>
          <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-xs text-gray-500">{{ 'pos.session.kpi.total_sales' | translate }}</p>
            <p class="text-lg font-bold text-green-600">{{ sessionSvc.currentSession()?.totalSales | number: '1.2-2' }}</p>
          </div>
          <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-xs text-gray-500">{{ 'pos.session.kpi.cash_in' | translate }}</p>
            <p class="text-lg font-bold text-blue-600">{{ sessionSvc.currentSession()?.totalCashIn | number: '1.2-2' }}</p>
          </div>
          <div class="bg-white rounded-lg shadow p-3 text-center">
            <p class="text-xs text-gray-500">{{ 'pos.session.kpi.expected' | translate }}</p>
            <p class="text-lg font-bold">{{ sessionSvc.currentSession()?.expectedClosing | number: '1.2-2' }}</p>
          </div>
        </div>

        <!-- Cash movement buttons -->
        <div class="grid grid-cols-2 gap-3">
          <button
            pButton
            type="button"
            [label]="'pos.session.cash_in' | translate"
            icon="pi pi-plus-circle"
            severity="success"
            (click)="openCashMovement('in')"
            class="w-full"
          ></button>
          <button
            pButton
            type="button"
            [label]="'pos.session.cash_out' | translate"
            icon="pi pi-minus-circle"
            severity="warning"
            (click)="openCashMovement('out')"
            class="w-full"
          ></button>
        </div>

        <!-- Close session button -->
        <button
          pButton
          type="button"
          [label]="'pos.session.close.button' | translate"
          icon="pi pi-stop-circle"
          severity="danger"
          (click)="showCloseDialog.set(true)"
          class="w-full"
          outlined
        ></button>
      </div>
    }

    <!-- Cash movement dialog -->
    <p-dialog
      [header]="movementType() === 'in' ? ('pos.session.cash_in' | translate) : ('pos.session.cash_out' | translate)"
      [(visible)]="showMovementDialog"
      [modal]="true"
      [style]="{ width: '22rem' }"
    >
      <div class="space-y-3 pt-2">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'pos.session.amount' | translate }}</label>
          <p-inputNumber
            [(ngModel)]="movementAmount"
            [min]="0.01"
            [minFractionDigits]="2"
            [maxFractionDigits]="2"
            styleClass="w-full"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'pos.session.reason' | translate }}</label>
          <textarea
            pInputTextarea
            [(ngModel)]="movementReason"
            rows="2"
            class="w-full"
          ></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" severity="secondary" (click)="showMovementDialog = false" text></button>
        <button pButton [label]="'common.save' | translate" [loading]="saving()" (click)="submitMovement()"></button>
      </ng-template>
    </p-dialog>

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
            [minFractionDigits]="2"
            [maxFractionDigits]="2"
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
  `,
})
export class SessionPage implements OnInit {
  protected readonly sessionSvc = inject(SessionService);
  private readonly api = inject(PosApiService);
  private readonly sync = inject(SyncService);
  private readonly msg = inject(MessageService);

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly registers = this.sessionSvc.registers;
  protected readonly pendingCount = signal(0);
  protected readonly showCloseDialog = signal(false);

  protected selectedRegisterId: string | null = null;
  protected openingFloat = 0;
  protected showMovementDialog = false;
  protected movementType = signal<'in' | 'out'>('in');
  protected movementAmount = 0;
  protected movementReason = '';
  protected countedClosing = 0;
  protected closeNote = '';

  get showCloseDialogVisible(): boolean {
    return this.showCloseDialog();
  }
  set showCloseDialogVisible(v: boolean) {
    this.showCloseDialog.set(v);
  }

  async ngOnInit(): Promise<void> {
    this.loading.set(true);
    await this.sessionSvc.loadRegisters();
    this.loading.set(false);
    this.pendingCount.set(await this.sync.getPendingCount());
  }

  async openSession(): Promise<void> {
    if (!this.selectedRegisterId) return;
    this.saving.set(true);
    try {
      await this.sessionSvc.openSession(this.selectedRegisterId, this.openingFloat);
      this.msg.add({ severity: 'success', summary: 'Session ouverte', life: 3000 });
    } catch {
      this.msg.add({ severity: 'error', summary: 'Erreur', detail: 'Impossible d\'ouvrir la session', life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  openCashMovement(type: 'in' | 'out'): void {
    this.movementType.set(type);
    this.movementAmount = 0;
    this.movementReason = '';
    this.showMovementDialog = true;
  }

  async submitMovement(): Promise<void> {
    const session = this.sessionSvc.currentSession();
    if (!session) return;
    this.saving.set(true);
    try {
      const req = { amount: this.movementAmount, reason: this.movementReason || undefined };
      if (this.movementType() === 'in') {
        await this.api.cashIn(session.id, req).toPromise();
      } else {
        await this.api.cashOut(session.id, req).toPromise();
      }
      await this.sessionSvc.refreshSession();
      this.showMovementDialog = false;
      this.msg.add({ severity: 'success', summary: 'Mouvement enregistré', life: 3000 });
    } catch {
      this.msg.add({ severity: 'error', summary: 'Erreur', detail: 'Mouvement échoué', life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  async closeSession(): Promise<void> {
    this.saving.set(true);
    try {
      await this.sessionSvc.closeSession(this.countedClosing, this.closeNote || undefined);
      this.showCloseDialog.set(false);
      this.msg.add({ severity: 'success', summary: 'Session clôturée', life: 3000 });
    } catch {
      this.msg.add({ severity: 'error', summary: 'Erreur', detail: 'Clôture échouée', life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }
}
