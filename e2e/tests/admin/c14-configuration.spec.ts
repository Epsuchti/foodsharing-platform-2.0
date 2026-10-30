import type { Page } from '@playwright/test';
import { expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';

/**
 * C14 configuration (plan-m7 Q13, A53): ORGA edits typed configuration values on `/admin/configuration`; an
 * invalid value is refused (400 CONFIGURATION_VALUE_INVALID) and shown at its row; every change is recorded as
 * ADMIN_CONFIGURATION_CHANGED on the SYSTEM subject and listed in the history drawer. Donation settings are the
 * typed view on the `donation.*` keys.
 */
test.use({ seedReset: 'test', role: 'orga' });

const row = (page: Page, key: string) => page.locator(`[data-testid="adminSettings.configuration.row"][data-key="${key}"]`);

async function setValue(page: Page, key: string, value: string): Promise<void> {
  await row(page, key).getByTestId('adminSettings.configuration.input').fill(value);
  await row(page, key).getByTestId('adminSettings.configuration.save').click();
}

interface ConfigurationEntry {
  key: string;
  value: string;
}

test('C14 orga changes support email and petition values; validation error shown; history recorded @C14 @page', async ({ page, apiOf }) => {
  await page.goto('/admin/configuration');
  await expectDataState(page, 'adminSettings.configuration.state', 'success');
  await expect(row(page, 'support.email')).toHaveAttribute('data-type', 'EMAIL');
  await expect(row(page, 'petition.signatures')).toHaveAttribute('data-type', 'INTEGER');

  // invalid email: refused, shown at the row, the value stays unchanged on the server
  await setValue(page, 'support.email', 'keine-adresse');
  await expect(row(page, 'support.email').getByTestId('adminSettings.configuration.error')).toBeVisible();
  const api = apiOf(page);
  const before = await api.getJson<ConfigurationEntry[]>('/api/admin/configuration');
  expect(before.find((entry) => entry.key === 'support.email')?.value).not.toBe('keine-adresse');

  // valid values are saved row by row
  await setValue(page, 'support.email', 'hilfe@foodsharing.example');
  await expect(row(page, 'support.email').getByTestId('adminSettings.configuration.error')).toHaveCount(0);
  await expect(row(page, 'support.email').getByTestId('adminSettings.configuration.save').locator('button')).toBeDisabled();
  await setValue(page, 'petition.url', 'https://petition.example/foodsharing');
  await expect(row(page, 'petition.url').getByTestId('adminSettings.configuration.save').locator('button')).toBeDisabled();
  await setValue(page, 'petition.signatures', '12345');
  await expect(row(page, 'petition.signatures').getByTestId('adminSettings.configuration.save').locator('button')).toBeDisabled();

  const after = new Map((await api.getJson<ConfigurationEntry[]>('/api/admin/configuration')).map((entry) => [entry.key, entry.value]));
  expect(after.get('support.email')).toBe('hilfe@foodsharing.example');
  expect(after.get('petition.url')).toBe('https://petition.example/foodsharing');
  expect(after.get('petition.signatures')).toBe('12345');

  // the history drawer lists the three changes with their new values
  await page.getByTestId('adminSettings.configuration.historyButton').click();
  const history = page.getByTestId('adminSettings.configuration.history.list');
  await expect(history).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('adminSettings.configuration.history.entry')).toHaveCount(3);
  await expect(history).toContainText('hilfe@foodsharing.example');
  await expect(history).toContainText('12345');
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('adminSettings.configuration.historyDrawer')).toHaveCount(0);
});

test('C14 donation settings save the donation keys @C14 @C7', async ({ page, apiOf }) => {
  await page.goto('/admin/donations');
  await expectDataState(page, 'adminSettings.donations.state', 'success');
  await page.getByTestId('adminSettings.donations.modalHoursLoggedOut').fill('48');
  await page.getByTestId('adminSettings.donations.campaignPageSlugs').fill('donation-campaign-1, donation-campaign-2');
  await page.getByTestId('adminSettings.donations.save').click();
  await expect
    .poll(async () => (await apiOf(page).getJson<{ modalHoursLoggedOut: number }>('/api/admin/donations')).modalHoursLoggedOut)
    .toBe(48);
  const settings = await apiOf(page).getJson<{ campaignPageSlugs: string[] }>('/api/admin/donations');
  expect(settings.campaignPageSlugs).toEqual(['donation-campaign-1', 'donation-campaign-2']);
  const config = await apiOf(page).getJson<ConfigurationEntry[]>('/api/admin/configuration');
  expect(config.find((entry) => entry.key === 'donation.modal-hours-logged-out')?.value).toBe('48');
});

test('C14 configuration error state and retry @C14', async ({ page }) => {
  await injectApiError(page, { url: '/api/admin/configuration', method: 'GET', status: 500, times: 3 });
  await page.goto('/admin/configuration');
  const { retry } = await expectErrorState(page, 'adminSettings.configuration.state');
  await retry.click();
  await expectDataState(page, 'adminSettings.configuration.state', 'success');
});
