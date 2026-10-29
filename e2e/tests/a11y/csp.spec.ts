import { expect, test, type Role } from '../../fixtures';
import type { Page } from '@playwright/test';

/**
 * D17.2 security review: the strict Content-Security-Policy (no inline scripts except the hashed ones, no eval,
 * object-src 'none', form-action 'self') holds on every route: no `securitypolicyviolation` event and no CSP console
 * error while the page loads and settles, for visitors and the roles that use the page.
 */

test.use({ seedReset: 'file' });

const ZUERICH_STADT = '00000010-0001-4000-8000-000000000040';
const STORE_S01 = '00000020-0001-4000-8000-000000000001';

interface RouteCase {
  path: string;
  role: Role | null;
}

const ROUTES: RouteCase[] = [
  { path: '/', role: null },
  { path: '/login', role: null },
  { path: '/login/verify', role: null },
  { path: '/r/zuerich-stadt', role: null },
  { path: '/find-region', role: null },
  { path: '/onboarding', role: null },
  { path: '/learn', role: null },
  { path: '/legal/privacy', role: null },
  { path: '/intro-pickups', role: null },
  { path: '/forbidden', role: null },
  { path: '/does-not-exist', role: null },
  { path: '/dashboard', role: 'foodsaver' },
  { path: '/account/settings', role: 'foodsaver' },
  { path: '/account/sessions', role: 'foodsaver' },
  { path: '/account/profile', role: 'foodsaver' },
  { path: '/account/notifications', role: 'foodsaver' },
  { path: '/me/activity', role: 'foodsaver' },
  { path: '/bells', role: 'foodsaver' },
  { path: '/messages', role: 'foodsaver' },
  { path: '/regions/mine', role: 'foodsaver' },
  { path: `/regions/${ZUERICH_STADT}`, role: 'foodsaver' },
  { path: `/regions/${ZUERICH_STADT}/stores`, role: 'foodsaver' },
  { path: '/stores/mine', role: 'foodsaver' },
  { path: `/stores/${STORE_S01}`, role: 'foodsaver' },
  { path: '/pickups/mine', role: 'foodsaver' },
  { path: '/me/pickups/history', role: 'foodsaver' },
  { path: '/quizzes', role: 'foodsaver' },
  { path: '/mailboxes', role: 'bieb' },
  { path: '/store-chains', role: 'orga' },
  { path: '/admin/regions', role: 'orga' },
  { path: '/admin/jobs', role: 'orga' },
  { path: '/admin/content', role: 'orga' },
  { path: '/history', role: 'orga' },
];

/** Records CSP violations in the page (before any script of the app runs) and CSP errors on the console. */
async function watch(page: Page): Promise<() => Promise<string[]>> {
  const consoleErrors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error' && /content security policy/i.test(msg.text())) consoleErrors.push(msg.text());
  });
  await page.addInitScript(() => {
    const store: string[] = [];
    (window as unknown as { __cspViolations: string[] }).__cspViolations = store;
    document.addEventListener('securitypolicyviolation', (e) => {
      store.push(`${e.effectiveDirective} blocked ${e.blockedURI || '(inline)'} at ${e.sourceFile}:${e.lineNumber}`);
    });
  });
  return async () => [
    ...(await page.evaluate(() => (window as unknown as { __cspViolations?: string[] }).__cspViolations ?? [])),
    ...consoleErrors,
  ];
}

for (const route of ROUTES) {
  test(`D17.2 no CSP violation on ${route.path} as ${route.role ?? 'visitor'} @D17.2`, async ({ as }) => {
    const page = await as(route.role);
    const violations = await watch(page);
    const response = await page.goto(route.path);
    expect(response?.headers()['content-security-policy']).toContain("script-src 'self'");
    await expect(page.locator('main#main-content')).toBeVisible();
    await page.waitForLoadState('networkidle');
    expect(await violations()).toEqual([]);
  });
}
