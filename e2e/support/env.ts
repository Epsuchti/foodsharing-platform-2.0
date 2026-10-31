import fs from 'node:fs';
import path from 'node:path';

/** Absolute paths used by the config, global setup and fixtures. */
export const E2E_DIR = path.resolve(__dirname, '..');
export const REPO_DIR = path.resolve(E2E_DIR, '..');
export const SERVER_DIR = path.join(REPO_DIR, 'server');
export const OPENAPI_DIR = path.join(SERVER_DIR, 'src/main/resources/openapi');
/**
 * Name of a parallel Playwright run in the same checkout (`E2E_INSTANCE=shard-2`, set by scripts/run-sharded.mjs and
 * scripts/screenshots.sh --shards). Each instance gets its own server state file, auth states, test-results folder
 * (server log, artifacts) and upload directory, so shards never share files. Unset: the classic single-run paths.
 */
export const INSTANCE = (process.env.E2E_INSTANCE ?? '').replace(/[^\w.-]/g, '');
const perInstance = (dir: string): string => (INSTANCE ? path.join(dir, INSTANCE) : dir);
export const AUTH_DIR = perInstance(path.join(E2E_DIR, '.auth'));
export const STATE_DIR = perInstance(path.join(E2E_DIR, '.state'));
export const SERVER_STATE_FILE = path.join(STATE_DIR, 'server.json');
export const RESULTS_DIR = INSTANCE
  ? path.join(E2E_DIR, 'test-results', 'instances', INSTANCE)
  : path.join(E2E_DIR, 'test-results');

/** Seed time anchor (docs/conventions.md A6). The e2e profile fixes the server clock to it. */
export const SEED_NOW = '2026-11-02T09:00:00Z';

/** Seed users of plan §7.3 (`<role>@local`), one per role and journey stage. */
export const ROLES = [
  'newbie',
  'profile',
  'quiz',
  'einab1',
  'pending',
  'foodsaver',
  'jumper',
  'sleepy',
  'bieb',
  'guide',
  'bot',
  'welcome',
  'reports',
  'orga',
] as const;
export type Role = (typeof ROLES)[number];

export const roleEmail = (role: Role): string => `${role}@local`;
export const authFile = (role: Role): string => path.join(AUTH_DIR, `${role}.json`);

export interface ServerState {
  baseURL: string;
  /** PID of the jar started by global setup; absent when an existing server is reused. */
  pid?: number;
  port?: number;
  logFile?: string;
}

export function readServerState(): ServerState | undefined {
  try {
    return JSON.parse(fs.readFileSync(SERVER_STATE_FILE, 'utf8')) as ServerState;
  } catch {
    return undefined;
  }
}

export function writeServerState(state: ServerState): void {
  fs.mkdirSync(STATE_DIR, { recursive: true });
  fs.writeFileSync(SERVER_STATE_FILE, JSON.stringify(state, null, 2));
}

export function clearServerState(): void {
  fs.rmSync(SERVER_STATE_FILE, { force: true });
}

/**
 * Base URL of the app under test: `E2E_BASE_URL` (reuse a running server, e.g. inside the screenshot container),
 * else the URL global setup wrote after starting the jar.
 */
export function resolveBaseURL(): string {
  const fromEnv = process.env.E2E_BASE_URL;
  if (fromEnv) return fromEnv.replace(/\/+$/, '');
  const state = readServerState();
  if (state?.baseURL) return state.baseURL;
  throw new Error(
    'No app under test: set E2E_BASE_URL or run through `npx playwright test` so global-setup.ts starts the jar.',
  );
}
