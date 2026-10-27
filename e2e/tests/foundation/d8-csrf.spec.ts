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
