import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { debounceTime, filter, map } from 'rxjs';

import { Conversation, ConversationKind, ConversationPage, MessagingService, RealtimeEventType } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { NewConversationDialogComponent } from '../components/new-conversation-dialog.component';
import { bodySegments } from '../message-body';
import { MessagingStore } from '../messaging.resource';

/**
 * /messages (N1): conversation list (latest first, unread badge, preview, relative time, client-side search, "New
 * message") beside the open conversation (child route). On narrow screens only one pane shows: the list at
 * /messages, the conversation at /messages/:id. The list refreshes on realtime messaging events and reconnects.
 */
@Component({
  selector: 'fs-messages-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    FormsModule,
    InputText,
    NewConversationDialogComponent,
    PageHeaderComponent,
    RouterLink,
    RouterOutlet,
    TPipe,
    UserAvatarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.has-open]': 'openId() !== null' },
  template: `
    <fs-page-header class="title" [title]="'messaging.title' | t" icon="pi pi-comments">
      <p-button
        [label]="'messaging.new.button' | t"
        icon="pi pi-plus"
        (onClick)="composeOpen.set(true)"
        data-testid="messaging.list.new"
      />
    </fs-page-header>
    <div class="panes">
      <section class="list fs-card" [attr.aria-label]="'messaging.title' | t">
        <label class="sr-only" for="conversation-search">{{ 'messaging.search' | t }}</label>
        <input
          pInputText
          id="conversation-search"
          type="search"
          [placeholder]="'messaging.search' | t"
          [ngModel]="query()"
          (ngModelChange)="query.set($event)"
          data-testid="messaging.list.search"
        />
        <fs-data-state
          [state]="conversations"
          skeleton="list"
          [emptyTitle]="'messaging.emptyTitle' | t"
          [emptyText]="'messaging.emptyText' | t"
          emptyIcon="pi pi-comments"
          data-testid="messaging.list.state"
        >
          <ng-template [fsData]="conversations" let-page>
            <ul class="items">
              @for (c of filtered(page); track c.id) {
                <li>
                  <a
                    class="item"
                    [class.item--active]="c.id === openId()"
                    [class.item--unread]="c.unreadCount > 0"
                    [routerLink]="['/messages', c.id]"
                    [attr.aria-current]="c.id === openId() ? 'page' : null"
                    data-testid="messaging.list.item"
                    [attr.data-conversation-id]="c.id"
                    [attr.data-name]="c.name"
                  >
                    @if (partner(c); as p) {
                      <fs-user-avatar [name]="p.displayName" [avatarUrl]="p.avatarUrl" size="md" />
                    } @else {
                      <span class="item__icon" aria-hidden="true"><i [class]="icon(c.kind)"></i></span>
                    }
                    <span class="item__text">
                      <span class="item__name">{{ c.name }}</span>
                      <span class="item__preview" data-testid="messaging.list.preview">{{ preview(c) }}</span>
                    </span>
                    <span class="item__meta">
                      @if (c.lastMessageAt) {
                        <time [attr.datetime]="c.lastMessageAt">{{ c.lastMessageAt | fsDate: 'relative' }}</time>
                      }
                      @if (c.unreadCount > 0) {
                        <span class="badge" data-testid="messaging.list.unread" [attr.aria-label]="'messaging.unread' | t: { count: c.unreadCount }">{{ c.unreadCount }}</span>
                      }
                    </span>
                  </a>
                </li>
              } @empty {
                <li class="muted">{{ 'messaging.noMatch' | t }}</li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </section>
      <section class="open fs-card">
        <router-outlet />
      </section>
    </div>
    <fs-new-conversation-dialog [(visible)]="composeOpen" (created)="opened($event)" />
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 72rem);
      margin: 0 auto;
    }
    .panes {
      display: grid;
      grid-template-columns: minmax(16rem, 22rem) 1fr;
      gap: 1rem;
      align-items: start;
    }
    .list,
    .open {
      display: grid;
      gap: 0.5rem;
      min-width: 0;
      padding: 0.75rem;
    }
    .open {
      display: flex;
      flex-direction: column;
    }
    @media (max-width: 47.99rem) {
      .panes {
        grid-template-columns: 1fr;
      }
      :host(.has-open) .list,
      :host(.has-open) .title p-button,
      :host(:not(.has-open)) .open {
        display: none;
      }
      /* an open conversation fills the phone screen; the page title stays for screen readers (the h1 of the page) */
      :host(.has-open) .title {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip-path: inset(50%);
        white-space: nowrap;
      }
    }
    .items {
      display: grid;
      gap: 0.15rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .item {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      min-height: 3.25rem;
      padding: 0.45rem 0.5rem;
      border-radius: var(--fs-radius-sm);
      color: var(--fs-text);
      text-decoration: none;
    }
    .item:hover,
    .item--active {
      background: var(--fs-surface-muted, color-mix(in srgb, var(--fs-text) 6%, transparent));
    }
    .item__icon {
      display: grid;
      flex: none;
      place-items: center;
      width: 2.5rem;
      height: 2.5rem;
      border-radius: 50%;
      background: color-mix(in srgb, var(--fs-primary) 14%, var(--fs-surface));
      color: var(--fs-primary-strong);
    }
    .item__text {
      display: grid;
      flex: 1;
      min-width: 0;
    }
    .item__name,
    .item__preview {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .item__name {
      font-weight: 600;
    }
    .item--unread .item__name,
    .item--unread .item__preview {
      font-weight: 700;
    }
    .item__preview {
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
    .item__meta {
      display: grid;
      justify-items: end;
      gap: 0.2rem;
      color: var(--fs-text-muted);
      font-size: 0.75rem;
    }
    .badge {
      min-width: 1.4rem;
      padding: 0.05rem 0.4rem;
      border-radius: 999px;
      background: var(--fs-primary-strong);
      color: var(--fs-on-primary, #fff);
      font-weight: 700;
      text-align: center;
    }
    .muted {
      color: var(--fs-text-muted);
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class MessagesPageComponent {
  private readonly api = inject(MessagingService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(SessionService);
  private readonly realtime = inject(RealtimeService);

  protected readonly query = signal('');
  protected readonly composeOpen = signal(this.route.snapshot.data['compose'] === true);

  protected readonly openId = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.currentOpenId()),
    ),
    { initialValue: this.currentOpenId() },
  );

  protected readonly conversations = loadResource<ConversationPage>({
    loader: () => this.api.listConversations({ size: 100 }),
    isEmpty: (page) => page.items.length === 0,
    keepDataOnError: true,
  });

  private readonly me = computed(() => this.session.user()?.id);

  constructor() {
    const store = inject(MessagingStore);
    let seen = store.listVersion();
    effect(() => {
      const version = store.listVersion();
      if (version !== seen) {
        seen = version;
        untracked(() => this.conversations.reload());
      }
    });
    this.realtime
      .on(
        RealtimeEventType.ConversationMessage,
        RealtimeEventType.ConversationRead,
        RealtimeEventType.ConversationUpdated,
      )
      .pipe(debounceTime(300), takeUntilDestroyed())
      .subscribe(() => this.conversations.refresh());
    const unregister = this.realtime.onReconnect(() => this.conversations.refresh());
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        if (this.route.snapshot.firstChild?.data['compose'] === true) {
          this.composeOpen.set(true);
        }
      });
    if (this.route.snapshot.firstChild?.data['compose'] === true) {
      this.composeOpen.set(true);
    }
    inject(DestroyRef).onDestroy(unregister);
  }

  protected filtered(page: ConversationPage): Conversation[] {
    const needle = this.query().trim().toLowerCase();
    return needle ? page.items.filter((c) => c.name.toLowerCase().includes(needle)) : page.items;
  }

  /** The other person of a direct conversation (for the avatar). */
  protected partner(c: Conversation) {
    return c.kind === ConversationKind.Direct ? c.members.find((user) => user.id !== this.me()) : undefined;
  }

  protected icon(kind: ConversationKind): string {
    return LIST_ICONS[kind];
  }

  /** Last message as plain text (mention tokens as "@Name"). */
  protected preview(c: Conversation): string {
    const body = c.lastMessage?.body;
    if (!body) {
      return '';
    }
    return bodySegments(body)
      .map((s) => (s.kind === 'text' ? s.text : s.kind === 'mention' ? `@${s.name}` : s.href))
      .join('')
      .replace(/\s+/g, ' ');
  }

  protected opened(conversation: Conversation): void {
    this.conversations.reload();
    void this.router.navigate(['/messages', conversation.id]);
  }

  private currentOpenId(): string | null {
    return this.route.snapshot.firstChild?.paramMap.get('conversationId') ?? null;
  }
}

const LIST_ICONS: Record<ConversationKind, string> = {
  [ConversationKind.Direct]: 'pi pi-user',
  [ConversationKind.Group]: 'pi pi-users',
  [ConversationKind.StoreTeam]: 'pi pi-shop',
  [ConversationKind.StoreJumper]: 'pi pi-clock',
  [ConversationKind.Region]: 'pi pi-map',
};
