import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { firstValueFrom } from 'rxjs';

interface Customer {
  id: string;
  code: string;
  name: string;
  type: string;
  email: string;
  phone: string;
  currency: string;
  creditLimit: number;
  active: boolean;
}

@Component({
  selector: 'erp-admin-customer-list',
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, TagModule, InputTextModule, ButtonModule],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'customers.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'customers.subtitle' | translate }}</p>
        </div>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <div class="mb-3">
          <span class="p-input-icon-left w-full sm:w-72">
            <i class="pi pi-search"></i>
            <input pInputText type="text" [placeholder]="'common.search' | translate"
                   (input)="onSearch($event)" class="w-full" />
          </span>
        </div>

        <p-table [value]="customers()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'customers.code' | translate }}</th>
              <th>{{ 'customers.name' | translate }}</th>
              <th>{{ 'customers.type' | translate }}</th>
              <th>{{ 'customers.email' | translate }}</th>
              <th>{{ 'customers.phone' | translate }}</th>
              <th>{{ 'customers.creditLimit' | translate }}</th>
              <th>{{ 'customers.status' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-c>
            <tr>
              <td><span class="font-mono text-sm">{{ c.code }}</span></td>
              <td class="font-medium">{{ c.name }}</td>
              <td>{{ 'customers.types.' + c.type | translate }}</td>
              <td>{{ c.email }}</td>
              <td>{{ c.phone }}</td>
              <td class="text-right">{{ c.creditLimit | number:'1.0-0' }} {{ c.currency }}</td>
              <td>
                <p-tag [value]="(c.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="c.active ? 'success' : 'secondary'" />
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">{{ 'customers.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class CustomerListPage implements OnInit {
  private http = inject(HttpClient);

  protected customers = signal<Customer[]>([]);
  protected loading = signal(true);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    this.load();
  }

  protected onSearch(e: Event) {
    const q = (e.target as HTMLInputElement).value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(q), 300);
  }

  private async load(q?: string) {
    this.loading.set(true);
    try {
      const params = q ? `?q=${encodeURIComponent(q)}` : '';
      const res = await firstValueFrom(
        this.http.get<{ content: Customer[] }>(`/api/v1/customers${params}`)
      );
      this.customers.set(res.content ?? []);
    } catch {
      this.customers.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
