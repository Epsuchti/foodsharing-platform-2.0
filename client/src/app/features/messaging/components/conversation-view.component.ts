import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';
import { firstValueFrom } from 'rxjs';

import {
  ChatMessage,
  ConversationDetail,
  ConversationKind,
  MessagingService,
  RealtimeEventType,
  UserSummary,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { bodySegments, insertMention, mentionQuery } from '../message-body';
import { ConversationMessageEvent, MessagingStore } from '../messaging.resource';

interface PendingMessage {
  clientKey: string;
  body: string;
  failed: boolean;
}

interface DayGroup {
  day: string;
  messages: ChatMessage[];
}

/**
 * One conversation (N1): header with name, members and actions (rename / add member / leave for unlocked groups),
 * messages grouped by day with "load older" (`beforeSeq`), composer (Enter sends, Shift+Enter newline, `@` mention
 * autocomplete, optimistic pending bubble with retry, `clientKey` idempotency), auto mark-read while visible, and
 * realtime appends (`CONVERSATION_MESSAGE`; a gap or a reconnect refetches `afterSeq`, A32). Used by the
 * conversation page and the store chat tab.
 */
@Component({
  selector: 'fs-conversation-view',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    FormsModule,
    InputText,
    Textarea,
    TPipe,
    UserAvatarComponent,
    UserChipComponent,
    UserPickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="detail" skeleton="list" data-testid="messaging.conversation.state">
      <ng-template [fsData]="detail" let-c>
        <header class="head" data-testid="messaging.conversation.header">
          <ng-content select="[fsConversationBack]" />
          <span class="head__icon" aria-hidden="true"><i [class]="kindIcon(c.kind)"></i></span>
          <div class="head__text">
            <h2 class="head__name" data-testid="messaging.conversation.name">{{ c.name }}</h2>
            <span class="head__meta">
              @if (c.locked) {
                <i class="pi pi-lock" aria-hidden="true"></i>
              }
              {{ 'messaging.members' | t: { count: c.memberCount } }}
            </span>
          </div>
          <p-button
            icon="pi pi-users"
            [text]="true"
            [rounded]="true"
            severity="secondary"
            [ariaLabel]="'messaging.showMembers' | t"
            (onClick)="membersOpen.set(true)"
            data-testid="messaging.conversation.members"
          />
          @if (mayEdit(c)) {
            <p-button
              icon="pi pi-pencil"
              [text]="true"
              [rounded]="true"
              severity="secondary"
              [ariaLabel]="'messaging.rename' | t"
              (onClick)="openRename(c)"
              data-testid="messaging.conversation.rename"
            />
          }
          @if (c.kind === kinds.Group && !c.locked) {
            <p-button
              icon="pi pi-sign-out"
              [text]="true"
              [rounded]="true"
              severity="danger"
              [ariaLabel]="'messaging.leave' | t"
              (onClick)="askLeave(c)"
              data-testid="messaging.conversation.leave"
            />
          }
        </header>

        <div class="list" #list role="log" tabindex="0" [attr.aria-label]="'messaging.title' | t" data-testid="messaging.conversation.messages">
          @if (hasOlder()) {
            <div class="older">
              <p-button
                [label]="'messaging.loadOlder' | t"
                icon="pi pi-angle-up"
                size="small"
                [text]="true"
                [loading]="loadOlder.busy()"
                (onClick)="loadOlder.run()"
                data-testid="messaging.conversation.loadOlder"
              />
            </div>
          }
          @for (group of days(); track group.day) {
            <div class="day" role="separator">{{ group.messages[0].sentAt | fsDate: 'dateLong' }}</div>
            @for (m of group.messages; track m.id) {
              @let own = m.senderId === me();
              @let system = !m.senderId;
              <div
                class="msg"
                [class.msg--own]="own"
                [class.msg--system]="system"
                data-testid="messaging.conversation.message"
                [attr.data-seq]="m.seq"
                [attr.data-own]="own"
              >
                @if (!own && !system) {
                  @let sender = member(c, m.senderId);
                  <fs-user-avatar [name]="sender?.displayName" [avatarUrl]="sender?.avatarUrl" size="sm" />
                }
                <div class="bubble">
                  @if (!own && !system && c.kind !== kinds.Direct) {
                    <span class="bubble__sender">{{ member(c, m.senderId)?.displayName ?? ('messaging.formerMember' | t) }}</span>
                  }
                  <span class="bubble__body" data-testid="messaging.conversation.body">
                    @for (s of segments(m.body); track $index) {
                      @switch (s.kind) {
                        @case ('mention') {
                          <fs-user-chip class="mention" [name]="s.name" [userId]="s.userId" size="xs" data-testid="messaging.conversation.mention" />
                        }
                        @case ('link') {
                          <a [href]="s.href" target="_blank" rel="noopener noreferrer nofollow">{{ s.href }}</a>
                        }
                        @default {
                          {{ s.text }}
                        }
                      }
                    }
                  </span>
                  <time class="bubble__time" [attr.datetime]="m.sentAt">{{ m.sentAt | fsDate: 'time' }}</time>
                </div>
              </div>
            }
          } @empty {
            @if (pending().length === 0) {
              <p class="empty" data-testid="messaging.conversation.empty">{{ 'messaging.noMessages' | t }}</p>
            }
          }
          @for (p of pending(); track p.clientKey) {
            <div class="msg msg--own msg--pending" data-testid="messaging.conversation.pending" [attr.data-failed]="p.failed">
              <div class="bubble">
                <span class="bubble__body">{{ p.body }}</span>
                @if (p.failed) {
                  <span class="bubble__failed">
                    <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
                    {{ 'messaging.sendFailed' | t }}
                    <p-button
                      [label]="'messaging.retry' | t"
                      size="small"
                      [text]="true"
                      (onClick)="retry(p)"
                      data-testid="messaging.conversation.retry"
                    />
                  </span>
                } @else {
                  <span class="bubble__time"><i class="pi pi-clock" aria-hidden="true"></i> {{ 'messaging.sending' | t }}</span>
                }
              </div>
            </div>
          }
        </div>

        <form class="composer" (ngSubmit)="send()">
          @if (mentionHits().length > 0) {
            <ul class="mentions" role="listbox" [attr.aria-label]="'messaging.mentionSuggestions' | t" data-testid="messaging.conversation.mentionSuggestions">
              @for (u of mentionHits(); track u.id) {
                <li role="option" [attr.aria-selected]="false">
                  <button type="button" class="mentions__pick" (click)="pickMention(u)" data-testid="messaging.conversation.mentionPick">
                    <fs-user-avatar [name]="u.displayName" [avatarUrl]="u.avatarUrl" size="xs" />
                    {{ u.displayName }}
                  </button>
                </li>
              }
            </ul>
          }
          <label class="sr-only" for="composer-{{ c.id }}">{{ 'messaging.composer' | t }}</label>
          <textarea
            pTextarea
            [id]="'composer-' + c.id"
            name="body"
            rows="1"
            [autoResize]="true"
            maxlength="10000"
            [placeholder]="'messaging.composerPlaceholder' | t"
            [ngModel]="draft()"
            (ngModelChange)="draft.set($event)"
            (keydown)="onKeydown($event)"
            data-testid="messaging.conversation.composer"
          ></textarea>
          <p-button
            type="submit"
            icon="pi pi-send"
            [ariaLabel]="'messaging.send' | t"
            [disabled]="!draft().trim()"
            data-testid="messaging.conversation.send"
          />
        </form>

        <p-dialog
          [visible]="membersOpen()"
          (visibleChange)="membersOpen.set($event)"
          [modal]="true"
          [header]="'messaging.membersTitle' | t"
          [closeAriaLabel]="'common.close' | t"
          [style]="{ width: 'min(28rem, calc(100vw - 2rem))' }"
          data-testid="messaging.conversation.membersDialog"
        >
          <ul class="members">
            @for (u of c.members; track u.id) {
              <li data-testid="messaging.conversation.member" [attr.data-user-id]="u.id">
                <fs-user-chip [name]="u.displayName" [userId]="u.id" [avatarUrl]="u.avatarUrl" />
              </li>
            }
          </ul>
          @if (c.kind === kinds.Group && !c.locked) {
            <fs-user-picker
              [label]="'messaging.addMember' | t"
              [excludeIds]="memberIds(c)"
              (picked)="addMember.run($event)"
              testId="messaging.conversation.addMember"
            />
          }
        </p-dialog>

        <p-dialog
          [visible]="renameOpen()"
          (visibleChange)="renameOpen.set($event)"
          [modal]="true"
          [header]="'messaging.rename' | t"
          [closeAriaLabel]="'common.close' | t"
          [style]="{ width: 'min(26rem, calc(100vw - 2rem))' }"
          data-testid="messaging.conversation.renameDialog"
        >
          <form class="rename" (ngSubmit)="rename.run()">
            <label for="conversation-name">{{ 'messaging.groupName' | t }}</label>
            <input pInputText id="conversation-name" name="name" maxlength="100" [(ngModel)]="renameValue" data-testid="messaging.conversation.renameInput" />
            <div class="actions">
              <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="renameOpen.set(false)" />
              <p-button type="submit" [label]="'common.save' | t" [loading]="rename.busy()" [disabled]="!renameValue.trim()" data-testid="messaging.conversation.renameSave" />
            </div>
          </form>
        </p-dialog>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 0;
    }
    fs-data-state {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-height: 0;
    }
    .head {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.25rem 0.75rem;
      border-bottom: 1px solid var(--fs-border);
    }
    .head__icon {
      display: grid;
      place-items: center;
      width: 2.5rem;
      height: 2.5rem;
      border-radius: 50%;
      background: var(--fs-surface-muted, var(--fs-border));
      color: var(--fs-text-muted);
    }
    .head__text {
      display: grid;
      flex: 1;
      min-width: 0;
    }
    .head__name {
      overflow: hidden;
      margin: 0;
      font-size: 1.1rem;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .head__meta {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .list {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: 0.4rem;
      min-height: 16rem;
      max-height: min(65vh, 40rem);
      padding: 0.75rem 0.25rem;
      overflow-y: auto;
    }
    .older {
      display: flex;
      justify-content: center;
    }
    .day {
      align-self: center;
      margin: 0.5rem 0 0.25rem;
      padding: 0.1rem 0.75rem;
      border-radius: 999px;
      background: var(--fs-surface-muted, var(--fs-border));
      color: var(--fs-text-muted);
      font-size: 0.8rem;
    }
    .msg {
      display: flex;
      align-items: flex-end;
      gap: 0.4rem;
      max-width: min(85%, 36rem);
    }
    .msg--own {
      align-self: flex-end;
    }
    .msg--system {
      align-self: center;
      max-width: 90%;
    }
    .bubble {
      display: grid;
      gap: 0.15rem;
      padding: 0.45rem 0.7rem;
      border: 1px solid var(--fs-border);
      border-radius: 0.9rem 0.9rem 0.9rem 0.25rem;
      background: var(--fs-surface);
      overflow-wrap: anywhere;
    }
    .msg--own .bubble {
      border-color: transparent;
      border-radius: 0.9rem 0.9rem 0.25rem 0.9rem;
      background: var(--fs-primary-soft, color-mix(in srgb, var(--fs-primary) 14%, var(--fs-surface)));
    }
    .msg--system .bubble {
      border-style: dashed;
      border-radius: 0.6rem;
      background: transparent;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .msg--pending .bubble {
      opacity: 0.75;
    }
    .bubble__sender {
      color: var(--fs-primary-strong);
      font-size: 0.8rem;
      font-weight: 700;
    }
    .bubble__body {
      white-space: pre-wrap;
    }
    .bubble__body a {
      color: var(--fs-primary-strong);
    }
    .mention {
      display: inline-flex;
      vertical-align: middle;
    }
    .bubble__time {
      justify-self: end;
      color: var(--fs-text-muted);
      font-size: 0.75rem;
    }
    .bubble__failed {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      color: var(--fs-danger-text, #b42318);
      font-size: 0.8rem;
    }
    .empty {
      margin: auto;
      color: var(--fs-text-muted);
    }
    .composer {
      position: relative;
      display: flex;
      align-items: flex-end;
      gap: 0.5rem;
      padding-top: 0.5rem;
      border-top: 1px solid var(--fs-border);
    }
    .composer textarea {
      flex: 1;
      max-height: 12rem;
      min-height: 2.5rem;
      resize: none;
    }
    .mentions {
      position: absolute;
      bottom: 100%;
      left: 0;
      z-index: 2;
      display: grid;
      min-width: 14rem;
      margin: 0;
      padding: 0.25rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-sm);
      background: var(--fs-surface);
      box-shadow: 0 4px 12px rgb(0 0 0 / 0.15);
      list-style: none;
    }
    .mentions__pick {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      width: 100%;
      min-height: 2.5rem;
      padding: 0.25rem 0.5rem;
      border: 0;
      border-radius: var(--fs-radius-sm);
      background: transparent;
      color: var(--fs-text);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .mentions__pick:hover,
    .mentions__pick:focus-visible {
      background: var(--fs-surface-muted, var(--fs-border));
    }
    .members {
      display: grid;
      gap: 0.4rem;
      margin: 0 0 1rem;
      padding: 0;
      list-style: none;
    }
    .rename {
      display: grid;
      gap: 0.5rem;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
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
export class ConversationViewComponent {
  private readonly api = inject(MessagingService);
  private readonly store = inject(MessagingStore);
  private readonly realtime = inject(RealtimeService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);

  readonly conversationId = input.required<string>();
  /** Emitted after the user left the conversation. */
  readonly left = output<void>();

  protected readonly kinds = ConversationKind;
  protected readonly me = computed(() => this.session.user()?.id ?? null);
  protected readonly membersOpen = signal(false);
  protected readonly renameOpen = signal(false);
  protected renameValue = '';
  protected readonly draft = signal('');
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly pending = signal<PendingMessage[]>([]);
  private readonly oldestReached = signal(false);
  private lastMarkedSeq = 0;
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');

  protected readonly detail = loadResource<ConversationDetail, string>({
    params: () => this.conversationId(),
    loader: (conversationId) => this.api.getConversation({ conversationId }),
  });

  protected readonly days = computed<DayGroup[]>(() => {
    const groups: DayGroup[] = [];
    for (const message of this.messages()) {
      const day = message.sentAt.slice(0, 10);
      const last = groups.at(-1);
      if (last?.day === day) {
        last.messages.push(message);
      } else {
        groups.push({ day, messages: [message] });
      }
    }
    return groups;
  });

  protected readonly hasOlder = computed(() => {
    const first = this.messages()[0];
    return !!first && first.seq > 1 && !this.oldestReached();
  });

  protected readonly mentionHits = computed<UserSummary[]>(() => {
    const query = mentionQuery(this.draft());
    const detail = this.detail.data();
    if (query === null || !detail) {
      return [];
    }
    const needle = query.toLowerCase();
    return detail.members
      .filter((user) => user.id !== this.me() && user.displayName.toLowerCase().includes(needle))
      .slice(0, 6);
  });

  constructor() {
    // a fresh detail (first load, reload, other conversation) replaces the message list
    effect(() => {
      const detail = this.detail.data();
      untracked(() => {
        this.messages.set(detail?.messages ?? []);
        this.oldestReached.set(false);
        this.lastMarkedSeq = detail?.lastReadSeq ?? 0;
        this.scrollToEnd();
        this.markRead();
      });
    });
    effect((onCleanup) => {
      const id = this.conversationId();
      this.store.openConversationId.set(id);
      onCleanup(() => this.store.openConversationId.set(null));
    });
    this.realtime
      .on(RealtimeEventType.ConversationMessage, RealtimeEventType.ConversationUpdated)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => {
        const payload = (event.payload ?? {}) as Partial<ConversationMessageEvent>;
        if (payload.conversationId !== this.conversationId()) {
          return;
        }
        if (event.type === RealtimeEventType.ConversationUpdated) {
          this.detail.reload();
        } else {
          this.receive(payload as ConversationMessageEvent);
        }
      });
    const unregister = this.realtime.onReconnect(() => this.fillGap());
    inject(DestroyRef).onDestroy(unregister);
    const onVisible = () => this.markRead();
    this.document.addEventListener('visibilitychange', onVisible);
    inject(DestroyRef).onDestroy(() => this.document.removeEventListener('visibilitychange', onVisible));
  }

  protected kindIcon(kind: ConversationKind): string {
    return KIND_ICONS[kind];
  }

  protected member(c: ConversationDetail, userId: string | undefined): UserSummary | undefined {
    return c.members.find((user) => user.id === userId);
  }

  protected memberIds(c: ConversationDetail): string[] {
    return c.members.map((user) => user.id);
  }

  protected mayEdit(c: ConversationDetail): boolean {
    return !c.locked && c.kind === ConversationKind.Group;
  }

  protected segments(body: string) {
    return bodySegments(body);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      const hits = this.mentionHits();
      if (hits.length > 0) {
        this.pickMention(hits[0]);
      } else {
        this.send();
      }
    }
  }

  protected pickMention(user: UserSummary): void {
    this.draft.set(insertMention(this.draft(), user.displayName, user.id));
  }

  protected send(): void {
    const body = this.draft().trim();
    if (!body) {
      return;
    }
    this.draft.set('');
    const message: PendingMessage = { clientKey: crypto.randomUUID(), body, failed: false };
    this.pending.update((list) => [...list, message]);
    this.scrollToEnd();
    void this.deliver(message);
  }

  protected retry(message: PendingMessage): void {
    this.pending.update((list) => list.map((p) => (p.clientKey === message.clientKey ? { ...p, failed: false } : p)));
    void this.deliver(message);
  }

  /** Sends one pending message; the same clientKey makes a retry idempotent on the server (A33). */
  private async deliver(message: PendingMessage): Promise<void> {
    const ok = await this.sendMutation.run(message);
    if (!ok) {
      this.pending.update((list) => list.map((p) => (p.clientKey === message.clientKey ? { ...p, failed: true } : p)));
    }
  }

  private readonly sendMutation = runMutation(
    (message: PendingMessage) =>
      this.api.sendMessage({
        conversationId: this.conversationId(),
        messageCreate: { body: message.body, clientKey: message.clientKey },
      }),
    {
      errorToast: false,
      onSuccess: (sent: ChatMessage) => this.receive({ ...sent, conversationId: this.conversationId() }),
    },
  );

  protected readonly loadOlder = runMutation(
    () => this.store.older(this.conversationId(), this.messages()[0]?.seq ?? 1),
    {
      onSuccess: (older: ChatMessage[]) => {
        if (older.length === 0) {
          this.oldestReached.set(true);
        }
        this.messages.update((list) => merge(older, list));
      },
    },
  );

  protected readonly addMember = runMutation(
    (user: UserSummary) => this.api.addConversationMember({ conversationId: this.conversationId(), userId: user.id }),
    {
      successToastKey: 'messaging.memberAdded',
      onSuccess: () => {
        this.detail.reload();
        this.store.changed();
      },
    },
  );

  protected openRename(c: ConversationDetail): void {
    this.renameValue = c.customName ?? c.name;
    this.renameOpen.set(true);
  }

  protected readonly rename = runMutation(
    () =>
      this.api.renameConversation({
        conversationId: this.conversationId(),
        conversationRename: { name: this.renameValue.trim() },
      }),
    {
      successToastKey: 'messaging.renamed',
      onSuccess: () => {
        this.renameOpen.set(false);
        this.detail.reload();
        this.store.changed();
      },
    },
  );

  private readonly leave = runMutation(
    () => this.api.removeConversationMember({ conversationId: this.conversationId(), userId: this.me()! }),
    {
      successToastKey: 'messaging.leftToast',
      onSuccess: () => {
        this.store.changed();
        this.left.emit();
      },
    },
  );

  protected askLeave(c: ConversationDetail): void {
    this.confirm.confirm({
      header: this.i18n.t('messaging.leaveTitle', { name: c.name }),
      message: this.i18n.t('messaging.leaveText'),
      acceptLabel: this.i18n.t('messaging.leave'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.leave.run(),
    });
  }

  /** A message from the server (send response or realtime): appended in seq order, gaps are refetched. */
  private receive(message: ConversationMessageEvent): void {
    if (message.clientKey) {
      this.pending.update((list) => list.filter((p) => p.clientKey !== message.clientKey));
    }
    const list = this.messages();
    const lastSeq = list.at(-1)?.seq ?? 0;
    if (list.some((m) => m.id === message.id)) {
      return;
    }
    const chat: ChatMessage = {
      id: message.id,
      seq: message.seq,
      senderId: message.senderId,
      body: message.body,
      sentAt: message.sentAt,
      clientKey: message.clientKey,
    };
    this.messages.set(merge(list, [chat]));
    if (lastSeq > 0 && message.seq > lastSeq + 1) {
      this.fillGap(lastSeq);
    }
    this.scrollToEnd();
    this.markRead();
  }

  /** Refetches everything after the last known seq (after a missed event or a reconnect; A32). */
  private fillGap(afterSeq = this.messages().at(-1)?.seq ?? 0): void {
    void firstValueFrom(this.store.after(this.conversationId(), afterSeq))
      .then((missing) => {
        if (missing.length > 0) {
          this.messages.update((list) => merge(list, missing));
          this.scrollToEnd();
          this.markRead();
        }
      })
      .catch(() => undefined);
  }

  private markRead(): void {
    const lastSeq = this.messages().at(-1)?.seq ?? 0;
    if (lastSeq > this.lastMarkedSeq && this.document.visibilityState === 'visible') {
      this.lastMarkedSeq = lastSeq;
      this.store.markRead(this.conversationId(), lastSeq);
    }
  }

  private scrollToEnd(): void {
    setTimeout(() => {
      const element = this.list()?.nativeElement;
      if (element) {
        element.scrollTop = element.scrollHeight;
      }
    });
  }
}

const KIND_ICONS: Record<ConversationKind, string> = {
  [ConversationKind.Direct]: 'pi pi-user',
  [ConversationKind.Group]: 'pi pi-users',
  [ConversationKind.StoreTeam]: 'pi pi-shop',
  [ConversationKind.StoreJumper]: 'pi pi-clock',
  [ConversationKind.Region]: 'pi pi-map',
};

/** Merges two ascending lists by seq without duplicates. */
function merge(a: readonly ChatMessage[], b: readonly ChatMessage[]): ChatMessage[] {
  const bySeq = new Map<number, ChatMessage>();
  for (const message of [...a, ...b]) {
    bySeq.set(message.seq, message);
  }
  return [...bySeq.values()].sort((x, y) => x.seq - y.seq);
}
