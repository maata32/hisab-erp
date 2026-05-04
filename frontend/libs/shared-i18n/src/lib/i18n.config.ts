import { HttpClient } from '@angular/common/http';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { importProvidersFrom } from '@angular/core';

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
];
