import { expect, type Locator, type Page } from '@playwright/test';

/** Shell helpers of the shell journeys (moved from tests/shell/d4-sidenav.spec.ts and phone-width.spec.ts). */

/** The path of the current URL. */
export function pathOf(page: Page): string {
  return new URL(page.url()).pathname;
}

async function box(locator: Locator) {
  const b = await locator.boundingBox();
  expect(b, 'element has a box').not.toBeNull();
  return b!;
}

/**
 * A side menu card (user menu, bells, region switcher, rail flyout, visitor preferences) opens outside the side
 * menu's right edge, over the content, without a scrim: the content next to it stays reachable.
 */
export async function expectFloatingCard(page: Page, card: Locator): Promise<void> {
  await expect(card).toBeVisible();
  const menu = await box(page.getByTestId('nav.sidenav'));
  const cardBox = await box(card);
  expect(cardBox.x, 'card starts right of the side menu').toBeGreaterThanOrEqual(menu.x + menu.width);
  const probe = {
    x: Math.min(cardBox.x + cardBox.width + 40, (page.viewportSize()?.width ?? 1280) - 10),
    y: cardBox.y + 10,
  };
  const inMain = await page.evaluate(({ x, y }) => !!document.elementFromPoint(x, y)?.closest('#main-content, fs-footer'), probe);
  expect(inMain, 'content next to the card is not covered').toBe(true);
}

/** The content sits right of the side menu (expanded or rail), never under it. */
export async function expectContentBesideMenu(page: Page): Promise<void> {
  const menu = await box(page.getByTestId('nav.sidenav'));
  expect((await box(page.locator('#main-content'))).x).toBeGreaterThanOrEqual(menu.x + menu.width);
}

/** Page overflow in pixels, with the elements that stick out on the right and aren't inside a scrolling box. */
async function overflow(page: Page): Promise<{ over: number; culprits: string[] }> {
  return page.evaluate(() => {
    const root = document.documentElement;
    const scrolls = (el: Element | null): boolean => {
      for (let node = el?.parentElement; node && node !== document.body; node = node.parentElement) {
        if (['auto', 'scroll', 'hidden'].includes(getComputedStyle(node).overflowX)) return true;
      }
      return false;
    };
    const culprits = [...document.body.querySelectorAll('*')]
      .filter((el) => el.getBoundingClientRect().right > root.clientWidth + 0.5 && !scrolls(el))
      .slice(0, 12)
      .map((el) => `${el.tagName.toLowerCase()}.${String(el.getAttribute('class') ?? '')} ${Math.round(el.getBoundingClientRect().right)}`);
    return { over: root.scrollWidth - root.clientWidth, culprits };
  });
}

/** Nothing pushes the layout viewport past the screen (it would move the fixed tab bar); names the culprits. */
export async function expectFitsScreen(page: Page, what: string): Promise<void> {
  await expect
    .poll(async () => (await overflow(page)).over, { message: `${what}: ${JSON.stringify(await overflow(page))}` })
    .toBeLessThanOrEqual(0);
}

/**
 * A data table shows all its columns without scrolling sideways: on a phone its rows are blocks
 * (`shared/styles/_table-stack.scss`), so no column hides behind the right edge of its card.
 */
export async function expectTableFits(table: Locator): Promise<void> {
  await expect(table).toBeVisible();
  await expect
    .poll(() => table.evaluate((el) => el.scrollWidth - (el.parentElement?.clientWidth ?? el.scrollWidth)), {
      message: 'table wider than its container',
    })
    .toBeLessThanOrEqual(1);
}

/** `inner` lies within the left and right edges of `outer` (text that ran out of its dialog or chip on a phone). */
export async function expectWithin(inner: Locator, outer: Locator, what: string): Promise<void> {
  // both boxes are read on every poll: dialogs scale in while they open
  const edges = async () => {
    const outerBox = await box(outer);
    const text = await inner.evaluate((el) => {
      const range = document.createRange();
      range.selectNodeContents(el);
      const r = range.getBoundingClientRect();
      return { left: r.left, right: r.right };
    });
    return { text, outer: { left: outerBox.x, right: outerBox.x + outerBox.width } };
  };
  await expect
    .poll(
      async () => {
        const { text, outer: o } = await edges();
        return text.left >= o.left - 0.5 && text.right <= o.right + 0.5;
      },
      { message: `${what} sticks out sideways: ${JSON.stringify(await edges().catch(() => null))}` },
    )
    .toBe(true);
}
