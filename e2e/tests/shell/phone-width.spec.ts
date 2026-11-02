import type { Page } from '@playwright/test';
import { expect, expectDataState, test } from '../../fixtures';
import { openStore } from '../stores/_stores';
import { ZH } from '../intro-pickups/_intro';

/** Pages that were wider than a phone: nothing may push the layout viewport past the screen (it moves the fixed tab bar). */
test.use({ viewport: { width: 360, height: 740 } });

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

async function expectFitsPhone(page: Page): Promise<void> {
  await expect.poll(async () => (await overflow(page)).over, { message: JSON.stringify(await overflow(page)) }).toBeLessThanOrEqual(0);
}

test.describe('bot', () => {
  test.use({ role: 'bot' });

  test('poll form fits a phone @V1', async ({ page }) => {
    await page.goto(`/polls/new?regionId=${ZH}`);
    await expect(page.getByTestId('polls.form.option').first()).toBeVisible();
    await expectFitsPhone(page);
  });

  test('intro pickups admin fits a phone @EA18', async ({ page }) => {
    await page.goto(`/regions/${ZH}/intro-pickups/admin`);
    await expect(page.getByTestId('introPickups.admin.einab').first()).toBeVisible();
    await expectFitsPhone(page);
  });
});

test.describe('orga', () => {
  test.use({ role: 'orga' });

  test('store categories fit a phone @S4', async ({ page }) => {
    await page.goto('/admin/store-categories');
    await expect(page.locator('table').first()).toBeVisible();
    await expectFitsPhone(page);
  });
});

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('store statistics fit a phone @S14', async ({ page }) => {
    await openStore(page, 'S-01', 'statistics');
    await expectDataState(page, 'stores.statistics.state', 'success');
    await expect(page.getByTestId('stores.statistics.member').first()).toBeVisible();
    await expectFitsPhone(page);
  });
});
