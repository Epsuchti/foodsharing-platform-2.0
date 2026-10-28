import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectButton } from '@openng/optimus-ui/selectbutton';

import { MessagingService, StoreConversations } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { ConversationViewComponent } from '../../messaging/components/conversation-view.component';
import { StorePageContext } from '../store-page.context';

type Channel = 'team' | 'jumper';

/**
 * /stores/:storeId/chat (S10): the store's locked team chat and, for managers and jumpers, the jumper chat
 * (segment). Membership follows the team (accept → team chat, standby → jumper chat; A33).
 */
@Component({
  selector: 'fs-store-chat-tab',
  imports: [...DATA_STATE_IMPORTS, ConversationViewComponent, FormsModule, SelectButton, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state
      [state]="chats"
      skeleton="list"
      [emptyTitle]="'stores.chat.none' | t"
      emptyIcon="pi pi-comments"
      data-testid="stores.chat.state"
    >
      <ng-template [fsData]="chats" let-c>
        @if (options().length > 1) {
          <p-selectbutton
            [options]="options()"
            optionLabel="label"
            optionValue="value"
            [allowEmpty]="false"
            [ngModel]="active()"
            (ngModelChange)="channel.set($event)"
            [ariaLabel]="'stores.chat.channel' | t"
            data-testid="stores.chat.channel"
          />
        }
        @if (activeId(); as id) {
          <fs-conversation-view class="fs-card view" [conversationId]="id" />
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.75rem;
    }
    p-selectbutton {
      justify-self: start;
    }
    .view {
      padding: 0.75rem;
    }
  `,
})
export class StoreChatTab {
  private readonly api = inject(MessagingService);
  private readonly i18n = inject(I18nService);
  protected readonly ctx = inject(StorePageContext);

  protected readonly channel = signal<Channel | null>(null);

  protected readonly chats = loadResource<StoreConversations, string>({
    params: () => this.ctx.storeId() ?? undefined,
    loader: (storeId) => this.api.getStoreConversations({ storeId }),
    isEmpty: (c) => !c.teamConversationId && !c.jumperConversationId,
  });

  protected readonly options = computed(() => {
    const c = this.chats.data();
    const list: { label: string; value: Channel }[] = [];
    if (c?.teamConversationId) list.push({ label: this.i18n.t('stores.chat.team'), value: 'team' });
    if (c?.jumperConversationId) list.push({ label: this.i18n.t('stores.chat.jumper'), value: 'jumper' });
    return list;
  });

  protected readonly active = computed<Channel | null>(() => this.channel() ?? this.options()[0]?.value ?? null);

  protected readonly activeId = computed(() => {
    const c = this.chats.data();
    const active = this.active();
    return active === 'team' ? c?.teamConversationId : active === 'jumper' ? c?.jumperConversationId : undefined;
  });
}
