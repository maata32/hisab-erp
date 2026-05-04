import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { EmptyStateComponent } from '@minierp/shared-ui';

@Component({
  selector: 'pos-sale-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, EmptyStateComponent],
  template: `
    <me-empty-state
      icon="pi pi-shopping-cart"
      [title]="'pos.sale.coming_soon.title' | translate"
      [description]="'pos.sale.coming_soon.description' | translate"
    />
  `,
})
export class SalePage {}
