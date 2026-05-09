import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { PosApiService } from './pos-api.service';

@Injectable({ providedIn: 'root' })
export class PosSettingsService {
  private readonly api = inject(PosApiService);

  readonly decimalPlaces = signal(0); // MRU default: no sub-unit

  async load(): Promise<void> {
    try {
      const settings = await firstValueFrom(this.api.getSettings());
      const pos = settings['posSettings'] as Record<string, unknown> | null;
      const d = pos?.['currencyDecimalPlaces'];
      if (typeof d === 'number') this.decimalPlaces.set(d);
    } catch {
      // keep default
    }
  }

  format(value: number): string {
    const d = this.decimalPlaces();
    return value.toLocaleString('fr-MR', {
      minimumFractionDigits: d,
      maximumFractionDigits: d,
    });
  }
}
