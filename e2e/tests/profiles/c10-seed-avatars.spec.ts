import { expect, test } from '../../fixtures';
import type { ApiClient } from '../../fixtures';

/**
 * C10 image variants for seeded uploads: every seed user has an avatar upload (docs/seed-ids.md module 0040,
 * `00000040-0001-…-101`–`114`) whose bytes and sm/md/lg variants must be served, also after a test replaced or
 * removed a seed photo (which deletes its bytes) and the seed was reset (which re-inserts the row).
 */
test.use({ seedReset: 'test', role: 'foodsaver' });

const FOODSAVER_PHOTO = '00000040-0001-4000-8000-000000000106';

async function expectSeedAvatarServed(api: ApiClient): Promise<void> {
  const me = await api.getJson<{ avatarUrl?: string }>('/api/users/me');
  expect(me.avatarUrl).toBe(`/api/uploads/${FOODSAVER_PHOTO}?size=md`);
  for (const size of ['sm', 'md', 'lg']) {
    const variant = await api.get(`/api/uploads/${FOODSAVER_PHOTO}?size=${size}`);
    expect(variant.status(), size).toBe(200);
    expect(variant.headers()['content-type'], size).toBe('image/jpeg');
    const bytes = await variant.body();
    expect(bytes.length, size).toBe(Number(variant.headers()['content-length']));
    expect(bytes.subarray(0, 3).toString('hex'), size).toBe('ffd8ff'); // JPEG magic
  }
  const original = await api.get(`/api/uploads/${FOODSAVER_PHOTO}`);
  expect(original.status()).toBe(200);
  expect(original.headers()['content-type']).toBe('image/png');
  expect((await original.body()).length).toBe(Number(original.headers()['content-length']));
}

test('C10 seed avatar of foodsaver is served as jpeg variants, also after removal and a seed reset @C10 @A6', async ({ api, resetSeed }) => {
  await expectSeedAvatarServed(api);

  // removing the photo (as A5 does when it replaces it) deletes the seed bytes; the seed reset brings them back
  expect((await api.delete('/api/users/me/photo')).status()).toBeLessThan(300);
  expect((await api.get(`/api/uploads/${FOODSAVER_PHOTO}?size=md`)).status()).toBe(404);
  await resetSeed();
  await api.loginAs('foodsaver');
  await expectSeedAvatarServed(api);
});
