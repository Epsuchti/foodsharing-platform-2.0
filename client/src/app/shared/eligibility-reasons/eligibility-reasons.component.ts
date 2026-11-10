import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { PickupEligibility, PickupEligibilityReason, RegionPickupRuleState } from '../../api';
import { TPipe } from '../../core/i18n/t.pipe';

/**
 * Why a pickup sign-up is not possible (PU4/PU5): translated `pickups.eligibility.<REASON>` sentences and, when the
 * region pickup rule applies, one line per limit (review fix A3): the counters are the user's other sign-ups dated
 * within ±timespanDays of the pickup's date, and on the same date. A limit ≤ 0 (no limit) gets no line, and nothing is
 * said about the rule when the user is already signed up.
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
      @if (ruleShown(e); as rule) {
        @if ((rule.limitNumber ?? 0) > 0) {
          <p class="rule" data-testid="shared.eligibility.ruleTimespan">
            <i class="pi pi-info-circle" aria-hidden="true"></i>
            {{ 'shared.eligibility.ruleTimespan' | t: { limit: rule.limitNumber ?? 0, days: rule.timespanDays ?? 0, count: rule.pickupsInTimespan ?? 0 } }}
          </p>
        }
        @if ((rule.limitDayNumber ?? 0) > 0) {
          <p class="rule" data-testid="shared.eligibility.ruleDay">
            <i class="pi pi-info-circle" aria-hidden="true"></i>
            {{ 'shared.eligibility.ruleDay' | t: { dayLimit: rule.limitDayNumber ?? 0, sameDay: rule.pickupsSameDay ?? 0 } }}
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

  /** The active region rule, unless the user is already signed up for this pickup. */
  protected ruleShown(e: PickupEligibility): RegionPickupRuleState | null {
    const rule = e.regionRule;
    if (!rule?.active || e.reasons.includes(PickupEligibilityReason.AlreadySignedUp)) {
      return null;
    }
    return rule;
  }
}
