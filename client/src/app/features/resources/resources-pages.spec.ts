import { HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode, Language, Resource, ResourceOpenness, Role, Theme } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { RegionPageContext } from '../regions/region-page.context';
import { FavouriteResourcesPageComponent } from './pages/favourite-resources-page.component';
import { MyResourcesPageComponent } from './pages/my-resources-page.component';
import { RegionResourcesTab } from './tabs/region-resources.tab';

const ME = '00000001-0001-4000-8000-000000000006';
const ZH = '00000010-0001-4000-8000-000000000040';
const TOOLS = { id: 'c1', name: 'Werkzeug' };
const KITCHEN = { id: 'c2', name: 'Küche' };
const owner = { id: ME, displayName: 'Sarah Müller', role: Role.Foodsaver, verified: true };

function resource(overrides: Partial<Resource> = {}): Resource {
  return {
    id: 'r1',
    name: 'Lastenvelo',
    categories: [TOOLS],
    owner,
    private: false,
    openness: ResourceOpenness.AskFirst,
    images: [],
    favourite: false,
    mayEdit: true,
    mayDelete: true,
    ...overrides,
  };
}

describe('resource pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector) as HTMLElement | null;
  const all = (selector: string) => Array.from(harness.routeNativeElement!.querySelectorAll(selector)) as HTMLElement[];
  const settle = () => harness.fixture.whenStable();
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const click = (selector: string) => ((q(`${selector} button`) ?? q(selector)) as HTMLElement).click();
  const flushRegion = async (resources: Resource[], permissions = { mayAdd: true, mayEditCommons: false }) => {
    http.expectOne(`/api/regions/${ZH}/resources`).flush(resources);
    http.expectOne(`/api/regions/${ZH}/resources/permissions`).flush(permissions);
    await new Promise((resolve) => setTimeout(resolve));
    await settle();
  };
  /** /resources/mine loads the list and `getMyResourceLimits` together. */
  const flushMine = (resources: Resource[], maxPerUser = 10) => {
    http.expectOne('/api/resources/mine').flush(resources);
    http
      .expectOne('/api/resources/mine/limits')
      .flush({ ownCount: resources.length, maxPerUser, mayAdd: resources.length < maxPerUser });
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            {
              path: 'regions/:regionId/resources',
              component: RegionResourcesTab,
              providers: [
                {
                  provide: RegionPageContext,
                  useValue: { regionId: signal(ZH), data: signal({ id: ZH, name: 'Zürich Stadt' }) },
                },
              ],
            },
            { path: 'resources/mine', component: MyResourcesPageComponent },
            { path: 'resources/favourites', component: FavouriteResourcesPageComponent },
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

  describe('region tab', () => {
    it('lists commons and members’ resources, filters by category and toggles the favourite star', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/resources`);
      expect(state('resources.region.state')).toBe('loading');
      await flushRegion([
        resource({ id: 'c', name: 'Kühlbox', owner: undefined, categories: [KITCHEN], mayEdit: false, mayDelete: false }),
        resource({ id: 'a', categories: [TOOLS] }),
      ]);
      expect(state('resources.region.state')).toBe('success');
      expect(q('[data-testid="resources.region.commons"] [data-testid="resources.card"]')?.getAttribute('data-resource-id')).toBe('c');
      expect(q('[data-testid="resources.region.members"] [data-testid="resources.card"]')?.getAttribute('data-resource-id')).toBe('a');
      expect(q('[data-testid="resources.region.add"]')).not.toBeNull();
      expect(q('[data-testid="resources.region.limit"]')).toBeNull();

      click('[data-testid="resources.region.category"][data-category-id="c2"]');
      await settle();
      expect(all('[data-testid="resources.card"]').map((card) => card.getAttribute('data-resource-id'))).toEqual(['c']);

      click('[data-resource-id="c"] [data-testid="resources.card.favourite"]');
      await settle();
      const put = http.expectOne('/api/resources/c/favourite');
      expect(put.request.method).toBe('PUT');
      put.flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      // reload after the change
      await flushRegion([resource({ id: 'c', name: 'Kühlbox', owner: undefined, categories: [KITCHEN], favourite: true }), resource({ id: 'a' })]);
      expect(q('[data-resource-id="c"]')?.getAttribute('data-favourite')).toBe('true');
    });

    it('hints at the limit when nothing more may be added', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/resources`);
      await flushRegion([resource()], { mayAdd: false, mayEditCommons: false });
      expect(q('[data-testid="resources.region.add"]')).toBeNull();
      expect(q('[data-testid="resources.region.limit"]')).not.toBeNull();
    });

    it('shows the empty state, the forbidden state of regions without resources and the error state with retry', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/resources`);
      await flushRegion([]);
      expect(state('resources.region.state')).toBe('empty');

      await harness.navigateByUrl('/resources/favourites');
      http.expectOne('/api/resources/favourites').flush([]);
      await harness.navigateByUrl(`/regions/${ZH}/resources`);
      http.expectOne(`/api/regions/${ZH}/resources`).flush(apiHttpError(403, ApiErrorCode.ResourcesNotAvailable).error, {
        status: 403,
        statusText: 'Forbidden',
      });
      http.match(`/api/regions/${ZH}/resources/permissions`).forEach((req) => req.flush({ mayAdd: true, mayEditCommons: false }));
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      // a 403 refreshes the session (error interceptor)
      http.match('/api/auth/session').forEach((request) => request.flush(TestBed.inject(SessionService).session()));
      await settle();
      expect(state('resources.region.state')).toBe('forbidden');
    });

    it('shows the error state with retry', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/resources`);
      http.expectOne(`/api/regions/${ZH}/resources`).flush(null, { status: 500, statusText: 'Server Error' });
      http.match(`/api/regions/${ZH}/resources/permissions`).forEach((req) => req.flush({ mayAdd: true, mayEditCommons: false }));
      await new Promise((resolve) => setTimeout(resolve));
      await settle();
      expect(state('resources.region.state')).toBe('error');
      click('[data-testid="resources.region.state"] [data-testid$=".retry"]');
      await settle();
      await flushRegion([resource()]);
      expect(state('resources.region.state')).toBe('success');
    });
  });

  describe('my resources', () => {
    it('counts against the server limit and disables adding at the limit', async () => {
      await harness.navigateByUrl('/resources/mine');
      expect(state('resources.mine.state')).toBe('loading');
      expect(q('[data-testid="resources.mine.add"] button')?.hasAttribute('disabled')).toBe(true);
      flushMine([resource()]);
      await settle();
      expect(state('resources.mine.state')).toBe('success');
      expect(q('[data-testid="resources.mine.count"]')?.getAttribute('data-count')).toBe('1');
      expect(q('[data-testid="resources.mine.count"]')?.getAttribute('data-max')).toBe('10');
      expect(q('[data-testid="resources.mine.add"] button')?.hasAttribute('disabled')).toBe(false);

      await harness.navigateByUrl('/resources/favourites');
      http.expectOne('/api/resources/favourites').flush([]);
      await harness.navigateByUrl('/resources/mine');
      flushMine(Array.from({ length: 10 }, (_, i) => resource({ id: `r${i}` })));
      await settle();
      expect(q('[data-testid="resources.mine.add"] button')?.hasAttribute('disabled')).toBe(true);
      expect(q('[data-testid="resources.mine.count"]')?.className).toContain('count--full');

      // the limit is the configured one (app.resources.max-per-user), not a client constant
      await harness.navigateByUrl('/resources/favourites');
      http.expectOne('/api/resources/favourites').flush([]);
      await harness.navigateByUrl('/resources/mine');
      flushMine([resource(), resource({ id: 'r2' }), resource({ id: 'r3' })], 3);
      await settle();
      expect(q('[data-testid="resources.mine.count"]')?.getAttribute('data-max')).toBe('3');
      expect(q('[data-testid="resources.mine.add"] button')?.hasAttribute('disabled')).toBe(true);
    });

    it('adds a resource through the form dialog', async () => {
      await harness.navigateByUrl('/resources/mine');
      flushMine([]);
      await settle();
      expect(state('resources.mine.state')).toBe('empty');
      click('[data-testid="resources.mine.add"]');
      await settle();
      http.expectOne((req) => req.url === '/api/resources/categories').flush([TOOLS, KITCHEN]);
      await settle();
      const name = document.querySelector('[data-testid="resources.form.name"]') as HTMLInputElement;
      name.value = 'Einkochtopf';
      name.dispatchEvent(new Event('input'));
      // without a category the form does not submit
      (document.querySelector('[data-testid="resources.form.submit"] button') as HTMLButtonElement).click();
      await settle();
      expect(document.querySelector('[data-testid="resources.form.categoryRequired"]')).not.toBeNull();
      http.expectNone('/api/resources');
      (document.querySelector('[data-testid="resources.form.category"][data-category-id="c2"]') as HTMLButtonElement).click();
      (document.querySelector('[data-testid="resources.form.openness"][data-value="GIVE_AWAY"]') as HTMLInputElement).click();
      await settle();
      (document.querySelector('[data-testid="resources.form.submit"] button') as HTMLButtonElement).click();
      await settle();
      const post = http.expectOne('/api/resources');
      expect(post.request.body).toEqual({
        name: 'Einkochtopf',
        categoryIds: ['c2'],
        private: false,
        openness: 'GIVE_AWAY',
        commons: false,
        imageUploadIds: [],
      });
      post.flush(resource({ id: 'new', name: 'Einkochtopf' }));
      await settle();
      flushMine([resource({ id: 'new', name: 'Einkochtopf' })]);
      await settle();
      expect(state('resources.mine.state')).toBe('success');
    });

    it('shows the error state', async () => {
      await harness.navigateByUrl('/resources/mine');
      http.expectOne('/api/resources/mine').flush(null, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(state('resources.mine.state')).toBe('error');
      expect(q('[data-testid="resources.mine.add"] button')?.hasAttribute('disabled')).toBe(true);
    });
  });

  describe('favourites', () => {
    it('lists favourites, shows the empty and the error state', async () => {
      await harness.navigateByUrl('/resources/favourites');
      expect(state('resources.favourites.state')).toBe('loading');
      http.expectOne('/api/resources/favourites').flush([resource({ favourite: true, mayEdit: false, mayDelete: false })]);
      await settle();
      expect(state('resources.favourites.state')).toBe('success');
      expect(q('[data-testid="resources.card.edit"]')).toBeNull();

      click('[data-testid="resources.card.favourite"]');
      await settle();
      const del = http.expectOne('/api/resources/r1/favourite');
      expect(del.request.method).toBe('DELETE');
      del.flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      http.expectOne('/api/resources/favourites').flush([]);
      await settle();
      expect(state('resources.favourites.state')).toBe('empty');

      await harness.navigateByUrl('/resources/mine');
      flushMine([]);
      await harness.navigateByUrl('/resources/favourites');
      http.expectOne('/api/resources/favourites').flush(null, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(state('resources.favourites.state')).toBe('error');
    });
  });
});
