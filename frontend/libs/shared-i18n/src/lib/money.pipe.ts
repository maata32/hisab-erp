import { Pipe, PipeTransform, inject } from '@angular/core';
import { MoneyFormatService } from './money-format.service';

@Pipe({ name: 'money', standalone: true, pure: false })
export class MoneyPipe implements PipeTransform {
  private readonly fmt = inject(MoneyFormatService);

  transform(value: number | string | null | undefined): string {
    return this.fmt.format(value);
  }
}
