import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { ZH, bookableCards, openBookable } from '../../fixtures/flows/einab';
import { Walk } from '../../fixtures/persona';
import { fillDateTime } from '../../support/date-inputs';

/**
 * Zürich's ambassador Reto (bot@local, user manager) runs the region's intro pickups (EinAbs). In the region settings
 * he allows several EinAbs with the same guide and drops the Fairteiler rule, so newcomer Luca can book a take-away
 * EinAb. On the admin page he deletes an unbooked EinAb with a reason, adds a past EinAb for Luca, counts a cancelled
 * booking after all and leaves a marked admin comment. On the guides tab he gives Julien the newcomer-manager right
 * (Julien then sees the guide candidates, but the release stays with the ambassadors) and takes it back, then pauses
 * him with a reason. He imports past EinAbs from a CSV file (preview with duplicates,
 * unknown people and future dates; a second import creates nothing) and reads the newcomer funnel with names.
 * Rules behind it: IntroPickupValidationTest and PastImportZuerichIT (import), PastImportJobTest (large imports, EA20),
 * NewcomerProgressServiceTest (funnel, names only for user managers), IntroPickupAdminServiceGuideTest (release/pause),
 * BookingRulesTest (the settings' rules), intro-pickups-pages.spec.ts (counted switch).
 */
test.use({ role: 'bot' });

const CSV =
  '﻿ID;Datum;No.;Betrieb;Kategorie;EinAb-Geber;FT-Besuch;Feedback\n' +
  '100004;01.09.2026;1;babus;Bäckerei;Anna;nein;"Gut\ngemacht"\n' +
  '100004;01.09.2026;1;Babus Bakery;Bäckerei;Anna;nein;doppelt\n' +
  '100099;02.09.2026;1;Coop;Supermarkt;Anna;ja;x\n' +
  '100005;03.12.2026;x;Coop;Supermarkt;Anna;ja;Zukunft\n' +
  '100005;05.09.2026;x;Coop;FT-Reinigung;Anna;ja;Nicht erschienen\n';

test('ambassador manages EinAbs, bookings, guides, rules, the past import and the funnel @EA13 @EA17 @EA18 @EA19 @EA21 @EA22 @D10.3', async ({
  page,
  as,
}, testInfo) => {
  test.setTimeout(180_000);
  const walk = new Walk(page, testInfo, 'ambassador');
  const rows = page.getByTestId('introPickups.admin.einab');
  const bookings = page.getByTestId('introPickups.admin.booking');

  await walk.step('relaxes the booking rules of the region', async () => {
    const luca = await as('einab1');
    await openBookable(luca);
    await expect(bookableCards(luca, 'TAKEOUT')).toHaveCount(0);

    await page.goto(`/regions/${ZH}/settings`);
    await expect(page.getByTestId('regions.settings.state')).toHaveAttribute('data-state', 'success');
    await walk.click(page.locator('#set-guide'));
    await walk.click(page.getByTestId('regions.settings.requireFairteiler'));
    await checkpoint(page, 'booking-rules');
    await walk.click(page.getByTestId('regions.settings.save'));
    await expect(page.getByTestId('shell.toast')).toBeVisible();

    // Luca can book a take-away EinAb now
    await openBookable(luca);
    await expect(bookableCards(luca, 'TAKEOUT').first().getByTestId('introPickups.bookable.book')).toBeVisible();
  });

  await walk.step('deletes an unbooked EinAb with a reason', async () => {
    await page.goto('/regions/home/intro-pickups/admin');
    await expect(page).toHaveURL(new RegExp(`/regions/${ZH}/intro-pickups/admin$`));
    await expect(page.getByTestId('introPickups.admin.einabsState')).toHaveAttribute('data-state', 'success');
    await expect(rows).toHaveCount(15);
    await checkpoint(page, 'admin-einabs');
    await walk.click(rows.first().getByTestId('introPickups.admin.delete'));
    await walk.fill(page.getByTestId('introPickups.admin.deleteDialog.message'), 'Betrieb hat geschlossen, Termin entfällt.');
    await checkpoint(page, 'delete-dialog');
    await walk.click(page.getByTestId('introPickups.admin.deleteDialog.confirm'));
    await expect(rows).toHaveCount(14);
    // booked EinAbs have no delete button
    await expect(rows.filter({ hasText: '1 / 1' }).getByTestId('introPickups.admin.delete')).toHaveCount(0);
  });

  await walk.step('adds a past EinAb for Luca', async () => {
    await walk.click(page.getByTestId('introPickups.admin.historicalOpen'));
    await walk.fill(page.getByTestId('introPickups.pick.search'), 'Luca');
    await expect(page.getByTestId('introPickups.pick.choose')).toHaveCount(1);
    await walk.click(page.getByTestId('introPickups.pick.choose'));
    const form = page.getByTestId('introPickups.admin.historicalForm');
    await fillDateTime(form.getByTestId('introPickups.admin.historicalStart'), '2026-10-01T10:00');
    await walk.fill(form.getByTestId('introPickups.admin.historicalBusiness'), 'Coop Altstetten');
    await walk.click(page.getByTestId('introPickups.admin.historicalSave'));
    await expect(form).toBeHidden();
  });

  await walk.step('counts a cancelled booking after all', async () => {
    await walk.click(page.getByTestId('introPickups.admin.tab.bookings'));
    await walk.click(page.getByTestId('introPickups.admin.pastBookings'));
    await expect(page.getByTestId('introPickups.admin.bookingsState')).toHaveAttribute('data-state', 'success');
    await expect(bookings.filter({ hasText: 'Luca' })).toHaveCount(2);
    const statuses = await bookings.evaluateAll((list) => list.map((row) => row.getAttribute('data-status')));
    const cancelled = bookings.nth(statuses.indexOf('CANCELLED'));
    const toggle = cancelled.getByTestId('introPickups.admin.counted').locator('input');
    await expect(toggle).not.toBeChecked();
    await checkpoint(page, 'past-bookings');
    const reloaded = page.waitForResponse((r) => r.request().method() === 'GET' && r.url().includes('/admin/bookings'));
    await walk.click(cancelled.getByTestId('introPickups.admin.counted'));
    await reloaded;
    await expect(page.getByTestId('introPickups.admin.bookingsState')).toHaveAttribute('data-state', 'success');
    await expect(cancelled).toHaveAttribute('data-status', 'BOOKED');
    await expect(toggle).toBeChecked();
  });

  await walk.step('leaves an admin comment on a booking', async () => {
    await walk.click(bookings.first().getByTestId('introPickups.admin.comments'));
    const drawer = page.getByTestId('introPickups.comments');
    await expect(drawer.getByTestId('introPickups.comments.state')).toHaveAttribute('data-state', /success|empty/);
    await walk.fill(drawer.getByTestId('introPickups.comments.text'), 'Ausweis vor Ort gesehen.');
    await walk.click(drawer.getByTestId('introPickups.comments.send'));
    const mine = drawer.getByTestId('introPickups.comments.item').filter({ hasText: 'Ausweis vor Ort' });
    await expect(mine.getByTestId('introPickups.comments.admin')).toBeVisible();
    await checkpoint(page, 'admin-comment');
    await page.keyboard.press('Escape');
    await expect(drawer).toBeHidden();
  });

  await walk.step('gives Julien the manager right, takes it back and pauses him', async () => {
    await walk.click(page.getByTestId('introPickups.admin.tab.guides'));
    await expect(page.getByTestId('introPickups.admin.guidesState')).toHaveAttribute('data-state', 'success');
    const julien = page.getByTestId('introPickups.admin.guide').filter({ hasText: 'Julien Favre' });
    await expect(julien).toHaveAttribute('data-status', 'ACTIVE');
    await expect(julien).toHaveAttribute('data-admin', 'false');
    await walk.click(julien.getByTestId('introPickups.admin.toggleAdmin'));
    await expect(julien).toHaveAttribute('data-admin', 'true');
    await expect(julien.getByTestId('introPickups.admin.managerTag')).toBeVisible();
    // as a newcomer manager without region administration Julien sees who is ready, but the release is the ambassadors'
    const asJulien = await as('guide');
    await asJulien.goto(`/regions/${ZH}/intro-pickups/admin?tab=guides`);
    await expect(asJulien.getByTestId('introPickups.admin.candidatesState')).toHaveAttribute('data-state', 'success');
    const jonas = asJulien.getByTestId('introPickups.admin.candidate').filter({ hasText: 'Jonas' });
    await expect(jonas.getByTestId('introPickups.admin.releaseByAmbassadors')).toBeVisible();
    await expect(jonas.getByTestId('introPickups.admin.candidateRelease')).toHaveCount(0);
    await expect(asJulien.getByTestId('introPickups.admin.addGuide')).toHaveCount(0);
    await walk.click(julien.getByTestId('introPickups.admin.toggleAdmin'));
    await expect(julien).toHaveAttribute('data-admin', 'false');
    await walk.click(julien.getByTestId('introPickups.admin.toggleGuide'));
    await walk.fill(page.getByTestId('introPickups.admin.enableDialog.message'), 'Pause bis Januar');
    await walk.click(page.getByTestId('introPickups.admin.enableDialog.confirm'));
    // he stays in the list as paused, with the action to enable him again
    await expect(julien).toHaveAttribute('data-status', 'DISABLED');
    await expect(julien.getByTestId('introPickups.admin.guideStatus')).toHaveText(/Deaktiviert/);
    await expect(julien.getByTestId('introPickups.admin.toggleGuide')).toHaveText(/Freischalten/);
    await checkpoint(page, 'guides-tab');
  });

  await walk.step('imports past EinAbs from a CSV file', async () => {
    await walk.click(page.getByTestId('introPickups.admin.import'));
    await expect(page).toHaveURL(/\/intro-pickups\/import$/);
    await page.getByTestId('introPickups.import.csv.input').setInputFiles({ name: 'einab.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
    walk.count(1);
    await expect(page.getByTestId('introPickups.import.csv.file')).toHaveText('einab.csv');
    await walk.click(page.getByTestId('introPickups.import.previewButton'));
    const preview = page.getByTestId('introPickups.import.preview');
    await expect(preview.getByTestId('introPickups.import.total')).toHaveText('5');
    await expect(preview.getByTestId('introPickups.import.willCreate')).toHaveText('2');
    await expect(preview.getByTestId('introPickups.import.willSkip')).toHaveText('1');
    await expect(preview.getByTestId('introPickups.import.attention')).toHaveText('2');
    await expect(preview.getByTestId('introPickups.import.storeMapping').first()).toContainText("Babu's Bakery");
    await expect(preview.locator('[data-reason="DUPLICATE_IN_FILE"]')).toHaveCount(1);
    await expect(preview.locator('[data-reason="UNKNOWN_USER"]')).toHaveCount(1);
    await expect(preview.locator('[data-reason="FUTURE_DATE"]')).toHaveCount(1);
    await checkpoint(page, 'import-preview');
    await walk.click(preview.getByTestId('introPickups.import.filter.NEEDS_ATTENTION'));
    await expect(preview.getByTestId('introPickups.import.row')).toHaveCount(2);
    await walk.click(preview.getByTestId('introPickups.import.confirm'));
    await expect(page.getByTestId('introPickups.import.resultText')).toContainText('2');

    // the same file again: nothing new
    await page.getByTestId('introPickups.import.csv.input').setInputFiles({ name: 'einab-2.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
    walk.count(1);
    await walk.click(page.getByTestId('introPickups.import.previewButton'));
    await expect(page.getByTestId('introPickups.import.willCreate')).toHaveText('0');
    await expect(page.getByTestId('introPickups.import.confirm').locator('button')).toBeDisabled();
  });

  await walk.step('reads the newcomer funnel', async () => {
    await page.goto('/dashboard');
    await walk.nav('newcomers');
    await expect(page).toHaveURL(new RegExp(`/regions/${ZH}/newcomers$`));
    await expect(page.getByTestId('introPickups.funnel.state')).toHaveAttribute('data-state', 'success');
    await walk.click(page.getByTestId('introPickups.funnel.window'));
    await walk.click(page.getByRole('option').nth(3)); // 365 days
    await expect(page.locator('[data-testid="introPickups.funnel.step"][data-stage="IN_PROGRESS"]').first()).toBeVisible();
    await walk.click(page.getByTestId('introPickups.funnel.names').first().locator('summary'));
    await expect(page.getByTestId('introPickups.funnel.person').first()).toBeVisible();
    await expect(page.getByTestId('introPickups.funnel.gap').first()).toBeVisible();
    await checkpoint(page, 'funnel');
  });

  walk.finish({ steps: 8, clicks: 30, fields: 5 });
});
