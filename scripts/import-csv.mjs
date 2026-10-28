#!/usr/bin/env node
// Initial-data import against a running instance (D16.x, runbook "Initial data").
//
//   node scripts/import-csv.mjs regions <file> --base-url <url> --session <fs_session> [--confirm]
//   node scripts/import-csv.mjs stores  <file> --base-url <url> --session <fs_session> --region <slug> [--confirm]
//   node scripts/import-csv.mjs einabs  <file> --base-url <url> --session <fs_session> --region <slug> [--confirm]
//
// Dry run by default (prints the preview); --confirm imports. The session is the value of the fs_session cookie of an
// ORGA (regions) / store coordinator or ambassador (stores, einabs) after a normal magic-link login in the browser.
// Never use a dev endpoint for this. Exit code 0 = no row errors, 2 = rows with errors, 1 = request failed.
import fs from 'node:fs';

const USAGE = 'usage: import-csv.mjs regions|stores|einabs <file> --base-url <url> --session <fs_session> '
  + '[--region <slug>] [--confirm] [--json]';

function parseArgs(argv) {
  const args = { positional: [], confirm: false, json: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--confirm') args.confirm = true;
    else if (a === '--json') args.json = true;
    else if (a === '--base-url') args.baseUrl = argv[++i];
    else if (a === '--session') args.session = argv[++i];
    else if (a === '--region') args.region = argv[++i];
    else if (a.startsWith('--')) throw new Error(`unknown option ${a}`);
    else args.positional.push(a);
  }
  return args;
}

export class Client {
  constructor(baseUrl, session) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.cookies = { fs_session: session };
  }

  cookieHeader() {
    return Object.entries(this.cookies).map(([k, v]) => `${k}=${v}`).join('; ');
  }

  remember(response) {
    for (const line of response.headers.getSetCookie?.() ?? []) {
      const [pair] = line.split(';');
      const eq = pair.indexOf('=');
      this.cookies[pair.slice(0, eq).trim()] = pair.slice(eq + 1).trim();
    }
  }

  async request(method, path, body) {
    const headers = { Accept: 'application/json', Cookie: this.cookieHeader() };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET' && this.cookies['XSRF-TOKEN']) headers['X-XSRF-TOKEN'] = this.cookies['XSRF-TOKEN'];
    const response = await fetch(this.baseUrl + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
    });
    this.remember(response);
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const code = data?.code ?? response.status;
      throw new Error(`${method} ${path} -> ${response.status} ${code}${data?.message ? `: ${data.message}` : ''}`);
    }
    return data;
  }
}

async function regionId(client, slug) {
  if (!slug) throw new Error('--region <slug> is required');
  return (await client.request('GET', `/api/public/regions/${encodeURIComponent(slug)}`)).id;
}

async function waitForJob(client, jobId) {
  for (let i = 0; i < 600; i++) {
    const job = await client.request('GET', `/api/jobs/${jobId}`);
    if (job.status !== 'RUNNING') return job;
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`job ${jobId} did not finish in 10 minutes`);
}

function printRows(rows, columns) {
  for (const row of rows) {
    const problems = [...(row.errors ?? []), ...(row.warnings ?? [])];
    console.log(`  ${columns.map((c) => row[c] ?? '').join(' | ')}${problems.length ? `  [${problems.join(', ')}]` : ''}`);
  }
}

export async function run(argv, log = console.log) {
  const args = parseArgs(argv);
  const [kind, file] = args.positional;
  if (!kind || !file || !args.baseUrl || !args.session) throw new Error(USAGE);
  const csv = fs.readFileSync(file, 'utf8');
  const client = new Client(args.baseUrl, args.session);
  const session = await client.request('GET', '/api/auth/session');
  if (!session?.authenticated) {
    throw new Error('the session is not logged in (copy fs_session after a magic-link login)');
  }
  const dryRun = !args.confirm;
  let result;
  let errors = 0;
  if (kind === 'regions') {
    result = await client.request('POST', '/api/admin/regions/import', { csv, dryRun });
    if (!args.json) printRows(result.rows, ['line', 'status', 'slug', 'name', 'type']);
    log(`regions: created=${result.created} updated=${result.updated} errors=${result.errors} dryRun=${result.dryRun}`);
    errors = result.errors;
  } else if (kind === 'stores') {
    const id = await regionId(client, args.region);
    result = await client.request('POST', `/api/regions/${id}/stores/import`, { csv, dryRun });
    if (!args.json) printRows(result.rows, ['line', 'status', 'name', 'postcode', 'city']);
    log(`stores: created=${result.created} updated=${result.updated} skipped=${result.skipped} `
      + `errors=${result.errors} dryRun=${result.dryRun}`);
    errors = result.errors;
  } else if (kind === 'einabs') {
    const id = await regionId(client, args.region);
    if (dryRun) {
      result = await client.request('POST', `/api/regions/${id}/admin/past-import/preview`, { csv });
      const c = result.counts;
      if (!args.json) printRows(result.rows.filter((r) => r.status !== 'CREATE'), ['line', 'status', 'name', 'date', 'store']);
      log(`einabs: total=${c.totalRows} willCreate=${c.willCreate} willSkip=${c.willSkip} `
        + `needsAttention=${c.needsAttention} knownUsers=${c.knownUsers} unknownUsers=${c.unknownUsers} dryRun=true`);
      errors = c.needsAttention;
    } else {
      result = await client.request('POST', `/api/regions/${id}/admin/past-import/confirm`, { csv });
      if (result.jobId) {
        const job = await waitForJob(client, result.jobId);
        log(`einabs: job ${result.jobId} ${job.status}`);
        if (job.status !== 'DONE') errors = 1;
      }
      log(`einabs: created=${result.created} skipped=${result.skipped} needsAttention=${result.needsAttention} dryRun=false`);
      errors = errors || result.needsAttention;
    }
  } else {
    throw new Error(USAGE);
  }
  if (args.json) log(JSON.stringify(result, null, 2));
  return errors > 0 ? 2 : 0;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  run(process.argv.slice(2)).then((code) => process.exit(code), (e) => {
    console.error(e.message);
    process.exit(1);
  });
}
