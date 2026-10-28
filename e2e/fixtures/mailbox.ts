import { expect, type Page } from '@playwright/test';

import type { ApiClient } from './api';

/** Seeded mailboxes (docs/seed-ids.md, mailboxes). bot is ambassador of zuerich-stadt and reads `zuerich`. */
export const MAILBOXES = {
  zuerich: '00000042-0001-4000-8000-000000000001',
  welcomeZuerich: '00000042-0001-4000-8000-000000000002',
  retoBaumann: '00000042-0001-4000-8000-000000000003',
} as const;

export interface IncomingMail {
  to: string;
  from?: string;
  subject: string;
  text?: string;
  html?: string;
  messageId?: string;
  /** Raw MIME override (bounces). */
  raw?: string;
}

/** Builds a simple RFC 822 message (text and/or HTML, multipart/alternative when both). */
export function rawMail(mail: IncomingMail): string {
  const id = mail.messageId ?? `<e2e-${Date.now()}-${Math.random().toString(36).slice(2)}@example.ch>`;
  const head = [
    `From: ${mail.from ?? 'Anna Beispiel <anna@example.ch>'}`,
    `To: ${mail.to}`,
    `Subject: ${mail.subject}`,
    `Message-ID: ${id}`,
    'Date: Mon, 2 Nov 2026 08:00:00 +0100',
    'MIME-Version: 1.0',
  ];
  if (mail.html && mail.text) {
    return [
      ...head,
      'Content-Type: multipart/alternative; boundary="b1"',
      '',
      '--b1',
      'Content-Type: text/plain; charset=utf-8',
      '',
      mail.text,
      '--b1',
      'Content-Type: text/html; charset=utf-8',
      '',
      mail.html,
      '--b1--',
      '',
    ].join('\r\n');
  }
  const type = mail.html ? 'text/html' : 'text/plain';
  return [...head, `Content-Type: ${type}; charset=utf-8`, '', mail.html ?? mail.text ?? '', ''].join('\r\n');
}

/** Processes an inbound mail through the dev endpoint (`/api/dev/mailbox/incoming`, local/e2e only). */
export async function injectIncoming(client: ApiClient, mail: IncomingMail): Promise<void> {
  const response = await client.post('/api/dev/mailbox/incoming', { rawMime: mail.raw ?? rawMail(mail) });
  expect(response.status(), await response.text()).toBeLessThan(300);
}

/** Mail rows of the open folder. */
export function mailRow(page: Page, subject: string) {
  return page.locator(`[data-testid="mailboxes.list.mail"][data-subject="${subject}"]`);
}
