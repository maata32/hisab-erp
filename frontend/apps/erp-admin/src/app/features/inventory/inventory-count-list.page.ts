import { Component, OnInit, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { firstValueFrom } from 'rxjs';

interface InventoryCount {
  id: string;
  countNumber: string;
  warehouseId: string;
  status: 'DRAFT' | 'IN_PROGRESS' | 'VALIDATED' | 'CANCELLED';
  countDate: string;
  validatedAt: string | null;
  notes: string | null;
}

interface WarehouseLite { id: string; code: string; name: string; }

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-inventory-count-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, DialogModule, DropdownModule, InputTextModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'inventoryCounts.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'inventoryCounts.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'inventoryCounts.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="counts()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'inventoryCounts.number' | translate }}</th>
              <th>{{ 'inventoryCounts.warehouse' | translate }}</th>
              <th>{{ 'inventoryCounts.date' | translate }}</th>
              <th>{{ 'inventoryCounts.validated' | translate }}</th>
              <th>{{ 'inventoryCounts.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-c>
            <tr>
              <td><span class="font-mono text-sm">{{ c.countNumber }}</span></td>
              <td>{{ warehouseName(c.warehouseId) }}</td>
              <td>{{ c.countDate }}</td>
              <td>{{ c.validatedAt ? (c.validatedAt | date:'short') : '—' }}</td>
              <td>
                <p-tag [value]="'inventoryCounts.statuses.' + c.status | translate"
                       [severity]="statusSeverity(c.status)" />
              </td>
              <td>
                @if (c.status === 'DRAFT' || c.status === 'IN_PROGRESS') {
                  <button pButton icon="pi pi-check-circle" class="p-button-sm p-button-text p-button-success"
                          [label]="'inventoryCounts.validate' | translate"
                          (click)="validateCount(c.id)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">
              {{ 'inventoryCounts.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '450px' }"
                [header]="'inventoryCounts.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'inventoryCounts.warehouse' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.warehouseId" [options]="warehouses()"
                        optionLabel="name" optionValue="id"
                        [styleClass]="'w-full' + (warehouseInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (warehouseInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'inventoryCounts.date' | translate }} *</label>
            <input pInputText type="date" [(ngModel)]="form.countDate" class="w-full"
                   [class.ng-invalid]="dateInvalid()" [class.ng-dirty]="dateInvalid()" />
            @if (dateInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
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
export class InventoryCountListPage implements OnInit {
  private http = inject(HttpClient);

  protected counts = signal<InventoryCount[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;

  protected form = this.emptyForm();

  protected warehouseInvalid(): boolean { return this.submitted() && !this.form.warehouseId; }
  protected dateInvalid(): boolean { return this.submitted() && !this.form.countDate; }

  ngOnInit() {
    this.loadWarehouses();
    // auto-loaded by p-table lazy
  }

  protected statusSeverity(s: string): Severity {
    switch (s) {
      case 'VALIDATED': return 'success';
      case 'IN_PROGRESS': return 'info';
      case 'CANCELLED': return 'danger';
      default: return 'secondary';
    }
  }

  protected warehouseName(id: string): string {
    return this.warehouses().find((x) => x.id === id)?.name ?? '—';
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.form.countDate = new Date().toISOString().slice(0, 10);
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.warehouseId || !this.form.countDate) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/inventory/counts', {
        warehouseId: this.form.warehouseId,
        countDate: this.form.countDate,
        notes: this.form.notes || null,
      }));
      this.dialogOpen = false;
      this.reload();
    } finally { this.saving.set(false); }
  }

  protected async validateCount(id: string) {
    await firstValueFrom(this.http.post(`/api/v1/inventory/counts/${id}/validate`, {}));
    this.reload();
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: InventoryCount[]; totalElements: number }>(
          `/api/v1/inventory/counts?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.counts()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.counts.set(arr);
      this.total.set(totalElements);
    } catch {
      this.counts.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.counts.set([]);
    this.total.set(0);
    this.table?.reset();
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
    return { warehouseId: null as string | null, countDate: '', notes: '' };
  }
}
