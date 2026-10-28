import { expect, test } from '../../fixtures';

/** EA13: guides comment on a booking (how did it go); the comment count is shown on the slot. */
test.use({ seedReset: 'test', role: 'guide' });

test('EA13 guide comments on a past booking @EA13', async ({ page }) => {
  await page.goto('/guide');
  await page.getByTestId('introPickups.guide.showPast').click();
  await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
  const slot = page.locator('[data-testid="introPickups.slot"][data-status="DONE"]').first();
  const before = Number((await slot.getByTestId('introPickups.slot.commentCount').textContent().catch(() => '0')) ?? '0');
  await slot.getByTestId('introPickups.slot.comments').click();

  const drawer = page.getByTestId('introPickups.comments');
  await expect(drawer.getByTestId('introPickups.comments.state')).toHaveAttribute('data-state', /success|empty/);
  const items = drawer.getByTestId('introPickups.comments.item');
  const count = await items.count();
  await drawer.getByTestId('introPickups.comments.text').fill('Sehr motiviert, hat gut mitgeholfen.');
  await drawer.getByTestId('introPickups.comments.send').click();
  await expect(items).toHaveCount(count + 1);
  await expect(items.last()).toContainText('Sehr motiviert');
  await page.keyboard.press('Escape');
  await expect(slot.getByTestId('introPickups.slot.commentCount')).toHaveText(String(before + 1));
});
