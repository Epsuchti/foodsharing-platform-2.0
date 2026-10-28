import { expect, test } from '../../fixtures';

/**
 * EA16: guides link their own foodsharing calendar (iCal); its dates are offered as EinAb candidates and prefill the
 * form. The server only fetches https feeds on port 443 of allowed hosts, which an e2e run cannot serve, so the
 * candidate list is answered by the browser (`page.route`); link validation runs against the real server.
 */
test.use({ seedReset: 'test', role: 'guide' });

const CANDIDATES = [
  { startAt: '2026-11-18T17:30:00Z', endAt: '2026-11-18T18:30:00Z', summary: 'Abholung Bäckerei Kleiner', location: 'Langstrasse 120, 8004 Zürich' },
];

test('EA16 iCal dates become EinAb candidates @EA16', async ({ page }) => {
  await page.goto('/guide/settings');
  const state = page.getByTestId('introPickups.settings.profileState');
  await expect(state).toHaveAttribute('data-state', 'success');
  // not an allowed calendar host → refused by the server
  await page.getByTestId('introPickups.settings.icalLink').fill('https://evil.example.com/calendar.ics');
  await page.getByTestId('introPickups.settings.save').click();
  await expect(page.getByTestId('shell.toast')).toContainText(/.+/);
  await page.getByTestId('introPickups.settings.icalLink').fill('https://foodsharing.network/api/calendar/abc.ics');
  await page.getByTestId('introPickups.settings.save').click();
  await page.reload();
  await expect(page.getByTestId('introPickups.settings.icalLink')).toHaveValue('https://foodsharing.network/api/calendar/abc.ics');

  await page.route('**/api/guide/ical-candidates', (route) => route.fulfill({ json: CANDIDATES }));
  await page.goto('/guide');
  const items = page.getByTestId('introPickups.guide.icalItem');
  await expect(items).toHaveCount(1);
  await items.first().getByTestId('introPickups.guide.icalUse').click();
  const form = page.getByTestId('introPickups.form');
  await expect(form.getByTestId('introPickups.form.start')).toHaveValue('2026-11-18T18:30');
  await expect(form.getByTestId('introPickups.form.businessName')).toHaveValue('Abholung Bäckerei Kleiner');
  await expect(form.getByTestId('introPickups.form.address')).toHaveValue('Langstrasse 120, 8004 Zürich');
  await form.getByTestId('introPickups.form.businessName').fill('Bäckerei Kleiner');
  await form.getByTestId('introPickups.form.area').fill('Kreis 4');
  await page.getByTestId('introPickups.form.save').click();
  await expect(form).toBeHidden();
  await expect(page.getByTestId('introPickups.guide.einab').filter({ hasText: 'Bäckerei Kleiner' })).toHaveCount(1);
});
