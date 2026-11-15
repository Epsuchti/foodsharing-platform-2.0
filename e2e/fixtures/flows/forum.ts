import { expect, type Locator, type Page } from '@playwright/test';
import type { ApiClient } from '../api';
import { regionId } from '../regions';

/** Seed forum ids (docs/seed-ids.md module 0043): zuerich-stadt is moderated, bot moderates it. */
export const FORUM = {
  zuerich: regionId('zuerich-stadt'),
  kochgruppe: '00000011-0001-4000-8000-000000000033',
  thread: (n: number): string => `00000043-0001-4000-8000-${String(n).padStart(12, '0')}`,
  post: (n: number): string => `00000043-0002-4000-8000-${String(n).padStart(12, '0')}`,
} as const;

/** Replies through the API as the client's user; returns the new post id. */
export async function replyVia(api: ApiClient, threadId: string, body: string): Promise<string> {
  const response = await api.post(`/api/forum/threads/${threadId}/posts`, { body });
  expect(response.status(), await response.text()).toBe(201);
  return ((await response.json()) as { id: string }).id;
}

/** The card of one post on a thread page. */
export function postCard(page: Page, postId: string): Locator {
  return page.locator(`[data-testid="forum.thread.post"][data-post-id="${postId}"]`);
}

/** Accepts the open confirm dialog (language independent). */
export async function acceptConfirm(page: Page): Promise<void> {
  await page.locator('.p-confirmdialog .p-confirmdialog-accept-button').click();
}
