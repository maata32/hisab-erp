import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { CheckboxModule } from 'primeng/checkbox';
import { firstValueFrom } from 'rxjs';

interface AttributeValue {
  id: string;
  attributeId: string;
  value: string;
  code: string | null;
  sortOrder: number;
  active: boolean;
}

interface Attribute {
  id: string;
  name: string;
  sortOrder: number;
  active: boolean;
  values: AttributeValue[];
}

interface AttributeForm {
  name: string;
  sortOrder: number;
  active: boolean;
}

@Component({
  selector: 'erp-admin-attribute-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule, ButtonModule,
    DialogModule, InputTextModule, InputNumberModule, TooltipModule, CheckboxModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'attributes.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'attributes.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'attributes.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <p-table [value]="attributes()" [loading]="loading()" stripedRows responsiveLayout="scroll"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'attributes.name' | translate }}</th>
              <th>{{ 'attributes.values' | translate }}</th>
              <th>{{ 'common.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-a>
            <tr>
              <td class="font-medium">{{ a.name }}</td>
              <td>
                @if (a.values?.length) {
                  <span class="flex flex-wrap gap-1">
                    @for (v of a.values; track v.id) {
                      <span class="px-2 py-0.5 text-xs rounded-full border"
                            [class.opacity-40]="!v.active"
                            [class.bg-gray-50]="v.active">{{ v.value }}</span>
                    }
                  </span>
                } @else { <span class="text-gray-300">—</span> }
              </td>
              <td>
                <p-tag [value]="(a.active ? 'common.active' : 'common.inactive') | translate"
                       [severity]="a.active ? 'success' : 'secondary'" />
              </td>
              <td class="whitespace-nowrap text-right">
                <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                        [pTooltip]="'attributes.manage' | translate"
                        (click)="openEdit(a)"></button>
                <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                        [pTooltip]="'common.delete' | translate"
                        (click)="remove(a)"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="4" class="text-center text-gray-400 py-8">
              {{ 'attributes.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '480px' }"
                [header]="(editing() ? 'attributes.editTitle' : 'attributes.createTitle') | translate"
                [closable]="!saving()">
        <div class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div class="col-span-2">
              <label class="block text-sm font-medium mb-1">{{ 'attributes.name' | translate }} *</label>
              <input pInputText [(ngModel)]="form.name" class="w-full"
                     [class.ng-invalid]="nameInvalid()" [class.ng-dirty]="nameInvalid()" />
              @if (nameInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              }
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'attributes.sortOrder' | translate }}</label>
              <p-inputNumber [(ngModel)]="form.sortOrder" [min]="0" styleClass="w-full" inputStyleClass="w-full" />
            </div>
          </div>
          @if (editing()) {
            <div class="flex items-center gap-2">
              <p-checkbox [(ngModel)]="form.active" [binary]="true" inputId="attrActive" />
              <label for="attrActive" class="text-sm">{{ 'common.active' | translate }}</label>
            </div>

            <div class="border-t pt-3">
              <label class="block text-sm font-medium mb-2">{{ 'attributes.values' | translate }}</label>
              <div class="space-y-1 max-h-56 overflow-auto">
                @for (v of editValues(); track v.id) {
                  <div class="flex items-center gap-2 text-sm">
                    <span class="flex-1" [class.line-through]="!v.active" [class.text-gray-400]="!v.active">{{ v.value }}</span>
                    @if (v.code) { <span class="font-mono text-xs text-gray-400">{{ v.code }}</span> }
                    <button pButton icon="pi pi-trash" class="p-button-xs p-button-text p-button-danger"
                            [attr.aria-label]="'common.delete' | translate"
                            (click)="removeValue(v)"></button>
                  </div>
                }
                @if (!editValues().length) {
                  <p class="text-xs text-gray-400">{{ 'attributes.noValues' | translate }}</p>
                }
              </div>
              <div class="flex items-end gap-2 mt-3">
                <div class="flex-1">
                  <label class="block text-xs text-gray-500 mb-1">{{ 'attributes.valueLabel' | translate }}</label>
                  <input pInputText [(ngModel)]="newValue" class="w-full" (keydown.enter)="addValue()" />
                </div>
                <div class="w-24">
                  <label class="block text-xs text-gray-500 mb-1">{{ 'attributes.code' | translate }}</label>
                  <input pInputText [(ngModel)]="newCode" class="w-full" (keydown.enter)="addValue()" />
                </div>
                <button pButton icon="pi pi-plus" class="p-button-sm" [loading]="addingValue()"
                        (click)="addValue()"></button>
              </div>
            </div>
          } @else {
            <p class="text-xs text-gray-500">{{ 'attributes.createHint' | translate }}</p>
          }
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.close' | translate" class="p-button-text"
                  (click)="dialogOpen = false" [disabled]="saving()"></button>
          <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                  (click)="save()" [loading]="saving()"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class AttributeListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);
  private toast = inject(MessageService);

  protected attributes = signal<Attribute[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected addingValue = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;
  protected editing = signal<Attribute | null>(null);
  protected editValues = signal<AttributeValue[]>([]);
  protected form: AttributeForm = this.emptyForm();
  protected newValue = '';
  protected newCode = '';

  protected nameInvalid(): boolean {
    return this.submitted() && !this.form.name?.trim();
  }

  ngOnInit() { this.load(); }

  protected openCreate() {
    this.editing.set(null);
    this.form = this.emptyForm();
    this.editValues.set([]);
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected openEdit(a: Attribute) {
    this.editing.set(a);
    this.form = { name: a.name, sortOrder: a.sortOrder, active: a.active };
    this.editValues.set([...(a.values ?? [])]);
    this.newValue = '';
    this.newCode = '';
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.name?.trim()) return;
    this.saving.set(true);
    try {
      const payload = { name: this.form.name.trim(), sortOrder: this.form.sortOrder ?? 0, active: this.form.active };
      const current = this.editing();
      if (current) {
        await firstValueFrom(this.http.patch(`/api/v1/attributes/${current.id}`, payload));
      } else {
        await firstValueFrom(this.http.post('/api/v1/attributes', payload));
      }
      this.dialogOpen = false;
      this.load();
    } finally {
      this.saving.set(false);
    }
  }

  protected async addValue() {
    const current = this.editing();
    const value = this.newValue?.trim();
    if (!current || !value) return;
    this.addingValue.set(true);
    try {
      const created = await firstValueFrom(this.http.post<AttributeValue>(
        `/api/v1/attributes/${current.id}/values`,
        { value, code: this.newCode?.trim() || null }));
      this.editValues.update(vs => [...vs, created]);
      this.newValue = '';
      this.newCode = '';
      this.load();
    } finally {
      this.addingValue.set(false);
    }
  }

  protected removeValue(v: AttributeValue) {
    const current = this.editing();
    if (!current) return;
    this.confirmation.confirm({
      message: this.i18n.instant('attributes.confirmDeleteValue', { value: v.value }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        try {
          await firstValueFrom(this.http.delete(`/api/v1/attributes/${current.id}/values/${v.id}`));
          this.editValues.update(vs => vs.filter(x => x.id !== v.id));
          this.load();
        } catch {
          this.toast.add({ severity: 'error', summary: this.i18n.instant('attributes.valueInUse') });
        }
      },
    });
  }

  protected remove(a: Attribute) {
    this.confirmation.confirm({
      message: this.i18n.instant('attributes.confirmDelete', { name: a.name }),
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        try {
          await firstValueFrom(this.http.delete(`/api/v1/attributes/${a.id}`));
          this.load();
        } catch {
          this.toast.add({ severity: 'error', summary: this.i18n.instant('attributes.inUse') });
        }
      },
    });
  }

  protected async load() {
    this.loading.set(true);
    try {
      const list = await firstValueFrom(this.http.get<Attribute[]>('/api/v1/attributes'));
      this.attributes.set(list ?? []);
    } catch {
      this.attributes.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private emptyForm(): AttributeForm {
    return { name: '', sortOrder: 0, active: true };
  }
}
