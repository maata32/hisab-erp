import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { SwUpdate } from '@angular/service-worker';
import { filter } from 'rxjs';
import { AUTH_SERVICE } from '@hisaberp/shared-auth';
import { LocaleSwitcherComponent } from '@hisaberp/shared-ui';

@Component({
  selector: 'pos-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, TranslateModule, LocaleSwitcherComponent],
  template: `
    <div class="min-h-screen flex flex-col">
      <header class="bg-primary-700 text-white px-4 h-14 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <span class="font-bold text-lg">POS</span>
          <span class="text-xs opacity-80">{{ user()?.email }}</span>
        </div>
        <div class="flex items-center gap-2">
          <me-locale-switcher />
          <button class="btn-touch" (click)="logout()" [attr.aria-label]="'common.logout' | translate">
            <i class="pi pi-sign-out"></i>
          </button>
        </div>
      </header>
      <main class="flex-1 bg-gray-100 p-4"><router-outlet /></main>
      <nav class="bg-white border-t grid grid-cols-4">
        <a routerLink="/sale" routerLinkActive="text-primary-700 bg-primary-50"
           class="flex flex-col items-center py-3 min-h-touch text-gray-700">
          <i class="pi pi-shopping-cart text-xl"></i>
          <span class="text-xs mt-1">{{ 'pos.tab.sale' | translate }}</span>
        </a>
        <a routerLink="/history" routerLinkActive="text-primary-700 bg-primary-50"
           class="flex flex-col items-center py-3 min-h-touch text-gray-700">
          <i class="pi pi-history text-xl"></i>
          <span class="text-xs mt-1">{{ 'pos.tab.history' | translate }}</span>
        </a>
        <a routerLink="/session-history" routerLinkActive="text-primary-700 bg-primary-50"
           class="flex flex-col items-center py-3 min-h-touch text-gray-700">
          <i class="pi pi-folder-open text-xl"></i>
          <span class="text-xs mt-1">{{ 'pos.tab.sessionHistory' | translate }}</span>
        </a>
        <a routerLink="/session" routerLinkActive="text-primary-700 bg-primary-50"
           class="flex flex-col items-center py-3 min-h-touch text-gray-700">
          <i class="pi pi-box text-xl"></i>
          <span class="text-xs mt-1">{{ 'pos.tab.session' | translate }}</span>
        </a>
      </nav>
    </div>
  `,
})
export class PosShellComponent implements OnInit {
  private readonly auth = inject(AUTH_SERVICE);
  private readonly router = inject(Router);
  private readonly swUpdate = inject(SwUpdate);

  protected user() { return this.auth.getCurrentUser(); }

  ngOnInit(): void {
    if (this.swUpdate.isEnabled) {
      this.swUpdate.versionUpdates
        .pipe(filter((e) => e.type === 'VERSION_READY'))
        .subscribe(() => {
          // New bundle available — reload so the user picks up new routes/features.
          document.location.reload();
        });
      // Poll for updates every 60 seconds so users on a long-open POS pick up the new version.
      void this.swUpdate.checkForUpdate();
      setInterval(() => void this.swUpdate.checkForUpdate(), 60_000);
    }
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
