import { collapseSidenav } from '../../fixtures';
import { animationsDone } from '../../fixtures/a11y';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { expectFloatingCard, pathOf } from '../../fixtures/flows/shell';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * A visitor (not signed in) looks around before signing up (D4.3, decision 133): on desktop the side menu with the
 * public pages, the preferences card (theme) and the login buttons, also as the icon rail on a laptop-wide window,
 * and the skip link to the content; on phones the login button and the visitor menu drawer instead of a tab bar.
 * `/` is prerendered with the phone header: hydrating it reports no Angular mismatch.
 */
test('visitor looks around the side menu or the phone menu and opens the map @D4.3', async ({ page, browserName }, testInfo) => {
  test.setTimeout(90_000);
  const walk = new Walk(page, testInfo, 'visitor');
  const phone = isPhone(page);
  const angularErrors: string[] = [];
  page.on('console', (message) => {
    if (/NG0\d+/.test(message.text())) angularErrors.push(message.text());
  });

  await walk.step('opens the start page', async () => {
    await page.goto('/');
    await expect(page.getByTestId('public.landing.hero')).toBeVisible();
    await expect(page.getByTestId('shell.login')).toBeVisible();
    await expect(page.getByTestId('shell.login')).toHaveAttribute('href', '/login');
    await expect(page.getByTestId('shell.tabBar')).toHaveCount(0);
    await expect(page.getByTestId('shell.userMenu')).toHaveCount(0);
    if (phone) {
      await expect(page.getByTestId('nav.home')).toBeHidden();
    } else {
      await expect(page.getByTestId('nav.sidenav')).toBeVisible();
      await expect(page.locator('fs-header')).toHaveCount(0);
      await expect(page.getByTestId('nav.home')).toHaveAttribute('aria-current', 'page');
      await expect(page.getByTestId('nav.map')).toBeVisible();
      await expect(page.getByTestId('shell.register')).toBeVisible();
      await expect(page.getByTestId('shell.visitorMenu.open')).toHaveCount(0);
    }
    expect(angularErrors).toEqual([]);
    await checkpoint(page, 'start', { mask: [page.getByTestId('shell.version')] });
  });

  await walk.step('looks at the menu', async () => {
    if (phone) {
      const open = page.getByTestId('shell.visitorMenu.open');
      await expect(open).toHaveAttribute('aria-expanded', 'false');
      await walk.click(open);
      await expect(open).toHaveAttribute('aria-expanded', 'true');
      // the <p-drawer> host has no box; the panel is appended to <body>
      const drawer = page.locator('.fs-mobile-menu');
      await expect(drawer).toBeVisible();
      await animationsDone(page);
      // the same sections as the desktop side menu for visitors: Entdecken first, then the about pages
      await expect(drawer.getByTestId('shell.visitorMenu.section.daily')).toBeVisible();
      await expect(drawer.getByTestId('shell.visitorMenu.home')).toHaveAttribute('aria-current', 'page');
      await expect(drawer.getByTestId('shell.visitorMenu.login')).toHaveAttribute('href', '/login');
      await checkpoint(page, 'menu');
    } else {
      const prefs = page.getByTestId('shell.prefs');
      await walk.click(prefs);
      await expect(prefs).toHaveAttribute('aria-expanded', 'true');
      await expectFloatingCard(page, page.locator('#sn-card-prefs'));
      await checkpoint(page, 'menu');
      await walk.click(page.getByTestId('shell.prefs.theme.dark'));
      await expect(page.locator('html')).toHaveClass(/fs-dark/);
      await walk.click(page.getByTestId('shell.prefs.theme.light'));
      await expect(page.locator('html')).not.toHaveClass(/fs-dark/);
      await page.keyboard.press('Escape');
      await expect(prefs).toBeFocused();

      // the rail exists for visitors too
      await page.setViewportSize({ width: 1000, height: 800 });
      await collapseSidenav(page);
      walk.count(1);
      await expect(page.getByTestId('shell.login')).toBeVisible();
      await checkpoint(page, 'rail', { mask: [page.getByTestId('shell.version')] });
      await page.setViewportSize({ width: 1280, height: 800 });
    }
  });

  await walk.step('opens the map', async () => {
    if (phone) {
      const drawer = page.locator('.fs-mobile-menu');
      await walk.click(drawer.getByTestId('shell.visitorMenu.map'));
      await expect(drawer).toBeHidden();
    } else {
      // back with the keyboard: the skip link jumps over the menu (Safari tabs to links with Option+Tab)
      await page.reload();
      await expect(page.getByTestId('public.landing.hero')).toBeVisible();
      await page.keyboard.press(browserName === 'webkit' ? 'Alt+Tab' : 'Tab');
      const skip = page.getByTestId('shell.skipLink');
      await expect(skip).toBeFocused();
      await expect(skip).toBeInViewport();
      await page.keyboard.press('Enter');
      await expect(page.locator('#main-content')).toBeFocused();
      await walk.click(page.getByTestId('nav.map'));
    }
    await expect.poll(() => pathOf(page)).toBe('/map');
    await expect(page.getByTestId('shell.login')).toBeVisible();
    await checkpoint(page, 'map');
  });

  // measured 2026-11-15
  walk.finish(phone ? { steps: 3, clicks: 2, fields: 0 } : { steps: 3, clicks: 5, fields: 0 });
});
