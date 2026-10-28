import fs from 'node:fs';
import {
  acceptPendingConsents,
  SEED_NOW,
  expect,
  expectDataState,
  expectErrorState,
  expectNoMissingKeys,
  expectNoSeriousViolations,
  expectSkeleton,
  injectApiError,
  injectNetworkFailure,
  retryButton,
  test,
  type ApiClient,
  type Language,
} from '../../fixtures';
import type { Locator, Page } from '@playwright/test';

/**
 * D10.1/D10.3: the shared history timeline on /me/activity (plan §10, plan-m1 T7). Seed: foodsaver@local (Sarah
 * Müller) has 13 history entries incl. a FIELD_CHANGED (display name) and a ROLE_CHANGED; every dev login adds a
 * LOGIN entry.
 */

const LIST = 'history.myActivity.list';
const ENTRY = 'history.myActivity.entry';
const MY_ACTIVITY = '/api/history/me';

interface HistoryEntryPage {
  items: { action: string; subjectType: string; occurredAt: string; field?: string }[];
  page: { page: number; size: number; totalItems: number; totalPages: number };
}
interface HistoryActionInfo {
  subjectType: string;
  action: string;
  labelKey: string;
}

/** The merged catalog of the language the page currently shows. */
async function catalog(page: Page): Promise<Record<string, string>> {
  const lang = await page.locator('html').getAttribute('data-language');
  const response = await page.request.get(`/i18n/${lang ?? 'de'}.json`);
  return (await response.json()) as Record<string, string>;
}

/** The `other` branch of an ICU select label (what the filter offers for an action). */
function genericLabel(text: string): string {
  const match = /other \{([^{}]*)\}\}\s*$/.exec(text);
  return match ? match[1] : text;
}

function entries(page: Page): Locator {
  return page.getByTestId(ENTRY);
}

async function openActivity(page: Page): Promise<void> {
  await page.goto('/me/activity');
  await expectDataState(page, LIST, 'success');
}

/** Toggles the theme `times` times through the API → `times` SETTINGS_CHANGED entries. */
async function changeThemeRepeatedly(api: ApiClient, times: number): Promise<void> {
  for (let i = 0; i < times; i++) {
    const response = await api.patch('/api/users/me/settings', { theme: i % 2 === 0 ? 'DARK' : 'LIGHT' });
    expect(response.ok(), await response.text()).toBeTruthy();
  }
}

async function chooseActionFilter(page: Page, label: string): Promise<void> {
  await page.getByTestId('history.myActivity.filter.actions').click();
  await page.getByRole('option', { name: label, exact: true }).click();
  await closeOverlay(page);
}

/** Closes an open select/multiselect/datepicker overlay by clicking outside (Escape is not reliable on touch). */
async function closeOverlay(page: Page): Promise<void> {
  await page.getByRole('heading', { level: 1 }).click();
  await expect(page.locator('.p-overlay, .p-multiselect-overlay, .p-datepicker-panel')).toHaveCount(0);
}

test.describe('as foodsaver', () => {
  test.use({ role: 'foodsaver', seedReset: 'test' });

  test('D10.3 my activity shows own actions with filters and pagination @D10.3 @page', async ({ page, api, apiOf }) => {
    await changeThemeRepeatedly(api, 10);
    await page.goto('/me/activity');
    await expectDataState(page, LIST, 'success');
    // the browser session's own login is part of the total, so count through it
    const total = (await apiOf(page).getJson<HistoryEntryPage>(`${MY_ACTIVITY}?size=1`)).page.totalItems;
    expect(total).toBeGreaterThan(20);
    await expectDataState(page, LIST, 'success');
    const texts = await catalog(page);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(texts['history.myActivity.title']);
    await expect(entries(page)).toHaveCount(20);
    await expect(page.getByTestId('history.myActivity.count')).toContainText(String(total));
    // newest first: the theme changes made just now (server clock = SEED_NOW)
    await expect(entries(page).first()).toHaveAttribute('data-action', /USER\.(SETTINGS_CHANGED|LOGIN)/);
    await expect(entries(page).first()).toHaveAttribute('data-occurred-at', /2026-11-02T09:00/);
    // own actions read "by you" instead of the own chip
    await expect(entries(page).first()).toContainText(texts['shared.historyTimeline.byYou']);
    // no CSV export for non-orga
    await expect(page.getByTestId('history.myActivity.export')).toHaveCount(0);
    await expectNoMissingKeys(page);
    await expectNoSeriousViolations(page, { include: ['fs-my-activity-page'] });

    // pagination: second page holds the rest
    const paginator = page.getByTestId('history.myActivity.paginator');
    await expect(paginator).toBeVisible();
    const pageRequest = page.waitForRequest((request) => request.url().includes(`${MY_ACTIVITY}?`) && request.url().includes('page=1'));
    await paginator.getByRole('button', { name: '2', exact: true }).click();
    await pageRequest;
    await expectDataState(page, LIST, 'success');
    await expect(entries(page)).toHaveCount(total - 20);
    // the oldest entry of the seed history is on the last page
    await expect(page.locator(`[data-testid="${ENTRY}"][data-action="USER.ACCOUNT_CREATED"]`)).toHaveCount(1);

    // action filter: only role changes (back to page 1)
    await chooseActionFilter(page, texts['history.action.USER.ROLE_CHANGED']);
    await expectDataState(page, LIST, 'success');
    await expect(entries(page)).toHaveCount(1);
    await expect(entries(page).first()).toHaveAttribute('data-action', 'USER.ROLE_CHANGED');
    await expect(page.getByTestId('history.myActivity.paginator')).toHaveCount(0);
    await expect(entries(page).first().getByTestId(`${ENTRY}.change`)).toContainText(texts['common.role.FOODSAVER']);

    // details show old/new value and the reason
    await entries(page).first().getByTestId(`${ENTRY}.details`).click();
    const details = entries(page).first().getByTestId(`${ENTRY}.detailsPanel`);
    await expect(details).toBeVisible();
    await expect(details).toContainText(texts['common.role.FOODSHARER']);
    await expect(details).toContainText('ID checked by the ambassador');

    // reset → everything again
    await page.getByTestId('history.myActivity.filter.reset').click();
    await expectDataState(page, LIST, 'success');
    await expect(entries(page)).toHaveCount(20);

    // date range: 1–2 November 2026 (the picker opens on the month of the browser clock)
    await page.clock.setFixedTime(new Date(SEED_NOW));
    await page.reload();
    await expectDataState(page, LIST, 'success');
    const rangeRequest = page.waitForRequest((request) => request.url().includes(`${MY_ACTIVITY}?`) && request.url().includes('from='));
    await page.getByTestId('history.myActivity.filter.dateRange').locator('input').click();
    const panel = page.locator('.p-datepicker-panel, .p-datepicker-overlay, [role="dialog"]').filter({ has: page.locator('table') }).first();
    await expect(panel).toBeVisible();
    await dayCell(panel, 1).click();
    await dayCell(panel, 2).click();
    const url = new URL((await rangeRequest).url());
    await closeOverlay(page);
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
    await expect(page.locator(`[data-testid="${ENTRY}"][data-action="USER.ACCOUNT_CREATED"]`)).toHaveCount(0);
  });

  test('D10.1 recorded changes appear in my activity with old and new value @D10.1', async ({ page, api, dev }) => {
    await page.goto('/me/activity');
    await expectDataState(page, LIST, 'success');

    // ROLE_CHANGED through the dev role switch, SETTINGS_CHANGED (tracked language field) through the settings API
    await dev.setRole('foodsaver', 'STORE_MANAGER');
    // store managers must accept the confidentiality notice (EA4) before any other page
    expect(await acceptPendingConsents(api)).toContain('CONFIDENTIALITY_NOTICE');
    const settings = await api.patch('/api/users/me/settings', { language: 'it' });
    expect(settings.ok(), await settings.text()).toBeTruthy();

    await page.reload();
    await expectDataState(page, LIST, 'success');
    const texts = await catalog(page);

    const language = page.locator(`[data-testid="${ENTRY}"][data-action="USER.SETTINGS_CHANGED"]`).first();
    await expect(language).toHaveAttribute('data-occurred-at', /2026-11-02T09:00/);
    await expect(language.getByTestId(`${ENTRY}.label`)).toHaveText(
      /\{field, select,.*preferredLanguage \{([^}]*)\}/.exec(texts['history.action.USER.SETTINGS_CHANGED'])![1],
    );
    await expect(language.getByTestId(`${ENTRY}.change`)).toContainText('Deutsch');
    await expect(language.getByTestId(`${ENTRY}.change`)).toContainText('Italiano');

    const role = page.locator(`[data-testid="${ENTRY}"][data-action="USER.ROLE_CHANGED"]`).first();
    await expect(role).toHaveAttribute('data-occurred-at', /2026-11-02T09:00/);
    await expect(role.getByTestId(`${ENTRY}.change`)).toContainText(texts['common.role.FOODSAVER']);
    await expect(role.getByTestId(`${ENTRY}.change`)).toContainText(texts['common.role.STORE_MANAGER']);

    // a field change recorded by the @Tracked listener (seed: display name) with old and new value
    const field = page.locator(`[data-testid="${ENTRY}"][data-action="USER.FIELD_CHANGED"]`);
    await expect(field).toHaveCount(1);
    await expect(field.getByTestId(`${ENTRY}.change`)).toContainText('Sarah M.');
    await expect(field.getByTestId(`${ENTRY}.change`)).toContainText('Sarah Müller');
    await expectNoMissingKeys(page);

    // the API reports the same entries
    const history = await api.getJson<HistoryEntryPage>(`${MY_ACTIVITY}?actions=ROLE_CHANGED&actions=SETTINGS_CHANGED&size=5`);
    expect(history.items[0]).toMatchObject({ occurredAt: expect.stringMatching(/^2026-11-02T09:00/) });
    expect(history.items.map((item) => item.action)).toEqual(expect.arrayContaining(['ROLE_CHANGED', 'SETTINGS_CHANGED']));
  });

  test('D10.3 history access is denied for other users\' subjects @D10.3', async ({ page, api, dev }) => {
    const orga = await dev.user('orga');
    const foreign = await api.get(`/api/history?subjectType=USER&subjectId=${orga.id}`);
    expect(foreign.status()).toBe(403);
    const error = (await foreign.json()) as { code: string };
    expect(error.code).toBe('HISTORY_SUBJECT_NOT_READABLE');
    expect((await api.get('/api/history')).status()).toBe(403);
    expect((await api.get(`/api/history/export?subjectType=USER&subjectId=${orga.id}`)).status()).toBe(403);
    // own subject is readable
    const me = await dev.user('foodsaver');
    expect((await api.get(`/api/history?subjectType=USER&subjectId=${me.id}`)).status()).toBe(200);

    // the UI shows the forbidden state for exactly that server answer
    await injectApiError(page, { url: MY_ACTIVITY, status: 403, body: error });
    await page.goto('/me/activity');
    const { root } = await expectErrorState(page, LIST, { status: 'forbidden', retry: false });
    const texts = await catalog(page);
    await expect(root).toContainText(texts['shared.errorState.title.forbidden']);
    await expect(root).toContainText(texts['history.myActivity.askWho']);
  });

  test('D10.3 my activity shows loading, error with retry and offline states @D10.3', async ({ page }) => {
    // loading: skeleton while the request is held
    const slow = await injectApiError(page, { url: MY_ACTIVITY, delayMs: 1500 });
    await page.goto('/me/activity');
    await expectSkeleton(page, LIST);
    await expectDataState(page, LIST, 'success');
    await slow.remove();

    // 500 (also for the automatic retries) → error with trace id → retry recovers
    const failure = await injectApiError(page, { url: MY_ACTIVITY, status: 500 });
    await page.reload();
    const { retry, root } = await expectErrorState(page, LIST);
    const texts = await catalog(page);
    await expect(root).toContainText(texts['errors.UNEXPECTED_ERROR']);
    expect(failure.calls()).toBeGreaterThanOrEqual(1);
    await failure.remove();
    await retry.click();
    await expectDataState(page, LIST, 'success');
    await expect(entries(page).first()).toBeVisible();

    // network failure → offline state → back online reloads
    const offline = await injectNetworkFailure(page, { url: MY_ACTIVITY });
    await page.reload();
    await expectErrorState(page, LIST, { status: 'offline', retry: false });
    await offline.remove();
    await retryButton(page.getByTestId(LIST)).click();
    await expectDataState(page, LIST, 'success');
  });

  test('D10.3 action filter shows loading errors inside the overlay with retry @D10.3', async ({ page }) => {
    const failure = await injectApiError(page, { url: '/api/history/actions', status: 500 });
    await openActivity(page);
    await page.getByTestId('history.myActivity.filter.actions').click();
    const overlayState = page.getByTestId('history.myActivity.filter.actionsState');
    await expect(overlayState).toHaveAttribute('data-state', 'error');
    await failure.remove();
    await retryButton(overlayState).click();
    const texts = await catalog(page);
    await expect(page.getByRole('option', { name: genericLabel(texts['history.action.USER.LOGIN']), exact: true })).toBeVisible();
    await closeOverlay(page);
  });

  test('D10.3 security log links to my activity filtered to logins @D10.3 @A22', async ({ page }) => {
    await page.goto('/account/security-log');
    await expectDataState(page, 'account.securityLog.list', 'success');
    await page.getByTestId('account.securityLog.allActivity').click();
    await expect(page).toHaveURL(/\/me\/activity\?preset=logins$/);
    await expectDataState(page, LIST, 'success');
    await expect(page.getByTestId('history.myActivity.preset.logins')).toHaveAttribute('aria-pressed', 'true');
    const actions = await entries(page).evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-action')));
    expect(actions.length).toBeGreaterThan(0);
    expect(actions.every((action) => /^USER\.(LOGIN|LOGOUT|LOGIN_FAILED|SESSION_REVOKED|SESSIONS_REVOKED_ALL|MAGIC_LINK_REQUESTED)$/.test(action ?? ''))).toBe(true);

    // "all" clears the preset again
    await page.getByTestId('history.myActivity.preset.all').click();
    await expect(page.getByTestId('history.myActivity.preset.all')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator(`[data-testid="${ENTRY}"][data-action="USER.ROLE_CHANGED"]`).first()).toBeAttached();
  });

  test('D10.3 every history action has a label in all five languages @D10.3', async ({ api }) => {
    const actions = await api.getJson<HistoryActionInfo[]>('/api/history/actions');
    expect(actions.length).toBeGreaterThanOrEqual(20);
    for (const lang of ['de', 'en', 'gws', 'fr', 'it'] as Language[]) {
      const texts = await api.getJson<Record<string, string>>(`/i18n/${lang}.json`);
      const missing = actions.filter((action) => !texts[action.labelKey]).map((action) => action.labelKey);
      expect(missing, `history action labels missing in ${lang}`).toEqual([]);
    }
  });
});

test.describe('as orga', () => {
  test.use({ role: 'orga', seedReset: 'test' });

  test('D10.3 orga exports history CSV @D10.3', async ({ page }) => {
    await openActivity(page);
    const button = page.getByTestId('history.myActivity.export');
    await expect(button).toBeVisible();
    const download = page.waitForEvent('download');
    await button.click();
    const file = await download;
    expect(file.suggestedFilename()).toBe('my-activity.csv');
    const csv = fs.readFileSync((await file.path())!, 'utf8');
    const [header, ...rows] = csv.trim().split(/\r?\n/);
    expect(header).toBe(
      'occurredAt,actorType,actorId,actorName,subjectType,subjectId,secondarySubjectId,action,field,oldValue,newValue,reason,regionId,metadata,traceId',
    );
    expect(rows.length).toBeGreaterThanOrEqual(9);
    expect(csv).toContain('ACCOUNT_CREATED');
    expect(csv).toContain('PASSKEY_REGISTERED');
    expect(csv).toContain('Daniel Huber');

    // the export follows the action filter
    const texts = await catalog(page);
    await chooseActionFilter(page, texts['history.action.USER.ROLE_CHANGED']);
    await expectDataState(page, LIST, 'success');
    const filtered = page.waitForEvent('download');
    const request = page.waitForRequest((r) => r.url().includes('/api/history/export'));
    await button.click();
    expect(new URL((await request).url()).searchParams.getAll('actions')).toEqual(['ROLE_CHANGED']);
    const filteredCsv = fs.readFileSync((await (await filtered).path())!, 'utf8').trim().split(/\r?\n/);
    expect(filteredCsv.length).toBe(3);
    expect(filteredCsv.slice(1).every((row) => row.includes(',ROLE_CHANGED,'))).toBe(true);
  });
});

function dayCell(panel: Locator, day: number): Locator {
  return panel
    .locator('td:not(.p-datepicker-other-month) > span, td:not(.p-datepicker-other-month) [data-pc-section="day"]')
    .filter({ hasText: new RegExp(`^${day}$`) })
    .first();
}
