import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { DropdownModule } from 'primeng/dropdown';
import { firstValueFrom } from 'rxjs';

interface Product {
  id: string;
  sku: string;
  barcode: string | null;
  name: string;
  description: string | null;
  baseUomId: string;
  defaultTaxRate: number;
  tracksLots: boolean;
  trackExpiry: boolean;
  shelfLifeDays: number | null;
  sellable: boolean;
  purchasable: boolean;
  active: boolean;
}

interface UomLite { id: string; code: string; name: string; }

@Component({
  selector: 'erp-admin-product-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, InputTextModule, DialogModule, InputNumberModule,
    CheckboxModule, DropdownModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'products.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'products.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'products.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <div class="mb-3">
          <span class="p-input-icon-left w-full sm:w-72">
            <i class="pi pi-search"></i>
            <input pInputText type="text" [placeholder]="'common.search' | translate"
                   (input)="onSearch($event)" class="w-full" />
          </span>
        </div>

        <p-table [value]="products()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'products.sku' | translate }}</th>
              <th>{{ 'products.name' | translate }}</th>
              <th>{{ 'products.barcode' | translate }}</th>
              <th>{{ 'products.tax' | translate }}</th>
              <th>{{ 'products.expiry' | translate }}</th>
              <th>{{ 'products.status' | translate }}</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-p>
            <tr>
              <td><span class="font-mono text-sm">{{ p.sku }}</span></td>
              <td class="font-medium">{{ p.name }}</td>
              <td>{{ p.barcode || '—' }}</td>
              <td class="text-right">{{ p.defaultTaxRate * 100 | number:'1.0-1' }} %</td>
              <td>
                @if (p.trackExpiry) {
                  <p-tag [value]="'products.tracksExpiry' | translate" severity="warning" />
                  <span class="text-xs text-gray-500 ml-2">
                    @if (p.shelfLifeDays) { {{ p.shelfLifeDays }} j }
                  </span>
                } @else { <span class="text-gray-300">—</span> }
              </td>
              <td>
                <p-tag [value]="(p.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="p.active ? 'success' : 'secondary'" />
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">
              {{ 'products.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="'products.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.sku' | translate }} *</label>
            <input pInputText [(ngModel)]="form.sku" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.barcode' | translate }}</label>
            <input pInputText [(ngModel)]="form.barcode" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'products.baseUom' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.baseUomId" [options]="uoms()" optionLabel="name"
                        optionValue="id" [filter]="true" filterBy="name,code"
                        [placeholder]="'common.select' | translate" styleClass="w-full" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'products.tax' | translate }}</label>
              <p-inputNumber [(ngModel)]="form.taxRatePercent" mode="decimal" [maxFractionDigits]="2"
                             suffix=" %" styleClass="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'products.shelfLifeDays' | translate }}</label>
              <p-inputNumber [(ngModel)]="form.shelfLifeDays" styleClass="w-full" />
            </div>
          </div>
          <div class="flex items-center gap-4 pt-1">
            <p-checkbox [(ngModel)]="form.trackExpiry" [binary]="true"
                        [label]="'products.trackExpiry' | translate" />
            <p-checkbox [(ngModel)]="form.sellable" [binary]="true"
                        [label]="'products.sellable' | translate" />
            <p-checkbox [(ngModel)]="form.purchasable" [binary]="true"
                        [label]="'products.purchasable' | translate" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class ProductListPage implements OnInit {
  private http = inject(HttpClient);

  protected products = signal<Product[]>([]);
  protected uoms = signal<UomLite[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected dialogOpen = false;

  protected form: {
    sku: string;
    name: string;
    barcode: string;
    baseUomId: string | null;
    taxRatePercent: number;
    shelfLifeDays: number | null;
    trackExpiry: boolean;
    sellable: boolean;
    purchasable: boolean;
  } = this.emptyForm();

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit() {
    this.load();
    this.loadUoms();
  }

  protected onSearch(e: Event) {
    const q = (e.target as HTMLInputElement).value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(q), 300);
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.dialogOpen = true;
  }

  protected async save() {
    if (!this.form.sku || !this.form.name || !this.form.baseUomId) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post('/api/v1/products', {
        sku: this.form.sku,
        name: this.form.name,
        barcode: this.form.barcode || null,
        baseUomId: this.form.baseUomId,
        defaultTaxRate: (this.form.taxRatePercent ?? 0) / 100,
        trackExpiry: this.form.trackExpiry,
        shelfLifeDays: this.form.shelfLifeDays,
        sellable: this.form.sellable,
        purchasable: this.form.purchasable,
      }));
      this.dialogOpen = false;
      this.load();
    } finally {
      this.saving.set(false);
    }
  }

  private async load(q?: string) {
    this.loading.set(true);
    try {
      const params = q ? `?q=${encodeURIComponent(q)}` : '';
      const res = await firstValueFrom(
        this.http.get<{ content: Product[] }>(`/api/v1/products${params}`)
      );
      this.products.set(res.content ?? []);
    } catch { this.products.set([]); }
    finally { this.loading.set(false); }
  }

  private async loadUoms() {
    try {
      const list = await firstValueFrom(this.http.get<UomLite[]>('/api/v1/uoms'));
      this.uoms.set(list ?? []);
    } catch { this.uoms.set([]); }
  }

  private emptyForm() {
    return {
      sku: '', name: '', barcode: '', baseUomId: null,
      taxRatePercent: 16, shelfLifeDays: null,
      trackExpiry: false, sellable: true, purchasable: true,
    };
  }
}
