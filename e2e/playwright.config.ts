import { defineConfig, devices, type PlaywrightTestConfig, type Project } from '@playwright/test';
import path from 'node:path';
import { RESULTS_DIR } from './support/env';

/**
 * E2E against the packaged jar (profile `e2e`: in-memory H2 + seed, fixed clock, captured mails).
 * global-setup.ts starts the jar (or reuses E2E_BASE_URL); the base URL reaches the tests through the `baseURL`
 * fixture override in fixtures/index.ts (see support/env.ts), not through `use.baseURL` here.
 *
 * Tags: every test carries the ledger ID(s) it proves (`@A2`, `@D4.3`), see docs/conventions.md A8 and
 * scripts/feature-coverage.mjs. Meta tags: `@page` (auto axe check), `@screenshot`, `@chromium-only`
 * (CDP features such as the virtual WebAuthn authenticator; filtered out of the other browser projects), `@infra`
 * (tests of the test infrastructure itself) and `@gallery` (component gallery screenshots).
 */
const CI = !!process.env.CI;
const CHROMIUM_ONLY = /@chromium-only/;
/** CPU-sensitive Lighthouse thresholds (D11.5.3): only in the dedicated `lighthouse` project / CI job. */
const LIGHTHOUSE = /@D11\.5\.3/;
const TESTS = 'tests/**/*.spec.ts';
const SCREENS = 'screenshots/**/*.screen.ts';
/**
 * Screenshot projects only exist when E2E_SCREENSHOTS=1 (set by scripts/screenshots.sh inside the official Playwright
 * image, and by the coverage script for listing). A plain `npx playwright test` on a developer machine therefore never
 * writes host-rendered baselines (plan-m1 risk 6).
 */
const SCREENSHOTS = process.env.E2E_SCREENSHOTS === '1';

/**
 * Windows worker crashes (exit code 3221226505 = 0xC0000409, a fast-fail/abort inside the node test worker, about once
 * per full mobile-chrome run): Playwright pipes the worker's stderr away, so the crash left no trace. Workers inherit
 * this environment, so a fatal error (V8 out of memory, a native abort) now writes a Node diagnostic report (JS and
 * native stacks, heap statistics, loaded native modules) to test-results/node-reports. Diagnostics only: nothing is
 * retried or hidden.
 */
if (!/--report-on-fatalerror/.test(process.env.NODE_OPTIONS ?? '')) {
  const reports = `${__dirname}/test-results/node-reports`.split('\\').join('/');
  process.env.NODE_OPTIONS = `${process.env.NODE_OPTIONS ?? ''} --report-on-fatalerror --report-uncaught-exception --report-directory="${reports}"`.trim();
}

const shared: PlaywrightTestConfig['use'] = {
  serviceWorkers: 'block',
  locale: 'de-CH',
  timezoneId: 'Europe/Zurich',
  testIdAttribute: 'data-testid',
  trace: CI ? 'retain-on-failure' : 'on-first-retry',
  screenshot: 'only-on-failure',
  // E2E_VIDEO=off skips recording a video of every test (it is only kept on failure); see decision 137.
  video: process.env.E2E_VIDEO === 'off' ? 'off' : 'retain-on-failure',
  actionTimeout: 10_000,
  navigationTimeout: 20_000,
};

const e2eProject = (name: string, use: Project['use'], extra: Partial<Project> = {}): Project => ({
  name,
  testMatch: TESTS,
  dependencies: ['setup'],
  use: { ...use },
  ...extra,
});

export default defineConfig({
  testDir: '.',
  // Per E2E_INSTANCE (sharded runs) a folder of its own, else test-results/artifacts.
  outputDir: path.join(RESULTS_DIR, 'artifacts'),
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  // One shared in-memory database per jar: tests run serially; parallelism comes from shards with their own jar
  // (CI matrix, locally scripts/run-sharded.mjs and screenshots.sh --shards, decision 137).
  workers: 1,
  fullyParallel: false,
  retries: 0,
  forbidOnly: CI,
  // CI never writes baselines: a missing or changed screenshot fails the run (review-m1 §1.1).
  // Locally Playwright's default ('missing') applies; deliberate updates go through screenshots.sh --update-snapshots.
  ...(CI ? { updateSnapshots: 'none' as const } : {}),
  timeout: 60_000,
  expect: {
    timeout: 10_000,
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.002,
      animations: 'disabled',
      caret: 'hide',
      scale: 'css',
    },
  },
  snapshotPathTemplate: '{testDir}/__screenshots__/{projectName}/{testFilePath}/{arg}{ext}',
  reporter: CI
    ? [
        ['blob'],
        ['github'],
        ['list'],
        ['json', { outputFile: 'test-results/results.json' }],
        ['html', { open: 'never' }],
      ]
    : [['list'], ['json', { outputFile: 'test-results/results.json' }], ['html', { open: 'never' }]],
  use: shared,
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts$/,
    },
    e2eProject('chromium', { ...devices['Desktop Chrome'] }, { grepInvert: LIGHTHOUSE }),
    e2eProject('firefox', { ...devices['Desktop Firefox'] }, { grepInvert: [CHROMIUM_ONLY, LIGHTHOUSE] }),
    e2eProject('webkit', { ...devices['Desktop Safari'] }, { grepInvert: [CHROMIUM_ONLY, LIGHTHOUSE] }),
    e2eProject('mobile-chrome', { ...devices['Pixel 7'] }, { grepInvert: LIGHTHOUSE }),
    e2eProject('lighthouse', { ...devices['Desktop Chrome'] }, { grep: LIGHTHOUSE }),
    e2eProject('mobile-safari', { ...devices['iPhone 14'] }, { grepInvert: [CHROMIUM_ONLY, LIGHTHOUSE] }),
    ...(SCREENSHOTS ? screenshotProjects() : []),
  ],
});

function screenshotProjects(): Project[] {
  return [
    {
      name: 'screenshots-desktop',
      testMatch: SCREENS,
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 800 },
        reducedMotion: 'reduce',
      },
    },
    {
      name: 'screenshots-mobile',
      testMatch: SCREENS,
      dependencies: ['setup'],
      use: { ...devices['Pixel 7'], reducedMotion: 'reduce' },
    },
  ];
}
