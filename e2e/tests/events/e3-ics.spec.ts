import fs from 'node:fs';
import { expect, test } from '../../fixtures';
import { event } from './event-helpers';

/** E3 calendar export (A37b): single event and "my events" feed (ACCEPTED/MAYBE). */
test.use({ role: 'bot' });

test('E3 event ICS download contains VEVENT with name and location; my events feed @E3', async ({ page }) => {
  await page.goto(`/events/${event(1)}`);
  const button = page.getByTestId('events.event.ics');
  await expect(button).toBeVisible();
  let download = page.waitForEvent('download');
  await button.click();
  let text = fs.readFileSync((await (await download).path())!, 'utf8');
  expect(text).toContain('BEGIN:VEVENT');
  expect(text).toContain('Brunch');
  expect(text).toMatch(/LOCATION:.*Fairteiler/);

  await page.goto('/events/mine');
  await expect(page.locator(`[data-testid="events.list.item"][data-event-id="${event(1)}"]`)).toBeVisible();
  download = page.waitForEvent('download');
  await page.getByTestId('events.mine.export').click();
  text = fs.readFileSync((await (await download).path())!, 'utf8');
  expect(text.match(/BEGIN:VEVENT/g)?.length ?? 0).toBeGreaterThan(1);
});
