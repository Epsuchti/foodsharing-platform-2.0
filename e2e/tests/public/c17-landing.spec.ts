import { type Page } from '@playwright/test';

import { expect, expectNoSeriousViolations, injectApiError, test } from '../../fixtures';

/**
 * C17 public landing page (decision 160, Variant D of docs/design/landing-page-proposal.md): every section, the live
 * counters, the postcode search with its result card and states, the real map (decision 179: deferred until it is in
 * view, pins, popup, search focus, tile failure, one-finger page scroll on phones, the `/map` deep link), the partner
 * CTA, no third-party request, reduced motion and the sticky join on phones.
 */

interface ExplainerDto {
  counters: { foodsavers: number; kgSaved: number; pickups: number; stores: number };
}

interface PublicRegionDto {
  slug: string;
  name: string;
  storeCount: number;
  memberCount: number;
  pin?: { latitude: number; longitude: number };
}

/** The `/map` deep link of a region pin at zoom 11 (decision 179, D14). */
function regionMapHref(region: PublicRegionDto): string {
  return `/map?lat=${region.pin!.latitude.toFixed(4)}&lon=${region.pin!.longitude.toFixed(4)}&z=11`;
}

/** `data-center` of a MapView at a region pin (3 decimals). */
function regionCenter(region: PublicRegionDto): string {
  return `${region.pin!.latitude.toFixed(3)},${region.pin!.longitude.toFixed(3)}`;
}

const MAP = 'public.landing.near.map';

const pathAndQuery = (page: Page): string => {
  const url = new URL(page.url());
  return url.pathname + url.search;
};

/** Scrolls the map frame into view (it loads only then) and waits for its pins. */
async function showMap(page: Page): Promise<void> {
  await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
  await page.getByTestId('public.landing.near.mapBlock').scrollIntoViewIfNeeded();
  await expect(page.getByTestId(MAP).locator('.leaflet-container')).toBeAttached();
  await expect(page.getByTestId(`${MAP}.state`)).toHaveAttribute('data-state', 'success');
}

const SECTIONS = [
  'public.landing.hero',
  'public.landing.mission',
  'public.explainer.state',
  'public.explainer.journey',
  'public.explainer.preview',
  'public.landing.near',
  'public.landing.fairteiler',
  'public.explainer.business',
  'public.landing.business.cta',
  'public.landing.business.quote',
  'public.landing.moreWays',
  'public.landing.duties',
  'public.landing.duties.rules',
  'public.explainer.faq',
  'public.explainer.faq.wiki',
  'public.explainer.faq.support',
  'public.landing.band',
];

const de = (n: number) => new Intl.NumberFormat('de-CH', { maximumFractionDigits: 0 }).format(n);

async function searchPostcode(page: Page, postcode: string): Promise<void> {
  await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
  await page.getByTestId('public.landing.near.search').fill(postcode);
  await page.getByTestId('public.landing.near.search.submit').click();
}

test('C17 landing shows every section of Variant D @C17 @page', async ({ page }) => {
  // the map loads only once it is in view (decision 179): no tile, no marker request and no Leaflet chunk before
  const mapRequests: string[] = [];
  const leafletChunks: string[] = [];
  page.on('request', (request) => {
    const { pathname } = new URL(request.url());
    if (pathname.startsWith('/tiles/') || pathname.startsWith('/api/public/map/')) mapRequests.push(pathname);
  });
  page.on('response', async (response) => {
    if (!new URL(response.url()).pathname.endsWith('.js')) return;
    const body = await response.text().catch(() => '');
    if (body.includes('leaflet-container')) leafletChunks.push(response.url());
  });
  await page.goto('/');
  await expect(page.getByTestId('public.explainer.state')).toHaveAttribute('data-state', 'success');
  await page.waitForLoadState('networkidle');
  expect(mapRequests).toEqual([]);
  expect(leafletChunks).toEqual([]);
  await expect(page.locator('fs-map-view, .leaflet-container')).toHaveCount(0);
  for (const id of SECTIONS) {
    await expect(page.getByTestId(id), id).toBeAttached();
  }
  for (const id of ['member', 'home', 'business']) {
    await expect(page.getByTestId(`public.landing.way.${id}`)).toBeVisible();
  }
  for (const n of [1, 2, 3, 4]) {
    await expect(page.getByTestId(`public.landing.journey.step.${n}`)).toBeVisible();
  }
  for (const id of ['free', 'cost', 'staff', 'accept', 'reliable', 'liability']) {
    await expect(page.getByTestId(`public.landing.business.benefit.${id}`)).toBeVisible();
  }
  for (const id of ['basket', 'donate', 'it']) {
    await expect(page.getByTestId(`public.landing.moreWays.${id}`)).toBeVisible();
  }
  for (const id of ['reliable', 'take', 'nosale', 'conduct', 'privacy', 'not.min', 'not.money', 'not.alone']) {
    await expect(page.getByTestId(`public.landing.duties.${id}`)).toBeVisible();
  }
  for (const group of ['join', 'pickup', 'share']) {
    await expect(page.getByTestId(`public.explainer.faq.group.${group}`)).toBeVisible();
  }
  for (const id of ['what', 'who', 'cost', 'time', 'id', 'duties', 'cancel', 'all', 'safe', 'fairteiler', 'basket', 'business']) {
    await expect(page.getByTestId(`public.explainer.faq.${id}`)).toBeVisible();
  }
  // the steps are a static overview: no buttons, no current step
  await expect(page.getByTestId('public.explainer.preview').locator('button, [aria-current]')).toHaveCount(0);
  // FAQ opens
  await page.getByTestId('public.explainer.faq.cost').locator('summary').click();
  await expect(page.getByTestId('public.explainer.faq.cost').locator('p')).toBeVisible();

  // E-Mail first and solid, Google second; Google goes straight to Google with the journey as return URL
  const email = page.getByTestId('public.landing.joinEmail');
  const google = page.getByTestId('public.landing.joinGoogle');
  const emailBox = (await email.boundingBox())!;
  const googleBox = (await google.boundingBox())!;
  expect(emailBox.y < googleBox.y || (emailBox.y === googleBox.y && emailBox.x < googleBox.x)).toBe(true);
  await expect(google).toHaveAttribute('href', '/oauth2/authorization/google?returnUrl=/onboarding');
  await expect(email).toHaveAttribute('href', '/login?method=email');
  // in view, the map loads: Leaflet, the self-hosted basemap and the markers
  await showMap(page);
  await expect.poll(() => leafletChunks.length).toBeGreaterThan(0);
  expect(mapRequests.some((path) => path.startsWith('/tiles/'))).toBe(true);
  expect(mapRequests).toContain('/api/public/map/markers/regions');
});

test('C17 counters equal the live explainer @C17', async ({ page, api }) => {
  const live = await api.getJson<ExplainerDto>('/api/public/onboarding/explainer');
  await page.goto('/');
  await expect(page.getByTestId('public.explainer.state')).toHaveAttribute('data-state', 'success');
  for (const key of ['kgSaved', 'pickups', 'foodsavers', 'stores'] as const) {
    const counter = page.getByTestId(`public.explainer.counter.${key}`);
    await expect(counter).toHaveAttribute('data-value', String(live.counters[key]));
    await expect(counter).toHaveText(de(live.counters[key]));
  }
  await expect(page.getByTestId('public.explainer.counter.regions')).toHaveCount(0);
});

test('C17 postcode 8004 shows the Zürich result card @C17 @page', async ({ page, api }) => {
  const region = await api.getJson<PublicRegionDto>('/api/public/regions/zuerich-stadt');
  await page.goto('/');
  await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
  await expect(page.getByTestId('public.landing.near.idle')).toBeVisible();
  await searchPostcode(page, '8004');
  const card = page.getByTestId('public.landing.near.result');
  await expect(card).toBeVisible();
  await expect(card).toHaveAttribute('aria-live', 'polite');
  await expect(page.getByTestId('public.landing.near.result.name')).toHaveText(region.name);
  await expect(page.getByTestId('public.landing.near.result.cooperations')).toContainText(de(region.storeCount));
  await expect(page.getByTestId('public.landing.near.result.members')).toContainText(de(region.memberCount));
  // the seed has active Fairteiler around Zürich HB, so two names are shown
  await expect(page.getByTestId('public.landing.near.result.fsps')).toContainText('Fairteiler in der Nähe');
  await expect(page.getByTestId('public.landing.near.regionLink')).toHaveAttribute('href', '/r/zuerich-stadt');
  // "Karte öffnen" opens /map at the region (card) and at the map's view, which the search moved there
  await expect(page.getByTestId('public.landing.near.openMap')).toHaveAttribute('href', regionMapHref(region));
  await expect(page.getByTestId(`${MAP}.openMap`)).toHaveAttribute('href', regionMapHref(region));
  await expectNoSeriousViolations(page);

  await page.getByTestId('public.landing.near.regionLink').click();
  await expect(page).toHaveURL(/\/r\/zuerich-stadt$/);
  await page.goBack();
  await searchPostcode(page, '8004');
  await page.getByTestId('public.landing.near.openMap').click();
  await expect(page).toHaveURL(/\/map\?lat=-?\d+\.\d{4}&lon=-?\d+\.\d{4}&z=11$/);
});

test('C17 invalid postcode shows a hint and sends no request @C17', async ({ page }) => {
  const suggests: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/public/regions/suggest') suggests.push(request.url());
  });
  await page.goto('/');
  await searchPostcode(page, '80');
  await expect(page.getByTestId('public.landing.near.search.hint')).toContainText('4 oder 5 Ziffern');
  await expect(page.getByTestId('public.landing.near.search')).toHaveAttribute('aria-invalid', 'true');
  await searchPostcode(page, 'Zürich');
  await expect(page.getByTestId('public.landing.near.search.hint')).toContainText('4 oder 5 Ziffern');
  expect(suggests).toEqual([]);
});

test('C17 no region for the postcode shows the empty state @C17', async ({ page }) => {
  await page.route('**/api/public/regions/suggest**', (route) => route.fulfill({ json: [] }));
  await page.goto('/');
  await searchPostcode(page, '9999');
  const state = page.getByTestId('public.landing.near.state');
  await expect(state).toHaveAttribute('data-state', 'empty');
  await expect(state).toContainText('gründe einen');
});

test('C17 geocoding unavailable offers retry @C17', async ({ page }) => {
  // three answers: the first request and the two automatic GET retries (retry.interceptor)
  await injectApiError(page, { url: '/api/public/regions/suggest', status: 503, code: 'GEOCODING_UNAVAILABLE', times: 3 });
  await page.goto('/');
  await searchPostcode(page, '8004');
  const state = page.getByTestId('public.landing.near.state');
  await expect(state).toHaveAttribute('data-state', 'error');
  await expect(state).toContainText('Suche gerade nicht möglich');
  await page.getByTestId('public.landing.near.state.retry').getByRole('button').click();
  await expect(state).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('public.landing.near.result')).toBeVisible();
});

test('C17 explainer error keeps the hero and offers retry @C17', async ({ page }) => {
  await injectApiError(page, { url: '**/api/public/onboarding/explainer**', status: 400, code: 'VALIDATION_FAILED', times: 1 });
  await page.goto('/');
  await expect(page.getByTestId('public.landing.hero')).toBeVisible();
  const state = page.getByTestId('public.explainer.state');
  await expect(state).toHaveAttribute('data-state', 'error');
  await page.getByTestId('public.explainer.state.retry').getByRole('button').click();
  await expect(state).toHaveAttribute('data-state', 'success');
});

test('C17 failing Fairteiler markers leave the map with region pins only, search still works @C17', async ({ page }) => {
  await injectApiError(page, { url: '/api/public/map/markers/food-share-points', status: 500 });
  await page.goto('/');
  await showMap(page);
  const map = page.getByTestId(MAP);
  await expect(map.locator('.fs-map-marker[data-kind="region-pin"], .fs-map-marker[data-kind="region"]').first()).toBeAttached();
  await expect(map.locator('.fs-map-marker[data-kind="fsp"]')).toHaveCount(0);
  await expect(page.getByTestId('public.landing.near.mapBlock').locator('[role="alert"]')).toHaveCount(0);
  await expect(page.getByTestId(`${MAP}.note`)).toHaveText('Bezirke und Fairteiler in deiner Nähe');
  await searchPostcode(page, '8004');
  await expect(page.getByTestId('public.landing.near.result')).toBeVisible();
  await expect(page.getByTestId('public.landing.near.result.fsps')).toContainText('Noch kein Fairteiler in der Nähe.');
});

test('C17 the map shows region and Fairteiler pins and their counts @C17', async ({ page, api }) => {
  const fsps = await api.getJson<unknown[]>('/api/public/map/markers/food-share-points');
  const regions = await api.getJson<unknown[]>('/api/public/map/markers/regions');
  await page.goto('/');
  await showMap(page);
  const map = page.getByTestId(MAP);
  await expect(map.locator('.fs-map-marker[data-kind="fsp"]')).toHaveCount(fsps.length);
  await expect(map.locator('.fs-map-marker[data-kind="region-pin"], .fs-map-marker[data-kind="region"]')).toHaveCount(regions.length);
  // the pins are no Tab stops; the map itself is one
  await expect(map.locator('.fs-map-marker[tabindex]')).toHaveCount(0);
  await expect(map.locator('.leaflet-container')).toHaveAttribute('tabindex', '0');
  await expect(page.getByTestId(`${MAP}.note`)).toHaveText(`${regions.length} Bezirke · ${fsps.length} Fairteiler`);
  await expect(map.locator('[role="region"]')).toHaveAttribute('aria-label', new RegExp(`${regions.length} Bezirke`));
});

test('C17 a pin opens its popup, "Ansehen" opens the page, axe clean @C17', async ({ page }) => {
  await page.goto('/');
  await showMap(page);
  // pins overlap at country zoom: click the pin's element directly
  await page.getByTestId(MAP).locator('.fs-map-marker[data-kind="fsp"]').first().dispatchEvent('click');
  const popup = page.getByTestId(`${MAP}.bubble`);
  await expect(popup).toBeVisible();
  await expect(page.getByTestId(`${MAP}.bubble.state`)).toHaveAttribute('data-state', 'success');
  await expect(popup).toContainText('Fairteiler');
  await expect(page.getByTestId(`${MAP}.bubble.title`)).toBeFocused();
  await expect(page.getByTestId(`${MAP}.bubble.title`)).not.toBeEmpty();
  await expectNoSeriousViolations(page);
  // Escape closes it, focus goes back to the map
  await page.keyboard.press('Escape');
  await expect(popup).toHaveCount(0);
  await expect(page.getByTestId(MAP).locator('.leaflet-container')).toBeFocused();

  // a region pin: "Ansehen" follows the bubble's link (the region page; it asks visitors to sign in, as on /map)
  await page.getByTestId(MAP).locator('.fs-map-marker[data-kind="region-pin"]').first().dispatchEvent('click');
  await expect(page.getByTestId(`${MAP}.bubble.state`)).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId(`${MAP}.bubble`)).toContainText('Bezirk');
  const open = page.getByTestId(`${MAP}.bubble.open`);
  const href = await open.getAttribute('href');
  expect(href).toMatch(/^\/regions\//);
  await open.click();
  await expect.poll(() => decodeURIComponent(pathAndQuery(page))).toContain(href!);
});

test('C17 postcode 8004 moves the map to Zürich @C17', async ({ page, api }) => {
  const region = await api.getJson<PublicRegionDto>('/api/public/regions/zuerich-stadt');
  await page.goto('/');
  await showMap(page);
  await searchPostcode(page, '8004');
  await expect(page.getByTestId('public.landing.near.result')).toBeVisible();
  await expect(page.getByTestId(MAP)).toHaveAttribute('data-center', regionCenter(region));
  expect(regionCenter(region)).toMatch(/^47\.3\d\d,8\.5\d\d$/);
});

test('C17 a tile failure shows the overlay with "Karte öffnen" @C17', async ({ page }) => {
  await page.route('**/tiles/**', (route) => route.fulfill({ status: 500, body: '' }));
  await page.goto('/');
  await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
  await page.getByTestId('public.landing.near.mapBlock').scrollIntoViewIfNeeded();
  await expect(page.getByTestId(MAP)).toHaveAttribute('data-tiles', 'error');
  const overlay = page.getByTestId(`${MAP}.tileError`);
  await expect(overlay).toBeVisible();
  await expect(page.getByTestId(`${MAP}.tilesOpenMap`)).toHaveAttribute('href', /^\/map\?lat=47\.9000&lon=10\.4000&z=[56]$/);
  // the pins stay usable
  await expect(page.getByTestId(MAP).locator('.fs-map-marker').first()).toBeAttached();
});

test('C17 "Karte öffnen" after a search lands on /map at Zürich, zoom 11 @C17 @M1', async ({ page, api }) => {
  const region = await api.getJson<PublicRegionDto>('/api/public/regions/zuerich-stadt');
  await page.goto('/');
  await showMap(page);
  await searchPostcode(page, '8004');
  await expect(page.getByTestId(MAP)).toHaveAttribute('data-center', regionCenter(region));
  await page.getByTestId(`${MAP}.openMap`).click();
  await expect.poll(() => pathAndQuery(page)).toBe(regionMapHref(region));
  await expect(page.getByTestId('map.page.map')).toHaveAttribute('data-center', regionCenter(region));
});

test.describe('C17 phone map', () => {
  test.use({ viewport: { width: 412, height: 800 }, hasTouch: true, isMobile: true });

  test('C17 phone: one finger scrolls the page over the map, the map stays @C17 @chromium-only', async ({ page }) => {
    await page.goto('/');
    await showMap(page);
    const map = page.getByTestId(MAP);
    const frame = (await map.locator('.leaflet-container').boundingBox())!;
    const center = await map.getAttribute('data-center');
    const before = await page.evaluate(() => window.scrollY);
    // a real one-finger swipe up: trusted touch events (the page gets touchstart/move/end, Leaflet sees them too).
    // Not Input.synthesizeScrollGesture: on headless Linux (CI) it does not scroll at all, not even off the map.
    const cdp = await page.context().newCDPSession(page);
    const x = Math.round(frame.x + frame.width / 2);
    const y = Math.round(frame.y + frame.height * 0.75);
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] });
    for (let step = 1; step <= 10; step++) {
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y: y - step * 20 }] });
    }
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(before + 100);
    await expect(map).toHaveAttribute('data-center', center!);
  });
});

test('C17 partner CTA opens support with the subject prefilled @C17', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('public.landing.business.cta').click();
  await expect(page).toHaveURL(/\/support\?from=%2F&subject=partner$/);
  await expect(page.getByTestId('content.support.subject')).toHaveValue('Partner werden: Lebensmittel abholen lassen');
});

test('C17 no request leaves the app origin @C17', async ({ page, baseURL }) => {
  const origin = new URL(baseURL!).origin;
  const foreign: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (/^https?:$/.test(url.protocol) && url.origin !== origin) foreign.push(request.url());
  });
  await page.goto('/');
  await expect(page.getByTestId('public.explainer.state')).toHaveAttribute('data-state', 'success');
  await searchPostcode(page, '8004');
  await expect(page.getByTestId('public.landing.near.result')).toBeVisible();
  await page.getByTestId('public.landing.band').scrollIntoViewIfNeeded();
  await page.waitForLoadState('networkidle');
  expect(foreign).toEqual([]);
});

test('C17 reduced motion shows the final counter values at once @C17', async ({ page, api }) => {
  const live = await api.getJson<ExplainerDto>('/api/public/onboarding/explainer');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await expect(page.getByTestId('public.explainer.state')).toHaveAttribute('data-state', 'success');
  const text = await page.getByTestId('public.explainer.counter.kgSaved').textContent();
  expect(text?.trim()).toBe(de(live.counters.kgSaved));
});

test('C17 phone: the sticky join appears once the hero is out of view @C17', async ({ page }) => {
  await page.setViewportSize({ width: 412, height: 800 });
  await page.goto('/');
  const sticky = page.getByTestId('public.landing.stickyJoin');
  await expect(page.getByTestId('public.landing.hero')).toBeVisible();
  await expect(sticky).toHaveCount(0);
  await page.getByTestId('public.explainer.journey').scrollIntoViewIfNeeded();
  await expect(sticky).toBeVisible();
  await expect(sticky.getByRole('link')).toHaveAttribute('href', '/login?method=email');
  // hidden again above the footer
  await page.locator('fs-footer').scrollIntoViewIfNeeded();
  await expect(sticky).toHaveCount(0);
});
