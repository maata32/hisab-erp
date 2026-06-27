import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MasterDataNamePipe } from '@minierp/shared-i18n';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AUTH_SERVICE } from '@minierp/shared-auth';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TabViewModule } from 'primeng/tabview';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { DropdownModule } from 'primeng/dropdown';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { firstValueFrom } from 'rxjs';

interface UomCategory { id: string; code: string; name: string; }
interface Uom {
  id: string; categoryId: string; categoryCode: string | null;
  code: string; name: string; ratioToBase: number; isBase: boolean;
  decimalPlaces: number; inUse: boolean;
}
interface ConvGroup { label: string; items: { label: string; value: string }[]; }

@Component({
  selector: 'erp-admin-uom-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule, TabViewModule,
    ButtonModule, InputTextModule, DialogModule, InputNumberModule, CheckboxModule,
    DropdownModule, TooltipModule, ToastModule, MasterDataNamePipe,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="space-y-4">
      <header>
        <h1 class="text-2xl font-bold text-gray-800">{{ 'uoms.title' | translate }}</h1>
        <p class="text-gray-500 text-sm mt-1">{{ 'uoms.subtitle' | translate }}</p>
      </header>

      <div class="bg-white rounded-lg border border-gray-200">
        <p-tabView>
          <!-- ── Units ─────────────────────────────────────────────── -->
          <p-tabPanel [header]="'uoms.tabs.units' | translate">
            @if (canCreate) {
              <div class="flex justify-end mb-3">
                <button pButton icon="pi pi-plus" [label]="'uoms.addUnit' | translate"
                        (click)="openCreateUnit()" class="p-button-sm"></button>
              </div>
            }
            <p-table [value]="groupedUoms()" [loading]="loading()" stripedRows
                     rowGroupMode="subheader" groupRowsBy="categoryCode"
                     sortField="categoryCode" [sortOrder]="1" styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'uoms.code' | translate }}</th>
                  <th>{{ 'uoms.name' | translate }}</th>
                  <th class="text-right">{{ 'uoms.ratio' | translate }}</th>
                  <th class="text-right">{{ 'uoms.decimals' | translate }}</th>
                  <th>{{ 'uoms.base' | translate }}</th>
                  <th class="w-24"></th>
                </tr>
              </ng-template>
              <ng-template pTemplate="groupheader" let-u>
                <tr pRowGroupHeader>
                  <td colspan="6" class="font-semibold text-gray-700 bg-gray-50">
                    {{ u.categoryCode | mdName:'uoms.cat':u.categoryCode }}
                  </td>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-u>
                <tr>
                  <td><span class="font-mono text-sm">{{ u.code }}</span></td>
                  <td class="font-medium">{{ u.code | mdName:'uoms.unit':u.name }}</td>
                  <td class="text-right">{{ u.ratioToBase | number:'1.0-6' }}</td>
                  <td class="text-right">{{ u.decimalPlaces }}</td>
                  <td>
                    @if (u.isBase) { <p-tag value="base" severity="info" /> }
                    @if (u.inUse) {
                      <i class="pi pi-lock text-gray-400 ml-1 text-xs"
                         [pTooltip]="'uoms.inUseTip' | translate"></i>
                    }
                  </td>
                  <td class="whitespace-nowrap text-right">
                    @if (canUpdate) {
                      <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                              [pTooltip]="'common.edit' | translate"
                              (click)="openEditUnit(u)"></button>
                    }
                    @if (canDelete) {
                      <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                              [pTooltip]="'common.delete' | translate"
                              (click)="deleteUnit(u)"></button>
                    }
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

          <!-- ── Categories ────────────────────────────────────────── -->
          <p-tabPanel [header]="'uoms.tabs.categories' | translate">
            @if (canCreate) {
              <div class="flex justify-end mb-3">
                <button pButton icon="pi pi-plus" [label]="'uoms.addCategory' | translate"
                        (click)="openCreateCategory()" class="p-button-sm"></button>
              </div>
            }
            <p-table [value]="categories()" stripedRows styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>{{ 'uoms.categoryCode' | translate }}</th>
                  <th>{{ 'uoms.categoryName' | translate }}</th>
                  <th class="w-24"></th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-c>
                <tr>
                  <td><span class="font-mono text-sm">{{ c.code }}</span></td>
                  <td class="font-medium">{{ c.code | mdName:'uoms.cat':c.name }}</td>
                  <td class="whitespace-nowrap text-right">
                    @if (canUpdate) {
                      <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                              [pTooltip]="'common.edit' | translate"
                              (click)="openEditCategory(c)"></button>
                    }
                    @if (canDelete) {
                      <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                              [pTooltip]="'common.delete' | translate"
                              (click)="deleteCategory(c)"></button>
                    }
                  </td>
                </tr>
              </ng-template>
              <ng-template pTemplate="emptymessage">
                <tr><td colspan="3" class="text-center text-gray-400 py-8">
                  {{ 'uoms.empty' | translate }}
                </td></tr>
              </ng-template>
            </p-table>
          </p-tabPanel>

          <!-- ── Conversion ────────────────────────────────────────── -->
          <p-tabPanel [header]="'uoms.tabs.conversion' | translate">
            <div class="max-w-xl space-y-3 p-2">
              <p class="text-sm text-gray-500">{{ 'uoms.conv.help' | translate }}</p>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-sm font-medium mb-1">{{ 'uoms.conv.amount' | translate }}</label>
                  <p-inputNumber [(ngModel)]="conv.amount" mode="decimal" [minFractionDigits]="0"
                                 [maxFractionDigits]="6" styleClass="w-full" />
                </div>
                <div>
                  <label class="block text-sm font-medium mb-1">{{ 'uoms.conv.from' | translate }}</label>
                  <p-dropdown [(ngModel)]="conv.from" [options]="convOptions()" [group]="true"
                              optionLabel="label" optionValue="value" [filter]="true"
                              [placeholder]="'common.select' | translate" styleClass="w-full" />
                </div>
              </div>
              <div class="grid grid-cols-2 gap-3 items-end">
                <div>
                  <label class="block text-sm font-medium mb-1">{{ 'uoms.conv.to' | translate }}</label>
                  <p-dropdown [(ngModel)]="conv.to" [options]="convOptions()" [group]="true"
                              optionLabel="label" optionValue="value" [filter]="true"
                              [placeholder]="'common.select' | translate" styleClass="w-full" />
                </div>
                <div>
                  <button pButton icon="pi pi-arrow-right-arrow-left" [label]="'uoms.conv.convert' | translate"
                          (click)="convert()" [disabled]="!conv.from || !conv.to || conv.amount == null"
                          [loading]="converting()" class="p-button-sm"></button>
                </div>
              </div>
              @if (convResult() != null) {
                <div class="bg-primary-50 border border-primary-200 rounded p-3 text-sm">
                  {{ 'uoms.conv.result' | translate }}:
                  <span class="font-semibold">{{ convResult() | number:'1.0-6' }}</span>
                </div>
              }
            </div>
          </p-tabPanel>
        </p-tabView>
      </div>

      <!-- ── Unit dialog ─────────────────────────────────────────── -->
      <p-dialog [(visible)]="unitDialogOpen" [modal]="true" [style]="{ width: '480px' }"
                [header]="(editingUnitId() ? 'uoms.dialogEdit' : 'uoms.dialogNew') | translate"
                [closable]="!saving()">
        <div class="space-y-3">
          @if (unitLocked()) {
            <p class="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded p-2">
              {{ 'uoms.lockedHelp' | translate }}
            </p>
          }
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'uoms.category' | translate }} *</label>
            <p-dropdown [(ngModel)]="unitForm.categoryId" [options]="categories()" optionLabel="code"
                        optionValue="id" [disabled]="!!editingUnitId()" appendTo="body"
                        [placeholder]="'common.select' | translate"
                        [styleClass]="'w-full' + (categoryInvalid() ? ' ng-invalid ng-dirty' : '')" />
            @if (categoryInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'uoms.categoryRequired' | translate }}</p>
            }
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'uoms.code' | translate }} *</label>
              <input pInputText [(ngModel)]="unitForm.code" class="w-full" [disabled]="unitLocked()"
                     [class.ng-invalid]="codeInvalid()" [class.ng-dirty]="codeInvalid()" />
              @if (codeInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'uoms.codeRequired' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'uoms.name' | translate }} *</label>
              <input pInputText [(ngModel)]="unitForm.name" class="w-full"
                     [class.ng-invalid]="nameInvalid()" [class.ng-dirty]="nameInvalid()" />
              @if (nameInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'uoms.nameRequired' | translate }}</p>
              }
            </div>
          </div>
          <div class="flex items-center gap-2 pt-1">
            <p-checkbox [(ngModel)]="unitForm.isBase" [binary]="true" [disabled]="unitLocked()"
                        (onChange)="onBaseToggle()" inputId="uomIsBase" />
            <label for="uomIsBase" class="text-sm">{{ 'uoms.isBaseLabel' | translate }}</label>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'uoms.ratio' | translate }} *</label>
              <p-inputNumber [(ngModel)]="unitForm.ratioToBase" mode="decimal" [minFractionDigits]="0"
                             [maxFractionDigits]="6" [min]="0" [disabled]="unitForm.isBase || unitLocked()"
                             [styleClass]="'w-full' + (ratioInvalid() ? ' ng-invalid ng-dirty' : '')" />
              <p class="text-xs text-gray-400 mt-1">{{ 'uoms.ratioHelp' | translate }}</p>
              @if (ratioInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'uoms.ratioPositive' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'uoms.decimals' | translate }}</label>
              <p-inputNumber [(ngModel)]="unitForm.decimalPlaces" [min]="0" [max]="6" styleClass="w-full" />
            </div>
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="unitDialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="saveUnit()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>

      <!-- ── Category dialog ─────────────────────────────────────── -->
      <p-dialog [(visible)]="catDialogOpen" [modal]="true" [style]="{ width: '420px' }"
                [header]="(editingCatId() ? 'uoms.categoryDialogEdit' : 'uoms.categoryDialogNew') | translate"
                [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'uoms.categoryCode' | translate }} *</label>
            <input pInputText [(ngModel)]="catForm.code" class="w-full"
                   [class.ng-invalid]="catCodeInvalid()" [class.ng-dirty]="catCodeInvalid()" />
            @if (catCodeInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'uoms.codeRequired' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'uoms.categoryName' | translate }} *</label>
            <input pInputText [(ngModel)]="catForm.name" class="w-full"
                   [class.ng-invalid]="catNameInvalid()" [class.ng-dirty]="catNameInvalid()" />
            @if (catNameInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'uoms.nameRequired' | translate }}</p>
            }
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.cancel' | translate" class="p-button-text"
                  (click)="catDialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="saveCategory()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class UomListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private toast = inject(MessageService);
  private auth = inject(AUTH_SERVICE);

  protected readonly canCreate = this.auth.hasPermission('uom:create');
  protected readonly canUpdate = this.auth.hasPermission('uom:update');
  protected readonly canDelete = this.auth.hasPermission('uom:delete');

  protected uoms = signal<Uom[]>([]);
  protected categories = signal<UomCategory[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);

  // Units sorted by category then ratio, for stable subheader grouping.
  protected groupedUoms = computed(() =>
    [...this.uoms()].sort((a, b) =>
      (a.categoryCode ?? '').localeCompare(b.categoryCode ?? '') ||
      a.ratioToBase - b.ratioToBase));

  // tracks the active language so the localized labels below recompute on switch
  private readonly lang = toSignal(this.i18n.onLangChange);

  protected convOptions = computed<ConvGroup[]>(() => {
    this.lang();
    const byCat = new Map<string, ConvGroup>();
    for (const u of this.groupedUoms()) {
      const code = u.categoryCode ?? '—';
      let group = byCat.get(code);
      if (!group) {
        group = { label: this.tr('uoms.cat', u.categoryCode, code), items: [] };
        byCat.set(code, group);
      }
      group.items.push({ label: `${u.code} — ${this.tr('uoms.unit', u.code, u.name)}`, value: u.id });
    }
    return [...byCat.values()];
  });

  // localizes a built-in UoM code; falls back to the stored name for custom records
  private tr(prefix: string, code: string | null | undefined, fallback: string): string {
    if (!code) return fallback;
    const key = `${prefix}.${code}`;
    const val = this.i18n.instant(key);
    return val === key ? fallback : val;
  }

  // Unit dialog state
  protected unitDialogOpen = false;
  protected editingUnitId = signal<string | null>(null);
  protected editingUnitInUse = signal(false);
  protected submitted = signal(false);
  protected unitForm = this.emptyUnitForm();

  // Category dialog state
  protected catDialogOpen = false;
  protected editingCatId = signal<string | null>(null);
  protected catSubmitted = signal(false);
  protected catForm = { code: '', name: '' };

  // Conversion state
  protected conv: { amount: number | null; from: string | null; to: string | null } =
    { amount: 1, from: null, to: null };
  protected convResult = signal<number | null>(null);
  protected converting = signal(false);

  ngOnInit() {
    this.load();
    this.loadCategories();
  }

  // editing a referenced unit locks its structural fields
  protected unitLocked(): boolean {
    return !!this.editingUnitId() && this.editingUnitInUse();
  }

  // ── Units ────────────────────────────────────────────────────────
  protected openCreateUnit() {
    this.editingUnitId.set(null);
    this.editingUnitInUse.set(false);
    this.unitForm = this.emptyUnitForm();
    this.submitted.set(false);
    this.unitDialogOpen = true;
  }

  protected openEditUnit(u: Uom) {
    this.editingUnitId.set(u.id);
    this.editingUnitInUse.set(u.inUse);
    this.unitForm = {
      categoryId: u.categoryId,
      code: u.code,
      name: u.name,
      ratioToBase: u.ratioToBase,
      isBase: u.isBase,
      decimalPlaces: u.decimalPlaces,
    };
    this.submitted.set(false);
    this.unitDialogOpen = true;
  }

  protected onBaseToggle() {
    if (this.unitForm.isBase) this.unitForm.ratioToBase = 1;
  }

  protected async saveUnit() {
    this.submitted.set(true);
    if (this.nameInvalid() || this.codeInvalid() || this.categoryInvalid() || this.ratioInvalid()) return;
    this.saving.set(true);
    try {
      const id = this.editingUnitId();
      const body = {
        code: this.unitForm.code.trim(),
        name: this.unitForm.name.trim(),
        ratioToBase: this.unitForm.isBase ? 1 : this.unitForm.ratioToBase,
        isBase: this.unitForm.isBase,
        decimalPlaces: this.unitForm.decimalPlaces ?? 0,
      };
      if (id) {
        await firstValueFrom(this.http.put(`/api/v1/uoms/${id}`, body));
      } else {
        await firstValueFrom(this.http.post('/api/v1/uoms', { ...body, categoryId: this.unitForm.categoryId }));
      }
      this.unitDialogOpen = false;
      this.success('uoms.saved');
      this.load();
    } catch (e) {
      this.showError(e);
    } finally {
      this.saving.set(false);
    }
  }

  protected deleteUnit(u: Uom) {
    this.confirmation.confirm({
      message: this.i18n.instant('uoms.deleteUnitConfirm', { code: u.code }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        try {
          await firstValueFrom(this.http.delete(`/api/v1/uoms/${u.id}`));
          this.success('uoms.deleted');
          this.load();
        } catch (e) {
          this.showError(e);
        }
      },
    });
  }

  // ── Categories ───────────────────────────────────────────────────
  protected openCreateCategory() {
    this.editingCatId.set(null);
    this.catForm = { code: '', name: '' };
    this.catSubmitted.set(false);
    this.catDialogOpen = true;
  }

  protected openEditCategory(c: UomCategory) {
    this.editingCatId.set(c.id);
    this.catForm = { code: c.code, name: c.name };
    this.catSubmitted.set(false);
    this.catDialogOpen = true;
  }

  protected async saveCategory() {
    this.catSubmitted.set(true);
    if (this.catCodeInvalid() || this.catNameInvalid()) return;
    this.saving.set(true);
    try {
      const id = this.editingCatId();
      const body = { code: this.catForm.code.trim(), name: this.catForm.name.trim() };
      if (id) {
        await firstValueFrom(this.http.put(`/api/v1/uoms/categories/${id}`, body));
      } else {
        await firstValueFrom(this.http.post('/api/v1/uoms/categories', body));
      }
      this.catDialogOpen = false;
      this.success('uoms.saved');
      this.loadCategories();
      this.load();
    } catch (e) {
      this.showError(e);
    } finally {
      this.saving.set(false);
    }
  }

  protected deleteCategory(c: UomCategory) {
    this.confirmation.confirm({
      message: this.i18n.instant('uoms.deleteCategoryConfirm', { code: c.code }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        try {
          await firstValueFrom(this.http.delete(`/api/v1/uoms/categories/${c.id}`));
          this.success('uoms.deleted');
          this.loadCategories();
        } catch (e) {
          this.showError(e);
        }
      },
    });
  }

  // ── Conversion ───────────────────────────────────────────────────
  protected async convert() {
    if (!this.conv.from || !this.conv.to || this.conv.amount == null) return;
    this.converting.set(true);
    this.convResult.set(null);
    try {
      const params = new URLSearchParams({
        amount: String(this.conv.amount),
        from: this.conv.from,
        to: this.conv.to,
      });
      const res = await firstValueFrom(
        this.http.get<{ result: number }>(`/api/v1/uoms/convert?${params}`)
      );
      this.convResult.set(res.result);
    } catch (e) {
      this.showError(e);
    } finally {
      this.converting.set(false);
    }
  }

  // ── Validation helpers ───────────────────────────────────────────
  protected nameInvalid() { return this.submitted() && !this.unitForm.name?.trim(); }
  protected codeInvalid() { return this.submitted() && !this.unitForm.code?.trim(); }
  protected categoryInvalid() {
    return this.submitted() && !this.editingUnitId() && !this.unitForm.categoryId;
  }
  protected ratioInvalid() {
    if (!this.submitted() || this.unitForm.isBase) return false;
    return this.unitForm.ratioToBase == null || this.unitForm.ratioToBase <= 0;
  }
  protected catCodeInvalid() { return this.catSubmitted() && !this.catForm.code?.trim(); }
  protected catNameInvalid() { return this.catSubmitted() && !this.catForm.name?.trim(); }

  // ── Data ─────────────────────────────────────────────────────────
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
      const list = await firstValueFrom(this.http.get<UomCategory[]>('/api/v1/uoms/categories'));
      this.categories.set(list ?? []);
    } catch { this.categories.set([]); }
  }

  private emptyUnitForm(): {
    categoryId: string | null; code: string; name: string;
    ratioToBase: number | null; isBase: boolean; decimalPlaces: number | null;
  } {
    return { categoryId: null, code: '', name: '', ratioToBase: 1, isBase: false, decimalPlaces: 0 };
  }

  private success(key: string) {
    this.toast.add({
      severity: 'success',
      summary: this.i18n.instant('common.success'),
      detail: this.i18n.instant(key),
      life: 3000,
    });
  }

  private showError(err: unknown) {
    let detail = this.i18n.instant('common.error_generic');
    if (err instanceof HttpErrorResponse) {
      const body = err.error;
      if (body?.code) {
        const translated = this.i18n.instant(body.code);
        detail = translated !== body.code ? translated : (body.message ?? body.code);
      } else if (body?.message) {
        detail = body.message;
      }
    }
    this.toast.add({ severity: 'error', summary: this.i18n.instant('common.error'), detail, life: 5000 });
  }
}
