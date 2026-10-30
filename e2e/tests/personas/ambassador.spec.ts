import { REPORTS, acceptPendingConsents, expect, expectDataState, regionId, test } from '../../fixtures';
import { USERS, queueCard } from '../verification/_verification';
import { Walk } from './_persona';

/**
 * D11.5.5 persona walkthrough "ambassador" (decision 3, design review M7 round 3): Reto (bot@local, ambassador of
 * Zürich Stadt) verifies a newcomer from the queue, works on an open report of his region and writes a region
 * newsletter that he queues for sending. Runs on desktop and on the phone project.
 */
test.use({ seedReset: 'test', role: 'bot' });

const ZURICH = regionId('zuerich-stadt');

test('D11.5.5 ambassador: verify a newcomer, handle a report, send a newsletter @D11.5.5 @Ve2 @G1 @N7 @page', async ({ page, api }, testInfo) => {
  test.setTimeout(120_000);
  await acceptPendingConsents(api);
  const walk = new Walk(page, testInfo, 'ambassador');

  await walk.step('opens the verification queue', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('verification');
    await expect(page).toHaveURL(/\/verification$/);
    await expect(page.getByTestId('verification.queue.state')).toHaveAttribute('data-state', 'success');
  });

  await walk.step('checks the identity and verifies Amélie', async () => {
    const card = queueCard(page, USERS.pending);
    await expect(card).toHaveAttribute('data-verified', 'false');
    const identity = card.getByTestId('verification.queue.confirmIdentity');
    if (await identity.isVisible()) {
      await walk.click(identity);
      await expect(card.getByTestId('verification.queue.identityTag')).toBeVisible();
    }
    await walk.click(card.getByTestId('verification.queue.verify'));
    await walk.fill(page.getByTestId('verification.verifyDialog.message'), 'Willkommen im Team, Ausweis geprüft!');
    await walk.click(page.getByTestId('verification.verifyDialog.confirm'));
    await expect(card).toHaveAttribute('data-verified', 'true');
    await expect(card.getByTestId('verification.queue.verifiedTag')).toBeVisible();
  });

  await walk.step('takes on the open no-show report', async () => {
    await page.goto(`/regions/${ZURICH}`);
    await walk.click(page.getByTestId('regions.page.tab.reports'));
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/reports$`));
    await expectDataState(page, 'reports.region.state', 'success');
    const item = page.locator(`[data-testid="reports.item"][data-report-id="${REPORTS.noShowS01}"]`);
    await walk.click(item.getByTestId('reports.item.open'));
    await expectDataState(page, 'reports.detail.state', 'success');
    await walk.click(page.getByTestId('reports.detail.statusSelect'));
    await walk.click(page.locator('.p-select-overlay [data-value="IN_PROGRESS"]'));
    await walk.fill(page.getByTestId('reports.detail.consequence'), 'Gespräch vereinbart, Rückmeldung bis Freitag.');
    await walk.click(page.getByTestId('reports.detail.save'));
    await expect(page.getByTestId('reports.detail.status')).toHaveAttribute('data-status', 'IN_PROGRESS');
  });

  await walk.step('writes a region newsletter and queues it', async () => {
    await walk.nav('newsletters');
    await expect(page.getByTestId('newsletters.list.state')).toHaveAttribute('data-state', /success|empty/);
    await walk.click(page.getByTestId('newsletters.list.create'));
    await expect(page).toHaveURL(/\/newsletters\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('newsletters.editor.state')).toHaveAttribute('data-state', 'success');
    const subject = `Zürich: Neues aus dem Bezirk ${Date.now()}`;
    await walk.fill(page.getByTestId('newsletters.editor.subject'), subject);
    await walk.fill(page.getByTestId('newsletters.editor.body'), 'Hallo zusammen,\n\nam Samstag ist **Bezirkstreffen**.');
    await expect(page.getByTestId('newsletters.editor.preview')).toContainText(subject);
    await walk.click(page.getByTestId('newsletters.editor.save'));
    await expect(page.getByTestId('newsletters.editor.save').locator('button')).toBeDisabled();
    const id = page.url().split('/').pop()!;
    expect((await api.getJson<{ region?: { id: string } }>(`/api/newsletters/${id}`)).region?.id).toBe(ZURICH);
    const count = Number(await page.getByTestId('newsletters.editor.recipients').getAttribute('data-count'));
    expect(count).toBeGreaterThan(0);
    await walk.click(page.getByTestId('newsletters.editor.queue'));
    await expect(page.locator('.p-confirmdialog')).toContainText(String(count));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    // Reto's account speaks Swiss German (gws): assert the status, not its text
    await expect(page.getByTestId('newsletters.editor.status')).toHaveAttribute('class', /badge--(QUEUED|SENDING|SENT)/);
  });

  // round 3: 15 clicks on desktop ("More" for verification and newsletters), 16 on the phone
  walk.finish({ steps: 4, clicks: 16, fields: 4 });
});

