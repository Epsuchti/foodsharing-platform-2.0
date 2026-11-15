import type { Page } from '@playwright/test';
import { S01_THURSDAY, S04_WEDNESDAY, S07_WEDNESDAY, expectDataState, seedStore } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local) plans her week from the home screen (A19): "my next pickups" (S-04 Denner
 * Wednesday, S-01 Coop Thursday), the /pickups page with "my pickups" above "where can I help?" (PU7, 18 options),
 * the day agenda (PU8) and her pickup history (PU6, 24 past pickups). She signs up for Hiltl's free Wednesday from the
 * options and for the first free pickup from the home card "help needed soon". An administrator (orga@local) later
 * removes all her future pickups when she moves away (PU6, decision 165: never on the own history).
 */
const S01 = seedStore('S-01');
const S04 = seedStore('S-04');

/**
 * Cards below the fold load when their placeholder comes into view (`@defer (on viewport)`). The placeholder is
 * replaced as soon as it is seen, possibly while it is being scrolled to, so retry until the card exists.
 */
async function reveal(page: Page, card: 'help' | 'events' | 'stores') {
  const element = page.getByTestId(`dashboard.${card}`);
  await expect(async () => {
    if (!(await element.count())) await page.locator(`.defer-slot.o-${card}`).scrollIntoViewIfNeeded({ timeout: 1_000 });
    await expect(element).toBeAttached({ timeout: 1_000 });
  }).toPass();
  await element.scrollIntoViewIfNeeded();
  return element;
}

const option = (page: Page, store: string, pickupAt: string) =>
  page.locator(`[data-testid="pickups.options.option"][data-store="${store}"][data-pickup-at="${pickupAt}"]`);

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('foodsaver plans her week from home, pickups page, agenda and history @A19 @PU7 @PU8 @PU6 @PU3', async ({ page }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'foodsaver');

    await walk.step('checks her next pickups on the home screen', async () => {
      await page.goto('/dashboard');
      await expectDataState(page, 'dashboard.pickups.state', 'success');
      const items = page.getByTestId('dashboard.pickups.pickup');
      await expect(items).toHaveCount(2);
      await expect(items.first()).toHaveAttribute('data-store', S04.name);
      await expect(items.first().locator('time')).toHaveAttribute('datetime', /\d{4}-\d{2}-\d{2}T/);
      // the free-slot count moved to "Help needed soon"
      await expect(page.getByTestId('dashboard.pickups.free')).toHaveCount(0);
      await expectDataState(page, 'dashboard.waiting.state', 'success');
      await expect(page.getByTestId('dashboard.waiting.unread')).toHaveAttribute('data-count', '2');
      await checkpoint(page, 'dashboard');
    });

    await walk.step('opens all her pickups', async () => {
      await walk.click(page.getByTestId('dashboard.pickups.all'));
      await expect(page).toHaveURL(/\/pickups#mine$/);
      await expectDataState(page, 'pickups.mine.state', 'success');
      await expectDataState(page, 'pickups.options.state', 'success');
      await expect(page.getByTestId('pickups.mine.pickup')).toHaveCount(2);
      await expect(page.getByTestId('pickups.mine.showMore')).toHaveCount(0);
      await expect(page.getByTestId('pickups.options.option')).toHaveCount(10);
      // own sign-ups stay in the options as a state (review fix A2) with the confirmation summary
      const wednesday = option(page, S04.name, S04_WEDNESDAY).getByTestId('pickups.options.signedUp');
      await expect(wednesday).toHaveText('Eingetragen · bestätigt');
      const thursday = option(page, S01.name, S01_THURSDAY);
      await expect(thursday.getByTestId('pickups.options.signedUp')).toHaveAttribute('data-confirmed', 'false');
      await expect(thursday.getByTestId('pickups.options.signUp')).toHaveCount(0);
      await expect(thursday.getByTestId('pickups.options.summary')).toContainText('1 von 3 bestätigt');
      // review 2026-11-10 F1: the chip's icon sits beside its text
      const icon = (await wednesday.locator('i').boundingBox())!;
      const text = (await wednesday.locator('span').boundingBox())!;
      expect(Math.abs(text.x - (icon.x + icon.width))).toBeLessThanOrEqual(8);
      await checkpoint(page, 'pickups-page');
      const total = Number(await page.getByTestId('pickups.options.count').textContent());
      expect(total).toBeGreaterThan(10);
      await walk.click(page.getByTestId('pickups.options.showMore'));
      await expect(page.getByTestId('pickups.options.option')).toHaveCount(Math.min(total, 20));
      // the focus moved into the revealed cards
      await expect(page.getByTestId('pickups.options.option').nth(10).getByRole('link')).toBeFocused();
    });

    await walk.step('goes through her days in the agenda', async () => {
      await walk.click(page.getByTestId('pickups.mine.agenda'));
      await expect(page).toHaveURL(/\/pickups\/agenda\/2026-11-04$/);
      await expectDataState(page, 'pickups.agenda.state', 'success');
      await expect(page.locator(`[data-testid="pickups.agenda.pickup"][data-store="${S04.name}"]`)).toBeVisible();
      await checkpoint(page, 'agenda');
      await walk.click(page.getByTestId('pickups.agenda.next'));
      await expect(page).toHaveURL(/2026-11-05$/);
      await expect(page.locator(`[data-testid="pickups.agenda.pickup"][data-store="${S01.name}"]`)).toBeVisible();
      await expect(page.locator(`[data-testid="pickups.agenda.pickup"][data-store="${S04.name}"]`)).toHaveCount(0);
      await walk.click(page.getByTestId('pickups.agenda.next'));
      await expectDataState(page, 'pickups.agenda.state', 'empty');
      await checkpoint(page, 'agenda-empty-day');
      // decision 165: the header leads back to the pickups page
      await walk.click(page.getByTestId('shared.pageHeader.back'));
      await expect(page).toHaveURL(/\/pickups$/);
    });

    await walk.step('looks back at her past pickups', async () => {
      await expectDataState(page, 'pickups.mine.state', 'success');
      await walk.click(page.getByTestId('pickups.mine.history'));
      await expect(page).toHaveURL(/\/me\/pickups\/history$/);
      await expectDataState(page, 'pickups.past.state', 'success');
      await expect(page.getByTestId('pickups.past.count')).toContainText('24');
      await expect(page.getByTestId('pickups.past.pickup')).toHaveCount(24);
      const shown = await page.getByTestId('pickups.past.pickup').evaluateAll((rows) => rows.map((row) => row.getAttribute('data-pickup-at')!));
      expect(shown).toEqual([...shown].sort().reverse());
      // decision 165: no "sign out of all my future pickups" on the own history
      await expect(page.getByTestId('pickups.past.deleteFuture')).toHaveCount(0);
      await checkpoint(page, 'history');
    });

    await walk.step('signs up for Hiltl on Wednesday from "where can I help?"', async () => {
      await page.goBack();
      await expectDataState(page, 'pickups.options.state', 'success');
      const hiltl = option(page, 'Hiltl', S07_WEDNESDAY);
      await hiltl.scrollIntoViewIfNeeded();
      await walk.click(hiltl.getByTestId('pickups.options.signUp'));
      await expectDataState(page, 'pickups.signup.state', 'success');
      await walk.click(page.getByTestId('pickups.signup.confirm'));
      await expect(page.getByTestId('shell.toast').locator('.p-toast-message-success')).toBeVisible();
      // "my pickups" above reloads with the new, unconfirmed pickup; the option turns into a state
      await expectDataState(page, 'pickups.mine.state', 'success');
      await expect(page.locator('[data-testid="pickups.mine.pickup"][data-store="Hiltl"]')).toHaveAttribute('data-confirmed', 'false');
      await expectDataState(page, 'pickups.options.state', 'success');
      await expect(hiltl.getByTestId('pickups.options.signedUp')).toHaveText('Eingetragen · noch unbestätigt');
      await expect(hiltl.getByTestId('pickups.options.signUp')).toHaveCount(0);
      await checkpoint(page, 'options-signed-up');
    });

    await walk.step('helps out from the home card "help needed soon"', async () => {
      await page.goto('/dashboard');
      const help = await reveal(page, 'help');
      await expectDataState(page, 'dashboard.help.state', 'success');
      await expect(help.getByTestId('dashboard.help.option')).toHaveCount(3);
      await expect(help.getByTestId('dashboard.help.all')).toHaveAttribute('href', '/pickups#options');
      const first = help.getByTestId('dashboard.help.option').first();
      const pickupAt = await first.getAttribute('data-pickup-at');
      await walk.click(first.getByTestId('dashboard.help.signUp'));
      await expectDataState(page, 'pickups.signup.state', 'success');
      await walk.click(page.getByTestId('pickups.signup.confirm'));
      // review fix A2: the card reloads and shows the own sign-up as a state instead of "Eintragen"
      const signedUp = help.locator(`[data-testid="dashboard.help.option"][data-pickup-at="${pickupAt}"]`);
      await expect(signedUp.getByTestId('dashboard.help.signedUp')).toHaveText('Eingetragen · noch unbestätigt');
      await expect(signedUp.getByTestId('dashboard.help.signUp')).toHaveCount(0);
      await checkpointElement(help, 'help-card-signed-up');
    });

    walk.finish({ steps: 6, clicks: 11, fields: 0 });
  });
});

test.describe('orga', () => {
  test.use({ role: 'orga' });

  test('an administrator removes all future pickups of a foodsaver who moves away @PU6 @D10.3', async ({ page, as, apiOf, dev }) => {
    const me = await dev.user('foodsaver');
    const foodsaver = apiOf(await as('foodsaver'));
    expect((await foodsaver.getJson<unknown[]>('/api/users/me/pickups')).length).toBe(2);
    await page.goto(`/users/${me.id}`);
    await page.getByTestId('usersAdmin.user.pickups').click();
    await expect(page).toHaveURL(new RegExp(`/users/${me.id}/pickups$`));
    await expectDataState(page, 'pickups.past.state', 'success');
    await expect(page.getByTestId('pickups.past.count')).toContainText('24');
    await page.getByTestId('pickups.past.deleteFuture').click();
    await page.getByTestId('pickups.past.deleteDialog.message').fill('Zieht weg.');
    await checkpoint(page, 'delete-future-dialog');
    await page.getByTestId('pickups.past.deleteDialog.confirm').click();
    await expect(page.getByTestId('pickups.past.deleteDialog.message')).toBeHidden();
    await expect.poll(async () => (await foodsaver.getJson<unknown[]>('/api/users/me/pickups')).length).toBe(0);
  });
});
