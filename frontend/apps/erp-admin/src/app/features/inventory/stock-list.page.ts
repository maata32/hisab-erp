import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';

interface StockRow {
  warehouseId: string;
  productId: string;
  productName: string;
  sku: string;
  qtyOnHand: number;
  qtyReserved: number;
  qtyAvailable: number;
  averageCost: number;
  stockValue: number;
  isLowStock: boolean;
}

interface WarehouseLite { id: string; code: string; name: string; }

@Component({
  selector: 'erp-admin-stock-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, DropdownModule, TagModule,
  ],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'stock.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'stock.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <div class="mb-3 flex items-center gap-3 flex-wrap">
          <label class="text-sm font-medium">{{ 'stock.filterByWarehouse' | translate }}</label>
          <p-dropdown [(ngModel)]="selectedWarehouseId" [options]="warehouses()"
                      optionLabel="name" optionValue="id" [showClear]="true"
                      [placeholder]="'stock.allWarehouses' | translate"
                      (onChange)="load()" styleClass="w-64" />
        </div>

        <p-table [value]="rows()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'stock.sku' | translate }}</th>
              <th>{{ 'stock.product' | translate }}</th>
              <th class="text-right">{{ 'stock.qtyOnHand' | translate }}</th>
              <th class="text-right">{{ 'stock.qtyReserved' | translate }}</th>
              <th class="text-right">{{ 'stock.qtyAvailable' | translate }}</th>
              <th class="text-right">{{ 'stock.avgCost' | translate }}</th>
              <th class="text-right">{{ 'stock.value' | translate }}</th>
              <th>{{ 'stock.status' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-r>
            <tr>
              <td><span class="font-mono text-sm">{{ r.sku }}</span></td>
              <td class="font-medium">{{ r.productName }}</td>
              <td class="text-right">{{ r.qtyOnHand | number:'1.0-3' }}</td>
              <td class="text-right text-gray-500">{{ r.qtyReserved | number:'1.0-3' }}</td>
              <td class="text-right font-semibold">{{ r.qtyAvailable | number:'1.0-3' }}</td>
              <td class="text-right text-gray-600">{{ r.averageCost | number:'1.0-2' }}</td>
              <td class="text-right font-semibold">{{ r.stockValue | number:'1.0-0' }}</td>
              <td>
                @if (r.isLowStock) {
                  <p-tag [value]="'stock.low' | translate" severity="danger" icon="pi pi-exclamation-triangle" />
                } @else {
                  <p-tag [value]="'stock.ok' | translate" severity="success" />
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">
              {{ 'stock.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class StockListPage implements OnInit {
  private http = inject(HttpClient);

  protected rows = signal<StockRow[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  protected loading = signal(true);
  protected selectedWarehouseId: string | null = null;

  ngOnInit() {
    this.loadWarehouses();
    this.load();
  }

  protected async load() {
    this.loading.set(true);
    try {
      const params = this.selectedWarehouseId
        ? `?warehouseId=${this.selectedWarehouseId}`
        : '';
      const list = await firstValueFrom(
        this.http.get<StockRow[]>(`/api/v1/reports/stock${params}`)
      );
      this.rows.set(list ?? []);
    } catch { this.rows.set([]); }
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
}
