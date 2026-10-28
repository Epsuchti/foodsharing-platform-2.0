import { expect } from '@playwright/test';
import type { Role } from '../support/env';
import type { ApiClient, DevApi } from './api';

/** One captured notification of `GET /api/dev/notifications` (bells and mails from the database, pushes/Telegram in memory). */
export interface DevNotification {
  id: string;
  userId: string;
  type: string;
  channel: 'BELL' | 'MAIL' | 'PUSH' | 'TELEGRAM';
  params: Record<string, string>;
  link?: string;
  identifier?: string;
  body?: string;
  target?: string;
  createdAt: string;
}

export interface BellDto {
  id: string;
  type: string;
  titleKey: string;
  params: Record<string, string>;
  link?: string;
  identifier?: string;
  readAt?: string;
  createdAt: string;
}

/**
 * M4 notification helpers over the dev endpoints (`/api/dev/notifications`, `/api/dev/jobs/{name}/run`,
 * `/api/dev/telegram/incoming`, `/api/dev/realtime/sessions`).
 */
export class NotificationHelpers {
  constructor(
    private readonly api: ApiClient,
    private readonly dev: DevApi,
  ) {}

  private async captured(role: Role, channel: DevNotification['channel'], type?: string): Promise<DevNotification[]> {
    const user = await this.dev.user(role);
    const query = new URLSearchParams({ userId: user.id, channel });
    if (type) query.set('type', type);
    return this.api.getJson<DevNotification[]>(`/api/dev/notifications?${query}`);
  }

  /** Bells of a seed user, newest first. */
  bells(role: Role, type?: string): Promise<DevNotification[]> {
    return this.captured(role, 'BELL', type);
  }

  /** Notification mails of a seed user. */
  mails(role: Role, type?: string): Promise<DevNotification[]> {
    return this.captured(role, 'MAIL', type);
  }

  /** Web pushes captured for a seed user (log-only locally). */
  pushes(role: Role, type?: string): Promise<DevNotification[]> {
    return this.captured(role, 'PUSH', type);
  }

  /** Telegram messages captured for a seed user. */
  telegram(role: Role, type?: string): Promise<DevNotification[]> {
    return this.captured(role, 'TELEGRAM', type);
  }

  /** Runs a scheduled job now (`onboarding-nudges`, `pickup-reminder`, …). */
  async runJob(name: string): Promise<void> {
    const response = await this.api.fetch('POST', `/api/dev/jobs/${name}/run`);
    expect(response.status(), await response.text()).toBe(204);
  }

  /** Simulates an incoming Telegram message `/start <token>` from a chat. */
  async simulateTelegramStart(token: string, chatId = '424242'): Promise<void> {
    const response = await this.api.fetch('POST', '/api/dev/telegram/incoming', { chatId, text: `/start ${token}` });
    expect(response.status(), await response.text()).toBe(204);
  }

  /** Forgets captured pushes and Telegram messages. */
  async clearNotifications(): Promise<void> {
    const response = await this.api.fetch('DELETE', '/api/dev/notifications');
    expect(response.status(), await response.text()).toBe(204);
  }

  /** Waits until a seed user has an open realtime (STOMP) session. */
  async waitForRealtime(role: Role): Promise<void> {
    const user = await this.dev.user(role);
    await expect
      .poll(async () => (await this.api.getJson<{ userIds: string[] }>('/api/dev/realtime/sessions')).userIds, { timeout: 15_000 })
      .toContain(user.id);
  }
}
