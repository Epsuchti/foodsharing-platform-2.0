import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { DateTimePipe } from '../../core/dates/date-time.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { RegionContextService } from '../../core/region/region-context.service';

/** Weekday, day and month of an instant in a time zone and locale ("Mi", "4", "Nov."), and whether it is today. */
export interface DateTileParts {
  weekday: string;
  day: string;
  month: string;
  today: boolean;
}

/** Calendar day (YYYY-MM-DD) of an instant in a time zone. */
function dayKey(date: Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}

export function dateTileParts(at: string | Date, timeZone: string, locale: string, now: Date = new Date()): DateTileParts | null {
  const date = at instanceof Date ? at : new Date(at);
  if (Number.isNaN(date.getTime())) return null;
  const part = (options: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(locale, { timeZone, ...options }).format(date);
  return {
    weekday: part({ weekday: 'short' }).replace(/\.$/, ''),
    day: part({ day: 'numeric' }).replace(/\.$/, ''),
    month: part({ month: 'short' }),
    today: dayKey(date, timeZone) === dayKey(now, timeZone),
  };
}

/**
 * Calendar tile of a date row (docs/design/ui-polish-batch-proposal.md §3, home cards): weekday, day and month,
 * filled for today. Decorative (`aria-hidden`): the row's `<time datetime>` carries the full date for screen readers.
 *
 * ```html
 * <fs-date-tile [at]="pickup.pickupAt" />
 * ```
 */
@Component({
  selector: 'fs-date-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', '[class.tile--today]': 'parts()?.today', '[attr.data-today]': 'parts()?.today' },
  template: `
    @if (parts(); as p) {
      <span class="tile__wd">{{ p.weekday }}</span>
      <span class="tile__d">{{ p.day }}</span>
      <span class="tile__m">{{ p.month }}</span>
    }
  `,
  styles: `
    :host {
      display: grid; justify-items: center; align-content: center; width: var(--fs-date-tile); min-height: 3.4rem;
      padding: 0.3rem 0.2rem; border-radius: var(--fs-radius-md); background: var(--fs-primary-soft);
      color: var(--fs-primary-strong); line-height: 1.05;
    }
    .tile__wd { font-size: 0.66rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; }
    .tile__d { color: var(--fs-text); font-size: 1.35rem; font-weight: 800; letter-spacing: -0.03em; font-variant-numeric: tabular-nums; }
    .tile__m { font-size: 0.66rem; font-weight: 650; }
    :host(.tile--today) { background: var(--fs-primary); color: var(--fs-on-primary); }
    :host(.tile--today) .tile__d { color: var(--fs-on-primary); }
  `,
})
export class DateTileComponent {
  private readonly i18n = inject(I18nService);
  private readonly region = inject(RegionContextService);

  readonly at = input.required<string>();
  /** Defaults to the active region's zone (A15). */
  readonly timeZone = input<string | undefined>(undefined);

  protected readonly parts = computed(() =>
    dateTileParts(this.at(), this.timeZone() ?? this.region.timeZone(), this.i18n.locale()),
  );
}

/**
 * The time of a date-tile row: `<time datetime>` with the full date as a visually hidden prefix ("Today, " or
 * "04.11.2026, "), since the tile beside it is decorative.
 */
@Component({
  selector: 'fs-tile-time',
  imports: [DateTimePipe, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<time [attr.datetime]="at()"><span class="fs-visually-hidden">{{ today() ? ('shared.dateTile.today' | t) : (at() | fsDate: 'date' : timeZone()) }}, </span>{{ at() | fsDate: 'time' : timeZone() }}</time>`,
  styles: `:host { font-variant-numeric: tabular-nums; }`,
})
export class TileTimeComponent {
  private readonly i18n = inject(I18nService);
  private readonly region = inject(RegionContextService);

  readonly at = input.required<string>();
  readonly timeZone = input<string | undefined>(undefined);

  protected readonly today = computed(
    () => dateTileParts(this.at(), this.timeZone() ?? this.region.timeZone(), this.i18n.locale())?.today ?? false,
  );
}

/** `fs-date-tile` and `fs-tile-time` for a date row. */
export const DATE_TILE = [DateTileComponent, TileTimeComponent] as const;
