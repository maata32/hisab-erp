import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';

interface PriceTier {
  id: string;
  code: string;
  name: string;
  defaultTier: boolean;
  active: boolean;
}

@Component({
  selector: 'erp-admin-price-tier-list',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, TableModule, TagModule],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'priceTiers.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'priceTiers.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="tiers()" [loading]="loading()" stripedRows styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'priceTiers.code' | translate }}</th>
              <th>{{ 'priceTiers.name' | translate }}</th>
              <th>{{ 'priceTiers.default' | translate }}</th>
              <th>{{ 'common.active' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-t>
            <tr>
              <td><span class="font-mono text-sm">{{ t.code }}</span></td>
              <td class="font-medium">{{ t.name }}</td>
              <td>
                @if (t.defaultTier) {
                  <p-tag [value]="'priceTiers.isDefault' | translate" severity="info" icon="pi pi-star-fill" />
                }
              </td>
              <td>
                <p-tag [value]="(t.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="t.active ? 'success' : 'secondary'" />
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="4" class="text-center text-gray-400 py-8">
              {{ 'priceTiers.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `,
})
export class PriceTierListPage implements OnInit {
  private http = inject(HttpClient);

  protected tiers = signal<PriceTier[]>([]);
  protected loading = signal(true);

  ngOnInit() { this.load(); }

  private async load() {
    this.loading.set(true);
    try {
      const list = await firstValueFrom(
        this.http.get<PriceTier[]>('/api/v1/pricing/tiers')
      );
      this.tiers.set(list ?? []);
    } catch { this.tiers.set([]); }
    finally { this.loading.set(false); }
  }
}
