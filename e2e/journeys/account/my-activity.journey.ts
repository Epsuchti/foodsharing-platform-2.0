import type { Locator, Page } from '@playwright/test';
import { SEED_NOW, acceptPendingConsents, expectDataState, expectNoMissingKeys } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local) reads her own history on "my activity" (D10.3): newest first, "by you", 20 per
 * page, the action filter with the details of a role change, and a date range. What happened before is recorded by
 * the history module (D10.1): her ambassador made her store manager (ROLE_CHANGED), she switched the language on
 * her phone (SETTINGS_CHANGED) and the seed's display name change (FIELD_CHANGED, @Tracked listener) shows old and
 * new value. Seed: 13 history entries incl. FIELD_CHANGED and ROLE_CHANGED. Access rules (other people's
 * subjects, ORGA) and labels: HistoryAccessPolicyTest, HistoryI18nCoverageTest; CSV export: HistoryControllerTest.
 */
test.use({ role: 'foodsaver' });

const LIST = 'history.myActivity.list';
const ENTRY = 'history.myActivity.entry';

const entries = (page: Page): Locator => page.getByTestId(ENTRY);
const entry = (page: Page, action: string): Locator => page.locator(`[data-testid="${ENTRY}"][data-action="USER.${action}"]`);

/** The merged catalog of the language the page currently shows. */
async function catalog(page: Page): Promise<Record<string, string>> {
  const lang = await page.locator('html').getAttribute('data-language');
  return (await (await page.request.get(`/i18n/${lang ?? 'de'}.json`)).json()) as Record<string, string>;
}

/** Closes an open select/multiselect/datepicker overlay by clicking outside (Escape is not reliable on touch). */
async function closeOverlay(page: Page): Promise<void> {
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message')).toHaveCount(0, { timeout: 15_000 });
  const overlays = page.locator('.p-overlay, .p-multiselect-overlay, .p-datepicker-panel');
  // under load the first outside click can land while the overlay is still opening and is swallowed: retry it
  await expect(async () => {
    if ((await overlays.count()) > 0) await page.getByRole('heading', { level: 1 }).click({ timeout: 2_000 });
    await expect(overlays).toHaveCount(0, { timeout: 1_500 });
  }).toPass({ timeout: 10_000 });
}

function dayCell(panel: Locator, day: number): Locator {
  return panel
    .locator('td:not(.p-datepicker-other-month) > span, td:not(.p-datepicker-other-month) [data-pc-section="day"]')
    .filter({ hasText: new RegExp(`^${day}$`) })
    .first();
}

test('foodsaver reads her activity with filters, details, pages and a date range @D10.3 @D10.1', async ({ page, api, dev }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'foodsaver');

  // before: ten theme switches and a language change on her phone, and her ambassador makes her store manager
  for (let i = 0; i < 10; i++) {
    const response = await api.patch('/api/users/me/settings', { theme: i % 2 === 0 ? 'DARK' : 'LIGHT' });
    expect(response.ok(), await response.text()).toBeTruthy();
  }
  await dev.setRole('foodsaver', 'STORE_MANAGER');
  // store managers accept the confidentiality notice (EA4) before any other page
  expect(await acceptPendingConsents(api)).toContain('CONFIDENTIALITY_NOTICE');
  for (const language of ['it', 'de']) {
    const response = await api.patch('/api/users/me/settings', { language });
    expect(response.ok(), await response.text()).toBeTruthy();
  }

  let total = 0;
  let fieldChanges = 0;
  await walk.step('opens her activity: newest first, by her', async () => {
    await page.goto('/me/activity');
    await expectDataState(page, LIST, 'success');
    total = (await api.getJson<{ page: { totalItems: number } }>('/api/history/me?size=1')).page.totalItems;
    expect(total).toBeGreaterThan(20);
    const texts = await catalog(page);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(texts['history.myActivity.title']);
    await expect(entries(page)).toHaveCount(20);
    await expect(page.getByTestId('history.myActivity.count')).toContainText(String(total));
    await expect(entries(page).first()).toHaveAttribute('data-action', /USER\.(SETTINGS_CHANGED|LOGIN)/);
    await expect(entries(page).first()).toHaveAttribute('data-occurred-at', /2026-11-02T09:00/);
    await expect(entries(page).first()).toContainText(texts['shared.historyTimeline.byYou']);
    // no CSV export for non-orga
    await expect(page.getByTestId('history.myActivity.export')).toHaveCount(0);
    await expectNoMissingKeys(page);
    await checkpoint(page, 'my-activity');

    // D10.1: the language change of her phone and the role change, with old and new value
    const language = entry(page, 'SETTINGS_CHANGED').first();
    await expect(language.getByTestId(`${ENTRY}.label`)).toHaveText(
      /\{field, select,.*preferredLanguage \{([^}]*)\}/.exec(texts['history.action.USER.SETTINGS_CHANGED'])![1],
    );
    await expect(language.getByTestId(`${ENTRY}.change`)).toContainText('Italiano');
    await expect(language.getByTestId(`${ENTRY}.change`)).toContainText('Deutsch');
    const role = entry(page, 'ROLE_CHANGED').first();
    await expect(role).toHaveAttribute('data-occurred-at', /2026-11-02T09:00/);
    await expect(role.getByTestId(`${ENTRY}.change`)).toContainText(texts['common.role.FOODSAVER']);
    await expect(role.getByTestId(`${ENTRY}.change`)).toContainText(texts['common.role.STORE_MANAGER']);
    fieldChanges = await entry(page, 'FIELD_CHANGED').count();
  });

  await walk.step('reads the second page', async () => {
    const paginator = page.getByTestId('history.myActivity.paginator');
    await expect(paginator).toBeVisible();
    const pageRequest = page.waitForRequest((request) => request.url().includes('/api/history/me?') && request.url().includes('page=1'));
    await walk.click(paginator.getByRole('button', { name: '2', exact: true }));
    await pageRequest;
    await expectDataState(page, LIST, 'success');
    await expect(entries(page)).toHaveCount(total - 20);
    // the oldest entry of the seed history is on the last page
    await expect(entry(page, 'ACCOUNT_CREATED')).toHaveCount(1);
    // the seed's display name change (recorded by the @Tracked listener) with old and new value
    const field = entry(page, 'FIELD_CHANGED');
    expect(fieldChanges + (await field.count())).toBe(1);
    if ((await field.count()) === 1) {
      await expect(field.getByTestId(`${ENTRY}.change`)).toContainText('Sarah M.');
      await expect(field.getByTestId(`${ENTRY}.change`)).toContainText('Sarah Müller');
    }
  });

  await walk.step('filters role changes and opens the details', async () => {
    const texts = await catalog(page);
    await walk.click(page.getByTestId('history.myActivity.filter.actions'));
    await walk.click(page.getByRole('option', { name: texts['history.action.USER.ROLE_CHANGED'], exact: true }));
    await closeOverlay(page);
    walk.count(1);
    await expectDataState(page, LIST, 'success');
    await expect(entries(page)).toHaveCount(2);
    await expect(page.getByTestId('history.myActivity.paginator')).toHaveCount(0);
    // the seed's verification: FOODSHARER → FOODSAVER with the ambassador's reason
    const verified = entries(page).nth(1);
    await expect(verified).toHaveAttribute('data-action', 'USER.ROLE_CHANGED');
    await expect(verified.getByTestId(`${ENTRY}.change`)).toContainText(texts['common.role.FOODSAVER']);
    await walk.click(verified.getByTestId(`${ENTRY}.details`));
    const details = verified.getByTestId(`${ENTRY}.detailsPanel`);
    await expect(details).toBeVisible();
    await expect(details).toContainText(texts['common.role.FOODSHARER']);
    await expect(details).toContainText('ID checked by the ambassador');
    await checkpoint(page, 'activity-details');
    await walk.click(page.getByTestId('history.myActivity.filter.reset'));
    await expectDataState(page, LIST, 'success');
    await expect(entries(page)).toHaveCount(20);
  });

  await walk.step('looks at 1 and 2 November only', async () => {
    // the picker opens on the month of the browser clock
    await page.clock.setFixedTime(new Date(SEED_NOW));
    await page.reload();
    await expectDataState(page, LIST, 'success');
    const rangeRequest = page.waitForRequest((request) => request.url().includes('/api/history/me?') && request.url().includes('from='));
    await walk.click(page.getByTestId('history.myActivity.filter.dateRange').locator('input'));
    const panel = page.locator('.p-datepicker-panel, .p-datepicker-overlay, [role="dialog"]').filter({ has: page.locator('table') }).first();
    await expect(panel).toBeVisible();
    await walk.click(dayCell(panel, 1));
    await walk.click(dayCell(panel, 2));
    const url = new URL((await rangeRequest).url());
    await closeOverlay(page);
    walk.count(1);
    const from = new Date(url.searchParams.get('from')!);
    const to = new Date(url.searchParams.get('to')!);
    expect(from.getTime()).toBeLessThanOrEqual(Date.parse('2026-11-01T00:00:00+01:00'));
    expect(to.getTime()).toBeGreaterThanOrEqual(Date.parse('2026-11-02T23:00:00+01:00'));
    await expectDataState(page, LIST, 'success');
    const occurred = await entries(page).evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-occurred-at')));
    expect(occurred.length).toBeGreaterThan(0);
    for (const value of occurred) {
      expect(Date.parse(value!)).toBeGreaterThanOrEqual(from.getTime());
      expect(Date.parse(value!)).toBeLessThanOrEqual(to.getTime());
    }
    await expect(entry(page, 'ACCOUNT_CREATED')).toHaveCount(0);
    await checkpoint(page, 'activity-date-range');
  });

  // measured 2026-11-15 (rework step 3): 10 clicks on desktop and phone
  walk.finish({ steps: 4, clicks: 10, fields: 0 });
});
