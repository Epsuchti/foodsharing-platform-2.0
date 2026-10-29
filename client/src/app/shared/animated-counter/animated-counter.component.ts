import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  InjectionToken,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';

import { I18nService } from '../../core/i18n/i18n.service';

/** Ease-out cubic progress for t ∈ [0, 1]. */
export function easeOutCubic(t: number): number {
  const clamped = Math.min(Math.max(t, 0), 1);
  return 1 - Math.pow(1 - clamped, 3);
}

/** Frame scheduler of the counter (tests replace it; Angular's own scheduling also uses requestAnimationFrame). */
export interface AnimationFrames {
  request(callback: FrameRequestCallback): number;
  cancel(handle: number): void;
}

export const ANIMATION_FRAMES = new InjectionToken<AnimationFrames | null>('ANIMATION_FRAMES', {
  providedIn: 'root',
  factory: () => {
    const view = inject(DOCUMENT).defaultView;
    return view?.requestAnimationFrame
      ? { request: (callback) => view.requestAnimationFrame(callback), cancel: (handle) => view.cancelAnimationFrame(handle) }
      : null;
  },
});

/**
 * Number that counts up (M6 statistics, profile stats): from the previously shown value (0 at first) to `value`
 * with requestAnimationFrame (ease-out, `duration` ms). With `prefers-reduced-motion: reduce` — or without
 * requestAnimationFrame (prerendering) — the final value is shown at once. Formatted for the UI language;
 * `data-value` always carries the real number and screen readers get the final value only.
 *
 * `<fs-animated-counter [value]="stats.kgSaved" suffix=" kg" testId="statistics.kg" />`
 */
@Component({
  selector: 'fs-animated-counter',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-animated-counter',
    '[attr.data-testid]': 'testId()',
    '[attr.data-value]': 'value()',
  },
  template: `<span aria-hidden="true">{{ text() }}{{ suffix() }}</span><span class="fs-visually-hidden">{{ finalText() }}{{ suffix() }}</span>`,
  styles: `
    :host { font-variant-numeric: tabular-nums; }
  `,
})
export class AnimatedCounterComponent {
  private readonly i18n = inject(I18nService);
  private readonly view = inject(DOCUMENT).defaultView;

  readonly value = input.required<number>();
  readonly duration = input(1200);
  readonly fractionDigits = input(0);
  readonly suffix = input('');
  readonly testId = input<string | undefined>(undefined);

  /** The number currently displayed (public for tests). */
  readonly shown = signal(0);
  private readonly format = computed(
    () =>
      new Intl.NumberFormat(this.i18n.locale(), {
        minimumFractionDigits: this.fractionDigits(),
        maximumFractionDigits: this.fractionDigits(),
      }),
  );
  protected readonly text = computed(() => this.format().format(this.shown()));
  protected readonly finalText = computed(() => this.format().format(this.value()));

  constructor() {
    let frame = 0;
    const view = this.view;
    const frames = inject(ANIMATION_FRAMES);
    inject(DestroyRef).onDestroy(() => frames?.cancel(frame));
    effect(() => {
      const target = this.value();
      const total = this.duration();
      untracked(() => {
        frames?.cancel(frame);
        const reduced = view?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true;
        const from = this.shown();
        if (reduced || !frames || total <= 0 || from === target) {
          this.shown.set(target);
          return;
        }
        let start: number | null = null;
        const step = (now: number) => {
          start ??= now;
          const t = (now - start) / total;
          this.shown.set(from + (target - from) * easeOutCubic(t));
          if (t < 1) {
            frame = frames.request(step);
          } else {
            this.shown.set(target);
          }
        };
        frame = frames.request(step);
      });
    });
  }
}
