import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode, CategorySetting, NotificationCategory, NotificationSettings, PushMode } from '../../api';
import { vapidKeyBytes } from '../../core/push/push-client.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { devicePushState } from './components/push-card.component';
import { readPromptState, shouldPrompt } from './components/push-opt-in-card.component';
import { NotificationSettingsPageComponent, withChannel } from './pages/notification-settings-page.component';

const row = (category: NotificationCategory, extra: Partial<CategorySetting> = {}): CategorySetting => ({
  category,
  bell: true,
  mail: true,
  push: false,
  telegram: false,
  ...extra,
});

const SETTINGS: NotificationSettings = {
  categories: [row(NotificationCategory.Pickups), row(NotificationCategory.Region, { mail: false })],
  pushAvailable: false,
  telegramLinked: false,
  newsletterOptIn: true,
};

describe('notification helpers', () => {
  it('withChannel changes one cell and keeps bells on', () => {
    const next = withChannel(SETTINGS.categories, NotificationCategory.Pickups, 'mail', false);
    expect(next[0]).toEqual(row(NotificationCategory.Pickups, { mail: false }));
    expect(next[1]).toEqual(SETTINGS.categories[1]);
  });

  it('device push state', () => {
    expect(devicePushState('unsupported', false)).toBe('unsupported');
    expect(devicePushState('denied', true)).toBe('denied');
    expect(devicePushState('default', false)).toBe('off');
    expect(devicePushState('granted', true)).toBe('on');
  });

  it('opt-in card from the second session on, once per device', () => {
    expect(readPromptState(null)).toEqual({ visits: 0, done: undefined });
    expect(readPromptState('{broken')).toEqual({ visits: 0 });
    expect(shouldPrompt({ visits: 1 }, 'default', false)).toBe(false);
    expect(shouldPrompt({ visits: 2 }, 'default', false)).toBe(true);
    expect(shouldPrompt({ visits: 3, done: 'dismissed' }, 'default', false)).toBe(false);
    expect(shouldPrompt({ visits: 3 }, 'granted', false)).toBe(false);
    expect(shouldPrompt({ visits: 3 }, 'default', true)).toBe(false);
  });

  it('VAPID key bytes from URL-safe base64', () => {
    expect(Array.from(vapidKeyBytes('AQID_-8'))).toEqual([1, 2, 3, 255, 239]);
  });
});

describe('notification settings page', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({ routes: [{ path: 'account/notifications', component: NotificationSettingsPageComponent }] }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const flushSide = () => {
    http.match('/api/push/vapid-public-key').forEach((r) => r.flush({ publicKey: 'AQID', mode: PushMode.Log }));
    http.match('/api/users/me/profile').forEach((r) => r.flush({}));
    http.match((req) => req.url.startsWith('/api/users/me')  && req.url !== '/api/users/me/notification-settings').forEach((r) => r.flush({}));
  };

  it('loading, matrix, optimistic toggle sends every category', async () => {
    await harness.navigateByUrl('/account/notifications');
    expect(q('[data-testid="notifications.settings.state"]')?.getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/users/me/notification-settings').flush(SETTINGS);
    flushSide();
    await settle();
    expect(harness.routeNativeElement!.querySelectorAll('[data-testid="notifications.settings.row"]').length).toBe(2);

    const page = harness.routeDebugElement!.componentInstance as { toggle(c: NotificationCategory, ch: string, v: boolean): void };
    page.toggle(NotificationCategory.Region, 'mail', true);
    await settle();
    const put = http.expectOne((req) => req.url === '/api/users/me/notification-settings' && req.method !== 'GET');
    expect(put.request.body.categories).toHaveLength(2);
    expect(put.request.body.categories[1]).toEqual(row(NotificationCategory.Region, { mail: true }));
    put.flush({ ...SETTINGS, categories: put.request.body.categories });
    await settle();
    expect(q('[data-testid="notifications.settings.toggle.REGION.mail"]')?.getAttribute('data-checked')).toBe('true');
  });

  it('error state with retry', async () => {
    await harness.navigateByUrl('/account/notifications');
    http
      .expectOne('/api/users/me/notification-settings')
      .flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
    flushSide();
    await settle();
    expect(q('[data-testid="notifications.settings.state"]')?.getAttribute('data-state')).toBe('error');
  });
});
