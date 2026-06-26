import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { LoadingSpinnerComponent } from '@minierp/shared-ui';
import { isApiError } from '@minierp/shared-api';
import { firstValueFrom } from 'rxjs';

interface OrgType {
  id: string;
  code: string;
  label: string;
  sortOrder: number;
  active: boolean;
}

/** Super-admin CRUD for the configurable organization types. */
@Component({
  selector: 'erp-admin-organization-type-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule,
    TableModule, TagModule, ButtonModule, InputTextModule, DialogModule, TooltipModule,
    LoadingSpinnerComponent,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'orgTypes.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'orgTypes.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'orgTypes.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      @if (loading()) {
        <me-loading-spinner />
      } @else {
        <div class="bg-white rounded-lg border border-gray-200">
          <p-table [value]="rows()" styleClass="p-datatable-sm" responsiveLayout="scroll">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'orgTypes.code' | translate }}</th>
                <th>{{ 'orgTypes.label' | translate }}</th>
                <th class="text-center">{{ 'orgTypes.sortOrder' | translate }}</th>
                <th>{{ 'orgTypes.status' | translate }}</th>
                <th class="text-right">{{ 'common.actions' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-row>
              <tr>
                <td class="font-mono text-xs">{{ row.code }}</td>
                <td class="font-medium">{{ row.label }}</td>
                <td class="text-center">{{ row.sortOrder }}</td>
                <td>
                  @if (row.active) {
                    <p-tag [value]="'common.active' | translate" severity="success" />
                  } @else {
                    <p-tag [value]="'common.inactive' | translate" severity="secondary" />
                  }
                </td>
                <td class="text-right whitespace-nowrap">
                  <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                          [pTooltip]="'common.edit' | translate" (click)="openEdit(row)"></button>
                  <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.delete' | translate" (click)="remove(row)"></button>
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="5" class="text-center text-gray-400 py-8">
                {{ 'orgTypes.empty' | translate }}
              </td></tr>
            </ng-template>
          </p-table>
        </div>
      }
    </div>

    <!-- Create / edit -->
    <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '440px' }"
              [header]="(editing() ? 'orgTypes.edit' : 'orgTypes.create') | translate" [closable]="!saving()">
      <div class="space-y-3">
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'orgTypes.code' | translate }} *</label>
          <input pInputText [(ngModel)]="form.code" class="w-full uppercase" [disabled]="editing()"
                 [class.ng-invalid]="submitted() && !form.code.trim()"
                 [class.ng-dirty]="submitted() && !form.code.trim()" />
          @if (editing()) {
            <small class="text-gray-400">{{ 'orgTypes.codeLocked' | translate }}</small>
          }
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'orgTypes.label' | translate }} *</label>
          <input pInputText [(ngModel)]="form.label" class="w-full"
                 [class.ng-invalid]="submitted() && !form.label.trim()"
                 [class.ng-dirty]="submitted() && !form.label.trim()" />
        </div>
        <div>
          <label class="block text-sm font-medium mb-1">{{ 'orgTypes.sortOrder' | translate }}</label>
          <input pInputText type="number" [(ngModel)]="form.sortOrder" class="w-full" />
        </div>
        @if (editing()) {
          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" [(ngModel)]="form.active" />
            {{ 'orgTypes.active' | translate }}
          </label>
        }
      </div>
      <ng-template pTemplate="footer">
        <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                (click)="dialogOpen = false" [disabled]="saving()"></button>
        <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                (click)="save()" [loading]="saving()"></button>
      </ng-template>
    </p-dialog>
  `,
})
export class OrganizationTypeListPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly confirm = inject(ConfirmationService);
  private readonly toast = inject(MessageService);
  private readonly translate = inject(TranslateService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<OrgType[]>([]);
  protected readonly editing = signal(false);
  protected readonly submitted = signal(false);

  protected dialogOpen = false;
  protected form: { id: string | null; code: string; label: string; sortOrder: number; active: boolean } = {
    id: null, code: '', label: '', sortOrder: 0, active: true,
  };

  ngOnInit(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.rows.set((await firstValueFrom(this.http.get<OrgType[]>('/api/v1/organization-types'))) ?? []);
    } catch {
      this.rows.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  protected openCreate(): void {
    this.editing.set(false);
    this.submitted.set(false);
    this.form = { id: null, code: '', label: '', sortOrder: this.rows().length + 1, active: true };
    this.dialogOpen = true;
  }

  protected openEdit(row: OrgType): void {
    this.editing.set(true);
    this.submitted.set(false);
    this.form = { id: row.id, code: row.code, label: row.label, sortOrder: row.sortOrder, active: row.active };
    this.dialogOpen = true;
  }

  protected async save(): Promise<void> {
    this.submitted.set(true);
    if (!this.form.label.trim() || (!this.editing() && !this.form.code.trim())) return;
    this.saving.set(true);
    try {
      if (this.editing()) {
        await firstValueFrom(this.http.put(`/api/v1/organization-types/${this.form.id}`, {
          label: this.form.label.trim(),
          sortOrder: Number(this.form.sortOrder) || 0,
          active: this.form.active,
        }));
      } else {
        await firstValueFrom(this.http.post('/api/v1/organization-types', {
          code: this.form.code.trim().toUpperCase(),
          label: this.form.label.trim(),
          sortOrder: Number(this.form.sortOrder) || 0,
        }));
      }
      this.dialogOpen = false;
      this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t('common.success') });
      void this.load();
    } catch (err: unknown) {
      this.toast.add({ severity: 'error', summary: this.t('common.error'), detail: this.apiMessage(err) });
    } finally {
      this.saving.set(false);
    }
  }

  protected remove(row: OrgType): void {
    this.confirm.confirm({
      header: this.t('common.confirmation'),
      message: this.translate.instant('orgTypes.confirmDelete', { label: row.label }),
      icon: 'pi pi-trash',
      accept: async () => {
        try {
          await firstValueFrom(this.http.delete(`/api/v1/organization-types/${row.id}`));
          this.toast.add({ severity: 'success', summary: this.t('common.success'), detail: this.t('common.success') });
          void this.load();
        } catch (err: unknown) {
          this.toast.add({ severity: 'error', summary: this.t('common.error'), detail: this.apiMessage(err) });
        }
      },
    });
  }

  private apiMessage(err: unknown): string {
    const body = (err as { error?: unknown })?.error;
    return isApiError(body) ? body.message : this.t('common.error_generic');
  }

  private t(key: string): string {
    return this.translate.instant(key);
  }
}
