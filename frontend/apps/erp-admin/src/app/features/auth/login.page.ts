import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageService } from 'primeng/api';
import { AUTH_SERVICE } from '@minierp/shared-auth';
import { isApiError } from '@minierp/shared-api';
import { LocaleSwitcherComponent } from '@minierp/shared-ui';
import { LocaleService } from '@minierp/shared-i18n';

@Component({
  selector: 'erp-admin-login-page',
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
        class="hidden md:flex md:w-1/2 bg-gradient-to-br from-primary-600 to-primary-800 text-white p-12 flex-col justify-between"
      >
        <div>
          <h1 class="text-3xl font-bold">Mini-ERP</h1>
          <p class="mt-2 opacity-90">{{ 'app.tagline' | translate }}</p>
        </div>
        <p class="text-sm opacity-70">© Mini-ERP {{ year }}</p>
      </aside>

      <main class="flex-1 flex items-center justify-center p-6 sm:p-10">
        <form
          [formGroup]="form"
          (ngSubmit)="submit()"
          class="w-full max-w-md space-y-5"
          novalidate
          aria-labelledby="login-title"
        >
          <div class="flex items-center justify-between">
            <h2 id="login-title" class="text-2xl font-bold text-gray-800">
              {{ 'auth.login.title' | translate }}
            </h2>
            <me-locale-switcher />
          </div>

          <div class="space-y-2">
            <label for="tenantCode" class="text-sm font-medium text-gray-700">
              {{ 'auth.login.tenantCode' | translate }}
            </label>
            <input
              id="tenantCode"
              pInputText
              formControlName="tenantCode"
              class="w-full"
              autocomplete="organization"
              [attr.aria-invalid]="hasErr('tenantCode')"
            />
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
            [label]="loading() ? ('common.loading' | translate) : ('auth.login.submit' | translate)"
          ></button>

          <div class="text-center text-sm text-gray-500">
            {{ 'auth.login.demo_hint' | translate }}
          </div>

          <div class="text-center text-sm text-gray-500">
            {{ 'auth.login.noAccount' | translate }}
            <a routerLink="/register" class="text-primary-600 hover:underline">
              {{ 'auth.login.signUp' | translate }}
            </a>
          </div>
        </form>
      </main>
    </div>
  `,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AUTH_SERVICE);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(MessageService);
  private readonly translate = inject(TranslateService);
  private readonly locale = inject(LocaleService);

  protected readonly year = new Date().getFullYear();
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    tenantCode: ['demo', Validators.required],
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
    this.auth.login(this.form.getRawValue() as any).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.user.preferredLanguage) {
          this.auth.setCurrentLanguage(res.user.preferredLanguage);
          this.locale.use(res.user.preferredLanguage as any);
        }
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.loading.set(false);
        const apiError = isApiError(err.error) ? err.error : null;
        const code = apiError?.code ?? 'auth.bad_credentials';
        this.errorMessage.set(this.translate.instant(code));
      },
    });
  }
}
