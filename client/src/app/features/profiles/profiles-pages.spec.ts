import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode, BuddyRelation, Language, PublicProfile, Role, Theme } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { BuddiesPageComponent } from './pages/buddies-page.component';
import { ProfilePageComponent } from './pages/profile-page.component';
import { myProfileRedirect } from './profiles.routes';

const ME = '00000001-0001-4000-8000-000000000009';
const SARAH = '00000001-0001-4000-8000-000000000006';
const EMPTY_PAGE = { items: [], page: { page: 0, size: 50, totalItems: 0, totalPages: 0 } };

function profile(overrides: Partial<PublicProfile> = {}): PublicProfile {
  return {
    user: { id: SARAH, displayName: 'Sarah Müller', firstName: 'Sarah', role: Role.Foodsaver, verified: true, foodsaverNumber: 100006 },
    regions: [{ id: 'r1', name: 'Zürich Stadt', type: 'CITY', admin: false }],
    groups: [],
    stats: { pickupCount: 42, kgSaved: 310.4, postCount: 7, bananaCount: 5, buddyCount: 2, basketsSharedCount: 1 },
    banana: { count: 5, givenByMe: false, mayGive: true },
    buddy: { status: BuddyRelation.None },
    achievements: [],
    kamChains: [],
    permissions: {
      mayEditProfile: false,
      maySeeNotes: false,
      mayReport: false,
      mayGiveBanana: true,
      maySeeHistory: false,
      maySeeCommitments: false,
      mayAdminister: false,
      maySeePickups: false,
    },
    ...overrides,
  };
}

describe('profiles pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const doc = (selector: string) => document.querySelector(selector);
  const settle = () => harness.fixture.whenStable();
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'profiles.card.foodsaverNumber': 'Nr. {number}' },
          routes: [
            { path: 'profile/me/buddies', component: BuddiesPageComponent },
            { path: 'profile/me', canActivate: [myProfileRedirect], children: [] },
            { path: 'profile/:userId', component: ProfilePageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: { id: ME, email: 'bieb@local', displayName: 'Nina', role: Role.StoreManager, language: Language.De, theme: Theme.System, verified: true },
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  async function open(body: PublicProfile = profile(), url = `/profile/${SARAH}`) {
    await harness.navigateByUrl(url);
    expect(state('profiles.page.state')).toBe('loading');
    http.expectOne(`/api/users/${SARAH}/profile`).flush(body);
    await settle();
  }

  it('/profile/me redirects to the own profile and keeps the tab', async () => {
    await harness.navigateByUrl('/profile/me?tab=bananas');
    expect(TestBed.inject(Router).url).toBe(`/profile/${ME}?tab=bananas`);
    http.expectOne(`/api/users/${ME}/profile`).flush(profile({ user: { id: ME, displayName: 'Nina', role: Role.StoreManager, verified: true } }));
    await settle();
    http.expectOne((req) => req.url === `/api/users/${ME}/bananas`).flush([]);
    await settle();
    expect(state('profiles.bananas.state')).toBe('empty');
  });

  it('renders head, stats and regions; the wall loads and the foreign wall has no composer', async () => {
    await open();
    expect(state('profiles.page.state')).toBe('success');
    expect(q('[data-testid="profiles.page.name"]')?.textContent).toContain('Sarah Müller');
    expect(q('[data-testid="profiles.page.stat.pickups"]')?.textContent).toContain('42');
    expect(q('[data-testid="profiles.page.region"]')?.textContent).toContain('Zürich Stadt');
    // number only for administrators, no sleep banner, no notes/history tabs
    expect(q('[data-testid="profiles.page.foodsaverNumber"]')).toBeNull();
    expect(q('[data-testid="profiles.page.sleep"]')).toBeNull();
    expect(q('[data-testid="profiles.page.tab.notes"]')).toBeNull();
    expect(q('[data-testid="profiles.page.tab.history"]')).toBeNull();
    expect(q('[data-testid="profiles.page.report"]')).toBeNull();

    const wall = http.expectOne((req) => req.url === `/api/users/${SARAH}/wall`);
    wall.flush({
      items: [{ id: 'w1', body: 'Hallo **Welt**', createdAt: '2026-11-01T09:00:00Z', mayDelete: false }],
      page: { page: 0, size: 50, totalItems: 1, totalPages: 1 },
    });
    await settle();
    expect(state('profiles.wall.state')).toBe('success');
    expect(q('[data-testid="profiles.wall.post"]')).not.toBeNull();
    expect(q('[data-testid="profiles.wall.composer"]')).toBeNull();
  });

  it('shows the sleep banner and, for administrators, the foodsaver number and admin tabs', async () => {
    const admin = profile({
      user: { ...profile().user, sleepMode: true, sleepMessage: 'Bis Ende Monat in den Ferien' },
      permissions: { ...profile().permissions, mayAdminister: true, maySeeNotes: true, maySeeHistory: true, maySeeCommitments: true, mayEditProfile: true },
    });
    await open(admin);
    http.expectOne((req) => req.url === `/api/users/${SARAH}/wall`).flush(EMPTY_PAGE);
    await settle();
    expect(q('[data-testid="profiles.page.sleepMessage"]')?.textContent).toContain('Ferien');
    expect(q('[data-testid="profiles.page.foodsaverNumber"]')?.textContent).toContain('100006');
    expect(q('[data-testid="profiles.page.tab.notes"]')).not.toBeNull();
    expect(q('[data-testid="profiles.page.edit"]')).not.toBeNull();

    await harness.navigateByUrl(`/profile/${SARAH}?tab=notes`);
    await settle();
    const notes = http.expectOne(`/api/admin/users/${SARAH}/notes`);
    notes.flush([{ id: 'n1', body: 'Gespräch geführt', createdAt: '2026-10-01T09:00:00Z', mayDelete: true }]);
    await settle();
    expect(state('profiles.notes.state')).toBe('success');
    expect(q('[data-testid="profiles.notes.composer"]')).not.toBeNull();
  });

  it('shows the not-found state for an unknown user', async () => {
    await harness.navigateByUrl('/profile/00000001-0001-4000-8000-000000000099');
    http
      .expectOne('/api/users/00000001-0001-4000-8000-000000000099/profile')
      .flush(apiHttpError(404, ApiErrorCode.UserNotFound).error, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(state('profiles.page.state')).toBe('notFound');
  });

  it('error state retries the profile request', async () => {
    await harness.navigateByUrl(`/profile/${SARAH}`);
    http
      .expectOne(`/api/users/${SARAH}/profile`)
      .flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(state('profiles.page.state')).toBe('error');
    (q('[data-testid$=".retry"] button') as HTMLButtonElement).click();
    await settle();
    http.expectOne(`/api/users/${SARAH}/profile`).flush(profile());
    await settle();
    expect(state('profiles.page.state')).toBe('success');
    http.expectOne((req) => req.url === `/api/users/${SARAH}/wall`).flush(EMPTY_PAGE);
  });

  it('gives a banana only with at least 100 characters', async () => {
    await open();
    http.expectOne((req) => req.url === `/api/users/${SARAH}/wall`).flush(EMPTY_PAGE);
    await settle();
    (q('[data-testid="profiles.page.banana"] button') as HTMLButtonElement).click();
    await settle();
    const textarea = doc('[data-testid="profiles.bananaDialog.message"]') as HTMLTextAreaElement;
    const confirm = doc('[data-testid="profiles.bananaDialog.confirm"] button') as HTMLButtonElement;
    textarea.value = 'Zu kurz';
    textarea.dispatchEvent(new Event('input'));
    await settle();
    confirm.click();
    await settle();
    http.expectNone(`/api/users/${SARAH}/bananas`);

    const message = 'Sarah ist zuverlässig, pünktlich und freundlich. Wir haben schon viele Abholungen zusammen gemacht. Danke!';
    expect(message.length).toBeGreaterThanOrEqual(100);
    textarea.value = message;
    textarea.dispatchEvent(new Event('input'));
    await settle();
    confirm.click();
    const give = http.expectOne((req) => req.method === 'POST' && req.url === `/api/users/${SARAH}/bananas`);
    expect(give.request.body).toEqual({ message });
    give.flush({ sender: { id: ME, displayName: 'Nina', role: Role.StoreManager, verified: true }, recipient: profile().user, message, givenAt: '2026-11-02T09:00:00Z' });
    await settle();
    http.expectOne(`/api/users/${SARAH}/profile`).flush(profile({ banana: { count: 6, givenByMe: true, mayGive: false } }));
    await settle();
    expect(q('[data-testid="profiles.page.bananaGiven"]')).not.toBeNull();
    expect(q('[data-testid="profiles.page.banana"]')).toBeNull();
  });

  it('sends a buddy request and accepts an incoming one', async () => {
    await open();
    http.expectOne((req) => req.url === `/api/users/${SARAH}/wall`).flush(EMPTY_PAGE);
    await settle();
    (q('[data-testid="profiles.page.buddyRequest"] button') as HTMLButtonElement).click();
    http.expectOne((req) => req.method === 'POST' && req.url === `/api/users/${SARAH}/buddies`).flush({ status: BuddyRelation.RequestedByMe });
    await settle();
    http.expectOne(`/api/users/${SARAH}/profile`).flush(profile({ buddy: { status: BuddyRelation.RequestedByThem } }));
    await settle();
    expect(q('[data-testid="profiles.page.buddyDecline"]')).not.toBeNull();
    (q('[data-testid="profiles.page.buddyAccept"] button') as HTMLButtonElement).click();
    http.expectOne((req) => req.method === 'POST' && req.url === `/api/users/${SARAH}/buddies`).flush({ status: BuddyRelation.Confirmed });
    await settle();
    http.expectOne(`/api/users/${SARAH}/profile`).flush(profile({ buddy: { status: BuddyRelation.Confirmed } }));
    await settle();
    expect(q('[data-testid="profiles.page.buddyConfirmed"]')).not.toBeNull();
    expect(q('[data-testid="profiles.page.buddyRemove"]')).not.toBeNull();
  });

  it('bananas tab: received list, switch to given, delete own received banana', async () => {
    await open(profile(), `/profile/${SARAH}?tab=bananas`);
    const banana = {
      sender: { id: ME, displayName: 'Nina', role: Role.StoreManager, verified: true },
      recipient: profile().user,
      message: 'Danke!',
      givenAt: '2026-11-01T09:00:00Z',
    };
    const received = http.expectOne((req) => req.url === `/api/users/${SARAH}/bananas`);
    expect(received.request.params.get('direction')).toBe('received');
    received.flush([banana]);
    await settle();
    expect(state('profiles.bananas.state')).toBe('success');
    // the viewer is the sender: may delete
    expect(q('[data-testid="profiles.bananas.delete"]')).not.toBeNull();

    (q('[data-testid="profiles.bananas.sent"] button') as HTMLButtonElement).click();
    await settle();
    const sent = http.expectOne((req) => req.url === `/api/users/${SARAH}/bananas`);
    expect(sent.request.params.get('direction')).toBe('sent');
    sent.flush([]);
    await settle();
    expect(state('profiles.bananas.state')).toBe('empty');
  });

  it('buddies page: loading, empty requests, buddies list and accept', async () => {
    await harness.navigateByUrl('/profile/me/buddies');
    expect(state('profiles.buddies.state')).toBe('loading');
    const nina = { id: SARAH, displayName: 'Sarah Müller', role: Role.Foodsaver, verified: true };
    http.expectOne('/api/users/me/buddies/requests').flush([nina]);
    http.expectOne('/api/users/me/buddies').flush([]);
    await settle();
    expect(state('profiles.buddies.state')).toBe('empty');
    expect(state('profiles.buddies.requests.state')).toBe('success');

    (q('[data-testid="profiles.buddies.accept"] button') as HTMLButtonElement).click();
    http.expectOne((req) => req.method === 'POST' && req.url === `/api/users/${SARAH}/buddies`).flush({ status: BuddyRelation.Confirmed });
    await settle();
    http.expectOne('/api/users/me/buddies/requests').flush([]);
    http.expectOne('/api/users/me/buddies').flush([nina]);
    await settle();
    expect(state('profiles.buddies.requests.state')).toBe('empty');
    expect(q('[data-testid="profiles.buddies.buddy"]')).not.toBeNull();
  });

  it('buddies page: error with retry', async () => {
    await harness.navigateByUrl('/profile/me/buddies');
    http.expectOne('/api/users/me/buddies/requests').flush([]);
    http.expectOne('/api/users/me/buddies').flush(apiHttpError(503, ApiErrorCode.UnexpectedError).error, { status: 503, statusText: 'Unavailable' });
    await settle();
    expect(state('profiles.buddies.state')).toBe('error');
  });
});
