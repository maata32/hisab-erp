import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { EmptyStateComponent } from '@minierp/shared-ui';

@Component({
  selector: 'erp-admin-user-list',
  standalone: true,
  imports: [CommonModule, TranslateModule, EmptyStateComponent],
  template: `
    <header class="mb-4">
      <h1 class="text-2xl font-bold text-gray-800">{{ 'users.title' | translate }}</h1>
    </header>
    <me-empty-state
      icon="pi pi-users"
      [title]="'users.coming_soon.title' | translate"
      [description]="'users.coming_soon.description' | translate"
    />
  `,
})
export class UserListPage {}
