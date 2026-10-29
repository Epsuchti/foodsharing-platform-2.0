import type { Page } from '@playwright/test';
import {
  REPORTS,
  acceptPendingConsents,
  expect,
  expectDataState,
  expectErrorState,
  injectApiError,
  pickOption,
  regionId,
  seedStore,
  test,
} from '../../fixtures';

/**
 * G1 reports and D10.3 report log (plan-m7 Q8, A51). Seed (`e2e/fixtures/reports.ts`): seven reports in
 * zuerich-stadt; reports@local administers the Zürich REPORT group, so REPORT_NEW and REPORT_REMINDER bells reach
 * them; Zürich has the report button on. foodsaver is in the team of S-01 (a Zürich store).
 */
const ZURICH = regionId('zuerich-stadt');
const S01 = seedStore('S-01');

const item = (page: Page, reportId: string) => page.locator(`[data-testid="reports.item"][data-report-id="${reportId}"]`);
const logEntry = (page: Page, action: string) =>
  page.locator(`[data-testid="reports.log.entry"][data-action="REPORT.${action}"]`);

test.describe('report team', () => {
  test.use({ seedReset: 'test', role: 'foodsaver' });

  test('G1 member reports a user with store, report team gets bell, handles status, consequence, wall and reminder; log lists changes @G1 @D10.3 @N3 @page', async ({
    page,
    as,
    apiOf,
    profiles,
    notifications,
    mails,
    clock,
  }) => {
    await acceptPendingConsents(apiOf(page));
    const jumperId = await profiles.userId('jumper');
    const message = `Jonas ist am Donnerstag ohne Abmeldung nicht zur Abholung gekommen ${Date.now()}`;

    // foodsaver reports jumper from the profile, about store S-01, with a confirmation mail
    await page.goto(`/profile/${jumperId}`);
    await expectDataState(page, 'profiles.page.state', 'success');
    await page.getByTestId('profiles.page.report').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expectDataState(page, 'reports.dialog.options', 'success');
    await pickOption(page, 'reports.dialog.store', S01.id);
    await expectDataState(page, 'reports.dialog.options', 'success');
    // the region's option set 1: the four pickup reasons, no OTHER
    await page.getByTestId('reports.dialog.reason').click();
    await expect(page.locator('.p-select-overlay [data-value]')).toHaveCount(4);
    await page.locator('.p-select-overlay [data-value="NO_SHOW"]').click();
    await page.getByTestId('reports.dialog.message').fill(message);
    await page.getByTestId('reports.dialog.confirmation').locator('input').check();
    await page.getByTestId('reports.dialog.submit').click();
    await expect(dialog).toBeHidden();
    const confirmation = await mails.waitForMail({ to: 'foodsaver@local', predicate: (mail) => mail.text.includes(message) });
    expect(confirmation.text).toContain('Jonas Weber');

    // the report team is rung with a link to the report
    let reportPath = '';
    await expect
      .poll(async () => {
        const bells = await notifications.bells('reports', 'REPORT_NEW');
        reportPath = bells.map((bell) => bell.link ?? '').find((link) => link.startsWith('/reports/') && !Object.values(REPORTS).some((id) => link.endsWith(id))) ?? '';
        return reportPath;
      })
      .toMatch(/^\/reports\/[0-9a-f-]{36}$/);
    const reportId = reportPath.split('/').pop()!;

    // reports@local finds it on the region tab (TO_DO) and opens it
    const team = await as('reports');
    await acceptPendingConsents(apiOf(team));
    await team.goto(`/regions/${ZURICH}`);
    await team.getByTestId('regions.page.tab.reports').click();
    await expect(team).toHaveURL(new RegExp(`/regions/${ZURICH}/reports$`));
    await expectDataState(team, 'reports.region.state', 'success');
    await pickOption(team, 'reports.region.status', 'TO_DO');
    await expectDataState(team, 'reports.region.state', 'success');
    await expect(item(team, reportId)).toBeVisible();
    await expect(item(team, reportId).getByTestId('reports.item.store')).toContainText(S01.name);
    await expect(item(team, REPORTS.completed)).toHaveCount(0);
    await item(team, reportId).getByTestId('reports.item.open').click();
    await expect(team).toHaveURL(new RegExp(`/reports/${reportId}$`));
    await expectDataState(team, 'reports.detail.state', 'success');
    await expect(team.getByTestId('reports.detail.message')).toContainText(message);
    await expect(team.getByTestId('reports.detail.store')).toContainText(S01.name);
    await expect(team.getByTestId('reports.detail.status')).toHaveAttribute('data-status', 'TO_DO');
    // deleting is ORGA only
    await expect(team.getByTestId('reports.detail.delete')).toHaveCount(0);

    // status, consequence and a reminder at 12:00 Zürich time (11:00Z) in one save
    await pickOption(team, 'reports.detail.statusSelect', 'IN_PROGRESS');
    await team.getByTestId('reports.detail.consequence').fill('Gespräch mit Jonas geführt, Verwarnung ausgesprochen.');
    await team.getByTestId('reports.detail.reminder').fill('2026-11-02T12:00');
    await team.getByTestId('reports.detail.save').click();
    await expect(team.getByTestId('reports.detail.status')).toHaveAttribute('data-status', 'IN_PROGRESS');
    await expect(team.getByTestId('reports.detail.save').locator('button')).toBeDisabled();

    // the handlers' wall
    const comment = `Ich rufe Jonas morgen an ${Date.now()}`;
    await expectDataState(team, 'reports.detail.wall.state', 'empty');
    await team.getByTestId('reports.detail.wall.text').fill(comment);
    await team.getByTestId('reports.detail.wall.submit').click();
    await expect(team.getByTestId('reports.detail.wall.post').filter({ hasText: comment })).toHaveCount(1);

    // the reminder job rings the team once the reminder is due
    await clock.fixServer('2026-11-02T11:30:00Z');
    await notifications.runJob('report-reminders');
    await expect
      .poll(async () => (await notifications.bells('reports', 'REPORT_REMINDER')).map((bell) => bell.link))
      .toContain(`/reports/${reportId}`);
    await clock.fixServer();

    // the report log lists every change
    await team.goto(`/regions/${ZURICH}/reports`);
    await team.getByTestId('regions.page.tab.reportLog').click();
    await expect(team).toHaveURL(new RegExp(`/regions/${ZURICH}/report-log$`));
    for (const action of ['CREATED', 'STATUS_CHANGED', 'CONSEQUENCE_SET', 'REMINDER_SET', 'WALL_POST_CREATED']) {
      await expect(logEntry(team, action).first()).toBeVisible();
    }

    // the reporter does not see the report
    await page.goto(`/reports/${reportId}`);
    await expectDataState(page, 'reports.detail.state', 'forbidden');
  });
});

test.describe('visibility', () => {
  test.use({ seedReset: 'test', role: 'foodsaver' });

  test('G1 reports hidden from non-team members, button hidden when region option off @G1 @R5', async ({ page, as, apiOf, profiles }) => {
    await acceptPendingConsents(apiOf(page));
    const jumperId = await profiles.userId('jumper');

    // a member of Zürich without a report function: no report tabs, forbidden pages, no reports block
    await page.goto(`/regions/${ZURICH}`);
    await expect(page.getByTestId('regions.page.tab.mediation')).toBeVisible();
    await expect(page.getByTestId('regions.page.tab.reports')).toHaveCount(0);
    await expect(page.getByTestId('regions.page.tab.reportLog')).toHaveCount(0);
    await page.goto(`/regions/${ZURICH}/reports`);
    await expectDataState(page, 'reports.region.state', 'forbidden');
    await page.goto(`/regions/${ZURICH}/report-log`);
    await expectDataState(page, 'reports.log.list', 'forbidden');
    await page.goto(`/reports/${REPORTS.noShowS01}`);
    await expectDataState(page, 'reports.detail.state', 'forbidden');
    await page.goto(`/profile/${jumperId}`);
    await expectDataState(page, 'profiles.page.state', 'success');
    await expect(page.getByTestId('reports.user')).toHaveCount(0);
    await expect(page.getByTestId('profiles.page.report')).toBeVisible();

    // the report team sees jumper's reports on the profile; list errors retry (three failures reach the error state)
    const team = await as('reports');
    await acceptPendingConsents(apiOf(team));
    const injection = await injectApiError(team, { url: `/api/users/${jumperId}/reports`, status: 503, times: 3 });
    await team.goto(`/profile/${jumperId}`);
    await expectDataState(team, 'profiles.page.state', 'success');
    const { retry } = await expectErrorState(team, 'reports.user.state');
    expect(injection.calls()).toBe(3);
    await retry.click();
    await expectDataState(team, 'reports.user.state', 'success');
    await expect(item(team, REPORTS.noShowS01)).toBeVisible();
    await injectApiError(team, { url: `/api/regions/${ZURICH}/reports`, status: 503, times: 3 });
    await team.goto(`/regions/${ZURICH}/reports`);
    const tab = await expectErrorState(team, 'reports.region.state');
    await tab.retry.click();
    await expectDataState(team, 'reports.region.state', 'success');

    // the report team switches the report button of Zürich off: foodsaver and jumper share no region with it on
    const response = await apiOf(team).patch(`/api/regions/${ZURICH}/options`, { enableReportButton: false, reason: 'e2e G1' });
    expect(response.status(), await response.text()).toBe(200);
    await page.reload();
    await expectDataState(page, 'profiles.page.state', 'success');
    await expect(page.getByTestId('profiles.page.message')).toBeVisible();
    await expect(page.getByTestId('profiles.page.report')).toHaveCount(0);
  });
});
