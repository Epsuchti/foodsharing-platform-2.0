import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { GapDayResult, fromIsoDate, gapPreview, joinAll } from '../automation.util';

const ICONS: Record<GapDayResult, string> = { PICKUP: 'pi-shopping-bag', BLOCKED: 'pi-times', OK: 'pi-check' };
const WORDS: Record<GapDayResult, string> = {
  PICKUP: 'automation.policy.gapPreview.pickup',
  BLOCKED: 'automation.policy.gapPreview.blocked',
  OK: 'automation.policy.gapPreview.ok',
};

/**
 * Live preview of the gap rule (decision 151): 11 days around an example pickup with the verdict for a second
 * sign-up on each day, the next possible pickup and the decline message (single-store or group text).
 */
@Component({
  selector: 'fs-gap-preview',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="gap-preview" data-testid="automation.policy.gapPreview" aria-labelledby="gap-preview-title">
      <h4 class="gap-preview__title" id="gap-preview-title"><i class="pi pi-eye" aria-hidden="true"></i>{{ 'automation.policy.gapPreview.title' | t }}</h4>
      <p class="muted">{{ 'automation.policy.gapPreview.intro' | t: { date: long(pickup()) } }}</p>
      <ol class="days" [attr.aria-label]="'automation.policy.gapPreview.label' | t">
        @for (day of preview().days; track day.date) {
          <li
            class="day"
            data-testid="automation.policy.gapPreview.day"
            [attr.data-date]="day.date"
            [attr.data-result]="day.result"
            [attr.aria-current]="day.result === 'PICKUP' ? 'date' : null"
            [attr.aria-label]="long(day.date) + ': ' + (words[day.result] | t)"
          >
            <span class="day__date" aria-hidden="true"><span class="day__wd">{{ weekday(day.date) }}</span>{{ short(day.date) }}</span>
            <i class="pi" [class]="icons[day.result]" aria-hidden="true"></i>
            <span class="day__word" aria-hidden="true">
              <span class="day__word--long">{{ words[day.result] | t }}</span>
              <span class="day__word--short">{{ (day.result === 'PICKUP' ? 'automation.policy.gapPreview.pickupShort' : words[day.result]) | t }}</span>
            </span>
          </li>
        }
      </ol>
      <p class="legend" aria-hidden="true">
        <span><i class="pi pi-shopping-bag"></i>{{ 'automation.policy.gapPreview.pickup' | t }}</span>
        <span><i class="pi pi-times"></i>{{ 'automation.policy.gapPreview.legendBlocked' | t }}</span>
        <span><i class="pi pi-check"></i>{{ 'automation.policy.gapPreview.legendOk' | t }}</span>
      </p>
      <p class="next" data-testid="automation.policy.gapPreview.next">
        <i class="pi pi-calendar" aria-hidden="true"></i>{{ 'automation.policy.gapPreview.next' | t: { date: long(preview().next) } }}
      </p>
      <div class="preview" data-testid="automation.policy.gapPreview.message">
        <span class="preview__t">{{ 'automation.policy.gapPreview.messageTitle' | t }}</span>{{ message() }}
      </div>
    </section>
  `,
  styles: `
    :host { display: block; }
    .gap-preview { display: grid; gap: 0.6rem; padding: 0.75rem; border-radius: var(--fs-radius-md); background: var(--fs-surface-muted); }
    .gap-preview__title { display: flex; align-items: center; gap: 0.45rem; margin: 0; font-size: 1rem; font-weight: 650; }
    .gap-preview__title .pi { color: var(--fs-primary-strong); }
    .days { display: grid; grid-template-columns: repeat(11, minmax(0, 1fr)); gap: 3px; margin: 0; padding: 0; list-style: none; }
    .day {
      display: grid; align-content: center; justify-items: center; gap: 0.1rem; min-width: 0; min-height: 3.4rem; padding: 0.3rem 0.1rem;
      border: 1px solid var(--fs-border); border-radius: var(--fs-radius-sm); background: var(--fs-surface); text-align: center; line-height: 1.15;
    }
    .day__date { font-size: 0.72rem; color: var(--fs-text-muted); font-variant-numeric: tabular-nums; }
    .day__wd { display: block; }
    .day__word { font-size: 0.78rem; font-weight: 700; overflow-wrap: anywhere; }
    .day__word--short { display: none; }
    .day[data-result='PICKUP'] { background: var(--fs-primary); border-color: var(--fs-primary); color: var(--fs-on-primary); }
    .day[data-result='PICKUP'] .day__date { color: inherit; opacity: 0.85; }
    .day[data-result='BLOCKED'] { background: var(--fs-danger-soft); border-color: var(--fs-danger-border); color: var(--fs-danger); }
    .day[data-result='OK'] { background: var(--fs-primary-soft); border-color: var(--fs-primary-200); color: var(--fs-primary-strong); }
    .legend { display: flex; flex-wrap: wrap; gap: 0.3rem 1rem; margin: 0; font-size: 0.8rem; color: var(--fs-text-muted); }
    .legend span { display: inline-flex; align-items: center; gap: 0.3rem; }
    .next { display: flex; align-items: center; gap: 0.45rem; margin: 0; font-weight: 600; }
    .next .pi { color: var(--fs-text-muted); }
    .preview {
      padding: 0.6rem 0.8rem; border-left: 3px solid var(--fs-primary); border-radius: var(--fs-radius-sm); background: var(--fs-surface);
      white-space: pre-wrap; overflow-wrap: anywhere; font-size: 0.92rem;
    }
    .preview__t { display: block; margin-bottom: 0.2rem; font-size: 0.78rem; font-weight: 700; color: var(--fs-text-muted); }
    @media (max-width: 600px) {
      .day { min-height: 3.2rem; padding: 0.25rem 0; }
      .day__wd { display: none; }
      .day__date { font-size: 0.64rem; }
      .day__word { font-size: 0.62rem; }
      .day__word--long { display: none; }
      .day__word--short { display: inline; }
    }
  `,
})
export class GapPreviewComponent {
  private readonly i18n = inject(I18nService);

  /** Example pickup `yyyy-MM-dd`. */
  readonly pickup = input.required<string>();
  readonly gap = input.required<number>();
  /** This store first, then the other listed stores. */
  readonly storeNames = input.required<string[]>();

  protected readonly icons = ICONS;
  protected readonly words = WORDS;
  protected readonly preview = computed(() => gapPreview(this.pickup(), Math.max(0, this.gap() ?? 0)));

  protected readonly message = computed(() => {
    const gap = Math.max(0, this.gap() ?? 0);
    if (gap === 0) return this.i18n.t('automation.policy.gapPreview.none');
    const days = (count: number) => this.i18n.t('automation.policy.gapDayCount', { count });
    const names = this.storeNames();
    const actual = days(this.preview().actual);
    if (names.length > 1) {
      return this.i18n.t('automation.policy.gapPreview.message', {
        date: this.long(this.pickup()),
        store: names[0],
        stores: joinAll(names, this.i18n.t('automation.policy.gapAnd')),
        gap: days(gap),
        actual,
      });
    }
    return this.i18n.t('automation.policy.gapPreview.messageSingle', { actual, gap: days(gap) });
  });

  protected long(day: string): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), { weekday: 'short', day: 'numeric', month: 'numeric', year: 'numeric' }).format(fromIsoDate(day));
  }

  protected weekday(day: string): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), { weekday: 'short' }).format(fromIsoDate(day));
  }

  protected short(day: string): string {
    const date = fromIsoDate(day);
    return `${date.getDate()}.${date.getMonth() + 1}.`;
  }
}
