import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { MoneyPipe } from '@hisaberp/shared-i18n';
import { HttpClient } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { firstValueFrom } from 'rxjs';

interface CreditNoteLine {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  taxRate: number;
  lineTotal: number;
  returnedToStockQty: number;
}

interface CreditNote {
  id: string;
  number: string;
  invoiceId: string;
  invoiceNumber: string;
  customerId: string;
  customerName: string;
  issueDate: string;
  reason: string | null;
  subtotal: number;
  taxAmount: number;
  total: number;
  status: string;
  currency: string;
  lines: CreditNoteLine[];
  createdAt: string;
}

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-credit-note-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, MoneyPipe, TableModule, TagModule, ButtonModule,
    TooltipModule, DialogModule,
  ],
  template: `
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'creditNotes.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'creditNotes.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="notes()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'creditNotes.number' | translate }}</th>
              <th>{{ 'creditNotes.invoiceNumber' | translate }}</th>
              <th>{{ 'sales.customer' | translate }}</th>
              <th>{{ 'creditNotes.issueDate' | translate }}</th>
              <th>{{ 'creditNotes.reason' | translate }}</th>
              <th class="text-right">{{ 'creditNotes.totals.total' | translate }}</th>
              <th>{{ 'sales.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-cn>
            <tr>
              <td><span class="font-mono text-sm">{{ cn.number }}</span></td>
              <td><span class="font-mono text-sm text-gray-600">{{ cn.invoiceNumber }}</span></td>
              <td>{{ cn.customerName }}</td>
              <td>{{ cn.issueDate | date:'mediumDate' }}</td>
              <td class="max-w-xs truncate text-sm text-gray-600">{{ cn.reason || '—' }}</td>
              <td class="text-right font-medium text-red-700">{{ cn.total | money }} {{ cn.currency }}</td>
              <td><p-tag [value]="'creditNotes.statuses.' + cn.status | translate" [severity]="statusSeverity(cn.status)" /></td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-eye" class="p-button-sm p-button-text mr-1"
                        [pTooltip]="'invoices.view' | translate"
                        (click)="openDetail(cn)"></button>
                <button pButton icon="pi pi-print" class="p-button-sm p-button-text"
                        [pTooltip]="'common.print' | translate"
                        (click)="printPdf('/api/v1/credit-notes/' + cn.id + '/pdf')"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="8" class="text-center text-gray-400 py-8">{{ 'creditNotes.empty' | translate }}</td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="detailOpen" [modal]="true" [style]="{ width: '900px' }"
                [header]="detail()?.number || ''">
        @if (detail(); as cn) {
          <div class="space-y-3">
            <div class="grid grid-cols-3 gap-3 text-sm">
              <div>
                <span class="text-gray-500">{{ 'creditNotes.invoiceNumber' | translate }} :</span>
                <div class="font-mono">{{ cn.invoiceNumber }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'sales.customer' | translate }} :</span>
                <div class="font-medium">{{ cn.customerName }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'creditNotes.issueDate' | translate }} :</span>
                <div class="font-medium">{{ cn.issueDate | date:'mediumDate' }}</div>
              </div>
              <div class="col-span-2">
                <span class="text-gray-500">{{ 'creditNotes.reason' | translate }} :</span>
                <div>{{ cn.reason || '—' }}</div>
              </div>
              <div>
                <span class="text-gray-500">{{ 'sales.status' | translate }} :</span>
                <div><p-tag [value]="'creditNotes.statuses.' + cn.status | translate" [severity]="statusSeverity(cn.status)" /></div>
              </div>
            </div>

            <div class="border rounded">
              <table class="w-full text-sm">
                <thead class="bg-gray-50 text-gray-600">
                  <tr>
                    <th class="text-left p-2">{{ 'sales.product' | translate }}</th>
                    <th class="text-right p-2 w-24">{{ 'sales.quantity' | translate }}</th>
                    <th class="text-right p-2 w-28">{{ 'sales.unitPrice' | translate }}</th>
                    <th class="text-right p-2 w-20">{{ 'sales.discount' | translate }}%</th>
                    @if (taxEnabled()) {
                      <th class="text-right p-2 w-20">TVA</th>
                    }
                    <th class="text-right p-2 w-28">{{ 'sales.lineTotal' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (l of cn.lines; track l.id) {
                    <tr class="border-t">
                      <td class="p-2">
                        <div class="font-medium">{{ l.productName }}</div>
                        <div class="text-xs text-gray-500 font-mono">{{ l.sku }}</div>
                      </td>
                      <td class="p-2 text-right">{{ l.quantity }}</td>
                      <td class="p-2 text-right">{{ l.unitPrice | money }}</td>
                      <td class="p-2 text-right">{{ l.discountPercent > 0 ? (l.discountPercent + '%') : '—' }}</td>
                      @if (taxEnabled()) {
                        <td class="p-2 text-right">{{ (l.taxRate * 100) | number:'1.0-0' }}%</td>
                      }
                      <td class="p-2 text-right font-medium">{{ l.lineTotal | money }}</td>
                    </tr>
                  }
                </tbody>
                <tfoot class="bg-gray-50 border-t">
                  <tr>
                    <td [attr.colspan]="taxEnabled() ? 5 : 4" class="p-2 text-right">{{ 'creditNotes.totals.subtotal' | translate }}</td>
                    <td class="p-2 text-right font-medium">{{ cn.subtotal | money }} {{ cn.currency }}</td>
                  </tr>
                  @if (taxEnabled() && cn.taxAmount > 0) {
                    <tr>
                      <td colspan="5" class="p-2 text-right">{{ 'creditNotes.totals.tax' | translate }}</td>
                      <td class="p-2 text-right">{{ cn.taxAmount | money }} {{ cn.currency }}</td>
                    </tr>
                  }
                  <tr>
                    <td [attr.colspan]="taxEnabled() ? 5 : 4" class="p-2 text-right font-bold">{{ 'creditNotes.totals.total' | translate }}</td>
                    <td class="p-2 text-right font-bold text-red-700">{{ cn.total | money }} {{ cn.currency }}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
        }
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.close' | translate" class="p-button-text"
                  (click)="detailOpen.set(false)"></button>
          @if (detail(); as cn) {
            <button pButton icon="pi pi-print" [label]="'common.print' | translate"
                    (click)="printPdf('/api/v1/credit-notes/' + cn.id + '/pdf')"></button>
          }
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class CreditNoteListPage implements OnInit {
  private http = inject(HttpClient);

  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected notes = signal<CreditNote[]>([]);
  protected loading = signal(true);
  protected detailOpen = signal(false);
  protected detail = signal<CreditNote | null>(null);
  protected taxEnabled = signal(true);

  ngOnInit() {
    // List is fetched on demand via the p-table's onLazyLoad.
    this.loadSettings();
  }

  private async loadSettings() {
    try {
      const s = await firstValueFrom(this.http.get<{ invoiceSettings?: { taxEnabled?: boolean } }>('/api/v1/settings'));
      const enabled = s?.invoiceSettings?.taxEnabled;
      if (typeof enabled === 'boolean') this.taxEnabled.set(enabled);
    } catch { /* keep default true */ }
  }

  protected statusSeverity(status: string): Severity {
    return ({ DRAFT: 'secondary', ISSUED: 'info', APPLIED: 'success', CANCELLED: 'secondary' } as Record<string, Severity>)[status] ?? 'secondary';
  }

  protected async openDetail(cn: CreditNote) {
    this.detail.set(null);
    this.detailOpen.set(true);
    try {
      const full = await firstValueFrom(this.http.get<CreditNote>(`/api/v1/credit-notes/${cn.id}`));
      this.detail.set(full);
    } catch {
      this.detail.set(cn);
    }
  }

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

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: CreditNote[]; totalElements: number }>(
          `/api/v1/credit-notes?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.notes()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.notes.set(arr);
      this.total.set(totalElements);
    } catch {
      this.notes.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.notes.set([]);
    this.total.set(0);
    this.table?.reset();
  }
}
