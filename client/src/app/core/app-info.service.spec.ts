import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach } from 'vitest';

import { AppInfoService } from './app-info.service';
import { provideDataStateTesting } from './testing/data-state-testing';

/**
 * App info that cannot be loaded must not break the app (A2, D4.3): the service resolves to null, keeps the defaults
 * (no dev mode, no banner) and tries again on the next call. Moved from the removed e2e spec auth-error-states
 * (decision 180 #5); the login page's defaults are asserted in login-page.component.spec.ts.
 */
describe('AppInfoService', () => {
  let http: HttpTestingController;
  let appInfo: AppInfoService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
    http = TestBed.inject(HttpTestingController);
    appInfo = TestBed.inject(AppInfoService);
  });

  afterEach(() => http.verify());

  const appInfoRequest = () => http.expectOne((request) => request.url.endsWith('/api/public/app-info'));

  it('resolves to null on a failure, keeps the defaults and loads again on the next call', async () => {
    const failed = appInfo.load();
    appInfoRequest().flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    expect(await failed).toBeNull();
    expect(appInfo.info()).toBeNull();
    expect(appInfo.devMode()).toBe(false);
    expect(appInfo.demoBanner()).toBe(false);

    const offline = appInfo.load();
    appInfoRequest().error(new ProgressEvent('error'));
    expect(await offline).toBeNull();

    const loaded = appInfo.load();
    appInfoRequest().flush({ version: '1.2.3', devMode: true });
    expect(await loaded).toEqual({ version: '1.2.3', devMode: true });
    expect(appInfo.devMode()).toBe(true);
    // loaded once: no further request
    expect(await appInfo.load()).toEqual({ version: '1.2.3', devMode: true });
  });
});
