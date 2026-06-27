import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { LoadingSpinnerComponent } from '@hisaberp/shared-ui';
import { firstValueFrom } from 'rxjs';

interface UserRow {
  id: string;
  email: string;
  fullName: string;
  preferredLanguage: string;
  active: boolean;
  lastLoginAt: string | null;
  locked: boolean;
  roleCodes: string[];
}

/** Cross-tenant view of a single organization's users (platform / super-admin console). */
@Component({
  selector: 'erp-admin-organization-users',
  standalone: true,
  imports: [
    CommonModule, RouterLink, TranslateModule,
    TableModule, TagModule, ButtonModule, DialogModule, TooltipModule,
    LoadingSpinnerComponent,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center gap-3">
        <a routerLink="/organizations" pButton icon="pi pi-arrow-left"
           class="p-button-text p-button-sm" [pTooltip]="'common.back' | translate"></a>
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'platform.users.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">
            <span class="font-medium">{{ orgName() || orgId }}</span>
            @if (orgCode()) { <span class="font-mono text-xs">({{ orgCode() }})</span> }
          </p>
        </div>
      </header>

      @if (loading()) {
        <me-loading-spinner />
      } @else {
        <div class="bg-white rounded-lg border border-gray-200">
          <p-table [value]="rows()" styleClass="p-datatable-sm" responsiveLayout="scroll">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'platform.users.email' | translate }}</th>
                <th>{{ 'platform.users.name' | translate }}</th>
                <th>{{ 'platform.users.roles' | translate }}</th>
                <th>{{ 'platform.users.status' | translate }}</th>
                <th class="text-right">{{ 'common.actions' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-row>
              <tr>
                <td class="font-mono text-xs">{{ row.email }}</td>
                <td class="font-medium">{{ row.fullName }}</td>
                <td class="text-xs">
                  @for (r of row.roleCodes; track r) {
                    <p-tag [value]="r" severity="info" styleClass="mr-1 mb-1" />
                  }
                  @if (!row.roleCodes.length) { <span class="text-gray-400">—</span> }
                </td>
                <td>
                  @if (row.locked) {
                    <p-tag [value]="'platform.users.locked' | translate" severity="danger" />
                  } @else if (row.active) {
                    <p-tag [value]="'common.active' | translate" severity="success" />
                  } @else {
                    <p-tag [value]="'common.inactive' | translate" severity="secondary" />
                  }
                </td>
                <td class="text-right whitespace-nowrap">
                  <button pButton icon="pi pi-key" class="p-button-sm p-button-text"
                          [pTooltip]="'platform.users.resetPassword' | translate"
                          (click)="resetPassword(row)"></button>
                  @if (row.locked) {
                    <button pButton icon="pi pi-lock-open" class="p-button-sm p-button-text p-button-warning"
                            [pTooltip]="'platform.users.unlock' | translate"
                            (click)="unlock(row)"></button>
                  }
                  @if (row.active) {
                    <button pButton icon="pi pi-ban" class="p-button-sm p-button-text p-button-danger"
                            [pTooltip]="'common.deactivate' | translate"
                            (click)="setActive(row, false)"></button>
                  } @else {
                    <button pButton icon="pi pi-check" class="p-button-sm p-button-text p-button-success"
                            [pTooltip]="'common.activate' | translate"
                            (click)="setActive(row, true)"></button>
                  }
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="5" class="text-center text-gray-400 py-8">
                {{ 'platform.users.empty' | translate }}
              </td></tr>
            </ng-template>
          </p-table>
        </div>
      }
    </div>

    <!-- Temporary password result -->
    <p-dialog [(visible)]="tempPwdOpen" [modal]="true" [style]="{ width: '420px' }"
              [header]="'platform.users.tempPasswordTitle' | translate" [closable]="true">
      <p class="text-sm text-gray-600 mb-2">{{ 'platform.users.tempPasswordHint' | translate }}</p>
      <div class="font-mono text-lg bg-gray-100 rounded p-3 text-center select-all">{{ tempPwd() }}</div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.close' | translate" icon="pi pi-check"
                (click)="tempPwdOpen = false"></button>
      </ng-template>
    </p-dialog>
  `,
})
export class OrganizationUsersPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly confirm = inject(ConfirmationService);
  private readonly toast = inject(MessageService);
  private readonly translate = inject(TranslateService);

  protected readonly orgId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly orgName = signal<string | null>(null);
  protected readonly orgCode = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly rows = signal<UserRow[]>([]);

  protected tempPwdOpen = false;
  protected readonly tempPwd = signal('');

  ngOnInit(): void {
    void this.loadOrg();
    void this.loadUsers();
  }

  private async loadOrg(): Promise<void> {
    try {
      const org = await firstValueFrom(
        this.http.get<{ name: string; code: string }>(`/api/v1/organizations/${this.orgId}`),
      );
      this.orgName.set(org?.name ?? null);
      this.orgCode.set(org?.code ?? null);
    } catch {
      /* header falls back to the id */
    }
  }

  private async loadUsers(): Promise<void> {
    this.loading.set(true);
    try {
      this.rows.set(
        (await firstValueFrom(
          this.http.get<UserRow[]>(`/api/v1/platform/organizations/${this.orgId}/users`),
        )) ?? [],
      );
    } catch {
      this.rows.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  protected resetPassword(row: UserRow): void {
    this.confirm.confirm({
      header: this.t('common.confirmation'),
      message: this.translate.instant('platform.users.confirm.resetPassword', { email: row.email }),
      icon: 'pi pi-key',
      accept: async () => {
        try {
          const res = await firstValueFrom(
            this.http.post<{ temporaryPassword: string }>(
              `/api/v1/platform/users/${row.id}/reset-password`, {}),
          );
          this.tempPwd.set(res.temporaryPassword);
          this.tempPwdOpen = true;
          void this.loadUsers();
        } catch {
          this.fail();
        }
      },
    });
  }

  protected unlock(row: UserRow): void {
    void this.act(`/api/v1/platform/users/${row.id}/unlock`, 'platform.users.toast.unlocked');
  }

  protected setActive(row: UserRow, active: boolean): void {
    const path = active ? 'activate' : 'deactivate';
    const toast = active ? 'platform.users.toast.activated' : 'platform.users.toast.deactivated';
    void this.act(`/api/v1/platform/users/${row.id}/${path}`, toast);
  }

  private async act(url: string, successKey: string): Promise<void> {
    try {
      await firstValueFrom(this.http.post(url, {}));
      this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t(successKey) });
      void this.loadUsers();
    } catch {
      this.fail();
    }
  }

  private fail(): void {
    this.toast.add({ severity: 'error', summary: this.t('common.error'), detail: this.t('common.error_generic') });
  }

  private t(key: string): string {
    return this.translate.instant(key);
  }
}
