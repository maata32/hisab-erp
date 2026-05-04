import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { LocaleService } from '@minierp/shared-i18n';
import { AUTH_SERVICE } from '@minierp/shared-auth';

@Component({
  selector: 'erp-admin-root',
  standalone: true,
  imports: [RouterOutlet, ToastModule, ConfirmDialogModule],
  template: `
    <p-toast position="top-right" />
    <p-confirmDialog />
    <router-outlet />
  `,
})
export class AppComponent implements OnInit {
  private readonly localeService = inject(LocaleService);
  private readonly auth = inject(AUTH_SERVICE);

  ngOnInit(): void {
    this.localeService.initialize(this.auth.getCurrentLanguage());
  }
}
