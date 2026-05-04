import { Component, input, output } from '@angular/core';

@Component({
  selector: 'me-empty-state',
  standalone: true,
  template: `
    <div class="flex flex-col items-center justify-center text-center py-16 px-4">
      @if (icon(); as i) {
        <i class="text-5xl text-gray-300" [class]="i"></i>
      }
      <h3 class="mt-4 text-lg font-semibold text-gray-700">{{ title() }}</h3>
      @if (description(); as d) {
        <p class="mt-1 text-sm text-gray-500 max-w-md">{{ d }}</p>
      }
      @if (actionLabel(); as a) {
        <button
          type="button"
          class="mt-6 px-4 py-2 min-h-touch rounded-md bg-primary-600 text-white hover:bg-primary-700"
          (click)="actionClick.emit()"
        >
          {{ a }}
        </button>
      }
    </div>
  `,
})
export class EmptyStateComponent {
  readonly icon = input<string>('pi pi-inbox');
  readonly title = input.required<string>();
  readonly description = input<string | null>(null);
  readonly actionLabel = input<string | null>(null);
  readonly actionClick = output<void>();
}
