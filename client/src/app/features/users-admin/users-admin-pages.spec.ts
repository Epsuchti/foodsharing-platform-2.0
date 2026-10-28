import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { Language, Role, Theme, UserProfile } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { RegionUsersPageComponent } from './pages/region-users-page.component';
import { UserAdminPageComponent } from './pages/user-admin-page.component';

const REGION_ID = '00000000-0000-4000-8000-000000000101';
const USER_ID = '00000001-0001-4000-8000-000000000006';

const PROFILE: UserProfile = {
  id: USER_ID,
  email: 'foodsaver@local',
  displayName: 'Sarah Müller',
  language: Language.De,
  theme: Theme.System,
  role: Role.Foodsaver,
  verified: true,
  registeredAt: '2025-12-19T18:18:00Z',
  active: true,
};

describe('users admin pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'regions/:regionId/users', component: RegionUsersPageComponent },
            { path: 'users/:userId', component: UserAdminPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('user list: filters go to the members endpoint and reset the page', async () => {
    await harness.navigateByUrl(`/regions/${REGION_ID}/users`);
    http
      .expectOne(`/api/regions/${REGION_ID}`)
      .flush({ id: REGION_ID, name: 'Zürich', membership: { member: true, admin: true, homeRegion: true } });
    await settle();
    const first = http.expectOne((req) => req.url === `/api/regions/${REGION_ID}/members`);
    expect(first.request.params.get('verified')).toBeNull();
    first.flush({
      items: [{ user: { id: USER_ID, displayName: 'Sarah', role: Role.Foodsaver, verified: false }, admin: false, verified: false, permissions: [] }],
      page: { page: 0, size: 25, totalItems: 1, totalPages: 1 },
    });
    await settle();
    expect(q('[data-testid="usersAdmin.list.row"] a')?.getAttribute('href')).toBe(`/users/${USER_ID}`);

    const page = harness.routeDebugElement!.componentInstance as {
      setFilter<T>(filter: { set(value: T): void }, value: T): void;
      verified: { set(value: string): void };
    };
    page.setFilter(page.verified, 'no');
    await settle();
    const filtered = http.expectOne((req) => req.url === `/api/regions/${REGION_ID}/members`);
    expect(filtered.request.params.get('verified')).toBe('false');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush({ items: [], page: { page: 0, size: 25, totalItems: 0, totalPages: 0 } });
  });

  it('user list: members without user management get the forbidden state, no member request', async () => {
    await harness.navigateByUrl(`/regions/${REGION_ID}/users`);
    http
      .expectOne(`/api/regions/${REGION_ID}`)
      .flush({ id: REGION_ID, name: 'Zürich', membership: { member: true, admin: false, homeRegion: true } });
    await settle();
    http.expectNone((req) => req.url === `/api/regions/${REGION_ID}/members`);
    expect(q('[data-testid="usersAdmin.list.state"]')?.getAttribute('data-state')).toBe('forbidden');
  });

  it('user page: disabling needs a reason and updates the status', async () => {
    await harness.navigateByUrl(`/users/${USER_ID}`);
    http.expectOne(`/api/admin/users/${USER_ID}/profile`).flush(PROFILE);
    await settle();
    http.match((req) => req.url.endsWith('/change-history') || req.url.includes('/history/actions')).forEach((req) =>
      req.flush(req.request.url.endsWith('/change-history') ? { items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } } : []),
    );
    await settle();

    (q('[data-testid="usersAdmin.user.toggleActive"] button') as HTMLButtonElement).click();
    await settle();
    const confirm = document.querySelector('[data-testid="usersAdmin.user.activeDialog.confirm"] button') as HTMLButtonElement;
    confirm.click();
    await settle();
    http.expectNone(`/api/admin/users/${USER_ID}/active`);

    const textarea = document.querySelector('[data-testid="usersAdmin.user.activeDialog.message"]') as HTMLTextAreaElement;
    textarea.value = 'Mehrfach nicht erschienen';
    textarea.dispatchEvent(new Event('input'));
    await settle();
    confirm.click();
    const request = http.expectOne(`/api/admin/users/${USER_ID}/active`);
    expect(request.request.body).toEqual({ active: false, reason: 'Mehrfach nicht erschienen' });
    request.flush({ ...PROFILE, active: false });
    await settle();
    expect(q('[data-testid="usersAdmin.user.inactive"]')).not.toBeNull();
  });
});
