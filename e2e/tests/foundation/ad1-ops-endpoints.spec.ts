import { expect, test, type AppInfo } from '../../fixtures';

/** Operations endpoints of §14.4 that the packaged jar exposes without login (AD1: version health, Prometheus). */

test.use({ seedReset: 'off' });

test('AD1 version health group reports the build version @AD1', async ({ api }) => {
  const appInfo = await api.getJson<AppInfo>('/api/public/app-info');
  const group = await api.getJson<{ status: string; components: Record<string, { status: string; details: { version: string } }> }>(
    '/actuator/health/version',
  );
  expect(group.status).toBe('UP');
  expect(group.components.appVersion.details.version).toBe(appInfo.version);

  const info = await api.getJson<{ app?: { version?: string } }>('/actuator/info');
  expect(info.app?.version).toBe(appInfo.version);
});

test('AD1 prometheus endpoint exposes http metrics @AD1', async ({ api }) => {
  // Make sure at least one request was recorded before scraping.
  await api.get('/api/public/app-info');
  const response = await api.get('/actuator/prometheus', { headers: { Accept: 'text/plain' } });
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toMatch(/text\/plain|openmetrics/);
  const body = await response.text();
  expect(body).toContain('http_server_requests_seconds_count');
  expect(body).toMatch(/uri="\/api\/public\/app-info"/);
  expect(body).toContain('jvm_memory_used_bytes');
});

test('AD1 other actuator endpoints are not public @AD1', async ({ api }) => {
  for (const path of ['/actuator/env', '/actuator/beans', '/actuator/loggers']) {
    const response = await api.get(path);
    expect([401, 403, 404], `${path} → ${response.status()}`).toContain(response.status());
  }
});
