import { expect, test } from '../../fixtures';
// @ts-expect-error plain ESM script without type declarations (also runnable as CLI)
import { THRESHOLDS, belowThreshold, defaultPages, runLighthouseMedian } from '../../scripts/lighthouse.mjs';

/**
 * D11.5.3: Lighthouse mobile ≥ 90 performance and ≥ 95 accessibility on `/` and `/onboarding` (newbie).
 * Runs only in the dedicated `lighthouse` Playwright project (CI job "Lighthouse", serial, own jar): the scores are
 * CPU-sensitive, so the pages are warmed up first and the median of three Lighthouse runs is compared.
 */
test.use({ seedReset: 'test' });

interface Score {
  path: string;
  performance: number;
  accessibility: number;
}

test('D11.5.3 lighthouse thresholds @D11.5.3 @chromium-only', async ({ baseURL, page }) => {
  test.setTimeout(600_000);
  // warm-up navigation: lazy chunks, fonts and compressed assets are served once before measuring
  await page.goto('/');
  await page.goto('/onboarding');
  const results: Score[] = await runLighthouseMedian(baseURL!, await defaultPages(baseURL!), 3);
  test.info().annotations.push({ type: 'lighthouse', description: JSON.stringify(results) });
  expect(results.map((r) => r.path)).toEqual(['/', '/onboarding']);
  expect(belowThreshold(results), `thresholds ${JSON.stringify(THRESHOLDS)}: ${JSON.stringify(results)}`).toEqual([]);
});
