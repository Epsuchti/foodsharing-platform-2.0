import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { UserProfile, UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AccountTabsComponent } from '../components/account-tabs.component';
import { EmailCardComponent } from '../components/email-card.component';
import { LoginMethodsCardComponent } from '../components/login-methods-card.component';
import { PhotoCardComponent } from '../components/photo-card.component';
import { PreferencesCardComponent } from '../components/preferences-card.component';
import { SleepModeCardComponent } from '../components/sleep-mode-card.component';
import { PickupReminderCardComponent } from '../../pickups/components/pickup-reminder-card.component';

/**
 * /account/settings — photo (A5), language, theme and mail switches (A12, A17, A20), email address (A3), sleep mode
 * (A9), login methods incl. passkeys (D8) and pickup reminders. The own profile is loaded once and shared by the
 * cards that show its values.
 */
@Component({
  selector: 'fs-settings-page',
  imports: [
    AccountTabsComponent,
    EmailCardComponent,
    LoginMethodsCardComponent,
    PageHeaderComponent,
    PhotoCardComponent,
    PickupReminderCardComponent,
    PreferencesCardComponent,
    SleepModeCardComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'account.settings.title' | t"
      [subtitle]="'account.settings.subtitle' | t"
      icon="pi pi-cog"
    />
    <fs-account-tabs />
    <div class="layout">
      <fs-photo-card [profile]="profile" />
      <fs-preferences-card [profile]="profile" />
      <fs-email-card [profile]="profile" />
      <fs-sleep-mode-card [profile]="profile" />
      <fs-login-methods-card [autoAddPasskey]="autoAdd()" />
      <fs-pickup-reminder-card />
    </div>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 60rem);
      margin: 0 auto;
    }
    .layout {
      display: grid;
      gap: 1.25rem;
    }
  `,
})
export class SettingsPageComponent {
  private readonly usersApi = inject(UsersService);

  /** `?addPasskey=1` (from the "log in faster" nudge) opens the passkey dialog right away. */
  readonly addPasskey = input<string | null | undefined>(undefined);
  protected readonly autoAdd = computed(() => this.addPasskey() === '1');

  protected readonly profile = loadResource<UserProfile>({
    loader: () => this.usersApi.getCurrentUser(),
    isEmpty: () => false,
  });
}
