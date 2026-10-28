import type { Page } from '@playwright/test';
import { expect, expectErrorState, expectSkeleton, injectApiError, injectNetworkFailure, test } from '../../fixtures';
import { skipPasskeyNudge } from './_auth';

/**
 * Error states of the auth/account pages (plan §12.2/§18.3): every data call answers 500/403/404/409/429 or fails
 * without a response; the page shows the skeleton, then the matching error view, and recovers through "retry"
 * (or a reload for forbidden/not found, which have no retry).
 */

interface Target {
  /** Feature ID tag of the page. */
  tag: string;
  path: string;
  /** API path called by the DataState. */
  api: string;
  /** data-testid of the fs-data-state root. */
  testId: string;
}

const TARGETS: Target[] = [
  { tag: '@A22', path: '/account/sessions', api: '/api/auth/sessions', testId: 'account.sessions.list' },
  { tag: '@A22', path: '/account/security-log', api: '/api/auth/security-log', testId: 'account.securityLog.list' },
  { tag: '@A2', path: '/account/settings', api: '/api/auth/methods', testId: 'account.settings.methods' },
  { tag: '@A2', path: '/account/profile', api: '/api/users/me', testId: 'account.profile.state' },
];

const STATUSES = [
  { status: 500, view: 'error' },
  { status: 403, view: 'forbidden' },
  { status: 404, view: 'notFound' },
  { status: 409, view: 'error' },
  { status: 429, view: 'error' },
] as const;

async function expectRecovery(page: Page, target: Target, viaRetry: boolean): Promise<void> {
  const root = page.getByTestId(target.testId);
  if (viaRetry) {
    await root.locator('[data-testid$=".retry"]').first().click();
  } else {
    await page.reload();
  }
  await expect(root).toHaveAttribute('data-state', /success|empty/);
}

test.describe('logged in', () => {
  test.use({ role: 'foodsaver' });

  test.beforeEach(async ({ page }) => {
    await skipPasskeyNudge(page);
  });

  for (const target of TARGETS) {
    test(`${target.tag.slice(1)} ${target.path} shows skeleton, errors and recovers ${target.tag}`, async ({ page }) => {
      for (const { status, view } of STATUSES) {
        const injection = await injectApiError(page, { url: target.api, status, delayMs: 400 });
        await page.goto(target.path);
        await expectSkeleton(page, target.testId);
        await expectErrorState(page, target.testId, { status: view, retry: view === 'error' });
        await injection.remove();
        await expectRecovery(page, target, view === 'error');
      }

      const offline = await injectNetworkFailure(page, { url: target.api });
      await page.goto(target.path);
      await expectErrorState(page, target.testId, { status: 'offline' });
      await offline.remove();
      await expectRecovery(page, target, true);
    });
  }

  test('D4.3 dashboard still works when app info cannot be loaded @D4.3', async ({ page }) => {
    await injectApiError(page, { url: '/api/public/app-info', status: 500 });
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await expect(page.getByTestId('dashboard.page.nextStepAction')).toBeVisible();
  });

  test('A2 session bootstrap failure falls back to the login and recovers on reload @A2', async ({ page }) => {
    const injection = await injectApiError(page, { url: '/api/auth/session', status: 500 });
    await page.goto('/account/sessions');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Faccount%2Fsessions$/);
    await injection.remove();
    await page.reload();
    await expect(page).toHaveURL(/\/account\/sessions$/);
    await expect(page.getByTestId('account.sessions.list')).toHaveAttribute('data-state', 'success');
  });
});

test('A2 login page still offers every login method when app info cannot be loaded @A2', async ({ page }) => {
  const inject = [
    () => injectApiError(page, { url: '/api/public/app-info', status: 500 }),
    () => injectNetworkFailure(page, { url: '/api/public/app-info' }),
  ];
  for (const start of inject) {
    const injection = await start();
    await page.goto('/login');
    await expect(page.getByTestId('auth.login.email')).toBeVisible();
    await expect(page.getByTestId('auth.login.google')).toBeVisible();
    await injection.remove();
  }
});

test('D7.3 dev user list shows skeleton, errors and recovers @D7.3', async ({ page }) => {
  for (const { status, view } of STATUSES) {
    const injection = await injectApiError(page, { url: '/api/dev/users', status, delayMs: 400 });
    await page.goto('/login');
    await expectSkeleton(page, 'auth.login.devSwitcher');
    await expectErrorState(page, 'auth.login.devSwitcher', { status: view, retry: view === 'error' });
    await injection.remove();
    await expectRecovery(
      page,
      { tag: '@D7.3', path: '/login', api: '/api/dev/users', testId: 'auth.login.devSwitcher' },
      view === 'error',
    );
  }
});
