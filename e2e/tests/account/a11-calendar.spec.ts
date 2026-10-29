import { acceptPendingConsents, expect, expectDataState, expectErrorState, injectApiError, test, type ApiClient } from '../../fixtures';

/**
 * A11 calendar feed (plan-m7 Q4, A49): the settings card shows the personal iCal link, its filters (pickups, intro
 * pickups, history, events, reminders) are part of the URL, regenerating replaces the link. Seed: foodsaver@local has
 * the token `0123456789abcdef` ×4, future pickups in S-01/S-04 and is INVITED to "Stammtisch November"; einab1 has no
 * token and the EinAb at Babu's Bakery booked (docs/seed-ids.md).
 */
test.use({ seedReset: 'test' });

const SEED_TOKEN = '0123456789abcdef'.repeat(4);

/** Path + query of the absolute feed URL (the server's base URL may differ from the e2e origin). */
function feedPath(url: string): string {
  const parsed = new URL(url);
  return parsed.pathname + parsed.search;
}

async function feed(api: ApiClient, url: string): Promise<{ status: number; text: string }> {
  const response = await api.get(feedPath(url), { headers: { Accept: 'text/calendar' } });
  return { status: response.status(), text: await response.text() };
}

test('A11 create calendar token, feed contains pickups, EinAbs and events with reminders, regenerate invalidates the old one @A11 @E3', async ({
  as,
  apiOf,
  api,
}) => {
  // einab1 has no link yet and creates one; the feed carries the booked EinAb
  const einab = await as('einab1');
  await acceptPendingConsents(apiOf(einab));
  await einab.goto('/account/settings');
  await expectDataState(einab, 'account.settings.calendar.state', 'empty');
  await einab.getByTestId('account.settings.calendar.create').click();
  const einabUrl = einab.getByTestId('account.settings.calendar.url');
  await expect(einabUrl).toHaveValue(/\/api\/public\/calendar\/[0-9a-f]{32}\.ics$/);
  const einabFeed = await feed(api, await einabUrl.inputValue());
  expect(einabFeed.status).toBe(200);
  expect(einabFeed.text).toContain('BEGIN:VCALENDAR');
  expect(einabFeed.text).toContain("SUMMARY:Einführungsabholung Babu's Bakery");

  // foodsaver: seed link, all events of the regions and two reminders
  const page = await as('foodsaver');
  await page.goto('/account/settings');
  const url = page.getByTestId('account.settings.calendar.url');
  await expect(url).toHaveValue(new RegExp(`/api/public/calendar/${SEED_TOKEN}\\.ics$`));
  await page.getByTestId('account.settings.calendar.events').click();
  await page.getByRole('option').first().click(); // "every"
  await page.locator('label[for="cal-reminder-15"]').click();
  await page.locator('label[for="cal-reminder-60"]').click();
  await expect(url).toHaveValue(/\?events=every&reminders=15,60$/);
  await expect(page.getByTestId('account.settings.calendar.subscribe')).toHaveAttribute('href', /^webcal:\/\/.*events=every&reminders=15,60$/);
  const oldUrl = await url.inputValue();
  const foodsaverFeed = await feed(api, oldUrl);
  expect(foodsaverFeed.status).toBe(200);
  expect(foodsaverFeed.text).toMatch(/SUMMARY:Abholung /);
  expect(foodsaverFeed.text).toContain('SUMMARY:Stammtisch November');
  expect(foodsaverFeed.text).toContain('TRIGGER:-PT15M');
  expect(foodsaverFeed.text).toContain('TRIGGER:-PT60M');
  // without the pickups the feed has none
  await page.locator('label[for="cal-pickups"]').click();
  await expect(url).toHaveValue(/pickups=false/);
  expect((await feed(api, await url.inputValue())).text).not.toMatch(/SUMMARY:Abholung /);

  // regenerate: the old link is gone, the new one works with the same filters
  await page.getByTestId('account.settings.calendar.regenerate').click();
  await page.locator('.p-confirmdialog-accept-button').click();
  await expect(url).not.toHaveValue(new RegExp(SEED_TOKEN));
  await expect(url).toHaveValue(/\/api\/public\/calendar\/[0-9a-f]{32}\.ics\?events=every&pickups=false&reminders=15,60$/);
  expect((await feed(api, oldUrl)).status).toBe(404);
  expect((await feed(api, await url.inputValue())).status).toBe(200);

  // delete: the card is back to "no link"
  await page.getByTestId('account.settings.calendar.delete').click();
  await page.locator('.p-confirmdialog-accept-button').click();
  await expectDataState(page, 'account.settings.calendar.state', 'empty');
});

test('A11 calendar card error state and retry @A11', async ({ as }) => {
  const page = await as('foodsaver');
  // GETs are retried twice by the retry interceptor
  await injectApiError(page, { url: '/api/calendar/token', method: 'GET', status: 500, times: 3 });
  await page.goto('/account/settings');
  const { retry } = await expectErrorState(page, 'account.settings.calendar.state');
  await retry.click();
  await expect(page.getByTestId('account.settings.calendar.url')).toHaveValue(new RegExp(SEED_TOKEN));
});
