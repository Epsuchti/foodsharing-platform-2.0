import { S01_THURSDAY, acceptPendingConsents, expect, expectStatus, test } from '../../fixtures';
import { NAMES, acceptConfirm, confirmWithMessage, memberAction, memberRow, openStore } from './_stores';

/**
 * S8 standby and managers. Seed: S-01 = bieb (manager), foodsaver (unconfirmed on Thursday 19:00), sleepy, jumper;
 * S-10 = bot + welcome (managers); S-16 = welcome as the only manager. bieb administrates the Zürich store
 * coordination group and is therefore exempt from the manager limits (A19).
 */

test.use({ seedReset: 'test', role: 'bieb' });

test('S8 move to standby with message and back @S8', async ({ page }) => {
  await openStore(page, 'S-01', 'team');
  await memberAction(page, NAMES.foodsaver, 'toStandby');
  await confirmWithMessage(page, 'Du warst lange nicht mehr abholen, wir setzen dich vorerst auf die Springerliste.');
  await expect(page.getByTestId('stores.team.jumpers').locator(`[data-user="${NAMES.foodsaver}"]`)).toBeVisible();
  await expect(memberRow(page, NAMES.foodsaver)).toHaveAttribute('data-status', 'JUMPER');

  await memberAction(page, NAMES.foodsaver, 'toTeam');
  await expect(page.getByTestId('stores.team.members').locator(`[data-user="${NAMES.foodsaver}"]`)).toBeVisible();
  await expect(memberRow(page, NAMES.foodsaver)).toHaveAttribute('data-status', 'MEMBER');
});

test('S8 appoint third manager allowed, fourth refused, coordinator overrides @S8', async ({ as, apiOf, dev, store }) => {
  // welcome manages S-10 with bot; welcome is no store coordinator (Zürich has a coordination group, A19)
  const s10 = store.id('S-10');
  const orga = apiOf(await as('orga'));
  for (const role of ['reports', 'bieb'] as const) {
    const user = await dev.user(role);
    await expectStatus(await orga.post(`/api/stores/${s10}/team/${user.id}`), [200, 201, 204], `add ${role} to S-10`);
  }
  const page = await as('welcome');
  await acceptPendingConsents(apiOf(page));
  await openStore(page, 'S-10', 'team');
  // third manager
  await memberAction(page, NAMES.reports, 'appointManager');
  await acceptConfirm(page);
  await expect(memberRow(page, NAMES.reports).getByTestId('stores.team.manager')).toBeVisible();
  // a fourth is refused for a store manager …
  await memberAction(page, NAMES.bieb, 'appointManager');
  await acceptConfirm(page);
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message-error')).toBeVisible();
  await expect(memberRow(page, NAMES.bieb).getByTestId('stores.team.manager')).toHaveCount(0);
  // … but a coordinator (orga) may ignore the limit
  const bieb = await dev.user('bieb');
  await expectStatus(await orga.put(`/api/stores/${s10}/managers/${bieb.id}`), [200, 204], 'orga appoints a fourth manager');
  await page.reload();
  await expect(memberRow(page, NAMES.bieb).getByTestId('stores.team.manager')).toBeVisible();
});

test('S8 last manager cannot be removed or leave @S8', async ({ as, apiOf, dev, store }) => {
  // welcome is the only manager of S-16 Bäckerei Kleiner
  const page = await as('welcome');
  const api = apiOf(page);
  await acceptPendingConsents(api);
  await openStore(page, 'S-16');
  await expect(page.getByTestId('stores.membership.managerCannotLeave')).toBeVisible();
  await expect(page.getByTestId('stores.membership.leave')).toHaveCount(0);
  // no row menu for oneself
  await openStore(page, 'S-16', 'team');
  await expect(memberRow(page, NAMES.welcome)).toBeVisible();
  await expect(memberRow(page, NAMES.welcome).getByTestId('stores.team.menu')).toHaveCount(0);

  const welcome = await dev.user('welcome');
  const leave = await api.fetch('DELETE', `/api/stores/${store.id('S-16')}/team/${welcome.id}`);
  expect(leave.status()).toBe(409);
  expect((await leave.json()).code).toBe('MANAGER_CANNOT_LEAVE');
  const demote = await api.fetch('DELETE', `/api/stores/${store.id('S-16')}/managers/${welcome.id}`, {
    message: 'Ich gebe die Verantwortung ab.',
  });
  expect(demote.status()).toBe(409);
  expect((await demote.json()).code).toBe('LAST_MANAGER');
});

test('S8 remove member deletes future pickups @S8 @PU6', async ({ page, api, store }) => {
  const before = (await store.pickupsOf(api, 'S-01')).find((p) => p.pickupAt === S01_THURSDAY);
  expect(before?.occupiedSlots.map((slot) => slot.user.displayName)).toContain(NAMES.foodsaver);

  await openStore(page, 'S-01', 'team');
  await memberAction(page, NAMES.foodsaver, 'remove');
  await confirmWithMessage(page, 'Wir haben uns im Team darauf geeinigt, dich aus dem Betrieb zu nehmen.');
  await expect(memberRow(page, NAMES.foodsaver)).toHaveCount(0);

  const after = (await store.pickupsOf(api, 'S-01')).find((p) => p.pickupAt === S01_THURSDAY);
  expect(after?.occupiedSlots.map((slot) => slot.user.displayName) ?? []).not.toContain(NAMES.foodsaver);
});
