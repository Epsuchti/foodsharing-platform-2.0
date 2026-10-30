import type { Locator, Page } from '@playwright/test';
import { collapseSidenav, expect, openNav, seedStore, test } from '../../fixtures';

/**
 * D4.3 shell, desktop side menu (decision 133, docs/design/sidenav-proposal.md §9): next to the content from 48rem,
 * always expanded from 75rem, collapsible to an icon rail between 48 and 75rem (toggle and `[`, nothing persisted),
 * floating cards without a scrim, visitors included. Phones keep the tab bar (d4-shell-navigation.spec.ts).
 */

const WIDE = { width: 1280, height: 800 };
const LAPTOP = { width: 1000, height: 800 };

function path(page: Page): string {
  return new URL(page.url()).pathname;
}

async function box(locator: Locator) {
  const b = await locator.boundingBox();
  expect(b, 'element has a box').not.toBeNull();
  return b!;
}

/** The card opens outside the side menu's right edge, over the content, and the content around it stays reachable. */
async function expectFloatingCard(page: Page, card: Locator): Promise<void> {
  await expect(card).toBeVisible();
  const menu = await box(page.getByTestId('nav.sidenav'));
  const cardBox = await box(card);
  expect(cardBox.x, 'card starts right of the side menu').toBeGreaterThanOrEqual(
    menu.x + menu.width,
  );
  // no scrim: right of the card, the topmost element is page content, not an overlay
  const probe = {
    x: Math.min(cardBox.x + cardBox.width + 40, (page.viewportSize()?.width ?? 1280) - 10),
    y: cardBox.y + 10,
  };
  const inMain = await page.evaluate(
    ({ x, y }) => !!document.elementFromPoint(x, y)?.closest('#main-content, fs-footer'),
    probe,
  );
  expect(inMain, 'content next to the card is not covered').toBe(true);
}

test.describe('signed in', () => {
  test.use({ role: 'foodsaver' });

  test('D4.3 side menu from 75rem: always expanded, no toggle, shortcuts and floating cards @D4.3 @page', async ({
    page,
  }) => {
    await page.setViewportSize(WIDE);
    await page.goto('/dashboard');
    const sidenav = page.getByTestId('nav.sidenav');
    await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
    await expect(page.getByTestId('nav.sidenav.toggle')).toHaveCount(0);
    // the content sits next to the menu, never under it
    const menu = await box(sidenav);
    expect((await box(page.locator('#main-content'))).x).toBeGreaterThanOrEqual(
      menu.x + menu.width,
    );
    // no top header: the sticky bars of the pages stick to the top
    expect(
      await page
        .locator('#main-content')
        .evaluate((el) => getComputedStyle(el).getPropertyValue('--fs-header-h').trim()),
    ).toBe('0px');

    // "[" does nothing from 75rem; Ctrl K focuses the search
    await page.getByTestId('dashboard.page.welcome').click();
    await page.keyboard.press('[');
    await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
    // the search field comes with the feature toggles, after the first page
    await expect(page.getByTestId('search.global.input')).toBeVisible();
    await page.keyboard.press('Control+k');
    await expect(page.getByTestId('search.global.input')).toBeFocused();
    // while typing, "[" is text
    await page.keyboard.type('[');
    await expect(page.getByTestId('search.global.input')).toHaveValue('[');
    await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
    await page.keyboard.press('Escape');
    await page.getByTestId('search.global.input').fill('');

    // the user menu: a card beside the menu, pressed trigger, Escape closes it and returns the focus
    const user = page.getByTestId('shell.userMenu');
    await user.click();
    await expect(user).toHaveAttribute('aria-expanded', 'true');
    await expectFloatingCard(page, page.getByTestId('shell.userMenu.card'));
    await expect(page.getByTestId('shell.userMenu.shortcuts')).toContainText('K');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('shell.userMenu.card')).toBeHidden();
    await expect(user).toHaveAttribute('aria-expanded', 'false');
    await expect(user).toBeFocused();

    // language from the user menu; choosing keeps the card, an outside click closes it
    await user.click();
    await page.getByTestId('shell.userMenu.language').click();
    await page.getByTestId('shell.userMenu.language.fr').click();
    await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
    await page.getByTestId('shell.userMenu.language.de').click();
    await expect(page.locator('html')).toHaveAttribute('lang', 'de');
    await page.getByTestId('dashboard.page.welcome').click();
    await expect(page.getByTestId('shell.userMenu.card')).toBeHidden();

    // the bell panel opens next to the bell item (no page change)
    const bell = page.getByTestId('bells.button');
    await bell.click();
    await expect(bell).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('bells.panel.state')).toHaveAttribute(
      'data-state',
      /^(success|empty)$/,
    );
    await expectFloatingCard(page, page.locator('#sn-card-bells'));
    expect(path(page)).toBe('/dashboard');
    // one card at a time: the region switcher replaces it
    await page.getByTestId('shell.regionSwitcher').click();
    await expect(bell).toHaveAttribute('aria-expanded', 'false');
    await expectFloatingCard(page, page.getByTestId('shell.regionSwitcher.card'));
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('shell.regionSwitcher')).toBeFocused();
  });

  test('D4.3 side menu between 48 and 75rem: collapses to the rail with the toggle and "[", rail flyouts @D4.3 @page', async ({
    page,
  }) => {
    await page.setViewportSize(LAPTOP);
    await page.goto('/dashboard');
    const sidenav = page.getByTestId('nav.sidenav');
    const toggle = page.getByTestId('nav.sidenav.toggle');
    // starts expanded
    await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    const contentBefore = (await box(page.locator('#main-content'))).width;

    await toggle.click();
    await expect(sidenav).toHaveAttribute('data-mode', 'rail');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(toggle).toBeFocused();
    // the rail stays next to the content, which gets wider
    await expect
      .poll(async () => (await box(page.locator('#main-content'))).width)
      .toBeGreaterThan(contentBefore);
    const rail = await box(sidenav);
    expect((await box(page.locator('#main-content'))).x).toBeGreaterThanOrEqual(
      rail.x + rail.width,
    );

    // "[" expands it again, not while typing in a field
    await page.getByTestId('dashboard.page.welcome').click();
    await page.keyboard.press('[');
    await expect(sidenav).toHaveAttribute('data-mode', 'sidebar');
    await page.getByTestId('search.global.input').click();
    await page.keyboard.press('[');
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
    expect(await sidenav.evaluate((el) => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(
      1,
    );
    await flyout.click();
    await expect(flyout).toHaveAttribute('aria-expanded', 'true');
    await expectFloatingCard(page, page.getByTestId('nav.flyout.community.list'));
    await page.keyboard.press('Escape');
    await expect(flyout).toHaveAttribute('aria-expanded', 'false');
    await expect(flyout).toBeFocused();
    // the helper opens the flyout, choosing an item closes it
    expect(await openNav(page, 'map')).toBe(2);
    await expect.poll(() => path(page)).toBe('/map');
    await expect(page.getByTestId('nav.map')).toHaveAttribute('aria-current', 'page');
    // the folded section of the current page is marked
    await expect(page.locator('[data-section="community"]')).toHaveClass(/sn__section--current/);
  });

  test('D4.3 store page: "Betriebe" is current by route prefix and the page fits next to the menu @D4.3', async ({
    page,
  }) => {
    await page.setViewportSize(WIDE);
    await page.goto(`/stores/${seedStore('S-01').id}/overview`);
    await expect(page.getByTestId('stores.page.tabs')).toBeVisible();
    await expect(page.getByTestId('nav.stores')).toHaveAttribute('aria-current', 'page');
    await expect(page.getByTestId('nav.dashboard')).not.toHaveAttribute('aria-current', 'page');
    // the sticky offset of the store tabs starts at the top: there is no header above them
    const headerH = await page
      .getByTestId('stores.page.tabs')
      .evaluate((el) => getComputedStyle(el).getPropertyValue('--fs-header-h').trim());
    expect(headerH).toBe('0px');
    await expect
      .poll(() =>
        page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        ),
      )
      .toBeLessThanOrEqual(0);
  });
});

test.describe('orga', () => {
  test.use({ role: 'orga' });

  test('D4.3 Orga section is closed by default and open when its page is current @D4.3', async ({
    page,
  }) => {
    await page.setViewportSize(WIDE);
    await page.goto('/dashboard');
    const orga = page.getByTestId('nav.section.orga');
    await expect(orga).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByTestId('nav.adminJobs')).toBeHidden();
    await expect(page.getByTestId('nav.section.manage')).toHaveAttribute('aria-expanded', 'true');
    expect(await openNav(page, 'adminJobs')).toBe(2);
    await expect.poll(() => path(page)).toBe('/admin/jobs');
    await expect(page.getByTestId('nav.adminJobs')).toHaveAttribute('aria-current', 'page');
    // a reload on an Orga page starts with Orga open
    await page.reload();
    await expect(page.getByTestId('nav.section.orga')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('nav.adminJobs')).toBeVisible();
  });
});

test.describe('visitor', () => {
  test('D4.3 visitors get the side menu with language and theme and the login buttons @D4.3 @page', async ({
    page,
  }) => {
    await page.setViewportSize(WIDE);
    // `/` is prerendered with the phone header: hydrating it must not report a mismatch, and the server's header
    // must be gone once the side menu took over
    const angularErrors: string[] = [];
    page.on('console', (message) => {
      if (/NG0\d+/.test(message.text())) angularErrors.push(message.text());
    });
    await page.goto('/');
    await expect(page.getByTestId('nav.sidenav')).toBeVisible();
    await expect(page.locator('fs-header')).toHaveCount(0);
    expect(angularErrors).toEqual([]);
    await expect(page.getByTestId('nav.home')).toHaveAttribute('aria-current', 'page');
    await expect(page.getByTestId('nav.map')).toBeVisible();
    await expect(page.getByTestId('shell.userMenu')).toHaveCount(0);
    await expect(page.getByTestId('shell.login')).toHaveAttribute('href', '/login');
    await expect(page.getByTestId('shell.register')).toBeVisible();

    const prefs = page.getByTestId('shell.prefs');
    await prefs.click();
    await expect(prefs).toHaveAttribute('aria-expanded', 'true');
    await expectFloatingCard(page, page.locator('#sn-card-prefs'));
    await page.getByTestId('shell.prefs.theme.dark').click();
    await expect(page.locator('html')).toHaveClass(/fs-dark/);
    await page.getByTestId('shell.prefs.theme.light').click();
    await expect(page.locator('html')).not.toHaveClass(/fs-dark/);
    await page.keyboard.press('Escape');
    await expect(prefs).toBeFocused();

    // the rail exists for visitors too
    await page.setViewportSize(LAPTOP);
    await collapseSidenav(page);
    await expect(page.getByTestId('shell.login')).toBeVisible();
  });
});
