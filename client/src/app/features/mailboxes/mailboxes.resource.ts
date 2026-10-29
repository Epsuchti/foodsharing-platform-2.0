import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import DOMPurify from 'dompurify';
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

/**
 * Mail HTML for the sandboxed iframe: DOMPurify on top of the server-side jsoup pass (A35, risk 5); remote images
 * are dropped (tracking pixels), links open in a new tab.
 */
export function sanitizeMailHtml(html: string): string {
  const clean = DOMPurify.sanitize(html, {
    FORBID_TAGS: ['style', 'script', 'iframe', 'object', 'embed', 'form', 'input', 'link', 'meta', 'base'],
    FORBID_ATTR: ['srcset'],
  });
  const doc = new DOMParser().parseFromString(clean, 'text/html');
  doc.querySelectorAll('img').forEach((img) => {
    const src = img.getAttribute('src') ?? '';
    if (!src.startsWith('data:') && !src.startsWith('cid:')) {
      img.remove();
    }
  });
  doc.querySelectorAll('a[href]').forEach((a) => {
    a.setAttribute('target', '_blank');
    a.setAttribute('rel', 'noopener noreferrer nofollow');
  });
  const style =
    '<style>body{font:15px/1.5 system-ui,sans-serif;margin:0;padding:0.5rem;color:#1f2933;background:#fff;overflow-wrap:anywhere}img{max-width:100%}</style>';
  return `<!doctype html><html><head><meta charset="utf-8">${style}</head><body>${doc.body.innerHTML}</body></html>`;
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
