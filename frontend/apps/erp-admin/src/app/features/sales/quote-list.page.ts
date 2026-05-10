import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { firstValueFrom } from 'rxjs';

interface Quote {
  id: string;
  number: string;
  customerName: string;
  issueDate: string;
  validUntil: string;
  status: string;
  currency: string;
  total: number;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-quote-list',
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, TagModule, ButtonModule],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'quotes.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'quotes.subtitle' | translate }}</p>
        </div>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="quotes()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'sales.issueDate' | translate }}</th>
              <th>{{ 'sales.validUntil' | translate }}</th>
              <th>{{ 'sales.total' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-q>
            <tr>
              <td><span class="font-mono text-sm">{{ q.number }}</span></td>
              <td>{{ q.customerName }}</td>
              <td>{{ q.issueDate | date:'mediumDate' }}</td>
              <td>{{ q.validUntil | date:'mediumDate' }}</td>
              <td class="text-right font-medium">{{ q.total | number:'1.2-2' }} {{ q.currency }}</td>
              <td><p-tag [value]="'sales.statuses.' + q.status | translate" [severity]="statusSeverity(q.status)" /></td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">{{ 'quotes.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class QuoteListPage implements OnInit {
  private http = inject(HttpClient);

  protected quotes = signal<Quote[]>([]);
  protected loading = signal(true);

  ngOnInit() { this.load(); }

  protected statusSeverity(status: string): Severity {
    return ({ DRAFT: 'secondary', SENT: 'info', ACCEPTED: 'success', REJECTED: 'danger', EXPIRED: 'warning' } as Record<string, Severity>)[status] ?? 'secondary';
  }

  private async load() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(this.http.get<{ content: Quote[] }>('/api/v1/quotes'));
      this.quotes.set(res.content ?? []);
    } catch {
      this.quotes.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
