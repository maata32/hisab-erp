import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { MoneyPipe } from '@hisaberp/shared-i18n';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TabViewModule } from 'primeng/tabview';
import { firstValueFrom } from 'rxjs';

interface DirectionRow {
  saleDay: string; saleMonth: string; saleYear: number;
  saleCount: number; revenue: number; grossProfit: number;
}
interface CaisseRow {
  cashierUserId: string; registerId: string; saleDay: string;
  paymentMethod: string; saleCount: number; totalRevenue: number; avgTicket: number;
}
interface StockRow {
  warehouseId: string; productId: string; productName: string; sku: string;
  qtyOnHand: number; qtyReserved: number; qtyAvailable: number;
  averageCost: number; stockValue: number; isLowStock: boolean;
}
interface ExpiryRow {
  lotId: string; lotNumber: string; productName: string;
  expirationDate: string; daysRemaining: number;
  quantityRemaining: number; status: string; riskLevel: string;
}
interface PaymentRow {
  paymentDay: string; paymentMonth: string; type: string; method: string;
  paymentCount: number; totalAmount: number;
}
interface DeliveryRow {
  status: string; deliveryCount: number; onTimeCount: number; lateCount: number;
}
interface ExpiryRiskRow {
  riskLevel: string; lotCount: number; quantityAtRisk: number; valueAtRisk: number;
}
interface AgingRow {
  customerId: string; customerCode: string; customerName: string;
  current: number; d1to30: number; d31to60: number; d61to90: number;
  d90plus: number; totalOutstanding: number;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-reporting',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule, TabViewModule],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'reporting.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'reporting.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200">
        <p-tabView [(activeIndex)]="activeTab" (onChange)="onTabChange()">
          <p-tabPanel [header]="'reporting.tabs.direction' | translate">
            <p-table [value]="direction()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                     styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'reporting.day' | translate }}</th>
                  <th class="text-right">{{ 'reporting.sales' | translate }}</th>
                  <th class="text-right">{{ 'reporting.revenue' | translate }}</th>
                  <th class="text-right">{{ 'reporting.grossProfit' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-r>
                <tr>
                  <td>{{ r.saleDay }}</td>
                  <td class="text-right">{{ r.saleCount }}</td>
                  <td class="text-right font-semibold">{{ r.revenue | money }}</td>
                  <td class="text-right">{{ r.grossProfit | money }}</td>
                </tr>
              </ng-template>
            </p-table>
          </p-tabPanel>

          <p-tabPanel [header]="'reporting.tabs.caisse' | translate">
            <p-table [value]="caisse()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                     styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'reporting.day' | translate }}</th>
                  <th>{{ 'reporting.paymentMethod' | translate }}</th>
                  <th class="text-right">{{ 'reporting.sales' | translate }}</th>
                  <th class="text-right">{{ 'reporting.revenue' | translate }}</th>
                  <th class="text-right">{{ 'reporting.avgTicket' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-r>
                <tr>
                  <td>{{ r.saleDay }}</td>
                  <td>{{ r.paymentMethod }}</td>
                  <td class="text-right">{{ r.saleCount }}</td>
                  <td class="text-right font-semibold">{{ r.totalRevenue | money }}</td>
                  <td class="text-right">{{ r.avgTicket | number:'1.0-2' }}</td>
                </tr>
              </ng-template>
            </p-table>
          </p-tabPanel>

          <p-tabPanel [header]="'reporting.tabs.stock' | translate">
            <p-table [value]="stock()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                     styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'reporting.product' | translate }}</th>
                  <th class="text-right">{{ 'reporting.onHand' | translate }}</th>
                  <th class="text-right">{{ 'reporting.value' | translate }}</th>
                  <th>{{ 'reporting.status' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-r>
                <tr>
                  <td><span class="font-mono text-xs">{{ r.sku }}</span> · {{ r.productName }}</td>
                  <td class="text-right">{{ r.qtyOnHand | number:'1.0-3' }}</td>
                  <td class="text-right font-semibold">{{ r.stockValue | money }}</td>
                  <td>
                    @if (r.isLowStock) {
                      <p-tag [value]="'reporting.lowStock' | translate" severity="danger" />
                    } @else {
                      <p-tag [value]="'reporting.ok' | translate" severity="success" />
                    }
                  </td>
                </tr>
              </ng-template>
            </p-table>
          </p-tabPanel>

          <p-tabPanel [header]="'reporting.tabs.expiry' | translate">
            <p-table [value]="expiry()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                     styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'reporting.lot' | translate }}</th>
                  <th>{{ 'reporting.product' | translate }}</th>
                  <th>{{ 'reporting.expirationDate' | translate }}</th>
                  <th class="text-right">{{ 'reporting.daysLeft' | translate }}</th>
                  <th class="text-right">{{ 'reporting.remaining' | translate }}</th>
                  <th>{{ 'reporting.risk' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-r>
                <tr>
                  <td><span class="font-mono text-xs">{{ r.lotNumber }}</span></td>
                  <td>{{ r.productName }}</td>
                  <td>{{ r.expirationDate }}</td>
                  <td class="text-right">{{ r.daysRemaining }}</td>
                  <td class="text-right">{{ r.quantityRemaining | number:'1.0-3' }}</td>
                  <td><p-tag [value]="r.riskLevel" [severity]="riskSeverity(r.riskLevel)" /></td>
                </tr>
              </ng-template>
            </p-table>

            <div *ngIf="expiryRisk().length" class="mt-6">
              <h3 class="text-sm font-semibold text-gray-700 mb-2">
                {{ 'reporting.valueAtRisk' | translate }}
              </h3>
              <p-table [value]="expiryRisk()" stripedRows styleClass="p-datatable-sm">
                <ng-template pTemplate="header">
                  <tr>
                    <th>{{ 'reporting.risk' | translate }}</th>
                    <th class="text-right">{{ 'reporting.lots' | translate }}</th>
                    <th class="text-right">{{ 'reporting.qtyAtRisk' | translate }}</th>
                    <th class="text-right">{{ 'reporting.valueAtRisk' | translate }}</th>
                  </tr>
                </ng-template>
                <ng-template pTemplate="body" let-r>
                  <tr>
                    <td><p-tag [value]="r.riskLevel" [severity]="riskSeverity(r.riskLevel)" /></td>
                    <td class="text-right">{{ r.lotCount }}</td>
                    <td class="text-right">{{ r.quantityAtRisk | number:'1.0-3' }}</td>
                    <td class="text-right font-semibold">{{ r.valueAtRisk | money }}</td>
                  </tr>
                </ng-template>
              </p-table>
            </div>
          </p-tabPanel>

          <p-tabPanel [header]="'reporting.tabs.payments' | translate">
            <p-table [value]="payments()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                     styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'reporting.day' | translate }}</th>
                  <th>{{ 'reporting.type' | translate }}</th>
                  <th>{{ 'reporting.method' | translate }}</th>
                  <th class="text-right">{{ 'reporting.count' | translate }}</th>
                  <th class="text-right">{{ 'reporting.total' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-r>
                <tr>
                  <td>{{ r.paymentDay }}</td>
                  <td>{{ r.type }}</td>
                  <td>{{ r.method }}</td>
                  <td class="text-right">{{ r.paymentCount }}</td>
                  <td class="text-right font-semibold">{{ r.totalAmount | money }}</td>
                </tr>
              </ng-template>
            </p-table>
          </p-tabPanel>

          <p-tabPanel [header]="'reporting.tabs.deliveries' | translate">
            <p-table [value]="deliveries()" [loading]="loading()" stripedRows styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'reporting.status' | translate }}</th>
                  <th class="text-right">{{ 'reporting.count' | translate }}</th>
                  <th class="text-right">{{ 'reporting.onTime' | translate }}</th>
                  <th class="text-right">{{ 'reporting.late' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-r>
                <tr>
                  <td><p-tag [value]="r.status" severity="info" /></td>
                  <td class="text-right">{{ r.deliveryCount }}</td>
                  <td class="text-right text-green-600">{{ r.onTimeCount }}</td>
                  <td class="text-right text-red-600">{{ r.lateCount }}</td>
                </tr>
              </ng-template>
            </p-table>
          </p-tabPanel>

          <p-tabPanel [header]="'reporting.tabs.aging' | translate">
            <p-table [value]="aging()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                     styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'reporting.customer' | translate }}</th>
                  <th class="text-right">{{ 'reporting.aging.current' | translate }}</th>
                  <th class="text-right">{{ 'reporting.aging.d1to30' | translate }}</th>
                  <th class="text-right">{{ 'reporting.aging.d31to60' | translate }}</th>
                  <th class="text-right">{{ 'reporting.aging.d61to90' | translate }}</th>
                  <th class="text-right">{{ 'reporting.aging.d90plus' | translate }}</th>
                  <th class="text-right">{{ 'reporting.aging.total' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-r>
                <tr>
                  <td><span class="font-mono text-xs">{{ r.customerCode }}</span> · {{ r.customerName }}</td>
                  <td class="text-right">{{ r.current | money }}</td>
                  <td class="text-right">{{ r.d1to30 | money }}</td>
                  <td class="text-right">{{ r.d31to60 | money }}</td>
                  <td class="text-right text-orange-600">{{ r.d61to90 | money }}</td>
                  <td class="text-right text-red-600 font-semibold">{{ r.d90plus | money }}</td>
                  <td class="text-right font-bold">{{ r.totalOutstanding | money }}</td>
                </tr>
              </ng-template>
            </p-table>
          </p-tabPanel>
        </p-tabView>
      </div>
    </div>
  `,
})
export class ReportingPage implements OnInit {
  private http = inject(HttpClient);

  protected loading = signal(false);
  protected activeTab = 0;

  protected direction = signal<DirectionRow[]>([]);
  protected caisse = signal<CaisseRow[]>([]);
  protected stock = signal<StockRow[]>([]);
  protected expiry = signal<ExpiryRow[]>([]);
  protected expiryRisk = signal<ExpiryRiskRow[]>([]);
  protected payments = signal<PaymentRow[]>([]);
  protected deliveries = signal<DeliveryRow[]>([]);
  protected aging = signal<AgingRow[]>([]);

  ngOnInit() { this.loadFor(0); }

  protected onTabChange() { this.loadFor(this.activeTab); }

  protected riskSeverity(level: string): Severity {
    return level === 'CRITICAL' ? 'danger'
         : level === 'HIGH' ? 'warning'
         : level === 'MEDIUM' ? 'info' : 'success';
  }

  private async loadFor(idx: number) {
    this.loading.set(true);
    try {
      switch (idx) {
        case 0:
          this.direction.set(await this.fetch<DirectionRow[]>('/api/v1/reports/direction'));
          break;
        case 1:
          this.caisse.set(await this.fetch<CaisseRow[]>('/api/v1/reports/caisse'));
          break;
        case 2:
          this.stock.set(await this.fetch<StockRow[]>('/api/v1/reports/stock'));
          break;
        case 3:
          this.expiry.set(await this.fetch<ExpiryRow[]>('/api/v1/reports/expiry'));
          this.expiryRisk.set(await this.fetch<ExpiryRiskRow[]>('/api/v1/reports/expiry-risk'));
          break;
        case 4:
          this.payments.set(await this.fetch<PaymentRow[]>('/api/v1/reports/payments'));
          break;
        case 5:
          this.deliveries.set(await this.fetch<DeliveryRow[]>('/api/v1/reports/deliveries'));
          break;
        case 6:
          this.aging.set(await this.fetch<AgingRow[]>('/api/v1/reports/aging'));
          break;
      }
    } finally { this.loading.set(false); }
  }

  private async fetch<T>(url: string): Promise<T> {
    try { return (await firstValueFrom(this.http.get<T>(url))) ?? ([] as unknown as T); }
    catch { return [] as unknown as T; }
  }
}
