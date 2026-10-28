import {
  LANGUAGES,
  S01_THURSDAY,
  S02_SATURDAY,
  expect,
  expectStatus,
  setLanguage,
  test,
  zurichPickupAt,
} from '../../fixtures';
import { openPickups, openSignup, pickupRow, reasonsOf } from './_pickups';

/**
 * PU4 eligibility reasons. Seed: S-02 Bäckerei Vohdin requires a hygiene certificate (member jumper has none;
 * Saturday 18:00 is full with bieb + jumper); S-03 Migros Limmatplatz requires a phone number (team bot manager,
 * foodsaver, guide); jumper is a jumper in S-01; guide is not in S-01's team; pending@local is unverified. Users who
 * may not see a store's pickups get 403 on eligibility and sign-up (A19).
 */

test.use({ seedReset: 'test' });

const S02_WEDNESDAY = zurichPickupAt(2, '18:00');

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('PU4 phone number required @PU4', async ({ page, api, store }) => {
    await expectStatus(await api.patch('/api/users/me/profile', { mobilePhone: '', landlinePhone: '' }), [200, 204], 'remove phone');
    const next = (await store.pickupsOf(api, 'S-03')).find((p) => p.occupiedSlots.length < p.slotCount)!;
    await openPickups(page, 'S-03');
    await openSignup(page, next.pickupAt);
    await expect(page.locator('[data-testid="shared.eligibility.reason"][data-reason="PHONE_REQUIRED"]')).toBeVisible();
  });

  test('PU4 pickup in the past @PU4', async ({ api }) => {
    expect(await reasonsOf(api, 'S-01', zurichPickupAt(-4, '19:00'))).toContain('PICKUP_IN_PAST');
  });

  test('PU4 already signed up @PU4', async ({ page, api }) => {
    expect(await reasonsOf(api, 'S-01', S01_THURSDAY)).toContain('ALREADY_SIGNED_UP');
    await openPickups(page, 'S-01');
    // the free "+" of a pickup one is already in is not clickable
    await expect(pickupRow(page, S01_THURSDAY).getByTestId('stores.pickups.slot.free')).toBeDisabled();
  });

  test('PU4 full pickup @PU4', async ({ api }) => {
    expect(await reasonsOf(api, 'S-02', S02_SATURDAY)).toEqual(['NO_FREE_SLOT']);
  });
});

test.describe('jumper', () => {
  test.use({ role: 'jumper' });

  test('PU4 hygiene certificate required @PU4 @page', async ({ page }) => {
    await openPickups(page, 'S-02');
    await openSignup(page, S02_WEDNESDAY);
    await expect(page.locator('[data-testid="shared.eligibility.reason"][data-reason="HYGIENE_CERTIFICATE_REQUIRED"]')).toBeVisible();
    await expect(page.getByTestId('pickups.signup.confirm')).toHaveCount(0);
  });

  test('PU4 reasons rendered in five languages @PU4', async ({ page, api }) => {
    const texts = new Set<string>();
    for (const language of LANGUAGES) {
      // the account language wins over the device language
      await api.patchJson('/api/users/me/settings', { language });
      await setLanguage(page, language);
      await openPickups(page, 'S-02');
      await openSignup(page, S02_WEDNESDAY);
      const reason = page.locator('[data-testid="shared.eligibility.reason"][data-reason="HYGIENE_CERTIFICATE_REQUIRED"]');
      await expect(reason).toBeVisible();
      const text = (await reason.textContent())!.trim();
      expect(text).not.toContain('pickups.eligibility');
      texts.add(text);
    }
    expect(texts.size).toBe(LANGUAGES.length);
  });
});

test.describe('other roles', () => {
  for (const role of ['guide', 'jumper', 'pending'] as const) {
    test(`PU4 ${role === 'guide' ? 'not a team member' : role === 'jumper' ? 'jumpers cannot sign up' : 'unverified user'} is refused @PU4`, async ({ as, apiOf, store }) => {
      const client = apiOf(await as(role));
      const path = `/api/stores/${store.id('S-01')}/pickups/${encodeURIComponent(S01_THURSDAY)}`;
      expect((await client.get(`${path}/eligibility`)).status()).toBe(403);
      const signUp = await client.post(`${path}/signups`);
      expect(signUp.status()).toBe(403);
    });
  }
});
