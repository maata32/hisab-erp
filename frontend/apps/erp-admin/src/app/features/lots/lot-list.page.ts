import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TabViewModule } from 'primeng/tabview';
import { DropdownModule } from 'primeng/dropdown';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface Lot {
  id: string;
  productId: string;
  warehouseId: string;
  lotNumber: string;
  productionDate: string | null;
  expirationDate: string;
  initialQuantity: number;
  quantityRemaining: number;
  uomId: string;
  status: 'ACTIVE' | 'EXHAUSTED' | 'EXPIRED' | 'DESTROYED' | 'BLOCKED';
  blockedReason: string | null;
  notes: string | null;
  daysUntilExpiry: number;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-lot-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule, ButtonModule,
    TabViewModule, DropdownModule, DialogModule, InputTextModule, InputNumberModule,
    TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'lots.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'lots.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200">
        <p-tabView [(activeIndex)]="activeTab" (onChange)="onTabChange()">
          <p-tabPanel [header]="'lots.tabs.all' | translate">
            <ng-container *ngTemplateOutlet="lotTable; context: { rows: lots() }"></ng-container>
          </p-tabPanel>
          <p-tabPanel [header]="'lots.tabs.expiring' | translate">
            <div class="mb-3 flex items-center gap-2">
              <label class="text-sm">{{ 'lots.daysWindow' | translate }}</label>
              <p-dropdown [(ngModel)]="daysWindow" [options]="dayOptions"
                          optionLabel="label" optionValue="value"
                          (onChange)="loadExpiring()" styleClass="w-32" />
            </div>
            <ng-container *ngTemplateOutlet="lotTable; context: { rows: expiring() }"></ng-container>
          </p-tabPanel>
          <p-tabPanel [header]="'lots.tabs.expired' | translate">
            <ng-container *ngTemplateOutlet="lotTable; context: { rows: expired() }"></ng-container>
          </p-tabPanel>
        </p-tabView>
      </div>

      <ng-template #lotTable let-rows="rows">
        <p-table [value]="rows" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'lots.number' | translate }}</th>
              <th>{{ 'lots.expirationDate' | translate }}</th>
              <th class="text-right">{{ 'lots.daysLeft' | translate }}</th>
              <th class="text-right">{{ 'lots.remaining' | translate }}</th>
              <th>{{ 'lots.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-l>
            <tr>
              <td><span class="font-mono text-sm">{{ l.lotNumber }}</span></td>
              <td>{{ l.expirationDate }}</td>
              <td class="text-right">
                <span [class.text-red-600]="l.daysUntilExpiry <= 7"
                      [class.text-orange-500]="l.daysUntilExpiry > 7 && l.daysUntilExpiry <= 30"
                      [class.font-bold]="l.daysUntilExpiry <= 7">
                  {{ l.daysUntilExpiry }} j
                </span>
              </td>
              <td class="text-right">{{ l.quantityRemaining | number:'1.0-3' }}</td>
              <td>
                <p-tag [value]="'lots.statuses.' + l.status | translate"
                       [severity]="statusSeverity(l.status)" />
              </td>
              <td>
                @if (l.status === 'ACTIVE') {
                  <button pButton icon="pi pi-ban" class="p-button-sm p-button-text p-button-warning"
                          [pTooltip]="'lots.block' | translate"
                          (click)="block(l.id)"></button>
                }
                @if (l.status === 'EXPIRED' || l.status === 'BLOCKED') {
                  <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                          [label]="'lots.destroy' | translate"
                          (click)="openDestroy(l)"></button>
                }
                <button pButton icon="pi pi-tag" class="p-button-sm p-button-text"
                        [pTooltip]="'lots.label' | translate"
                        (click)="printPdf('/api/v1/lots/' + l.id + '/label.pdf')"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">
              {{ 'lots.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </ng-template>

      <p-dialog [(visible)]="destroyOpen" [modal]="true" [style]="{ width: '450px' }"
                [header]="'lots.destroy' | translate" [closable]="!saving()">
        <div class="space-y-3" *ngIf="destroyTarget">
          <div class="text-sm text-gray-600">
            {{ 'lots.destroyOf' | translate }}
            <span class="font-mono font-semibold">{{ destroyTarget.lotNumber }}</span>
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'lots.quantity' | translate }} *</label>
            <p-inputNumber [(ngModel)]="destroyForm.quantity" mode="decimal" [maxFractionDigits]="3"
                           [styleClass]="'w-full' + (quantityInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (quantityInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.mustBePositive' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'lots.method' | translate }} *</label>
            <p-dropdown [(ngModel)]="destroyForm.method" [options]="methodOptions"
                        optionLabel="label" optionValue="value" styleClass="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'lots.cost' | translate }}</label>
            <p-inputNumber [(ngModel)]="destroyForm.cost" mode="decimal" [maxFractionDigits]="2"
                           styleClass="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="destroyForm.notes" class="w-full" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="destroyOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'lots.destroy' | translate" icon="pi pi-trash"
                  class="p-button-danger" (click)="confirmDestroy()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class LotListPage implements OnInit {
  private http = inject(HttpClient);

  protected lots = signal<Lot[]>([]);
  protected expiring = signal<Lot[]>([]);
  protected expired = signal<Lot[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected activeTab = 0;
  protected daysWindow = 30;
  protected destroyOpen = false;
  protected destroyTarget: Lot | null = null;

  protected quantityInvalid(): boolean {
    return this.submitted() && (this.destroyForm.quantity == null || this.destroyForm.quantity <= 0);
  }

  protected readonly dayOptions = [
    { value: 7, label: 'J-7' }, { value: 15, label: 'J-15' },
    { value: 30, label: 'J-30' }, { value: 60, label: 'J-60' },
  ];
  protected readonly methodOptions = [
    { value: 'INCINERATION', label: 'Incinération' },
    { value: 'RETURN_SUPPLIER', label: 'Retour fournisseur' },
    { value: 'DUMP', label: 'Mise au rebut' },
    { value: 'OTHER', label: 'Autre' },
  ];

  protected destroyForm = { quantity: 0, method: 'INCINERATION', cost: 0, notes: '' };

  ngOnInit() { this.load(); }

  protected onTabChange() {
    if (this.activeTab === 1) this.loadExpiring();
    if (this.activeTab === 2) this.loadExpired();
  }

  protected statusSeverity(s: string): Severity {
    switch (s) {
      case 'ACTIVE': return 'success';
      case 'EXPIRED': return 'danger';
      case 'BLOCKED': return 'warning';
      case 'DESTROYED': return 'secondary';
      case 'EXHAUSTED': return 'info';
      default: return 'secondary';
    }
  }

  protected async block(id: string) {
    const reason = prompt('Raison du blocage ?') ?? 'Quality issue';
    await firstValueFrom(this.http.post(
      `/api/v1/lots/${id}/block?reason=${encodeURIComponent(reason)}`, {}
    ));
    this.load();
  }

  protected openDestroy(lot: Lot) {
    this.destroyTarget = lot;
    this.destroyForm = { quantity: lot.quantityRemaining, method: 'INCINERATION', cost: 0, notes: '' };
    this.submitted.set(false);
    this.destroyOpen = true;
  }

  protected async confirmDestroy() {
    this.submitted.set(true);
    if (!this.destroyTarget) return;
    if (this.quantityInvalid()) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post(
        `/api/v1/lots/${this.destroyTarget.id}/destroy`, this.destroyForm
      ));
      this.destroyOpen = false;
      this.load();
      if (this.activeTab === 2) this.loadExpired();
    } finally { this.saving.set(false); }
  }

  /** Fetch the PDF through HttpClient so the auth interceptor attaches the JWT
   *  (a plain anchor navigation would 401), then open it in a new tab. */
  protected async printPdf(url: string) {
    try {
      const blob = await firstValueFrom(this.http.get(url, { responseType: 'blob' }));
      const blobUrl = URL.createObjectURL(blob);
      window.open(blobUrl, '_blank');
      setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
    } catch (e) {
      console.error('PDF fetch failed', e);
    }
  }

  private async load() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Lot[] }>('/api/v1/lots')
      );
      this.lots.set(res.content ?? []);
    } catch { this.lots.set([]); }
    finally { this.loading.set(false); }
  }

  protected async loadExpiring() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Lot[] }>(`/api/v1/lots/expiring?days=${this.daysWindow}`)
      );
      this.expiring.set(res.content ?? []);
    } catch { this.expiring.set([]); }
    finally { this.loading.set(false); }
  }

  protected async loadExpired() {
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: Lot[] }>('/api/v1/lots/expired')
      );
      this.expired.set(res.content ?? []);
    } catch { this.expired.set([]); }
    finally { this.loading.set(false); }
  }
}
