import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { EmptyStateComponent } from '@minierp/shared-ui';

@Component({
  selector: 'pos-session-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, EmptyStateComponent],
  template: `
    <me-empty-state
      icon="pi pi-box"
      [title]="'pos.session.coming_soon.title' | translate"
      [description]="'pos.session.coming_soon.description' | translate"
    />
  `,
})
export class SessionPage {}
