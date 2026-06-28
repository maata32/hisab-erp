import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { LocaleSwitcherComponent } from '@hisaberp/shared-ui';
import { AUTH_SERVICE } from '@hisaberp/shared-auth';
import { isApiError } from '@hisaberp/shared-api';

@Component({
  selector: 'pos-login-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, TranslateModule,
    ButtonModule, InputTextModule, PasswordModule, LocaleSwitcherComponent,
  ],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-600 to-primary-700 p-4">
      <form
        [formGroup]="form"
        (ngSubmit)="submit()"
        class="w-full max-w-sm bg-white rounded-2xl shadow-xl p-6 space-y-5"
        novalidate
      >
        <h1 class="sr-only">{{ 'auth.submit' | translate }}</h1>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <img src="assets/brand/hisab-logo.svg" alt="HisabERP" class="h-8 w-auto" />
            <span class="text-[10px] font-bold tracking-widest text-primary-600 bg-primary-50 rounded px-1.5 py-0.5">POS</span>
          </div>
          <me-locale-switcher />
        </div>

        <div>
          <label for="tenantCode" class="text-sm font-medium">{{ 'auth.tenantCode' | translate }}</label>
          <input id="tenantCode" pInputText formControlName="tenantCode" class="w-full mt-1" autocomplete="organization" />
        </div>
        <div>
          <label for="email" class="text-sm font-medium">{{ 'auth.email' | translate }}</label>
          <input id="email" pInputText formControlName="email" type="email" class="w-full mt-1" autocomplete="email" />
        </div>
        <div>
          <label for="password" class="text-sm font-medium">{{ 'auth.password' | translate }}</label>
          <p-password inputId="password" formControlName="password" [feedback]="false" [toggleMask]="true"
                      styleClass="w-full" inputStyleClass="w-full" />
        </div>

        @if (errorMessage(); as msg) {
          <div role="alert" class="rounded-md bg-red-50 p-3 text-sm text-red-700">{{ msg }}</div>
        }

        <button pButton type="submit" class="w-full min-h-touch"
                [disabled]="form.invalid || loading()"
                [label]="loading() ? ('common.loading' | translate) : ('auth.submit' | translate)"></button>
      </form>
    </div>
  `,
})
export class PosLoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AUTH_SERVICE);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    tenantCode: ['demo', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.auth.login(this.form.getRawValue() as { tenantCode: string; email: string; password: string }).subscribe({
      next: () => { this.loading.set(false); this.router.navigateByUrl('/sale'); },
      error: (err) => {
        this.loading.set(false);
        const code = isApiError(err.error) ? err.error.code : 'auth.bad_credentials';
        this.errorMessage.set(code);
      },
    });
  }
}
