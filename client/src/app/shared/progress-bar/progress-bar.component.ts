import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Determinate progress bar (0–100). Replaces Optimus `p-progressbar`, whose host carries `aria-level="<value>%"`
 * (not allowed on role progressbar and not a number: axe aria-allowed-attr / aria-valid-attr-value, F6 sweep).
 *
 * `<fs-progress-bar [value]="42" [label]="'x.progress' | t" />`
 */
@Component({
  selector: 'fs-progress-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-progress-bar',
    role: 'progressbar',
    'aria-valuemin': '0',
    'aria-valuemax': '100',
    '[attr.aria-valuenow]': 'percent()',
    '[attr.aria-label]': 'label()',
  },
  template: `<span class="fs-progress-bar__value" [style.width.%]="percent()"></span>`,
  styles: `
    :host {
      display: block;
      height: var(--p-progressbar-height, 1.25rem);
      overflow: hidden;
      border-radius: 999px;
      background: var(--fs-surface-muted);
    }
    .fs-progress-bar__value {
      display: block;
      height: 100%;
      border-radius: inherit;
      background: var(--fs-primary);
      transition: width 0.3s ease;
    }
  `,
})
export class ProgressBarComponent {
  readonly value = input(0);
  readonly label = input<string | undefined>(undefined);

  protected readonly percent = computed(() => Math.round(Math.min(100, Math.max(0, this.value() || 0))));
}
