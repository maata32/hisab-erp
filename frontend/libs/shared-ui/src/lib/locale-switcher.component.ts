import { Component, inject } from '@angular/core';
import { LocaleService, SUPPORTED_LOCALES, SupportedLocale } from '@hisaberp/shared-i18n';

@Component({
  selector: 'me-locale-switcher',
  standalone: true,
  template: `
    <div class="relative inline-block">
      <select
        class="rounded-md border border-gray-300 px-3 py-2 min-h-touch text-sm bg-white"
        [value]="current()"
        (change)="onChange($any($event.target).value)"
        aria-label="Language"
      >
        @for (l of locales; track l.code) {
          <option [value]="l.code">{{ l.flag }} {{ l.label }}</option>
        }
      </select>
    </div>
  `,
})
export class LocaleSwitcherComponent {
  protected readonly locales = SUPPORTED_LOCALES;
  private readonly localeService = inject(LocaleService);

  current(): SupportedLocale {
    return this.localeService.current();
  }

  onChange(code: string): void {
    this.localeService.use(code as SupportedLocale);
  }
}
