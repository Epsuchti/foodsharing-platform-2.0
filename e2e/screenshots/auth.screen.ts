import { injectApiError } from '../fixtures';
import type { Role } from '../fixtures';
import { forEachVariant, screenshotTest as test, type Variant } from './_setup';

/**
 * Screenshots of the auth and account pages (plan §18.4): login (with dev switcher, Google error), magic-link sent,
 * verify error, settings, sessions, security log, profile — per variant (light/dark × de/fr) on the desktop and
 * mobile screenshot projects, plus loading / empty / error states via injected API responses.
 *
 * Logged-in pages use a seed user whose account language matches the variant (the account language wins over the
 * device language): foodsaver@local (de) and guide@local (fr), both with theme SYSTEM so the emulated colour scheme
 * decides.
 */
const USER_FOR: Record<string, Role> = { de: 'foodsaver', fr: 'guide' };

function userFor(variant: Variant): Role {
  return USER_FOR[variant.lang] ?? 'foodsaver';
}

forEachVariant((variant) => {
  test.describe(`logged out ${variant.name}`, () => {
    test.use({ variant, seedReset: 'off' });

    test(`login page @A1 @A2 @D7.3 @screenshot`, async ({ page, visual }) => {
      await page.goto('/login');
      await page.getByTestId('auth.login.devSwitcher').waitFor();
      await visual.expectScreen(page, `login-${variant.name}`);
      await visual.expectElement(page.getByTestId('auth.login.devSwitcher'), `login-dev-switcher-${variant.name}`);
    });

    test(`login with Google error @A1 @screenshot`, async ({ page, visual }) => {
      await page.goto('/login?error=GOOGLE_EMAIL_NOT_VERIFIED');
      await page.getByTestId('auth.login.error').waitFor();
      await visual.expectScreen(page, `login-google-error-${variant.name}`, { fullPage: false });
    });

    test(`magic link sent @A2 @screenshot`, async ({ page, visual }) => {
      await page.goto('/login/sent?email=anna.beispiel%40example.org');
      await page.getByTestId('auth.sent.page').waitFor();
      await visual.expectScreen(page, `magic-link-sent-${variant.name}`);
    });

    test(`magic link verify error @A2 @screenshot`, async ({ page, visual }) => {
      await page.goto('/login/verify?token=expired-or-used');
      await page.getByTestId('auth.verify.tokenError').waitFor();
      await visual.expectScreen(page, `magic-link-verify-error-${variant.name}`);
    });

    test(`magic link verify in progress @A2 @screenshot`, async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/auth/magic-link/verify', delayMs: 60_000 });
      await page.goto('/login/verify?token=pending');
      await page.locator('[data-testid="auth.verify.state"][data-state="loading"]').waitFor();
      await visual.expectScreen(page, `magic-link-verify-loading-${variant.name}`, { waitForData: false });
    });
  });

  test.describe(`logged in ${variant.name}`, () => {
    test.use({ variant, role: userFor(variant) });

    test(`settings @A12 @A17 @screenshot`, async ({ page, visual }) => {
      await page.goto('/account/settings');
      await page.locator('[data-testid="account.settings.methods"][data-state="success"]').waitFor();
      await visual.expectScreen(page, `settings-${variant.name}`);
    });

    test(`passkey nudge after login @A2 @screenshot`, async ({ page, visual }) => {
      // what the user sees right after an email-link login on a device without a passkey
      await page.addInitScript(() => {
        try {
          window.sessionStorage.setItem('fs.loginPending', 'EMAIL');
          window.localStorage.removeItem('fs.passkeyNudge');
        } catch {
          /* storage disabled */
        }
      });
      await page.goto('/dashboard');
      await page.getByTestId('auth.passkeyNudge').waitFor();
      await visual.expectScreen(page, `passkey-nudge-${variant.name}`, { fullPage: false });
    });

    test(`sessions @A22 @screenshot`, async ({ page, visual }) => {
      await page.goto('/account/sessions');
      await page.locator('[data-testid="account.sessions.list"][data-state="success"]').waitFor();
      await visual.expectScreen(page, `sessions-${variant.name}`);
    });

    test(`security log @A22 @screenshot`, async ({ page, visual }) => {
      await page.goto('/account/security-log');
      await page.locator('[data-testid="account.securityLog.list"][data-state="success"]').waitFor();
      await visual.expectScreen(page, `security-log-${variant.name}`);
    });

    test(`profile @A2 @screenshot`, async ({ page, visual }) => {
      await page.goto('/account/profile');
      await page.locator('[data-testid="account.profile.state"][data-state="success"]').waitFor();
      await visual.expectScreen(page, `profile-${variant.name}`);
    });

    test(`passkey dialog @A2 @screenshot`, async ({ page, visual }) => {
      await page.goto('/account/settings?addPasskey=1');
      await page.locator('[data-testid="account.settings.methods"][data-state="success"]').waitFor();
      // the dialog only opens where the browser supports WebAuthn (Chromium in the screenshot image does)
      const label = page.getByTestId('account.settings.passkeyDialog.label');
      await label.waitFor();
      await label.fill('Laptop');
      await visual.expectScreen(page, `passkey-dialog-${variant.name}`, { fullPage: false });
    });

    test(`sessions loading, error and empty states @A22 @screenshot`, async ({ page, visual }) => {
      const loading = await injectApiError(page, { url: '/api/auth/sessions', delayMs: 60_000 });
      await page.goto('/account/sessions');
      await page.locator('[data-testid="account.sessions.list"][data-state="loading"]').waitFor();
      await visual.expectScreen(page, `sessions-loading-${variant.name}`, { waitForData: false });
      await loading.remove();

      await injectApiError(page, { url: '/api/auth/sessions', status: 500 });
      await page.goto('/account/sessions');
      await page.locator('[data-testid="account.sessions.list"][data-state="error"]').waitFor();
      await visual.expectScreen(page, `sessions-error-${variant.name}`);
    });

    test(`security log empty and forbidden states @A22 @screenshot`, async ({ page, visual }) => {
      const empty = await injectApiError(page, {
        url: '/api/auth/security-log',
        status: 200,
        body: { items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } },
      });
      await page.goto('/account/security-log');
      await page.locator('[data-testid="account.securityLog.list"][data-state="empty"]').waitFor();
      await visual.expectScreen(page, `security-log-empty-${variant.name}`);
      await empty.remove();

      await injectApiError(page, { url: '/api/auth/methods', status: 403 });
      await page.goto('/account/settings');
      await page.locator('[data-testid="account.settings.methods"][data-state="forbidden"]').waitFor();
      await visual.expectScreen(page, `settings-methods-forbidden-${variant.name}`);
    });
  });
});
