import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DropdownModule } from 'primeng/dropdown';
import { TooltipModule } from 'primeng/tooltip';
import { CheckboxModule } from 'primeng/checkbox';
import { firstValueFrom } from 'rxjs';

interface ExpenseCategory {
  id: string;
  name: string;
  parentId: string | null;
  color: string | null;
  active: boolean;
}

interface CategoryForm {
  name: string;
  parentId: string | null;
  color: string;
  active: boolean;
}

@Component({
  selector: 'erp-admin-expense-category-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule, ButtonModule,
    DialogModule, InputTextModule, DropdownModule, TooltipModule, CheckboxModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'expenseCategories.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'expenseCategories.subtitle' | translate }}</p>
        </div>
        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2 text-sm">
            <p-checkbox [(ngModel)]="includeInactive" [binary]="true" (onChange)="load()" />
            {{ 'expenseCategories.showInactive' | translate }}
          </label>
          <button pButton icon="pi pi-plus" [label]="'expenseCategories.create' | translate"
                  (click)="openCreate()" class="p-button-sm"></button>
        </div>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="categories()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'expenseCategories.name' | translate }}</th>
              <th>{{ 'expenseCategories.parent' | translate }}</th>
              <th>{{ 'expenseCategories.color' | translate }}</th>
              <th>{{ 'expenseCategories.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-c>
            <tr>
              <td class="font-medium">{{ c.name }}</td>
              <td>{{ parentName(c.parentId) }}</td>
              <td>
                @if (c.color) {
                  <span class="inline-flex items-center gap-2">
                    <span class="w-4 h-4 rounded border" [style.background]="c.color"></span>
                    <span class="font-mono text-xs">{{ c.color }}</span>
                  </span>
                } @else {
                  —
                }
              </td>
              <td>
                <p-tag [value]="(c.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="c.active ? 'success' : 'secondary'" />
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                        [pTooltip]="'common.edit' | translate"
                        (click)="openEdit(c)"></button>
                @if (c.active) {
                  <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.deactivate' | translate"
                          (click)="deactivate(c)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="5" class="text-center text-gray-400 py-8">
              {{ 'expenseCategories.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '420px' }"
                [header]="(editing() ? 'expenseCategories.editTitle' : 'expenseCategories.createTitle') | translate"
                [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'expenseCategories.name' | translate }} *</label>
            <input pInputText [(ngModel)]="form.name" class="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'expenseCategories.parent' | translate }}</label>
            <p-dropdown [(ngModel)]="form.parentId" [options]="parentOptions()"
                        optionLabel="label" optionValue="value"
                        [showClear]="true"
                        [placeholder]="'expenseCategories.noParent' | translate"
                        styleClass="w-full" />
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'expenseCategories.color' | translate }}</label>
            <div class="flex items-center gap-2">
              <input type="color" [(ngModel)]="form.color" class="h-9 w-12 rounded border" />
              <input pInputText [(ngModel)]="form.color" placeholder="#3B82F6" class="flex-1" />
            </div>
          </div>
          @if (editing()) {
            <div class="flex items-center gap-2">
              <p-checkbox [(ngModel)]="form.active" [binary]="true" inputId="catActive" />
              <label for="catActive" class="text-sm">{{ 'common.active' | translate }}</label>
            </div>
          }
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
export class ExpenseCategoryListPage implements OnInit {
  private http = inject(HttpClient);

  protected categories = signal<ExpenseCategory[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected dialogOpen = false;
  protected editing = signal<ExpenseCategory | null>(null);
  protected form: CategoryForm = this.emptyForm();
  protected includeInactive = false;

  ngOnInit() { this.load(); }

  protected parentName(id: string | null): string {
    if (!id) return '—';
    return this.categories().find(c => c.id === id)?.name ?? '—';
  }

  protected parentOptions() {
    const editingId = this.editing()?.id;
    return this.categories()
      .filter(c => c.active && c.id !== editingId)
      .map(c => ({ value: c.id, label: c.name }));
  }

  protected openCreate() {
    this.editing.set(null);
    this.form = this.emptyForm();
    this.dialogOpen = true;
  }

  protected openEdit(c: ExpenseCategory) {
    this.editing.set(c);
    this.form = {
      name: c.name,
      parentId: c.parentId,
      color: c.color ?? '#3B82F6',
      active: c.active,
    };
    this.dialogOpen = true;
  }

  protected async save() {
    if (!this.form.name?.trim()) return;
    this.saving.set(true);
    try {
      const payload = {
        name: this.form.name.trim(),
        parentId: this.form.parentId,
        color: this.form.color || null,
        active: this.form.active,
      };
      const current = this.editing();
      if (current) {
        await firstValueFrom(this.http.put(`/api/v1/expense-categories/${current.id}`, payload));
      } else {
        await firstValueFrom(this.http.post('/api/v1/expense-categories', payload));
      }
      this.dialogOpen = false;
      this.load();
    } finally {
      this.saving.set(false);
    }
  }

  protected async deactivate(c: ExpenseCategory) {
    if (!confirm(`Désactiver la catégorie « ${c.name} » ?`)) return;
    await firstValueFrom(this.http.delete(`/api/v1/expense-categories/${c.id}`));
    this.load();
  }

  protected async load() {
    this.loading.set(true);
    try {
      const params = this.includeInactive ? '?includeInactive=true' : '';
      const list = await firstValueFrom(
        this.http.get<ExpenseCategory[]>(`/api/v1/expense-categories${params}`)
      );
      this.categories.set(list ?? []);
    } catch {
      this.categories.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private emptyForm(): CategoryForm {
    return { name: '', parentId: null, color: '#3B82F6', active: true };
  }
}
