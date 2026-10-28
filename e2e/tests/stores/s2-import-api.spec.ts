import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { expect, test } from '../../fixtures';
import { REPO_DIR } from '../../support/env';

/**
 * S2 / D16.2 stores CSV import over the API (decision 79): dry run previews without writing, confirm creates, a rerun
 * skips everything; row errors and warnings; only the store coordination of the region may import; the
 * `scripts/import-csv.mjs` runbook command works against the running jar.
 */
const WINTERTHUR = '00000010-0001-4000-8000-000000000041';
const HEADER = 'name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,team_status,public_info,manager_foodsaver_numbers';

interface ImportRow {
  line: number;
  name?: string;
  status: string;
  storeId?: string;
  errors: string[];
  warnings: string[];
}
interface ImportPreview {
  rows: ImportRow[];
  created: number;
  updated: number;
  skipped: number;
  errors: number;
  dryRun: boolean;
}

test.describe('orga', () => {
  test.use({ role: 'orga', seedReset: 'test' });

  test('S2 stores CSV import: dry run, confirm, rerun, errors and warnings @S2 @D16.2', async ({ api, store }) => {
    const csv = [
      '# e2e import',
      HEADER,
      'Import Bäckerei Altstadt,Bäckerei,,Marktgasse 1,8400,Winterthur,,,COOPERATION_ESTABLISHED,OPEN,Hintereingang,100009 999999',
      'Import Coop Bahnhof,Supermarkt,Coop,Bahnhofplatz 1,8400,Winterthur,47.5,8.72,IN_NEGOTIATION,CLOSED,,',
      'Import Metzgerei,Metzgerei,,,8400,Winterthur,,,,,,',
    ].join('\n');
    const url = `/api/regions/${WINTERTHUR}/stores/import`;

    const preview = await api.postJson<ImportPreview>(url, { csv, dryRun: true });
    expect(preview.dryRun).toBe(true);
    expect(preview.rows.map((row) => row.status)).toEqual(['CREATE', 'CREATE', 'ERROR']);
    expect(preview.rows[0].line).toBe(3);
    expect(preview.rows[0].warnings).toEqual(['MISSING_COORDINATES', 'UNKNOWN_USER']);
    expect(preview.rows[2].errors).toEqual(['UNKNOWN_CATEGORY']);
    expect(preview).toMatchObject({ created: 2, updated: 0, skipped: 0, errors: 1 });
    expect(preview.rows[0].storeId ?? null).toBeNull();

    const result = await api.postJson<ImportPreview>(url, { csv, dryRun: false });
    expect(result).toMatchObject({ created: 2, errors: 1, dryRun: false });
    const bakery = result.rows[0].storeId!;
    expect(bakery).toBeTruthy();
    const detail = await api.getJson<{ name: string; latitude?: number }>(`/api/stores/${bakery}`);
    expect(detail.name).toBe('Import Bäckerei Altstadt');
    expect(detail.latitude).toBeTruthy(); // geocoded from 8400

    const again = await api.postJson<ImportPreview>(url, { csv, dryRun: false });
    expect(again.rows.map((row) => row.status)).toEqual(['UNCHANGED', 'UNCHANGED', 'ERROR']);
    expect(again).toMatchObject({ created: 0, updated: 0, skipped: 2 });

    // only the store coordination of the region may import
    const denied = await store.withRole('foodsaver', (client) => client.post(url, { csv, dryRun: true }));
    expect(denied.status()).toBe(403);
    expect((await api.post(url, { csv: '', dryRun: true })).status()).toBe(400);
  });

  test('S2 import-csv.mjs runbook command previews stores @S2 @D16.2', async ({ api }) => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fs-import-'));
    const file = path.join(dir, 'stores.csv');
    fs.writeFileSync(file, `${HEADER}\nScript Laden,Supermarkt,,,8400,Winterthur,,,,,,\n`);
    const session = await api.cookie('fs_session');
    expect(session).toBeTruthy();
    const output = execFileSync(
      process.execPath,
      [path.join(REPO_DIR, 'scripts/import-csv.mjs'), 'stores', file, '--base-url', api.baseURL, '--session', session!, '--region', 'winterthur'],
      { encoding: 'utf8' },
    );
    expect(output).toContain('stores: created=1 updated=0 skipped=0 errors=0 dryRun=true');
    const after = await api.postJson<ImportPreview>(`/api/regions/${WINTERTHUR}/stores/import`, {
      csv: `${HEADER}\nScript Laden,Supermarkt,,,8400,Winterthur,,,,,,\n`,
      dryRun: true,
    });
    expect(after.rows[0].status).toBe('CREATE'); // the script's dry run wrote nothing
  });
});
