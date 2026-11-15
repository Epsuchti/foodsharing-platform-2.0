import { acceptPendingConsents } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { einabId } from '../../fixtures/flows/einab';
import { Walk } from '../../fixtures/persona';

/**
 * Guide Julien (guide@local) books newcomer Chiara (quiz@local) onto his supermarket EinAb on her behalf, and she sees
 * the booking. He hands another EinAb over to Reto. Two days after the date he marks Chiara's EinAb done and writes how
 * it went; it counts towards her progress. Rules behind it: IntroPickupGuideServiceRulesTest (no done before the date),
 * IntroPickupLinkedPickupTest (linked EinAbs are not handed over).
 */
test.use({ role: 'guide' });

test('guide books a newcomer on her behalf, hands an EinAb over, marks it done and comments @EA12 @EA13 @EA14', async ({
  page,
  as,
  apiOf,
  clock,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'guide');
  const supermarket = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(1)}"]`);
  const second = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(9)}"]`);
  const chiara = await as('quiz');
  await acceptPendingConsents(apiOf(chiara));

  await walk.step('books Chiara onto his supermarket EinAb', async () => {
    await page.goto('/guide');
    await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
    await expect(supermarket.getByTestId('introPickups.slot')).toHaveCount(3);
    await walk.click(supermarket.getByTestId('introPickups.slot.bookFor').first());
    const pick = page.getByTestId('introPickups.pick');
    await walk.fill(pick.getByTestId('introPickups.pick.search'), 'Chiara');
    const choose = pick.getByTestId('introPickups.pick.choose');
    await expect(choose).toHaveCount(1);
    await checkpoint(page, 'book-for-dialog');
    await walk.click(choose);
    await expect(pick).toBeHidden();
    const booked = supermarket.locator('[data-testid="introPickups.slot"][data-status="BOOKED"]');
    await expect(booked).toHaveCount(1);
    await expect(booked).toContainText('Chiara');
    await checkpoint(page, 'booked-for-chiara');
  });

  // Chiara finds the booking among hers
  await chiara.goto('/me/intro-pickups');
  await expect(chiara.getByTestId('introPickups.my.upcomingCard')).toHaveCount(1);

  await walk.step('hands another EinAb over to Reto', async () => {
    await walk.click(second.getByTestId('introPickups.guide.assign'));
    const assign = page.getByTestId('introPickups.assign');
    await walk.click(assign.getByTestId('introPickups.assign.guide'));
    await walk.click(page.getByRole('option', { name: 'Reto Baumann' }));
    await checkpoint(page, 'assign-dialog');
    await walk.click(assign.getByTestId('introPickups.assign.save'));
    await expect(second).toHaveCount(0);
  });

  // two days after the date
  await clock.fix(page, '2026-11-05T09:00:00Z');

  await walk.step('marks Chiara\'s EinAb done', async () => {
    await page.goto('/guide');
    await walk.click(page.getByTestId('introPickups.guide.showPast'));
    await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
    const slot = supermarket.locator('[data-testid="introPickups.slot"][data-status="BOOKED"]');
    await expect(slot).toContainText('Chiara');
    await walk.click(slot.getByTestId('introPickups.slot.done'));
    await expect(supermarket.locator('[data-testid="introPickups.slot"][data-status="DONE"]')).toHaveCount(1);
    await checkpoint(page, 'marked-done');
  });

  await walk.step('writes how it went', async () => {
    const slot = supermarket.locator('[data-testid="introPickups.slot"][data-status="DONE"]');
    await walk.click(slot.getByTestId('introPickups.slot.comments'));
    const drawer = page.getByTestId('introPickups.comments');
    await expect(drawer.getByTestId('introPickups.comments.state')).toHaveAttribute('data-state', /success|empty/);
    const items = drawer.getByTestId('introPickups.comments.item');
    await expect(items).toHaveCount(0);
    await walk.fill(drawer.getByTestId('introPickups.comments.text'), 'Sehr motiviert, hat gut mitgeholfen.');
    await walk.click(drawer.getByTestId('introPickups.comments.send'));
    await expect(items).toHaveCount(1);
    await expect(items.last()).toContainText('Sehr motiviert');
    await checkpoint(page, 'comment-written');
    await page.keyboard.press('Escape');
    await expect(slot.getByTestId('introPickups.slot.commentCount')).toHaveText('1');
  });

  // it counts towards Chiara's progress
  await chiara.goto('/me/intro-pickups');
  await expect(chiara.getByTestId('introPickups.my.progress')).toHaveAttribute('data-done', '1');
  await expect(chiara.getByTestId('introPickups.my.pastRow')).toHaveAttribute('data-status', 'DONE');
  await expect(chiara.getByTestId('introPickups.my.counted')).toBeVisible();

  walk.finish({ steps: 4, clicks: 10, fields: 2 });
});
