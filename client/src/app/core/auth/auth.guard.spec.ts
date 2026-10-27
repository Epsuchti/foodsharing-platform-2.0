import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree, convertToParamMap } from '@angular/router';

import { Language, Permission, Role, Theme } from '../../api';
import { provideDataStateTesting } from '../testing/data-state-testing';
import { hasScopedPermission, parseScopedPermission, routeAccess } from './auth.guard';
import { consentGuard, isConsentExempt } from './consent.guard';
import { SessionService } from './session.service';

const REGION = '00000010-0001-4000-8000-000000000040';
const OTHER = '00000010-0001-4000-8000-000000000041';

function route(params: Record<string, string>, parent?: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
  return { paramMap: convertToParamMap(params), parent: parent ?? null } as unknown as ActivatedRouteSnapshot;
}

describe('scoped permissions (A10)', () => {
  it('parses scoped entries', () => {
    expect(parseScopedPermission(`CAN_MANAGE_USERS@${REGION}`)).toEqual({ permission: 'CAN_MANAGE_USERS', scope: REGION });
    expect(parseScopedPermission('CAN_MANAGE_USERS@*')).toEqual({ permission: 'CAN_MANAGE_USERS', scope: '*' });
    expect(parseScopedPermission('CAN_MANAGE_USERS')).toEqual({ permission: 'CAN_MANAGE_USERS' });
  });

  it('matches the region, the wildcard, or any region without scope', () => {
    const granted = [`CAN_GIVE_INTRO_PICKUPS@${REGION}`];
    expect(hasScopedPermission(granted, 'CAN_GIVE_INTRO_PICKUPS', REGION)).toBe(true);
    expect(hasScopedPermission(granted, 'CAN_GIVE_INTRO_PICKUPS', OTHER)).toBe(false);
    expect(hasScopedPermission(granted, 'CAN_GIVE_INTRO_PICKUPS')).toBe(true);
    expect(hasScopedPermission(granted, 'CAN_MANAGE_USERS')).toBe(false);
    expect(hasScopedPermission(['CAN_MANAGE_USERS@*'], 'CAN_MANAGE_USERS', OTHER)).toBe(true);
  });
});

describe('routeAccess / consentGuard', () => {
  let session: SessionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
    session = TestBed.inject(SessionService);
  });

  function logIn(permissions: string[], pendingConsents: string[] = [], role = Role.Foodsaver) {
    session.applySession({
      authenticated: true,
      permissions,
      pendingConsents: pendingConsents as never,
      user: {
        id: 'u1',
        email: 'a@local',
        displayName: 'Anna',
        role,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
  }

  it('checks permissions for the region of the route param (also from a parent route)', () => {
    logIn([`${Permission.CanManageUsers}@${REGION}`]);
    const data = { permissions: [Permission.CanManageUsers], scope: 'route:regionId' };
    expect(routeAccess(session, data, route({ regionId: REGION }))).toBe(true);
    expect(routeAccess(session, data, route({}, route({ regionId: REGION })))).toBe(true);
    expect(routeAccess(session, data, route({ regionId: OTHER }))).toBe(false);
    expect(routeAccess(session, data, route({}))).toBe(false);
    // any-of semantics and unscoped routes
    expect(
      routeAccess(session, { permissions: [Permission.CanGiveIntroPickups, Permission.CanManageUsers] }),
    ).toBe(true);
    expect(routeAccess(session, { permissions: [Permission.CanGiveIntroPickups] })).toBe(false);
  });

  it('lets ORGA in everywhere through @*', () => {
    logIn([`${Permission.CanManageUsers}@*`], [], Role.Orga);
    expect(
      routeAccess(session, { permissions: [Permission.CanManageUsers], scope: 'route:regionId' }, route({ regionId: OTHER })),
    ).toBe(true);
  });

  it('redirects to /consent while consents are pending, except for exempt pages', () => {
    const run = (url: string) =>
      TestBed.runInInjectionContext(() =>
        consentGuard({} as ActivatedRouteSnapshot, { url } as never),
      );
    expect(run('/dashboard')).toBe(true);

    logIn([], ['PRIVACY_POLICY']);
    const redirect = run('/dashboard?tab=1') as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(redirect)).toBe('/consent?returnUrl=%2Fdashboard%3Ftab%3D1');
    expect(run('/consent')).toBe(true);
    expect(run('/legal/PRIVACY_POLICY')).toBe(true);
    expect(isConsentExempt('/login/verify?token=x')).toBe(true);
    expect(isConsentExempt('/legalese')).toBe(false);

    logIn([], []);
    expect(run('/dashboard')).toBe(true);
  });
});
