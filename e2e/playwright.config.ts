import { defineConfig, devices, type PlaywrightTestConfig, type Project } from '@playwright/test';

/**
 * E2E against the packaged jar (profile `e2e`: in-memory H2 + seed, fixed clock, captured mails).
 * global-setup.ts starts the jar (or reuses E2E_BASE_URL); the base URL reaches the tests through the `baseURL`
 * fixture override in fixtures/index.ts (see support/env.ts), not through `use.baseURL` here.
 *
 * Tags: every test carries the ledger ID(s) it proves (`@A2`, `@D4.3`), see docs/conventions.md A8 and
 * scripts/feature-coverage.mjs. Meta tags: `@page` (auto axe check), `@screenshot`, `@chromium-only`
 * (CDP features such as the virtual WebAuthn authenticator; filtered out of the other browser projects).
 */
const CI = !!process.env.CI;
const CHROMIUM_ONLY = /@chromium-only/;
const TESTS = 'tests/**/*.spec.ts';
const SCREENS = 'screenshots/**/*.screen.ts';
/**
 * Screenshot projects only exist when E2E_SCREENSHOTS=1 (set by scripts/screenshots.sh inside the official Playwright
 * image, and by the coverage script for listing). A plain `npx playwright test` on a developer machine therefore never
 * writes host-rendered baselines (plan-m1 risk 6).
 */
const SCREENSHOTS = process.env.E2E_SCREENSHOTS === '1';

const shared: PlaywrightTestConfig['use'] = {
  serviceWorkers: 'block',
  locale: 'de-CH',
  timezoneId: 'Europe/Zurich',
  testIdAttribute: 'data-testid',
  trace: CI ? 'retain-on-failure' : 'on-first-retry',
  screenshot: 'only-on-failure',
  video: 'retain-on-failure',
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
  outputDir: 'test-results/artifacts',
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  // One shared in-memory database per jar: tests run serially; CI parallelism comes from shards with their own jar.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  forbidOnly: CI,
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
    e2eProject('chromium', { ...devices['Desktop Chrome'] }),
    e2eProject('firefox', { ...devices['Desktop Firefox'] }, { grepInvert: CHROMIUM_ONLY }),
    e2eProject('webkit', { ...devices['Desktop Safari'] }, { grepInvert: CHROMIUM_ONLY }),
    e2eProject('mobile-chrome', { ...devices['Pixel 7'] }),
    e2eProject('mobile-safari', { ...devices['iPhone 14'] }, { grepInvert: CHROMIUM_ONLY }),
    ...(SCREENSHOTS ? screenshotProjects() : []),
  ],
});

function screenshotProjects(): Project[] {
  return [
    {
      name: 'screenshots-desktop',
      testMatch: SCREENS,
      dependencies: ['setup'],
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 }, reducedMotion: 'reduce' },
    },
    {
      name: 'screenshots-mobile',
      testMatch: SCREENS,
      dependencies: ['setup'],
      use: { ...devices['Pixel 7'], reducedMotion: 'reduce' },
    },
  ];
}
