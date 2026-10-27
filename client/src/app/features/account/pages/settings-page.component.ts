import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AccountTabsComponent } from '../components/account-tabs.component';
import { LoginMethodsCardComponent } from '../components/login-methods-card.component';
import { PreferencesCardComponent } from '../components/preferences-card.component';

/** /account/settings — language, theme (A12, A17) and login methods incl. passkeys (D8). */
@Component({
  selector: 'fs-settings-page',
  imports: [
    AccountTabsComponent,
    LoginMethodsCardComponent,
    PageHeaderComponent,
    PreferencesCardComponent,
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
      <fs-preferences-card />
      <fs-login-methods-card [autoAddPasskey]="autoAdd()" />
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
  /** `?addPasskey=1` (from the "log in faster" nudge) opens the passkey dialog right away. */
  readonly addPasskey = input<string | null | undefined>(undefined);
  protected readonly autoAdd = computed(() => this.addPasskey() === '1');
}
