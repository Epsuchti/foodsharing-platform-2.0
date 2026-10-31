#!/usr/bin/env node
// Local sharded Playwright run (decision 137). N slots run in parallel; each slot has its own jar (port, in-memory
// database, upload folder, log). The suite is cut into K chunks (`playwright test --shard=k/K`) that the slots take
// from a queue, the last chunk first, against their slot's jar (E2E_BASE_URL), one after another like a serial run on
// one jar: every spec file still starts with a seed reset. Small chunks balance the fast axe sweep against the slower
// feature specs; reusing the jar costs no extra start-up. All blob reports are merged into one HTML report and one
// results.json.
//
// Usage (from e2e/):
//   npm run e2e:sharded -- --project=chromium                 # default slot count, see below
//   npm run e2e:sharded -- --project=chromium --shards=4 tests/stores
//   node scripts/run-sharded.mjs --summary test-results/results.json   # only print the summary of a JSON report
//
// Options:
//   --shards=N   parallel slots (jars + browsers); default min(4, floor(free memory GB / 2.5)), at least 1 (E2E_SHARDS)
//   --chunks=K   chunks of the suite; default 3 × N (1 when N is 1)
//   --video      keep Playwright's video recording (retain-on-failure); by default the runner sets E2E_VIDEO=off,
//                which saves ~20 % test time (failure screenshots and error context stay)
// Every other argument goes to `playwright test` unchanged; the runner adds `--shard=k/K --reporter=blob,list`.
// Env: E2E_JAR (default: newest server/target/foodsharing-platform-*.jar), E2E_JAVA_OPTS, E2E_STARTUP_TIMEOUT_MS.
//
// Output:
//   test-results/instances/slot-<i>/server.log    the slot's jar
//   test-results/instances/chunk-<k>/output.log   list reporter output of a chunk
//   test-results/instances/chunk-<k>/artifacts    failure screenshots (videos with --video)
//   blob-report/sharded/report-<k>.zip            blob reports
//   playwright-report/ + test-results/results.json  the merged reports (feature-coverage.mjs --results reads the JSON)
// Exit 1 when a test failed or was flaky, a chunk or jar crashed, or the merge failed.
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SERVER_DIR = path.resolve(E2E_DIR, '..', 'server');
const CLI = path.join(E2E_DIR, 'node_modules', '@playwright', 'test', 'cli.js');
const RESULTS_DIR = path.join(E2E_DIR, 'test-results');
const INSTANCES_DIR = path.join(RESULTS_DIR, 'instances');
const BLOB_DIR = path.join(E2E_DIR, 'blob-report', 'sharded');
const RESULTS_JSON = path.join(RESULTS_DIR, 'results.json');
const HTML_DIR = path.join(E2E_DIR, 'playwright-report');
const STARTUP_TIMEOUT_MS = Number(process.env.E2E_STARTUP_TIMEOUT_MS ?? 240_000);
const PROGRESS_EVERY_MS = 60_000;
const GB = 1024 ** 3;

/** Default slot count: one jar + browser + Playwright needs ~2–2.5 GB under load (decision 137). */
export function defaultShards(freeBytes = os.freemem()) {
  return Math.max(1, Math.min(4, Math.floor(freeBytes / GB / 2.5)));
}

function parseArgs(argv) {
  let shards = process.env.E2E_SHARDS ? Number(process.env.E2E_SHARDS) : undefined;
  let chunks;
  let summary;
  let video = false;
  const rest = [];
  const count = (value, what) => {
    const n = Number(value);
    if (!Number.isInteger(n) || n < 1) throw new Error(`Invalid ${what}: ${value}`);
    return n;
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg.startsWith('--shards=')) shards = count(arg.slice('--shards='.length), 'shard count');
    else if (arg === '--shards') shards = count(argv[++i], 'shard count');
    else if (arg.startsWith('--chunks=')) chunks = count(arg.slice('--chunks='.length), 'chunk count');
    else if (arg === '--chunks') chunks = count(argv[++i], 'chunk count');
    else if (arg === '--video') video = true;
    else if (arg === '--summary') summary = argv[++i] ?? RESULTS_JSON;
    else if (arg === '--shard' || arg.startsWith('--shard=')) throw new Error('Use --shards=N; the runner sets --shard itself.');
    else if (arg === '--help' || arg === '-h') {
      console.log(fs.readFileSync(fileURLToPath(import.meta.url), 'utf8').split('\n').slice(1, 30).join('\n'));
      process.exit(0);
    } else rest.push(arg);
  }
  if (shards !== undefined) count(shards, 'shard count');
  shards ??= defaultShards();
  chunks ??= shards === 1 ? 1 : 3 * shards;
  return { shards, chunks: Math.max(chunks, shards), video, summary, rest };
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

function findJar() {
  if (process.env.E2E_JAR) {
    const jar = path.resolve(process.env.E2E_JAR);
    if (!fs.existsSync(jar)) throw new Error(`E2E_JAR does not exist: ${jar}`);
    return jar;
  }
  const target = path.join(SERVER_DIR, 'target');
  const jar = (fs.existsSync(target) ? fs.readdirSync(target) : [])
    .filter((name) => /^foodsharing-platform-.*\.jar$/.test(name) && !name.endsWith('-plain.jar'))
    .map((name) => path.join(target, name))
    .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs)[0];
  if (!jar) throw new Error('No jar in server/target. Build it with `cd server && ./mvnw -q -DskipTests package`.');
  return jar;
}

function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      server.close(() => resolve(address.port));
    });
  });
}

/** Starts a jar like global-setup.ts does, plus its own upload folder, and waits for readiness. */
async function startJar(jar, slot, env) {
  const dir = path.join(INSTANCES_DIR, `slot-${slot}`);
  fs.mkdirSync(dir, { recursive: true });
  const port = await freePort();
  const logFile = path.join(dir, 'server.log');
  const log = fs.openSync(logFile, 'w');
  const javaOpts = (process.env.E2E_JAVA_OPTS ?? '').split(' ').filter(Boolean);
  const child = spawn(
    'java',
    [
      ...javaOpts,
      '-jar',
      jar,
      '--spring.profiles.active=e2e',
      `--server.port=${port}`,
      // see global-setup.ts: parallel jars must not share app.uploads.dir
      `--app.uploads.dir=${path.join(dir, 'uploads')}`,
    ],
    { cwd: SERVER_DIR, env: { ...env, E2E_PORT: String(port) }, stdio: ['ignore', log, log] },
  );
  const jarState = { slot, child, port, logFile, baseURL: `http://localhost:${port}`, exited: undefined };
  child.on('exit', (code) => {
    jarState.exited = code ?? 1;
  });
  child.on('error', (error) => {
    jarState.exited = 1;
    jarState.error = error;
  });
  const deadline = Date.now() + STARTUP_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (jarState.exited !== undefined) {
      const tail = fs.readFileSync(logFile, 'utf8').split('\n').slice(-30).join('\n');
      throw new Error(`The jar of slot ${slot} exited during startup (is a JDK 21 on the PATH?)\n${jarState.error?.message ?? tail}`);
    }
    try {
      const response = await fetch(`${jarState.baseURL}/actuator/health/readiness`, { signal: AbortSignal.timeout(2_000) });
      const body = await response.json().catch(() => ({}));
      if (response.ok && body.status === 'UP') return jarState;
    } catch {
      // not up yet
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  child.kill('SIGKILL');
  throw new Error(`The jar of slot ${slot} was not ready within ${STARTUP_TIMEOUT_MS / 1000}s (log: ${logFile})`);
}

/** Runs chunk k of K against a slot's jar; resolves with the chunk's state once the process exited. */
function runChunk(chunk, chunks, jarState, rest, env) {
  const instance = `chunk-${chunk}`;
  const dir = path.join(INSTANCES_DIR, instance);
  fs.mkdirSync(dir, { recursive: true });
  const out = fs.createWriteStream(path.join(dir, 'output.log'));
  const state = { chunk, slot: jarState.slot, total: undefined, done: 0, failed: 0, exitCode: undefined, child: undefined };
  const child = spawn(process.execPath, [CLI, 'test', `--shard=${chunk}/${chunks}`, '--reporter=blob,list', ...rest], {
    cwd: E2E_DIR,
    env: {
      ...env,
      E2E_INSTANCE: instance,
      E2E_BASE_URL: jarState.baseURL,
      PLAYWRIGHT_BLOB_OUTPUT_FILE: path.join(BLOB_DIR, `report-${chunk}.zip`),
      FORCE_COLOR: '0',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  state.child = child;
  let buffer = '';
  const onData = (data) => {
    out.write(data);
    buffer += data.toString('utf8');
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? '';
    for (const line of lines) {
      const running = /Running (\d+) tests? using/.exec(line);
      if (running) state.total = Number(running[1]);
      // list reporter marks; Windows consoles get ok/x instead of ✓/✘
      const mark = /^\s+(✓|ok|✘|x|-|±|°)\s+\d+\s+\[/.exec(line);
      if (!mark) continue;
      state.done++;
      if (mark[1] === '✘' || mark[1] === 'x') {
        state.failed++;
        console.log(`[chunk ${chunk}] ${line.trim()}`);
      }
    }
  };
  child.stdout.on('data', onData);
  child.stderr.on('data', onData);
  state.exit = new Promise((resolve) => {
    child.on('exit', (code, signal) => {
      state.exitCode = code ?? (signal ? 1 : 0);
      out.end();
      resolve(state);
    });
  });
  return state;
}

async function run({ shards, chunks, video, rest }) {
  if (!fs.existsSync(CLI)) throw new Error(`${CLI} is missing; run \`npm ci\` in e2e/ first.`);
  if (process.env.E2E_BASE_URL) throw new Error('E2E_BASE_URL is set: all shards would share one database. Unset it.');
  const env = { ...process.env };
  delete env.E2E_PORT; // each slot picks its own free port
  if (!video) env.E2E_VIDEO = 'off';
  const jar = findJar();

  fs.rmSync(INSTANCES_DIR, { recursive: true, force: true });
  fs.rmSync(BLOB_DIR, { recursive: true, force: true });
  fs.rmSync(RESULTS_JSON, { force: true });
  fs.mkdirSync(BLOB_DIR, { recursive: true });

  const started = Date.now();
  console.log(
    `[sharded] ${shards} slot(s), ${chunks} chunk(s), ${os.cpus().length} CPUs, ${(os.freemem() / GB).toFixed(1)} of ` +
      `${(os.totalmem() / GB).toFixed(1)} GB free, video ${video ? 'on failure' : 'off'}; playwright test ${rest.join(' ')}`,
  );

  const jars = [];
  const chunkStates = [];
  // Last chunk first: the feature specs (alphabetically after a11y/) are the slow end of the suite.
  const queue = Array.from({ length: chunks }, (_, i) => chunks - i);
  let stopping = false;
  const jarFailures = [];

  const slotLoop = async (slot) => {
    let jarState;
    try {
      jarState = await startJar(jar, slot, env);
    } catch (error) {
      jarFailures.push(error.message);
      console.log(`[slot ${slot}] ${error.message}`);
      return;
    }
    jars.push(jarState);
    console.log(`[slot ${slot}] ${path.basename(jar)} is up at ${jarState.baseURL} after ${fmt(Date.now() - started)}`);
    while (queue.length && !stopping) {
      if (jarState.exited !== undefined) {
        jarFailures.push(`the jar of slot ${slot} died (log: ${jarState.logFile})`);
        console.log(`[slot ${slot}] the jar died; its remaining chunks go to the other slots`);
        return;
      }
      const state = runChunk(queue.shift(), chunks, jarState, rest, env);
      chunkStates.push(state);
      await state.exit;
    }
  };

  const killAll = () => {
    for (const s of chunkStates) if (s.exitCode === undefined) s.child.kill();
    for (const j of jars) if (j.exited === undefined) j.child.kill('SIGKILL');
  };
  const stop = async (signal) => {
    if (stopping) return;
    stopping = true;
    console.log(`\n[sharded] ${signal}: stopping`);
    await Promise.race([Promise.all(chunkStates.map((s) => s.exit)), new Promise((r) => setTimeout(r, 10_000))]);
    killAll();
    process.exit(130);
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
  process.on('exit', killAll);

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
    const finished = chunkStates.filter((s) => s.exitCode !== undefined).length;
    const done = chunkStates.reduce((sum, s) => sum + s.done, 0);
    const failed = chunkStates.reduce((sum, s) => sum + s.failed, 0);
    const running = chunkStates.filter((s) => s.exitCode === undefined).map((s) => `${s.chunk}:${s.done}/${s.total ?? '?'}`);
    console.log(
      `[sharded] ${fmt(Date.now() - started)}  ${done} tests done${failed ? ` (${failed} failed)` : ''}, ` +
        `chunks ${finished}/${chunks} finished, running ${running.join(' ') || '-'}  ` +
        `cpu ${(busy * 100).toFixed(0)}%  free ${(os.freemem() / GB).toFixed(1)} GB`,
    );
  }, PROGRESS_EVERY_MS);

  await Promise.all(Array.from({ length: shards }, (_, i) => slotLoop(i + 1)));
  clearInterval(progress);
  clearInterval(sampler);
  killAll();
  const wall = Date.now() - started;

  let ok = jarFailures.length === 0 && queue.length === 0;
  if (queue.length) console.log(`[sharded] ${queue.length} chunk(s) never ran: ${queue.join(', ')}`);
  for (const s of chunkStates.sort((a, b) => a.chunk - b.chunk)) {
    if (s.exitCode !== 0) {
      ok = false;
      console.log(`[sharded] chunk ${s.chunk} (slot ${s.slot}): exit ${s.exitCode} (log: ${path.relative(E2E_DIR, path.join(INSTANCES_DIR, `chunk-${s.chunk}`, 'output.log'))})`);
    }
  }

  const blobs = fs.readdirSync(BLOB_DIR).filter((f) => f.endsWith('.zip'));
  if (blobs.length === 0) {
    console.log('[sharded] no blob report was written; see the logs above.');
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
    `[sharded] wall time ${fmt(wall)} with ${shards} slot(s) / ${chunks} chunk(s); peak CPU ${(maxCpu * 100).toFixed(0)}% ` +
      `(per ${PROGRESS_EVERY_MS / 1000}s), lowest free memory ${(minFree / GB).toFixed(1)} GB`,
  );
  console.log(`[sharded] report: ${path.relative(process.cwd(), HTML_DIR)}  results: ${path.relative(process.cwd(), RESULTS_JSON)}`);
  if (blobs.length !== chunks) {
    console.log(`[sharded] only ${blobs.length} of ${chunks} blob reports were written`);
    ok = false;
  }
  return ok ? 0 : 1;
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
