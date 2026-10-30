import { expect, type Page } from '@playwright/test';

/** Below this width the header nav is hidden and the mobile tab bar + menu drawer take over (48rem). */
const PHONE_MAX = 767;

/**
 * Opens a main navigation item the way a user would, on every layout (design review M7 round 3):
 * - phones: the bottom tab when the item is a tab, otherwise "Menu" → drawer entry (`nav.drawer.<id>`);
 * - desktop: the header link `nav.<id>`; items beyond the first ones live in the header's "More" menu (`nav.more`),
 *   which is opened first.
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
  const link = page.getByTestId(`nav.${id}`);
  // the item list settles once session and feature toggles are loaded (an item may move between header and menu)
  await expect(link).toBeAttached();
  if (await link.isVisible()) {
    await link.click();
    return 1;
  }
  const more = page.getByTestId('nav.more');
  await more.click();
  await expect(more).toHaveAttribute('aria-expanded', 'true');
  await link.click();
  await expect(more).toHaveAttribute('aria-expanded', 'false');
  return 2;
}
