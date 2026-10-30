import fs from 'node:fs';
import path from 'node:path';
import type { Page } from '@playwright/test';
import { SEED_NOW, expect, expectDataState, expectErrorState, injectApiError, pickOption, test, type ApiClient } from '../../fixtures';
import { acceptConfirm } from '../auth/_auth';

/**
 * C10 uploads administration (plan-m7 Q13, A34/A53): ORGA sees the storage usage per module and every upload on
 * `/admin/uploads`, filters the unused (unclaimed) ones, previews images through the `sm` variant and deletes an
 * upload. Unclaimed uploads expire after 24 h and the `cleanup` job removes them.
 */
test.use({ seedReset: 'test', role: 'orga' });

const PNG = fs.readFileSync(path.join(__dirname, '..', '..', 'screenshots', 'fixtures', 'tile.png'));

async function upload(api: ApiClient, name: string): Promise<string> {
  const response = await api.fetch('POST', '/api/uploads', undefined, {
    multipart: { file: { name, mimeType: 'image/png', buffer: PNG } },
  });
  expect(response.status(), await response.text()).toBe(201);
  return ((await response.json()) as { id: string }).id;
}

const uploadRow = (page: Page, id: string) => page.locator(`[data-testid="adminSettings.uploads.row"][data-id="${id}"]`);

test('C10 uploads admin lists usage, deletes unclaimed upload, cleanup job removes expired @C10', async ({ page, as, apiOf, clock, notifications }) => {
  const member = apiOf(await as('foodsaver'));
  const flyer = await upload(member, 'flyer-c10.png');

  // usage: the seed avatars count as profile photos; the new upload is unused
  await page.goto('/admin/uploads');
  await expectDataState(page, 'adminSettings.uploads.state', 'success');
  await expect(page.locator('[data-testid="adminSettings.uploads.module"][data-module="USER_PHOTO"]')).toBeVisible();
  await expect(page.getByTestId('adminSettings.uploads.unclaimedCount')).toHaveText('1');

  await pickOption(page, 'adminSettings.uploads.filterSelect', 'UNCLAIMED');
  const row = uploadRow(page, flyer);
  await expect(row).toBeVisible();
  await expect(row).toContainText('flyer-c10.png');
  await expect(row.getByTestId('adminSettings.uploads.unclaimedTag')).toBeVisible();
  const preview = row.locator('img');
  await expect(preview).toHaveAttribute('src', `/api/uploads/${flyer}?size=sm`);
  await expect.poll(() => preview.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
  await expect(page.locator('[data-testid="adminSettings.uploads.row"]:not([data-module="UNCLAIMED"])')).toHaveCount(0);

  // delete: the row and the bytes are gone
  await row.getByTestId('adminSettings.uploads.delete').click();
  await acceptConfirm(page);
  await expect(row).toHaveCount(0);
  await expectDataState(page, 'adminSettings.uploads.state', 'empty');
  await expect(page.getByTestId('adminSettings.uploads.unclaimedCount')).toHaveText('0');
  expect((await member.get(`/api/uploads/${flyer}`)).status()).toBe(404);

  // an upload nobody claims expires after a day; the nightly cleanup removes it
  const forgotten = await upload(member, 'forgotten-c10.png');
  await page.reload();
  await pickOption(page, 'adminSettings.uploads.filterSelect', 'UNCLAIMED');
  await expect(uploadRow(page, forgotten)).toBeVisible();
  await clock.fixServer(new Date(new Date(SEED_NOW).getTime() + 2 * 24 * 3600_000).toISOString());
  await notifications.runJob('cleanup');
  await page.reload();
  await expectDataState(page, 'adminSettings.uploads.state', 'success');
  await pickOption(page, 'adminSettings.uploads.filterSelect', 'UNCLAIMED');
  await expectDataState(page, 'adminSettings.uploads.state', 'empty');
  const unclaimed = await apiOf(page).getJson<{ items: { id: string }[] }>('/api/admin/uploads?unclaimed=true');
  expect(unclaimed.items.map((item) => item.id)).not.toContain(forgotten);
});

test('C10 uploads list error state and retry @C10', async ({ page }) => {
  await injectApiError(page, { url: '/api/admin/uploads', method: 'GET', status: 500, times: 3 });
  await page.goto('/admin/uploads');
  const { retry } = await expectErrorState(page, 'adminSettings.uploads.state');
  await retry.click();
  await expectDataState(page, 'adminSettings.uploads.state', 'success');
});
