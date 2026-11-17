import { spawn, spawnSync, type ChildProcess } from 'node:child_process';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import type { FullConfig } from '@playwright/test';
import { RESULTS_DIR, SERVER_DIR, WORKERS, baseURLsFromEnv, clearServerState, writeServerState, type JarState } from './support/env';

const HEALTH_TIMEOUT_MS = Number(process.env.E2E_STARTUP_TIMEOUT_MS ?? 120_000);

/**
 * Starts `E2E_WORKERS` (default 2) packaged jars with profile `e2e`, each on a free port with its own in-memory
 * database and log, or reuses `E2E_BASE_URL` (one URL per worker, space- or comma-separated), and waits for
 * `/actuator/health/readiness` (fallback `/actuator/health`). Worker slot `parallelIndex` uses jar `parallelIndex`
 * (fixtures/index.ts). The URLs reach the workers through `.state/server.json` (see support/env.ts);
 * global-teardown.ts stops the processes again.
 *
 * Env:
 * - `E2E_BASE_URL`  reuse running apps (no jar is started); one per worker.
 * - `E2E_WORKERS`   number of workers and jars (default 2).
 * - `E2E_JAR`       explicit jar path (default: newest `server/target/foodsharing-platform-*.jar`).
 * - `E2E_BUILD=1`   build the jar with `./mvnw -q -DskipTests package` first when none exists
 *                   (`E2E_BUILD=always` rebuilds every time).
 * - `E2E_PORT`      fixed port of the first jar instead of a free one (the others count up from it).
 * - `E2E_JAVA_OPTS` extra JVM options.
 * - `E2E_INSTANCE`  name of a parallel run in this checkout (own state and log; see support/env.ts).
 */
export default async function globalSetup(_config: FullConfig): Promise<void> {
  const reuse = baseURLsFromEnv();
  if (reuse.length) {
    await Promise.all(reuse.map((url) => waitForHealth(url, HEALTH_TIMEOUT_MS)));
    writeServerState({ jars: reuse.map((baseURL) => ({ baseURL })) });
    return;
  }

  clearServerState();
  const jar = findOrBuildJar();
  fs.mkdirSync(RESULTS_DIR, { recursive: true });
  const fixedPort = process.env.E2E_PORT ? Number(process.env.E2E_PORT) : undefined;
  const ports: number[] = [];
  for (let slot = 0; slot < WORKERS; slot++) ports.push(fixedPort !== undefined ? fixedPort + slot : await freePort(ports));
  const started = ports.map((port, slot) => startJar(jar, port, slot));
  writeServerState({ jars: started.map(({ state }) => state) });

  try {
    await Promise.all(started.map(({ ready }) => ready));
  } catch (error) {
    for (const { child } of started) child.kill('SIGKILL');
    clearServerState();
    throw error;
  }
  // Do not keep the runner alive because of the child handles; teardown kills them by PID.
  for (const { child } of started) child.unref();
  for (const { state } of started) {
    process.stdout.write(`[e2e] ${path.basename(jar)} is up at ${state.baseURL} (log: ${path.relative(process.cwd(), state.logFile!)})\n`);
  }
}

/** Starts one jar; `ready` resolves once it reports UP and rejects when it exits or times out. */
function startJar(jar: string, port: number, slot: number): { child: ChildProcess; state: JarState; ready: Promise<void> } {
  const baseURL = `http://localhost:${port}`;
  // slot 0 keeps the classic log name (server.log), so single-worker runs and docs stay as they were
  const logFile = path.join(RESULTS_DIR, slot === 0 ? 'server.log' : `server-${slot + 1}.log`);
  const log = fs.openSync(logFile, 'w');
  const javaOpts = (process.env.E2E_JAVA_OPTS ?? '').split(' ').filter(Boolean);
  const child = spawn('java', [...javaOpts, '-jar', jar, '--spring.profiles.active=e2e', `--server.port=${port}`], {
    cwd: SERVER_DIR,
    // application-e2e.properties derives app.base-url and the fake OIDC URIs from E2E_PORT.
    env: { ...process.env, E2E_PORT: String(port) },
    stdio: ['ignore', log, log],
  });
  let exited: number | null | undefined;
  child.on('exit', (code) => {
    exited = code;
  });
  if (!child.pid) throw new Error('Could not start java; is a JDK 21 on the PATH?');
  const ready = waitForHealth(baseURL, HEALTH_TIMEOUT_MS, () => {
    if (exited !== undefined) throw new Error(`The jar on port ${port} exited with code ${exited} during startup.\n${tail(logFile)}`);
  });
  return { child, state: { baseURL, pid: child.pid, port, logFile }, ready };
}

function findOrBuildJar(): string {
  if (process.env.E2E_JAR) {
    const jar = path.resolve(process.env.E2E_JAR);
    if (!fs.existsSync(jar)) throw new Error(`E2E_JAR does not exist: ${jar}`);
    return jar;
  }
  const build = process.env.E2E_BUILD;
  let jar = build === 'always' ? undefined : newestJar();
  if (!jar && (build === '1' || build === 'true' || build === 'always')) {
    process.stdout.write('[e2e] building the jar (./mvnw -q -DskipTests package)…\n');
    const result = spawnSync('./mvnw', ['-q', '-DskipTests', 'package'], { cwd: SERVER_DIR, stdio: 'inherit' });
    if (result.status !== 0) throw new Error(`Maven build failed with exit code ${result.status}`);
    jar = newestJar();
  }
  if (!jar) {
    throw new Error(
      'No jar in server/target. Build it with `cd server && ./mvnw -DskipTests package`, ' +
        'or run with E2E_BUILD=1, or point E2E_BASE_URL at a running app.',
    );
  }
  return jar;
}

function newestJar(): string | undefined {
  const target = path.join(SERVER_DIR, 'target');
  if (!fs.existsSync(target)) return undefined;
  return fs
    .readdirSync(target)
    .filter((name) => /^foodsharing-platform-.*\.jar$/.test(name) && !name.endsWith('-plain.jar'))
    .map((name) => path.join(target, name))
    .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs)[0];
}

/** A free port that is not one of `taken` (two jars must never get the same one). */
async function freePort(taken: number[] = []): Promise<number> {
  for (;;) {
    const port = await anyFreePort();
    if (!taken.includes(port)) return port;
  }
}

function anyFreePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : 0;
      server.close(() => resolve(port));
    });
  });
}

async function waitForHealth(baseURL: string, timeoutMs: number, check?: () => void): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  let last = 'no response';
  while (Date.now() < deadline) {
    check?.();
    try {
      // Readiness, not the aggregate: an unreachable external dependency (e.g. the SMTP health check) must not stop
      // the suite from starting; the smoke spec asserts the aggregate health separately.
      let response = await fetch(`${baseURL}/actuator/health/readiness`, { signal: AbortSignal.timeout(2_000) });
      if (response.status === 404) response = await fetch(`${baseURL}/actuator/health`, { signal: AbortSignal.timeout(2_000) });
      const body = (await response.json().catch(() => ({}))) as { status?: string };
      if (response.ok && body.status === 'UP') return;
      last = `HTTP ${response.status} ${JSON.stringify(body).slice(0, 500)}`;
    } catch (error) {
      last = (error as Error).message;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`${baseURL}/actuator/health was not UP within ${timeoutMs / 1000}s (last: ${last})`);
}

function tail(file: string, lines = 60): string {
  try {
    return fs.readFileSync(file, 'utf8').split('\n').slice(-lines).join('\n');
  } catch {
    return '';
  }
}
