import { HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode, Fsp, FspStatus, FspSummary, Language, Role, Theme, UnitType } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { RegionPageContext } from '../regions/region-page.context';
import { FspFormPageComponent } from './pages/fsp-form-page.component';
import { FspPageComponent } from './pages/fsp-page.component';
import { MyFspsPageComponent } from './pages/my-fsps-page.component';
import { RegionFspsTab } from './tabs/region-fsps.tab';

const ME = '00000001-0001-4000-8000-000000000006';
const BIEB = '00000001-0001-4000-8000-000000000009';
const ZH = '00000010-0001-4000-8000-000000000040';
const F1 = '00000049-0001-4000-8000-000000000001';
const F5 = '00000049-0001-4000-8000-000000000005';

function summary(overrides: Partial<FspSummary> = {}): FspSummary {
  return { id: F1, name: 'Fairteiler Wiedikon', city: 'Zürich', status: FspStatus.Active, followerCount: 3, following: false, ...overrides };
}

function fsp(overrides: Partial<Fsp> = {}): Fsp {
  return {
    id: F1,
    name: 'Fairteiler Wiedikon',
    description: 'Kühlschrank im Hinterhof.',
    region: { id: ZH, slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.BigCity },
    address: { street: 'Schlossgasse 10', postcode: '8003', city: 'Zürich' },
    lat: 47.3721,
    lon: 8.5172,
    status: FspStatus.Active,
    managers: [{ id: BIEB, displayName: 'Nina Schneider', role: Role.StoreManager, verified: true }],
    createdAt: '2026-01-10T10:00:00Z',
    followerCount: 3,
    following: { following: false, bell: false, mail: false },
    permissions: { mayEdit: false, mayDelete: false, mayActivate: false, mayPost: true, mayFollow: true },
    ...overrides,
  };
}

const EMPTY_WALL = { items: [], page: { number: 0, size: 50, totalElements: 0, totalPages: 0 } };

describe('food share point pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const member = signal(true);
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const all = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const settle = () => harness.fixture.whenStable();
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const click = (selector: string) => ((q(`${selector} button`) ?? q(selector)) as HTMLElement).click();
  const type = (testId: string, value: string) => {
    const input = q(`[data-testid="${testId}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const retry = (testId: string) => (q(`[data-testid="${testId}"] [data-testid$=".retry"] button`) as HTMLButtonElement).click();
  const serverError = { status: 500, statusText: 'Server Error' };

  beforeEach(async () => {
    member.set(true);
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'foodSharePoints.card.followers': '{count} followers' },
          routes: [
            {
              path: 'regions/:regionId/food-share-points',
              component: RegionFspsTab,
              providers: [
                {
                  provide: RegionPageContext,
                  useValue: { regionId: signal(ZH), isMember: member, isOrga: signal(false) },
                },
              ],
            },
            { path: 'food-share-points/mine', component: MyFspsPageComponent },
            { path: 'food-share-points/new', component: FspFormPageComponent },
            { path: 'food-share-points/:fspId/edit', component: FspFormPageComponent },
            { path: 'food-share-points/:fspId', component: FspPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: { id: ME, email: 'foodsaver@local', displayName: 'Sarah', role: Role.Foodsaver, language: Language.De, theme: Theme.System, verified: true, homeRegionId: ZH },
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const regionList = (req: { url: string }) => req.url === `/api/regions/${ZH}/food-share-points`;
  const markers = '/api/public/map/markers/food-share-points';

  describe('region tab', () => {
    it('lists the FSPs including inactive ones, with pins for all of them from their own coordinates', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/food-share-points`);
      expect(state('foodSharePoints.region.state')).toBe('loading');
      const list = http.expectOne(regionList);
      expect(list.request.params.get('includeInactive')).toBe('true');
      list.flush([
        summary({ lat: 47.3721, lon: 8.5172 }),
        summary({ id: F5, name: 'Fairteiler Altstetten', status: FspStatus.Inactive, followerCount: 1, lat: 47.3874, lon: 8.4867 }),
      ]);
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      http.expectNone(markers);
      expect(state('foodSharePoints.region.state')).toBe('success');
      expect(all('fs-map-view .fs-visually-hidden li').length).toBe(2);
      expect(all('[data-testid="foodSharePoints.card"]').length).toBe(2);
      expect(q(`fs-fsp-card[data-fsp-id="${F5}"] [data-testid="foodSharePoints.card.inactive"]`)).not.toBeNull();
      expect(q(`fs-fsp-card[data-fsp-id="${F1}"] [data-testid="foodSharePoints.card.inactive"]`)).toBeNull();
      expect(q('[data-testid="foodSharePoints.region.add"]')).not.toBeNull();
    });

    it('shows the empty state without the propose button for non-members', async () => {
      member.set(false);
      await harness.navigateByUrl(`/regions/${ZH}/food-share-points`);
      http.expectOne(regionList).flush([]);
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      http.expectNone(markers);
      expect(state('foodSharePoints.region.state')).toBe('empty');
      expect(q('[data-testid="foodSharePoints.region.add"]')).toBeNull();
    });

    it('retries a failed list; an FSP without a position gets no pin', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/food-share-points`);
      http.expectOne(regionList).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      expect(state('foodSharePoints.region.state')).toBe('error');
      retry('foodSharePoints.region.state');
      await settle();
      http.expectOne(regionList).flush([summary()]);
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      expect(state('foodSharePoints.region.state')).toBe('success');
      expect(all('[data-testid="foodSharePoints.card"]').length).toBe(1);
      expect(all('fs-map-view .fs-visually-hidden li').length).toBe(0);
    });
  });

  describe('/food-share-points/:id', () => {
    async function open(detail: Fsp) {
      await harness.navigateByUrl(`/food-share-points/${detail.id}`);
      expect(state('foodSharePoints.detail.state')).toBe('loading');
      http.expectOne(`/api/food-share-points/${detail.id}`).flush(detail);
      await settle();
      http.expectOne((req) => req.url === `/api/food-share-points/${detail.id}/wall`).flush(EMPTY_WALL);
      await settle();
    }

    it('shows address links and follows with bell and mail', async () => {
      await open(fsp());
      expect(state('foodSharePoints.detail.state')).toBe('success');
      expect(q('[data-testid="foodSharePoints.detail.addressText"]')?.textContent).toContain('Schlossgasse 10');
      expect(q('[data-testid="foodSharePoints.detail.googleMaps"]')?.getAttribute('href')).toContain('query=47.3721,8.5172');
      expect(q('[data-testid="foodSharePoints.detail.edit"]')).toBeNull();
      expect(q('[data-testid="foodSharePoints.detail.activate"]')).toBeNull();
      expect(q('[data-testid="foodSharePoints.managers.edit"]')).toBeNull();

      // the mail switch is a draft until "follow"
      click('[data-testid="foodSharePoints.follow.mail"] input');
      await settle();
      http.expectNone((req) => req.url.endsWith('/follow'));
      click('[data-testid="foodSharePoints.follow.submit"]');
      const follow = http.expectOne((req) => req.method === 'PUT' && req.url === `/api/food-share-points/${F1}/follow`);
      expect(follow.request.body).toEqual({ bell: true, mail: true });
      follow.flush({ following: true, bell: true, mail: true });
      await settle();
      http.expectOne(`/api/food-share-points/${F1}`).flush(fsp({ following: { following: true, bell: true, mail: true }, followerCount: 4 }));
      await settle();
      expect(q('[data-testid="foodSharePoints.detail.follow"]')?.getAttribute('data-following')).toBe('true');

      // following: a switch change is saved at once
      click('[data-testid="foodSharePoints.follow.bell"] input');
      const change = http.expectOne((req) => req.method === 'PUT' && req.url === `/api/food-share-points/${F1}/follow`);
      expect(change.request.body).toEqual({ bell: false, mail: true });
      change.flush({ following: true, bell: false, mail: true });
      await settle();
      http.expectOne(`/api/food-share-points/${F1}`).flush(fsp({ following: { following: true, bell: false, mail: true } }));
      await settle();

      click('[data-testid="foodSharePoints.follow.unfollow"]');
      http.expectOne((req) => req.method === 'DELETE' && req.url === `/api/food-share-points/${F1}/follow`).flush(null);
      await settle();
      http.expectOne(`/api/food-share-points/${F1}`).flush(fsp());
      await settle();
      expect(q('[data-testid="foodSharePoints.follow.submit"]')).not.toBeNull();
    });

    it('lets an FSP admin activate an inactive FSP and replace the managers', async () => {
      const admin = fsp({
        id: F5,
        name: 'Fairteiler Altstetten',
        status: FspStatus.Inactive,
        managers: [
          { id: ME, displayName: 'Sarah', role: Role.Foodsaver, verified: true },
          { id: BIEB, displayName: 'Nina Schneider', role: Role.StoreManager, verified: true },
        ],
        permissions: { mayEdit: true, mayDelete: true, mayActivate: true, mayPost: true, mayFollow: false },
      });
      await open(admin);
      expect(q('[data-testid="foodSharePoints.detail.inactive"]')).not.toBeNull();
      // inactive: nobody can start following
      expect(q('[data-testid="foodSharePoints.detail.follow"]')).toBeNull();
      click('[data-testid="foodSharePoints.detail.activate"]');
      const status = http.expectOne((req) => req.method === 'PUT' && req.url === `/api/food-share-points/${F5}/status`);
      expect(status.request.body).toEqual({ status: 'ACTIVE' });
      status.flush({ ...admin, status: FspStatus.Active });
      await settle();
      const active = { ...admin, status: FspStatus.Active, permissions: { ...admin.permissions, mayFollow: true } };
      http.expectOne(`/api/food-share-points/${F5}`).flush(active);
      await settle();
      expect(q('[data-testid="foodSharePoints.detail.inactive"]')).toBeNull();

      click('[data-testid="foodSharePoints.managers.edit"]');
      await settle();
      expect(all('[data-testid="foodSharePoints.managers.draftItem"]').length).toBe(2);
      (q(`[data-testid="foodSharePoints.managers.draftItem"][data-user-id="${BIEB}"] [data-testid="foodSharePoints.managers.remove"] button`) as HTMLButtonElement).click();
      await settle();
      expect(all('[data-testid="foodSharePoints.managers.draftItem"]').length).toBe(1);
      // the last manager cannot be removed
      expect((q('[data-testid="foodSharePoints.managers.remove"] button') as HTMLButtonElement).disabled).toBe(true);
      click('[data-testid="foodSharePoints.managers.save"]');
      const save = http.expectOne((req) => req.method === 'PUT' && req.url === `/api/food-share-points/${F5}/managers`);
      expect(save.request.body).toEqual({ userIds: [ME] });
      save.flush({ ...active, managers: [active.managers[0]] });
      await settle();
      http.expectOne(`/api/food-share-points/${F5}`).flush({ ...active, managers: [active.managers[0]] });
      await settle();
      expect(all('[data-testid="foodSharePoints.managers.item"]').length).toBe(1);
    });

    it('renders not found for a hidden or unknown FSP', async () => {
      await harness.navigateByUrl(`/food-share-points/${F5}`);
      http.expectOne(`/api/food-share-points/${F5}`).flush(apiHttpError(404, ApiErrorCode.FspNotFound).error, { status: 404, statusText: 'Not Found' });
      await settle();
      expect(state('foodSharePoints.detail.state')).toBe('notFound');
    });
  });

  describe('/food-share-points/mine', () => {
    it('lists followed FSPs and retries after an error', async () => {
      await harness.navigateByUrl('/food-share-points/mine');
      expect(state('foodSharePoints.mine.state')).toBe('loading');
      http.expectOne('/api/food-share-points/mine').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      await settle();
      expect(state('foodSharePoints.mine.state')).toBe('error');
      retry('foodSharePoints.mine.state');
      await settle();
      http.expectOne('/api/food-share-points/mine').flush([summary({ following: true })]);
      await settle();
      expect(state('foodSharePoints.mine.state')).toBe('success');
      expect(q('[data-testid="foodSharePoints.card.following"]')).not.toBeNull();
      expect(q('[data-testid="foodSharePoints.mine.region"]')).not.toBeNull();
    });
  });

  describe('form', () => {
    it('without a region the create form is not found', async () => {
      await harness.navigateByUrl('/food-share-points/new');
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      expect(state('foodSharePoints.form.state')).toBe('notFound');
    });

    it('proposes an FSP in the region and opens it', async () => {
      await harness.navigateByUrl(`/food-share-points/new?regionId=${ZH}`);
      http.expectOne(`/api/regions/${ZH}`).flush({ id: ZH, slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.BigCity });
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      expect(state('foodSharePoints.form.state')).toBe('success');
      expect(q('[data-testid="foodSharePoints.form.activationNote"]')).not.toBeNull();

      // required fields are explained instead of sent
      click('[data-testid="foodSharePoints.form.submit"]');
      await settle();
      expect(q('#ff-name-hint .error')).not.toBeNull();
      http.expectNone((req) => req.method === 'POST');

      type('foodSharePoints.form.name', ' Fairteiler Albisrieden ');
      type('foodSharePoints.form.street', 'Albisriederstrasse 300');
      type('foodSharePoints.form.postcode', '8047');
      type('foodSharePoints.form.city', 'Zürich');
      await settle();
      click('[data-testid="foodSharePoints.form.submit"]');
      const create = http.expectOne((req) => req.method === 'POST' && req.url === `/api/regions/${ZH}/food-share-points`);
      expect(create.request.body).toMatchObject({ name: 'Fairteiler Albisrieden', street: 'Albisriederstrasse 300', postcode: '8047', city: 'Zürich' });
      expect(create.request.body.lat).toBeUndefined();
      create.flush(fsp({ id: 'new-id', status: FspStatus.Inactive }));
      await settle();
      http.expectOne('/api/food-share-points/new-id').flush(fsp({ id: 'new-id', status: FspStatus.Inactive }));
      await settle();
      expect(state('foodSharePoints.detail.state')).toBe('success');
      http.expectOne((req) => req.url === '/api/food-share-points/new-id/wall').flush(EMPTY_WALL);
    });

    it('edits an FSP with its current values', async () => {
      await harness.navigateByUrl(`/food-share-points/${F1}/edit`);
      http.expectOne(`/api/food-share-points/${F1}`).flush(fsp({ permissions: { mayEdit: true, mayDelete: false, mayActivate: false, mayPost: true, mayFollow: true } }));
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      expect((q('[data-testid="foodSharePoints.form.name"]') as HTMLInputElement).value).toBe('Fairteiler Wiedikon');
      expect(q('[data-testid="foodSharePoints.form.position"]')).not.toBeNull();
      type('foodSharePoints.form.description', 'Neu: Kühlschrank und Regal.');
      await settle();
      click('[data-testid="foodSharePoints.form.submit"]');
      const update = http.expectOne((req) => req.method === 'PATCH' && req.url === `/api/food-share-points/${F1}`);
      expect(update.request.body).toMatchObject({ description: 'Neu: Kühlschrank und Regal.', lat: 47.3721, lon: 8.5172 });
      update.flush(fsp());
      await settle();
      http.expectOne(`/api/food-share-points/${F1}`).flush(fsp());
      await settle();
      http.expectOne((req) => req.url === `/api/food-share-points/${F1}/wall`).flush(EMPTY_WALL);
    });

    it('explains that an FSP without edit rights cannot be edited', async () => {
      await harness.navigateByUrl(`/food-share-points/${F1}/edit`);
      http.expectOne(`/api/food-share-points/${F1}`).flush(fsp());
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      expect(q('[data-testid="foodSharePoints.form.notEditable"]')).not.toBeNull();
      expect(q('[data-testid="foodSharePoints.form"]')).toBeNull();
    });
  });
});
