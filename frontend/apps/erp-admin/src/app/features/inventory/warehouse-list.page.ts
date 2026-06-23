import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { CheckboxModule } from 'primeng/checkbox';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

interface Warehouse {
  id: string;
  code: string;
  name: string;
  address: string | null;
  type: 'MAIN' | 'SECONDARY' | 'TRANSIT' | 'RETURN';
  defaultWarehouse: boolean;
  active: boolean;
}

@Component({
  selector: 'erp-admin-warehouse-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, InputTextModule, DialogModule, DropdownModule, CheckboxModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'warehouses.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'warehouses.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'warehouses.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="warehouses()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'warehouses.code' | translate }}</th>
              <th>{{ 'warehouses.name' | translate }}</th>
              <th>{{ 'warehouses.type' | translate }}</th>
              <th>{{ 'warehouses.address' | translate }}</th>
              <th>{{ 'warehouses.default' | translate }}</th>
              <th>{{ 'common.active' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-w>
            <tr>
              <td><span class="font-mono text-sm">{{ w.code }}</span></td>
              <td class="font-medium">{{ w.name }}</td>
              <td>
                <p-tag [value]="'warehouses.types.' + w.type | translate" severity="info" />
              </td>
              <td class="text-sm text-gray-600">{{ w.address || '—' }}</td>
              <td>
                @if (w.defaultWarehouse) {
                  <i class="pi pi-star-fill text-yellow-500"></i>
                } @else if (w.active) {
                  <button pButton icon="pi pi-star"
                          class="p-button-sm p-button-text p-button-rounded text-gray-400"
                          [pTooltip]="'warehouses.setDefault' | translate"
                          (click)="setDefault(w)"></button>
                }
              </td>
              <td>
                <p-tag [value]="(w.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="w.active ? 'success' : 'secondary'" />
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">
              {{ 'warehouses.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '450px' }"
                [header]="'warehouses.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'warehouses.code' | translate }} *</label>
            <input pInputText [(ngModel)]="form.code" class="w-full"
                   [class.ng-invalid]="codeInvalid()" [class.ng-dirty]="codeInvalid()" />
            @if (codeInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'warehouses.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full"
                   [class.ng-invalid]="nameInvalid()" [class.ng-dirty]="nameInvalid()" />
            @if (nameInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'warehouses.type' | translate }}</label>
            <p-dropdown [(ngModel)]="form.type" [options]="typeOptions"
                        optionLabel="label" optionValue="value" styleClass="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'warehouses.address' | translate }}</label>
            <input pInputText [(ngModel)]="form.address" class="w-full" />
          </div>
          <div class="flex items-center gap-4">
            <p-checkbox [(ngModel)]="form.defaultWarehouse" [binary]="true"
                        [label]="'warehouses.markDefault' | translate" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class WarehouseListPage implements OnInit {
  private http = inject(HttpClient);
  private confirmation = inject(ConfirmationService);
  private translate = inject(TranslateService);

  protected warehouses = signal<Warehouse[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;

  protected codeInvalid(): boolean { return this.submitted() && !this.form.code?.trim(); }
  protected nameInvalid(): boolean { return this.submitted() && !this.form.name?.trim(); }

  protected readonly typeOptions = [
    { value: 'MAIN', label: 'Principal' },
    { value: 'SECONDARY', label: 'Secondaire' },
    { value: 'TRANSIT', label: 'Transit' },
    { value: 'RETURN', label: 'Retours' },
  ];

  protected form = this.emptyForm();

  ngOnInit() { this.load(); }

  protected openCreate() {
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected setDefault(w: Warehouse) {
    this.confirmation.confirm({
      message: this.translate.instant('warehouses.setDefaultConfirm', { name: w.name }),
      header: this.translate.instant('common.confirmation'),
      icon: 'pi pi-star',
      accept: async () => {
        try {
          await firstValueFrom(
            this.http.post(`/api/v1/inventory/warehouses/${w.id}/set-default`, {})
          );
          this.load();
        } catch { /* errors handled globally */ }
      },
    });
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.code?.trim() || !this.form.name?.trim()) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/inventory/warehouses', this.form));
      this.dialogOpen = false;
      this.load();
    } finally { this.saving.set(false); }
  }

  private async load() {
    this.loading.set(true);
    try {
      const list = await firstValueFrom(
        this.http.get<Warehouse[]>('/api/v1/inventory/warehouses')
      );
      this.warehouses.set(list ?? []);
    } catch { this.warehouses.set([]); }
    finally { this.loading.set(false); }
  }

  private emptyForm() {
    return {
      code: '', name: '', address: '', type: 'MAIN' as const, defaultWarehouse: false,
    };
  }
}
