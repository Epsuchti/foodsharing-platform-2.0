import type { Locator, Page } from '@playwright/test';
import { expectDataState } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * A visitor (not signed in) discovers foodsharing on the public landing page, Variant D (C17, decision 160): every
 * section, the live counters, the grouped FAQ, the join buttons (E-Mail first, Google second), the postcode search
 * with its result card, the real map (decision 179: loaded only once in view, pins, popup, Escape) and its "Karte
 * öffnen" deep link to /map, where the public layers show (FSPs, baskets, region pins, the exact store counts per city
 * of S17, no stores layer and no search for visitors). The partner CTA prefills the support form, the public pages
 * use the old foodsharing palette (decision 178, Optimus buttons included) and no request ever leaves our origin. On
 * the phone the sticky join appears once the hero is out of view.
 *
 * Rules behind it (unit): landing-geo.spec (4/5-digit postcodes, two nearest Fairteiler), landing-near.component.spec
 * (empty, 404, 503 retry, failing Fairteiler markers), landing-map.component.spec (failing list drops its pins, popup,
 * Escape, "Karte öffnen" follows the view), landing-page.component.spec (section order, defer of the map),
 * m7-shared.spec AnimatedCounterComponent (reduced motion), map-page.spec (visitor layers, count bubbles),
 * map-view.component.spec (cooperative gestures, tile error), shell.component.spec (fs-public only while logged out).
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

const MAP = 'public.landing.near.map';
const OLD_GREEN = 'rgb(68, 128, 26)';

const de = (n: number) => new Intl.NumberFormat('de-CH', { maximumFractionDigits: 0 }).format(n);
/** `data-center` of a MapView at a region pin (3 decimals). */
const center = (region: PublicRegionDto) => `${region.pin!.latitude.toFixed(3)},${region.pin!.longitude.toFixed(3)}`;
/** The `/map` deep link of a region pin at zoom 11 (decision 179, D14). */
const mapHref = (region: PublicRegionDto) =>
  `/map?lat=${region.pin!.latitude.toFixed(4)}&lon=${region.pin!.longitude.toFixed(4)}&z=11`;

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

const main = (page: Page): Locator => page.locator('main#main-content');
const primaryToken = (page: Page) => main(page).evaluate((element) => getComputedStyle(element).getPropertyValue('--fs-primary').trim());
const background = (locator: Locator) =>
  locator.evaluate((element) => getComputedStyle(element.matches('button, a') ? element : element.querySelector('button')!).backgroundColor);
const mapPins = (page: Page, kind: string): Locator => page.getByTestId('map.page.map').locator(`.fs-map-marker[data-kind="${kind}"]`);

/** Scrolls the map frame into view (it loads only then) and waits for its tiles and pins. */
async function showMap(page: Page): Promise<void> {
  await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
  await page.getByTestId('public.landing.near.mapBlock').scrollIntoViewIfNeeded();
  await expect(page.getByTestId(MAP).locator('.leaflet-container')).toBeAttached();
  await expect(page.getByTestId(MAP)).toHaveAttribute('data-tiles', 'ok');
  await expect(page.getByTestId(`${MAP}.state`)).toHaveAttribute('data-state', 'success');
}

test('visitor discovers foodsharing on the landing page, searches a postcode and opens the public map @C17 @M1 @S17', async ({
  page,
  api,
  baseURL,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'visitor');
  const live = await api.getJson<ExplainerDto>('/api/public/onboarding/explainer');
  const zurich = await api.getJson<PublicRegionDto>('/api/public/regions/zuerich-stadt');
  const fsps = await api.getJson<unknown[]>('/api/public/map/markers/food-share-points');
  const regions = await api.getJson<unknown[]>('/api/public/map/markers/regions');

  // no request ever leaves our origin (fonts, tiles, map data, avatars: all self-hosted)
  const origin = new URL(baseURL!).origin;
  const foreign: string[] = [];
  // the map loads only once it is in view (decision 179): no tile, no marker request, no Leaflet chunk before
  const mapRequests: string[] = [];
  const leafletChunks: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (/^https?:$/.test(url.protocol) && url.origin !== origin) foreign.push(request.url());
    if (/^\/(tiles|__fs-stub-tile)\//.test(url.pathname) || url.pathname.startsWith('/api/public/map/')) mapRequests.push(url.pathname);
  });
  page.on('response', async (response) => {
    if (!new URL(response.url()).pathname.endsWith('.js')) return;
    const body = await response.text().catch(() => '');
    if (body.includes('leaflet-container')) leafletChunks.push(response.url());
  });

  await walk.step('opens the landing page and reads what foodsharing is', async () => {
    await page.goto('/');
    await expectDataState(page, 'public.explainer.state', 'success');
    await page.waitForLoadState('networkidle');
    expect(mapRequests, 'map requests before the map is in view').toEqual([]);
    expect(leafletChunks, 'Leaflet before the map is in view').toEqual([]);
    await expect(page.locator('fs-map-view, .leaflet-container')).toHaveCount(0);
    for (const id of SECTIONS) await expect(page.getByTestId(id), id).toBeAttached();
    for (const id of ['member', 'home', 'business']) await expect(page.getByTestId(`public.landing.way.${id}`)).toBeVisible();
    for (const n of [1, 2, 3, 4]) await expect(page.getByTestId(`public.landing.journey.step.${n}`)).toBeVisible();
    for (const id of ['free', 'cost', 'staff', 'accept', 'reliable', 'liability']) {
      await expect(page.getByTestId(`public.landing.business.benefit.${id}`)).toBeVisible();
    }
    for (const id of ['basket', 'donate', 'it']) await expect(page.getByTestId(`public.landing.moreWays.${id}`)).toBeVisible();
    for (const id of ['reliable', 'take', 'nosale', 'conduct', 'privacy', 'not.min', 'not.money', 'not.alone']) {
      await expect(page.getByTestId(`public.landing.duties.${id}`)).toBeVisible();
    }
    for (const group of ['join', 'pickup', 'share']) await expect(page.getByTestId(`public.explainer.faq.group.${group}`)).toBeVisible();
    for (const id of ['what', 'who', 'cost', 'time', 'id', 'duties', 'cancel', 'all', 'safe', 'fairteiler', 'basket', 'business']) {
      await expect(page.getByTestId(`public.explainer.faq.${id}`)).toBeVisible();
    }
    // the steps are a static overview: no buttons, no current step
    await expect(page.getByTestId('public.explainer.preview').locator('button, [aria-current]')).toHaveCount(0);
    // the counters are the live numbers, formatted for Switzerland; no region counter
    for (const key of ['kgSaved', 'pickups', 'foodsavers', 'stores'] as const) {
      const counter = page.getByTestId(`public.explainer.counter.${key}`);
      await expect(counter).toHaveAttribute('data-value', String(live.counters[key]));
      await expect(counter).toHaveText(de(live.counters[key]));
    }
    await expect(page.getByTestId('public.explainer.counter.regions')).toHaveCount(0);
    // the old foodsharing palette while nobody is signed in (decision 178)
    await expect(main(page)).toHaveClass(/fs-public/);
    expect(await primaryToken(page)).toBe('#44801a');
    // E-Mail first and solid, Google second; Google goes straight to Google with the journey as return URL
    const email = page.getByTestId('public.landing.joinEmail');
    const google = page.getByTestId('public.landing.joinGoogle');
    const emailBox = (await email.boundingBox())!;
    const googleBox = (await google.boundingBox())!;
    expect(emailBox.y < googleBox.y || (emailBox.y === googleBox.y && emailBox.x < googleBox.x)).toBe(true);
    await expect(email).toHaveAttribute('href', '/login?method=email');
    await expect(google).toHaveAttribute('href', '/oauth2/authorization/google?returnUrl=/onboarding');
    expect(await background(email)).toBe(OLD_GREEN);

    await walk.click(page.getByTestId('public.explainer.faq.cost').locator('summary'));
    await expect(page.getByTestId('public.explainer.faq.cost').locator('p')).toBeVisible();

    if (isPhone(page)) {
      // the sticky join appears once the hero is out of view and hides again above the footer
      const sticky = page.getByTestId('public.landing.stickyJoin');
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
      await expect(page.getByTestId('public.landing.hero')).toBeInViewport();
      await expect(sticky).toHaveCount(0);
      await page.getByTestId('public.explainer.journey').scrollIntoViewIfNeeded();
      await expect(sticky).toBeVisible();
      await expect(sticky.getByRole('link')).toHaveAttribute('href', '/login?method=email');
      await page.locator('fs-footer').scrollIntoViewIfNeeded();
      await expect(sticky).toHaveCount(0);
    }

    // in view, the map loads: Leaflet, the basemap and the markers
    await showMap(page);
    await expect.poll(() => leafletChunks.length).toBeGreaterThan(0);
    expect(mapRequests.some((path) => /^\/(tiles|__fs-stub-tile)\//.test(path))).toBe(true);
    expect(mapRequests).toContain('/api/public/map/markers/regions');
    await expect(page.getByTestId('public.landing.near.idle')).toBeVisible();
    await checkpoint(page, 'landing');
  });

  await walk.step('looks at the pins on the map and opens one', async () => {
    const map = page.getByTestId(MAP);
    await expect(map.locator('.fs-map-marker[data-kind="fsp"]')).toHaveCount(fsps.length);
    await expect(map.locator('.fs-map-marker[data-kind="region-pin"], .fs-map-marker[data-kind="region"]')).toHaveCount(regions.length);
    // the pins are no Tab stops; the map itself is one
    await expect(map.locator('.fs-map-marker[tabindex]')).toHaveCount(0);
    await expect(map.locator('.leaflet-container')).toHaveAttribute('tabindex', '0');
    await expect(page.getByTestId(`${MAP}.note`)).toHaveText(`${regions.length} Bezirke · ${fsps.length} Fairteiler`);
    await expect(map.locator('[role="region"]')).toHaveAttribute('aria-label', new RegExp(`${regions.length} Bezirke`));

    // pins overlap at country zoom: a tap on the pin itself
    walk.count(1);
    await map.locator('.fs-map-marker[data-kind="fsp"]').first().dispatchEvent('click');
    const popup = page.getByTestId(`${MAP}.bubble`);
    await expect(popup).toBeVisible();
    await expectDataState(page, `${MAP}.bubble.state`, 'success');
    await expect(popup).toContainText('Fairteiler');
    await expect(page.getByTestId(`${MAP}.bubble.title`)).toBeFocused();
    await expect(page.getByTestId(`${MAP}.bubble.title`)).not.toBeEmpty();
    await checkpointElement(page.getByTestId('public.landing.near.mapBlock'), 'map-popup');
    // Escape closes it, focus goes back to the map
    await page.keyboard.press('Escape');
    await expect(popup).toHaveCount(0);
    await expect(map.locator('.leaflet-container')).toBeFocused();
  });

  await walk.step('searches the postcode 8004 and finds Zürich', async () => {
    const search = page.getByTestId('public.landing.near.search');
    const suggests: string[] = [];
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/public/regions/suggest') suggests.push(request.url());
    });
    // a postcode has 4 or 5 digits: anything else gets a hint and no request
    await walk.fill(search, '80');
    await walk.click(page.getByTestId('public.landing.near.search.submit'));
    await expect(page.getByTestId('public.landing.near.search.hint')).toContainText('4 oder 5 Ziffern');
    await expect(search).toHaveAttribute('aria-invalid', 'true');
    expect(suggests).toEqual([]);

    await walk.fill(search, '8004');
    await walk.click(page.getByTestId('public.landing.near.search.submit'));
    const card = page.getByTestId('public.landing.near.result');
    await expect(card).toBeVisible();
    await expect(card).toHaveAttribute('aria-live', 'polite');
    await expect(page.getByTestId('public.landing.near.result.name')).toHaveText(zurich.name);
    await expect(page.getByTestId('public.landing.near.result.cooperations')).toContainText(de(zurich.storeCount));
    await expect(page.getByTestId('public.landing.near.result.members')).toContainText(de(zurich.memberCount));
    // the seed has active Fairteiler around Zürich HB, so two names are shown
    await expect(page.getByTestId('public.landing.near.result.fsps')).toContainText('Fairteiler in der Nähe');
    await expect(page.getByTestId('public.landing.near.regionLink')).toHaveAttribute('href', '/r/zuerich-stadt');
    // the map moved to the region; "Karte öffnen" of the card and of the map open /map there
    await expect(page.getByTestId(MAP)).toHaveAttribute('data-center', center(zurich));
    expect(center(zurich)).toMatch(/^47\.3\d\d,8\.5\d\d$/);
    await expect(page.getByTestId(MAP)).toHaveAttribute('data-tiles', 'ok');
    await expect(page.getByTestId('public.landing.near.openMap')).toHaveAttribute('href', mapHref(zurich));
    await expect(page.getByTestId(`${MAP}.openMap`)).toHaveAttribute('href', mapHref(zurich));
    await checkpointElement(page.getByTestId('public.landing.near'), 'postcode-result');
  });

  await walk.step('opens the region from a region pin', async () => {
    // "Ansehen" follows the bubble's link (the region page; it asks visitors to sign in, as on /map)
    walk.count(1);
    await page.getByTestId(MAP).locator('.fs-map-marker[data-kind="region-pin"]').first().dispatchEvent('click');
    await expectDataState(page, `${MAP}.bubble.state`, 'success');
    await expect(page.getByTestId(`${MAP}.bubble`)).toContainText('Bezirk');
    const open = page.getByTestId(`${MAP}.bubble.open`);
    const href = await open.getAttribute('href');
    expect(href).toMatch(/^\/regions\//);
    await walk.click(open);
    await expect.poll(() => decodeURIComponent(new URL(page.url()).pathname + new URL(page.url()).search)).toContain(href!);
    await page.goBack();
    await expectDataState(page, 'public.explainer.state', 'success');
  });

  await walk.step('opens the big map at Zürich', async () => {
    // "In deiner Nähe" is deferred until it is in view
    await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
    await walk.fill(page.getByTestId('public.landing.near.search'), '8004');
    await walk.click(page.getByTestId('public.landing.near.search.submit'));
    await expect(page.getByTestId('public.landing.near.result')).toBeVisible();
    await walk.click(page.getByTestId('public.landing.near.openMap'));
    await expect.poll(() => new URL(page.url()).pathname + new URL(page.url()).search).toBe(mapHref(zurich));
    const map = page.getByTestId('map.page.map');
    await expect(map).toHaveAttribute('data-center', center(zurich));
    await expectDataState(page, 'map.page.map.state', 'success');
    await expect(map).toHaveAttribute('data-tiles', 'ok');
    // the public layers: Fairteiler, baskets (positions rounded by the server), region pins; store counts, no stores
    await expect(mapPins(page, 'fsp')).toHaveCount(5);
    await expect(mapPins(page, 'basket')).toHaveCount(10);
    await expect(mapPins(page, 'region-pin').first()).toBeAttached();
    await expect(page.getByTestId('map.page.layer.stores')).toHaveCount(0);
    await expect(page.getByTestId('map.page.layer.storeAreas')).toBeVisible();
    await expect(page.getByTestId('map.page.search')).toHaveCount(0);
    await expect(page.getByTestId('map.page.loginHint')).toHaveCount(0);
    await checkpoint(page, 'public-map');
  });

  await walk.step('opens the store count of a city and a Fairteiler on the map', async () => {
    // S17: exact store counts per city, no store identity
    const area = mapPins(page, 'store-area').first();
    await area.focus();
    walk.count(1);
    await area.press('Enter');
    await expect(page.getByTestId('map.area.text')).toBeVisible();
    await expect(page.getByTestId('map.area.text')).toContainText(/\d+ Betrieb/);
    await checkpoint(page, 'store-count-bubble');
    await walk.click(page.getByTestId('map.area.close'));
    await expect(page.getByTestId('map.area')).toHaveCount(0);

    const fsp = mapPins(page, 'fsp').first();
    await fsp.focus();
    walk.count(1);
    await fsp.press('Enter');
    await expectDataState(page, 'map.bubble.state', 'success');
    await expect(page.getByTestId('map.bubble.link')).toHaveAttribute('href', /^\/food-share-points\/[0-9a-f-]{36}$/);
    // the X takes the click in its middle (the card content used to lie over it and swallow the click)
    await walk.click(page.getByTestId('map.bubble.close'));
    await expect(page.getByTestId('map.bubble')).toHaveCount(0);
  });

  await walk.step('asks to become a partner store', async () => {
    await page.goto('/');
    await expectDataState(page, 'public.explainer.state', 'success');
    await walk.click(page.getByTestId('public.landing.business.cta'));
    await expect(page).toHaveURL(/\/support\?from=%2F&subject=partner$/);
    await expect(page.getByTestId('content.support.subject')).toHaveValue('Partner werden: Lebensmittel abholen lassen');
    await expect(main(page)).toHaveClass(/fs-public/);
    await checkpoint(page, 'partner-support');
  });

  await walk.step('goes to the sign-in, in the old colours too', async () => {
    await page.goto('/faq');
    await expect(main(page)).toHaveClass(/fs-public/);
    expect(await primaryToken(page)).toBe('#44801a');
    await page.goto('/login?method=email');
    await expect(main(page)).toHaveClass(/fs-public/);
    // a <p-button>: its colours come from the --p-* tokens re-declared in the scope
    await expect(page.getByTestId('auth.login.emailSubmit')).toBeVisible();
    expect(await background(page.getByTestId('auth.login.emailSubmit'))).toBe(OLD_GREEN);
  });

  expect(foreign, 'requests to other hosts').toEqual([]);
  walk.finish({ steps: 8, clicks: 14, fields: 3 });
});

test.describe('phone map', () => {
  test.use({ viewport: { width: 412, height: 800 }, hasTouch: true, isMobile: true });

  test('visitor scrolls the landing page with one finger over the map @C17 @chromium-only', async ({ page }) => {
    await page.goto('/');
    await showMap(page);
    const map = page.getByTestId(MAP);
    const frame = (await map.locator('.leaflet-container').boundingBox())!;
    const before = await map.getAttribute('data-center');
    const scrollY = await page.evaluate(() => window.scrollY);
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
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(scrollY + 100);
    await expect(map).toHaveAttribute('data-center', before!);
  });
});
