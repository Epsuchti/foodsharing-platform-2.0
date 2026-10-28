import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { PickupEligibility } from '../../api';
import { TPipe } from '../../core/i18n/t.pipe';

/**
 * Why a pickup sign-up is not possible (PU4/PU5): translated `pickups.eligibility.<REASON>` sentences and, when the
 * region pickup rule applies, its counters.
 */
@Component({
  selector: 'fs-eligibility-reasons',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (eligibility(); as e) {
      @if (e.reasons.length) {
        <ul class="reasons" data-testid="shared.eligibility.reasons">
          @for (reason of e.reasons; track reason) {
            <li [attr.data-reason]="reason" data-testid="shared.eligibility.reason">
              <i class="pi pi-ban" aria-hidden="true"></i>{{ 'pickups.eligibility.' + reason | t }}
            </li>
          }
        </ul>
      }
      @if (e.regionRule; as rule) {
        @if (rule.active) {
          <p class="rule" data-testid="shared.eligibility.rule">
            <i class="pi pi-info-circle" aria-hidden="true"></i>
            {{
              'shared.eligibility.rule'
                | t
                  : {
                      count: rule.pickupsInTimespan ?? 0,
                      limit: rule.limitNumber ?? 0,
                      days: rule.timespanDays ?? 0,
                      sameDay: rule.pickupsSameDay ?? 0,
                      dayLimit: rule.limitDayNumber ?? 0,
                    }
            }}
          </p>
        }
      }
    }
  `,
  styles: `
    .reasons { display: grid; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .reasons li { display: flex; gap: 0.5rem; align-items: baseline; color: var(--fs-danger); font-weight: 600; }
    .rule { display: flex; gap: 0.5rem; align-items: baseline; margin: 0.6rem 0 0; color: var(--fs-text-muted); font-size: 0.88rem; }
  `,
})
export class EligibilityReasonsComponent {
  readonly eligibility = input<PickupEligibility | null | undefined>(undefined);
}
