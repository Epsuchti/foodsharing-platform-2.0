import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { Observable } from 'rxjs';

import { TelegramChat, TelegramChatConfig, TelegramChatConfigList, TelegramService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/** Whose alert chats: a region (AU10) or a store (decision 165 S4, Kanäle). */
export type TelegramChatsScope = { regionId: string } | { storeId: string };

/**
 * Telegram alert chats of a region or a store (AU10, decision 165 S4): pick from the chats the bot has seen, enable,
 * send a test message, remove, save. Test ids are `<testId>.state|chat|enabled|test|remove|pick|add|save`
 * (`automation.telegram` on the region page, `automation.channels` in the store tab).
 */
@Component({
  selector: 'fs-telegram-chats',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, Select, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="config" skeleton="list" [testId]="testId() + '.state'" [attr.data-testid]="testId() + '.state'" [askWho]="askWho()">
      <ng-template [fsData]="config">
        <ul class="list chats">
          @for (chat of draft(); track chat.chatId) {
            <li class="chat" [attr.data-testid]="testId() + '.chat'" [attr.data-chat-id]="chat.chatId">
              <span class="chat__name"><strong>{{ chat.title }}</strong> <span class="muted">{{ chat.chatId }}</span></span>
              <span class="chat__actions">
                <p-toggleswitch
                  [inputId]="idPrefix() + chat.chatId"
                  [(ngModel)]="chat.enabled"
                  [ariaLabel]="'automation.telegram.enabled' | t"
                  [attr.data-testid]="testId() + '.enabled'"
                />
                <p-button
                  [label]="'automation.telegram.test' | t"
                  icon="pi pi-send"
                  [text]="true"
                  size="small"
                  [loading]="test.busy()"
                  [disabled]="!saved(chat)"
                  (onClick)="test.run(chat)"
                  [attr.data-testid]="testId() + '.test'"
                />
                <p-button
                  icon="pi pi-trash"
                  severity="danger"
                  [text]="true"
                  size="small"
                  [ariaLabel]="'automation.telegram.remove' | t: { chat: chat.title }"
                  (onClick)="removeChat(chat)"
                  [attr.data-testid]="testId() + '.remove'"
                />
              </span>
            </li>
          } @empty {
            <li class="muted" [attr.data-testid]="testId() + '.none'">
              {{ 'automation.telegram.none' | t }}
              @if (emptyHint(); as hint) {
                {{ hint }}
              }
            </li>
          }
        </ul>
        <div class="row">
          <p-select
            [options]="available()"
            optionLabel="title"
            [ngModel]="pick()"
            (ngModelChange)="pick.set($event)"
            [placeholder]="'automation.telegram.pick' | t"
            [loading]="seen.status() === 'loading'"
            [emptyMessage]="'automation.telegram.noneSeen' | t"
            [attr.data-testid]="testId() + '.pick'"
          />
          <p-button
            [label]="'automation.telegram.add' | t"
            icon="pi pi-plus"
            [outlined]="true"
            [disabled]="!pick()"
            (onClick)="addChat()"
            [attr.data-testid]="testId() + '.add'"
          />
        </div>
        <div class="actions">
          <p-button
            [label]="'common.save' | t"
            icon="pi pi-save"
            [disabled]="!dirty()"
            [loading]="save.busy()"
            (onClick)="save.run()"
            [attr.data-testid]="testId() + '.save'"
          />
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../automation.scss',
  styles: `
    :host { display: block; }
    .chat {
      display: flex; flex-wrap: wrap; align-items: center; gap: 0.25rem 0.75rem;
      min-height: var(--fs-target-sm); padding: 0.4rem 0; border-bottom: 1px solid var(--fs-border);
    }
    .chat__name { flex: 1 1 14rem; min-width: 0; overflow-wrap: anywhere; }
    .chat__actions { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 0.25rem 0.5rem; }
  `,
})
export class TelegramChatsComponent {
  private readonly api = inject(TelegramService);

  readonly scope = input.required<TelegramChatsScope | undefined>();
  readonly testId = input('automation.telegram');
  readonly askWho = input<string | undefined>(undefined);
  /** Added after "Keine Chats eingerichtet." (store: where the messages go until then). */
  readonly emptyHint = input<string | undefined>(undefined);
  /** After a save (the store tab reloads its chat count). */
  readonly changed = output<void>();

  protected readonly draft = signal<TelegramChatConfig[]>([]);
  protected readonly pick = signal<TelegramChat | null>(null);
  protected readonly idPrefix = computed(() => this.testId().replace(/\W+/g, '-') + '-');

  protected readonly config = loadResource<TelegramChatConfigList, TelegramChatsScope>({
    params: () => this.scope(),
    loader: (scope) =>
      'regionId' in scope ? this.api.getRegionTelegramChats({ regionId: scope.regionId }) : this.api.getStoreTelegramChats({ storeId: scope.storeId }),
    isEmpty: () => false,
  });

  protected readonly seen = loadResource<TelegramChat[]>({
    loader: () => this.api.listTelegramChats(),
    isEmpty: () => false,
  });

  protected readonly available = computed(() => {
    const used = new Set(this.draft().map((c) => c.chatId));
    return (this.seen.data() ?? []).filter((c) => !used.has(c.chatId));
  });

  constructor() {
    // copy the stored configuration into the editable draft whenever it (re)loads
    effect(() => {
      const data = this.config.data();
      untracked(() => this.draft.set((data?.chats ?? []).map((c) => ({ ...c }))));
    });
  }

  /** Re-evaluated on every change detection run (the switches edit the draft in place). */
  protected dirty(): boolean {
    return JSON.stringify(this.draft()) !== JSON.stringify(this.config.data()?.chats ?? []);
  }

  /** A test message needs the chat to be stored (the server sends to configured chats only). */
  protected saved(chat: TelegramChatConfig): boolean {
    return (this.config.data()?.chats ?? []).some((c) => c.chatId === chat.chatId);
  }

  protected addChat(): void {
    const chat = this.pick();
    if (!chat) return;
    this.draft.update((list) => [...list, { chatId: chat.chatId, title: chat.title, enabled: true }]);
    this.pick.set(null);
  }

  protected removeChat(chat: TelegramChatConfig): void {
    this.draft.update((list) => list.filter((c) => c !== chat));
  }

  protected readonly save = runMutation(
    (): Observable<TelegramChatConfigList> => {
      const scope = this.scope()!;
      const telegramChatConfigList = { chats: this.draft() };
      return 'regionId' in scope
        ? this.api.putRegionTelegramChats({ regionId: scope.regionId, telegramChatConfigList })
        : this.api.putStoreTelegramChats({ storeId: scope.storeId, telegramChatConfigList });
    },
    {
      successToastKey: 'automation.common.saved',
      onSuccess: () => {
        this.config.reload();
        this.changed.emit();
      },
    },
  );

  protected readonly test = runMutation(
    (chat: TelegramChatConfig) => {
      const scope = this.scope()!;
      const telegramChatTest = { chatId: chat.chatId };
      return 'regionId' in scope
        ? this.api.testRegionTelegramChat({ regionId: scope.regionId, telegramChatTest })
        : this.api.testStoreTelegramChat({ storeId: scope.storeId, telegramChatTest });
    },
    { successToastKey: 'automation.telegram.testSent' },
  );
}
