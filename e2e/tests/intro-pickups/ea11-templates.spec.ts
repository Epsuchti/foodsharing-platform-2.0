import { expect, test } from '../../fixtures';

/** EA11: private info templates per category – insert, save as template, manage in the guide settings. */
test.use({ seedReset: 'test', role: 'guide' });

test('EA11 private info templates are inserted, saved and deleted @EA11', async ({ page }) => {
  await page.goto('/guide');
  await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
  await page.getByTestId('introPickups.guide.create').click();
  const form = page.getByTestId('introPickups.form');
  // SUPERMARKET (default) has a seeded template
  await form.getByTestId('introPickups.form.useTemplate').click();
  await expect(form.getByTestId('introPickups.form.privateInfo')).not.toHaveValue('');

  // a new template for TAKEOUT, saved together with a new EinAb
  await form.getByTestId('introPickups.form.category').click();
  await page.getByRole('option', { name: 'Vente à emporter' }).click();
  await expect(form.getByTestId('introPickups.form.useTemplate')).toHaveCount(0);
  await form.getByTestId('introPickups.form.start').fill('2026-11-25T19:00');
  await form.getByTestId('introPickups.form.businessName').fill('Tiny Fish Oerlikon');
  await form.getByTestId('introPickups.form.address').fill('Schaffhauserstrasse 300, 8050 Zürich');
  await form.getByTestId('introPickups.form.area').fill('Oerlikon');
  await form.getByTestId('introPickups.form.privateInfo').fill('Hintereingang, bitte klingeln.');
  await form.getByTestId('introPickups.form.saveTemplate').click();
  await page.getByTestId('introPickups.form.save').click();
  await expect(form).toBeHidden();

  await page.getByTestId('introPickups.guide.settings').click();
  await expect(page).toHaveURL(/\/guide\/settings$/);
  const templates = page.getByTestId('introPickups.settings.template');
  await expect(templates).toHaveCount(3);
  const takeout = templates.filter({ hasText: 'Hintereingang' });
  await expect(takeout).toHaveAttribute('data-category', 'TAKEOUT');
  await takeout.getByTestId('introPickups.settings.deleteTemplate').click();
  await expect(templates).toHaveCount(2);
});
