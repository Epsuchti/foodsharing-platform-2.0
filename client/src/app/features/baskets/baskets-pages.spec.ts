import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import {
  ApiErrorCode,
  Basket,
  BasketKind,
  BasketLocationType,
  BasketRequestStatus,
  BasketStatus,
  BasketSummary,
  Language,
  Role,
  Theme,
  UserProfile,
} from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { BasketFormPageComponent } from './pages/basket-form-page.component';
import { BasketPageComponent } from './pages/basket-page.component';
import { BasketsPageComponent } from './pages/baskets-page.component';
import { MyBasketsPageComponent } from './pages/my-baskets-page.component';

const ME = '00000001-0001-4000-8000-000000000006';
const NINA = '00000001-0001-4000-8000-000000000009';
const B3 = '00000048-0001-4000-8000-000000000003';

function me(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    id: ME,
    email: 'foodsaver@local',
    displayName: 'Sarah Müller',
    language: Language.De,
    theme: Theme.System,
    role: Role.Foodsaver,
    verified: true,
    registeredAt: '2024-05-10T17:00:00Z',
    latitude: 47.389,
    longitude: 8.548,
    ...overrides,
  };
}

function summary(overrides: Partial<BasketSummary> = {}): BasketSummary {
  return {
    id: B3,
    description: 'Joghurt und Milch, Ablaufdatum morgen',
    lat: 47.38,
    lon: 8.51,
    distanceKm: 3.1,
    createdAt: '2026-10-31T09:00:00Z',
    expiresAt: '2099-12-31T22:00:00Z',
    owner: { id: NINA, displayName: 'Nina Schneider', role: Role.StoreManager, verified: true },
    status: BasketStatus.Active,
    ...overrides,
  };
}

function basket(overrides: Partial<Basket> = {}): Basket {
  return {
    ...summary(),
    locationType: BasketLocationType.Home,
    groceries: [{ id: 'g1', name: 'Milchprodukte' }],
    kinds: [BasketKind.Dairy],
    contact: { message: true, phone: '+41441234509' },
    permissions: { mayEdit: false, mayDelete: false, mayRequest: true },
    ...overrides,
  };
}

describe('baskets pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const all = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const doc = (selector: string) => document.querySelector(selector);
  const settle = () => harness.fixture.whenStable();
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const click = (selector: string) => ((q(`${selector} button`) ?? q(selector)) as HTMLElement).click();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'baskets.card.distance': '{km} km' },
          routes: [
            { path: 'baskets', component: BasketsPageComponent },
            { path: 'baskets/mine', component: MyBasketsPageComponent },
            { path: 'baskets/new', component: BasketFormPageComponent },
            { path: 'baskets/:basketId/edit', component: BasketFormPageComponent },
            { path: 'baskets/:basketId', component: BasketPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: { id: ME, email: 'foodsaver@local', displayName: 'Sarah', role: Role.Foodsaver, language: Language.De, theme: Theme.System, verified: true },
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const nearby = (req: { url: string }) => req.url === '/api/baskets/nearby';

  describe('/baskets', () => {
    it('loads the baskets around the profile position and reloads with a new radius', async () => {
      await harness.navigateByUrl('/baskets');
      expect(state('baskets.nearby.profileState')).toBe('loading');
      http.expectOne('/api/users/me').flush(me());
      await settle();
      const first = http.expectOne(nearby);
      expect(first.request.params.get('lat')).toBe('47.389');
      expect(first.request.params.get('distanceKm')).toBe('10');
      expect(state('baskets.nearby.state')).toBe('loading');
      first.flush([summary(), summary({ id: 'b2', description: 'Äpfel', distanceKm: 4.2 })]);
      await settle();
      expect(state('baskets.nearby.state')).toBe('success');
      expect(all('[data-testid="baskets.card"]').length).toBe(2);
      expect(q('[data-testid="baskets.card.distance"]')?.textContent).toContain('3.1');

      const slider = q('[data-testid="baskets.nearby.distance"]') as HTMLInputElement;
      slider.value = '30';
      slider.dispatchEvent(new Event('input'));
      slider.dispatchEvent(new Event('change'));
      await settle();
      const second = http.expectOne(nearby);
      expect(second.request.params.get('distanceKm')).toBe('30');
      second.flush([]);
      await settle();
      expect(state('baskets.nearby.state')).toBe('empty');
    });

    it('asks for a position when the profile has no coordinates', async () => {
      await harness.navigateByUrl('/baskets');
      http.expectOne('/api/users/me').flush(me({ latitude: undefined, longitude: undefined }));
      await settle();
      http.expectNone(nearby);
      expect(q('[data-testid="baskets.nearby.position"]')).not.toBeNull();
      expect(q('[data-testid="baskets.nearby.locate"]')).not.toBeNull();
    });

    it('shows the error state with retry', async () => {
      await harness.navigateByUrl('/baskets');
      http.expectOne('/api/users/me').flush(me());
      await settle();
      http.expectOne(nearby).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(state('baskets.nearby.state')).toBe('error');
      (q('[data-testid="baskets.nearby.state"] [data-testid$=".retry"] button') as HTMLButtonElement).click();
      await settle();
      http.expectOne(nearby).flush([summary()]);
      await settle();
      expect(state('baskets.nearby.state')).toBe('success');
    });
  });

  describe('/baskets/:id', () => {
    it('lets others request with a message and shows their request', async () => {
      await harness.navigateByUrl(`/baskets/${B3}`);
      expect(state('baskets.detail.state')).toBe('loading');
      http.expectOne(`/api/baskets/${B3}`).flush(basket());
      await settle();
      expect(state('baskets.detail.state')).toBe('success');
      expect(q('[data-testid="baskets.detail.kind"]')?.textContent).toContain('baskets.kind.DAIRY');
      expect(q('[data-testid="baskets.detail.message"]')).not.toBeNull();
      expect(q('[data-testid="baskets.detail.requests"]')).toBeNull();
      expect(q('[data-testid="baskets.detail.edit"]')).toBeNull();

      click('[data-testid="baskets.detail.request"]');
      await settle();
      const textarea = doc('[data-testid="baskets.requestDialog.message"]') as HTMLTextAreaElement;
      textarea.value = '  Kann ich heute um 18 Uhr vorbeikommen?  ';
      textarea.dispatchEvent(new Event('input'));
      await settle();
      (doc('[data-testid="baskets.requestDialog.send"] button') as HTMLButtonElement).click();
      const request = http.expectOne((req) => req.method === 'POST' && req.url === `/api/baskets/${B3}/requests`);
      expect(request.request.body).toEqual({ message: 'Kann ich heute um 18 Uhr vorbeikommen?' });
      request.flush({ requester: { id: ME, displayName: 'Sarah', role: Role.Foodsaver, verified: true }, status: BasketRequestStatus.Requested, requestedAt: '2026-11-02T09:00:00Z' });
      await settle();
      const mine = { requester: { id: ME, displayName: 'Sarah', role: Role.Foodsaver, verified: true }, status: BasketRequestStatus.Requested, requestedAt: '2026-11-02T09:00:00Z' };
      http.expectOne(`/api/baskets/${B3}`).flush(basket({ myRequest: mine, permissions: { mayEdit: false, mayDelete: false, mayRequest: false } }));
      await settle();
      expect(q('[data-testid="baskets.detail.myRequest"]')?.getAttribute('data-status')).toBe('REQUESTED');
      expect(q('[data-testid="baskets.detail.request"]')).toBeNull();
      click('[data-testid="baskets.detail.withdraw"]');
      http.expectOne((req) => req.method === 'DELETE' && req.url === `/api/baskets/${B3}/requests`).flush(null);
      await settle();
      http.expectOne(`/api/baskets/${B3}`).flush(basket());
      await settle();
    });

    it('shows the owner the requests and answers them', async () => {
      const own = basket({
        owner: { id: ME, displayName: 'Sarah', role: Role.Foodsaver, verified: true },
        permissions: { mayEdit: true, mayDelete: true, mayRequest: false },
        requests: [
          {
            requester: { id: NINA, displayName: 'Nina Schneider', role: Role.StoreManager, verified: true },
            status: BasketRequestStatus.Requested,
            requestedAt: '2026-11-01T09:00:00Z',
            message: 'Gerne heute Abend!',
          },
        ],
      });
      await harness.navigateByUrl(`/baskets/${B3}`);
      http.expectOne(`/api/baskets/${B3}`).flush(own);
      await settle();
      expect(q('[data-testid="baskets.detail.edit"]')).not.toBeNull();
      expect(q('[data-testid="baskets.detail.request"]')).toBeNull();
      expect(q('[data-testid="baskets.detail.requestMessage"]')?.textContent).toContain('Gerne heute Abend');
      click('[data-testid="baskets.detail.answer.PICKED_UP"]');
      const answer = http.expectOne((req) => req.method === 'PATCH' && req.url === `/api/baskets/${B3}/requests/${NINA}`);
      expect(answer.request.body).toEqual({ status: 'PICKED_UP' });
      answer.flush({ ...own.requests![0], status: BasketRequestStatus.PickedUp });
      await settle();
      http.expectOne(`/api/baskets/${B3}`).flush({ ...own, status: BasketStatus.Taken });
      await settle();
      expect(q('[data-testid="baskets.detail.status"]')).not.toBeNull();
    });

    it('renders not found for an unknown basket', async () => {
      await harness.navigateByUrl(`/baskets/${B3}`);
      http.expectOne(`/api/baskets/${B3}`).flush(apiHttpError(404, ApiErrorCode.BasketNotFound).error, { status: 404, statusText: 'Not Found' });
      await settle();
      expect(state('baskets.detail.state')).toBe('notFound');
    });
  });

  describe('/baskets/mine', () => {
    it('loads offered and requested baskets with empty and error states', async () => {
      await harness.navigateByUrl('/baskets/mine');
      expect(state('baskets.mine.offeredState')).toBe('loading');
      http.expectOne('/api/baskets/mine').flush([summary({ owner: { id: ME, displayName: 'Sarah', role: Role.Foodsaver, verified: true }, requestCount: 2 })]);
      http.expectOne('/api/baskets/requested').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(state('baskets.mine.offeredState')).toBe('success');
      expect(q('[data-testid="baskets.card.requests"]')).not.toBeNull();
      expect(state('baskets.mine.requestedState')).toBe('error');
      (q('[data-testid="baskets.mine.requestedState"] [data-testid$=".retry"] button') as HTMLButtonElement).click();
      await settle();
      http.expectOne('/api/baskets/requested').flush([]);
      await settle();
      expect(state('baskets.mine.requestedState')).toBe('empty');
    });
  });

  describe('/baskets/new', () => {
    async function openForm(profile = me()) {
      await harness.navigateByUrl('/baskets/new');
      expect(state('baskets.form.state')).toBe('loading');
      http.expectOne('/api/users/me').flush(profile);
      http.expectOne('/api/store-metadata').flush({ categories: [], groceries: [{ id: 'g1', name: 'Brot' }], weightCategories: [], chains: [] });
      // the loader is a promise chain: let it resolve before the change detection settles
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      expect(state('baskets.form.state')).toBe('success');
    }

    it('explains missing contact and location before sending', async () => {
      await openForm(me({ latitude: undefined, longitude: undefined }));
      // without home coordinates the form starts with a custom location
      expect((q('[data-testid="baskets.form.locationCustom"]') as HTMLInputElement).checked).toBe(true);
      const description = q('[data-testid="baskets.form.description"]') as HTMLTextAreaElement;
      description.value = 'Zwei Brote';
      description.dispatchEvent(new Event('input'));
      (q('[data-testid="baskets.form.contactMessage"]') as HTMLInputElement).click();
      await settle();
      click('[data-testid="baskets.form.submit"]');
      await settle();
      const codes = [...all('[data-testid="baskets.form.problems"] li')].map((li) => li.getAttribute('data-code'));
      expect(codes).toEqual(['BASKET_LOCATION_REQUIRED', 'BASKET_CONTACT_REQUIRED']);
      http.expectNone((req) => req.method === 'POST' && req.url === '/api/baskets');
    });

    it('creates a basket at home with kinds and lifetime', async () => {
      await openForm();
      const description = q('[data-testid="baskets.form.description"]') as HTMLTextAreaElement;
      description.value = 'Zwei Brote';
      description.dispatchEvent(new Event('input'));
      (q('[data-testid="baskets.form.kind.BAKED_GOODS"]') as HTMLInputElement).click();
      const lifetime = q('[data-testid="baskets.form.lifetime"]') as HTMLInputElement;
      lifetime.value = '3';
      lifetime.dispatchEvent(new Event('input'));
      await settle();
      click('[data-testid="baskets.form.submit"]');
      const create = http.expectOne((req) => req.method === 'POST' && req.url === '/api/baskets');
      expect(create.request.body).toMatchObject({
        description: 'Zwei Brote',
        kinds: ['BAKED_GOODS'],
        locationType: 'HOME',
        contactMessage: true,
        lifetimeDays: 3,
      });
      create.flush(basket({ id: 'new-id' }));
      await settle();
      // the detail page of the new basket opens
      http.expectOne('/api/baskets/new-id').flush(basket({ id: 'new-id' }));
      await settle();
      expect(state('baskets.detail.state')).toBe('success');
    });
  });
});
