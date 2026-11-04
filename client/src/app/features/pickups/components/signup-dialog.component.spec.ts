import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';

import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { PickupSignupDialogComponent, SignupDone } from './signup-dialog.component';

const X = '2030-11-12T18:00:00Z';
const TARGET = { storeId: 's1', storeName: 'Coop', pickupAt: X, timeZone: 'Europe/Zurich' };
const Y = { id: 'y', name: "Babu's Bakery" };
const LINK_ONLY = {
  eligible: false,
  reasons: ['AUTOMATION_RULE'],
  ruleBlocks: [{ rule: 'LINKED_STORE', params: { stores: [Y], windowMode: 'LAST_DAYS', before: 0, after: 2 } }],
};
const rule = (patch: object) => ({
  stores: [Y],
  windowMode: 'LAST_DAYS',
  before: 0,
  after: 2,
  from: '2030-11-11T23:00:00Z',
  to: '2030-11-13T23:00:00Z',
  options: [],
  notMember: [],
  ...patch,
});
const option = (pickupAt: string, patch: object = {}) => ({ store: Y, pickupAt, freeCount: 2, reasons: [], ruleBlocks: [], ...patch });

/** Decision 165 S3: the linked-store step of the sign-up dialog (proposal §3.4). */
describe('PickupSignupDialogComponent linked step (decision 165 S3)', () => {
  let fixture: ComponentFixture<PickupSignupDialogComponent>;
  let http: HttpTestingController;
  const el = () => document.body;
  const byTestId = (id: string) => el().querySelectorAll<HTMLElement>(`[data-testid="${id}"]`);
  const button = (id: string) => byTestId(id)[0]?.querySelector('button') ?? null;

  async function open(eligibility: object | null, linked: object, target: object = TARGET): Promise<void> {
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting({}), provideRouter([]), provideOptimus(), MessageService],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PickupSignupDialogComponent);
    fixture.componentRef.setInput('target', target);
    fixture.componentInstance.visible.set(true);
    await fixture.whenStable();
    if (eligibility) {
      http.expectOne((req) => req.url === `/api/stores/s1/pickups/${encodeURIComponent(X)}/eligibility` || req.url.endsWith('/eligibility')).flush(eligibility);
      await fixture.whenStable();
    }
    http.expectOne((req) => req.url === '/api/stores/s1/automation/linked-options').flush(linked);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    document.body.innerHTML = '';
  });

  it('choose: step 2 of 2 lists free pickups, blocked ones as "Gesperrt dort", and books both in one request', async () => {
    const free = option('2030-11-13T17:00:00Z');
    const blocked = option('2030-11-14T17:00:00Z', { ruleBlocks: [{ rule: 'GAP', params: {} }] });
    await open(LINK_ONLY, { rules: [rule({ options: [free, blocked] })] });
    expect(byTestId('pickups.signup.linked.step')[0].dataset['step']).toBe('2');
    expect(byTestId('pickups.signup.linked.step')[0].dataset['total']).toBe('2');
    expect(byTestId('pickups.signup.linked.option')).toHaveLength(2);
    expect(el().querySelectorAll('[data-testid="pickups.signup.linked.option"][data-blocked="true"]')).toHaveLength(1);
    expect(button('pickups.signup.linked.confirm')?.disabled).toBe(true);
    expect(byTestId('pickups.signup.confirm')).toHaveLength(0);

    byTestId('pickups.signup.linked.option')[0].querySelector('input')!.click();
    await fixture.whenStable();
    expect(button('pickups.signup.linked.confirm')?.disabled).toBe(false);
    let done: SignupDone | undefined;
    fixture.componentInstance.signedUp.subscribe((result) => (done = result));
    button('pickups.signup.linked.confirm')!.click();
    const request = http.expectOne((req) => req.method === 'POST' && req.url.endsWith('/signups'));
    expect(request.request.url).toBe(`/api/stores/s1/pickups/${encodeURIComponent(X)}/signups`);
    expect(request.request.body).toEqual({ linkedPickups: [{ storeId: 'y', pickupAt: free.pickupAt }] });
    request.flush({ confirmed: false });
    await fixture.whenStable();
    expect(done).toEqual({ confirmed: false, linkedStores: ["Babu's Bakery"] });
  });

  it('satisfied: the existing linked pickup is named and one plain sign-up is enough', async () => {
    await open(
      { eligible: true, reasons: [] },
      { rules: [rule({ satisfiedBy: { store: Y, pickupAt: '2030-11-13T17:30:00Z' } })] },
    );
    expect(byTestId('pickups.signup.linked.satisfied')).toHaveLength(1);
    expect(byTestId('pickups.signup.linked.step')).toHaveLength(0);
    button('pickups.signup.confirm')!.click();
    const request = http.expectOne((req) => req.method === 'POST');
    expect(request.request.body).toBeNull();
    request.flush({ confirmed: true });
  });

  it('none free and not a member: no way to confirm', async () => {
    await open(LINK_ONLY, { rules: [rule({})] });
    expect(byTestId('pickups.signup.linked.noneFree')).toHaveLength(1);
    expect(button('pickups.signup.linked.confirm')?.disabled).toBe(true);
    TestBed.resetTestingModule();
    document.body.innerHTML = '';
    await open(LINK_ONLY, { rules: [rule({ notMember: [Y] })] });
    expect(byTestId('pickups.signup.linked.notMember')).toHaveLength(1);
    expect(byTestId('pickups.signup.linked.apply')[0].getAttribute('href')).toBe('/stores/y');
    expect(byTestId('pickups.signup.linked.noneFree')).toHaveLength(0);
  });

  it('several rules: one step each, "Weiter" until the last, all chosen pickups in one request', async () => {
    const z = { id: 'z', name: 'Hiltl' };
    const LINKS = {
      ...LINK_ONLY,
      ruleBlocks: [...LINK_ONLY.ruleBlocks, { rule: 'LINKED_STORE', params: { stores: [z] } }],
    };
    const first = option('2030-11-13T17:00:00Z');
    const second = { ...option('2030-11-12T10:00:00Z'), store: z };
    await open(LINKS, { rules: [rule({ options: [first] }), rule({ stores: [z], options: [second] })] });
    expect(byTestId('pickups.signup.linked.step')[0].dataset['total']).toBe('3');
    expect(button('pickups.signup.linked.next')?.disabled).toBe(true);
    byTestId('pickups.signup.linked.option')[0].querySelector('input')!.click();
    await fixture.whenStable();
    button('pickups.signup.linked.next')!.click();
    await fixture.whenStable();
    expect(byTestId('pickups.signup.linked.step')[0].dataset['step']).toBe('3');
    byTestId('pickups.signup.linked.option')[0].querySelector('input')!.click();
    await fixture.whenStable();
    button('pickups.signup.linked.confirm')!.click();
    const request = http.expectOne((req) => req.method === 'POST');
    expect(request.request.body).toEqual({
      linkedPickups: [
        { storeId: 'y', pickupAt: first.pickupAt },
        { storeId: 'z', pickupAt: second.pickupAt },
      ],
    });
    request.flush({ confirmed: false });
  });

  it('repair: the own sign-up stays, the chosen linked pickup is the sign-up', async () => {
    const free = option('2030-11-13T17:00:00Z');
    await open(null, { rules: [rule({ options: [free] })] }, { ...TARGET, linkMissingUntil: '2030-11-10T11:00:00Z' });
    expect(byTestId('pickups.signup.linked.repair')).toHaveLength(1);
    byTestId('pickups.signup.linked.option')[0].querySelector('input')!.click();
    await fixture.whenStable();
    button('pickups.signup.linked.confirm')!.click();
    const request = http.expectOne((req) => req.method === 'POST');
    expect(request.request.url).toBe(`/api/stores/y/pickups/${encodeURIComponent(free.pickupAt)}/signups`);
    expect(request.request.body).toBeNull();
    request.flush({ confirmed: false });
  });
});
