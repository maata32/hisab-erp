import { Component, input } from '@angular/core';

@Component({
  selector: 'me-loading-spinner',
  standalone: true,
  template: `
    <div
      class="flex flex-col items-center justify-center gap-2 p-6"
      role="status"
      aria-live="polite"
    >
      <div
        class="animate-spin rounded-full border-4 border-primary-200 border-t-primary-600"
        [style.width.px]="size()"
        [style.height.px]="size()"
      ></div>
      @if (label(); as text) {
        <span class="text-sm text-gray-600">{{ text }}</span>
      }
    </div>
  `,
})
export class LoadingSpinnerComponent {
  readonly size = input<number>(40);
  readonly label = input<string | null>(null);
}
