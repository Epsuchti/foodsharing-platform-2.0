import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

/**
 * Friendly empty state with explanation and the next action.
 * `<fs-empty-state icon="pi pi-calendar" [title]="'x.empty.title' | t" [text]="…" [actionLabel]="…" (action)="…" />`
 */
@Component({
  selector: 'fs-empty-state',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-empty-state', '[class.fs-empty-state--compact]': 'compact()' },
  template: `
    <div class="empty__icon" aria-hidden="true"><i [class]="icon()"></i></div>
    <div class="empty__body">
      @if (title()) {
        <h3 class="empty__title">{{ title() }}</h3>
      }
      @if (text()) {
        <p class="empty__text">{{ text() }}</p>
      }
      <ng-content />
    </div>
    @if (actionLabel()) {
      <p-button
        [label]="actionLabel()!"
        [icon]="actionIcon()"
        [size]="compact() ? 'small' : undefined"
        (onClick)="action.emit()"
        [attr.data-testid]="testId() ? testId() + '.action' : null"
      />
    }
  `,
  styles: `
    :host {
      display: grid;
      justify-items: center;
      gap: 1rem;
      padding: 2.5rem 1.5rem;
      border: 1px dashed var(--fs-border-strong);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface-muted);
      text-align: center;
    }
    :host(.fs-empty-state--compact) {
      grid-template-columns: auto 1fr auto;
      align-items: center;
      justify-items: start;
      gap: 0.75rem;
      padding: 0.85rem 1rem;
      text-align: left;
    }
    .empty__icon {
      display: grid;
      width: 4rem;
      height: 4rem;
      place-items: center;
      border-radius: 50%;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.6rem;
    }
    :host(.fs-empty-state--compact) .empty__icon {
      width: 2.25rem;
      height: 2.25rem;
      font-size: 1rem;
    }
    .empty__body {
      display: grid;
      max-width: 32rem;
      gap: 0.35rem;
    }
    .empty__title {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 700;
    }
    :host(.fs-empty-state--compact) .empty__title {
      font-size: 0.95rem;
    }
    .empty__text {
      margin: 0;
      color: var(--fs-text-muted);
    }
  `,
})
export class EmptyStateComponent {
  readonly icon = input('pi pi-inbox');
  readonly title = input<string | undefined>(undefined);
  readonly text = input<string | undefined>(undefined);
  readonly actionLabel = input<string | undefined>(undefined);
  readonly actionIcon = input<string | undefined>(undefined);
  readonly compact = input(false);
  readonly testId = input<string | undefined>(undefined);
  readonly action = output<void>();
}
