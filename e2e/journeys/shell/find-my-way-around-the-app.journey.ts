import { SEED_NOW, collapseSidenav, expectDataState, injectApiError, seedStore } from '../../fixtures';
import { animationsDone } from '../../fixtures/a11y';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { expectContentBesideMenu, expectFitsScreen, expectFloatingCard, pathOf } from '../../fixtures/flows/shell';
import { Walk, isPhone } from '../../fixtures/persona';
import { dateField, fillDate } from '../../support/date-inputs';

/**
 * Sarah (foodsaver@local, home region Zürich Stadt) finds her way around the app (D4.3 shell, plan §12.5,
 * decisions 133 and 149): on desktop the side menu next to the content with its floating cards (user menu, bells,
 * region switcher) and, on a laptop-wide window, the icon rail with flyouts; on phones the tab bar and the menu drawer.
 * On the way she sees the request bar while a slow list loads (AD1), opens the forum and a store from the menu, and
 * plans a sleep mode with the shared date inputs (AD1, decision 150): `dd.mm.yyyy` also on an en-US browser, a typed,
 * a lenient and an unreadable date, a date picked from the calendar. The app is an installable PWA (AU12): the page
 * links the web manifest, and the jar serves it with the service worker configuration.
 *
 * Rule permutations are unit tests: menu items per role and capability (sidenav.component.spec.ts,
 * shell.component.spec.ts, SessionCapabilitiesIT), side menu state (sidenav-state.spec.ts), next step
 * (home-cards.spec.ts), request bar counting (loading.interceptor.spec.ts), date parsing (date-input.util.spec.ts),
 * calendar names per language (optimus-locale.spec.ts).
 */
test.use({ role: 'foodsaver', locale: 'en-US' });

const S01 = seedStore('S-01');
const LAPTOP = { width: 1000, height: 800 };

test('foodsaver finds her way around the side menu, the phone tabs and the date inputs @D4.3 @AD1 @AU12', async ({
  page,
  apiOf,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const phone = isPhone(page);

  await walk.step('opens the app on her dashboard', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await expect(page.getByTestId('dashboard.page.homeRegion')).toContainText('Zürich');
    // nothing is missing for her: no next step card
    await expect(page.getByTestId('dashboard.page.nextStep')).toHaveCount(0);
    // AU12: the page links the web manifest; the jar serves it and the service worker configuration
    await expect(page.locator('link[rel="manifest"]')).toHaveAttribute('href', /manifest\.webmanifest$/);
    const manifest = await page.request.get('/manifest.webmanifest');
    expect(manifest.status()).toBe(200);
    const app = (await manifest.json()) as { name?: string; icons?: unknown[] };
    expect(app.name).toBeTruthy();
    expect(app.icons?.length).toBeGreaterThan(0);
    const ngsw = await page.request.get('/ngsw.json');
    expect(ngsw.status()).toBe(200);
    expect(((await ngsw.json()) as { configVersion?: number }).configVersion).toBe(1);
    expect((await page.request.get('/ngsw-worker.js')).status()).toBe(200);

    if (phone) {
      const tabBar = page.getByTestId('shell.tabBar');
      await expect(tabBar).toBeVisible();
      await expect(page.getByTestId('nav.sidenav')).toHaveCount(0);
      await expect(page.getByTestId('nav.tab.dashboard')).toHaveAttribute('aria-current', 'page');
      // decision 149: four tabs; activity and account are in the menu drawer
      await expect(page.getByTestId('nav.tab.stores')).toHaveAttribute('href', '/stores/mine');
      await expect(page.getByTestId('nav.tab.messages')).toHaveAttribute('href', '/messages');
      await expect(page.getByTestId('nav.tab.account')).toHaveCount(0);
      // the tab bar never covers the page: scrolled to the end, the footer ends above it (late cards push it down)
      const version = page.getByTestId('shell.version');
      await expect(version).toBeAttached();
      await expect
        .poll(
          async () => {
            await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
            const v = await version.boundingBox();
            const bar = await tabBar.boundingBox();
            return v && bar ? v.y + v.height - bar.y : Number.POSITIVE_INFINITY;
          },
          { message: 'footer version above the tab bar when scrolled to the end' },
        )
        .toBeLessThanOrEqual(1);
      await expectFitsScreen(page, 'dashboard');
    } else {
      // decision 133: the side menu replaces the top header, always expanded from 75rem
      const sidenav = page.getByTestId('nav.sidenav');
      await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
      await expect(page.getByTestId('nav.sidenav.toggle')).toHaveCount(0);
      await expect(page.locator('fs-header')).toHaveCount(0);
      await expect(page.getByTestId('shell.tabBar')).toBeHidden();
      await expectContentBesideMenu(page);
      expect(await page.locator('#main-content').evaluate((el) => getComputedStyle(el).getPropertyValue('--fs-header-h').trim())).toBe('0px');
      await expect(page.getByTestId('nav.dashboard')).toHaveAttribute('aria-current', 'page');
      await expect(page.getByTestId('nav.section.community')).toHaveAttribute('aria-expanded', 'true');
      await expect(page.getByTestId('nav.pickups')).toBeVisible();
      await expect(page.getByTestId('nav.footer.gallery')).toBeVisible();
      // "[" does nothing from 75rem
      await page.getByTestId('dashboard.page.welcome').click();
      await page.keyboard.press('[');
      await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
      // the side menu never scrolls sideways
      expect(await sidenav.evaluate((el) => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(0);
    }
    await checkpoint(page, 'dashboard', { mask: [page.getByTestId('shell.version')] });
  });

  await walk.step('opens her activity from the menu while the list loads slowly', async () => {
    let release!: () => void;
    const held = new Promise<void>((resolve) => (release = resolve));
    await injectApiError(page, { url: '/api/history/me', holdUntil: held, times: 1 });
    if (phone) {
      const menuTab = page.getByTestId('nav.tab.menu');
      await expect(menuTab).toHaveAttribute('aria-expanded', 'false');
      await walk.click(menuTab);
      await expect(menuTab).toHaveAttribute('aria-expanded', 'true');
      const drawer = page.locator('.fs-mobile-menu');
      await expect(drawer).toBeVisible();
      await animationsDone(page);
      // decision 149: Meine Aktivität and Quiz right under the profile, then the bells, then the main items
      const links = await drawer.locator('a[data-testid^="nav.drawer."]').evaluateAll((items) => items.map((a) => a.getAttribute('data-testid')));
      expect(links.slice(0, 4)).toEqual(['nav.drawer.activity', 'nav.drawer.quizzes', 'nav.drawer.bells', 'nav.drawer.dashboard']);
      const activity = drawer.getByTestId('nav.drawer.activity');
      const at = (await activity.boundingBox())!;
      const onTop = await page.evaluate(
        ([x, y]) => !!document.elementFromPoint(x, y)?.closest('.fs-mobile-menu'),
        [at.x + at.width / 2, at.y + at.height / 2] as const,
      );
      expect(onTop, 'the drawer lies above its modal mask').toBe(true);
      await checkpoint(page, 'menu');
      await walk.click(activity);
      await expect(drawer).toBeHidden();
    } else {
      const user = page.getByTestId('shell.userMenu');
      await walk.click(user);
      await expect(user).toHaveAttribute('aria-expanded', 'true');
      const card = page.getByTestId('shell.userMenu.card');
      await expectFloatingCard(page, card);
      const order = await card.locator('a[data-testid^="nav.user."]').evaluateAll((items) => items.map((a) => a.getAttribute('data-testid')));
      expect(order.slice(0, 3)).toEqual(['nav.user.profile', 'nav.user.activity', 'nav.user.quizzes']);
      // no shortcuts to list from 75rem: the menu is always expanded
      await expect(page.getByTestId('shell.userMenu.shortcuts')).toHaveCount(0);
      await checkpoint(page, 'menu');
      // Escape closes the card and gives the focus back
      await page.keyboard.press('Escape');
      await expect(card).toBeHidden();
      await expect(user).toBeFocused();
      await walk.click(user);
      await walk.click(card.getByTestId('nav.user.activity'));
    }
    await expect.poll(() => pathOf(page)).toBe('/me/activity');
    // AD1: the request bar runs while the list loads
    const bar = page.getByTestId('shell.requestBar');
    await expect(bar).toBeVisible();
    await expect(bar).toHaveAttribute('role', 'progressbar');
    await expect(bar).toHaveAccessibleName(/.+/);
    release();
    await expectDataState(page, 'history.myActivity.list', 'success');
    await expect(bar).toBeHidden();
    if (phone) {
      await expect(page.getByTestId('nav.tab.dashboard')).not.toHaveAttribute('aria-current', 'page');
      await expectFitsScreen(page, 'my activity');
    } else {
      await expect(page.getByTestId('nav.dashboard')).not.toHaveAttribute('aria-current', 'page');
    }
  });

  await walk.step('checks her regions in the switcher', async () => {
    if (!phone) {
      // the bell panel opens next to the bell item, without a page change
      const bell = page.getByTestId('bells.button');
      await walk.click(bell);
      await expect(bell).toHaveAttribute('aria-expanded', 'true');
      await expect(page.getByTestId('bells.panel.state')).toHaveAttribute('data-state', /^(success|empty)$/);
      await expectFloatingCard(page, page.locator('#sn-card-bells'));
      expect(pathOf(page)).toBe('/me/activity');
    }
    const switcher = page.getByTestId('shell.regionSwitcher');
    await expect(switcher).toContainText('Zürich');
    // one card at a time: the switcher replaces the bell panel
    await walk.click(switcher);
    if (!phone) {
      await expect(page.getByTestId('bells.button')).toHaveAttribute('aria-expanded', 'false');
      await expectFloatingCard(page, page.getByTestId('shell.regionSwitcher.card'));
    }
    // the home region is the current one
    await expect(page.getByTestId('shell.region.zuerich-stadt')).toHaveAttribute('aria-checked', 'true');
    await checkpoint(page, 'region-switcher', { mask: [page.locator('[data-testid="history.myActivity.entry"] time')] });
    await page.keyboard.press('Escape');
    if (!phone) await expect(switcher).toBeFocused();
  });

  await walk.step('opens the forum of her home region and her store from the menu', async () => {
    await walk.nav('forum');
    await expect.poll(() => pathOf(page)).toMatch(/^\/regions\/[0-9a-f-]{36}\/forum$/);
    if (!phone) {
      await expect(page.getByTestId('nav.forum')).toHaveAttribute('aria-current', 'page');
      await expect(page.getByTestId('nav.regions')).not.toHaveAttribute('aria-current', 'page');
      // decision 149: Postfächer only for readers of a mailbox
      await expect(page.getByTestId('nav.mailboxes')).toHaveCount(0);
    }
    await walk.nav('stores');
    await expect.poll(() => pathOf(page)).toBe('/stores/mine');
    await walk.click(page.getByTestId('stores.mine.store').filter({ hasText: S01.name }).first());
    await expect(page.getByTestId('stores.page.tabs')).toBeVisible();
    await expect(page.getByTestId('stores.page.name')).toHaveText(S01.name);
    if (!phone) {
      // "Betriebe" is current by route prefix; the store tabs stick to the top (no header above them)
      await expect(page.getByTestId('nav.stores')).toHaveAttribute('aria-current', 'page');
      expect(await page.getByTestId('stores.page.tabs').evaluate((el) => getComputedStyle(el).getPropertyValue('--fs-header-h').trim())).toBe('0px');
    }
    await expectFitsScreen(page, 'store page');
    await checkpoint(page, 'store');
  });

  if (!phone) {
    await walk.step('folds the side menu to the rail on her laptop', async () => {
      await page.setViewportSize(LAPTOP);
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
      const sidenav = page.getByTestId('nav.sidenav');
      const toggle = page.getByTestId('nav.sidenav.toggle');
      await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
      await expect(toggle).toHaveAttribute('aria-expanded', 'true');
      const before = (await page.locator('#main-content').boundingBox())!.width;
      await walk.click(toggle);
      await expect(sidenav).toHaveAttribute('data-mode', 'rail');
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
      await expect(toggle).toBeFocused();
      // the rail stays next to the content, which gets wider
      await expect.poll(async () => (await page.locator('#main-content').boundingBox())!.width).toBeGreaterThan(before);
      await expectContentBesideMenu(page);
      await checkpoint(page, 'rail', { mask: [page.getByTestId('shell.version')] });
      // "[" expands it again, but not while typing
      await page.getByTestId('dashboard.page.welcome').click();
      await page.keyboard.press('[');
      await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
      await page.getByTestId('search.global.input').click();
      await page.keyboard.press('[');
      await expect(page.getByTestId('search.global.input')).toHaveValue('[');
      await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
      await page.getByTestId('search.global.input').fill('');
      // nothing is persisted: a reload starts expanded
      await collapseSidenav(page);
      await page.reload();
      await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');

      // a short window: sections fold into flyouts so the rail never scrolls
      await page.setViewportSize({ width: 1000, height: 700 });
      await collapseSidenav(page);
      const flyout = page.getByTestId('nav.flyout.community');
      await expect(flyout).toBeVisible();
      expect(await sidenav.evaluate((el) => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1);
      await walk.click(flyout);
      await expect(flyout).toHaveAttribute('aria-expanded', 'true');
      const list = page.getByTestId('nav.flyout.community.list');
      await expectFloatingCard(page, list);
      // Escape right away closes it and keeps the focus on the flyout button
      await page.keyboard.press('Escape');
      await expect(flyout).toHaveAttribute('aria-expanded', 'false');
      await expect(flyout).toBeFocused();
      await walk.click(flyout);
      await checkpointElement(list, 'rail-flyout');
      // choosing an item closes the flyout; the folded section of the current page is marked
      await walk.click(list.getByTestId('nav.map'));
      await expect(flyout).toHaveAttribute('aria-expanded', 'false');
      await expect.poll(() => pathOf(page)).toBe('/map');
      await expect(page.getByTestId('nav.map')).toHaveAttribute('aria-current', 'page');
      await expect(page.locator('[data-section="community"]')).toHaveClass(/sn__section--current/);
      await page.setViewportSize({ width: 1280, height: 800 });
      await walk.nav('dashboard');
      await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    });
  }

  await walk.step('switches the language for a moment and back', async () => {
    if (phone) {
      await walk.click(page.getByTestId('nav.tab.menu'));
      const drawer = page.locator('.fs-mobile-menu');
      await walk.click(drawer.getByTestId('shell.drawer.language.fr'));
      await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
      await expect(drawer.getByTestId('shell.drawer.language.fr')).toHaveAttribute('aria-pressed', 'true');
      await walk.click(drawer.getByTestId('shell.drawer.theme.dark'));
      await expect(page.locator('html')).toHaveClass(/fs-dark/);
      await walk.click(drawer.getByTestId('shell.drawer.theme.light'));
      await expect(page.locator('html')).not.toHaveClass(/fs-dark/);
      await walk.click(drawer.getByTestId('shell.drawer.language.de'));
      await expect(page.locator('html')).toHaveAttribute('lang', 'de');
      await page.keyboard.press('Escape');
      await expect(drawer).toBeHidden();
    } else {
      // choosing keeps the card open, an outside click closes it
      const user = page.getByTestId('shell.userMenu');
      await walk.click(user);
      await walk.click(page.getByTestId('shell.userMenu.language'));
      await walk.click(page.getByTestId('shell.userMenu.language.fr'));
      await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
      await walk.click(page.getByTestId('shell.userMenu.language.de'));
      await expect(page.locator('html')).toHaveAttribute('lang', 'de');
      await page.getByTestId('dashboard.page.welcome').click();
      await expect(page.getByTestId('shell.userMenu.card')).toBeHidden();
    }
  });

  await walk.step('plans a sleep mode with typed and picked dates', async () => {
    if (phone) {
      await walk.nav('account');
    } else {
      const user = page.getByTestId('shell.userMenu');
      await walk.click(user);
      await expect(user).toHaveAttribute('aria-expanded', 'true');
      await walk.click(page.getByTestId('shell.userMenu.card').getByTestId('nav.user.account'));
    }
    await expect.poll(() => pathOf(page)).toBe('/account/settings');
    // the calendar opens on the month of the browser clock
    await page.clock.setFixedTime(new Date(SEED_NOW));
    await expectDataState(page, 'account.settings.sleep.state', 'success');
    await walk.click(page.getByTestId('account.settings.sleep.status.TEMPORARY'));

    // typed: dd.mm.yyyy on an en-US browser too; unreadable text keeps the last value, lenient text is read
    const from = page.getByTestId('account.settings.sleep.from');
    await expect(dateField(from)).toHaveAttribute('placeholder', /^[A-Za-z]{2}\.[A-Za-z]{2}\.[A-Za-z]{4}$/);
    await fillDate(from, '2026-11-05');
    await expect(dateField(from)).toHaveValue('05.11.2026');
    await walk.fill(dateField(from), '31.02.2026');
    await dateField(from).press('Tab');
    await expect(from.getByRole('alert')).toBeVisible();
    await expect(dateField(from)).toHaveAttribute('aria-invalid', 'true');
    await checkpoint(page, 'unreadable-date');
    await walk.fill(dateField(from), '6.11.26');
    await dateField(from).press('Tab');
    await expect(from.getByRole('alert')).toHaveCount(0);
    await expect(dateField(from)).toHaveValue('06.11.2026');

    // picked from the calendar behind the icon; Escape closes it, the icon opens it again
    const until = page.getByTestId('account.settings.sleep.until');
    const trigger = until.locator('button.fs-date-input-trigger');
    const panel = page.locator('.p-datepicker-panel');
    await walk.click(trigger);
    await expect(panel).toBeVisible();
    await expect(panel.locator('.p-datepicker-select-month')).toHaveText(/November/);
    await page.keyboard.press('Escape');
    await expect(panel).toHaveCount(0);
    await walk.click(trigger);
    await expect(panel).toBeVisible();
    await walk.click(
      panel
        .locator('td:not(.p-datepicker-other-month) > span, td:not(.p-datepicker-other-month) [data-pc-section="day"]')
        .filter({ hasText: /^20$/ })
        .first(),
    );
    await expect(panel).toHaveCount(0);
    await expect(dateField(until)).toHaveValue('20.11.2026');

    await walk.click(page.getByTestId('account.settings.sleep.save'));
    await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'TEMPORARY');
    const me = await apiOf(page).getJson<{ sleepFrom?: string; sleepUntil?: string }>('/api/users/me');
    expect(me).toMatchObject({ sleepFrom: '2026-11-06', sleepUntil: '2026-11-20' });
    await checkpoint(page, 'sleep-mode-saved', { toasts: true });
  });

  // measured 2026-11-15
  walk.finish(phone ? { steps: 6, clicks: 19, fields: 2 } : { steps: 7, clicks: 24, fields: 2 });
});
