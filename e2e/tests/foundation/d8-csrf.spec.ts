import { CSRF_COOKIE, CSRF_HEADER, expect, test } from '../../fixtures';

/** CSRF mechanics of A9 that every later spec relies on through the `api` fixture. */

test.use({ seedReset: 'off' });

const REPORT = { message: 'e2e csrf probe', route: '/e2e', userAgent: 'playwright' };

test('D8.4 unsafe API calls need the XSRF-TOKEN cookie echoed as header @D8.4', async ({ api }) => {
  // Any GET hands out the token cookie.
  await api.get('/api/public/app-info');
  const token = await api.cookie(CSRF_COOKIE);
  expect(token).toBeTruthy();

  const withoutHeader = await api.request.post(api.url('/api/public/client-errors'), { data: REPORT });
  expect(withoutHeader.status()).toBe(403);
  expect((await withoutHeader.json()).code).toBe('ACCESS_DENIED');

  const wrongHeader = await api.post('/api/public/client-errors', REPORT, { headers: { [CSRF_HEADER]: 'wrong' } });
  expect(wrongHeader.status()).toBe(403);

  const withHeader = await api.post('/api/public/client-errors', REPORT);
  expect(withHeader.status()).toBe(202);
});

test('D8.4 the CSRF token stays stable across requests of a signed-in session @D8.4', async ({ api }) => {
  await api.loginAs('foodsaver');
  await api.get('/api/auth/session');
  const before = await api.cookie(CSRF_COOKIE);
  expect(before).toBeTruthy();
  for (const path of ['/api/auth/session', '/api/users/me', '/']) {
    const response = await api.get(path);
    const setCookies = response.headersArray().filter((header) => header.name.toLowerCase() === 'set-cookie');
    expect(setCookies.map((header) => header.value).join('\n')).not.toContain(`${CSRF_COOKIE}=`);
  }
  expect(await api.cookie(CSRF_COOKIE)).toBe(before);
  // Two mutations in a row with the same token both pass.
  for (let i = 0; i < 2; i++) {
    const response = await api.post('/api/public/client-errors', REPORT);
    expect(response.status()).toBe(202);
  }
});
