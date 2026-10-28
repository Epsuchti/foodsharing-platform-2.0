import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';

import { I18nService } from '../../../core/i18n/i18n.service';

/**
 * Animated impact counter of the explainer (§11.2): counts up from 0 to `value` once (ease-out, ~1.2 s); shows the
 * final value at once when the user prefers reduced motion. `data-value` always carries the real number.
 */
@Component({
  selector: 'fs-count-up',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-testid]': 'testId()', '[attr.data-value]': 'value()' },
  template: `{{ text() }}`,
})
export class CountUpComponent {
  private readonly i18n = inject(I18nService);
  private readonly view = inject(DOCUMENT).defaultView;

  readonly value = input.required<number>();
  readonly duration = input(1200);
  readonly testId = input<string | undefined>(undefined);

  private readonly shown = signal(0);
  protected readonly text = computed(() =>
    new Intl.NumberFormat(this.i18n.language() === 'gws' ? 'de-CH' : this.i18n.language(), {
      maximumFractionDigits: 0,
    }).format(this.shown()),
  );

  constructor() {
    let frame = 0;
    inject(DestroyRef).onDestroy(() => this.view?.cancelAnimationFrame?.(frame));
    effect(() => {
      const target = this.value();
      const view = this.view;
      const reduced = view?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true;
      if (reduced || !view?.requestAnimationFrame || target <= 0) {
        this.shown.set(target);
        return;
      }
      const start = view.performance.now();
      const total = this.duration();
      const step = (now: number) => {
        const t = Math.min((now - start) / total, 1);
        this.shown.set(target * (1 - Math.pow(1 - t, 3)));
        if (t < 1) {
          frame = view.requestAnimationFrame(step);
        }
      };
      frame = view.requestAnimationFrame(step);
    });
  }
}
