import fs from 'node:fs';
import { expect, test } from '../../fixtures';
// @ts-expect-error plain ESM script without type declarations (also runnable as CLI)
import { THRESHOLDS, belowThreshold, defaultPages, runLighthouseMedian } from '../../scripts/lighthouse.mjs';

/**
 * D11.5.3: Lighthouse mobile ≥ 70 performance (decision 153) and ≥ 95 accessibility on `/` and `/onboarding` (newbie).
 * Runs only in the dedicated `lighthouse` Playwright project (CI job "Lighthouse", serial, own jar): the scores are
 * CPU-sensitive, so the pages are warmed up first and the median of three Lighthouse runs is compared.
 * The scores also go to the job log and the run summary.
 */
test.use({ seedReset: 'test' });

interface Score {
  path: string;
  performance: number;
  accessibility: number;
  runs: number[];
  fcpMs: number;
  lcpMs: number;
  tbtMs: number;
  requestsBeforeLcp: number;
  kbBeforeLcp: number;
}

test('D11.5.3 lighthouse thresholds @D11.5.3 @chromium-only', async ({ baseURL, page }) => {
  test.setTimeout(600_000);
  // warm-up navigation: lazy chunks, fonts and compressed assets are served once before measuring
  await page.goto('/');
  await page.goto('/onboarding');
  const results: Score[] = await runLighthouseMedian(baseURL!, await defaultPages(baseURL!), 3);
  test.info().annotations.push({ type: 'lighthouse', description: JSON.stringify(results) });
  const report = results.map((r) => `Lighthouse ${r.path}: performance ${r.performance} (runs ${r.runs.join(', ')}), accessibility ${r.accessibility}, FCP ${r.fcpMs} ms, LCP ${r.lcpMs} ms, TBT ${r.tbtMs} ms, ${r.requestsBeforeLcp} requests and ${r.kbBeforeLcp} KB before LCP`);
  console.log(report.join('\n'));
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, report.map((line) => `- ${line}\n`).join(''));
  expect(results.map((r) => r.path)).toEqual(['/', '/onboarding']);
  expect(belowThreshold(results), `thresholds ${JSON.stringify(THRESHOLDS)}: ${JSON.stringify(results)}`).toEqual([]);
});
