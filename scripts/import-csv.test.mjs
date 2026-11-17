// Self-test of the runbook import script (D16.x, runbook "Initial data") against a stub server: no backend needed.
// Run: node --test scripts/import-csv.test.mjs (CI: contract job). The import rules themselves are server tests
// (RegionsCsvImportZuerichIT, StoresCsvImportZuerichIT, PastImportZuerichIT).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { after, before, beforeEach, describe, test } from 'node:test';

import { parseSimpleCsv, run } from './import-csv.mjs';

/** Stub of the few endpoints the script calls; records every request. */
const state = { requests: [], authenticated: true, regionsResult: null, storesResult: null, aliases: [] };
const server = http.createServer((req, res) => {
  let body = '';
  req.on('data', (chunk) => { body += chunk; });
  req.on('end', () => {
    const json = body ? JSON.parse(body) : undefined;
    state.requests.push({ method: req.method, url: req.url, headers: req.headers, body: json });
    const send = (status, data, headers = {}) => {
      res.writeHead(status, { 'Content-Type': 'application/json', ...headers });
      res.end(data === undefined ? '' : JSON.stringify(data));
    };
    const route = `${req.method} ${req.url}`;
    if (route === 'GET /api/auth/session') {
      return send(200, { authenticated: state.authenticated }, { 'Set-Cookie': 'XSRF-TOKEN=xsrf-1; Path=/' });
    }
    if (route === 'GET /api/public/regions/zuerich-stadt') return send(200, { id: 'region-1' });
    if (route === 'GET /api/public/regions/nowhere') return send(404, { code: 'REGION_NOT_FOUND', message: 'unknown' });
    if (route === 'POST /api/admin/regions/import') return send(200, { ...state.regionsResult, dryRun: json.dryRun });
    if (route === 'POST /api/regions/region-1/stores/import') return send(200, { ...state.storesResult, dryRun: json.dryRun });
    if (route === 'GET /api/regions/region-1/admin/store-aliases') return send(200, { items: state.aliases });
    if (route === 'POST /api/regions/region-1/admin/store-aliases') return send(201, json);
    return send(500, { code: 'UNEXPECTED', message: route });
  });
});

let baseUrl;
let dir;

before(async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'import-csv-test-'));
});

after(() => {
  server.close();
  fs.rmSync(dir, { recursive: true, force: true });
});

beforeEach(() => {
  state.requests = [];
  state.authenticated = true;
  state.regionsResult = { rows: [], created: 2, updated: 1, errors: 0 };
  state.storesResult = { rows: [], created: 1, updated: 0, skipped: 0, errors: 1 };
  state.aliases = [];
});

function file(name, text) {
  const target = path.join(dir, name);
  fs.writeFileSync(target, text);
  return target;
}

async function runQuietly(argv) {
  const lines = [];
  const log = console.log;
  console.log = () => {}; // row tables
  try {
    const code = await run([...argv, '--base-url', baseUrl, '--session', 'session-1'], (line) => lines.push(line));
    return { code, lines };
  } finally {
    console.log = log;
  }
}

const posts = () => state.requests.filter((r) => r.method === 'POST');

describe('import-csv.mjs', () => {
  test('dry run by default: sends the file with dryRun, the session cookie and the XSRF token, exit 0', async () => {
    const csv = 'parent_slug,slug,name,type\n,zuerich,Zürich,CITY\n';
    const { code, lines } = await runQuietly(['regions', file('regions.csv', csv)]);

    assert.equal(code, 0);
    assert.deepEqual(lines, ['regions: created=2 updated=1 errors=0 dryRun=true']);
    const [post] = posts();
    assert.equal(post.url, '/api/admin/regions/import');
    assert.deepEqual(post.body, { csv, dryRun: true });
    assert.match(post.headers.cookie, /fs_session=session-1/);
    assert.match(post.headers.cookie, /XSRF-TOKEN=xsrf-1/);
    assert.equal(post.headers['x-xsrf-token'], 'xsrf-1');
  });

  test('--confirm imports into the region of --region; rows with errors give exit 2', async () => {
    const { code, lines } = await runQuietly(['stores', file('stores.csv', 'name\nBäckerei\n'), '--region', 'zuerich-stadt', '--confirm']);

    assert.equal(code, 2);
    assert.equal(posts()[0].url, '/api/regions/region-1/stores/import');
    assert.equal(posts()[0].body.dryRun, false);
    assert.deepEqual(lines, ['stores: created=1 updated=0 skipped=0 errors=1 dryRun=false']);
  });

  test('aliases: existing aliases (by normalised key) are skipped, empty values are errors, dry run posts nothing', async () => {
    state.aliases = [{ aliasKey: 'Coop Seefeld' }];
    const csv = '# aliases\nalias,store_name\ncoop-seefeld,Coop Seefeld\n"Migros Ö, Altstadt",Migros City\n,Denner\n';
    const aliases = file('aliases.csv', csv);

    const dry = await runQuietly(['aliases', aliases, '--region', 'zuerich-stadt']);
    assert.equal(dry.code, 2);
    assert.equal(posts().length, 0);
    assert.equal(dry.lines.at(-1), 'aliases: created=1 skipped=1 errors=1 dryRun=true');

    state.requests = [];
    const confirmed = await runQuietly(['aliases', aliases, '--region', 'zuerich-stadt', '--confirm']);
    assert.equal(confirmed.code, 2);
    assert.deepEqual(posts().map((r) => r.body), [{ aliasKey: 'Migros Ö, Altstadt', storeName: 'Migros City' }]);
  });

  test('refuses a session that is not logged in, before sending the file', async () => {
    state.authenticated = false;
    await assert.rejects(runQuietly(['regions', file('r.csv', 'x\n')]), /not logged in/);
    assert.equal(posts().length, 0);
  });

  test('reports the error code of a failed request, and the usage for missing arguments', async () => {
    await assert.rejects(runQuietly(['stores', file('s.csv', 'x\n'), '--region', 'nowhere']), /404 REGION_NOT_FOUND: unknown/);
    await assert.rejects(runQuietly(['stores', file('s.csv', 'x\n')]), /--region <slug> is required/);
    await assert.rejects(runQuietly(['nonsense', file('n.csv', 'x\n')]), /usage: import-csv\.mjs/);
    await assert.rejects(run(['regions']), /usage: import-csv\.mjs/);
    await assert.rejects(runQuietly(['regions', file('r.csv', 'x\n'), '--bogus']), /unknown option --bogus/);
  });

  test('parseSimpleCsv: BOM, comments, blank lines, quotes with commas and doubled quotes', () => {
    const rows = parseSimpleCsv('﻿alias,store_name\n# comment\n\n "a, b" , "say ""hi"""\r\nx,y\n');
    assert.deepEqual(rows, [['alias', 'store_name'], ['a, b', 'say "hi"'], ['x', 'y']]);
  });
});
