import { randomInt } from 'node:crypto';
import { expect, roleEmail, type ApiClient, type Role } from '../../fixtures';
import type { Page } from '@playwright/test';

/** Mirrors of the contract models used by the auth/account specs (openapi/schemas/{auth,history}.yaml). */
export interface HistoryEntry {
  id: string;
  occurredAt: string;
  actorType: string;
  subjectType: string;
  subjectId: string;
  action: string;
  metadata?: Record<string, unknown>;
}

export interface HistoryEntryPage {
  items: HistoryEntry[];
  page: { page: number; size: number; totalItems: number; totalPages: number };
}

export interface SessionView {
  id: string;
  current: boolean;
  createdAt: string;
  lastSeenAt: string;
  userAgent?: string;
  ipHint?: string;
}

export interface AuthMethods {
  identities: { id: string; provider: 'GOOGLE' | 'EMAIL'; label: string }[];
  passkeys: { id: string; label: string }[];
}

/**
 * Helpers shared by the auth/account specs (not a spec file itself).
 *
 * Magic-link requests are rate limited per client IP (10 per 15 min, in memory). The e2e server clock is fixed, so
 * that window never slides during a run; every test that requests links therefore uses its own client IP through
 * `X-Forwarded-For` (the app runs with `server.forward-headers-strategy=framework`, as behind the prod proxy).
 */
export function randomClientIp(): string {
  return `10.${randomInt(0, 255)}.${randomInt(0, 255)}.${randomInt(1, 254)}`;
}

/** All requests of the page's browser context come from a fresh client IP. */
export async function useFreshClientIp(page: Page): Promise<string> {
  const ip = randomClientIp();
  await page.context().setExtraHTTPHeaders({ 'X-Forwarded-For': ip });
  return ip;
}

/** A unique, deliverable (not blocked) email address for sign-up tests. */
export function newEmail(prefix = 'newcomer'): string {
  return `${prefix}-${Date.now().toString(36)}${randomInt(0, 1_000_000).toString(36)}@example.org`;
}

/** Answers the "log in faster with a passkey" nudge in advance (tests that are not about it). */
export async function skipPasskeyNudge(page: Page): Promise<void> {
  await page.addInitScript(() => {
    try {
      window.localStorage.setItem('fs.passkeyNudge', 'dismissed');
    } catch {
      /* storage disabled */
    }
  });
}

/** Logs in through the dev user switcher on /login and waits for the target page. */
export async function loginWithSwitcher(page: Page, role: Role, target: string | RegExp = /\/dashboard$/): Promise<void> {
  if (!/\/login(\?|$)/.test(page.url())) await page.goto('/login');
  await page.getByTestId(`auth.login.devSwitcher.${roleEmail(role)}`).click();
  await expect(page).toHaveURL(target);
}

/** Opens the user menu in the header and clicks "Log out". */
export async function logoutWithMenu(page: Page): Promise<void> {
  await page.getByTestId('shell.userMenu').click();
  await page.getByTestId('shell.userMenu.logout').click();
  await expect(page.getByTestId('shell.login')).toBeVisible();
}

/** Requests a magic link through the login form and waits for the "sent" page. */
export async function requestLinkWithForm(page: Page, email: string): Promise<void> {
  await page.getByTestId('auth.login.email').fill(email);
  await page.getByTestId('auth.login.emailSubmit').click();
  await expect(page).toHaveURL(/\/login\/sent\?/);
  await expect(page.getByTestId('auth.sent.email')).toHaveText(email);
}

/** Actions of the current user's history (`GET /api/history/me`), newest first. */
export async function myActions(api: ApiClient, size = 50): Promise<string[]> {
  const page = await api.getJson<HistoryEntryPage>(`/api/history/me?size=${size}`);
  return page.items.map((entry) => entry.action);
}

/** Clicks the accept button of the global confirm dialog (p-confirmdialog in the shell). */
export async function acceptConfirm(page: Page): Promise<void> {
  const accept = page.locator('.p-confirmdialog-accept-button');
  await expect(accept).toBeVisible();
  await accept.click();
}

/** The current session id of a cookie jar (`GET /api/auth/session`). */
export async function sessionIdOf(api: ApiClient): Promise<string> {
  const session = await api.session();
  if (!session.sessionId) throw new Error('No session id: the context is not logged in');
  return session.sessionId;
}

/**
 * EA4: a user with pending legal consents (new accounts, newbie@local) lands on /consent first. Accepts every
 * document there through the UI and waits until the app continues to `expectedPath`.
 */
export async function acceptConsentPage(page: Page, expectedPath: RegExp): Promise<void> {
  await expect(page).toHaveURL(/\/consent(\?|$)/);
  const documents = page.getByTestId('legal.consent.documents');
  await expect(documents).toHaveAttribute('data-state', 'success');
  const checks = page.locator('[data-testid^="legal.consent.check."] input');
  const count = await checks.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) await checks.nth(i).check();
  // EA4 phone prompt: accounts without a valid mobile number add one together with the consents
  const phone = page.getByTestId('legal.consent.phoneInput');
  if (await phone.isVisible()) await phone.fill('079 123 45 99');
  await page.getByTestId('legal.consent.submit').click();
  await expect(page).toHaveURL(expectedPath);
}
