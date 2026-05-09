import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { firstValueFrom } from 'rxjs';

interface Payment {
  id: string;
  number: string;
  partyName: string;
  paymentDate: string;
  amount: number;
  currency: string;
  method: string;
  status: string;
  reference: string;
}

type Severity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-payment-list',
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, TagModule, ButtonModule],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'payments.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'payments.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="payments()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'sales.number' | translate }}</th>
              <th>{{ 'payments.party' | translate }}</th>
              <th>{{ 'payments.date' | translate }}</th>
              <th>{{ 'payments.amount' | translate }}</th>
              <th>{{ 'payments.method' | translate }}</th>
              <th>{{ 'payments.reference' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-p>
            <tr>
              <td><span class="font-mono text-sm">{{ p.number }}</span></td>
              <td>{{ p.partyName }}</td>
              <td>{{ p.paymentDate | date:'mediumDate' }}</td>
              <td class="text-right font-medium">{{ p.amount | number:'1.2-2' }} {{ p.currency }}</td>
              <td>{{ 'payments.methods.' + p.method | translate }}</td>
              <td class="text-sm text-gray-500">{{ p.reference }}</td>
              <td><p-tag [value]="'payments.statuses.' + p.status | translate" [severity]="statusSeverity(p.status)" /></td>
              <td>
                <a [href]="'/api/v1/payments/' + p.id + '/receipt.pdf'" target="_blank"
                   class="text-primary-600 hover:underline text-sm">
                  <i class="pi pi-file-pdf mr-1"></i>{{ 'payments.receipt' | translate }}
                </a>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'payments.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class PaymentListPage implements OnInit {
  private http = inject(HttpClient);

  protected payments = signal<Payment[]>([]);
  protected loading = signal(true);

  ngOnInit() { this.load(); }

  protected statusSeverity(status: string): Severity {
    return ({ DRAFT: 'secondary', CONFIRMED: 'success', CANCELLED: 'danger' } as Record<string, Severity>)[status] ?? 'secondary';
  }

  private async load() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(this.http.get<{ content: Payment[] }>('/api/v1/payments'));
      this.payments.set(res.content ?? []);
    } catch {
      this.payments.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
