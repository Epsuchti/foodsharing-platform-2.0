import { expect, test } from '../../fixtures';
import { einabId } from './_intro';

/** EA15: every EinAb exports as .ics; guides subscribe to a personal feed (token URL, no login). */
test.use({ role: 'guide' });

test('EA15 per-EinAb ICS and guide feed by token @EA15', async ({ page, request }) => {
  await page.goto('/guide');
  const einab = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(1)}"]`);
  const download = page.waitForEvent('download');
  await einab.getByTestId('introPickups.guide.calendar').click();
  const file = await download;
  const ics = Buffer.concat(await (await file.createReadStream()).toArray()).toString('utf8');
  expect(ics).toContain('BEGIN:VEVENT');
  expect(ics).toContain('Coop Stauffacher');

  await page.goto('/guide/settings');
  const url = await page.getByTestId('introPickups.settings.feedUrl').inputValue();
  expect(url).toContain('/api/public/intro-pickups/ical/');
  await expect(page.getByTestId('introPickups.settings.subscribe')).toHaveAttribute('href', /^webcal:/);

  // the feed works without a session; unknown tokens are 404
  const path = new URL(url).pathname;
  const feed = await request.get(path);
  expect(feed.status()).toBe(200);
  const body = await feed.text();
  expect(body).toContain('BEGIN:VCALENDAR');
  expect(body.match(/BEGIN:VEVENT/g)?.length ?? 0).toBeGreaterThanOrEqual(1);
  expect((await request.get('/api/public/intro-pickups/ical/unknown-token')).status()).toBe(404);
});
