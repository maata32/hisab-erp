import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'erp-admin-forbidden',
  standalone: true,
  imports: [TranslateModule, RouterLink, ButtonModule],
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center text-center p-6">
      <i class="pi pi-lock text-7xl text-gray-300"></i>
      <h1 class="mt-4 text-3xl font-bold text-gray-800">403</h1>
      <p class="mt-2 text-gray-600 max-w-md">{{ 'errors.forbidden' | translate }}</p>
      <a routerLink="/" pButton class="mt-6" [label]="'errors.back_home' | translate"></a>
    </div>
  `,
})
export class ForbiddenPage {}
