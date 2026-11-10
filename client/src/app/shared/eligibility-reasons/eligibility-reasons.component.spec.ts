import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PickupEligibility, PickupEligibilityReason, RegionPickupRuleState } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import de from '../i18n/de.json';
import { EligibilityReasonsComponent } from './eligibility-reasons.component';

const TRANSLATIONS = {
  'shared.eligibility.ruleTimespan': de['shared.eligibility.ruleTimespan'],
  'shared.eligibility.ruleDay': de['shared.eligibility.ruleDay'],
  'pickups.eligibility.REGION_RULE_TIMESPAN_LIMIT': 'Zu viele Abholungen',
  'pickups.eligibility.ALREADY_SIGNED_UP': 'Schon eingetragen',
};

/** Zürich seed rule: at most 2 within ±7 days, 1 per day. */
const RULE: RegionPickupRuleState = {
  active: true,
  timespanDays: 7,
  limitNumber: 2,
  limitDayNumber: 1,
  inactiveHours: 24,
  pickupsInTimespan: 2,
  pickupsSameDay: 0,
};

describe('EligibilityReasonsComponent (review fix A3: plain rule text)', () => {
  let fixture: ComponentFixture<EligibilityReasonsComponent>;
  const text = (testId: string) =>
    (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? null;

  async function render(eligibility: PickupEligibility): Promise<void> {
    TestBed.configureTestingModule({ providers: provideDataStateTesting({ translations: TRANSLATIONS }) });
    fixture = TestBed.createComponent(EligibilityReasonsComponent);
    fixture.componentRef.setInput('eligibility', eligibility);
    await fixture.whenStable();
  }

  it('says what the region rule counts, one line per limit', async () => {
    await render({ eligible: false, reasons: [PickupEligibilityReason.RegionRuleTimespanLimit], regionRule: RULE });
    expect(text('shared.eligibility.ruleTimespan')).toBe(
      'Bezirksregel: höchstens 2 Abholungen innerhalb von 7 Tagen vor oder nach diesem Termin. Du hast in dieser Zeit schon 2 andere.',
    );
    expect(text('shared.eligibility.ruleDay')).toBe('Höchstens 1 pro Tag. An diesem Tag hast du noch keine andere.');
  });

  it('uses the singular forms', async () => {
    await render({
      eligible: true,
      reasons: [],
      regionRule: { ...RULE, limitNumber: 1, timespanDays: 1, pickupsInTimespan: 1, limitDayNumber: 2, pickupsSameDay: 1 },
    });
    expect(text('shared.eligibility.ruleTimespan')).toBe(
      'Bezirksregel: höchstens 1 Abholung innerhalb von 1 Tag vor oder nach diesem Termin. Du hast in dieser Zeit schon 1 andere.',
    );
    expect(text('shared.eligibility.ruleDay')).toBe('Höchstens 2 pro Tag. An diesem Tag hast du schon 1 andere.');
  });

  it('leaves out a limit of 0 or less (no limit)', async () => {
    await render({ eligible: true, reasons: [], regionRule: { ...RULE, limitDayNumber: 0, limitNumber: -1 } });
    expect(text('shared.eligibility.ruleTimespan')).toBeNull();
    expect(text('shared.eligibility.ruleDay')).toBeNull();
  });

  it('says nothing about the rule when the user is already signed up', async () => {
    await render({
      eligible: false,
      reasons: [PickupEligibilityReason.AlreadySignedUp, PickupEligibilityReason.RegionRuleTimespanLimit],
      regionRule: RULE,
    });
    expect(text('shared.eligibility.ruleTimespan')).toBeNull();
    expect(text('shared.eligibility.ruleDay')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="shared.eligibility.reason"]').length).toBe(2);
  });

  it('says nothing about an inactive rule', async () => {
    await render({ eligible: true, reasons: [], regionRule: { ...RULE, active: false } });
    expect(text('shared.eligibility.ruleTimespan')).toBeNull();
    expect(text('shared.eligibility.ruleDay')).toBeNull();
  });
});
