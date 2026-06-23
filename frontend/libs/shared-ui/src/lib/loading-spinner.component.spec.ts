import { TestBed } from '@angular/core/testing';
import { LoadingSpinnerComponent } from './loading-spinner.component';

describe('LoadingSpinnerComponent', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [LoadingSpinnerComponent],
    }).compileComponents(),
  );

  it('creates with the default size and no label', () => {
    const fixture = TestBed.createComponent(LoadingSpinnerComponent);
    const component = fixture.componentInstance;
    expect(component).toBeTruthy();
    expect(component.size()).toBe(40);
    expect(component.label()).toBeNull();
  });
});
