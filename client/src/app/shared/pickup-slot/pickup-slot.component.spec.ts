import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PickupSlot, Role } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { PickupSlotComponent, firstName } from './pickup-slot.component';

const TRANSLATIONS = {
  'shared.pickupSlot.confirmed': '{name}, bestätigt',
  'shared.pickupSlot.unconfirmed': '{name}, unbestätigt',
  'shared.pickupSlot.mineConfirmed': 'Du ({name}), bestätigt – austragen',
  'shared.pickupSlot.mineUnconfirmed': 'Du ({name}), unbestätigt – austragen',
  'shared.pickupSlot.confirmOne': '{name} bestätigen',
  'shared.pickupSlot.you': 'Du',
  'shared.pickupSlot.free': 'Freier Platz – eintragen, {count} frei',
  'shared.pickupSlot.freeShort': '{count} frei',
  'shared.pickupSlot.signUp': 'Eintragen',
  'shared.pickupSlot.blocked': 'Gesperrt',
  'shared.pickupSlot.blockedLabel':
    'Für dich gesperrt, {count} frei – Grund anzeigen ({rules, plural, one {# Regel} other {# Regeln}})',
};

const slot = (id: string, displayName: string, confirmed: boolean): PickupSlot => ({
  user: { id, displayName, role: Role.Foodsaver, verified: true },
  confirmed,
});

describe('PickupSlotComponent (PU3 slot states)', () => {
  let fixture: ComponentFixture<PickupSlotComponent>;
  const qa = (selector: string) => [...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(selector)];
  const taken = () => qa('[data-testid="t.taken"]');

  async function render(inputs: Record<string, unknown>): Promise<void> {
    TestBed.configureTestingModule({ providers: provideDataStateTesting({ translations: TRANSLATIONS }) });
    fixture = TestBed.createComponent(PickupSlotComponent);
    fixture.componentRef.setInput('testId', 't');
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    await fixture.whenStable();
  }

  const SLOTS = [
    slot('a', 'Priya Sharma', true),
    slot('b', 'Marco Bernasconi', false),
    slot('me', 'Sarah Müller', false),
  ];

  it('gives every taken slot a ring style and a badge icon, and names the state', async () => {
    await render({ slotCount: 4, slots: SLOTS, myId: 'me' });
    const [confirmed, unconfirmed, mine] = taken();

    expect(confirmed.classList).not.toContain('slot--unconfirmed');
    expect(confirmed.querySelector('.slot__state .pi-check')).not.toBeNull();
    expect(confirmed.querySelector('.slot__state')?.getAttribute('aria-hidden')).toBe('true');
    expect(confirmed.getAttribute('aria-label')).toBe('Priya Sharma, bestätigt');
    expect(confirmed.getAttribute('data-confirmed')).toBe('true');

    expect(unconfirmed.classList).toContain('slot--unconfirmed');
    expect(unconfirmed.querySelector('.slot__state .pi-hourglass')).not.toBeNull();
    expect(unconfirmed.getAttribute('aria-label')).toBe('Marco Bernasconi, unbestätigt');
    expect(unconfirmed.getAttribute('data-confirmed')).toBe('false');

    expect(qa('[data-testid="t.free"]')).toHaveLength(1);
    expect(qa('[data-testid="t.confirm"]')).toHaveLength(0);
    expect(mine.getAttribute('data-mine')).toBe('true');
    expect(qa('[data-mine="true"]')).toHaveLength(1);
  });

  it('shows the own slot as a "Du" pill that stays clickable and keeps .taken', async () => {
    await render({ slotCount: 2, slots: [slot('me', 'Sarah Müller', true), slot('b', 'Marco Bernasconi', false)], myId: 'me' });
    const mine = qa('[data-mine="true"]')[0];
    expect(mine.getAttribute('data-testid')).toBe('t.taken');
    expect(mine.classList).toContain('slot--pill');
    expect(mine.querySelector('.slot__label')?.textContent?.trim()).toBe('Du');
    expect(mine.querySelector('.slot__label')?.getAttribute('aria-hidden')).toBe('true');
    expect(mine.querySelector('.slot__icon.pi-check')).not.toBeNull();
    expect(mine.getAttribute('aria-label')).toBe('Du (Sarah Müller), bestätigt – austragen');
    expect((mine as HTMLButtonElement).disabled).toBe(false);
    // others' slots are not clickable without slotsClickable
    expect((taken()[1] as HTMLButtonElement).disabled).toBe(true);

    const clicks: PickupSlot[] = [];
    fixture.componentInstance.slotClick.subscribe((s) => clicks.push(s));
    mine.click();
    expect(clicks.map((s) => s.user.id)).toEqual(['me']);
  });

  it('names an unconfirmed own slot', async () => {
    await render({ slotCount: 1, slots: [slot('me', 'Sarah Müller', false)], myId: 'me' });
    const mine = qa('[data-mine="true"]')[0];
    expect(mine.classList).toContain('slot--unconfirmed');
    expect(mine.querySelector('.slot__icon.pi-hourglass')).not.toBeNull();
    expect(mine.getAttribute('aria-label')).toBe('Du (Sarah Müller), unbestätigt – austragen');
  });

  it('renders split pills only when confirmable, with first name and a ✓ that emits confirmClick', async () => {
    await render({ slotCount: 3, slots: SLOTS, myId: 'me', confirmable: true, slotsClickable: true });
    const groups = qa('.split[role="group"]');
    expect(groups).toHaveLength(1);
    expect(groups[0].getAttribute('aria-label')).toBe('Marco Bernasconi, unbestätigt');

    const pill = groups[0].querySelector<HTMLElement>('[data-testid="t.taken"]')!;
    expect(pill.querySelector('.slot__label')?.textContent?.trim()).toBe('Marco');
    expect(pill.getAttribute('title')).toBe('Marco Bernasconi');
    expect(pill.getAttribute('aria-label')).toBe('Marco Bernasconi, unbestätigt');

    const confirm = qa('[data-testid="t.confirm"]');
    expect(confirm).toHaveLength(1);
    expect(confirm[0].getAttribute('aria-label')).toBe('Marco Bernasconi bestätigen');

    const slotClicks: string[] = [];
    const confirmClicks: string[] = [];
    fixture.componentInstance.slotClick.subscribe((s) => slotClicks.push(s.user.id));
    fixture.componentInstance.confirmClick.subscribe((s) => confirmClicks.push(s.user.id));
    pill.click();
    confirm[0].click();
    expect(slotClicks).toEqual(['b']);
    expect(confirmClicks).toEqual(['b']);

    // the confirmed slot and the own (unconfirmed) slot stay compact / "Du"
    expect(taken()).toHaveLength(3);
    expect(taken()[0].classList).not.toContain('slot--pill');
    expect(taken()[2].getAttribute('data-mine')).toBe('true');
  });

  it('renders one free chip per pickup: "+ Sign up" with the count in its name (owner feedback 2026-11-04)', async () => {
    // changed on purpose: one chip instead of one per free place (overrides M7 R2 #16)
    await render({ slotCount: 3, slots: [SLOTS[0]], freeClickable: true });
    const free = qa('[data-testid="t.free"]');
    expect(free).toHaveLength(1);
    expect(free[0].getAttribute('aria-label')).toBe('Freier Platz – eintragen, 2 frei');
    expect(free[0].textContent?.trim()).toBe('Eintragen');
    expect(free[0].querySelector('.pi-plus')).not.toBeNull();
    expect((free[0] as HTMLButtonElement).disabled).toBe(false);
    let clicked = 0;
    fixture.componentInstance.freeClick.subscribe(() => clicked++);
    free[0].click();
    expect(clicked).toBe(1);
  });

  it('shows "N free" disabled when the viewer cannot sign up, and no chip when the pickup is full', async () => {
    await render({ slotCount: 4, slots: [SLOTS[0]], freeClickable: false });
    const free = qa('[data-testid="t.free"]');
    expect(free).toHaveLength(1);
    expect(free[0].textContent?.trim()).toBe('3 frei');
    expect(free[0].getAttribute('aria-label')).toBe('3 frei');
    expect((free[0] as HTMLButtonElement).disabled).toBe(true);
    expect(free[0].querySelector('.pi-plus')).toBeNull();

    fixture.componentRef.setInput('slotCount', 1);
    await fixture.whenStable();
    expect(qa('[data-testid="t.free"]')).toHaveLength(0);
  });

  it('shows a blocked free place as a "Gesperrt" button that opens the reason (decision 165 S2)', async () => {
    await render({ slotCount: 3, slots: [SLOTS[0]], freeClickable: true, blocked: true, blockedRuleCount: 2 });
    expect(qa('[data-testid="t.free"]')).toHaveLength(0);
    const blocked = qa('[data-testid="t.blocked"]');
    expect(blocked).toHaveLength(1);
    expect(blocked[0].classList).toContain('slot--blocked');
    expect(blocked[0].getAttribute('aria-haspopup')).toBe('dialog');
    expect(blocked[0].getAttribute('aria-label')).toBe('Für dich gesperrt, 2 frei – Grund anzeigen (2 Regeln)');
    expect(blocked[0].querySelector('.pi-lock')).not.toBeNull();
    expect(blocked[0].textContent?.trim()).toBe('Gesperrt');
    expect((blocked[0] as HTMLButtonElement).disabled).toBe(false);
    let opened = 0;
    let signUps = 0;
    fixture.componentInstance.blockedClick.subscribe(() => opened++);
    fixture.componentInstance.freeClick.subscribe(() => signUps++);
    blocked[0].click();
    expect(opened).toBe(1);
    expect(signUps).toBe(0);

    // a full pickup has no free place to block
    fixture.componentRef.setInput('slotCount', 1);
    await fixture.whenStable();
    expect(qa('[data-testid="t.blocked"]')).toHaveLength(0);
  });

  it('takes the first name up to the first space', () => {
    expect(firstName('Marco Bernasconi')).toBe('Marco');
    expect(firstName('Priya')).toBe('Priya');
  });
});
