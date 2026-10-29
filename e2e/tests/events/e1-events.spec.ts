import { expect, expectDataState, expectErrorState, expectNoSeriousViolations, injectApiError, test } from '../../fixtures';
import { event } from './event-helpers';

/** E1 events (A37b): bot is a member/ambassador of zuerich-stadt; profile has no region. Seed clock 2026-11-02. */
test.describe('bot', () => {
  test.use({ seedReset: 'test', role: 'bot' });

  test('E1 create offline event with location and online event with link; validation errors per type @E1 @page', async ({ page, region, dev }) => {
    const me = await dev.user('bot');
    await page.goto(`/regions/${region.id('zuerich-stadt')}`);
    await page.getByTestId('regions.page.tab.events').click();
    await expectDataState(page, 'events.list.state', 'success');
    await expect(page.locator(`[data-testid="events.list.item"][data-event-id="${event(1)}"]`)).toBeVisible();
    await page.getByTestId('events.list.new').click();
    await expect(page).toHaveURL(/\/events\/new\?regionId=/);

    const submit = page.getByTestId('events.form.submit');
    await submit.click();
    await expect(page.getByTestId('events.form.error.name')).toBeVisible();
    await expect(page.getByTestId('events.form.error.location')).toBeVisible();
    await page.getByTestId('events.form.name').fill('Lagerräumung');
    await page.getByTestId('events.form.startsAt').fill('2026-11-20T18:00');
    await page.getByTestId('events.form.endsAt').fill('2026-11-20T17:00');
    await expect(page.getByTestId('events.form.error.endsAt')).toBeVisible();
    await page.getByTestId('events.form.endsAt').fill('2026-11-20T20:00');
    await page.getByTestId('events.form.locationName').fill('Lager Altstetten');
    await page.getByTestId('events.form.zip').fill('8048');
    await page.getByTestId('events.form.city').fill('Zürich');
    await expect(page.getByTestId('events.form.error.location')).toHaveCount(0);
    await expectNoSeriousViolations(page);
    await submit.click();
    await expect(page).toHaveURL(/\/events\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('events.event.location')).toContainText('Lager Altstetten');
    await expect(page.getByTestId('events.event.accepted')).toContainText(me.displayName);

    // edit keeps values and saves
    await page.getByTestId('events.event.edit').click();
    await expect(page.getByTestId('events.form.name')).toHaveValue('Lagerräumung');
    await page.getByTestId('events.form.name').fill('Lagerräumung Altstetten');
    await submit.click();
    await expect(page.locator('h1')).toContainText('Lagerräumung Altstetten');
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message')).toHaveCount(0, { timeout: 15_000 });
  });

  test('E1 online event needs an https link and shows the join button @E1', async ({ page, region }) => {
    const submit = page.getByTestId('events.form.submit');
    await page.goto(`/events/new?regionId=${region.id('zuerich-stadt')}`);
    await page.getByTestId('events.form.type.ONLINE').click();
    await expect(page.getByTestId('events.form.locationName')).toHaveCount(0);
    await page.getByTestId('events.form.name').fill('Online-Austausch');
    await page.getByTestId('events.form.startsAt').fill('2026-11-21T19:00');
    await page.getByTestId('events.form.endsAt').fill('2026-11-21T20:00');
    await page.getByTestId('events.form.link').fill('http://meet.example.org/fs');
    await submit.click();
    await expect(page.getByTestId('events.form.error.onlineLink')).toBeVisible();
    await page.getByTestId('events.form.link').fill('https://meet.example.org/fs');
    await page.getByTestId('events.form.public').click();
    await submit.click();
    await expect(page).toHaveURL(/\/events\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('events.event.onlineLink')).toHaveAttribute('href', 'https://meet.example.org/fs');
    await expect(page.getByTestId('events.event.public')).toBeVisible();
  });

  test('E1 events error state with retry @E1', async ({ page, region }) => {
    const injection = await injectApiError(page, { url: /\/api\/regions\/[^/]+\/events/, status: 500 });
    await page.goto(`/regions/${region.id('zuerich-stadt')}/events`);
    const { retry } = await expectErrorState(page, 'events.list.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'events.list.state', 'success');
  });
});

test.describe('profile (no region)', () => {
  test.use({ role: 'profile' });

  test('E1 public event visible to non-member, private not @E1', async ({ page, region }) => {
    await page.goto(`/regions/${region.id('zuerich-stadt')}/events`);
    await expectDataState(page, 'events.list.state', 'success');
    await expect(page.locator(`[data-event-id="${event(1)}"]`)).toBeVisible();
    await expect(page.locator(`[data-event-id="${event(3)}"]`)).toHaveCount(0);
    await expect(page.getByTestId('events.list.new')).toHaveCount(0);
    await page.goto(`/events/${event(1)}`);
    await expectDataState(page, 'events.event.state', 'success');
    await page.goto(`/events/${event(3)}`);
    await expectDataState(page, 'events.event.state', 'forbidden');
  });
});
