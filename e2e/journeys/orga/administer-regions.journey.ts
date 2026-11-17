import { acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { expectTableFits, expectWithin } from '../../fixtures/flows/shell';
import { Walk } from '../../fixtures/persona';

/**
 * ORGA (orga@local) administers the region tree on /admin/regions: opens it down to Kanton Zürich, creates a district
 * below it, renames it with a reason and deletes it again with a reason, moves the PR function of Zürich Stadt to its
 * working group again, and imports a region from CSV after the preview. Rules behind it: RegionAdminServiceTest (ORGA
 * only, unique slugs, functions unique per region, delete refused with children/members, CSV preview).
 */
test.use({ role: 'orga' });

test('orga creates, renames and deletes a region, assigns a function and imports regions from CSV @R7 @R1 @R10', async ({
  page,
  apiOf,
}, testInfo) => {
  test.setTimeout(150_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'orga');
  const row = page.locator('[data-region="testbezirk-uster"]');

  await walk.step('opens the region tree down to Kanton Zürich', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminRegions');
    await expect(page).toHaveURL(/\/admin\/regions$/);
    await expectDataState(page, 'regionAdmin.list', 'success');
    await walk.click(page.getByTestId('regionAdmin.open.europa'));
    await walk.click(page.getByTestId('regionAdmin.open.schweiz'));
    await walk.click(page.getByTestId('regionAdmin.open.kanton-zuerich'));
    await expect(page.locator('[data-region="zuerich-stadt"]')).toBeVisible();
    await checkpoint(page, 'region-tree');
  });

  await walk.step('creates a district below it', async () => {
    await walk.click(page.getByTestId('regionAdmin.create'));
    await walk.fill(page.getByTestId('regionAdmin.form.name'), 'Testbezirk Uster');
    await walk.fill(page.getByTestId('regionAdmin.form.slug'), 'testbezirk-uster');
    // the buttons sit in the dialog's footer: on a phone they stay on screen below the long form
    await expect(page.getByTestId('regionAdmin.form.save')).toBeInViewport();
    await checkpoint(page, 'create-dialog');
    await walk.click(page.getByTestId('regionAdmin.form.save'));
    await expect(row).toBeVisible();
  });

  await walk.step('renames it with a reason', async () => {
    await walk.click(row.getByTestId('regionAdmin.edit'));
    await walk.fill(page.getByTestId('regionAdmin.form.name'), 'Testbezirk Uster-Greifensee');
    // editing needs a reason
    await expect(page.getByTestId('regionAdmin.form.save').locator('button')).toBeDisabled();
    await walk.fill(page.getByTestId('regionAdmin.form.reason'), 'Name präzisiert');
    await walk.click(page.getByTestId('regionAdmin.form.save'));
    await expect(row).toContainText('Testbezirk Uster-Greifensee');
  });

  await walk.step('deletes it again with a reason', async () => {
    await walk.click(row.getByTestId('regionAdmin.delete'));
    await walk.fill(page.getByTestId('regionAdmin.deleteDialog.message'), 'Testbezirk wieder entfernt');
    await checkpoint(page, 'delete-dialog');
    await walk.click(page.getByTestId('regionAdmin.deleteDialog.confirm'));
    await expect(row).toHaveCount(0);
  });

  await walk.step('assigns the PR function of Zürich Stadt again', async () => {
    await walk.fill(page.getByTestId('regionAdmin.search'), 'Zürich Stadt');
    await walk.click(page.locator('[data-region="zuerich-stadt"]').getByTestId('regionAdmin.functions'));
    const dialog = page.getByTestId('regionAdmin.functionsDialog');
    const pr = dialog.locator('[data-function="PR"]');
    await expect(pr).toBeVisible();
    await walk.click(pr.getByTestId('regionAdmin.removeFunction'));
    await expect(pr).toHaveCount(0);
    await walk.click(page.getByTestId('regionAdmin.newFunction'));
    await walk.click(page.getByRole('option', { name: 'Öffentlichkeitsarbeit', exact: true }));
    await walk.click(page.getByTestId('regionAdmin.newGroup'));
    await walk.click(page.getByRole('option', { name: 'Öffentlichkeitsarbeit Zürich' }));
    // wait for the select overlay to close: on touch devices the next tap would only dismiss it
    await expect(page.getByRole('listbox')).toHaveCount(0);
    await checkpoint(page, 'functions-dialog');
    await walk.click(page.getByTestId('regionAdmin.assign'));
    await expect(pr).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
  });

  await walk.step('imports a region from CSV after the preview', async () => {
    await walk.click(page.getByTestId('regionAdmin.import'));
    const csv = 'parent_slug;slug;name;type;time_zone;country;postcodes\nkanton-zuerich;csv-test;CSV Test;CITY;Europe/Zurich;CH;8610\n';
    await page.getByTestId('regionAdmin.csv.input').setInputFiles({ name: 'regions.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
    walk.count(1);
    await expect(page.getByTestId('regionAdmin.importSummary')).toBeVisible();
    await expect(page.getByTestId('regionAdmin.importRow')).toHaveCount(1);
    // the column list wraps and the preview rows are blocks on a phone: nothing runs out of the dialog
    const importDialog = page.getByTestId('regionAdmin.importDialog');
    const columns = importDialog.locator('code.columns');
    await expectWithin(columns, page.locator('.p-dialog').filter({ has: page.locator('code.columns') }), 'CSV column list');
    await expectTableFits(importDialog.locator('table'));
    await expect(page.getByTestId('regionAdmin.importConfirm')).toBeInViewport();
    await checkpoint(page, 'import-preview');
    await walk.click(page.getByTestId('regionAdmin.importConfirm'));
    await expect(page.getByTestId('regionAdmin.importDialog')).toBeHidden();
  });

  walk.finish({ steps: 6, clicks: 22, fields: 7 });
});
