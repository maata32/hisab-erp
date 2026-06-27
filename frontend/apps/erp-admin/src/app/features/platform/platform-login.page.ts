import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { AUTH_SERVICE } from '@hisaberp/shared-auth';
import { isApiError } from '@hisaberp/shared-api';
import { LocaleSwitcherComponent } from '@hisaberp/shared-ui';
import { LocaleService, SupportedLocale } from '@hisaberp/shared-i18n';

/**
 * Platform (super-admin) sign-in — email + password, no organization code. Issues a
 * cross-tenant session limited to the super-admin console.
 */
@Component({
  selector: 'erp-admin-platform-login-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    TranslateModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    LocaleSwitcherComponent,
  ],
  template: `
    <div class="min-h-screen flex flex-col md:flex-row">
      <aside
        class="hidden md:flex md:w-1/2 bg-gradient-to-br from-gray-800 to-gray-900 text-white p-12 flex-col justify-between"
      >
        <div>
          <img src="assets/brand/hisab-logo-ondark.svg" alt="HisabERP" class="h-14 w-auto" />
          <p class="mt-2 opacity-90">{{ 'platform.login.subtitle' | translate }}</p>
        </div>
        <p class="text-sm opacity-70">© Hisab ERP {{ year }}</p>
      </aside>

      <main class="flex-1 flex items-center justify-center p-6 sm:p-10">
        <form
          [formGroup]="form"
          (ngSubmit)="submit()"
          class="w-full max-w-md space-y-5"
          novalidate
          aria-labelledby="platform-login-title"
        >
          <div class="flex items-center justify-between">
            <h2 id="platform-login-title" class="text-2xl font-bold text-gray-800">
              {{ 'platform.login.title' | translate }}
            </h2>
            <me-locale-switcher />
          </div>

          <div class="space-y-2">
            <label for="email" class="text-sm font-medium text-gray-700">
              {{ 'auth.login.email' | translate }}
            </label>
            <input
              id="email"
              pInputText
              formControlName="email"
              type="email"
              class="w-full"
              autocomplete="email"
              [attr.aria-invalid]="hasErr('email')"
            />
          </div>

          <div class="space-y-2">
            <label for="password" class="text-sm font-medium text-gray-700">
              {{ 'auth.login.password' | translate }}
            </label>
            <p-password
              inputId="password"
              formControlName="password"
              [feedback]="false"
              [toggleMask]="true"
              styleClass="w-full"
              inputStyleClass="w-full"
            />
          </div>

          @if (errorMessage(); as msg) {
            <div role="alert" class="rounded-md bg-red-50 p-3 text-sm text-red-700">{{ msg }}</div>
          }

          <button
            pButton
            type="submit"
            class="w-full min-h-touch"
            [disabled]="form.invalid || loading()"
            [label]="loading() ? ('common.loading' | translate) : ('platform.login.submit' | translate)"
          ></button>

          <div class="text-center text-sm text-gray-500">
            <a routerLink="/auth/login" class="text-primary-600 hover:underline">
              {{ 'platform.login.backToTenant' | translate }}
            </a>
          </div>
        </form>
      </main>
    </div>
  `,
})
export class PlatformLoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AUTH_SERVICE);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly locale = inject(LocaleService);

  protected readonly year = new Date().getFullYear();
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(1)]],
  });

  hasErr(field: string): boolean {
    const c = this.form.controls[field as keyof typeof this.form.controls];
    return !!(c && c.touched && c.invalid);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.platformLogin(email, password).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.user.preferredLanguage) {
          this.auth.setCurrentLanguage(res.user.preferredLanguage);
          this.locale.use(res.user.preferredLanguage as SupportedLocale);
        }
        this.router.navigateByUrl('/organizations');
      },
      error: (err) => {
        this.loading.set(false);
        const apiError = isApiError(err.error) ? err.error : null;
        this.errorMessage.set(
          apiError?.message || this.translate.instant(apiError?.code ?? 'auth.bad_credentials'),
        );
      },
    });
  }
}
