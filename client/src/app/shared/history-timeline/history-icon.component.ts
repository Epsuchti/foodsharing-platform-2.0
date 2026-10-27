import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { historyVisual } from './history-labels';

/** Round, tone-coloured icon of a history action (timeline marker and card icon). */
@Component({
  selector: 'fs-history-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-history-icon',
    'aria-hidden': 'true',
    '[attr.data-tone]': 'visual().tone',
  },
  template: `<i [class]="visual().icon"></i>`,
  styles: `
    :host {
      --tone: var(--fs-text-muted);
      --tone-bg: var(--fs-surface-muted);
      display: grid;
      width: 2.25rem;
      height: 2.25rem;
      flex: 0 0 auto;
      place-items: center;
      border: 2px solid var(--fs-surface);
      border-radius: 50%;
      background: var(--tone-bg);
      box-shadow: 0 0 0 1px var(--fs-border);
      color: var(--tone);
      font-size: 0.95rem;
    }
    :host([data-tone='primary']) {
      --tone: var(--fs-primary-strong);
      --tone-bg: var(--fs-primary-soft);
    }
    :host([data-tone='info']) {
      --tone: color-mix(in srgb, #1c6f91 70%, var(--fs-text));
      --tone-bg: color-mix(in srgb, #1c6f91 14%, var(--fs-surface));
    }
    :host([data-tone='warning']) {
      --tone: var(--fs-warning-text);
      --tone-bg: var(--fs-warning-soft);
    }
    :host([data-tone='danger']) {
      --tone: var(--fs-danger);
      --tone-bg: var(--fs-danger-soft);
    }
  `,
})
export class HistoryIconComponent {
  readonly action = input.required<string>();
  protected readonly visual = computed(() => historyVisual(this.action()));
}
