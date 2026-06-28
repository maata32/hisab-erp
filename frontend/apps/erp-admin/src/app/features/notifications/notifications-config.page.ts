import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DropdownModule } from 'primeng/dropdown';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface EventDefinition {
  code: string;
  name: string;
  description: string;
  category: string;
  defaultChannels: string[];
  defaultRecipients: string[];
  defaultEnabled: boolean;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  variables: string[];
}

interface TenantConfig {
  eventCode: string;
  enabled: boolean;
  channels: string | null;
  recipients: string | null;
  customRoles: string | null;
  customUsers: string | null;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-notifications-config',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, CheckboxModule, DropdownModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'notifConfig.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'notifConfig.subtitle' | translate }}</p>
        </div>
        <div class="flex items-center gap-2">
          <p-dropdown [(ngModel)]="categoryFilter" [options]="categoryOptions()"
                      optionLabel="label" optionValue="value"
                      [placeholder]="'notifConfig.filterCategory' | translate"
                      [showClear]="true" styleClass="w-48" />
          <button pButton icon="pi pi-refresh" [label]="'notifConfig.reset' | translate"
                  class="p-button-sm p-button-outlined" (click)="reset()"></button>
        </div>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="filtered()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'notifConfig.event' | translate }}</th>
              <th>{{ 'notifConfig.category' | translate }}</th>
              <th>{{ 'notifConfig.severity' | translate }}</th>
              <th class="text-center">{{ 'notifConfig.enabled' | translate }}</th>
              <th>{{ 'notifConfig.channels' | translate }}</th>
              <th>{{ 'notifConfig.recipients' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-e>
            <tr>
              <td>
                <div class="font-medium">{{ e.name }}</div>
                <div class="text-xs text-gray-500 font-mono">{{ e.code }}</div>
              </td>
              <td><p-tag [value]="e.category" severity="info" /></td>
              <td><p-tag [value]="e.severity" [severity]="severityColor(e.severity)" /></td>
              <td class="text-center">
                <p-checkbox [(ngModel)]="enabledMap[e.code]" [binary]="true"
                            (onChange)="onToggle(e)" />
              </td>
              <td>
                <div class="flex flex-wrap gap-1">
                  @for (ch of e.defaultChannels; track ch) {
                    <span class="px-2 py-0.5 bg-blue-50 text-blue-700 rounded text-xs">{{ ch }}</span>
                  }
                </div>
              </td>
              <td>
                <div class="flex flex-wrap gap-1">
                  @for (r of e.defaultRecipients; track r) {
                    <span class="px-2 py-0.5 bg-gray-100 text-gray-700 rounded text-xs">{{ r }}</span>
                  }
                </div>
              </td>
              <td>
                <button pButton icon="pi pi-cog" class="p-button-sm p-button-text"
                        [pTooltip]="'notifConfig.advanced' | translate"
                        (click)="openAdvanced(e)"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">
              {{ 'notifConfig.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class NotificationsConfigPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);

  protected events = signal<EventDefinition[]>([]);
  protected configs = signal<TenantConfig[]>([]);
  protected loading = signal(true);
  protected categoryFilter: string | null = null;

  protected enabledMap: Record<string, boolean> = {};

  protected categoryOptions = computed(() => {
    const set = new Set(this.events().map((e) => e.category));
    return Array.from(set).sort().map((c) => ({ value: c, label: c }));
  });

  protected filtered = computed(() => {
    const evts = this.events();
    if (!this.categoryFilter) return evts;
    return evts.filter((e) => e.category === this.categoryFilter);
  });

  ngOnInit() { this.load(); }

  protected severityColor(s: string): Severity {
    return s === 'CRITICAL' ? 'danger' : s === 'WARNING' ? 'warning' : 'info';
  }

  protected async onToggle(e: EventDefinition) {
    const enabled = this.enabledMap[e.code];
    const existing = this.configs().find((c) => c.eventCode === e.code);
    await firstValueFrom(this.http.put(`/api/v1/notifications/config/${e.code}`, {
      enabled,
      channels: existing?.channels ?? null,
      recipients: existing?.recipients ?? null,
      customRoles: existing?.customRoles ?? null,
      customUsers: existing?.customUsers ?? null,
    }));
    this.loadConfigs();
  }

  protected openAdvanced(e: EventDefinition) {
    this.confirmation.confirm({
      message: this.i18n.instant('notifConfig.advancedInfo', { code: e.code }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-info-circle',
      acceptLabel: this.i18n.instant('common.ok'),
      rejectVisible: false,
      acceptButtonStyleClass: 'p-button-sm',
      accept: () => { /* info only */ },
    });
  }

  protected reset() {
    this.confirmation.confirm({
      message: this.i18n.instant('notifConfig.resetConfirm'),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.post('/api/v1/notifications/config/reset', {}));
        this.load();
      },
    });
  }

  private async load() {
    this.loading.set(true);
    try {
      const events = await firstValueFrom(
        this.http.get<EventDefinition[]>('/api/v1/notifications/events')
      );
      this.events.set(events ?? []);
      await this.loadConfigs();
    } catch { this.events.set([]); }
    finally { this.loading.set(false); }
  }

  private async loadConfigs() {
    try {
      const list = await firstValueFrom(
        this.http.get<TenantConfig[]>('/api/v1/notifications/config')
      );
      this.configs.set(list ?? []);
      const byCode = new Map(list.map((c) => [c.eventCode, c.enabled] as const));
      for (const e of this.events()) {
        const configured = byCode.get(e.code);
        this.enabledMap[e.code] = configured ?? e.defaultEnabled;
      }
    } catch { this.configs.set([]); }
  }
}
