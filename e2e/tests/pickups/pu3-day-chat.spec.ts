import { expect, test } from '../../fixtures';
import { openPickups } from './_pickups';

/**
 * PU3 / M5 day chat (decision 164). Seed S-01 "Coop Bahnhofbrücke": Thursday 2026-11-05 foodsaver + sleepy signed up;
 * S-04 Wednesday 2026-11-04 only foodsaver. "Chat öffnen" finds or creates one named chat per store and day.
 */
test.use({ seedReset: 'test' });

interface ConversationDetail {
  id: string;
  name: string;
  members: { id: string }[];
}

test('PU3 day chat is created once, named and joined by the manager @PU3 @M5 @page', async ({ as, apiOf, dev }) => {
  const [foodsaverUser, sleepy, biebUser] = await Promise.all([dev.user('foodsaver'), dev.user('sleepy'), dev.user('bieb')]);
  const foodsaver = await as('foodsaver');
  await openPickups(foodsaver, 'S-01');
  const thursday = foodsaver.locator('[data-testid="stores.pickups.day"][data-day="2026-11-05"]');
  const button = thursday.getByTestId('stores.pickups.dayChat');
  await expect(button).toHaveAccessibleName('Chat öffnen');
  await button.click();
  await expect(foodsaver).toHaveURL(/\/messages\/[0-9a-f-]{36}$/);
  const id = foodsaver.url().split('/').pop()!;
  const chat = await apiOf(foodsaver).getJson<ConversationDetail>(`/api/conversations/${id}`);
  expect(chat.name).toContain('Coop Bahnhofbrücke');
  expect(chat.members.map((m) => m.id).sort()).toEqual([foodsaverUser.id, sleepy.id].sort());

  // the manager is not signed up that day: same chat, and he joins it
  const bieb = await as('bieb');
  await openPickups(bieb, 'S-01');
  await bieb.locator('[data-testid="stores.pickups.day"][data-day="2026-11-05"]').getByTestId('stores.pickups.dayChat').click();
  await expect(bieb).toHaveURL(new RegExp(`/messages/${id}$`));
  const after = await apiOf(bieb).getJson<ConversationDetail>(`/api/conversations/${id}`);
  expect(after.name).toBe(chat.name);
  expect(after.members.map((m) => m.id)).toContain(biebUser.id);
});

test('PU3 no day chat button when only I am signed up that day @PU3 @M5', async ({ as }) => {
  const foodsaver = await as('foodsaver');
  await openPickups(foodsaver, 'S-04');
  const wednesday = foodsaver.locator('[data-testid="stores.pickups.day"][data-day="2026-11-04"]');
  await expect(wednesday).toBeVisible();
  await expect(wednesday.getByTestId('stores.pickups.dayChat')).toHaveCount(0);
});
