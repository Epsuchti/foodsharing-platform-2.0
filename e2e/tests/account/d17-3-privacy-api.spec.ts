import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { expect, test } from '../../fixtures';
import { REPO_DIR } from '../../support/env';

/**
 * D17.3 privacy review over the API: the data export zip of a running jar contains a file for every personal-data
 * module named in docs/privacy/data-inventory.md (the deletion walk itself runs in AccountDeletionIT, it would remove
 * seed accounts other specs need).
 */
test.use({ role: 'reports', seedReset: 'test' });

test('D17.3 data export contains every module of the data inventory @D17.3', async ({ api }) => {
  const inventory = fs.readFileSync(path.join(REPO_DIR, 'docs', 'privacy', 'data-inventory.md'), 'utf8');
  const files = new Set<string>();
  for (const line of inventory.split('\n')) {
    const cells = line.split('|').map((cell) => cell.trim());
    const match = line.startsWith('| `') ? /`([A-Za-z]+\.json)`/.exec(cells[6] ?? '') : null;
    if (match) files.add(match[1]);
  }
  expect(files.size).toBeGreaterThan(20);

  const job = await api.postJson<{ jobId: string }>('/api/users/me/export');
  await expect
    .poll(async () => (await api.getJson<{ status: string }>(`/api/jobs/${job.jobId}`)).status, { timeout: 30_000 })
    .toBe('DONE');
  const result = await api.get(`/api/jobs/${job.jobId}/result`);
  expect(result.ok()).toBe(true);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'd17-3-'));
  const zip = path.join(dir, 'export.zip');
  fs.writeFileSync(zip, await result.body());
  const entries = execFileSync('unzip', ['-Z1', zip], { encoding: 'utf8' }).split('\n').filter(Boolean);
  const profile = JSON.parse(execFileSync('unzip', ['-p', zip, 'profile.json'], { encoding: 'utf8' })) as { email?: string };
  fs.rmSync(dir, { recursive: true, force: true });

  expect(entries).toEqual(expect.arrayContaining([...files].sort()));
  expect(profile.email).toBe('reports@local');
});
