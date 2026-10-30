import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { FeatureToggleKey } from '../../api';
import { StartupService } from '../startup/startup.service';
import { provideDataStateTesting } from '../testing/data-state-testing';
import { FeatureToggleService } from './feature-toggle.service';
import { featureGuard } from './feature.guard';

const URL = '/api/public/feature-toggles';

describe('feature toggles', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('are all off until loaded, loaded once and shared by concurrent callers', async () => {
    const toggles = TestBed.inject(FeatureToggleService);
    expect(toggles.isEnabled(FeatureToggleKey.Baskets)).toBe(false);
    const first = toggles.load();
    const second = toggles.load();
    http.expectOne(URL).flush({ BASKETS: true, BLOG: false });
    await Promise.all([first, second]);
    expect(toggles.loaded()).toBe(true);
    expect(toggles.isEnabled(FeatureToggleKey.Baskets)).toBe(true);
    expect(toggles.isEnabled(FeatureToggleKey.Blog)).toBe(false);
    expect(toggles.isEnabled(FeatureToggleKey.GlobalSearch)).toBe(false);
    await toggles.load();
    http.expectNone(URL);
  });

  it('stay off after a failed load and retry on the next call', async () => {
    const toggles = TestBed.inject(FeatureToggleService);
    const failed = toggles.load();
    http.expectOne(URL).flush(null, { status: 503, statusText: 'down' });
    expect(await failed).toEqual({});
    expect(toggles.loaded()).toBe(false);
    const retry = toggles.load();
    http.expectOne(URL).flush({ BASKETS: true });
    await retry;
    expect(toggles.isEnabled(FeatureToggleKey.Baskets)).toBe(true);
  });

  it('apply a change made in the admin settings once loaded, and ignore it before', async () => {
    const toggles = TestBed.inject(FeatureToggleService);
    toggles.apply(FeatureToggleKey.Baskets, true);
    expect(toggles.loaded()).toBe(false);
    const load = toggles.load();
    http.expectOne(URL).flush({ BASKETS: true, BLOG: true });
    await load;
    toggles.apply(FeatureToggleKey.Baskets, false);
    expect(toggles.isEnabled(FeatureToggleKey.Baskets)).toBe(false);
    expect(toggles.isEnabled(FeatureToggleKey.Blog)).toBe(true);
  });

  it('are loaded once the first page was shown', () => {
    const startup = TestBed.inject(StartupService);
    startup.markPageShown();
    startup.markPageShown();
    http.expectOne(URL).flush({});
    expect(startup.pageShown()).toBe(true);
  });

  it('featureGuard lets enabled features through and sends disabled ones to /not-found', async () => {
    const run = (key: FeatureToggleKey) =>
      TestBed.runInInjectionContext(() => featureGuard(key)({} as never, {} as never)) as Promise<boolean | UrlTree>;
    const baskets = run(FeatureToggleKey.Baskets);
    http.expectOne(URL).flush({ BASKETS: true, BLOG: false });
    expect(await baskets).toBe(true);
    // the loaded toggles are cached: no second request
    const blog = await run(FeatureToggleKey.Blog);
    expect(TestBed.inject(Router).serializeUrl(blog as UrlTree)).toBe('/not-found');
  });
});
