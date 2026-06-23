import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AppComponent } from './app.component';
import { appConfig } from './app.config';

describe('pos-terminal AppComponent', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [AppComponent],
      // Reuse the real application providers so the smoke test exercises the
      // app's actual DI graph; the testing HTTP backend prevents real requests.
      providers: [...appConfig.providers, provideHttpClientTesting()],
    }).compileComponents(),
  );

  it('creates the root component', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
