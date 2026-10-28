import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { PublicIntroPickup } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';

/** Upcoming intro pickups of a region (public data: date, kind of business, general area, free places). */
@Component({
  selector: 'fs-intro-pickup-dates',
  imports: [DateTimePipe, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (pickups().length === 0) {
      <p class="empty">{{ 'regions.intro.none' | t }}</p>
    } @else {
      <ul class="dates">
        @for (pickup of pickups(); track pickup.id) {
          <li class="date" [attr.data-testid]="testId() + '.date'">
            <span class="date__day">
              <strong>{{ pickup.startAt | fsDate: 'weekday' }}</strong>
              <span>{{ pickup.startAt | fsDate: 'time' }}</span>
            </span>
            <span class="date__what">
              <span class="date__category">{{ 'common.introCategory.' + pickup.category | t }}</span>
              @if (pickup.publicGeneralLocation) {
                <span class="date__where">
                  <i class="pi pi-map-marker" aria-hidden="true"></i>{{ pickup.publicGeneralLocation }}
                </span>
              }
              @if (pickup.visitFairteiler) {
                <span class="date__where">
                  <i class="pi pi-box" aria-hidden="true"></i>{{ 'regions.intro.fairteiler' | t }}
                </span>
              }
            </span>
            <span class="date__free" [class.date__free--full]="pickup.freeSlots === 0">
              {{ 'regions.intro.free' | t: { count: pickup.freeSlots } }}
            </span>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .empty {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .dates {
      display: grid;
      gap: 0.5rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .date {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.85rem;
      padding: 0.65rem 0.8rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
    }
    .date__day {
      display: grid;
      min-width: 6.5rem;
      font-size: 0.85rem;
    }
    .date__day span {
      color: var(--fs-text-muted);
      font-variant-numeric: tabular-nums;
    }
    .date__what {
      display: grid;
      gap: 0.1rem;
      min-width: 0;
    }
    .date__category {
      font-weight: 650;
    }
    .date__where {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .date__free {
      padding: 0.15rem 0.6rem;
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 0.8rem;
      font-weight: 700;
      white-space: nowrap;
    }
    .date__free--full {
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
    }
  `,
})
export class IntroPickupDatesComponent {
  readonly pickups = input<readonly PublicIntroPickup[]>([]);
  readonly testId = input('regions.intro');
}
