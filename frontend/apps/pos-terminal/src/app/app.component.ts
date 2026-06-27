import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { LocaleService } from '@hisaberp/shared-i18n';
import { AUTH_SERVICE } from '@hisaberp/shared-auth';
import { OnlineStatusService } from './services/online-status.service';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'pos-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, ToastModule, TranslateModule],
  template: `
    <p-toast position="top-right" />
    @if (!online()) {
      <div
        role="status"
        class="bg-amber-100 text-amber-900 text-center text-sm py-1 border-b border-amber-200"
      >
        <i class="pi pi-wifi"></i>&nbsp; {{ 'pos.offline_banner' | translate }}
      </div>
    }
    <router-outlet />
  `,
})
export class AppComponent implements OnInit {
  private readonly localeService = inject(LocaleService);
  private readonly auth = inject(AUTH_SERVICE);
  private readonly onlineSvc = inject(OnlineStatusService);

  protected readonly online = signal(true);

  ngOnInit(): void {
    this.localeService.initialize(this.auth.getCurrentLanguage());
    this.onlineSvc.online$.subscribe((v) => this.online.set(v));
  }
}
