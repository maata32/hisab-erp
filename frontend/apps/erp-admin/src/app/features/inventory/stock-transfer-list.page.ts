import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface StockTransfer {
  id: string;
  transferNumber: string;
  fromWarehouseId: string;
  toWarehouseId: string;
  status: 'DRAFT' | 'IN_TRANSIT' | 'COMPLETED' | 'CANCELLED';
  scheduledDate: string | null;
  completedAt: string | null;
  notes: string | null;
}

interface WarehouseLite { id: string; code: string; name: string; }

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-stock-transfer-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, DialogModule, DropdownModule, InputTextModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'stockTransfers.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'stockTransfers.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'stockTransfers.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="transfers()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'stockTransfers.number' | translate }}</th>
              <th>{{ 'stockTransfers.from' | translate }}</th>
              <th>{{ 'stockTransfers.to' | translate }}</th>
              <th>{{ 'stockTransfers.scheduled' | translate }}</th>
              <th>{{ 'stockTransfers.completed' | translate }}</th>
              <th>{{ 'stockTransfers.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-t>
            <tr>
              <td><span class="font-mono text-sm">{{ t.transferNumber }}</span></td>
              <td>{{ warehouseName(t.fromWarehouseId) }}</td>
              <td>{{ warehouseName(t.toWarehouseId) }}</td>
              <td>{{ t.scheduledDate || '—' }}</td>
              <td>{{ t.completedAt ? (t.completedAt | date:'short') : '—' }}</td>
              <td>
                <p-tag [value]="'stockTransfers.statuses.' + t.status | translate"
                       [severity]="statusSeverity(t.status)" />
              </td>
              <td>
                @if (t.status === 'DRAFT' || t.status === 'IN_TRANSIT') {
                  <button pButton icon="pi pi-check" class="p-button-sm p-button-text"
                          [pTooltip]="'stockTransfers.execute' | translate"
                          (click)="execute(t.id)"></button>
                  <button pButton icon="pi pi-times" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.cancel' | translate"
                          (click)="cancelTransfer(t.id)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">
              {{ 'stockTransfers.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '450px' }"
                [header]="'stockTransfers.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'stockTransfers.from' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.fromWarehouseId" [options]="warehouses()"
                        optionLabel="name" optionValue="id"
                        [styleClass]="'w-full' + (fromInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (fromInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'stockTransfers.to' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.toWarehouseId" [options]="warehouses()"
                        optionLabel="name" optionValue="id"
                        [styleClass]="'w-full' + (toInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (toInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'stockTransfers.scheduled' | translate }}</label>
            <input pInputText type="date" [(ngModel)]="form.scheduledDate" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="form.notes" class="w-full" />
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
export class StockTransferListPage implements OnInit {
  private http = inject(HttpClient);

  protected transfers = signal<StockTransfer[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;

  protected form = this.emptyForm();

  protected fromInvalid(): boolean { return this.submitted() && !this.form.fromWarehouseId; }
  protected toInvalid(): boolean { return this.submitted() && !this.form.toWarehouseId; }

  ngOnInit() {
    this.loadWarehouses();
    this.load();
  }

  protected statusSeverity(s: string): Severity {
    switch (s) {
      case 'COMPLETED': return 'success';
      case 'IN_TRANSIT': return 'info';
      case 'CANCELLED': return 'danger';
      default: return 'secondary';
    }
  }

  protected warehouseName(id: string): string {
    const w = this.warehouses().find((x) => x.id === id);
    return w ? w.name : '—';
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.fromWarehouseId || !this.form.toWarehouseId) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/inventory/stock-transfers', {
        fromWarehouseId: this.form.fromWarehouseId,
        toWarehouseId: this.form.toWarehouseId,
        scheduledDate: this.form.scheduledDate || null,
        notes: this.form.notes || null,
        lines: [],
      }));
      this.dialogOpen = false;
      this.load();
    } finally { this.saving.set(false); }
  }

  protected async execute(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/inventory/stock-transfers/${id}/execute`, {}));
    this.load();
  }

  protected async cancelTransfer(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/inventory/stock-transfers/${id}/cancel`, {}));
    this.load();
  }

  private async load() {
    this.loading.set(true);
    try {
      const list = await firstValueFrom(
        this.http.get<StockTransfer[]>('/api/v1/inventory/stock-transfers')
      );
      this.transfers.set(list ?? []);
    } catch { this.transfers.set([]); }
    finally { this.loading.set(false); }
  }

  private async loadWarehouses() {
    try {
      const list = await firstValueFrom(
        this.http.get<WarehouseLite[]>('/api/v1/inventory/warehouses')
      );
      this.warehouses.set(list ?? []);
    } catch { this.warehouses.set([]); }
  }

  private emptyForm() {
    return {
      fromWarehouseId: null as string | null,
      toWarehouseId: null as string | null,
      scheduledDate: '',
      notes: '',
    };
  }
}
