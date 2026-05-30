import { HttpClient } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { LOCALE_ID, importProvidersFrom } from '@angular/core';
import localeFr from '@angular/common/locales/fr';
import localeAr from '@angular/common/locales/ar';

registerLocaleData(localeFr);
registerLocaleData(localeAr);

export function httpTranslateLoader(http: HttpClient): TranslateLoader {
  return new TranslateHttpLoader(http, '/assets/i18n/', '.json');
}

export const provideAppI18n = () => [
  importProvidersFrom(
    TranslateModule.forRoot({
      defaultLanguage: 'fr',
      loader: {
        provide: TranslateLoader,
        useFactory: httpTranslateLoader,
        deps: [HttpClient],
      },
    }),
  ),
  { provide: LOCALE_ID, useValue: 'fr' },
];
