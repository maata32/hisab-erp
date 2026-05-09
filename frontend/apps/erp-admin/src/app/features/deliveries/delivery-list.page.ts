import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { firstValueFrom } from 'rxjs';

interface Delivery {
  id: string;
  number: string;
  customerName: string;
  scheduledDate: string;
  deliveredAt: string;
  status: string;
  address: string;
  lines: { quantityOrdered: number; quantityDelivered: number }[];
}

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-delivery-list',
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, TagModule, ButtonModule],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'deliveries.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'deliveries.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="deliveries()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'deliveries.scheduledDate' | translate }}</th>
              <th>{{ 'deliveries.deliveredAt' | translate }}</th>
              <th>{{ 'deliveries.progress' | translate }}</th>
              <th>{{ 'deliveries.address' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-d>
            <tr>
              <td><span class="font-mono text-sm">{{ d.number }}</span></td>
              <td>{{ d.customerName }}</td>
              <td>{{ d.scheduledDate | date:'mediumDate' }}</td>
              <td>{{ d.deliveredAt | date:'medium' }}</td>
              <td>
                <span class="text-sm text-gray-600">
                  {{ totalDelivered(d) }}/{{ totalOrdered(d) }}
                </span>
              </td>
              <td class="max-w-xs truncate text-sm text-gray-600">{{ d.address }}</td>
              <td><p-tag [value]="'deliveries.statuses.' + d.status | translate" [severity]="statusSeverity(d.status)" /></td>
              <td>
                <a [href]="'/api/v1/deliveries/' + d.id + '/pdf'" target="_blank"
                   class="text-primary-600 hover:underline text-sm">
                  <i class="pi pi-file-pdf mr-1"></i>BL
                </a>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'deliveries.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class DeliveryListPage implements OnInit {
  private http = inject(HttpClient);

  protected deliveries = signal<Delivery[]>([]);
  protected loading = signal(true);

  ngOnInit() { this.load(); }

  protected totalOrdered(d: Delivery): number {
    return d.lines?.reduce((s, l) => s + l.quantityOrdered, 0) ?? 0;
  }

  protected totalDelivered(d: Delivery): number {
    return d.lines?.reduce((s, l) => s + l.quantityDelivered, 0) ?? 0;
  }

  protected statusSeverity(status: string): Severity {
    return ({ PENDING: 'secondary', IN_PROGRESS: 'info', PARTIAL: 'warn', DELIVERED: 'success', CANCELLED: 'danger' } as Record<string, Severity>)[status] ?? 'secondary';
  }

  private async load() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(this.http.get<{ content: Delivery[] }>('/api/v1/deliveries'));
      this.deliveries.set(res.content ?? []);
    } catch {
      this.deliveries.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
