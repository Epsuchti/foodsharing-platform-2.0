import { expect, type Locator, type Page } from '@playwright/test';

/** Below this width the side menu is not rendered and the mobile tab bar + menu drawer take over (48rem). */
export const PHONE_MAX = 767;
/** From 75rem (1200px) the desktop side menu is always expanded; below it can be collapsed to the rail. */
export const SIDENAV_WIDE_MIN = 1200;

/**
 * Opens a main navigation item the way a user would, on every layout:
 * - phones: the bottom tab when the item is a tab, otherwise "Menu" → drawer entry (`nav.drawer.<id>`);
 * - desktop (decision 133): the side menu link `nav.<id>`. A link in a closed section (Orga is closed by default)
 *   opens its section heading `nav.section.<section>` first; in the collapsed rail a folded section opens its flyout
 *   `nav.flyout.<section>` first.
 * Returns the number of clicks it took.
 */
export async function openNav(page: Page, id: string): Promise<number> {
  const width = page.viewportSize()?.width ?? 1280;
  if (width <= PHONE_MAX) {
    const tab = page.getByTestId(`nav.tab.${id}`);
    await expect(page.getByTestId('shell.tabBar')).toBeVisible();
    if (await tab.isVisible()) {
      await tab.click();
      return 1;
    }
    await page.getByTestId('nav.tab.menu').click();
    await page.getByTestId(`nav.drawer.${id}`).click();
    return 2;
  }
  const link = page.getByTestId('nav.sidenav').getByTestId(`nav.${id}`);
  // the item list settles once session and feature toggles are loaded
  await expect(link).toBeAttached();
  if (await link.isVisible()) {
    await link.click();
    return 1;
  }
  const section = await link.getAttribute('data-nav-section');
  expect(section, `nav.${id} carries its side menu section`).toBeTruthy();
  const flyout = page.getByTestId(`nav.flyout.${section}`);
  if (await flyout.isVisible()) {
    await flyout.click();
    await expect(flyout).toHaveAttribute('aria-expanded', 'true');
    await link.click();
    await expect(flyout).toHaveAttribute('aria-expanded', 'false');
    return 2;
  }
  const heading = page.getByTestId(`nav.section.${section}`);
  await expect(heading).toHaveAttribute('aria-expanded', 'false');
  await heading.click();
  await expect(heading).toHaveAttribute('aria-expanded', 'true');
  await link.click();
  return 2;
}

/**
 * Collapses the side menu to the icon rail (only between 48 and 75rem; nothing is persisted, so it is done after
 * every page load).
 */
export async function collapseSidenav(page: Page): Promise<void> {
  const sidenav = page.getByTestId('nav.sidenav');
  if ((await sidenav.getAttribute('data-mode')) === 'rail') return;
  await page.getByTestId('nav.sidenav.toggle').click();
  await expect(sidenav).toHaveAttribute('data-mode', 'rail');
}

/**
 * The journey progress newcomers see on every page: the journey bar under the header on phones, the meter on the
 * "Mein Einstieg" item of the desktop side menu (decision 133: no bar above the content on desktop). Both carry the
 * current step key in `data-current`.
 */
export function journeyProgress(page: Page): Locator {
  const width = page.viewportSize()?.width ?? 1280;
  return width <= PHONE_MAX
    ? page.getByTestId('onboarding.bar.stepper')
    : page.getByTestId('nav.onboarding.progress');
}

/** The container of the journey progress (gone once the journey is complete): bar on phones, meter on desktop. */
export function journeyIndicator(page: Page): Locator {
  const width = page.viewportSize()?.width ?? 1280;
  return width <= PHONE_MAX
    ? page.getByTestId('onboarding.bar')
    : page.getByTestId('nav.onboarding.progress');
}
