import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { BellsService, CategorySetting, NotificationCategory, NotificationSettings } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AccountTabsComponent } from '../../account/components/account-tabs.component';
import { PickupReminderCardComponent } from '../../pickups/components/pickup-reminder-card.component';
import { PushCardComponent } from '../components/push-card.component';
import { TelegramCardComponent } from '../components/telegram-card.component';

/** Channels a user can switch per category; bells are always on. */
export type SwitchableChannel = 'mail' | 'push' | 'telegram';
export const CHANNELS: readonly SwitchableChannel[] = ['mail', 'push', 'telegram'];

/** Settings with one channel of one category changed (the update always sends every category). */
export function withChannel(
  settings: readonly CategorySetting[],
  category: NotificationCategory,
  channel: SwitchableChannel,
  value: boolean,
): CategorySetting[] {
  return settings.map((row) => (row.category === category ? { ...row, [channel]: value, bell: true } : { ...row, bell: true }));
}

/**
 * `/account/notifications` (N4, N5 user side, PU9): matrix category × channel (bell locked on, mail, push,
 * Telegram) with optimistic toggles, this device's push opt-in, Telegram linking, the pickup reminder switch and
 * the newsletter opt-in.
 */
@Component({
  selector: 'fs-notification-settings-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    AccountTabsComponent,
    FormsModule,
    PageHeaderComponent,
    PickupReminderCardComponent,
    PushCardComponent,
    TelegramCardComponent,
    ToggleSwitch,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'notifications.settings.title' | t" [subtitle]="'notifications.settings.subtitle' | t" icon="pi pi-bell" />
    <fs-account-tabs />
    <div class="layout">
      <section class="fs-card card" aria-labelledby="ns-matrix-title">
        <header class="card__head">
          <h2 id="ns-matrix-title">{{ 'notifications.settings.matrixTitle' | t }}</h2>
          <p>{{ 'notifications.settings.matrixText' | t }}</p>
        </header>
        <fs-data-state [state]="settings" skeleton="table" [skeletonRows]="6" data-testid="notifications.settings.state">
          <ng-template [fsData]="settings">
            <div class="table-wrap">
              <table class="matrix" data-testid="notifications.settings.matrix">
                <thead>
                  <tr>
                    <th scope="col">{{ 'notifications.settings.category' | t }}</th>
                    <th scope="col">{{ 'notifications.channel.bell' | t }}</th>
                    @for (channel of channels; track channel) {
                      <th scope="col">{{ 'notifications.channel.' + channel | t }}</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (row of rows(); track row.category) {
                    <tr [attr.data-category]="row.category" data-testid="notifications.settings.row">
                      <th scope="row">
                        <span class="cat">{{ 'notifications.category.' + row.category | t }}</span>
                      </th>
                      <td>
                        <i class="pi pi-lock muted" role="img" [attr.aria-label]="'notifications.settings.bellLocked' | t"></i>
                      </td>
                      @for (channel of channels; track channel) {
                        <td>
                          <p-toggleswitch
                            [inputId]="'ns-' + row.category + '-' + channel"
                            [ngModel]="row[channel]"
                            (ngModelChange)="toggle(row.category, channel, $event)"
                            [disabled]="save.busy() || !available(channel)"
                            [ariaLabel]="('notifications.category.' + row.category | t) + ': ' + ('notifications.channel.' + channel | t)"
                            [attr.data-testid]="'notifications.settings.toggle.' + row.category + '.' + channel"
                            [attr.data-checked]="row[channel]"
                          />
                        </td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (!settings.data()?.pushAvailable || !settings.data()?.telegramLinked) {
              <p class="muted hint" data-testid="notifications.settings.channelHint">{{ 'notifications.settings.channelHint' | t }}</p>
            }
          </ng-template>
        </fs-data-state>
      </section>

      <fs-push-card (changed)="settings.reload()" />
      <fs-telegram-card [linked]="settings.data()?.telegramLinked ?? false" [botName]="settings.data()?.telegramBotName" (changed)="settings.reload()" />
      <fs-pickup-reminder-card />

      <section class="fs-card card" aria-labelledby="ns-newsletter-title">
        <header class="card__head">
          <h2 id="ns-newsletter-title">{{ 'notifications.newsletter.title' | t }}</h2>
          <p>{{ 'notifications.newsletter.text' | t }}</p>
        </header>
        @if (settings.data(); as data) {
          <div class="row">
            <p-toggleswitch
              inputId="ns-newsletter"
              [ngModel]="newsletter() ?? data.newsletterOptIn"
              (ngModelChange)="setNewsletter($event)"
              [disabled]="save.busy()"
              data-testid="notifications.newsletter.toggle"
            />
            <label for="ns-newsletter">{{ 'notifications.newsletter.label' | t }}</label>
          </div>
        }
      </section>
    </div>
  `,
  styleUrl: '../notifications.scss',
  styles: `
    .table-wrap {
      overflow-x: auto;
    }
    .matrix {
      width: 100%;
      border-collapse: collapse;
    }
    .matrix th,
    .matrix td {
      padding: 0.5rem;
      border-bottom: 1px solid var(--fs-border);
      text-align: center;
    }
    .matrix th[scope='row'],
    .matrix thead th:first-child {
      text-align: start;
    }
    .hint {
      margin: 0.75rem 0 0;
    }
  `,
})
export class NotificationSettingsPageComponent {
  private readonly api = inject(BellsService);
  protected readonly channels = CHANNELS;

  protected readonly settings = loadResource<NotificationSettings>({
    loader: () => this.api.getNotificationSettings(),
    isEmpty: () => false,
  });

  /** Local copy for optimistic toggles. */
  private readonly local = signal<CategorySetting[] | null>(null);
  protected readonly newsletter = signal<boolean | null>(null);
  protected readonly rows = computed(() => this.local() ?? this.settings.data()?.categories ?? []);

  protected available(channel: SwitchableChannel): boolean {
    const data = this.settings.data();
    if (channel === 'push') return !!data?.pushAvailable;
    if (channel === 'telegram') return !!data?.telegramLinked;
    return true;
  }

  private previous: CategorySetting[] | null = null;
  protected readonly save = runMutation(
    (categories: CategorySetting[], newsletterOptIn: boolean | undefined) =>
      this.api.updateNotificationSettings({ notificationSettingsUpdate: { categories, newsletterOptIn } }),
    {
      successToastKey: 'notifications.settings.saved',
      onSuccess: (saved) => {
        this.local.set(saved.categories);
        this.newsletter.set(saved.newsletterOptIn);
      },
      onError: () => this.local.set(this.previous),
    },
  );

  protected toggle(category: NotificationCategory, channel: SwitchableChannel, value: boolean): void {
    this.previous = this.local();
    const next = withChannel(this.rows(), category, channel, value);
    this.local.set(next);
    void this.save.run(next, undefined);
  }

  protected setNewsletter(value: boolean): void {
    this.previous = this.local();
    void this.save.run(this.rows().map((row) => ({ ...row, bell: true })), value);
  }
}
