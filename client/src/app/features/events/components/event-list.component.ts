import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { EventSummary } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { intlLocale } from '../../../core/i18n/languages';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EVENT_TYPE_ICON, groupByMonth } from '../events.resource';

/** Events grouped by month (E1): type icon, date range, location or "online", RSVP counts and my answer. */
@Component({
  selector: 'fs-event-list',
  imports: [DateTimePipe, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (group of groups(); track group.key) {
      <section class="month" [attr.aria-labelledby]="'month-' + group.key">
        <h2 class="month__title" [id]="'month-' + group.key">{{ monthLabel(group.month) }}</h2>
        <ul class="items fs-card">
          @for (e of group.events; track e.id) {
            <li>
              <a class="item" [routerLink]="['/events', e.id]" data-testid="events.list.item" [attr.data-event-id]="e.id" [attr.data-name]="e.name">
                <span class="item__icon" aria-hidden="true"><i [class]="icon[e.type]"></i></span>
                <span class="item__text">
                  <span class="item__name">{{ e.name }}</span>
                  <span class="item__meta">
                    <time [attr.datetime]="e.startsAt">{{ e.startsAt | fsDate: 'dateTime' }}</time>
                    · {{ e.type === 'ONLINE' ? ('events.online' | t) : (e.location?.name ?? ('events.type.' + e.type | t)) }}
                  </span>
                </span>
                <span class="item__counts">
                  <span>{{ 'events.counts' | t: { accepted: e.acceptedCount, maybe: e.maybeCount } }}</span>
                  @if (e.myStatus) {
                    <span class="status status--{{ e.myStatus }}" data-testid="events.list.myStatus">{{ 'events.status.' + e.myStatus | t }}</span>
                  }
                </span>
              </a>
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
    .month { display: grid; gap: 0.4rem; }
    .month__title { margin: 0; font-size: 1rem; color: var(--fs-text-muted); text-transform: capitalize; }
    .items { list-style: none; margin: 0; padding: 0; }
    .items li + li { border-top: 1px solid var(--fs-border); }
    .item { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem 1rem; color: inherit; text-decoration: none; }
    .item:hover, .item:focus-visible { background: var(--fs-surface-muted); }
    .item__icon { display: grid; place-items: center; width: 2.5rem; height: 2.5rem; border-radius: var(--fs-radius-md); background: var(--fs-primary-soft); color: var(--fs-primary-strong); flex: none; }
    .item__text { display: grid; gap: 0.15rem; flex: 1; min-width: 0; }
    .item__name { font-weight: 600; }
    .item__meta, .item__counts { color: var(--fs-text-muted); font-size: 0.875rem; }
    .item__counts { display: grid; justify-items: end; gap: 0.2rem; text-align: right; }
    .status { padding: 0.05rem 0.5rem; border-radius: 999px; border: 1px solid var(--fs-border); color: var(--fs-text); font-weight: 600; }
    .status--ACCEPTED { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .status--INVITED { border-color: var(--fs-warning-border); background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    @media (max-width: 30rem) { .item__counts { display: none; } }
  `,
})
export class EventListComponent {
  private readonly i18n = inject(I18nService);
  readonly events = input.required<readonly EventSummary[]>();
  protected readonly icon = EVENT_TYPE_ICON;
  protected readonly groups = computed(() => groupByMonth(this.events()));

  protected monthLabel(iso: string): string {
    return new Intl.DateTimeFormat(intlLocale(this.i18n.language()), { month: 'long', year: 'numeric', timeZone: 'Europe/Zurich' }).format(new Date(iso));
  }
}
