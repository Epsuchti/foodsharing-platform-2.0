import { defineConfig, devices, type PlaywrightTestConfig, type Project } from '@playwright/test';
import path from 'node:path';
import { RESULTS_DIR, WORKERS, baseURLsFromEnv } from './support/env';

/**
 * E2E against the packaged jar (profile `e2e`: in-memory H2 + seed, fixed clock, captured mails).
 * global-setup.ts starts the jar (or reuses E2E_BASE_URL); the base URL reaches the tests through the `baseURL`
 * fixture override in fixtures/index.ts (see support/env.ts), not through `use.baseURL` here.
 *
 * Journeys (decision 180): `journeys/<persona>/<slug>.journey.ts` walk a persona through a real process and call
 * `checkpoint()` (fixtures/checkpoint.ts) at the states worth looking at. `chromium` (1280x800) and `mobile-chrome`
 * are the checkpoint projects (axe at every checkpoint; screenshots inside the Playwright image, scripts/screenshots.sh);
 * `firefox`, `webkit` and `mobile-safari` only wait for stability there. The axe route sweep (light + dark) is its
 * own project `a11y` (chromium). `E2E_WORKERS` (default 2) workers run files in parallel, each against a jar of its
 * own (global-setup.ts), and every journey starts from the seed.
 *
 * Tags: every journey carries the ledger ID(s) it proves (`@A2`, `@D4.3`) in its title; IDs proven below e2e are listed
 * in coverage-map.json (docs/conventions.md A8, scripts/feature-coverage.mjs). Meta tags: `@chromium-only`
 * (CDP features such as the virtual WebAuthn authenticator; filtered out of the other browser projects), `@infra`
 * (tests of the test infrastructure itself) and `@gallery` (component gallery screenshots).
 */
const CI = !!process.env.CI;
const CHROMIUM_ONLY = /@chromium-only/;
/** CPU-sensitive Lighthouse thresholds (D11.5.3): only in the dedicated `lighthouse` project / CI job. */
const LIGHTHOUSE = /@D11\.5\.3/;
const LIGHTHOUSE_SPECS = 'lighthouse/**/*.spec.ts';
const JOURNEYS = 'journeys/**/*.journey.ts';
/** The axe route sweep and the CSP check: project `a11y` only. */
const A11Y = 'tests/a11y/**/*.spec.ts';
/** One worker per running app: `E2E_BASE_URL` with several URLs, else the jars global setup starts. */
const WORKER_COUNT = baseURLsFromEnv().length || WORKERS;

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
  testMatch: JOURNEYS,
  dependencies: ['setup'],
  use: { ...use },
  ...extra,
});

/** Checkpoint projects: axe at every checkpoint, screenshots inside the Playwright image. */
const CHECKPOINTS = { metadata: { checkpoints: true } } satisfies Partial<Project>;

export default defineConfig({
  testDir: '.',
  // Per E2E_INSTANCE (sharded runs) a folder of its own, else test-results/artifacts.
  outputDir: path.join(RESULTS_DIR, 'artifacts'),
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  // One in-memory database per jar and one jar per worker (decision 180): files run in parallel, the tests of a file
  // in order on their worker's jar.
  workers: WORKER_COUNT,
  fullyParallel: false,
  // CI runs a failed test up to 3 more times: a pass on a retry counts as flaky, not as failed, and stays listed in the
  // reports. Locally a failure stays a failure.
  retries: CI ? 3 : 0,
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
    e2eProject('chromium', { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } }, { grepInvert: LIGHTHOUSE, ...CHECKPOINTS }),
    e2eProject('firefox', { ...devices['Desktop Firefox'] }, { grepInvert: [CHROMIUM_ONLY, LIGHTHOUSE] }),
    e2eProject('webkit', { ...devices['Desktop Safari'] }, { grepInvert: [CHROMIUM_ONLY, LIGHTHOUSE] }),
    e2eProject('mobile-chrome', { ...devices['Pixel 7'] }, { grepInvert: LIGHTHOUSE, ...CHECKPOINTS }),
    {
      name: 'lighthouse',
      testMatch: LIGHTHOUSE_SPECS,
      dependencies: ['setup'],
      use: { ...devices['Desktop Chrome'] },
    },
    e2eProject('mobile-safari', { ...devices['iPhone 14'] }, { grepInvert: [CHROMIUM_ONLY, LIGHTHOUSE] }),
    {
      name: 'a11y',
      testMatch: A11Y,
      dependencies: ['setup'],
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
  ],
});
