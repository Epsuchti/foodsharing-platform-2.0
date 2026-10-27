import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { AuthService, HistoryActorType, HistoryEntry, HistoryEntryPage } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AccountTabsComponent } from '../components/account-tabs.component';

export const SECURITY_LOG_PAGE_SIZE = 20;

/** Icon and tone per auth action; unknown actions get the default. */
const ACTION_STYLE: Record<string, { icon: string; tone: 'ok' | 'warn' | 'neutral' }> = {
  LOGIN: { icon: 'pi pi-sign-in', tone: 'ok' },
  LOGOUT: { icon: 'pi pi-sign-out', tone: 'neutral' },
  LOGIN_FAILED: { icon: 'pi pi-exclamation-triangle', tone: 'warn' },
  SESSION_REVOKED: { icon: 'pi pi-times-circle', tone: 'neutral' },
  SESSIONS_REVOKED_ALL: { icon: 'pi pi-power-off', tone: 'warn' },
  ACCOUNT_LINKED: { icon: 'pi pi-link', tone: 'ok' },
  METHOD_REMOVED: { icon: 'pi pi-minus-circle', tone: 'warn' },
  PASSKEY_REGISTERED: { icon: 'pi pi-key', tone: 'ok' },
  PASSKEY_DELETED: { icon: 'pi pi-trash', tone: 'warn' },
  PASSKEY_RENAMED: { icon: 'pi pi-pencil', tone: 'neutral' },
  MAGIC_LINK_REQUESTED: { icon: 'pi pi-envelope', tone: 'neutral' },
};
const DEFAULT_STYLE = { icon: 'pi pi-shield', tone: 'neutral' as const };

/**
 * /account/security-log — logins, method changes and revocations of the own account (A22, GET
 * /api/auth/security-log). A simple paged list owned by this feature; the generic `fs-history-timeline`
 * (T7) can replace it later.
 */
@Component({
  selector: 'fs-security-log-page',
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
      [title]="'account.securityLog.title' | t"
      [subtitle]="'account.securityLog.subtitle' | t"
      icon="pi pi-shield"
    />
    <fs-account-tabs />

    <section class="fs-card card" aria-labelledby="log-title">
      <h2 id="log-title" class="fs-visually-hidden">{{ 'account.securityLog.title' | t }}</h2>
      <fs-data-state
        [state]="log"
        skeleton="list"
        [skeletonRows]="5"
        emptyIcon="pi pi-shield"
        [emptyTitle]="'account.securityLog.empty' | t"
        [emptyText]="'account.securityLog.emptyText' | t"
        data-testid="account.securityLog.list"
      >
        <ng-template [fsData]="log" let-page>
          <ol class="log">
            @for (entry of page.items; track entry.id) {
              <li
                class="entry"
                [attr.data-tone]="style(entry).tone"
                [attr.data-action]="entry.action"
                data-testid="account.securityLog.entry"
              >
                <span class="entry__icon" aria-hidden="true"
                  ><i [class]="style(entry).icon"></i
                ></span>
                <span class="entry__main">
                  <span class="entry__title">{{ label(entry) }}</span>
                  <span class="entry__meta">
                    <time [attr.datetime]="entry.occurredAt">{{
                      entry.occurredAt | fsDate: 'dateTime'
                    }}</time>
                    @if (byOther(entry); as name) {
                      · {{ 'account.securityLog.by' | t: { name } }}
                    }
                  </span>
                </span>
              </li>
            }
          </ol>
          @if (hasMore()) {
            <div class="more">
              @if (more.error()) {
                <p class="more__error" role="alert" data-testid="account.securityLog.moreError">
                  <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
                  {{ 'account.securityLog.moreFailed' | t }}
                </p>
              }
              <p-button
                [label]="
                  (more.error() ? 'account.securityLog.retry' : 'account.securityLog.more') | t
                "
                [icon]="more.error() ? 'pi pi-refresh' : 'pi pi-angle-down'"
                severity="secondary"
                [outlined]="true"
                [loading]="more.busy()"
                (onClick)="loadMore()"
                data-testid="account.securityLog.more"
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
    .log {
      position: relative;
      display: grid;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .entry {
      position: relative;
      display: flex;
      align-items: flex-start;
      gap: 0.85rem;
      padding: 0.7rem 0;
    }
    .entry:not(:last-child)::before {
      position: absolute;
      top: 3rem;
      bottom: -0.6rem;
      left: 1.2rem;
      width: 2px;
      background: var(--fs-border);
      content: '';
    }
    .entry__icon {
      position: relative;
      display: grid;
      width: 2.4rem;
      height: 2.4rem;
      flex: 0 0 auto;
      place-items: center;
      border-radius: 50%;
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
    }
    .entry[data-tone='ok'] .entry__icon {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .entry[data-tone='warn'] .entry__icon {
      background: var(--fs-warning-soft);
      color: var(--fs-warning);
    }
    .entry__main {
      display: grid;
      min-width: 0;
      gap: 0.1rem;
      padding-top: 0.2rem;
    }
    .entry__title {
      font-weight: 650;
      overflow-wrap: anywhere;
    }
    .entry__meta {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .more {
      display: grid;
      justify-items: center;
      gap: 0.5rem;
      padding-top: 0.75rem;
    }
    .more__error {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin: 0;
      color: var(--fs-danger);
      font-size: 0.9rem;
      font-weight: 600;
    }
  `,
})
export class SecurityLogPageComponent {
  private readonly authApi = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);

  protected readonly log = loadResource({
    loader: () => this.authApi.getSecurityLog({ page: 0, size: SECURITY_LOG_PAGE_SIZE }),
  });

  protected readonly hasMore = computed(() => {
    const page = this.log.data()?.page;
    return !!page && page.page + 1 < page.totalPages;
  });

  /** Next page; a failure keeps the loaded entries and offers a retry. */
  protected readonly more = runMutation(
    (page: number) => this.authApi.getSecurityLog({ page, size: SECURITY_LOG_PAGE_SIZE }),
    {
      errorToast: false,
      onSuccess: (next: HistoryEntryPage) =>
        this.log.update((current) => ({
          items: [...(current?.items ?? []), ...next.items],
          page: next.page,
        })),
    },
  );

  protected loadMore(): void {
    const page = this.log.data()?.page;
    if (page) {
      void this.more.run(page.page + 1);
    }
  }

  protected style(entry: HistoryEntry) {
    return ACTION_STYLE[entry.action] ?? DEFAULT_STYLE;
  }

  /** "Logged in with Google", "Passkey “MacBook” added", … */
  protected label(entry: HistoryEntry): string {
    const metadata = (entry.metadata ?? {}) as Record<string, unknown>;
    const method = String(metadata['method'] ?? metadata['provider'] ?? '');
    const key = `account.securityLog.action.${entry.action}`;
    if (!this.i18n.has(key)) {
      return this.i18n.t('account.securityLog.action.other', { action: entry.action });
    }
    return this.i18n.t(key, {
      method: method ? this.i18n.t(`account.securityLog.method.${method}`) : '',
      label: String(metadata['label'] ?? ''),
      reason: this.i18n.t(`account.securityLog.reason.${String(metadata['reason'] ?? 'other')}`),
    });
  }

  /** Name of the actor when someone else (or the system) did it. */
  protected byOther(entry: HistoryEntry): string | null {
    if (entry.actorType === HistoryActorType.User && entry.actor?.id === this.session.user()?.id) {
      return null;
    }
    if (entry.actorType !== HistoryActorType.User) {
      return this.i18n.t('account.securityLog.system');
    }
    return entry.actor?.displayName ?? null;
  }
}
