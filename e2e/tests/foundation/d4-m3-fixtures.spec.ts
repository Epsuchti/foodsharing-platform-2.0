import { S01_THURSDAY, S02_SATURDAY, expect, test } from '../../fixtures';

/** The M3 seed (docs/seed-ids.md "Pickups") as the store fixture sees it. */

const FOODSAVER_ID = '00000001-0001-4000-8000-000000000006';

test.use({ role: 'foodsaver' });

test('D4.2 M3 seed has 25 stores and pickups in both directions @D4.2', async ({ api, store }) => {
  expect(store.all()).toHaveLength(25);
  expect(store.byKey('S-01').name).toBe('Coop Bahnhofbrücke');
  expect(store.byKey("Babu's Bakery").code).toBe('S-06');

  // future: S-01 Thursday 19:00 has foodsaver (unconfirmed) and sleepy, S-02 Saturday is full
  const s01 = await store.pickupsOf(api, 'S-01');
  const thursday = s01.find((pickup) => pickup.pickupAt === S01_THURSDAY);
  expect(thursday, `S-01 ${S01_THURSDAY}`).toBeDefined();
  const mine = thursday!.occupiedSlots.find((slot) => slot.user.id === FOODSAVER_ID);
  expect(mine?.confirmed).toBe(false);
  expect(thursday!.occupiedSlots).toHaveLength(2);

  const s02 = await store.pickupsOf(api, 'S-02');
  const saturday = s02.find((pickup) => pickup.pickupAt === S02_SATURDAY);
  expect(saturday!.occupiedSlots.length).toBe(saturday!.slotCount);

  // past: foodsaver has 24 past pickups
  const past = await api.getJson<{ items: unknown[]; page: { totalItems: number } }>(
    `/api/users/${FOODSAVER_ID}/pickups/past?size=50`,
  );
  expect(past.page.totalItems).toBe(24);
});
