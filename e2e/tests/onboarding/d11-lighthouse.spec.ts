import { expect, test } from '../../fixtures';
// @ts-expect-error plain ESM script without type declarations (also runnable as CLI)
import { THRESHOLDS, belowThreshold, defaultPages, runLighthouse } from '../../scripts/lighthouse.mjs';

/** D11.5.3: Lighthouse mobile ≥ 90 performance and ≥ 95 accessibility on `/` and `/onboarding` (newbie). */
test.use({ seedReset: 'test' });

interface Score {
  path: string;
  performance: number;
  accessibility: number;
}

test('D11.5.3 lighthouse thresholds @D11.5.3 @chromium-only', async ({ baseURL }) => {
  test.setTimeout(240_000);
  const results: Score[] = await runLighthouse(baseURL!, await defaultPages(baseURL!));
  test.info().annotations.push({ type: 'lighthouse', description: JSON.stringify(results) });
  expect(results.map((r) => r.path)).toEqual(['/', '/onboarding']);
  expect(belowThreshold(results), `thresholds ${JSON.stringify(THRESHOLDS)}: ${JSON.stringify(results)}`).toEqual([]);
});
