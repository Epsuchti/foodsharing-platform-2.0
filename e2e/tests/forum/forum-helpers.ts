import { expect, type Page } from '@playwright/test';
import type { ApiClient } from '../../fixtures';

/** Seed forum ids (docs/seed-ids.md module 0043). */
export const KOCHGRUPPE = '00000011-0001-4000-8000-000000000033';
export const thread = (n: number): string => `00000043-0001-4000-8000-${String(n).padStart(12, '0')}`;
export const post = (n: number): string => `00000043-0002-4000-8000-${String(n).padStart(12, '0')}`;

/** Replies through the API as the client's user; returns the new post id. */
export async function replyVia(api: ApiClient, threadId: string, body: string): Promise<string> {
  const response = await api.post(`/api/forum/threads/${threadId}/posts`, { body });
  expect(response.status(), await response.text()).toBe(201);
  return ((await response.json()) as { id: string }).id;
}

export function postCard(page: Page, postId: string) {
  return page.locator(`[data-testid="forum.thread.post"][data-post-id="${postId}"]`);
}

/** Waits until success toasts have faded out (their enter animation trips the axe contrast check). */
export async function settleToasts(page: Page): Promise<void> {
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message')).toHaveCount(0, { timeout: 15_000 });
  await settleAnimations(page);
}

/** Waits until overlay/page transitions (popover fade-out, route enter) have finished before an axe check. */
export async function settleAnimations(page: Page): Promise<void> {
  await expect
    .poll(() => page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running').length))
    .toBe(0);
  await expect(page.locator('.p-popover, .p-dialog-mask')).toHaveCount(0);
}

/** Accepts the open confirm dialog (language independent). */
export async function acceptConfirm(page: Page): Promise<void> {
  await page.locator('.p-confirmdialog .p-confirmdialog-accept-button').click();
}
