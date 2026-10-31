import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import type { FullConfig } from '@playwright/test';
import { INSTANCE, RESULTS_DIR, SERVER_DIR, clearServerState, writeServerState } from './support/env';

const HEALTH_TIMEOUT_MS = Number(process.env.E2E_STARTUP_TIMEOUT_MS ?? 120_000);

/**
 * Starts the packaged jar with profile `e2e` on a free port (or reuses `E2E_BASE_URL`) and waits for
 * `/actuator/health/readiness` (fallback `/actuator/health`). The URL is handed to workers through `.state/server.json` (see support/env.ts);
 * global-teardown.ts stops the process again.
 *
 * Env:
 * - `E2E_BASE_URL`  reuse a running app (no jar is started).
 * - `E2E_JAR`       explicit jar path (default: newest `server/target/foodsharing-platform-*.jar`).
 * - `E2E_BUILD=1`   build the jar with `./mvnw -q -DskipTests package` first when none exists
 *                   (`E2E_BUILD=always` rebuilds every time).
 * - `E2E_PORT`      fixed port instead of a free one.
 * - `E2E_JAVA_OPTS` extra JVM options.
 * - `E2E_INSTANCE`  name of a parallel run in this checkout (own state, log, uploads; see support/env.ts).
 */
export default async function globalSetup(_config: FullConfig): Promise<void> {
  const reuse = process.env.E2E_BASE_URL?.replace(/\/+$/, '');
  if (reuse) {
    await waitForHealth(reuse, HEALTH_TIMEOUT_MS);
    writeServerState({ baseURL: reuse });
    return;
  }

  clearServerState();
  const jar = findOrBuildJar();
  const port = process.env.E2E_PORT ? Number(process.env.E2E_PORT) : await freePort();
  const baseURL = `http://localhost:${port}`;
  fs.mkdirSync(RESULTS_DIR, { recursive: true });
  const logFile = path.join(RESULTS_DIR, 'server.log');
  const log = fs.openSync(logFile, 'w');

  const javaOpts = (process.env.E2E_JAVA_OPTS ?? '').split(' ').filter(Boolean);
  // Parallel instances (E2E_INSTANCE) must not share app.uploads.dir: the seed reset rewrites the seed files and the
  // upload cleanup deletes variant files whose row is missing in *its own* database.
  const instanceArgs = INSTANCE ? [`--app.uploads.dir=${path.join(RESULTS_DIR, 'uploads')}`] : [];
  const child = spawn(
    'java',
    [...javaOpts, '-jar', jar, '--spring.profiles.active=e2e', `--server.port=${port}`, ...instanceArgs],
    {
      cwd: SERVER_DIR,
      // application-e2e.properties derives app.base-url and the fake OIDC URIs from E2E_PORT.
      env: { ...process.env, E2E_PORT: String(port) },
      stdio: ['ignore', log, log],
    },
  );
  let exited: number | null | undefined;
  child.on('exit', (code) => {
    exited = code;
  });
  if (!child.pid) throw new Error('Could not start java; is a JDK 21 on the PATH?');
  writeServerState({ baseURL, pid: child.pid, port, logFile });

  try {
    await waitForHealth(baseURL, HEALTH_TIMEOUT_MS, () => {
      if (exited !== undefined) {
        throw new Error(`The jar exited with code ${exited} during startup.\n${tail(logFile)}`);
      }
    });
  } catch (error) {
    child.kill('SIGKILL');
    clearServerState();
    throw error;
  }
  // Do not keep the runner alive because of the child handle; teardown kills it by PID.
  child.unref();
  process.stdout.write(`[e2e] ${path.basename(jar)} is up at ${baseURL} (log: ${path.relative(process.cwd(), logFile)})\n`);
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

function freePort(): Promise<number> {
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
