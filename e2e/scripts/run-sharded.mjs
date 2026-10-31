#!/usr/bin/env node
// Local sharded Playwright run (decision 131): N shard processes on this machine, each with its own jar, port,
// in-memory database, test-results folder and blob report, merged into one HTML report and one results.json.
//
// Usage (from e2e/):
//   npm run e2e:sharded -- --project=chromium                 # default shard count, see below
//   npm run e2e:sharded -- --project=chromium --shards=4 tests/stores
//   node scripts/run-sharded.mjs --summary test-results/results.json   # only print the summary of a JSON report
//
// Options: --shards=N (default min(4, floor(free memory GB / 2.5)), at least 1; env E2E_SHARDS works too).
// Every other argument goes to `playwright test` unchanged; the runner adds `--shard=i/N --reporter=blob,list`.
//
// Output:
//   test-results/instances/shard-<i>/output.log   the shard's list reporter output
//   test-results/instances/shard-<i>/server.log   its jar
//   test-results/instances/shard-<i>/artifacts    traces, videos, failure screenshots
//   blob-report/sharded/report-<i>.zip            blob reports
//   playwright-report/ + test-results/results.json  the merged reports (feature-coverage.mjs --results reads the JSON)
// Exit 1 when a test failed or was flaky, a shard crashed or the merge failed.
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const CLI = path.join(E2E_DIR, 'node_modules', '@playwright', 'test', 'cli.js');
const RESULTS_DIR = path.join(E2E_DIR, 'test-results');
const INSTANCES_DIR = path.join(RESULTS_DIR, 'instances');
const BLOB_DIR = path.join(E2E_DIR, 'blob-report', 'sharded');
const RESULTS_JSON = path.join(RESULTS_DIR, 'results.json');
const HTML_DIR = path.join(E2E_DIR, 'playwright-report');
const PROGRESS_EVERY_MS = 60_000;
const GB = 1024 ** 3;

/** Default shard count: one jar + browser needs ~2–2.5 GB under load (decision 131). */
export function defaultShards(freeBytes = os.freemem()) {
  return Math.max(1, Math.min(4, Math.floor(freeBytes / GB / 2.5)));
}

function parseArgs(argv) {
  let shards = process.env.E2E_SHARDS ? Number(process.env.E2E_SHARDS) : undefined;
  let summary;
  const rest = [];
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg.startsWith('--shards=')) shards = Number(arg.slice('--shards='.length));
    else if (arg === '--shards') shards = Number(argv[++i]);
    else if (arg === '--summary') summary = argv[++i] ?? RESULTS_JSON;
    else if (arg === '--shard' || arg.startsWith('--shard=')) throw new Error('Use --shards=N; the runner sets --shard itself.');
    else if (arg === '--help' || arg === '-h') {
      console.log(fs.readFileSync(fileURLToPath(import.meta.url), 'utf8').split('\n').slice(1, 20).join('\n'));
      process.exit(0);
    } else rest.push(arg);
  }
  if (shards !== undefined && (!Number.isInteger(shards) || shards < 1)) throw new Error(`Invalid shard count: ${shards}`);
  return { shards: shards ?? defaultShards(), summary, rest };
}

/** Passed/failed/flaky/skipped counts and the failed tests (file:line) of a Playwright JSON report. */
export function summarize(report) {
  const counts = { passed: 0, failed: 0, flaky: 0, skipped: 0 };
  const failed = [];
  const flaky = [];
  const walk = (suite, titles) => {
    for (const spec of suite.specs ?? []) {
      for (const test of spec.tests ?? []) {
        const where = `${spec.file}:${spec.line}`;
        const name = `[${test.projectName}] › ${[...titles, spec.title].join(' › ')}`;
        if (test.status === 'expected') counts.passed++;
        else if (test.status === 'skipped') counts.skipped++;
        else if (test.status === 'flaky') {
          counts.flaky++;
          flaky.push({ where, name });
        } else {
          counts.failed++;
          const error = test.results?.flatMap((r) => r.errors ?? [r.error]).find(Boolean);
          const message = (error?.message ?? '').replace(/\u001b\[[0-9;]*m/g, '').split('\n').find((l) => l.trim()) ?? '';
          failed.push({ where, name, message: message.trim().slice(0, 200) });
        }
      }
    }
    for (const child of suite.suites ?? []) walk(child, child.title && child.title !== child.file ? [...titles, child.title] : titles);
  };
  for (const suite of report.suites ?? []) walk(suite, []);
  const errors = (report.errors ?? []).map((e) => (e.message ?? String(e)).replace(/\u001b\[[0-9;]*m/g, '').split('\n')[0]);
  return { counts, failed, flaky, errors, durationMs: report.stats?.duration };
}

export function printSummary(summary, log = console.log) {
  const { counts, failed, flaky, errors } = summary;
  log(`\n[sharded] ${counts.passed} passed, ${counts.failed} failed, ${counts.flaky} flaky, ${counts.skipped} skipped`);
  if (failed.length) {
    log('[sharded] failed:');
    for (const f of failed) log(`  ${f.where}  ${f.name}${f.message ? `\n      ${f.message}` : ''}`);
  }
  if (flaky.length) {
    log('[sharded] flaky:');
    for (const f of flaky) log(`  ${f.where}  ${f.name}`);
  }
  for (const e of errors) log(`[sharded] error: ${e}`);
  return counts.failed === 0 && counts.flaky === 0 && errors.length === 0;
}

const fmt = (ms) => {
  const s = Math.round(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

function cpuTimes() {
  let idle = 0;
  let total = 0;
  for (const cpu of os.cpus()) {
    idle += cpu.times.idle;
    total += Object.values(cpu.times).reduce((a, b) => a + b, 0);
  }
  return { idle, total };
}

function isAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

/** Kills a jar a shard left behind (a killed shard never reaches global-teardown.ts). */
function killLeftoverJar(index) {
  const stateFile = path.join(E2E_DIR, '.state', `shard-${index}`, 'server.json');
  try {
    const { pid } = JSON.parse(fs.readFileSync(stateFile, 'utf8'));
    if (pid && isAlive(pid)) {
      process.kill(pid, 'SIGKILL');
      console.log(`[sharded] killed the leftover jar of shard ${index} (pid ${pid})`);
    }
    fs.rmSync(stateFile, { force: true });
  } catch {
    // no state file: nothing to clean up
  }
}

async function run({ shards, rest }) {
  if (!fs.existsSync(CLI)) throw new Error(`${CLI} is missing; run \`npm ci\` in e2e/ first.`);
  if (process.env.E2E_BASE_URL) throw new Error('E2E_BASE_URL is set: all shards would share one database. Unset it.');
  const env = { ...process.env };
  delete env.E2E_PORT; // each shard picks its own free port

  fs.rmSync(INSTANCES_DIR, { recursive: true, force: true });
  fs.rmSync(BLOB_DIR, { recursive: true, force: true });
  fs.rmSync(RESULTS_JSON, { force: true });
  fs.mkdirSync(BLOB_DIR, { recursive: true });

  const started = Date.now();
  console.log(
    `[sharded] ${shards} shard(s), ${os.cpus().length} CPUs, ${(os.freemem() / GB).toFixed(1)} of ` +
      `${(os.totalmem() / GB).toFixed(1)} GB free; playwright test ${rest.join(' ')}`,
  );

  const shardState = [];
  const children = [];
  for (let i = 1; i <= shards; i++) {
    const instance = `shard-${i}`;
    const dir = path.join(INSTANCES_DIR, instance);
    fs.mkdirSync(dir, { recursive: true });
    const out = fs.createWriteStream(path.join(dir, 'output.log'));
    const state = { index: i, total: undefined, done: 0, failed: 0, exitCode: undefined };
    shardState.push(state);
    const child = spawn(process.execPath, [CLI, 'test', `--shard=${i}/${shards}`, '--reporter=blob,list', ...rest], {
      cwd: E2E_DIR,
      env: {
        ...env,
        E2E_INSTANCE: instance,
        PLAYWRIGHT_BLOB_OUTPUT_FILE: path.join(BLOB_DIR, `report-${i}.zip`),
        FORCE_COLOR: '0',
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    children.push(child);
    let buffer = '';
    const onData = (chunk) => {
      out.write(chunk);
      buffer += chunk.toString('utf8');
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? '';
      for (const line of lines) {
        const running = /Running (\d+) tests? using/.exec(line);
        if (running) state.total = Number(running[1]);
        if (/^\s*\[e2e\] .* is up at /.test(line)) console.log(`[shard ${i}] ${line.trim()}`);
        const mark = /^\s+(✓|ok|✘|x|-|±|°)\s+\d+\s+\[/.exec(line);
        if (!mark) continue;
        state.done++;
        if (mark[1] === '✘' || mark[1] === 'x') {
          state.failed++;
          console.log(`[shard ${i}] ${line.trim()}`);
        }
      }
    };
    child.stdout.on('data', onData);
    child.stderr.on('data', onData);
    state.exit = new Promise((resolve) => {
      child.on('exit', (code, signal) => {
        state.exitCode = code ?? (signal ? 1 : 0);
        out.end();
        resolve();
      });
    });
  }

  let stopping = false;
  const stop = async (signal) => {
    if (stopping) return;
    stopping = true;
    // Ctrl+C reaches the shards too; give their global teardown a moment to stop the jars, then force it.
    console.log(`\n[sharded] ${signal}: stopping the shards`);
    await Promise.race([Promise.all(shardState.map((s) => s.exit)), new Promise((r) => setTimeout(r, 15_000))]);
    for (const child of children) if (child.exitCode === null) child.kill();
    for (let i = 1; i <= shards; i++) killLeftoverJar(i);
    process.exit(130);
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);

  let lastCpu = cpuTimes();
  let minFree = os.freemem();
  let maxCpu = 0;
  const sampler = setInterval(() => {
    minFree = Math.min(minFree, os.freemem());
  }, 5_000);
  const progress = setInterval(() => {
    const now = cpuTimes();
    const busy = 1 - (now.idle - lastCpu.idle) / Math.max(1, now.total - lastCpu.total);
    lastCpu = now;
    maxCpu = Math.max(maxCpu, busy);
    const parts = shardState.map((s) => `${s.exitCode === undefined ? '' : 'done '}${s.done}/${s.total ?? '?'}${s.failed ? ` (${s.failed} failed)` : ''}`);
    console.log(
      `[sharded] ${fmt(Date.now() - started)}  ${parts.join(' | ')}  cpu ${(busy * 100).toFixed(0)}%  ` +
        `free ${(os.freemem() / GB).toFixed(1)} GB`,
    );
  }, PROGRESS_EVERY_MS);

  await Promise.all(shardState.map((s) => s.exit));
  clearInterval(progress);
  clearInterval(sampler);
  for (let i = 1; i <= shards; i++) killLeftoverJar(i);
  const wall = Date.now() - started;

  for (const s of shardState) {
    console.log(`[sharded] shard ${s.index}: exit ${s.exitCode}, ${s.done} tests (log: ${path.relative(E2E_DIR, path.join(INSTANCES_DIR, `shard-${s.index}`, 'output.log'))})`);
  }

  const blobs = fs.readdirSync(BLOB_DIR).filter((f) => f.endsWith('.zip'));
  let ok = shardState.every((s) => s.exitCode === 0);
  if (blobs.length === 0) {
    console.log('[sharded] no blob report was written; see the shard logs above.');
    return 1;
  }
  const merge = spawnSync(process.execPath, [CLI, 'merge-reports', '--reporter=json,html', BLOB_DIR], {
    cwd: E2E_DIR,
    env: { ...env, PLAYWRIGHT_JSON_OUTPUT_NAME: RESULTS_JSON, PLAYWRIGHT_HTML_OUTPUT_DIR: HTML_DIR, PLAYWRIGHT_HTML_OPEN: 'never' },
    stdio: ['ignore', 'ignore', 'inherit'],
  });
  if (merge.status !== 0 || !fs.existsSync(RESULTS_JSON)) {
    console.log(`[sharded] merge-reports failed (exit ${merge.status})`);
    return 1;
  }
  const summary = summarize(JSON.parse(fs.readFileSync(RESULTS_JSON, 'utf8')));
  ok = printSummary(summary) && ok;
  console.log(
    `[sharded] wall time ${fmt(wall)} with ${shards} shard(s); peak CPU ${(maxCpu * 100).toFixed(0)}% (per ${PROGRESS_EVERY_MS / 1000}s), ` +
      `lowest free memory ${(minFree / GB).toFixed(1)} GB`,
  );
  console.log(`[sharded] report: ${path.relative(process.cwd(), HTML_DIR)}  results: ${path.relative(process.cwd(), RESULTS_JSON)}`);
  if (blobs.length !== shards) console.log(`[sharded] only ${blobs.length} of ${shards} blob reports were written`);
  return ok && blobs.length === shards ? 0 : 1;
}

if (path.resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) {
  try {
    const args = parseArgs(process.argv.slice(2));
    if (args.summary) {
      process.exit(printSummary(summarize(JSON.parse(fs.readFileSync(args.summary, 'utf8')))) ? 0 : 1);
    }
    process.exit(await run(args));
  } catch (error) {
    console.error(`[sharded] ${error.message}`);
    process.exit(1);
  }
}
