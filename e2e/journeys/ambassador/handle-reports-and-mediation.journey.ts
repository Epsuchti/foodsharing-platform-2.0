import type { Page } from '@playwright/test';
import { REPORTS, acceptPendingConsents, expectDataState, pickOption, regionId, seedStore } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, openNewestBell } from '../../fixtures/persona';
import { fillDateTime } from '../../support/date-inputs';

/**
 * Reports and mediation of Zürich Stadt (G1, G2; seed `e2e/fixtures/reports.ts`). reports@local (Céline) administers the
 * Zürich REPORT and MEDIATION groups, Zürich has both buttons on. Sarah (foodsaver@local) reports Jonas for a no-show
 * at S-01; Céline gets the bell, handles the report (status, consequence, reminder, wall), finds it in the region list,
 * on Jonas' profile and in the report log, and switches the report button off. In the second test Sarah asks for
 * mediation. Rules behind it: ReportPermissionMatrixTest (who files, reasons per option set, who handles/deletes, who
 * sees mediation), ReportHandlerRuleTest, ReportReminderTest, MediationServiceTest.
 */
const ZURICH = regionId('zuerich-stadt');
const S01 = seedStore('S-01');
const JONAS = '00000001-0001-4000-8000-000000000007';
const SARAH = '00000001-0001-4000-8000-000000000006';
const CELINE = '00000001-0001-4000-8000-000000000013';

const item = (page: Page, reportId: string) => page.locator(`[data-testid="reports.item"][data-report-id="${reportId}"]`);

test.use({ role: 'foodsaver' });

test('a member reports a no-show, the report team handles it and switches the report button off @G1 @R5 @N3 @D10.3', async ({
  page,
  as,
  apiOf,
  mails,
  notifications,
  clock,
}, testInfo) => {
  test.setTimeout(150_000);
  await acceptPendingConsents(apiOf(page));
  await mails.clear();
  const member = new Walk(page, testInfo, 'member');
  const message = 'Jonas ist am Donnerstag ohne Abmeldung nicht zur Abholung gekommen.';

  await member.step('reports Jonas about S-01 from his profile', async () => {
    await page.goto(`/profile/${JONAS}`);
    await expectDataState(page, 'profiles.page.state', 'success');
    // members without a report function see no reports block
    await expect(page.getByTestId('reports.user')).toHaveCount(0);
    await member.click(page.getByTestId('profiles.page.report'));
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expectDataState(page, 'reports.dialog.options', 'success');
    await pickOption(page, 'reports.dialog.store', S01.id);
    member.count(2);
    await expectDataState(page, 'reports.dialog.options', 'success');
    // the region's option set 1: the four pickup reasons, no OTHER
    await member.click(page.getByTestId('reports.dialog.reason'));
    await expect(page.locator('.p-select-overlay [data-value]')).toHaveCount(4);
    await member.click(page.locator('.p-select-overlay [data-value="NO_SHOW"]'));
    await member.fill(page.getByTestId('reports.dialog.message'), message);
    await member.check(page.getByTestId('reports.dialog.confirmation').locator('input'));
    await checkpoint(page, 'report-dialog');
    await member.click(page.getByTestId('reports.dialog.submit'));
    await expect(dialog).toBeHidden();
    const confirmation = await mails.waitForMail({ to: 'foodsaver@local', predicate: (mail) => mail.text.includes(message) });
    expect(confirmation.text).toContain('Jonas Weber');
  });
  member.finish({ steps: 1, clicks: 7, fields: 1 });

  const team = await as('reports');
  await acceptPendingConsents(apiOf(team));
  const celine = new Walk(team, testInfo, 'report team');
  let reportId = '';

  await celine.step('opens the new report from the bell', async () => {
    await team.goto('/dashboard');
    const path = await openNewestBell(celine, apiOf(team), 'REPORT_NEW');
    expect(path).toMatch(/^\/reports\/[0-9a-f-]{36}$/);
    reportId = path.split('/').pop()!;
    expect(Object.values(REPORTS)).not.toContain(reportId);
    await expectDataState(team, 'reports.detail.state', 'success');
    await expect(team.getByTestId('reports.detail.message')).toContainText(message);
    await expect(team.getByTestId('reports.detail.store')).toContainText(S01.name);
    await expect(team.getByTestId('reports.detail.status')).toHaveAttribute('data-status', 'TO_DO');
    // deleting is ORGA only
    await expect(team.getByTestId('reports.detail.delete')).toHaveCount(0);
    await checkpoint(team, 'report-detail');
  });

  await celine.step('takes it on with a consequence, a reminder and a note on the wall', async () => {
    // status, consequence and a reminder at 12:00 Zürich time (11:00Z) in one save
    await pickOption(team, 'reports.detail.statusSelect', 'IN_PROGRESS');
    celine.count(2);
    await celine.fill(team.getByTestId('reports.detail.consequence'), 'Gespräch mit Jonas geführt, Verwarnung ausgesprochen.');
    await fillDateTime(team.getByTestId('reports.detail.reminder'), '2026-11-02T12:00');
    celine.count(1);
    await celine.click(team.getByTestId('reports.detail.save'));
    await expect(team.getByTestId('reports.detail.status')).toHaveAttribute('data-status', 'IN_PROGRESS');
    await expect(team.getByTestId('reports.detail.save').locator('button')).toBeDisabled();

    const comment = 'Ich rufe Jonas morgen an.';
    await expectDataState(team, 'reports.detail.wall.state', 'empty');
    await celine.fill(team.getByTestId('reports.detail.wall.text'), comment);
    await celine.click(team.getByTestId('reports.detail.wall.submit'));
    await expect(team.getByTestId('reports.detail.wall.post').filter({ hasText: comment })).toHaveCount(1);
    // the post's age counts from the browser clock
    await checkpoint(team, 'report-handled', { mask: [team.locator('fs-wall time')] });
  });

  // the reminder job rings the team once the reminder is due
  await clock.fixServer('2026-11-02T11:30:00Z');
  await notifications.runJob('report-reminders');
  await expect
    .poll(async () => (await notifications.bells('reports', 'REPORT_REMINDER')).map((bell) => bell.link))
    .toContain(`/reports/${reportId}`);
  await clock.fixServer();

  // the reporter does not see the report
  await page.goto(`/reports/${reportId}`);
  await expectDataState(page, 'reports.detail.state', 'forbidden');

  await celine.step('finds it in the region\'s reports and in the report log', async () => {
    await team.goto(`/regions/${ZURICH}`);
    await celine.click(team.getByTestId('regions.page.tab.reports'));
    await expect(team).toHaveURL(new RegExp(`/regions/${ZURICH}/reports$`));
    await expectDataState(team, 'reports.region.state', 'success');
    await pickOption(team, 'reports.region.status', 'IN_PROGRESS');
    celine.count(2);
    await expectDataState(team, 'reports.region.state', 'success');
    await expect(item(team, reportId).getByTestId('reports.item.store')).toContainText(S01.name);
    await expect(item(team, REPORTS.completed)).toHaveCount(0);
    await checkpoint(team, 'region-reports');

    await celine.click(team.getByTestId('regions.page.tab.reportLog'));
    await expect(team).toHaveURL(new RegExp(`/regions/${ZURICH}/report-log$`));
    for (const action of ['CREATED', 'STATUS_CHANGED', 'CONSEQUENCE_SET', 'REMINDER_SET', 'WALL_POST_CREATED']) {
      await expect(team.locator(`[data-testid="reports.log.entry"][data-action="REPORT.${action}"]`).first()).toBeVisible();
    }
    await checkpoint(team, 'report-log');
  });

  await celine.step('sees Jonas\' reports on his profile', async () => {
    await team.goto(`/profile/${JONAS}`);
    await expectDataState(team, 'reports.user.state', 'success');
    await expect(item(team, REPORTS.noShowS01)).toBeVisible();
    await expect(item(team, reportId)).toBeVisible();
    await checkpoint(team, 'profile-reports');
  });

  await celine.step('switches the report button of Zürich off', async () => {
    await team.goto(`/regions/${ZURICH}/options`);
    await expectDataState(team, 'regions.options.state', 'success');
    await celine.click(team.getByTestId('regions.options.enableReportButton'));
    await celine.click(team.getByTestId('regions.options.saveReport'));
    await expect(team.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
    await checkpoint(team, 'report-button-off');
  });
  celine.finish({ steps: 5, clicks: 14, fields: 2 });

  // Sarah and Jonas share no other region with the button on: she can only write to him now
  await page.goto(`/profile/${JONAS}`);
  await expectDataState(page, 'profiles.page.state', 'success');
  await expect(page.getByTestId('profiles.page.message')).toBeVisible();
  await expect(page.getByTestId('profiles.page.report')).toHaveCount(0);
});

test('a member asks the mediation team for help, the team opens the request @G2 @N3', async ({ page, as, apiOf }, testInfo) => {
  test.setTimeout(120_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'member');
  const mediation = page.getByTestId('reports.mediation.message');

  await walk.step('opens the mediation tab of Zürich', async () => {
    await page.goto(`/regions/${ZURICH}`);
    // members without a report function see mediation, not the reports
    await expect(page.getByTestId('regions.page.tab.reports')).toHaveCount(0);
    await expect(page.getByTestId('regions.page.tab.reportLog')).toHaveCount(0);
    await walk.click(page.getByTestId('regions.page.tab.mediation'));
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/mediation$`));
    await expectDataState(page, 'reports.mediation.state', 'success');
    await expect(page.locator(`[data-testid="reports.mediation.member"][data-user-id="${CELINE}"]`)).toBeVisible();
    await checkpoint(page, 'mediation');
  });

  await walk.step('asks for mediation with Jonas', async () => {
    // too short: nothing is sent
    await walk.fill(mediation, 'Hilfe');
    await walk.click(page.getByTestId('reports.mediation.send'));
    await expect(mediation).toHaveAttribute('aria-invalid', 'true');
    await walk.fill(page.getByTestId('reports.mediation.picker.search'), 'Weber');
    await walk.click(page.locator(`[data-testid="reports.mediation.picker.hit"][data-user-id="${JONAS}"]`).getByTestId('reports.mediation.picker.add'));
    await expect(page.getByTestId('reports.mediation.counterpart')).toHaveAttribute('data-user-id', JONAS);
    await checkpoint(page, 'mediation-counterpart');
    await walk.fill(mediation, 'Wir sind uns bei den Abholzeiten von S-01 uneinig und brauchen Hilfe.');
    await walk.click(page.getByTestId('reports.mediation.send'));
    await expect(page.getByTestId('reports.mediation.sent')).toBeVisible();
    await checkpoint(page, 'mediation-sent');
  });
  walk.finish({ steps: 2, clicks: 4, fields: 3 });

  // the team is rung with a link to the requester
  const team = await as('reports');
  await acceptPendingConsents(apiOf(team));
  const celine = new Walk(team, testInfo, 'mediation team');
  await celine.step('opens the request from the bell', async () => {
    await team.goto('/dashboard');
    expect(await openNewestBell(celine, apiOf(team), 'MEDIATION_REQUEST')).toBe(`/profile/${SARAH}`);
    await expectDataState(team, 'profiles.page.state', 'success');
    await checkpoint(team, 'requester-profile');
  });
  celine.finish({ steps: 1, clicks: 3, fields: 0 });
});
