import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AccountResources } from '../account.resource';
import { AccountTabsComponent } from '../components/account-tabs.component';

export const SECURITY_LOG_PAGE_SIZE = 20;

/**
 * /account/security-log: logins, method changes and revocations of the own account (A22, plan §10.2 "Login &
 * security log"), shown with the shared history timeline (GET /api/auth/security-log; pages only, no filters).
 */
@Component({
  selector: 'fs-security-log-page',
  imports: [AccountTabsComponent, HistoryTimelineComponent, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'account.securityLog.title' | t"
      [subtitle]="'account.securityLog.subtitle' | t"
      icon="pi pi-shield"
    />
    <fs-account-tabs />

    <fs-history-timeline
      [source]="resources.securityLog"
      [filters]="{}"
      [pageSize]="pageSize"
      [viewerId]="userId()"
      [emptyTitle]="'account.securityLog.empty' | t"
      [emptyText]="'account.securityLog.emptyText' | t"
      testId="account.securityLog"
    />

    <a
      class="more-link"
      routerLink="/me/activity"
      [queryParams]="{ preset: 'logins' }"
      data-testid="account.securityLog.allActivity"
    >
      <i class="pi pi-history" aria-hidden="true"></i>
      <span>{{ 'account.securityLog.allActivity' | t }}</span>
      <i class="pi pi-arrow-right" aria-hidden="true"></i>
    </a>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 60rem);
      margin: 0 auto;
    }
    fs-account-tabs {
      margin-bottom: 1.25rem;
    }
    .more-link {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      margin-top: 1.5rem;
      color: var(--fs-primary-strong);
      font-weight: 650;
      text-decoration: none;
    }
    .more-link:hover {
      text-decoration: underline;
    }
    .more-link .pi-arrow-right {
      font-size: 0.8rem;
    }
  `,
})
export class SecurityLogPageComponent {
  private readonly session = inject(SessionService);
  protected readonly resources = inject(AccountResources);
  protected readonly pageSize = SECURITY_LOG_PAGE_SIZE;
  protected readonly userId = computed(() => this.session.user()?.id ?? null);
}
