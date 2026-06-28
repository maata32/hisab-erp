import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { DropdownModule } from 'primeng/dropdown';
import { LocaleSwitcherComponent } from '@hisaberp/shared-ui';
import { LocaleService } from '@hisaberp/shared-i18n';
import { isApiError } from '@hisaberp/shared-api';
import { firstValueFrom } from 'rxjs';

interface Plan {
  code: string;
  name: string;
  monthlyPrice: number;
  annualPrice: number | null;
  maxCashRegisters: number | null;
  maxUsers: number | null;
  maxProducts: number | null;
}

@Component({
  selector: 'erp-admin-register-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    TranslateModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    DropdownModule,
    LocaleSwitcherComponent,
  ],
  template: `
    <div class="min-h-screen flex flex-col md:flex-row">
      <aside
        class="hidden md:flex md:w-2/5 bg-gradient-to-br from-primary-600 to-primary-800 text-white p-12 flex-col justify-between"
      >
        <div>
          <img src="assets/brand/hisab-logo-ondark.svg" alt="HisabERP" class="h-14 w-auto" />
          <p class="mt-2 opacity-90">{{ 'app.tagline' | translate }}</p>
        </div>
        <p class="text-sm opacity-70">© Hisab ERP {{ year }}</p>
      </aside>

      <main class="flex-1 flex items-center justify-center p-6 sm:p-10 overflow-auto">
        @if (done()) {
          <div class="w-full max-w-md text-center space-y-5">
            <i class="pi pi-check-circle text-6xl text-green-500"></i>
            <h1 class="text-2xl font-bold text-gray-800">{{ 'registration.success.title' | translate }}</h1>
            <p class="text-gray-600">
              {{ 'registration.success.message' | translate: { code: submittedCode() } }}
            </p>
            <a routerLink="/auth/login" pButton
               [label]="'registration.success.toLogin' | translate" class="p-button-outlined"></a>
          </div>
        } @else {
          <form
            [formGroup]="form"
            (ngSubmit)="submit()"
            class="w-full max-w-xl space-y-5"
            novalidate
            aria-labelledby="register-title"
          >
            <div class="flex items-center justify-between">
              <h1 id="register-title" class="text-2xl font-bold text-gray-800">
                {{ 'registration.title' | translate }}
              </h1>
              <me-locale-switcher />
            </div>
            <p class="text-sm text-gray-500">{{ 'registration.subtitle' | translate }}</p>

            <!-- Company -->
            <h3 class="text-sm font-semibold text-gray-700 uppercase tracking-wide pt-2">
              {{ 'registration.section.company' | translate }}
            </h3>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div class="space-y-1">
                <label for="companyName" class="text-sm font-medium text-gray-700">
                  {{ 'registration.companyName' | translate }} *
                </label>
                <input id="companyName" pInputText formControlName="companyName" class="w-full"
                       [attr.aria-invalid]="hasErr('companyName')" (blur)="suggestCode()" />
                @if (hasErr('companyName')) {
                  <p class="text-xs text-red-600">{{ 'common.required' | translate }}</p>
                }
              </div>
              <div class="space-y-1">
                <label for="tenantCode" class="text-sm font-medium text-gray-700">
                  {{ 'registration.tenantCode' | translate }} *
                </label>
                <input id="tenantCode" pInputText formControlName="tenantCode" class="w-full"
                       autocapitalize="off" autocomplete="off"
                       [attr.aria-invalid]="hasErr('tenantCode')" />
                @if (form.controls.tenantCode.touched && form.controls.tenantCode.errors?.['pattern']) {
                  <p class="text-xs text-red-600">{{ 'registration.codeFormat' | translate }}</p>
                } @else if (hasErr('tenantCode')) {
                  <p class="text-xs text-red-600">{{ 'common.required' | translate }}</p>
                } @else {
                  <p class="text-xs text-gray-400">{{ 'registration.codeHint' | translate }}</p>
                }
              </div>
              <div class="space-y-1">
                <label for="companyType" class="text-sm font-medium text-gray-700">
                  {{ 'registration.companyType' | translate }} *
                </label>
                <p-dropdown inputId="companyType" formControlName="companyType" [options]="typeOptions()"
                            optionLabel="label" optionValue="value" styleClass="w-full" appendTo="body" />
              </div>
              <div class="space-y-1">
                <label for="plan" class="text-sm font-medium text-gray-700">
                  {{ 'registration.plan' | translate }} *
                </label>
                <p-dropdown inputId="plan" formControlName="planCode" [options]="planOptions()"
                            optionLabel="label" optionValue="value" styleClass="w-full" appendTo="body"
                            [placeholder]="'registration.choosePlan' | translate" />
                @if (hasErr('planCode')) {
                  <p class="text-xs text-red-600">{{ 'common.required' | translate }}</p>
                }
              </div>
              <div class="space-y-1">
                <label for="companyPhone" class="text-sm font-medium text-gray-700">
                  {{ 'registration.phone' | translate }}
                </label>
                <input id="companyPhone" pInputText formControlName="companyPhone" class="w-full" />
              </div>
              <div class="space-y-1">
                <label for="locale" class="text-sm font-medium text-gray-700">
                  {{ 'registration.language' | translate }}
                </label>
                <p-dropdown inputId="locale" formControlName="locale" [options]="localeOptions"
                            optionLabel="label" optionValue="value" styleClass="w-full" appendTo="body" />
              </div>
            </div>

            <!-- Admin -->
            <h3 class="text-sm font-semibold text-gray-700 uppercase tracking-wide pt-2">
              {{ 'registration.section.admin' | translate }}
            </h3>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div class="space-y-1">
                <label for="adminFullName" class="text-sm font-medium text-gray-700">
                  {{ 'registration.adminName' | translate }} *
                </label>
                <input id="adminFullName" pInputText formControlName="adminFullName" class="w-full"
                       autocomplete="name" [attr.aria-invalid]="hasErr('adminFullName')" />
                @if (hasErr('adminFullName')) {
                  <p class="text-xs text-red-600">{{ 'common.required' | translate }}</p>
                }
              </div>
              <div class="space-y-1">
                <label for="adminEmail" class="text-sm font-medium text-gray-700">
                  {{ 'registration.adminEmail' | translate }} *
                </label>
                <input id="adminEmail" pInputText type="email" formControlName="adminEmail" class="w-full"
                       autocomplete="email" [attr.aria-invalid]="hasErr('adminEmail')" />
                @if (form.controls.adminEmail.touched && form.controls.adminEmail.errors?.['email']) {
                  <p class="text-xs text-red-600">{{ 'registration.emailInvalid' | translate }}</p>
                } @else if (hasErr('adminEmail')) {
                  <p class="text-xs text-red-600">{{ 'common.required' | translate }}</p>
                }
              </div>
              <div class="space-y-1 sm:col-span-2">
                <label for="password" class="text-sm font-medium text-gray-700">
                  {{ 'registration.password' | translate }} *
                </label>
                <p-password inputId="password" formControlName="password" [feedback]="true"
                            [toggleMask]="true" styleClass="w-full" inputStyleClass="w-full" />
                @if (form.controls.password.touched && form.controls.password.errors?.['minlength']) {
                  <p class="text-xs text-red-600">{{ 'registration.passwordShort' | translate }}</p>
                } @else if (hasErr('password')) {
                  <p class="text-xs text-red-600">{{ 'common.required' | translate }}</p>
                }
              </div>
            </div>

            @if (errorMessage(); as msg) {
              <div role="alert" class="rounded-md bg-red-50 p-3 text-sm text-red-700">{{ msg }}</div>
            }

            <button pButton type="submit" class="w-full min-h-touch"
                    [disabled]="loading()"
                    [label]="loading() ? ('common.loading' | translate) : ('registration.submit' | translate)">
            </button>

            <div class="text-center text-sm text-gray-500">
              {{ 'registration.haveAccount' | translate }}
              <a routerLink="/auth/login" class="text-primary-600 hover:underline">
                {{ 'registration.signIn' | translate }}
              </a>
            </div>
          </form>
        }
      </main>
    </div>
  `,
})
export class RegisterPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);
  private readonly locale = inject(LocaleService);

  protected readonly year = new Date().getFullYear();
  protected readonly loading = signal(false);
  protected readonly done = signal(false);
  protected readonly submittedCode = signal('');
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly plans = signal<Plan[]>([]);
  private codeEditedManually = false;

  // Loaded from the public active organization-types endpoint.
  protected readonly typeOptions = signal<{ value: string; label: string }[]>([]);

  protected readonly localeOptions = [
    { value: 'fr', label: 'Français' },
    { value: 'ar', label: 'العربية' },
    { value: 'en', label: 'English' },
  ];

  protected readonly form = this.fb.nonNullable.group({
    companyName: ['', Validators.required],
    tenantCode: ['', [Validators.required, Validators.pattern(/^[a-z0-9-]{2,50}$/)]],
    companyType: ['BOUTIQUE', Validators.required],
    planCode: ['', Validators.required],
    companyPhone: [''],
    locale: ['fr'],
    adminFullName: ['', Validators.required],
    adminEmail: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.form.controls.tenantCode.valueChanges.subscribe(() => {
      if (this.form.controls.tenantCode.dirty) this.codeEditedManually = true;
    });
    void this.loadPlans();
    void this.loadTypes();
  }

  protected planOptions() {
    return this.plans().map((p) => ({ value: p.code, label: this.planLabel(p) }));
  }

  /** "Starter — 1500 MRU/mois · 1 caisses · 3 utilisateurs · 500 produits" (∞ when unlimited). */
  private planLabel(p: Plan): string {
    const month = this.translate.instant('registration.perMonth');
    const lim = (v: number | null) => (v == null ? '∞' : v);
    const cond =
      `${lim(p.maxCashRegisters)} ${this.translate.instant('plans.unit.cashRegisters')}` +
      ` · ${lim(p.maxUsers)} ${this.translate.instant('plans.unit.users')}` +
      ` · ${lim(p.maxProducts)} ${this.translate.instant('plans.unit.products')}`;
    return `${p.name} — ${p.monthlyPrice} MRU/${month} · ${cond}`;
  }

  /** Derive a slug from the company name until the user edits the code field themselves. */
  protected suggestCode(): void {
    if (this.codeEditedManually) return;
    const name = this.form.controls.companyName.value ?? '';
    const slug = name
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 50);
    if (slug.length >= 2) this.form.controls.tenantCode.setValue(slug, { emitEvent: false });
  }

  protected hasErr(field: string): boolean {
    const c = this.form.controls[field as keyof typeof this.form.controls];
    return !!(c && c.touched && c.invalid);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    try {
      const v = this.form.getRawValue();
      await firstValueFrom(
        this.http.post('/api/v1/registrations', {
          tenantCode: v.tenantCode,
          companyName: v.companyName,
          companyType: v.companyType,
          planCode: v.planCode,
          companyPhone: v.companyPhone || null,
          locale: v.locale,
          adminFullName: v.adminFullName,
          adminEmail: v.adminEmail,
          password: v.password,
        }),
      );
      this.submittedCode.set(v.tenantCode);
      this.done.set(true);
    } catch (err: unknown) {
      const body = (err as { error?: unknown })?.error;
      const apiError = isApiError(body) ? body : null;
      if (apiError?.code === 'error.data_integrity') {
        this.errorMessage.set(this.translate.instant('registration.codeTaken'));
      } else {
        // Prefer the specific field error, then a known translated code, then the
        // server-localized message, then a generic fallback — never a raw key.
        const fieldMsg = apiError?.fieldErrors?.[0]?.message;
        const translated = apiError ? this.translate.instant(apiError.code) : null;
        this.errorMessage.set(
          fieldMsg
          || (translated && translated !== apiError?.code ? translated : null)
          || apiError?.message
          || this.translate.instant('registration.error'),
        );
      }
    } finally {
      this.loading.set(false);
    }
  }

  private async loadPlans(): Promise<void> {
    try {
      const list = await firstValueFrom(this.http.get<Plan[]>('/api/v1/plans'));
      this.plans.set(list ?? []);
    } catch {
      this.plans.set([]);
    }
  }

  private async loadTypes(): Promise<void> {
    try {
      const list = await firstValueFrom(
        this.http.get<{ code: string; label: string }[]>('/api/v1/organization-types?activeOnly=true'),
      );
      this.typeOptions.set((list ?? []).map((t) => ({ value: t.code, label: t.label })));
    } catch {
      this.typeOptions.set([]);
    }
  }
}
