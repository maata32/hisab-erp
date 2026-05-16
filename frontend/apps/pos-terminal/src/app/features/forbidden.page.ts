import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'pos-forbidden-page',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule],
  template: `
    <div class="flex flex-col items-center justify-center h-full text-center p-8">
      <i class="pi pi-lock text-6xl text-yellow-500 mb-4"></i>
      <h2 class="text-2xl font-bold mb-2">{{ 'pos.forbidden.title' | translate }}</h2>
      <p class="text-gray-600 mb-4 max-w-md">{{ 'pos.forbidden.description' | translate }}</p>
      <a routerLink="/sale" class="text-primary-700 underline">
        {{ 'pos.forbidden.back_to_sale' | translate }}
      </a>
    </div>
  `,
})
export class ForbiddenPage {}
