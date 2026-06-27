import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

/**
 * Localizes a seeded master-data label (UoM unit, UoM category, price tier…) by its
 * stable code. Looks up `<prefix>.<code>` in the active language; when no translation
 * exists (custom records the tenant created, or unknown codes) it falls back to the
 * stored name.
 *
 * Impure so it re-evaluates on language change, mirroring {@link MoneyPipe}.
 *
 * Usage: {{ unit.code | mdName:'uoms.unit':unit.name }}
 */
@Pipe({ name: 'mdName', standalone: true, pure: false })
export class MasterDataNamePipe implements PipeTransform {
  private readonly i18n = inject(TranslateService);

  transform(code: string | null | undefined, prefix: string, fallback?: string | null): string {
    const fb = (fallback ?? '').toString();
    if (!code) return fb;
    const key = `${prefix}.${code}`;
    const val = this.i18n.instant(key);
    return val == null || val === key ? fb : val;
  }
}
