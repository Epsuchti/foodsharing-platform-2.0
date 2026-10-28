import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { map } from 'rxjs';

import { AuthService, SessionView } from '../../../api';
import { AuthFacadeService } from '../../../core/auth/auth-facade.service';
import { describeUserAgent } from '../../../core/auth/webauthn.client';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AccountTabsComponent } from '../components/account-tabs.component';

interface DeviceView {
  icon: string;
  name: string;
}

/**
 * /account/sessions — signed-in devices (A22): current one marked, revoke others, and "log out everywhere" as a
 * secondary action below the list (it is rarely needed and should not compete with the page title).
 */
@Component({
  selector: 'fs-sessions-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    AccountTabsComponent,
    Button,
    DateTimePipe,
    PageHeaderComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'account.sessions.title' | t"
      [subtitle]="'account.sessions.subtitle' | t"
      icon="pi pi-desktop"
    />
    <fs-account-tabs />

    <section class="fs-card card" aria-labelledby="sessions-title">
      <h2 id="sessions-title" class="fs-visually-hidden">{{ 'account.sessions.title' | t }}</h2>
      <fs-data-state
        [state]="sessions"
        skeleton="list"
        [skeletonRows]="3"
        emptyIcon="pi pi-desktop"
        emptyIllustration="devices"
        [emptyTitle]="'account.sessions.empty' | t"
        data-testid="account.sessions.list"
      >
        <ng-template [fsData]="sessions" let-list>
          <ul class="rows">
            @for (session of list; track session.id) {
              <li
                class="row"
                [class.row--current]="session.current"
                [attr.data-current]="session.current"
                [attr.data-testid]="'account.sessions.item.' + session.id"
              >
                <span class="row__icon" aria-hidden="true">
                  <i [class]="device(session).icon"></i>
                </span>
                <span class="row__main">
                  <span class="row__title">
                    {{ device(session).name }}
                    @if (session.current) {
                      <span class="badge" data-testid="account.sessions.current">
                        <i class="pi pi-check-circle" aria-hidden="true"></i>
                        {{ 'account.sessions.thisDevice' | t }}
                      </span>
                    }
                  </span>
                  <span class="row__meta">
                    {{
                      (session.current
                        ? 'account.sessions.activeNow'
                        : 'account.sessions.lastActive'
                      ) | t: { date: (session.lastSeenAt | fsDate: 'relative') }
                    }}
                    ·
                    {{
                      'account.sessions.signedIn'
                        | t: { date: (session.createdAt | fsDate: 'dateTime') }
                    }}
                  </span>
                  @if (session.ipHint) {
                    <span class="row__meta row__ip" data-screenshot-mask>
                      {{ 'account.sessions.network' | t: { hint: session.ipHint } }}
                    </span>
                  }
                </span>
                <span class="row__actions">
                  @if (session.current) {
                    <p-button
                      [label]="'nav.logout' | t"
                      icon="pi pi-sign-out"
                      severity="secondary"
                      [outlined]="true"
                      size="small"
                      (onClick)="logout()"
                      data-testid="account.sessions.logoutCurrent"
                    />
                  } @else {
                    <p-button
                      [label]="'account.sessions.revoke' | t"
                      icon="pi pi-times"
                      severity="danger"
                      [text]="true"
                      size="small"
                      [loading]="revokingId() === session.id"
                      [disabled]="revoke.busy() || logoutEverywhere.busy()"
                      (onClick)="confirmRevoke(session)"
                      [attr.data-testid]="'account.sessions.item.' + session.id + '.revoke'"
                    />
                  }
                </span>
              </li>
            }
          </ul>
          @if (list.length === 1) {
            <p class="hint" data-testid="account.sessions.onlyThis">
              <i class="pi pi-shield" aria-hidden="true"></i>
              <span>{{ 'account.sessions.onlyThis' | t }}</span>
            </p>
          }
          @if (otherCount() > 0) {
            <div class="everywhere">
              <p class="everywhere__text">{{ 'account.sessions.logoutEverywhereHint' | t }}</p>
              <p-button
                [label]="'account.sessions.logoutEverywhere' | t"
                icon="pi pi-sign-out"
                severity="danger"
                [outlined]="true"
                size="small"
                [loading]="logoutEverywhere.busy()"
                (onClick)="confirmLogoutEverywhere()"
                data-testid="account.sessions.logoutEverywhere"
              />
            </div>
          }
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
    .row--current .row__icon {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .everywhere {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem 1rem;
      margin-top: 1rem;
      padding-top: 1rem;
      border-top: 1px solid var(--fs-border);
    }
    .everywhere__text {
      flex: 1 1 16rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
    .row__ip {
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 0.78rem;
    }
  `,
})
export class SessionsPageComponent {
  private readonly authApi = inject(AuthService);
  private readonly auth = inject(AuthFacadeService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  /** This device first, then the others by last activity (as the server sorts them). */
  protected readonly sessions = loadResource({
    loader: () =>
      this.authApi
        .listSessions()
        .pipe(map((list) => [...list].sort((a, b) => Number(b.current) - Number(a.current)))),
  });
  protected readonly otherCount = computed(
    () => (this.sessions.data() ?? []).filter((session) => !session.current).length,
  );
  protected readonly revokingId = signal<string | null>(null);

  protected readonly revoke = runMutation(
    (session: SessionView) => this.authApi.revokeSession({ sessionId: session.id }),
    {
      successToastKey: 'account.sessions.revoked',
      onSuccess: () => {
        const id = this.revokingId();
        this.sessions.update((list) => (list ?? []).filter((session) => session.id !== id));
        this.revokingId.set(null);
      },
      onError: () => this.revokingId.set(null),
    },
  );

  protected readonly logoutEverywhere = runMutation(() => this.authApi.logoutEverywhere(), {
    successToastKey: 'account.sessions.loggedOutEverywhere',
    onSuccess: () =>
      this.sessions.update((list) => (list ?? []).filter((session) => session.current)),
  });

  protected device(session: SessionView): DeviceView {
    const { browser, os, mobile } = describeUserAgent(session.userAgent);
    const name =
      browser && os
        ? this.i18n.t('account.sessions.browserOn', { browser, os })
        : browser || os || this.i18n.t('account.sessions.unknownDevice');
    return { icon: mobile ? 'pi pi-mobile' : 'pi pi-desktop', name };
  }

  protected confirmRevoke(session: SessionView): void {
    this.confirm.confirm({
      header: this.i18n.t('account.sessions.revokeTitle'),
      message: this.i18n.t('account.sessions.revokeText', { device: this.device(session).name }),
      icon: 'pi pi-sign-out',
      acceptLabel: this.i18n.t('account.sessions.revoke'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => {
        this.revokingId.set(session.id);
        void this.revoke.run(session);
      },
    });
  }

  protected confirmLogoutEverywhere(): void {
    this.confirm.confirm({
      header: this.i18n.t('account.sessions.logoutEverywhereTitle'),
      message: this.i18n.t('account.sessions.logoutEverywhereText', { count: this.otherCount() }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('account.sessions.logoutEverywhere'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.logoutEverywhere.run(),
    });
  }

  protected logout(): void {
    this.auth.logout().subscribe({ error: () => undefined });
  }
}
