import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Observable } from 'rxjs';

import { MailboxesService, RealtimeEventType, Role, Upload, UploadsService } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { RealtimeService } from '../../core/realtime/realtime.service';

/** Shared mailbox state (A35): bumped on MAILBOX_MAIL events and reconnects so open lists refetch. */
@Injectable({ providedIn: 'root' })
export class MailboxStore {
  private readonly api = inject(MailboxesService);
  private readonly uploads = inject(UploadsService);
  /** Increments whenever mail may have arrived (realtime event or reconnect). */
  readonly version = signal(0);
  /** Unread inbox mails over all readable mailboxes (nav badge; 0 below STORE_MANAGER, A35 mayHaveMailbox). */
  readonly unreadMails = signal(0);

  constructor() {
    const realtime = inject(RealtimeService);
    const session = inject(SessionService);
    realtime.on(RealtimeEventType.MailboxMail).subscribe(() => this.bump());
    realtime.onReconnect(() => this.bump());
    effect(() => {
      this.version();
      const may = session.isAuthenticated() && session.hasRole(Role.StoreManager);
      untracked(() => (may ? this.refreshUnread() : this.unreadMails.set(0)));
    });
  }

  refreshUnread(): void {
    this.api.listMailboxes().subscribe({
      next: (list) => this.unreadMails.set(list.reduce((n, m) => n + m.unreadCount, 0)),
      error: () => undefined,
    });
  }

  bump(): void {
    this.version.update((v) => v + 1);
  }

  /** Uploads one attachment (`/api/uploads`, claimed by the mailbox on send; A34). */
  upload(file: File): Observable<Upload> {
    return this.uploads.createUpload({ file });
  }

  markUnread(mailId: string): Observable<unknown> {
    return this.api.moveOrFlagMail({ mailId, mailUpdate: { read: false } });
  }
}

/** Plain text → minimal HTML (compose body). */
export function textToHtml(text: string): string {
  const escape = (value: string) =>
    value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  return text
    .split(/\n{2,}/)
    .map((paragraph) => `<p>${escape(paragraph).replace(/\n/g, '<br>')}</p>`)
    .join('');
}

/** Splits "a@x.ch, b@y.ch; c@z.ch" into addresses; returns the invalid ones too. */
export function parseAddresses(value: string): { valid: string[]; invalid: string[] } {
  const parts = value
    .split(/[,;\s]+/)
    .map((part) => part.trim())
    .filter(Boolean);
  const pattern = /^[^\s@<>()]+@[^\s@<>()]+\.[^\s@<>()]{2,}$/;
  return { valid: parts.filter((p) => pattern.test(p)), invalid: parts.filter((p) => !pattern.test(p)) };
}

/** Human-readable size (B, kB, MB). */
export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} kB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
