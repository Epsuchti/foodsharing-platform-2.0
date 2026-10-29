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

/**
 * A delivery status notification for `recipient` (a member's address) reaching the `zuerich` mailbox: the server
 * flags the address as bounced (A18, `UserProfile.emailBouncedAt`). Each call is a new mail (own Message-ID).
 */
export async function injectBounce(client: ApiClient, recipient: string): Promise<void> {
  await injectIncoming(client, {
    to: 'zuerich@local.test',
    subject: 'Undelivered Mail Returned to Sender',
    raw: [
      'From: MAILER-DAEMON@mx.example.ch',
      'To: zuerich@local.test',
      'Subject: Undelivered Mail Returned to Sender',
      `Message-ID: <e2e-dsn-${Date.now()}-${Math.random().toString(36).slice(2)}@mx.example.ch>`,
      'Date: Mon, 2 Nov 2026 08:00:00 +0100',
      'MIME-Version: 1.0',
      'Content-Type: multipart/report; report-type=delivery-status; boundary="B"',
      '',
      '--B',
      'Content-Type: text/plain',
      '',
      'Your message could not be delivered.',
      '--B',
      'Content-Type: message/delivery-status',
      '',
      `Final-Recipient: rfc822; ${recipient}`,
      'Action: failed',
      'Status: 5.1.1',
      '--B--',
      '',
    ].join('\r\n'),
  });
}

/** Mail rows of the open folder. */
export function mailRow(page: Page, subject: string) {
  return page.locator(`[data-testid="mailboxes.list.mail"][data-subject="${subject}"]`);
}
