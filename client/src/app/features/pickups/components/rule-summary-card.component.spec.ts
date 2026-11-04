import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { RuleSummaryCardComponent } from './rule-summary-card.component';

const RULES = { enabled: true, gapDays: 3, links: [], experience: false, manualDates: [], holidays: true };

describe('RuleSummaryCardComponent (decision 165 S2)', () => {
  let fixture: ComponentFixture<RuleSummaryCardComponent>;
  let http: HttpTestingController;
  const card = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLDetailsElement>('[data-testid="stores.pickups.rules"]');

  async function render(rules: object): Promise<void> {
    TestBed.configureTestingModule({ providers: provideDataStateTesting({}) });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RuleSummaryCardComponent);
    fixture.componentRef.setInput('storeId', 's1');
    fixture.componentRef.setInput('timeZone', 'Europe/Zurich');
    await fixture.whenStable();
    http.expectOne('/api/stores/s1/automation/rules').flush(rules);
    await fixture.whenStable();
  }

  afterEach(() => localStorage.removeItem('fs.pickups.rules.open.s1'));

  it('is collapsed by default and opens from the remembered state', async () => {
    await render(RULES);
    expect(card()?.open).toBe(false);
    expect(card()?.querySelectorAll('[data-testid="stores.pickups.rules.line"]')).toHaveLength(2);
    TestBed.resetTestingModule();
    localStorage.setItem('fs.pickups.rules.open.s1', '1');
    await render(RULES);
    expect(card()?.open).toBe(true);
  });

  it('the summary toggles the card and remembers it per store', async () => {
    await render(RULES);
    card()!.querySelector<HTMLElement>('[data-testid="stores.pickups.rules.toggle"]')!.click();
    await fixture.whenStable();
    expect(card()?.open).toBe(true);
    expect(localStorage.getItem('fs.pickups.rules.open.s1')).toBe('1');
    card()!.querySelector<HTMLElement>('[data-testid="stores.pickups.rules.toggle"]')!.click();
    await fixture.whenStable();
    expect(card()?.open).toBe(false);
    expect(localStorage.getItem('fs.pickups.rules.open.s1')).toBeNull();
  });

  it('is hidden while the rules are off', async () => {
    await render({ ...RULES, enabled: false });
    expect(card()).toBeNull();
  });
});
