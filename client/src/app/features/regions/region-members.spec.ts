import { HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { ConfirmationService } from '@openng/optimus-ui/api';

import { RegionMemberPage, Role } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { RegionPageContext } from './region-page.context';
import { RegionMembersTab } from './tabs/region-members.tab';

const ZH = '00000010-0001-4000-8000-000000000040';
const USER_ID = '00000001-0001-4000-8000-000000000008';
const tick = () => new Promise((resolve) => setTimeout(resolve));

const PAGE: RegionMemberPage = {
  items: [
    {
      user: { id: USER_ID, displayName: 'Marco Bernasconi', role: Role.Foodsaver, verified: true },
      admin: false,
      verified: true,
      permissions: [],
      lastPickupAt: '2026-10-01T10:00:00Z',
    },
  ],
  page: { page: 0, size: 25, totalItems: 1, totalPages: 1 },
};

/** Members tab (R8) with the admin extras of the former users page for people who manage members (D1). */
describe('region members tab', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const settle = async () => {
    await tick();
    await harness.fixture.whenStable();
  };
  const members = (req: { url: string }) => req.url === `/api/regions/${ZH}/members`;

  async function open(manage: boolean): Promise<void> {
    const context = {
      regionId: signal(ZH),
      manageUsers: signal(manage),
      isAdmin: signal(manage),
      isOrga: signal(false),
      reload: () => undefined,
    };
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            {
              path: 'regions/:regionId/members',
              component: RegionMembersTab,
              providers: [{ provide: RegionPageContext, useValue: context }, ConfirmationService],
            },
            { path: '**', children: [] },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/regions/${ZH}/members`);
    await settle();
  }

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('managers get the ambassador filter, the last pickup and the admin link', async () => {
    await open(true);
    http.match((req) => req.url === `/api/regions/${ZH}/permissions`).forEach((req) => req.flush([]));
    const first = http.expectOne(members);
    expect(first.request.params.get('admin')).toBeNull();
    first.flush(PAGE);
    await settle();
    expect(q('[data-testid="regions.members.admin"]')).not.toBeNull();
    expect(q('[data-testid="regions.members.lastPickup"]')?.textContent).toContain('regions.members.lastPickup');
    expect(q('[data-testid="regions.members.open"]')?.getAttribute('href')).toBe(`/users/${USER_ID}`);

    const tab = harness.routeDebugElement!.componentInstance as { setAdminFilter(value: 'all' | 'yes' | 'no'): void };
    tab.setAdminFilter('yes');
    await settle();
    const filtered = http.expectOne(members);
    expect(filtered.request.params.get('admin')).toBe('true');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush({ items: [], page: { page: 0, size: 25, totalItems: 0, totalPages: 0 } });
  });

  it('plain members see none of the admin extras and never send the admin filter', async () => {
    await open(false);
    http.expectNone((req) => req.url === `/api/regions/${ZH}/permissions`);
    const first = http.expectOne(members);
    expect(first.request.params.get('admin')).toBeNull();
    first.flush(PAGE);
    await settle();
    expect(q('[data-testid="regions.members.member"]')).not.toBeNull();
    expect(q('[data-testid="regions.members.admin"]')).toBeNull();
    expect(q('[data-testid="regions.members.lastPickup"]')).toBeNull();
    expect(q('[data-testid="regions.members.open"]')).toBeNull();
    expect(q('a[href^="/users/"]')).toBeNull();
  });
});
