import fs from 'node:fs';
import { request as playwrightRequest } from '@playwright/test';
import { ApiClient, ROLES, authFile, expect, roleEmail, test } from './fixtures';
import { AUTH_DIR } from './support/env';

/**
 * Project `setup`: logs in as every seed user through the dev switcher API and stores `e2e/.auth/<role>.json`.
 *
 * Specs should prefer `test.use({ role })` / `as(role)` (fresh login after the per-file seed reset); the stored
 * states are for tools (`npx playwright codegen --load-storage=e2e/.auth/orga.json <url>`) and for tests that
 * explicitly do not reset the seed. Running this project first also fails fast when a seed user is missing.
 */
test.use({ seedReset: 'off' });

test('dev switcher logs in every seed role and stores its session', async ({ baseURL, dev }) => {
  fs.mkdirSync(AUTH_DIR, { recursive: true });
  const users = await dev.users();
  for (const role of ROLES) {
    expect(users.map((user) => user.email.toLowerCase()), `seed user ${roleEmail(role)}`).toContain(roleEmail(role));
    const request = await playwrightRequest.newContext({ baseURL });
    try {
      const client = new ApiClient(request, baseURL!);
      const session = await client.loginAs(role);
      expect(session.authenticated).toBe(true);
      expect(session.user?.email.toLowerCase()).toBe(roleEmail(role));
      await client.csrfToken();
      await request.storageState({ path: authFile(role) });
    } finally {
      await request.dispose();
    }
  }
});
