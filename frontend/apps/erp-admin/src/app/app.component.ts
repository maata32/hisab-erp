import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { LocaleService, MoneyFormatService } from '@minierp/shared-i18n';
import { AUTH_SERVICE } from '@minierp/shared-auth';

@Component({
  selector: 'erp-admin-root',
  standalone: true,
  imports: [RouterOutlet, TranslateModule, ToastModule, ConfirmDialogModule],
  template: `
    <p-toast position="top-right" />
    <p-confirmDialog [acceptLabel]="'common.confirm' | translate"
                     [rejectLabel]="'common.cancel' | translate"
                     acceptButtonStyleClass="p-button-sm"
                     rejectButtonStyleClass="p-button-sm p-button-text" />
    <router-outlet />
  `,
})
export class AppComponent implements OnInit {
  private readonly localeService = inject(LocaleService);
  private readonly moneyFormat = inject(MoneyFormatService);
  private readonly auth = inject(AUTH_SERVICE);

  ngOnInit(): void {
    this.localeService.initialize(this.auth.getCurrentLanguage());
    this.moneyFormat.load();
  }
}
