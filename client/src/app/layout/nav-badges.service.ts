import { Injectable, Injector, Signal, effect, inject, signal, untracked } from '@angular/core';

import { SessionService } from '../core/auth/session.service';
import { StartupService } from '../core/startup/startup.service';
import type { NavBadge } from './nav.config';

/**
 * Unread counts for `NavItem.badge` (conversations with unread messages, unread inbox mails). The stores live in
 * their feature chunks and are imported on demand once the first page is shown and the user is logged in
 * (decision 84: nothing of this runs before `StartupService.pageShown`).
 */
@Injectable({ providedIn: 'root' })
export class NavBadgesService {
  private readonly injector = inject(Injector);
  private readonly session = inject(SessionService);
  private readonly startup = inject(StartupService);
  private readonly sources = signal<Partial<Record<NavBadge, Signal<number>>>>({});
  private loading = false;

  constructor() {
    effect(() => {
      if (this.session.isAuthenticated() && this.startup.pageShown()) untracked(() => void this.load());
    });
  }

  /** Current count of a badge (0 until its store is loaded). */
  count(badge: NavBadge): number {
    return this.sources()[badge]?.() ?? 0;
  }

  private async load(): Promise<void> {
    if (this.loading) return;
    this.loading = true;
    const [messaging, mailboxes] = await Promise.all([
      import('../features/messaging/messaging.resource'),
      import('../features/mailboxes/mailboxes.resource'),
    ]);
    this.sources.set({
      messages: this.injector.get(messaging.MessagingStore).unreadConversations,
      mailboxes: this.injector.get(mailboxes.MailboxStore).unreadMails,
    });
  }
}
