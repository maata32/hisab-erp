import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { firstValueFrom } from 'rxjs';

interface Invoice {
  id: string;
  number: string;
  customerName: string;
  issueDate: string;
  dueDate: string;
  status: string;
  currency: string;
  total: number;
  balance: number;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-invoice-list',
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, TagModule, ButtonModule],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'invoices.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'invoices.subtitle' | translate }}</p>
        </div>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="invoices()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'sales.issueDate' | translate }}</th>
              <th>{{ 'invoices.dueDate' | translate }}</th>
              <th>{{ 'sales.total' | translate }}</th>
              <th>{{ 'invoices.balance' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-inv>
            <tr>
              <td><span class="font-mono text-sm">{{ inv.number }}</span></td>
              <td>{{ inv.customerName }}</td>
              <td>{{ inv.issueDate | date:'mediumDate' }}</td>
              <td [class.text-red-600]="isOverdue(inv)">{{ inv.dueDate | date:'mediumDate' }}</td>
              <td class="text-right font-medium">{{ inv.total | number:'1.2-2' }} {{ inv.currency }}</td>
              <td class="text-right" [class.text-red-600]="inv.balance > 0">
                {{ inv.balance | number:'1.2-2' }} {{ inv.currency }}
              </td>
              <td><p-tag [value]="'sales.statuses.' + inv.status | translate" [severity]="statusSeverity(inv.status)" /></td>
              <td>
                <a [href]="'/api/v1/sales/invoices/' + inv.id + '/pdf'" target="_blank"
                   class="text-primary-600 hover:underline text-sm">
                  <i class="pi pi-file-pdf mr-1"></i>PDF
                </a>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'invoices.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class InvoiceListPage implements OnInit {
  private http = inject(HttpClient);

  protected invoices = signal<Invoice[]>([]);
  protected loading = signal(true);

  ngOnInit() { this.load(); }

  protected isOverdue(inv: Invoice): boolean {
    return inv.status !== 'PAID' && inv.status !== 'CANCELLED' && new Date(inv.dueDate) < new Date();
  }

  protected statusSeverity(status: string): Severity {
    return ({ DRAFT: 'secondary', ISSUED: 'info', PARTIAL: 'warning', PAID: 'success', OVERDUE: 'danger', CANCELLED: 'secondary' } as Record<string, Severity>)[status] ?? 'secondary';
  }

  private async load() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(this.http.get<{ content: Invoice[] }>('/api/v1/invoices'));
      this.invoices.set(res.content ?? []);
    } catch {
      this.invoices.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
