import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';

interface Order {
  id: string;
  number: string;
  customerName: string;
  orderDate: string;
  status: string;
  currency: string;
  total: number;
  deliveryRequired: boolean;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-order-list',
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, TagModule],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'orders.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'orders.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="orders()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'sales.orderDate' | translate }}</th>
              <th>{{ 'sales.delivery' | translate }}</th>
              <th>{{ 'sales.total' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-o>
            <tr>
              <td><span class="font-mono text-sm">{{ o.number }}</span></td>
              <td>{{ o.customerName }}</td>
              <td>{{ o.orderDate | date:'mediumDate' }}</td>
              <td>
                <i [class]="o.deliveryRequired ? 'pi pi-check text-green-500' : 'pi pi-minus text-gray-300'"></i>
              </td>
              <td class="text-right font-medium">{{ o.total | number:'1.2-2' }} {{ o.currency }}</td>
              <td><p-tag [value]="'sales.statuses.' + o.status | translate" [severity]="statusSeverity(o.status)" /></td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">{{ 'orders.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class OrderListPage implements OnInit {
  private http = inject(HttpClient);

  protected orders = signal<Order[]>([]);
  protected loading = signal(true);

  ngOnInit() { this.load(); }

  protected statusSeverity(status: string): Severity {
    return ({ DRAFT: 'secondary', CONFIRMED: 'info', INVOICED: 'success', CANCELLED: 'danger' } as Record<string, Severity>)[status] ?? 'secondary';
  }

  private async load() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(this.http.get<{ content: Order[] }>('/api/v1/orders'));
      this.orders.set(res.content ?? []);
    } catch {
      this.orders.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
