import { expect, type Page } from '@playwright/test';

import type { ApiClient } from './api';

/** Seeded conversations (docs/seed-ids.md, messaging). */
export const CONVERSATIONS = {
  /** DIRECT foodsaver ↔ bieb: 12 messages, foodsaver read up to 10 (2 unread). */
  foodsaverBieb: '00000041-0001-4000-8000-000000000001',
  /** DIRECT foodsaver ↔ bot, read. */
  foodsaverBot: '00000041-0001-4000-8000-000000000002',
  /** GROUP "Kochgruppe Planung" (foodsaver, jumper, guide). */
  kochgruppe: '00000041-0001-4000-8000-000000000003',
  /** STORE_TEAM / STORE_JUMPER of S-01. */
  s01Team: '00000041-0001-4000-8000-000000000004',
  s01Jumper: '00000041-0001-4000-8000-000000000005',
} as const;

export interface ChatMessage {
  id: string;
  seq: number;
  senderId?: string;
  body: string;
  sentAt: string;
  clientKey?: string;
}

export interface ConversationSummary {
  id: string;
  kind: string;
  name: string;
  locked: boolean;
  unreadCount: number;
  lastSeq: number;
  members: { id: string; displayName: string }[];
}

/** Sends a message as the API client's user. */
export function sendMessage(client: ApiClient, conversationId: string, body: string): Promise<ChatMessage> {
  return client.postJson<ChatMessage>(`/api/conversations/${conversationId}/messages`, { body });
}

/** Finds or creates the conversation with exactly these members (plus the caller). */
export function lookupConversation(client: ApiClient, memberIds: string[]): Promise<ConversationSummary> {
  return client.postJson<ConversationSummary>('/api/conversations/lookup', { memberIds });
}

export function getConversation(client: ApiClient, conversationId: string): Promise<ConversationSummary> {
  return client.getJson<ConversationSummary>(`/api/conversations/${conversationId}`);
}

/** Waits until the page's realtime client is subscribed (dev hook `window.__fsRealtime`, A32). */
export async function waitForRealtime(page: Page): Promise<void> {
  await expect
    .poll(() => page.evaluate(() => (window as unknown as { __fsRealtime?: { connected(): boolean } }).__fsRealtime?.connected() ?? false), {
      timeout: 15_000,
    })
    .toBe(true);
}

/** Realtime event types the page received so far (dev hook). */
export function realtimeEventTypes(page: Page): Promise<string[]> {
  return page.evaluate(
    () => (window as unknown as { __fsRealtime?: { events: { type: string }[] } }).__fsRealtime?.events.map((e) => e.type) ?? [],
  );
}

/** Message bubbles of the open conversation. */
export function bubbles(page: Page) {
  return page.getByTestId('messaging.conversation.message');
}

/** List entry of a conversation on /messages. */
export function listItem(page: Page, conversationId: string) {
  return page.locator(`[data-testid="messaging.list.item"][data-conversation-id="${conversationId}"]`);
}
