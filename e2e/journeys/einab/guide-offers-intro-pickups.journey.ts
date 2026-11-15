import { zurichPickupAt } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm, einabId } from '../../fixtures/flows/einab';
import { openPickups, pickupRow } from '../../fixtures/flows/pickups';
import { Walk } from '../../fixtures/persona';
import { dateField, fillDateTime } from '../../support/date-inputs';

/**
 * Guide Julien (guide@local) plans his intro pickups (EinAbs) on /guide. He adds an online EinAb (the form asks for a
 * call link instead of an address, and the server's check is shown), turns one of his own confirmed store pickups into
 * a linked EinAb (decision 169: date, store, address and category pre-filled), and a take-away EinAb whose private
 * info he saves as a template; the supermarket template is filled in on request. He gives the take-away one a third
 * place and deletes the online one again; past EinAbs cannot be changed. In his settings he manages the templates and
 * finds his calendar feed, and he saves one EinAb to his calendar. Finally he signs out of the store pickup the seeded
 * EinAb …-28 is linked to: the warning names the booked newcomer, and the EinAb is cancelled with it.
 * Rules behind it: IntroPickupValidationTest (form), IntroPickupLinkedPickupTest (candidates, link, auto-cancel),
 * IntroPickupGuideServiceRulesTest (past EinAbs), IcalServicesTest (feed token).
 */
test.use({ role: 'guide' });

const S03_FRIDAY = zurichPickupAt(4, '18:30');

test('guide offers online, linked and on-site intro pickups and manages templates and calendar @EA10 @EA11 @EA15 @EA16', async ({
  page,
  request,
  notifications,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'guide');
  const einabs = page.getByTestId('introPickups.guide.einab');
  const form = page.getByTestId('introPickups.form');
  const candidates = page.getByTestId('introPickups.guide.pickupCandidates').getByTestId('introPickups.guide.pickupCandidate');
  const seeded = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(28)}"]`);
  let onlineId = '';

  await walk.step('opens his guide dashboard', async () => {
    await page.goto('/dashboard');
    await walk.nav('guide');
    await expect(page).toHaveURL(/\/guide$/);
    await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
    await expect(einabs).toHaveCount(11);
    // confirmed store pickups only, and none that has an EinAb already
    await expect(candidates).toHaveCount(2);
    await expect(candidates.first()).toContainText('Tiny Fish Stadelhofen');
    await expect(page.getByTestId('introPickups.guide.pickupCandidates')).not.toContainText('Migros Limmatplatz');
    await expect(seeded.getByTestId('introPickups.guide.linked')).toBeVisible();
    await expect(seeded.getByTestId('introPickups.guide.assign')).toHaveCount(0);
    await checkpoint(page, 'guide-dashboard');
  });

  await walk.step('adds an online EinAb', async () => {
    await walk.click(page.getByTestId('introPickups.guide.create'));
    await walk.click(form.getByTestId('introPickups.form.category'));
    await walk.click(page.getByRole('option', { name: 'Online', exact: true }));
    await expect(form.getByTestId('introPickups.form.callLink')).toBeVisible();
    await expect(form.getByTestId('introPickups.form.address')).toHaveCount(0);
    await expect(form.getByTestId('introPickups.form.slotCount')).toHaveCount(0);
    await fillDateTime(form.getByTestId('introPickups.form.start'), '2026-11-20T18:30');
    await walk.click(page.getByTestId('introPickups.form.save'));
    // ONLINE_CALL_LINK_REQUIRED
    await expect(page.getByTestId('introPickups.form.error')).toBeVisible();
    await checkpoint(page, 'online-form-error');
    await walk.fill(form.getByTestId('introPickups.form.callLink'), 'https://meet.jit.si/foodsharing-einab');
    const saved = page.waitForResponse((r) => r.request().method() === 'POST' && /\/guide\/intro-pickups$/.test(r.url()));
    await walk.click(page.getByTestId('introPickups.form.save'));
    onlineId = ((await (await saved).json()) as { id: string }).id;
    await expect(form).toBeHidden();
    await expect(einabs).toHaveCount(12);
    const online = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${onlineId}"]`);
    await expect(online).toHaveAttribute('data-category', 'ONLINE');
    await expect(online.getByTestId('introPickups.slot')).toHaveCount(1);
  });

  await walk.step('turns his Tiny Fish pickup into a linked EinAb', async () => {
    await walk.click(candidates.first().getByTestId('introPickups.guide.pickupCandidateUse'));
    await expect(dateField(form.getByTestId('introPickups.form.start'))).toHaveValue('06.11.2026 20:00');
    await expect(form.getByTestId('introPickups.form.businessName')).toHaveValue('Tiny Fish Stadelhofen');
    await expect(form.getByTestId('introPickups.form.address')).toHaveValue('Stadelhoferstrasse 10, 8001 Zürich');
    await expect(form.getByTestId('introPickups.form.area')).toHaveValue('Zürich');
    await expect(form.getByTestId('introPickups.form.category')).toContainText('Take-away');
    await expect(form.getByTestId('introPickups.form.linkedHint')).toBeVisible();
    await expect(form.getByTestId('introPickups.form.unlinkHint')).toHaveCount(0);
    await checkpoint(page, 'linked-form');
    const saved = page.waitForResponse((r) => r.request().method() === 'POST' && /\/guide\/intro-pickups$/.test(r.url()));
    await walk.click(page.getByTestId('introPickups.form.save'));
    const createdId = ((await (await saved).json()) as { id: string }).id;
    await expect(form).toBeHidden();
    const created = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${createdId}"]`);
    await expect(created.getByTestId('introPickups.guide.linked')).toBeVisible();
    await expect(created.getByTestId('introPickups.guide.assign')).toHaveCount(0);
    await expect(candidates).toHaveCount(1);
  });

  await walk.step('adds a take-away EinAb and saves its private info as a template', async () => {
    await walk.click(page.getByTestId('introPickups.guide.create'));
    // the supermarket (default category) has a seeded template
    await walk.click(form.getByTestId('introPickups.form.useTemplate'));
    await expect(form.getByTestId('introPickups.form.privateInfo')).not.toHaveValue('');
    await walk.click(form.getByTestId('introPickups.form.category'));
    await walk.click(page.getByRole('option', { name: 'Take-away' }));
    await expect(form.getByTestId('introPickups.form.useTemplate')).toHaveCount(0);
    await fillDateTime(form.getByTestId('introPickups.form.start'), '2026-11-25T19:00');
    await walk.fill(form.getByTestId('introPickups.form.businessName'), 'Tiny Fish Oerlikon');
    await walk.fill(form.getByTestId('introPickups.form.address'), 'Schaffhauserstrasse 300, 8050 Zürich');
    await walk.fill(form.getByTestId('introPickups.form.area'), 'Oerlikon');
    await walk.fill(form.getByTestId('introPickups.form.privateInfo'), 'Hintereingang, bitte klingeln.');
    await walk.click(form.getByTestId('introPickups.form.saveTemplate'));
    await checkpoint(page, 'take-away-form');
    await walk.click(page.getByTestId('introPickups.form.save'));
    await expect(form).toBeHidden();
    const takeaway = einabs.filter({ hasText: 'Tiny Fish Oerlikon' });
    await expect(takeaway.getByTestId('introPickups.slot')).toHaveCount(2);

    // a third place
    await walk.click(takeaway.getByTestId('introPickups.guide.edit'));
    await expect(form.getByTestId('introPickups.form.address')).toHaveValue('Schaffhauserstrasse 300, 8050 Zürich');
    await walk.fill(form.getByTestId('introPickups.form.slotCount').locator('input'), '3');
    await walk.click(page.getByTestId('introPickups.form.save'));
    await expect(form).toBeHidden();
    await expect(takeaway.getByTestId('introPickups.slot')).toHaveCount(3);
  });

  await walk.step('deletes the online EinAb and looks at the past ones', async () => {
    const online = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${onlineId}"]`);
    await walk.click(online.getByTestId('introPickups.guide.delete'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(online).toHaveCount(0);
    await expect(einabs).toHaveCount(13);
    await checkpoint(page, 'upcoming-einabs');
    await walk.click(page.getByTestId('introPickups.guide.showPast'));
    await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
    await expect(einabs.first()).toBeVisible();
    await expect(page.getByTestId('introPickups.guide.edit')).toHaveCount(0);
  });

  await walk.step('manages his templates and finds his calendar feed', async () => {
    await walk.click(page.getByTestId('introPickups.guide.settings'));
    await expect(page).toHaveURL(/\/guide\/settings$/);
    const templates = page.getByTestId('introPickups.settings.template');
    await expect(templates).toHaveCount(3);
    const takeaway = templates.filter({ hasText: 'Hintereingang' });
    await expect(takeaway).toHaveAttribute('data-category', 'TAKEOUT');
    const feed = page.getByTestId('introPickups.settings.feedUrl');
    expect(await feed.inputValue()).toContain('/api/public/intro-pickups/ical/');
    await expect(page.getByTestId('introPickups.settings.subscribe')).toHaveAttribute('href', /^webcal:/);
    await checkpoint(page, 'guide-settings', { mask: [feed] });
    await walk.click(takeaway.getByTestId('introPickups.settings.deleteTemplate'));
    await expect(templates).toHaveCount(2);

    // the feed works without a session (a calendar app)
    const response = await request.get(new URL(await feed.inputValue()).pathname);
    expect(response.status()).toBe(200);
    expect(await response.text()).toContain('BEGIN:VEVENT');
  });

  await walk.step('saves an EinAb to his calendar', async () => {
    await page.goto('/guide');
    const einab = page.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(1)}"]`);
    const download = page.waitForEvent('download');
    await walk.click(einab.getByTestId('introPickups.guide.calendar'));
    const ics = Buffer.concat(await (await (await download).createReadStream()).toArray()).toString('utf8');
    expect(ics).toContain('BEGIN:VEVENT');
    expect(ics).toContain('Coop Stauffacher');
  });

  await walk.step('signs out of the store pickup with the linked EinAb', async () => {
    await openPickups(page, 'S-03');
    const row = pickupRow(page, S03_FRIDAY);
    await walk.click(row.locator('[data-testid="stores.pickups.slot.taken"][data-user="Julien Favre"]'));
    const dialog = page.locator('.p-confirmdialog');
    await expect(dialog).toContainText('Damit wird auch die verknüpfte Einführungsabholung abgesagt');
    await expect(dialog).toContainText('1 gebuchter Neuling wird benachrichtigt');
    await checkpoint(page, 'signout-warning');
    await acceptConfirm(page);
    walk.count(1);
    await expect(row.locator('[data-user="Julien Favre"]')).toHaveCount(0);

    await page.goto('/guide');
    await expect(page.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
    await expect(seeded).toHaveCount(0);
    await expect.poll(async () => (await notifications.bells('pending', 'INTRO_PICKUP_CANCELLED_BY_GUIDE')).length).toBe(1);
    await expect.poll(async () => (await notifications.bells('guide', 'INTRO_PICKUP_AUTO_CANCELLED')).length).toBe(1);
  });

  walk.finish({ steps: 8, clicks: 25, fields: 6 });
});
