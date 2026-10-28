import { expect, test } from '../../fixtures';

/** D6.3: every scheduled job of §6.3 is listed for ORGA and runs on demand. */
test.use({ seedReset: 'test' });

const SECTION_6_3_JOBS = [
  'mail-outbox',
  'open-slot-adverts',
  'signout-alerts',
  'pickup-reminder',
  'onboarding-nudges',
  'region-maintenance',
  'store-maintenance',
  'region-statistics',
  'audit-retention',
  'hygiene-expiry',
  'cleanup',
  'history-retention',
  'automation-evaluate',
  'request-automation',
  'newsletter-send',
  'telegram-poll',
];

test('D6.3 every §6.3 job is listed and runs on demand @D6.3 @page', async ({ as }) => {
  const page = await as('orga');
  await page.goto('/admin/jobs');
  await expect(page.getByTestId('adminOps.jobs.state')).toHaveAttribute('data-state', 'success');
  const listed = await page.getByTestId('adminOps.jobs.row').evaluateAll((rows) => rows.map((row) => row.getAttribute('data-job')));
  for (const job of SECTION_6_3_JOBS) {
    expect(listed, job).toContain(job);
  }

  const row = page.locator('[data-testid="adminOps.jobs.row"][data-job="onboarding-nudges"]');
  await row.getByTestId('adminOps.jobs.run').click();
  await expect(row).toHaveAttribute('data-outcome', 'OK');
  await expect(row).toContainText(/von Hand|vo Hand|manual|manuelle|manuale/);
});
