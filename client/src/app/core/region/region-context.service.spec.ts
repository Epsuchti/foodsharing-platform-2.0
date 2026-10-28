import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { Language, Role, Theme, UnitType } from '../../api';
import { SessionService } from '../auth/session.service';
import { DateTimeFormatter } from '../dates/date-time.pipe';
import { provideDataStateTesting } from '../testing/data-state-testing';
import { REGION_STORAGE_KEY, RegionContextService } from './region-context.service';

@Component({ template: '' })
class BlankComponent {}

const ZH = '00000010-0001-4000-8000-000000000040';
const WT = '00000010-0001-4000-8000-000000000041';
const BERLIN = '00000010-0001-4000-8000-000000000060';

describe('RegionContextService (A15)', () => {
  let context: RegionContextService;

  beforeEach(() => {
    localStorage.removeItem(REGION_STORAGE_KEY);
    TestBed.configureTestingModule({
      providers: provideDataStateTesting({
        routes: [
          { path: 'regions/:regionId', component: BlankComponent },
          { path: '', component: BlankComponent },
        ],
      }),
    });
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: { id: 'u', email: 'u@local', displayName: 'U', role: Role.Foodsaver, language: Language.De, theme: Theme.System, verified: true },
      units: [
        { id: WT, name: 'Winterthur', slug: 'winterthur', type: UnitType.City, member: true, admin: false, homeRegion: false },
        { id: ZH, name: 'Zürich Stadt', slug: 'zuerich-stadt', type: UnitType.BigCity, member: true, admin: false, homeRegion: true },
      ],
    });
    context = TestBed.inject(RegionContextService);
  });

  it('defaults to the home region, follows the switcher and the route', async () => {
    expect(context.activeRegionId()).toBe(ZH);
    expect(context.regions().map((region) => region.slug)).toEqual(['zuerich-stadt', 'winterthur']);

    context.choose(WT);
    expect(context.activeRegionId()).toBe(WT);
    expect(localStorage.getItem(REGION_STORAGE_KEY)).toBe(WT);

    await TestBed.inject(Router).navigateByUrl(`/regions/${BERLIN}`);
    expect(context.activeRegionId()).toBe(BERLIN);
    expect(context.activeRegion()).toBeNull();

    await TestBed.inject(Router).navigateByUrl('/');
    expect(context.activeRegionId()).toBe(WT);
  });

  it('uses the registered time zone of the active region for dates', async () => {
    expect(context.timeZone()).toBe('Europe/Zurich');
    await TestBed.inject(Router).navigateByUrl(`/regions/${BERLIN}`);
    context.registerTimeZone(BERLIN, 'Europe/Lisbon');
    expect(context.timeZone()).toBe('Europe/Lisbon');
    const formatter = TestBed.inject(DateTimeFormatter);
    expect(formatter.format('2026-11-02T09:00:00Z', 'time', context.timeZone())).toBe('09:00');
  });
});
