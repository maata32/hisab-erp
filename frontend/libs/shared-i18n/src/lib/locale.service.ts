import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DOCUMENT } from '@angular/common';
import { SUPPORTED_LOCALES, SupportedLocale } from './i18n.types';

@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly translate = inject(TranslateService);
  private readonly doc = inject(DOCUMENT);

  initialize(preferred?: string | null): void {
    this.translate.addLangs(SUPPORTED_LOCALES.map((l) => l.code));
    const target = this.normalize(preferred ?? this.detect());
    this.use(target);
  }

  use(code: SupportedLocale): void {
    this.translate.use(code);
    this.applyDirection(code);
  }

  current(): SupportedLocale {
    return (this.translate.currentLang as SupportedLocale) ?? 'fr';
  }

  isRtl(code?: SupportedLocale): boolean {
    const c = code ?? this.current();
    return SUPPORTED_LOCALES.find((l) => l.code === c)?.rtl ?? false;
  }

  private normalize(code: string): SupportedLocale {
    const short = code.split('-')[0].toLowerCase();
    return (SUPPORTED_LOCALES.find((l) => l.code === short)?.code ?? 'fr') as SupportedLocale;
  }

  private detect(): string {
    return navigator.language ?? 'fr';
  }

  private applyDirection(code: SupportedLocale): void {
    const dir = this.isRtl(code) ? 'rtl' : 'ltr';
    this.doc.documentElement.setAttribute('dir', dir);
    this.doc.documentElement.setAttribute('lang', code);
    this.doc.body.classList.toggle('rtl', dir === 'rtl');
  }
}
