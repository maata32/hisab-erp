import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { CalendarModule } from 'primeng/calendar';
import { TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

import { PosApiService } from '../services/pos-api.service';
import { PosSettingsService } from '../services/pos-settings.service';
import { CashSession } from '../models/pos.models';

@Component({
  selector: 'pos-session-history-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule,
    ButtonModule, CalendarModule, TableModule, ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast position="top-center" />

    <div class="space-y-4">
      <header>
        <h1 class="text-xl font-bold text-gray-800">{{ 'pos.sessionHistory.title' | translate }}</h1>
        <p class="text-gray-500 text-sm">{{ 'pos.sessionHistory.subtitle' | translate }}</p>
      </header>

      <!-- Pending deposits -->
      <div class="bg-amber-50 border border-amber-200 rounded-lg">
        <div class="flex items-center justify-between p-3 border-b border-amber-200 flex-wrap gap-2">
          <h3 class="font-semibold text-sm text-amber-900">
            <i class="pi pi-clock me-1"></i>
            {{ 'pos.session.pending.title' | translate }}
            @if (pendingSessions().length > 0) {
              <span class="ml-2 px-2 py-0.5 bg-amber-200 text-amber-900 rounded-full text-xs">
                {{ pendingSessions().length }}
              </span>
            }
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
              <th class="text-right">{{ 'pos.session.kpi.expected' | translate }}</th>
              <th class="text-right">{{ 'pos.session.history.counted' | translate }}</th>
              <th class="text-right">{{ 'pos.session.history.difference' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-s>
            <tr>
              <td class="text-sm whitespace-nowrap">{{ s.closedAt | date:'short' }}</td>
              <td class="text-right">{{ fmtSvc.format(s.expectedClosing) }}</td>
              <td class="text-right font-medium">{{ fmtSvc.format(s.countedClosing) }}</td>
              <td class="text-right"
                  [class.text-red-600]="(s.difference || 0) < 0"
                  [class.text-green-600]="(s.difference || 0) > 0">
                {{ fmtSvc.format(s.difference || 0) }}
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="4" class="text-center text-gray-400 py-4 text-sm">
                {{ 'pos.session.pending.empty' | translate }}
              </td>
            </tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Validated history by date -->
      <div class="bg-white border border-gray-200 rounded-lg">
        <div class="flex items-center justify-between p-3 border-b border-gray-100 flex-wrap gap-2">
          <h3 class="font-semibold text-sm text-gray-700">
            <i class="pi pi-check-circle me-1 text-green-600"></i>
            {{ 'pos.session.validated.title' | translate }}
          </h3>
          <div class="flex items-center gap-3 flex-wrap">
            <p-calendar
              [(ngModel)]="validatedDate"
              dateFormat="dd/mm/yy"
              [readonlyInput]="true"
              (onSelect)="loadValidatedSessions()"
              styleClass="w-36 history-filter"
              inputStyleClass="w-full"
            />
            <div class="text-sm">
              {{ 'pos.session.validated.totalDeposited' | translate }}:
              <span class="font-bold text-green-700">{{ fmtSvc.format(validatedTotal()) }}</span>
            </div>
          </div>
        </div>
        <p-table
          [value]="validatedSessions()"
          [loading]="loadingValidated()"
          stripedRows
          responsiveLayout="scroll"
          responsiveLayout="scroll"
          styleClass="p-datatable-sm"
        >
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'pos.session.history.validatedAt' | translate }}</th>
              <th>{{ 'pos.session.history.closedAt' | translate }}</th>
              <th class="text-right">{{ 'pos.session.kpi.expected' | translate }}</th>
              <th class="text-right">{{ 'pos.session.history.counted' | translate }}</th>
              <th class="text-right">{{ 'pos.session.history.difference' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-s>
            <tr>
              <td class="text-sm whitespace-nowrap">{{ s.validatedAt | date:'short' }}</td>
              <td class="text-sm whitespace-nowrap text-gray-500">{{ s.closedAt | date:'short' }}</td>
              <td class="text-right">{{ fmtSvc.format(s.expectedClosing) }}</td>
              <td class="text-right font-medium">{{ fmtSvc.format(s.countedClosing) }}</td>
              <td class="text-right"
                  [class.text-red-600]="(s.difference || 0) < 0"
                  [class.text-green-600]="(s.difference || 0) > 0">
                {{ fmtSvc.format(s.difference || 0) }}
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="5" class="text-center text-gray-400 py-4 text-sm">
                {{ 'pos.session.validated.empty' | translate }}
              </td>
            </tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class SessionHistoryPage implements OnInit {
  protected readonly fmtSvc = inject(PosSettingsService);
  private readonly api = inject(PosApiService);

  protected readonly pendingSessions = signal<CashSession[]>([]);
  protected readonly validatedSessions = signal<CashSession[]>([]);
  protected readonly loadingPending = signal(false);
  protected readonly loadingValidated = signal(false);
  protected validatedDate: Date = new Date();

  protected readonly pendingTotal = computed(() =>
    this.pendingSessions().reduce((sum, s) => sum + Number(s.countedClosing ?? 0), 0),
  );
  protected readonly validatedTotal = computed(() =>
    this.validatedSessions().reduce((sum, s) => sum + Number(s.countedClosing ?? 0), 0),
  );

  async ngOnInit(): Promise<void> {
    await this.fmtSvc.load();
    await Promise.all([this.loadPendingSessions(), this.loadValidatedSessions()]);
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

  async loadValidatedSessions(): Promise<void> {
    this.loadingValidated.set(true);
    try {
      const iso = this.formatIsoDate(this.validatedDate);
      this.validatedSessions.set(await firstValueFrom(this.api.listMyValidatedSessions(iso)));
    } catch {
      this.validatedSessions.set([]);
    } finally {
      this.loadingValidated.set(false);
    }
  }

  private formatIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
