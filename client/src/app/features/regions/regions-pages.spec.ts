import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import {
  ApplyType,
  GroupApplicationStatus,
  GroupCategory,
  IntroPickupCategory,
  RegionJoinMode,
  RegionSettings,
  Role,
  UnitMembership,
  UnitType,
  WorkingGroupDetail,
} from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { FindRegionPageComponent } from './pages/find-region-page.component';
import { GroupPageComponent } from './pages/group-page.component';
import { MyUnitsPageComponent } from './pages/my-units-page.component';
import { PublicRegionPageComponent } from './pages/public-region-page.component';
import { RegionPageContext } from './region-page.context';
import { RegionSettingsTab } from './tabs/region-settings.tab';

const REGION_ID = '00000000-0000-4000-8000-000000000101';
const GROUP_ID = '00000000-0000-4000-8000-000000000201';

const unit = (over: Partial<UnitMembership>): UnitMembership => ({
  id: over.name!,
  name: 'x',
  slug: 'x',
  type: UnitType.City,
  member: true,
  admin: false,
  homeRegion: false,
  ...over,
});

const group = (over: Partial<WorkingGroupDetail> = {}): WorkingGroupDetail => ({
  id: GROUP_ID,
  name: 'Begrüssungsteam',
  slug: 'begruessung',
  category: GroupCategory.Administrative,
  applyType: ApplyType.Everybody,
  memberCount: 3,
  admins: [],
  membership: { member: false, admin: false, applied: false },
  mayApply: true,
  mayJoin: false,
  mayEdit: false,
  requirements: { minPickups: 5, minBananas: 0, minWeeks: 4 },
  requirementCheck: { pickupsOk: true, bananasOk: true, weeksOk: false, pickups: 7, weeks: 2 },
  subGroups: [],
  ...over,
});

const user = (id: string, name: string) => ({ id, displayName: name, role: Role.Foodsaver, verified: true });

describe('regions pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const qa = (selector: string) => [...harness.routeNativeElement!.querySelectorAll(selector)];
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'regions/mine', component: MyUnitsPageComponent },
            { path: 'groups/:groupId', component: GroupPageComponent },
            { path: 'r/:slug', component: PublicRegionPageComponent },
            { path: 'find-region', component: FindRegionPageComponent },
            { path: 'regions/:regionId/settings', component: RegionSettingsTab },
            { path: '**', children: [] },
          ],
        }),
        provideOptimus(),
      ],
    });
    // the region page provides the context to its tabs; here the tab provides it itself
    TestBed.overrideComponent(RegionSettingsTab, { add: { providers: [RegionPageContext] } });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('my units: home region first, working groups separately', async () => {
    await harness.navigateByUrl('/regions/mine');
    http.expectOne('/api/regions/mine').flush([
      unit({ name: 'Bern', slug: 'bern' }),
      unit({ name: 'Zürich', slug: 'zuerich', homeRegion: true }),
      unit({ name: 'Begrüssung', type: UnitType.WorkingGroup, admin: true }),
    ]);
    await settle();
    const regions = qa('[data-testid="regions.mine.region"] .unit__name').map((el) => el.textContent);
    expect(regions).toEqual(['Zürich', 'Bern']);
    expect(qa('[data-testid="regions.mine.group"]')).toHaveLength(1);
  });

  it('group page: requirements checklist; a missing requirement hides apply; no member list', async () => {
    await harness.navigateByUrl(`/groups/${GROUP_ID}`);
    http.expectOne(`/api/groups/${GROUP_ID}`).flush(group());
    await settle();
    const rows = qa('[data-testid="regions.group.requirements"] li');
    expect(rows.map((row) => row.getAttribute('data-requirement'))).toEqual(['minPickups', 'minWeeks']);
    expect(rows[1].classList.contains('missing')).toBe(true);
    expect(q('[data-testid="regions.group.apply"]')).toBeNull();
    expect(q('[data-testid="regions.group.members"]')).toBeNull();
    http.expectNone(`/api/groups/${GROUP_ID}/members`);
  });

  it('group page: apply is offered when all requirements are met', async () => {
    await harness.navigateByUrl(`/groups/${GROUP_ID}`);
    http.expectOne(`/api/groups/${GROUP_ID}`).flush(
      group({ requirementCheck: { pickupsOk: true, bananasOk: true, weeksOk: true, pickups: 7, weeks: 6 } }),
    );
    await settle();
    expect(q('[data-testid="regions.group.apply"]')).not.toBeNull();
  });

  it('group page: admins see applications and decline with a message', async () => {
    await harness.navigateByUrl(`/groups/${GROUP_ID}`);
    http
      .expectOne(`/api/groups/${GROUP_ID}`)
      .flush(group({ mayEdit: true, membership: { member: true, admin: true, applied: false } }));
    await settle();
    http.expectOne(`/api/groups/${GROUP_ID}/members`).flush([{ user: user('u1', 'Anna'), admin: true }]);
    http.expectOne((req) => req.url === `/api/groups/${GROUP_ID}/applications`).flush([
      { id: 'a1', user: user('u2', 'Ben'), text: 'Hallo', status: GroupApplicationStatus.Pending, createdAt: '2026-10-20T10:00:00Z' },
    ]);
    await settle();
    expect(qa('[data-testid="regions.group.application"]')).toHaveLength(1);

    (q('[data-testid="regions.group.decline"] button') as HTMLButtonElement).click();
    await settle();
    const textarea = document.querySelector('[data-testid="regions.group.declineDialog.message"]') as HTMLTextAreaElement;
    textarea.value = 'Leider voll';
    textarea.dispatchEvent(new Event('input'));
    await settle();
    (document.querySelector('[data-testid="regions.group.declineDialog.confirm"] button') as HTMLButtonElement).click();
    const decision = http.expectOne(`/api/groups/${GROUP_ID}/applications/a1/decision`);
    expect(decision.request.body).toEqual({ accept: false, message: 'Leider voll' });
  });

  it('public region page: only an active pin becomes a marker', async () => {
    await harness.navigateByUrl('/r/zuerich');
    http.expectOne('/api/public/regions/zuerich').flush({
      id: REGION_ID,
      slug: 'zuerich',
      name: 'Zürich',
      type: UnitType.City,
      ambassadorNames: ['Anna'],
      children: [],
      memberCount: 12,
      storeCount: 4,
      nextIntroPickups: [],
      pin: { latitude: 47.37, longitude: 8.54, active: false },
    });
    await settle();
    expect(q('[data-testid="regions.public.name"]')?.textContent).toBe('Zürich');
    expect(q('[data-testid="regions.public.map"]')).toBeNull();
    expect(q('[data-testid="regions.public.signup"]')).not.toBeNull();
  });

  it('find region: visitors land on the public page', async () => {
    const page = (await harness.navigateByUrl('/find-region', FindRegionPageComponent)) as unknown as {
      open(region: { id: string; slug: string; name: string; type: UnitType }): void;
    };
    page.open({ id: REGION_ID, slug: 'zuerich', name: 'Zürich', type: UnitType.City });
    await settle();
    expect(TestBed.inject(Router).url).toBe('/r/zuerich');
    http.match(() => true).forEach((req) => req.flush(null));
  });

  it('settings tab: saves steps in their new order with sortOrder', async () => {
    const settings: RegionSettings = {
      introPickups: {
        maxActiveBookingsPerUser: 3,
        preventDuplicateGuideBookings: true,
        preventDuplicateCategoryBookings: true,
        requireFairteilerVisitByThirdIntroPickup: false,
        ignoredCategories: [IntroPickupCategory.Online],
        cleaningStoreId: 'store-1',
      },
      onboarding: {
        learningPagesMandatory: true,
        extraSteps: [
          { id: 's2', key: 'SECOND', titles: { de: 'Zwei' }, sortOrder: 2 },
          { id: 's1', key: 'FIRST', titles: { de: 'Eins' }, sortOrder: 1 },
        ],
      },
      joinMode: RegionJoinMode.Open,
      timeZone: 'Europe/Zurich',
    };
    await harness.navigateByUrl(`/regions/${REGION_ID}/settings`);
    http.match(`/api/regions/${REGION_ID}`).forEach((req) => req.flush(null));
    http.expectOne(`/api/regions/${REGION_ID}/settings`).flush(settings);
    await settle();
    expect(qa('[data-testid="regions.settings.step"]')).toHaveLength(2);

    // move SECOND up
    const second = qa('[data-testid="regions.settings.step"]')[1];
    (second.querySelector('button') as HTMLButtonElement).click();
    await settle();
    (q('[data-testid="regions.settings.save"] button') as HTMLButtonElement).click();
    const save = http.expectOne((req) => req.method === 'PATCH' && req.url === `/api/regions/${REGION_ID}/settings`);
    const body = save.request.body;
    expect(body.onboarding.extraSteps.map((s: { key: string; sortOrder: number }) => [s.key, s.sortOrder])).toEqual([
      ['SECOND', 1],
      ['FIRST', 2],
    ]);
    // fields the form does not edit are kept
    expect(body.introPickups.cleaningStoreId).toBe('store-1');
    expect(body.introPickups.ignoredCategories).toEqual([IntroPickupCategory.Online]);
  });
});
