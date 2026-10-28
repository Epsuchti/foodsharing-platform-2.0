import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Observable } from 'rxjs';

import { ChatMessage, MessagingService, RealtimeEventType } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { RealtimeService } from '../../core/realtime/realtime.service';

/** Payload of a CONVERSATION_MESSAGE realtime event (A32/A33). */
export interface ConversationMessageEvent extends ChatMessage {
  conversationId: string;
}

/**
 * Messaging state shared by the pages (A33): unread conversations count (badge), refreshed on (re)connect and on
 * CONVERSATION_MESSAGE / CONVERSATION_READ events; the id of the conversation on screen; loaders for paging/refetch.
 */
@Injectable({ providedIn: 'root' })
export class MessagingStore {
  private readonly api = inject(MessagingService);
  private readonly session = inject(SessionService);
  private readonly realtime = inject(RealtimeService);

  /** Conversations with unread messages (0 while logged out or not yet loaded). */
  readonly unreadConversations = signal(0);
  /** Conversation currently on screen (it is marked read as messages arrive). */
  readonly openConversationId = signal<string | null>(null);
  /** Bumped after local changes (leave, rename, members) so the list refetches without waiting for an event. */
  readonly listVersion = signal(0);

  constructor() {
    effect(() => {
      const authenticated = this.session.isAuthenticated();
      this.realtime.connection();
      untracked(() => (authenticated ? this.refreshUnread() : this.unreadConversations.set(0)));
    });
    this.realtime
      .on(RealtimeEventType.ConversationMessage, RealtimeEventType.ConversationRead)
      .subscribe(() => this.refreshUnread());
  }

  changed(): void {
    this.listVersion.update((version) => version + 1);
  }

  refreshUnread(): void {
    this.api.getConversationUnreadCount().subscribe({
      next: (count) => this.unreadConversations.set(count.conversations),
      error: () => undefined,
    });
  }

  /** Older messages (ascending) before `beforeSeq`. */
  older(conversationId: string, beforeSeq: number, limit = 30): Observable<ChatMessage[]> {
    return this.api.listMessages({ conversationId, beforeSeq, limit });
  }

  /** Messages after `afterSeq` (gap fill after a missed event or a reconnect). */
  after(conversationId: string, afterSeq: number): Observable<ChatMessage[]> {
    return this.api.listMessages({ conversationId, afterSeq, limit: 100 });
  }

  markRead(conversationId: string, lastReadSeq: number): void {
    this.api
      .markConversationRead({ conversationId, conversationReadUpdate: { lastReadSeq } })
      .subscribe({ next: () => this.refreshUnread(), error: () => undefined });
  }
}
