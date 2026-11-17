import { clearServerState, readServerState } from './support/env';

/** Stops the jars started by global-setup.ts (nothing to do when E2E_BASE_URL was reused). */
export default async function globalTeardown(): Promise<void> {
  const state = readServerState();
  clearServerState();
  await Promise.all((state?.jars ?? []).map((jar) => (jar.pid ? stop(jar.pid) : undefined)));
}

async function stop(pid: number): Promise<void> {
  if (!isAlive(pid)) return;
  process.kill(pid, 'SIGTERM');
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline && isAlive(pid)) {
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  if (isAlive(pid)) process.kill(pid, 'SIGKILL');
}

function isAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}
