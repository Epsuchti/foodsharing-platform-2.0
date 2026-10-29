import { expect, test } from '../../fixtures';

/**
 * EA16: guides link their own foodsharing calendar (iCal); its dates are offered as EinAb candidates and prefill the
 * form. In production the server only fetches https feeds on port 443 of allowed hosts; the e2e profile also allows
 * the app's own origin (`app.intropickup.ical-allow-own-base-url`, A50), so the server really downloads and parses the
 * dev iCal fixture `/api/dev/fixtures/ical` (two future events at the fixed clock 2026-11-02T09:00Z).
 */
test.use({ seedReset: 'test', role: 'guide' });

test('EA16 iCal dates become EinAb candidates @EA16', async ({ page, baseURL }) => {
  const feed = `${baseURL}/api/dev/fixtures/ical`;
  await page.goto('/guide/settings');
  const state = page.getByTestId('introPickups.settings.profileState');
  await expect(state).toHaveAttribute('data-state', 'success');
  // not an allowed calendar host → refused by the server
  await page.getByTestId('introPickups.settings.icalLink').fill('https://evil.example.com/calendar.ics');
  await page.getByTestId('introPickups.settings.save').click();
  await expect(page.getByTestId('shell.toast')).toContainText(/.+/);
  await page.getByTestId('introPickups.settings.icalLink').fill(feed);
  const saved = page.waitForResponse((r) => r.request().method() !== 'GET' && /\/api\/guide/.test(r.url()));
  await page.getByTestId('introPickups.settings.save').click();
  expect((await saved).status()).toBeLessThan(300);
  await page.reload();
  await expect(page.getByTestId('introPickups.settings.icalLink')).toHaveValue(feed);

  // the candidates come from the server's real fetch of the fixture (no browser stub)
  const candidates = page.waitForResponse((r) => r.url().endsWith('/api/guide/ical-candidates') && r.request().method() === 'GET');
  await page.goto('/guide');
  const body = (await (await candidates).json()) as { summary: string }[];
  expect(body.map((c) => c.summary)).toEqual(['Einführungsabholung Bäckerei Babu\'s', 'Einführungsabholung Supermarkt']);
  const items = page.getByTestId('introPickups.guide.icalItem');
  await expect(items).toHaveCount(2);
  await items.first().getByTestId('introPickups.guide.icalUse').click();
  const form = page.getByTestId('introPickups.form');
  // 2026-11-05T17:00Z = 18:00 in Zürich
  await expect(form.getByTestId('introPickups.form.start')).toHaveValue('2026-11-05T18:00');
  await expect(form.getByTestId('introPickups.form.businessName')).toHaveValue('Einführungsabholung Bäckerei Babu\'s');
  await expect(form.getByTestId('introPickups.form.address')).toHaveValue('Babu\'s Bakery, Löwenstrasse 1, 8001 Zürich');
  await form.getByTestId('introPickups.form.businessName').fill('Bäckerei Babu');
  await form.getByTestId('introPickups.form.area').fill('Kreis 1');
  await page.getByTestId('introPickups.form.save').click();
  await expect(form).toBeHidden();
  await expect(page.getByTestId('introPickups.guide.einab').filter({ hasText: 'Bäckerei Babu' })).toHaveCount(1);
});
