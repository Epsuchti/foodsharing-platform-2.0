import { expect, test } from '../../fixtures';

/** PWA (plan §12.1): the packaged jar serves the web manifest and the Angular service worker config. */

test.use({ seedReset: 'off' });

test('AU12 jar serves the web manifest and the service worker configuration @AU12', async ({ api, page }) => {
  const manifest = await api.get('/manifest.webmanifest');
  expect(manifest.status()).toBe(200);
  const app = (await manifest.json()) as { name?: string; short_name?: string; icons?: unknown[]; start_url?: string };
  expect(app.name).toBeTruthy();
  expect(app.icons?.length).toBeGreaterThan(0);

  const ngsw = await api.get('/ngsw.json');
  expect(ngsw.status()).toBe(200);
  expect(ngsw.headers()['content-type']).toContain('json');
  const config = (await ngsw.json()) as { configVersion?: number; assetGroups?: unknown[] };
  expect(config.configVersion).toBe(1);
  expect(config.assetGroups?.length).toBeGreaterThan(0);

  const worker = await api.get('/ngsw-worker.js');
  expect(worker.status()).toBe(200);

  // the index page links the manifest
  await page.goto('/');
  await expect(page.locator('link[rel="manifest"]')).toHaveAttribute('href', /manifest\.webmanifest$/);
});
