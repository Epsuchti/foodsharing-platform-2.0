import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of } from 'rxjs';

import { MessagingService, PickupsService, Role, StoresService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { WaitingRow, waitingRows } from '../dashboard.util';

/**
 * Home card "Waiting for you" (owner feedback 2026-11-04): store invitations, unread conversations, own unconfirmed
 * pickups and own pending applications, each a link with its count (existing endpoints only). Stores and pickups
 * only for verified foodsavers; hidden when every count is 0. The two warning rows wait on the team.
 */
@Component({
  selector: 'fs-waiting-card',
  imports: [...DATA_STATE_IMPORTS, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (rows.status() !== 'empty') {
      <section class="fs-card card" data-testid="dashboard.waiting" aria-labelledby="dashboard-waiting-title">
        <h2 class="title" id="dashboard-waiting-title"><i class="pi pi-inbox" aria-hidden="true"></i>{{ 'dashboard.waiting.title' | t }}</h2>
        <fs-data-state [state]="rows" skeleton="list" [skeletonRows]="3" [compact]="true" testId="dashboard.waiting.state" data-testid="dashboard.waiting.state">
          <ng-template [fsData]="rows" let-list>
            <ul class="wait">
              @for (row of asRows(list); track row.id) {
                <li>
                  <a class="wait__row" [routerLink]="row.link" [fragment]="row.fragment" [attr.data-testid]="'dashboard.waiting.' + row.id" [attr.data-count]="row.count">
                    <span class="wait__icon" [class.wait__icon--warn]="row.warn" aria-hidden="true"><i [class]="row.icon"></i></span>
                    <span class="wait__text">{{ 'dashboard.waiting.' + row.id | t: { count: row.count } }}</span>
                    <span class="wait__count" [class.wait__count--warn]="row.warn">{{ row.count }}</span>
                    <i class="pi pi-chevron-right wait__chev" aria-hidden="true"></i>
                  </a>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </section>
    }
  `,
  styleUrl: './home-card.scss',
  styles: `
    .wait { display: grid; margin: 0; padding: 0; list-style: none; }
    .wait li + li { border-top: 1px solid var(--fs-border); }
    .wait__row {
      display: flex; align-items: center; gap: 0.8rem; min-height: 2.75rem; padding: 0.45rem 0; color: var(--fs-text);
      text-decoration: none;
    }
    .wait__row:hover .wait__text { text-decoration: underline; }
    .wait__icon {
      display: grid; flex: none; width: 2.25rem; height: 2.25rem; place-items: center; border-radius: var(--fs-radius-sm);
      background: var(--fs-primary-soft); color: var(--fs-primary-strong);
    }
    .wait__icon--warn { background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .wait__text { flex: 1; min-width: 0; font-weight: 600; }
    .wait__count {
      display: inline-grid; place-items: center; min-width: 1.5rem; height: 1.5rem; padding: 0 0.4rem; border-radius: 999px;
      background: var(--fs-primary); color: var(--fs-on-primary); font-size: 0.8rem; font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .wait__count--warn { background: var(--fs-warning); color: var(--fs-on-warning); }
    .wait__chev { color: var(--fs-text-muted); font-size: 0.8rem; }
  `,
})
export class WaitingCardComponent {
  private readonly stores = inject(StoresService);
  private readonly messaging = inject(MessagingService);
  private readonly pickups = inject(PickupsService);
  private readonly session = inject(SessionService);

  /** Stores and pickups follow the pickups navigation rule (verified foodsavers). */
  private readonly storeRows = computed(() => this.session.hasRole(Role.Foodsaver) && !!this.session.user()?.verified);

  protected readonly rows = loadResource<WaitingRow[], boolean>({
    params: () => (this.session.user() ? this.storeRows() : undefined),
    loader: (withStores) =>
      forkJoin([
        withStores ? this.stores.listMyStores({ excludeInactive: true }) : of(null),
        this.messaging.getConversationUnreadCount(),
        withStores ? this.pickups.listMyPickups() : of(null),
      ]).pipe(
        map(([stores, unread, pickups]) => waitingRows({ stores, unreadConversations: unread.conversations, pickups })),
      ),
    isEmpty: (rows) => rows.length === 0,
  });

  protected asRows(list: unknown): WaitingRow[] {
    return list as WaitingRow[];
  }
}
