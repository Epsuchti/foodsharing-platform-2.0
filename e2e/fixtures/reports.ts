import { expect, type Page } from '@playwright/test';

/**
 * Report seed (M6 B8 / plan-m7 Q8, docs/seed-ids.md `167-report-seed.yaml`): seven reports in zuerich-stadt about
 * jumper and sleepy (`01`, `02` TO_DO about store S-01; `03`, `04` IN_PROGRESS with wall posts by reports@local;
 * `05` MEDIATION; `06` COMPLETED; `07` TO_DO with a reminder 2026-11-03 08:00Z), one mediation request. reports@local
 * administers the REPORT group (handler) and the MEDIATION group of Zürich; Zürich has the report and mediation
 * buttons on (the region option default is off).
 */
export const REPORTS = {
  noShowS01: '00000050-0001-4000-8000-000000000001',
  secondS01: '00000050-0001-4000-8000-000000000002',
  completed: '00000050-0001-4000-8000-000000000006',
  withReminder: '00000050-0001-4000-8000-000000000007',
} as const;

/** Opens a p-select and picks the option whose item template carries `data-value` (language independent). */
export async function pickOption(page: Page, selectTestId: string, value: string): Promise<void> {
  await page.getByTestId(selectTestId).click();
  const option = page.locator(`.p-select-overlay [data-value="${value}"]`);
  await expect(option).toBeVisible();
  await option.click();
  await expect(page.locator('.p-select-overlay')).toHaveCount(0);
}
