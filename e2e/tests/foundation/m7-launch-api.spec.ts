import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '../../fixtures';
import { REPO_DIR } from '../../support/env';

/**
 * M7 launch deliverables over the API of the running jar: the runbook import commands for the Zürich regions file
 * (D16.1) and the EinAb sample (D16.3) preview without writing, the app reports its version (D19.1) and every
 * metric the alert rules use is scraped (D19.3). Postgres-only checks live in RegionsCsvImportZuerichIT,
 * PastImportZuerichIT and MetricsContractTest.
 */
test.use({ role: 'orga', seedReset: 'test' });

function importCsv(kind: string, file: string, baseURL: string, session: string, extra: string[] = []) {
  return spawnSync(
    process.execPath,
    [path.join(REPO_DIR, 'scripts/import-csv.mjs'), kind, path.join(REPO_DIR, file), '--base-url', baseURL, '--session', session, ...extra],
    { encoding: 'utf8' },
  );
}

test('D16.1 Zürich regions file: runbook dry run reports no errors and writes nothing @D16.1', async ({ api }) => {
  const session = (await api.cookie('fs_session'))!;
  const first = importCsv('regions', 'docs/data/zuerich/regions.csv', api.baseURL, session);
  expect(first.status, first.stderr).toBe(0);
  const summary = /regions: created=(\d+) updated=(\d+) errors=0 dryRun=true/.exec(first.stdout);
  expect(summary, first.stdout).not.toBeNull();
  const second = importCsv('regions', 'docs/data/zuerich/regions.csv', api.baseURL, session);
  expect(second.stdout).toContain(summary![0]); // the dry run changed nothing
});

test('D16.3 EinAb sample: runbook dry run previews every row with its reasons @D16.3', async ({ api }) => {
  const session = (await api.cookie('fs_session'))!;
  const run = importCsv('einabs', 'docs/data/zuerich/einabs-sample.csv', api.baseURL, session, ['--region', 'zuerich-stadt']);
  expect([0, 2], run.stderr).toContain(run.status);
  expect(run.stdout).toMatch(/einabs: total=\d+ willCreate=\d+ willSkip=\d+ needsAttention=\d+ .*dryRun=true/);
});

test('D19.1 the app reports the version of the build @D19.1', async ({ api }) => {
  const info = await api.getJson<{ version?: string }>('/api/public/app-info');
  expect(info.version).toBeTruthy();
});

test('D19.3 every metric of the alert rules is scraped @D19.3', async ({ api }) => {
  const alerts = fs.readFileSync(path.join(REPO_DIR, 'server/deploy/monitoring/alerts.yml'), 'utf8');
  const metrics = [...new Set(alerts.match(/\bfs_[a-z_]+/g) ?? [])];
  expect(metrics.length).toBeGreaterThan(5);
  const response = await api.get('/actuator/prometheus', { headers: { Accept: 'text/plain' } });
  expect(response.status()).toBe(200);
  const scrape = await response.text();
  for (const metric of metrics) expect(scrape, metric).toContain(metric);
});
