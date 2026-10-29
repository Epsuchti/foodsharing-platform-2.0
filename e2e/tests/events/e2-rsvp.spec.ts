import { expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';
import { event, rsvpOption } from './event-helpers';

/** E2 RSVP and wall (A37b): foodsaver is still INVITED to `01`; `06` ended on 17 Oct (RSVP closed). */
test.use({ seedReset: 'test', role: 'foodsaver' });

test('E2 invited members respond yes maybe no, lists update, wall post notifies participants, RSVP closed 24 h after end @E2 @page @N3', async ({ page, dev, notifications }) => {
  const me = await dev.user('foodsaver');
  await page.goto(`/events/${event(1)}`);
  await expectDataState(page, 'events.event.state', 'success');
  await expect(page.getByTestId('events.event.invited')).toBeVisible();
  const accepted = page.getByTestId('events.event.accepted');
  const maybe = page.getByTestId('events.event.maybe');

  await rsvpOption(page, 0).click();
  await expect(accepted).toContainText(me.displayName);
  await expect(page.getByTestId('events.event.invited')).toHaveCount(0);
  await rsvpOption(page, 1).click();
  await expect(maybe).toContainText(me.displayName);
  await expect(accepted).not.toContainText(me.displayName);
  await rsvpOption(page, 2).click();
  await expect(maybe).not.toContainText(me.displayName);
  await expect(accepted).not.toContainText(me.displayName);

  await page.getByTestId('events.wall.body').fill('Ich bringe Zopf mit!');
  await page.getByTestId('events.wall.send').click();
  const post = page.getByTestId('events.wall.post').filter({ hasText: 'Ich bringe Zopf mit!' });
  await expect(post).toBeVisible();
  await expect.poll(async () => (await notifications.bells('bot', 'EVENT_POST')).length).toBeGreaterThan(0);
  await post.getByTestId('events.wall.delete').click();
  await expect(post).toHaveCount(0);

  await page.goto(`/events/${event(6)}`);
  await expect(page.getByTestId('events.event.rsvpClosed')).toBeVisible();
  await expect(page.getByTestId('events.event.rsvp')).toHaveCount(0);
  await expectNoSeriousViolations(page);
});
