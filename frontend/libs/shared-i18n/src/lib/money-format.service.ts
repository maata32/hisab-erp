import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class MoneyFormatService {
  private readonly http = inject(HttpClient);

  readonly decimalPlaces = signal(0);

  async load(): Promise<void> {
    try {
      const settings = await firstValueFrom(
        this.http.get<{ posSettings?: Record<string, unknown> }>('/api/v1/settings'),
      );
      const d = settings?.posSettings?.['currencyDecimalPlaces'];
      if (typeof d === 'number' && d >= 0) this.decimalPlaces.set(d);
    } catch {
      /* keep default 0 */
    }
  }

  format(value: number | string | null | undefined): string {
    const n = typeof value === 'number' ? value : Number(value);
    if (!isFinite(n)) return '';
    const d = this.decimalPlaces();
    return n.toLocaleString('fr-MR', {
      minimumFractionDigits: d,
      maximumFractionDigits: d,
    });
  }
}
