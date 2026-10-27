import { clearServerState, readServerState } from './support/env';

/** Stops the jar started by global-setup.ts (nothing to do when E2E_BASE_URL was reused). */
export default async function globalTeardown(): Promise<void> {
  const state = readServerState();
  clearServerState();
  if (!state?.pid) return;
  if (!isAlive(state.pid)) return;
  process.kill(state.pid, 'SIGTERM');
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline && isAlive(state.pid)) {
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  if (isAlive(state.pid)) process.kill(state.pid, 'SIGKILL');
}

function isAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}
