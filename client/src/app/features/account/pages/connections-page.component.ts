import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';

import { OAuthConsent, UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { OAuthScopeListComponent } from '../../oauth/components/scope-list.component';
import { AccountTabsComponent } from '../components/account-tabs.component';

/**
 * /account/connections (C13, A44): applications the user allowed to sign in with foodsharing (remembered consents and
 * clients with live refresh tokens), with what they may read. Revoking deletes the consent and its tokens; the
 * application has to ask again next time.
 */
@Component({
  selector: 'fs-connections-page',
  imports: [...DATA_STATE_IMPORTS, AccountTabsComponent, Button, DateTimePipe, OAuthScopeListComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'account.connections.title' | t" [subtitle]="'account.connections.subtitle' | t" icon="pi pi-link" />
    <fs-account-tabs />

    <section class="fs-card card" aria-labelledby="connections-title">
      <h2 id="connections-title" class="fs-visually-hidden">{{ 'account.connections.title' | t }}</h2>
      <fs-data-state
        [state]="connections"
        skeleton="list"
        [skeletonRows]="2"
        emptyIcon="pi pi-link"
        [emptyTitle]="'account.connections.empty' | t"
        [emptyText]="'account.connections.emptyText' | t"
        testId="account.connections.state"
        data-testid="account.connections.state"
      >
        <ng-template [fsData]="connections" let-list>
          <ul class="rows">
            @for (connection of list; track connection.clientId) {
              <li class="row" data-testid="account.connections.item" [attr.data-client]="connection.clientId">
                <span class="row__icon" aria-hidden="true"><i class="pi pi-th-large"></i></span>
                <span class="row__main">
                  <span class="row__title" data-testid="account.connections.name">{{ connection.clientName }}</span>
                  <span class="row__meta">
                    {{ 'account.connections.since' | t: { date: (connection.grantedAt | fsDate: 'date') } }}
                  </span>
                  <fs-oauth-scope-list [scopes]="connection.scopes" [compact]="true" testId="account.connections.scopes" />
                </span>
                <span class="row__actions">
                  <p-button
                    [label]="'account.connections.revoke' | t"
                    icon="pi pi-times"
                    severity="danger"
                    [text]="true"
                    size="small"
                    [loading]="revokingId() === connection.clientId"
                    [disabled]="revoke.busy()"
                    (onClick)="confirmRevoke(connection)"
                    data-testid="account.connections.revoke"
                  />
                </span>
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../components/account-card.scss',
  styles: `
    :host {
      width: min(100%, 60rem);
      margin: 0 auto;
    }
    .row__main fs-oauth-scope-list {
      display: block;
      margin-top: 0.4rem;
    }
  `,
})
export class ConnectionsPageComponent {
  private readonly api = inject(UsersService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly connections = loadResource<OAuthConsent[]>({ loader: () => this.api.listMyConnections() });
  protected readonly revokingId = signal<string | null>(null);

  protected readonly revoke = runMutation((connection: OAuthConsent) => this.api.revokeMyConnection({ clientId: connection.clientId }), {
    successToastKey: 'account.connections.revoked',
    onSuccess: () => {
      const id = this.revokingId();
      this.connections.update((list) => (list ?? []).filter((item) => item.clientId !== id));
      this.revokingId.set(null);
    },
    onError: () => this.revokingId.set(null),
  });

  protected confirmRevoke(connection: OAuthConsent): void {
    this.confirm.confirm({
      header: this.i18n.t('account.connections.revokeTitle'),
      message: this.i18n.t('account.connections.revokeText', { name: connection.clientName }),
      icon: 'pi pi-link',
      acceptLabel: this.i18n.t('account.connections.revoke'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => {
        this.revokingId.set(connection.clientId);
        void this.revoke.run(connection);
      },
    });
  }
}
