import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MoneyFormatService } from './money-format.service';

describe('MoneyFormatService', () => {
  let service: MoneyFormatService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MoneyFormatService);
  });

  it('is created with the default of 0 decimal places', () => {
    expect(service).toBeTruthy();
    expect(service.decimalPlaces()).toBe(0);
  });

  it('formats finite numbers to a non-empty string and non-finite input to empty', () => {
    expect(service.format(1234)).not.toBe('');
    expect(typeof service.format(1234)).toBe('string');
    // null coerces to 0 (a finite number), so it is formatted, not blanked.
    expect(service.format(null)).toBe('0');
    expect(service.format(undefined)).toBe('');
    expect(service.format('not-a-number')).toBe('');
  });
});
