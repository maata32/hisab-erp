import { Component, OnInit, inject, signal, computed, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AUTH_SERVICE } from '@hisaberp/shared-auth';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';

type CountStatus = 'DRAFT' | 'IN_PROGRESS' | 'VALIDATED' | 'CANCELLED';

interface InventoryCount {
  id: string;
  countNumber: string;
  warehouseId: string;
  status: CountStatus;
  countDate: string;
  validatedAt: string | null;
  notes: string | null;
}

interface CountLineRaw {
  id: string;
  productId: string;
  theoreticalQty: number;
  countedQty: number | null;
  discrepancy: number | null;
  unitCost: number;
}

interface CountDetail extends InventoryCount {
  lines: CountLineRaw[];
}

// Local editable line: countedQty is mutated in place via ngModel.
interface CountLine {
  id: string;
  productId: string;
  sku: string;
  productName: string;
  theoreticalQty: number;
  countedQty: number | null;
  unitCost: number;
}

interface WarehouseLite { id: string; code: string; name: string; }
interface StockNameRow { productId: string; productName: string; sku: string; }

type Severity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

@Component({
  selector: 'erp-admin-inventory-count-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, DialogModule, DropdownModule, InputTextModule, InputNumberModule,
    TooltipModule,
  ],
  providers: [MessageService],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'inventoryCounts.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'inventoryCounts.subtitle' | translate }}</p>
        </div>
        @if (canCount) {
          <button pButton icon="pi pi-plus" [label]="'inventoryCounts.create' | translate"
                  (click)="openCreate()" class="p-button-sm"></button>
        }
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table #table [value]="counts()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'inventoryCounts.number' | translate }}</th>
              <th>{{ 'inventoryCounts.warehouse' | translate }}</th>
              <th>{{ 'inventoryCounts.date' | translate }}</th>
              <th>{{ 'inventoryCounts.validated' | translate }}</th>
              <th>{{ 'inventoryCounts.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-c>
            <tr>
              <td>
                <button type="button" class="font-mono text-sm text-primary-600 hover:underline"
                        (click)="openCount(c)">{{ c.countNumber }}</button>
              </td>
              <td>{{ warehouseName(c.warehouseId) }}</td>
              <td>{{ c.countDate }}</td>
              <td>{{ c.validatedAt ? (c.validatedAt | date:'short') : '—' }}</td>
              <td>
                <p-tag [value]="'inventoryCounts.statuses.' + c.status | translate"
                       [severity]="statusSeverity(c.status)" />
              </td>
              <td>
                <button pButton class="p-button-sm p-button-text"
                        [icon]="c.status === 'VALIDATED' ? 'pi pi-eye' : 'pi pi-pencil'"
                        [pTooltip]="(c.status === 'VALIDATED' ? 'inventoryCounts.entry.view' : 'inventoryCounts.entry.enter') | translate"
                        (click)="openCount(c)"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="6" class="text-center text-gray-400 py-8">
              {{ 'inventoryCounts.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Création -->
      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '450px' }"
                [header]="'inventoryCounts.create' | translate" [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'inventoryCounts.warehouse' | translate }} *</label>
            <p-dropdown [(ngModel)]="form.warehouseId" [options]="warehouses()"
                        optionLabel="name" optionValue="id" appendTo="body"
                        [styleClass]="'w-full' + (warehouseInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (warehouseInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'inventoryCounts.date' | translate }} *</label>
            <input pInputText type="date" [(ngModel)]="form.countDate" class="w-full"
                   [class.ng-invalid]="dateInvalid()" [class.ng-dirty]="dateInvalid()" />
            @if (dateInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'common.notes' | translate }}</label>
            <input pInputText [(ngModel)]="form.notes" class="w-full" />
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- Saisie du comptage -->
      <p-dialog [(visible)]="entryOpen" [modal]="true" [style]="{ width: '960px' }"
                [closable]="!saving()"
                [header]="('inventoryCounts.entry.title' | translate) + (editCount() ? ' — ' + editCount()!.countNumber : '')">
        @if (editCount(); as ec) {
          <div class="mb-3 p-3 bg-gray-50 rounded text-sm flex gap-4 flex-wrap items-center">
            <div><span class="text-gray-500">{{ 'inventoryCounts.warehouse' | translate }} :</span>
              <span class="font-medium ml-2">{{ warehouseName(ec.warehouseId) }}</span></div>
            <div><span class="text-gray-500">{{ 'inventoryCounts.date' | translate }} :</span>
              <span class="font-medium ml-2">{{ ec.countDate }}</span></div>
            <p-tag [value]="'inventoryCounts.statuses.' + ec.status | translate"
                   [severity]="statusSeverity(ec.status)" />
            <span class="ml-auto text-gray-500">
              {{ 'inventoryCounts.entry.summary' | translate:{ counted: countedCount(), total: editLines().length, gaps: gapCount() } }}
            </span>
          </div>

          @if (editable()) {
            <p class="text-xs text-gray-500 mb-2">{{ 'inventoryCounts.entry.help' | translate }}</p>
          } @else {
            <p class="text-xs text-amber-600 mb-2">{{ 'inventoryCounts.entry.readonly' | translate }}</p>
          }

          <p-table [value]="editLines()" [loading]="entryLoading()" stripedRows responsiveLayout="scroll"
                   [scrollable]="true" scrollHeight="420px" [rowHover]="true" styleClass="p-datatable-sm">
            <ng-template pTemplate="header">
              <tr>
                <th>{{ 'inventoryCounts.entry.sku' | translate }}</th>
                <th>{{ 'inventoryCounts.entry.product' | translate }}</th>
                <th class="text-right">{{ 'inventoryCounts.entry.theoretical' | translate }}</th>
                <th class="text-right" style="width:170px">{{ 'inventoryCounts.entry.counted' | translate }}</th>
                <th class="text-right">{{ 'inventoryCounts.entry.discrepancy' | translate }}</th>
                <th class="text-right">{{ 'inventoryCounts.entry.impact' | translate }}</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-l>
              <tr>
                <td><span class="font-mono text-xs">{{ l.sku }}</span></td>
                <td class="font-medium">{{ l.productName }}</td>
                <td class="text-right">{{ l.theoreticalQty | number:'1.0-3' }}</td>
                <td class="text-right">
                  @if (editable()) {
                    <p-inputNumber [(ngModel)]="l.countedQty" mode="decimal"
                                   [attr.aria-label]="'inventoryCounts.entry.counted' | translate"
                                   [minFractionDigits]="0" [maxFractionDigits]="3" [min]="0"
                                   inputStyleClass="w-28 text-right" />
                  } @else {
                    {{ l.countedQty == null ? '—' : (l.countedQty | number:'1.0-3') }}
                  }
                </td>
                <td class="text-right font-semibold"
                    [class.text-green-600]="discrepancy(l) !== null && discrepancy(l)! > 0"
                    [class.text-red-600]="discrepancy(l) !== null && discrepancy(l)! < 0">
                  @if (discrepancy(l) === null) { — }
                  @else { {{ discrepancy(l)! > 0 ? '+' : '' }}{{ discrepancy(l) | number:'1.0-3' }} }
                </td>
                <td class="text-right text-gray-600">
                  @if (discrepancy(l) === null) { — }
                  @else { {{ (discrepancy(l)! * l.unitCost) | number:'1.0-2' }} }
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="6" class="text-center text-gray-400 py-8">
                {{ 'inventoryCounts.entry.emptyLines' | translate }}
              </td></tr>
            </ng-template>
          </p-table>
        }

        <ng-template pTemplate="footer">
          @if (confirmingValidate()) {
            <span class="text-sm text-amber-700 mr-auto">{{ 'inventoryCounts.entry.validateConfirm' | translate }}</span>
            <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                    (click)="confirmingValidate.set(false)" [disabled]="saving()"></button>
            <button pButton [label]="'inventoryCounts.entry.confirmValidate' | translate" icon="pi pi-check-circle"
                    class="p-button-danger" (click)="doValidate()" [loading]="saving()"></button>
          } @else {
            <button pButton [label]="'common.close' | translate" class="p-button-text"
                    (click)="entryOpen = false" [disabled]="saving()"></button>
            @if (editable() && canCount) {
              <button pButton [label]="'inventoryCounts.entry.saveCount' | translate" icon="pi pi-save"
                      class="p-button-outlined" (click)="saveCounts()" [loading]="saving()"></button>
              <button pButton [label]="'inventoryCounts.validate' | translate" icon="pi pi-check-circle"
                      class="p-button-success" (click)="confirmingValidate.set(true)" [disabled]="saving()"></button>
            }
          }
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class InventoryCountListPage implements OnInit {
  private http = inject(HttpClient);
  private toast = inject(MessageService);
  private i18n = inject(TranslateService);
  private auth = inject(AUTH_SERVICE);

  protected counts = signal<InventoryCount[]>([]);
  protected warehouses = signal<WarehouseLite[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;

  protected readonly canCount = this.auth.hasPermission('inventory:count');

  // Count-entry dialog state
  protected entryOpen = false;
  protected entryLoading = signal(false);
  protected editCount = signal<InventoryCount | null>(null);
  protected editLines = signal<CountLine[]>([]);
  protected confirmingValidate = signal(false);
  protected readonly editable = computed(() => {
    const s = this.editCount()?.status;
    return s === 'DRAFT' || s === 'IN_PROGRESS';
  });
  // Methods (not computed): counted quantities are mutated in place via ngModel, so the
  // editLines() signal reference never changes — a method re-evaluates each change cycle.
  protected countedCount(): number {
    return this.editLines().filter((l) => l.countedQty != null).length;
  }
  protected gapCount(): number {
    return this.editLines().filter((l) => l.countedQty != null && l.countedQty !== l.theoreticalQty).length;
  }

  protected form = this.emptyForm();

  protected warehouseInvalid(): boolean { return this.submitted() && !this.form.warehouseId; }
  protected dateInvalid(): boolean { return this.submitted() && !this.form.countDate; }

  ngOnInit() {
    this.loadWarehouses();
    // list auto-loaded by p-table lazy
  }

  protected statusSeverity(s: string): Severity {
    switch (s) {
      case 'VALIDATED': return 'success';
      case 'IN_PROGRESS': return 'info';
      case 'CANCELLED': return 'danger';
      default: return 'secondary';
    }
  }

  protected warehouseName(id: string): string {
    return this.warehouses().find((x) => x.id === id)?.name ?? '—';
  }

  protected discrepancy(l: CountLine): number | null {
    return l.countedQty == null ? null : l.countedQty - l.theoreticalQty;
  }

  protected openCreate() {
    this.form = this.emptyForm();
    this.form.countDate = new Date().toISOString().slice(0, 10);
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.warehouseId || !this.form.countDate) return;
    this.saving.set(true);
    try {
      const created = await firstValueFrom(this.http.post<CountDetail>('/api/v1/inventory/counts', {
        warehouseId: this.form.warehouseId,
        countDate: this.form.countDate,
        notes: this.form.notes || null,
      }));
      this.dialogOpen = false;
      this.reload();
      // Smooth flow: open the new count straight into entry.
      await this.openCountDetail(created);
    } catch (e) {
      this.toastError(this.errorDetail(e));
    } finally { this.saving.set(false); }
  }

  // --- Count entry ---

  protected async openCount(c: InventoryCount) {
    this.editCount.set(c);
    this.editLines.set([]);
    this.confirmingValidate.set(false);
    this.entryOpen = true;
    this.entryLoading.set(true);
    try {
      const [detail, names] = await Promise.all([
        firstValueFrom(this.http.get<CountDetail>(`/api/v1/inventory/counts/${c.id}`)),
        this.loadNameMap(c.warehouseId),
      ]);
      this.applyDetail(detail, names);
    } catch (e) {
      this.toastError(this.errorDetail(e));
    } finally {
      this.entryLoading.set(false);
    }
  }

  private async openCountDetail(detail: CountDetail) {
    this.editCount.set(detail);
    this.editLines.set([]);
    this.confirmingValidate.set(false);
    this.entryOpen = true;
    this.entryLoading.set(true);
    try {
      const names = await this.loadNameMap(detail.warehouseId);
      this.applyDetail(detail, names);
    } finally {
      this.entryLoading.set(false);
    }
  }

  private applyDetail(detail: CountDetail, names: Map<string, StockNameRow>) {
    this.editCount.set({
      id: detail.id, countNumber: detail.countNumber, warehouseId: detail.warehouseId,
      status: detail.status, countDate: detail.countDate,
      validatedAt: detail.validatedAt, notes: detail.notes,
    });
    this.editLines.set((detail.lines ?? []).map((l) => {
      const n = names.get(l.productId);
      return {
        id: l.id,
        productId: l.productId,
        sku: n?.sku ?? '—',
        productName: n?.productName ?? '—',
        theoreticalQty: l.theoreticalQty,
        countedQty: l.countedQty,
        unitCost: l.unitCost,
      };
    }));
  }

  protected async saveCounts(): Promise<boolean> {
    const c = this.editCount();
    if (!c) return false;
    const updates = this.editLines()
      .filter((l) => l.countedQty != null)
      .map((l) => ({ lineId: l.id, countedQty: l.countedQty }));
    this.saving.set(true);
    try {
      const detail = await firstValueFrom(this.http.patch<CountDetail>(
        `/api/v1/inventory/counts/${c.id}/lines`, updates));
      // reflect new IN_PROGRESS status without reloading the table position
      this.editCount.set({ ...c, status: detail.status as CountStatus });
      this.toast.add({
        severity: 'success',
        summary: this.i18n.instant('common.success'),
        detail: this.i18n.instant('inventoryCounts.entry.saved'),
      });
      this.reload();
      return true;
    } catch (e) {
      this.toastError(this.errorDetail(e));
      return false;
    } finally {
      this.saving.set(false);
    }
  }

  protected async doValidate() {
    const c = this.editCount();
    if (!c) return;
    // Persist entered counts first, then validate so the adjustments are applied.
    const saved = await this.saveCounts();
    if (!saved) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post(`/api/v1/inventory/counts/${c.id}/validate`, {}));
      this.toast.add({
        severity: 'success',
        summary: this.i18n.instant('common.success'),
        detail: this.i18n.instant('inventoryCounts.entry.validated'),
      });
      this.confirmingValidate.set(false);
      this.entryOpen = false;
      this.reload();
    } catch (e) {
      this.toastError(this.errorDetail(e));
    } finally {
      this.saving.set(false);
    }
  }

  private async loadNameMap(warehouseId: string): Promise<Map<string, StockNameRow>> {
    try {
      const list = await firstValueFrom(
        this.http.get<StockNameRow[]>(`/api/v1/reports/stock?warehouseId=${warehouseId}`));
      return new Map((list ?? []).map((r) => [r.productId, r]));
    } catch {
      return new Map();
    }
  }

  // --- list loading ---

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: InventoryCount[]; totalElements: number }>(
          `/api/v1/inventory/counts?page=${page}&size=${rows}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.counts()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.counts.set(arr);
      this.total.set(totalElements);
    } catch {
      this.counts.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.counts.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadWarehouses() {
    try {
      const list = await firstValueFrom(
        this.http.get<WarehouseLite[]>('/api/v1/inventory/warehouses')
      );
      this.warehouses.set(list ?? []);
    } catch { this.warehouses.set([]); }
  }

  private toastError(detail: string) {
    this.toast.add({
      severity: 'error',
      summary: this.i18n.instant('common.error'),
      detail,
      life: 5000,
    });
  }

  private errorDetail(e: unknown): string {
    if (e instanceof HttpErrorResponse && e.error?.message) return e.error.message;
    return this.i18n.instant('common.error_generic');
  }

  private emptyForm() {
    return { warehouseId: null as string | null, countDate: '', notes: '' };
  }
}
