import { expect, test, zurichPickupAt } from '../../fixtures';
import { dateField } from '../../support/date-inputs';
import { openPickups, pickupRow } from '../pickups/_pickups';
import { acceptConfirm, einabId } from './_intro';

/**
 * EA16 (decision 169): the guide's own confirmed upcoming store pickups are offered as EinAb candidates; "use"
 * pre-fills the form and the EinAb stays linked to the store pickup. Signing out of the pickup cancels the linked EinAb
 * and tells the booked newcomer. Seed (docs/seed-ids.md): guide@local (Julien Favre, French UI) has confirmed sign-ups
 * at S-09 Tiny Fish on Friday 6.11 and 13.11 20:00 (the candidates), an unconfirmed one at S-03 on Tuesday 10.11 (no
 * candidate) and the confirmed S-03 Migros Friday 6.11 18:30, which the seeded EinAb `…-28` is linked to (booked by
 * pending@local).
 */
test.use({ seedReset: 'test', role: 'guide' });

const S03_FRIDAY = zurichPickupAt(4, '18:30');

test('EA16 own store pickups become linked EinAbs; signing out cancels them @EA16', async ({ page, notifications }) => {
  await page.goto('/guide');
  await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');

  // confirmed pickups only, and none that has an EinAb already
  const card = page.getByTestId('introPickups.guide.pickupCandidates');
  const candidates = card.getByTestId('introPickups.guide.pickupCandidate');
  await expect(candidates).toHaveCount(2);
  await expect(candidates.first()).toContainText('Tiny Fish Stadelhofen');
  await expect(card).not.toContainText('Migros Limmatplatz');

  // the seeded linked EinAb: chip, no reassign
  const seeded = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(28)}"]`);
  await expect(seeded.getByTestId('introPickups.guide.linked')).toBeVisible();
  await expect(seeded.getByTestId('introPickups.guide.assign')).toHaveCount(0);

  // "use" pre-fills date, store name, address, city and category; saving creates the linked EinAb
  await candidates.first().getByTestId('introPickups.guide.pickupCandidateUse').click();
  const form = page.getByTestId('introPickups.form');
  await expect(dateField(form.getByTestId('introPickups.form.start'))).toHaveValue('06.11.2026 20:00');
  await expect(form.getByTestId('introPickups.form.businessName')).toHaveValue('Tiny Fish Stadelhofen');
  await expect(form.getByTestId('introPickups.form.address')).toHaveValue('Stadelhoferstrasse 10, 8001 Zürich');
  await expect(form.getByTestId('introPickups.form.area')).toHaveValue('Zürich');
  await expect(form.getByTestId('introPickups.form.category')).toContainText('Vente à emporter');
  await expect(form.getByTestId('introPickups.form.linkedHint')).toBeVisible();
  await expect(form.getByTestId('introPickups.form.unlinkHint')).toHaveCount(0);
  const saved = page.waitForResponse((r) => r.request().method() === 'POST' && /\/guide\/intro-pickups$/.test(r.url()));
  await page.getByTestId('introPickups.form.save').click();
  const createdId = ((await (await saved).json()) as { id: string }).id;
  await expect(form).toBeHidden();
  // the seed has an unlinked Tiny Fish EinAb of the guide, too (…-02), so find the new one by id
  const created = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${createdId}"]`);
  await expect(created.getByTestId('introPickups.guide.linked')).toBeVisible();
  await expect(created.getByTestId('introPickups.guide.assign')).toHaveCount(0);
  await expect(candidates).toHaveCount(1);

  // signing out of the S-03 pickup warns, then cancels the seeded EinAb and tells pending@local
  await openPickups(page, 'S-03');
  const row = pickupRow(page, S03_FRIDAY);
  await row.locator('[data-testid="stores.pickups.slot.taken"][data-user="Julien Favre"]').click();
  await expect(page.locator('.p-confirmdialog')).toContainText('Cela annule aussi la collecte d’introduction liée');
  await expect(page.locator('.p-confirmdialog')).toContainText('1 nouvelle personne inscrite sera prévenue');
  await acceptConfirm(page);
  await expect(row.locator('[data-user="Julien Favre"]')).toHaveCount(0);

  await page.goto('/guide');
  await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
  await expect(seeded).toHaveCount(0);
  await expect(created).toHaveCount(1);
  await expect.poll(async () => (await notifications.bells('pending', 'INTRO_PICKUP_CANCELLED_BY_GUIDE')).length).toBe(1);
  await expect.poll(async () => (await notifications.bells('guide', 'INTRO_PICKUP_AUTO_CANCELLED')).length).toBe(1);
});
