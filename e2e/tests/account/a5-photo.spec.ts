import type { Page } from '@playwright/test';
import { expect, expectErrorState, injectApiError, test } from '../../fixtures';

/**
 * A5 profile photo (plan-m7 Q3, A41/A48): `fs-image-crop` in the settings → square JPEG ≤ 1 MB → `createUpload` →
 * `setMyPhoto`; the server renders sm/md/lg variants, the photo is public (USER_PHOTO) and shows on the profile;
 * "Remove" deletes it.
 */
test.use({ seedReset: 'test', role: 'foodsaver' });

/** A 900×600 PNG drawn in the page (non-square, so the crop matters). */
async function picture(page: Page): Promise<Buffer> {
  const dataUrl = await page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 900;
    canvas.height = 600;
    const context = canvas.getContext('2d')!;
    const gradient = context.createLinearGradient(0, 0, 900, 600);
    gradient.addColorStop(0, '#25834a');
    gradient.addColorStop(1, '#f6d79b');
    context.fillStyle = gradient;
    context.fillRect(0, 0, 900, 600);
    context.fillStyle = '#1a201a';
    context.beginPath();
    context.arc(450, 300, 180, 0, Math.PI * 2);
    context.fill();
    return canvas.toDataURL('image/png');
  });
  return Buffer.from(dataUrl.split(',')[1], 'base64');
}

async function choosePicture(page: Page): Promise<void> {
  await page.getByTestId('account.settings.photo.change').click();
  await expect(page.getByTestId('account.settings.photo.crop.pick')).toBeVisible();
  await page.getByTestId('account.settings.photo.crop.input').setInputFiles({ name: 'ich.png', mimeType: 'image/png', buffer: await picture(page) });
  await expect(page.getByTestId('account.settings.photo.crop.stage')).toBeVisible();
}

test('A5 upload and crop a profile photo, variants served, remove @A5 @C10 @page', async ({ page, apiOf }) => {
  await page.goto('/account/settings');
  // seed: foodsaver already has a photo upload; it is replaced
  const avatar = page.getByTestId('account.settings.photo.avatar');
  await expect(avatar).toHaveAttribute('data-has-photo', 'true');
  const before = (await apiOf(page).getJson<{ avatarUrl?: string }>('/api/users/me')).avatarUrl;

  await choosePicture(page);
  await page.getByTestId('account.settings.photo.crop.zoom').fill('1.5');
  await page.getByTestId('account.settings.photo.crop.apply').click();
  await expect(page.getByTestId('account.settings.photo.dialog')).toBeHidden();
  await expect.poll(async () => (await apiOf(page).getJson<{ avatarUrl?: string }>('/api/users/me')).avatarUrl).not.toBe(before);
  const avatarUrl = (await apiOf(page).getJson<{ avatarUrl: string }>('/api/users/me')).avatarUrl;
  const id = /^\/api\/uploads\/([0-9a-f-]{36})\?size=md$/.exec(avatarUrl)?.[1];
  expect(id, avatarUrl).toBeTruthy();
  const uploaded = { id: id! };
  await expect(avatar.locator('img')).toHaveAttribute('src', avatarUrl);

  // the cropped original is a JPEG of at most 1 MB
  const original = await page.request.get(`/api/uploads/${uploaded.id}`);
  expect(original.status()).toBe(200);
  expect(original.headers()['content-type']).toContain('image/jpeg');
  expect((await original.body()).length).toBeLessThanOrEqual(1024 * 1024);

  // variants: JPEGs, readable without login (public USER_PHOTO)
  for (const size of ['sm', 'md', 'lg']) {
    const response = await page.request.get(`/api/uploads/${uploaded.id}?size=${size}`);
    expect(response.status(), size).toBe(200);
    expect(response.headers()['content-type']).toContain('image/jpeg');
  }
  const anonymous = await page.context().browser()!.newContext();
  try {
    const response = await anonymous.request.get(new URL(`/api/uploads/${uploaded.id}?size=sm`, page.url()).href);
    expect(response.status()).toBe(200);
  } finally {
    await anonymous.close();
  }

  // the header avatar and the public profile show the new photo
  await expect(page.locator('fs-user-menu img').first()).toHaveAttribute('src', avatarUrl);
  await page.goto('/profile/me');
  await expect(page.locator(`main img[src*="/api/uploads/${uploaded.id}"]`).first()).toBeVisible();

  // remove after confirmation
  await page.goto('/account/settings');
  await page.getByTestId('account.settings.photo.remove').click();
  await page.locator('.p-confirmdialog-accept-button').click();
  await expect(page.locator('.p-confirmdialog')).toHaveCount(0);
  await expect(avatar).toHaveAttribute('data-has-photo', 'false');
  await expect(page.getByTestId('account.settings.photo.remove')).toHaveCount(0);
});

test('A5 a failed upload keeps the dialog open; settings load error shows retry @A5', async ({ page }) => {
  await page.goto('/account/settings');
  await expect(page.getByTestId('account.settings.photo.avatar')).toBeVisible();
  const photoRequests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/users/me/photo')) photoRequests.push(request.method());
  });
  await choosePicture(page);
  await injectApiError(page, { url: '/api/uploads', method: 'POST', status: 413, code: 'UPLOAD_TOO_LARGE', times: 1 });
  await page.getByTestId('account.settings.photo.crop.apply').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message-error')).toBeVisible();
  await expect(page.getByTestId('account.settings.photo.crop.stage')).toBeVisible();
  expect(photoRequests).toEqual([]);

  const failing = await injectApiError(page, { url: '/api/users/me', method: 'GET', status: 500 });
  await page.reload();
  const { retry } = await expectErrorState(page, 'account.settings.photo.state');
  await failing.remove();
  await retry.click();
  await expect(page.getByTestId('account.settings.photo.avatar')).toBeVisible();
});
