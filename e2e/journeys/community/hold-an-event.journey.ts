import fs from 'node:fs';
import type { Locator, Page } from '@playwright/test';
import { expectDataState, regionId } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';
import { fillDateTime } from '../../support/date-inputs';

/**
 * Events of zuerich-stadt (A37b). Bot (bot@local, ambassador) plans a clear-out at the storage room (the form names
 * what is missing and refuses an end before the start), corrects its name and adds a public online exchange, which
 * needs an https link. Sarah (foodsaver@local) is invited to Bot's brunch: she answers maybe, no and finally yes,
 * writes on its wall (Bot is told by bell) and deletes her post again, looks up the way there, saves the event and all
 * her events to her calendar and finds the past clean-up closed for answers. Who may see, create, edit, answer and
 * comment, the RSVP grace time and the validation per type are unit-tested (EventRulesTest, events.resource.spec.ts);
 * the ICS content too (EventIcsServiceTest).
 */
const ZURICH = regionId('zuerich-stadt');
const event = (n: number) => `00000044-0002-4000-8000-${String(n).padStart(12, '0')}`;

/** RSVP option buttons in order yes / maybe / no. */
function rsvpOption(page: Page, index: 0 | 1 | 2): Locator {
  return page.getByTestId('events.event.rsvp').locator('.p-togglebutton').nth(index);
}

async function downloadedText(page: Page, click: () => Promise<void>): Promise<string> {
  const download = page.waitForEvent('download');
  await click();
  return fs.readFileSync((await (await download).path())!, 'utf8');
}

test.describe('organiser', () => {
  test.use({ role: 'bot' });

  test('ambassador plans an event at a place and a public online event @E1', async ({ page, dev }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'organiser');
    const me = await dev.user('bot');
    const submit = page.getByTestId('events.form.submit');

    await walk.step('opens the events of his region', async () => {
      await page.goto(`/regions/${ZURICH}`);
      await walk.click(page.getByTestId('regions.page.tab.events'));
      await expectDataState(page, 'events.list.state', 'success');
      await expect(page.locator(`[data-testid="events.list.item"][data-event-id="${event(1)}"]`)).toBeVisible();
      await checkpoint(page, 'region-events');
    });

    await walk.step('plans the clear-out and fixes what the form names', async () => {
      await walk.click(page.getByTestId('events.list.new'));
      await expect(page).toHaveURL(/\/events\/new\?regionId=/);
      await walk.click(submit);
      await expect(page.getByTestId('events.form.error.name')).toBeVisible();
      await expect(page.getByTestId('events.form.error.location')).toBeVisible();
      await checkpoint(page, 'event-form-errors');
      await walk.fill(page.getByTestId('events.form.name'), 'Lagerräumung');
      await fillDateTime(page.getByTestId('events.form.startsAt'), '2026-11-20T18:00');
      await fillDateTime(page.getByTestId('events.form.endsAt'), '2026-11-20T17:00');
      await expect(page.getByTestId('events.form.error.endsAt')).toBeVisible();
      await fillDateTime(page.getByTestId('events.form.endsAt'), '2026-11-20T20:00');
      await walk.fill(page.getByTestId('events.form.locationName'), 'Lager Altstetten');
      await walk.fill(page.getByTestId('events.form.zip'), '8048');
      await walk.fill(page.getByTestId('events.form.city'), 'Zürich');
      await expect(page.getByTestId('events.form.error.location')).toHaveCount(0);
      await walk.click(submit);
      await expect(page).toHaveURL(/\/events\/[0-9a-f-]{36}$/);
      await expect(page.getByTestId('events.event.location')).toContainText('Lager Altstetten');
      // a stray space before the comma would come from the flex gap between name and address
      await expect(page.getByTestId('events.event.location')).not.toContainText(/\s,/);
      // start and end on one day: the date is named once ("… 18:00 – 20:00")
      await expect(page.getByTestId('events.event.when')).toHaveText(/\d{2}:\d{2}\s+–\s+\d{2}:\d{2}\s*$/);
      await expect(page.getByTestId('events.event.accepted')).toContainText(me.displayName);
      await checkpoint(page, 'event-created');
    });

    await walk.step('corrects the name', async () => {
      await walk.click(page.getByTestId('events.event.edit'));
      await expect(page.getByTestId('events.form.name')).toHaveValue('Lagerräumung');
      await walk.fill(page.getByTestId('events.form.name'), 'Lagerräumung Altstetten');
      await walk.click(submit);
      await expect(page.locator('h1')).toContainText('Lagerräumung Altstetten');
    });

    await walk.step('adds a public online exchange', async () => {
      await page.goto(`/events/new?regionId=${ZURICH}`);
      await walk.click(page.getByTestId('events.form.type.ONLINE'));
      await expect(page.getByTestId('events.form.locationName')).toHaveCount(0);
      await walk.fill(page.getByTestId('events.form.name'), 'Online-Austausch');
      await fillDateTime(page.getByTestId('events.form.startsAt'), '2026-11-21T19:00');
      await fillDateTime(page.getByTestId('events.form.endsAt'), '2026-11-21T20:00');
      await walk.fill(page.getByTestId('events.form.link'), 'http://meet.example.org/fs');
      await walk.click(submit);
      await expect(page.getByTestId('events.form.error.onlineLink')).toBeVisible();
      await walk.fill(page.getByTestId('events.form.link'), 'https://meet.example.org/fs');
      await walk.click(page.getByTestId('events.form.public'));
      await walk.click(submit);
      await expect(page).toHaveURL(/\/events\/[0-9a-f-]{36}$/);
      await expect(page.getByTestId('events.event.onlineLink')).toHaveAttribute('href', 'https://meet.example.org/fs');
      await expect(page.getByTestId('events.event.public')).toBeVisible();
      await expect(page.getByTestId('events.event.navigate')).toHaveCount(0);
      await checkpoint(page, 'online-event');
    });

    // measured 2026-11-15, desktop and phone
    walk.finish({ steps: 4, clicks: 10, fields: 8 });
  });
});

test.describe('participant', () => {
  test.use({ role: 'foodsaver' });

  test('member answers an invitation, writes on the wall and saves events to her calendar @E2 @E3 @E1 @N3', async ({
    page,
    dev,
    notifications,
  }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'participant');
    const me = await dev.user('foodsaver');
    const accepted = page.getByTestId('events.event.accepted');
    const maybe = page.getByTestId('events.event.maybe');

    await walk.step('opens the brunch she is invited to', async () => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
      await walk.nav('events');
      await expect(page).toHaveURL(/\/events\/mine$/);
      await expectDataState(page, 'events.mine.state', 'success');
      await checkpoint(page, 'my-events');
      await walk.click(page.locator(`[data-testid="events.list.item"][data-event-id="${event(1)}"]`));
      await expectDataState(page, 'events.event.state', 'success');
      await expect(page.getByTestId('events.event.invited')).toBeVisible();
      await checkpoint(page, 'invitation', { mask: [page.getByTestId('events.event.wall').locator('time')] });
    });

    await walk.step('answers maybe, no and finally yes', async () => {
      await walk.click(rsvpOption(page, 1));
      await expect(maybe).toContainText(me.displayName);
      await expect(page.getByTestId('events.event.invited')).toHaveCount(0);
      await walk.click(rsvpOption(page, 2));
      await expect(maybe).not.toContainText(me.displayName);
      await expect(accepted).not.toContainText(me.displayName);
      await walk.click(rsvpOption(page, 0));
      await expect(accepted).toContainText(me.displayName);
    });

    await walk.step('writes on the wall and deletes her post again', async () => {
      const bells = (await notifications.bells('bot', 'EVENT_POST')).length;
      await walk.fill(page.getByTestId('events.wall.body'), 'Ich bringe Zopf mit!');
      await walk.click(page.getByTestId('events.wall.send'));
      const post = page.getByTestId('events.wall.post').filter({ hasText: 'Ich bringe Zopf mit!' });
      await expect(post).toBeVisible();
      // the organiser is told
      await expect.poll(async () => (await notifications.bells('bot', 'EVENT_POST')).length).toBeGreaterThan(bells);
      await checkpoint(page, 'wall-post', { mask: [page.getByTestId('events.event.wall').locator('time')] });
      await walk.click(post.getByTestId('events.wall.delete'));
      await expect(post).toHaveCount(0);
    });

    await walk.step('looks up the way there', async () => {
      // the brunch is at Fairteiler Wiedikon, 47.3721 / 8.5172
      await walk.click(page.getByTestId('events.event.navigate'));
      const google = page.getByTestId('events.event.navigate.google');
      await expect(google).toHaveAttribute('href', /destination=47\.3721,8\.5172/);
      await expect(page.getByTestId('events.event.navigate.apple')).toHaveAttribute('href', /daddr=47\.3721,8\.5172&q=Fairteiler/);
      await checkpointElement(page.locator('.p-popover'), 'navigate-menu');
      await page.keyboard.press('Escape');
      await expect(page.getByTestId('events.event.navigate')).toBeFocused();
    });

    await walk.step('saves the brunch and all her events to her calendar', async () => {
      const single = await downloadedText(page, () => walk.click(page.getByTestId('events.event.ics')));
      expect(single).toContain('BEGIN:VEVENT');
      expect(single).toContain('Brunch');
      expect(single).toMatch(/LOCATION:.*Fairteiler/);
      await walk.nav('events');
      await expectDataState(page, 'events.mine.state', 'success');
      const feed = await downloadedText(page, () => walk.click(page.getByTestId('events.mine.export')));
      expect(feed).toContain('Brunch');
      expect(feed.match(/BEGIN:VEVENT/g)?.length ?? 0).toBeGreaterThan(1);
    });

    await walk.step('finds the past clean-up closed for answers', async () => {
      await page.goto(`/regions/${ZURICH}/events`);
      await expectDataState(page, 'events.list.state', 'success');
      await walk.click(page.getByTestId('events.list.past'));
      const past = page.locator(`[data-testid="events.list.item"][data-event-id="${event(6)}"]`);
      await walk.click(past);
      await expectDataState(page, 'events.event.state', 'success');
      await expect(page.getByTestId('events.event.rsvpClosed')).toBeVisible();
      await expect(page.getByTestId('events.event.rsvp')).toHaveCount(0);
      await checkpoint(page, 'past-event', { mask: [page.getByTestId('events.event.wall').locator('time')] });
    });

    // measured 2026-11-15: 13 clicks on desktop, 15 on the phone
    walk.finish({ steps: 6, clicks: 15, fields: 1 });
  });
});
