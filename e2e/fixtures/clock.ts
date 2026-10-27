import type { Page } from '@playwright/test';
import { SEED_NOW } from '../support/env';
import type { DevApi } from './api';
import type { DevClockState } from './types';

/**
 * Server clock (`PUT /api/dev/clock`, a MutableClock in the e2e profile) and browser clock (`page.clock`).
 * The server clock is reset to SEED_NOW by every seed reset.
 */
export class ClockControl {
  constructor(private readonly dev: DevApi) {}

  /** Fix the server clock at `iso` (default SEED_NOW). */
  fixServer(iso: string = SEED_NOW): Promise<DevClockState> {
    return this.dev.setClock(iso);
  }

  /** Let the server clock run with system time again. */
  releaseServer(): Promise<DevClockState> {
    return this.dev.setClock();
  }

  /** `Date.now()`/`new Date()` in the page return `iso`; timers keep running. Call before `page.goto()`. */
  async fixBrowser(page: Page, iso: string = SEED_NOW): Promise<void> {
    await page.clock.setFixedTime(new Date(iso));
  }

  /** Server and browser at the same instant. */
  async fix(page: Page, iso: string = SEED_NOW): Promise<DevClockState> {
    const state = await this.fixServer(iso);
    await this.fixBrowser(page, iso);
    return state;
  }
}
