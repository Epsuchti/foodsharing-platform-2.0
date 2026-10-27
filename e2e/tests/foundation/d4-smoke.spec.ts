import { ApiClient, ROLES, expect, roleEmail, test, type AppInfo, type DevUser, type Role, type UserRole } from '../../fixtures';
import { conformance, schema } from '../../support/openapi';

/** Foundation smoke tests: the packaged jar, the e2e profile and the contract basics (plan-m1 T5). */

// Read-only checks: no seed reset needed, so this file also runs against any reused server.
test.use({ seedReset: 'off' });

const EXPECTED_ROLES: Record<Role, UserRole> = {
  newbie: 'FOODSHARER',
  profile: 'FOODSHARER',
  quiz: 'FOODSHARER',
  einab1: 'FOODSHARER',
  pending: 'FOODSHARER',
  foodsaver: 'FOODSAVER',
  jumper: 'FOODSAVER',
  sleepy: 'FOODSAVER',
  guide: 'FOODSAVER',
  bieb: 'STORE_MANAGER',
  bot: 'AMBASSADOR',
  welcome: 'AMBASSADOR',
  reports: 'AMBASSADOR',
  orga: 'ORGA',
};

test('D4.1 packaged jar is healthy and reports its version @D4.1', async ({ api }) => {
  const health = await api.getJson<{ status: string }>('/actuator/health');
  expect(health.status).toBe('UP');

  const appInfo = await api.getJson<AppInfo>('/api/public/app-info');
  const versionGroup = await api.getJson<{ status: string; components?: Record<string, { details?: { version?: string } }> }>(
    '/actuator/health/version',
  );
  expect(versionGroup.status).toBe('UP');
  const reported = Object.values(versionGroup.components ?? {}).map((component) => component.details?.version);
  expect(reported).toContain(appInfo.version);
});

test('D4.3 deep link serves the app @D4.3', async ({ page, api }) => {
  const root = await api.get('/');
  expect(root.status()).toBe(200);
  expect(root.headers()['content-type']).toContain('text/html');

  const response = await page.goto('/some/deep/link');
  expect(response?.status()).toBe(200);
  expect(response?.headers()['content-type']).toContain('text/html');
  expect(await response?.text()).toBe(await root.text());
  expect(new URL(page.url()).pathname).toBe('/some/deep/link');
  // Angular bootstrapped (the root component carries ng-version in every build).
  await expect(page.locator('[ng-version]')).toBeAttached();

  // Backend paths are never swallowed by the SPA fallback.
  const unknownApi = await api.get('/api/public/does-not-exist');
  expect(unknownApi.headers()['content-type']).toContain('application/json');
  expect(unknownApi.status()).toBe(404);
});

test('D4.2 local profile boots with seed users @D4.2 @D7.1 @D7.2', async ({ api, dev }) => {
  // Read the db component regardless of the aggregate status (D4.1 asserts that one).
  const health = (await (await api.get('/actuator/health')).json()) as {
    components?: { db?: { status?: string; details?: { database?: string } } };
  };
  expect(health.components?.db?.status).toBe('UP');
  expect(health.components?.db?.details?.database).toBe('H2');

  const users = await dev.users();
  const byEmail = new Map(users.map((user) => [user.email.toLowerCase(), user]));
  for (const role of ROLES) {
    const user = byEmail.get(roleEmail(role));
    expect(user, `seed user ${roleEmail(role)}`).toBeDefined();
    expect(user!.role, `role of ${roleEmail(role)}`).toBe(EXPECTED_ROLES[role]);
    expect(user!.id).toMatch(/^00000001-0001-4000-8000-\d{12}$/);
  }
});

test('D7.3 dev user switcher lists all seed users @D7.3', async ({ api, playwright, baseURL }) => {
  const users = await api.getJson<DevUser[]>('/api/dev/users');
  for (const user of users) {
    expect(conformance(user, 'dev', 'DevUser')).toEqual([]);
    expect(user.descriptionKey).toMatch(/^dev\.users\./);
  }
  // Ordered by journey stage: newcomer first, orga last.
  const seedEmails = users.map((user) => user.email.toLowerCase()).filter((email) => email.endsWith('@local'));
  expect(seedEmails[0]).toBe(roleEmail('newbie'));
  expect(seedEmails.at(-1)).toBe(roleEmail('orga'));

  for (const role of ROLES) {
    const request = await playwright.request.newContext({ baseURL });
    try {
      const client = new ApiClient(request, baseURL!);
      const session = await client.loginAs(role);
      expect(session.authenticated).toBe(true);
      expect(session.user?.email.toLowerCase()).toBe(roleEmail(role));
      expect(session.user?.role).toBe(EXPECTED_ROLES[role]);
      expect((await client.session()).user?.id).toBe(session.user?.id);
    } finally {
      await request.dispose();
    }
  }
});

test('D5.1 app-info contract @D5.1', async ({ api }) => {
  const response = await api.get('/api/public/app-info');
  expect(response.status()).toBe(200);
  expect(response.headers()['x-trace-id']).toMatch(/^[0-9a-f]{12}$/);

  const info = (await response.json()) as AppInfo;
  expect(conformance(info, 'public', 'AppInfo')).toEqual([]);
  expect(info.version).not.toBe('');
  expect(info.devMode).toBe(true);
  expect(info.languages).toEqual(schema('common', 'Language').enum);
  expect(Number.isNaN(Date.parse(info.serverTime))).toBe(false);

  // Errors use the ApiError schema with the same trace id as the header.
  const unauthenticated = await api.get('/api/users/me');
  expect(unauthenticated.status()).toBe(401);
  const error = await unauthenticated.json();
  expect(conformance(error, 'common', 'ApiError')).toEqual([]);
  expect(error.code).toBe('AUTHENTICATION_REQUIRED');
  expect(error.traceId).toBe(unauthenticated.headers()['x-trace-id']);
});
