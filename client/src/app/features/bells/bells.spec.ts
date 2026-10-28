import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode, Bell } from '../../api';
import { REALTIME_MAX_DELAY_MS, nextReconnectDelay } from '../../core/realtime/realtime.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { badgeText } from './components/bell-button.component';
import { bellIconClass, bellTitleKey } from './components/bell-item.component';
import { BellsPageComponent, mergeBellPages } from './pages/bells-page.component';

import de from './i18n/de.json';
import en from './i18n/en.json';
import fr from './i18n/fr.json';
import gws from './i18n/gws.json';
import itCatalog from './i18n/it.json';

const bell = (id: string, extra: Partial<Bell> = {}): Bell => ({
  id,
  type: 'REGION_NEW_MEMBER',
  titleKey: 'bell.REGION_NEW_MEMBER',
  params: { name: 'Lea' },
  createdAt: '2026-11-02T08:00:00Z',
  mailed: false,
  pushed: false,
  ...extra,
});

const page = (items: Bell[], pageIndex = 0, totalPages = 1) => ({
  items,
  page: { page: pageIndex, size: 20, totalItems: items.length, totalPages },
});

describe('bell helpers', () => {
  it('badge caps at 99+', () => {
    expect(badgeText(3)).toBe('3');
    expect(badgeText(120)).toBe('99+');
  });

  it('title key and icon fall back to the type and the bell icon', () => {
    expect(bellTitleKey(bell('a'))).toBe('bell.REGION_NEW_MEMBER');
    expect(bellTitleKey(bell('a', { titleKey: 'REGION_NEW_MEMBER', type: 'GENERIC' }))).toBe('bell.GENERIC');
    expect(bellIconClass(bell('a', { icon: 'user-plus' }))).toBe('pi pi-user-plus');
    expect(bellIconClass(bell('a'))).toBe('pi pi-bell');
  });

  it('paging merge skips bells already shown', () => {
    expect(mergeBellPages([bell('a'), bell('b')], [bell('b'), bell('c')]).map((b) => b.id)).toEqual(['a', 'b', 'c']);
  });

  it('reconnect delay doubles from 1 s to at most 30 s', () => {
    expect(nextReconnectDelay(1_000)).toBe(2_000);
    expect(nextReconnectDelay(20_000)).toBe(REALTIME_MAX_DELAY_MS);
    expect(nextReconnectDelay(0)).toBe(1_000);
  });

  it('every bell key exists in all five languages', () => {
    const keys = (catalog: Record<string, string>) => Object.keys(catalog).sort();
    for (const catalog of [en, fr, gws, itCatalog]) {
      expect(keys(catalog)).toEqual(keys(de));
    }
    expect(keys(de).filter((key) => /^bell\.[A-Z_]+$/.test(key)).length).toBeGreaterThan(50);
  });
});

describe('bells page', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const qa = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const settle = () => harness.fixture.whenStable();
  const list = () => http.expectOne((req) => req.url === '/api/bells');

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({ routes: [{ path: 'bells', component: BellsPageComponent }] }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('loading, then the list; "load more" appends the next page', async () => {
    await harness.navigateByUrl('/bells');
    expect(q('[data-testid="bells.page.state"]')?.getAttribute('data-state')).toBe('loading');
    const first = list();
    expect(first.request.params.get('page')).toBe('0');
    first.flush(page([bell('a'), bell('b', { readAt: '2026-11-02T09:00:00Z' })], 0, 2));
    await settle();
    expect(qa('[data-testid="bells.page.item"]').length).toBe(2);
    expect(q('[data-testid="bells.page.item"]')?.getAttribute('data-unread')).toBe('true');

    (q('[data-testid="bells.page.loadMore"] button') as HTMLButtonElement).click();
    await settle();
    const second = list();
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page([bell('c')], 1, 2));
    await settle();
    expect(qa('[data-testid="bells.page.item"]').length).toBe(3);
    expect(q('[data-testid="bells.page.loadMore"]')).toBeNull();
  });

  it('empty and error + retry', async () => {
    await harness.navigateByUrl('/bells');
    list().flush(page([], 0, 0));
    await settle();
    expect(q('[data-testid="bells.page.state"]')?.getAttribute('data-state')).toBe('empty');

    (harness.routeDebugElement!.componentInstance as { unreadOnly: { set(v: boolean): void } }).unreadOnly.set(true);
    await settle();
    const failing = list();
    expect(failing.request.params.get('unreadOnly')).toBe('true');
    failing.flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
    await settle();
    expect(q('[data-testid="bells.page.state"]')?.getAttribute('data-state')).toBe('error');
  });

  it('click marks the bell read and opens its link', async () => {
    await harness.navigateByUrl('/bells');
    list().flush(page([bell('a', { link: '/regions/x' })]));
    await settle();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    (q('[data-testid="bells.page.item.open"]') as HTMLButtonElement).click();
    const read = http.expectOne('/api/bells/read');
    expect(read.request.body).toEqual({ ids: ['a'] });
    expect(navigate).toHaveBeenCalledWith('/regions/x');
    read.flush(null);
    // the badge recounts after the change
    http.expectOne('/api/bells/unread-count').flush({ count: 0 });
  });
});
