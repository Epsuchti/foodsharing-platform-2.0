import { expect, test } from '../../fixtures';

/** Every bell shows an icon (UI polish #1): server icons are Lucide names, mapped to the icon font. */

test.use({ role: 'foodsaver' });

test('N3 no bell has an empty icon @N3', async ({ page }) => {
  await page.goto('/bells');
  await expect(page.getByTestId('bells.page.state')).toHaveAttribute('data-state', 'success');
  const icons = page.locator('[data-testid="bells.page.item"] .bell-item__icon i');
  await expect(icons.first()).toBeVisible();
  const glyphs = await icons.evaluateAll((elements) =>
    elements.map((element) => ({ cls: element.className, content: getComputedStyle(element, '::before').content })),
  );
  expect(glyphs.length).toBeGreaterThan(5);
  expect(glyphs.filter((glyph) => glyph.content === 'none' || glyph.content === '""' || glyph.content === '')).toEqual([]);
});
