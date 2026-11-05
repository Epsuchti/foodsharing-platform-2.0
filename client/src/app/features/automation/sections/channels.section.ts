import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AdvertChannel, SignoutAlertChannel } from '../../../api';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { TelegramChatsComponent } from '../components/telegram-chats.component';
import { StoreAutomationContext } from '../store-automation.context';

/** One automation that sends to Telegram ("Wer sendet hierhin?"). */
export interface ChannelUser {
  key: string;
  text: string;
  section: 'adverts' | 'signout-alerts';
}

/**
 * Kanäle (decision 167 S4, proposal §3.6): the store's Telegram chats (`fs-telegram-chats`, store scope; without an
 * active chat the messages go to the region's chats) and "Wer sendet hierhin?", the enabled adverts and sign-out
 * alert with channel Telegram.
 */
@Component({
  selector: 'fs-channels-section',
  imports: [RouterLink, TelegramChatsComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <h2>{{ 'automation.channels.title' | t }}</h2>
      <p class="muted">
        {{ 'automation.channels.intro' | t }}
        @if (ctx.regionId(); as regionId) {
          <a [routerLink]="['/regions', regionId, 'automation', 'telegram']" data-testid="automation.channels.regionLink">{{ 'automation.channels.regionLink' | t }}</a>
        }
      </p>
      <fs-telegram-chats
        [scope]="scope()"
        testId="automation.channels"
        [askWho]="'automation.store.askWho' | t"
        [emptyHint]="'automation.channels.emptyHint' | t"
        (changed)="ctx.reload()"
      />
    </section>
    <section class="card fs-card" data-testid="automation.channels.users">
      <h3>{{ 'automation.channels.users' | t }}</h3>
      <ul class="list">
        @for (user of users(); track user.key) {
          <li data-testid="automation.channels.user" [attr.data-key]="user.key">
            <a [routerLink]="['..', user.section]">{{ user.text }}</a>
          </li>
        } @empty {
          <li class="muted" data-testid="automation.channels.usersNone">{{ 'automation.channels.usersNone' | t }}</li>
        }
      </ul>
    </section>
  `,
  styleUrl: '../automation.scss',
})
export class ChannelsSection {
  protected readonly ctx = inject(StoreAutomationContext);
  private readonly i18n = inject(I18nService);

  protected readonly scope = computed(() => {
    const storeId = this.ctx.storeId();
    return storeId ? { storeId } : undefined;
  });

  protected readonly users = computed<ChannelUser[]>(() => {
    this.i18n.locale();
    const data = this.ctx.data();
    const users: ChannelUser[] = [];
    for (const advert of data?.adverts ?? []) {
      if (advert.enabled && advert.channels.includes(AdvertChannel.Telegram)) {
        users.push({
          key: `advert:${advert.advertNumber}`,
          section: 'adverts',
          text: this.i18n.t('automation.channels.usersAdvert', { number: advert.advertNumber ?? '', hours: advert.triggerHoursBefore }),
        });
      }
    }
    const alert = data?.signoutAlert;
    if (alert?.enabled && alert.channels.includes(SignoutAlertChannel.Telegram)) {
      users.push({ key: 'signout', section: 'signout-alerts', text: this.i18n.t('automation.channels.usersSignout') });
    }
    return users;
  });
}
