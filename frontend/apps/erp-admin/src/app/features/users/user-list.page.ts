import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { TooltipModule } from 'primeng/tooltip';
import { firstValueFrom } from 'rxjs';

interface User {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  preferredLanguage: string;
  active: boolean;
  lastLoginAt: string | null;
  locked: boolean;
  roleCodes: string[];
}

interface Role { id: string; code: string; name: string; description: string; }

interface UserForm {
  email: string;
  fullName: string;
  phone: string;
  preferredLanguage: string;
  password: string;
  roleCodes: string[];
  active: boolean;
}

@Component({
  selector: 'erp-admin-user-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, TableModule, TagModule,
    ButtonModule, DialogModule, DropdownModule, InputTextModule, MultiSelectModule, TooltipModule,
  ],
  template: `
    <div class="space-y-4">
      <header class="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 class="text-2xl font-bold text-gray-800">{{ 'users.title' | translate }}</h1>
          <p class="text-gray-500 text-sm mt-1">{{ 'users.subtitle' | translate }}</p>
        </div>
        <button pButton icon="pi pi-plus" [label]="'users.create' | translate"
                (click)="openCreate()" class="p-button-sm"></button>
      </header>

      <div class="bg-white rounded-lg border border-gray-200 p-4">
        <div class="mb-3">
          <span class="relative block w-full sm:w-72">
            <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none"></i>
            <input pInputText type="text" [placeholder]="'common.search' | translate"
                   (input)="onSearch($event)" class="w-full !pl-9" />
          </span>
        </div>

        <p-table #table [value]="users()" [loading]="loading()" stripedRows
                 [lazy]="true" (onLazyLoad)="loadChunk($event)"
                 [totalRecords]="total()" [rows]="pageSize"
                 [scrollable]="true" scrollHeight="600px"
                 [virtualScroll]="true" [virtualScrollItemSize]="48"
                 [rowHover]="true" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>{{ 'users.email' | translate }}</th>
              <th>{{ 'users.fullName' | translate }}</th>
              <th>{{ 'users.phone' | translate }}</th>
              <th>{{ 'users.roles' | translate }}</th>
              <th>{{ 'users.lastLogin' | translate }}</th>
              <th>{{ 'users.status' | translate }}</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-u>
            <tr>
              <td class="font-medium">{{ u.email }}</td>
              <td>{{ u.fullName }}</td>
              <td>{{ u.phone || '—' }}</td>
              <td>
                <div class="flex flex-wrap gap-1">
                  @for (r of u.roleCodes; track r) {
                    <span class="px-2 py-0.5 bg-blue-50 text-blue-700 rounded text-xs">{{ r }}</span>
                  }
                </div>
              </td>
              <td class="text-sm text-gray-500">
                {{ u.lastLoginAt ? (u.lastLoginAt | date:'short') : '—' }}
              </td>
              <td>
                @if (u.locked) {
                  <p-tag [value]="'users.locked' | translate" severity="danger" />
                } @else if (u.active) {
                  <p-tag [value]="'common.active' | translate" severity="success" />
                } @else {
                  <p-tag [value]="'common.inactive' | translate" severity="secondary" />
                }
              </td>
              <td class="whitespace-nowrap">
                <button pButton icon="pi pi-pencil" class="p-button-sm p-button-text"
                        [pTooltip]="'common.edit' | translate"
                        (click)="openEdit(u)"></button>
                <button pButton icon="pi pi-key" class="p-button-sm p-button-text p-button-warning"
                        [pTooltip]="'users.resetPassword' | translate"
                        (click)="resetPassword(u)"></button>
                @if (u.locked) {
                  <button pButton icon="pi pi-unlock" class="p-button-sm p-button-text p-button-help"
                          [pTooltip]="'users.unlock' | translate"
                          (click)="unlock(u)"></button>
                }
                @if (u.active) {
                  <button pButton icon="pi pi-trash" class="p-button-sm p-button-text p-button-danger"
                          [pTooltip]="'common.deactivate' | translate"
                          (click)="deactivate(u)"></button>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7" class="text-center text-gray-400 py-8">
              {{ 'users.empty' | translate }}
            </td></tr>
          </ng-template>
        </p-table>
      </div>

      <p-dialog [(visible)]="dialogOpen" [modal]="true" [style]="{ width: '500px' }"
                [header]="(editing() ? 'users.editTitle' : 'users.createTitle') | translate"
                [closable]="!saving()">
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'users.email' | translate }} *</label>
            <input pInputText type="email" [(ngModel)]="form.email" class="w-full"
                   [class.ng-invalid]="emailInvalid()" [class.ng-dirty]="emailInvalid()" />
            @if (emailInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'users.fullName' | translate }} *</label>
            <input pInputText [(ngModel)]="form.fullName" class="w-full"
                   [class.ng-invalid]="fullNameInvalid()" [class.ng-dirty]="fullNameInvalid()" />
            @if (fullNameInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'users.phone' | translate }}</label>
              <input pInputText [(ngModel)]="form.phone" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'users.language' | translate }}</label>
              <p-dropdown [(ngModel)]="form.preferredLanguage" [options]="languageOptions"
                          optionLabel="label" optionValue="value" styleClass="w-full" />
            </div>
          </div>
          @if (!editing()) {
            <div>
              <label class="block text-sm font-medium mb-1">{{ 'users.password' | translate }} *</label>
              <input pInputText type="password" [(ngModel)]="form.password" class="w-full"
                     [class.ng-invalid]="passwordInvalid()" [class.ng-dirty]="passwordInvalid()" />
              @if (passwordInvalid()) {
                <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
              } @else {
                <p class="text-xs text-gray-500 mt-1">{{ 'users.passwordHint' | translate }}</p>
              }
            </div>
          }
          <div>
            <label class="block text-sm font-medium mb-1">{{ 'users.roles' | translate }} *</label>
            <p-multiSelect [(ngModel)]="form.roleCodes" [options]="roles()"
                           optionLabel="name" optionValue="code"
                           [styleClass]="'w-full' + (rolesInvalid() ? ' ng-invalid ng-dirty' : '')"
                           [placeholder]="'users.pickRoles' | translate" display="chip" />
            @if (rolesInvalid()) {
              <p class="text-xs text-red-600 mt-1">{{ 'common.required' | translate }}</p>
            }
          </div>
          @if (editing()) {
            <div class="flex items-center gap-2">
              <input type="checkbox" id="active" [(ngModel)]="form.active" class="rounded" />
              <label for="active" class="text-sm">{{ 'users.active' | translate }}</label>
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

      <p-dialog [(visible)]="tempPasswordOpen" [modal]="true" [style]="{ width: '420px' }"
                [header]="'users.tempPasswordTitle' | translate">
        <div class="space-y-3">
          <p class="text-sm text-gray-600">{{ 'users.tempPasswordHint' | translate }}</p>
          <div class="bg-gray-50 border rounded p-3 font-mono text-lg text-center select-all">
            {{ tempPassword() }}
          </div>
        </div>
        <ng-template pTemplate="footer">
          <button pButton [label]="'common.close' | translate"
                  (click)="tempPasswordOpen = false"></button>
        </ng-template>
      </p-dialog>
    </div>
  `,
})
export class UserListPage implements OnInit {
  private http = inject(HttpClient);
  private i18n = inject(TranslateService);
  private confirmation = inject(ConfirmationService);

  protected users = signal<User[]>([]);
  protected roles = signal<Role[]>([]);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected total = signal(0);
  private searchQuery = '';
  protected loading = signal(true);
  protected saving = signal(false);
  protected submitted = signal(false);
  protected dialogOpen = false;
  protected editing = signal<User | null>(null);
  protected form: UserForm = this.emptyForm();

  protected emailInvalid(): boolean { return this.submitted() && !this.form.email?.trim(); }
  protected fullNameInvalid(): boolean { return this.submitted() && !this.form.fullName?.trim(); }
  protected passwordInvalid(): boolean { return this.submitted() && !this.editing() && !this.form.password?.trim(); }
  protected rolesInvalid(): boolean { return this.submitted() && this.form.roleCodes.length === 0; }
  protected tempPasswordOpen = false;
  protected tempPassword = signal<string>('');
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly languageOptions = [
    { value: 'fr', label: 'Français' },
    { value: 'en', label: 'English' },
    { value: 'ar', label: 'العربية' },
  ];

  ngOnInit() {
    this.loadRoles();
    // Users are fetched on demand via the p-table's onLazyLoad.
  }

  protected onSearch(e: Event) {
    const q = (e.target as HTMLInputElement).value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.searchQuery = q;
      this.reload();
    }, 300);
  }

  protected openCreate() {
    this.editing.set(null);
    this.form = this.emptyForm();
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected openEdit(u: User) {
    this.editing.set(u);
    this.form = {
      email: u.email,
      fullName: u.fullName,
      phone: u.phone ?? '',
      preferredLanguage: u.preferredLanguage ?? 'fr',
      password: '',
      roleCodes: [...u.roleCodes],
      active: u.active,
    };
    this.submitted.set(false);
    this.dialogOpen = true;
  }

  protected async save() {
    this.submitted.set(true);
    if (!this.form.email?.trim() || !this.form.fullName?.trim()) return;
    if (!this.editing() && !this.form.password?.trim()) return;
    if (this.form.roleCodes.length === 0) return;
    this.saving.set(true);
    try {
      const current = this.editing();
      if (current) {
        await firstValueFrom(this.http.put(`/api/v1/users/${current.id}`, {
          email: this.form.email,
          fullName: this.form.fullName,
          phone: this.form.phone || null,
          preferredLanguage: this.form.preferredLanguage,
          active: this.form.active,
          roleCodes: this.form.roleCodes,
        }));
      } else {
        await firstValueFrom(this.http.post('/api/v1/users', {
          email: this.form.email,
          fullName: this.form.fullName,
          phone: this.form.phone || null,
          preferredLanguage: this.form.preferredLanguage,
          password: this.form.password,
          roleCodes: this.form.roleCodes,
        }));
      }
      this.dialogOpen = false;
      this.reload();
    } finally {
      this.saving.set(false);
    }
  }

  protected deactivate(u: User) {
    this.confirmation.confirm({
      message: `Désactiver l'utilisateur « ${u.fullName} » ?`,
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm p-button-danger',
      accept: async () => {
        await firstValueFrom(this.http.delete(`/api/v1/users/${u.id}`));
        this.reload();
      },
    });
  }

  protected resetPassword(u: User) {
    this.confirmation.confirm({
      message: `Réinitialiser le mot de passe de « ${u.fullName} » ?`,
      header: this.i18n.instant('common.confirmation'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-sm',
      accept: async () => {
        const res = await firstValueFrom(
          this.http.post<{ temporaryPassword: string }>(`/api/v1/users/${u.id}/reset-password`, {})
        );
        this.tempPassword.set(res.temporaryPassword);
        this.tempPasswordOpen = true;
      },
    });
  }

  protected async unlock(u: User) {
    await firstValueFrom(this.http.post(`/api/v1/users/${u.id}/unlock`, {}));
    this.reload();
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    const q = this.searchQuery ? `&q=${encodeURIComponent(this.searchQuery)}` : '';
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content: User[]; totalElements: number }>(
          `/api/v1/users?page=${page}&size=${rows}${q}`
        )
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.users()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.users.set(arr);
      this.total.set(totalElements);
    } catch {
      this.users.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  protected reload() {
    this.users.set([]);
    this.total.set(0);
    this.table?.reset();
  }

  private async loadRoles() {
    try {
      const list = await firstValueFrom(this.http.get<Role[]>('/api/v1/users/roles'));
      this.roles.set(list ?? []);
    } catch {
      this.roles.set([]);
    }
  }

  private emptyForm(): UserForm {
    return {
      email: '',
      fullName: '',
      phone: '',
      preferredLanguage: 'fr',
      password: '',
      roleCodes: [],
      active: true,
    };
  }
}
