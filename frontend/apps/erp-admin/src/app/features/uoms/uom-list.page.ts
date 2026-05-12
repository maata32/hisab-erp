import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TabViewModule } from 'primeng/tabview';
import { firstValueFrom } from 'rxjs';

interface UomCategory { id: string; code: string; name: string; }
interface Uom {
  id: string; categoryId: string; categoryCode: string | null;
  code: string; name: string; ratioToBase: number; isBase: boolean; decimalPlaces: number;
}

@Component({
  selector: 'erp-admin-uom-list',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, TableModule, TagModule, TabViewModule],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'uoms.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'uoms.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200">
        <p-tabView>
          <p-tabPanel [header]="'uoms.tabs.units' | translate">
            <p-table [value]="uoms()" [loading]="loading()" stripedRows styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'uoms.code' | translate }}</th>
                  <th>{{ 'uoms.name' | translate }}</th>
                  <th>{{ 'uoms.category' | translate }}</th>
                  <th class="text-right">{{ 'uoms.ratio' | translate }}</th>
                  <th class="text-right">{{ 'uoms.decimals' | translate }}</th>
                  <th>{{ 'uoms.base' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-u>
                <tr>
                  <td><span class="font-mono text-sm">{{ u.code }}</span></td>
                  <td class="font-medium">{{ u.name }}</td>
                  <td>{{ u.categoryCode }}</td>
                  <td class="text-right">{{ u.ratioToBase | number:'1.0-6' }}</td>
                  <td class="text-right">{{ u.decimalPlaces }}</td>
                  <td>
                    @if (u.isBase) { <p-tag value="base" severity="info" /> }
                  </td>
                </tr>
              </ng-template>
              <ng-template pTemplate="emptymessage">
                <tr><td colspan="6" class="text-center text-gray-400 py-8">
                  {{ 'uoms.empty' | translate }}
                </td></tr>
              </ng-template>
            </p-table>
          </p-tabPanel>

          <p-tabPanel [header]="'uoms.tabs.categories' | translate">
            <p-table [value]="categories()" stripedRows styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'uoms.categoryCode' | translate }}</th>
                  <th>{{ 'uoms.categoryName' | translate }}</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-c>
                <tr>
                  <td><span class="font-mono text-sm">{{ c.code }}</span></td>
                  <td class="font-medium">{{ c.name }}</td>
                </tr>
              </ng-template>
            </p-table>
          </p-tabPanel>
        </p-tabView>
      </div>
    </div>
  `,
})
export class UomListPage implements OnInit {
  private http = inject(HttpClient);

  protected uoms = signal<Uom[]>([]);
  protected categories = signal<UomCategory[]>([]);
  protected loading = signal(true);

  ngOnInit() {
    this.load();
    this.loadCategories();
  }

  private async load() {
    this.loading.set(true);
    try {
      const list = await firstValueFrom(this.http.get<Uom[]>('/api/v1/uoms'));
      this.uoms.set(list ?? []);
    } catch { this.uoms.set([]); }
    finally { this.loading.set(false); }
  }

  private async loadCategories() {
    try {
      const list = await firstValueFrom(
        this.http.get<UomCategory[]>('/api/v1/uoms/categories')
      );
      this.categories.set(list ?? []);
    } catch { this.categories.set([]); }
  }
}
