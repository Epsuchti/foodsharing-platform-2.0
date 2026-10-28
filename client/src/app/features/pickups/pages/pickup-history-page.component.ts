import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { PickupsService, UserPickupPage } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';

const PAGE_SIZE = 25;

/**
 * /me/pickups/history (own) and /users/:userId/pickups (admins, `mayAdministrateUser`) (PU6): past pickups of a user,
 * newest first, and "delete all future pickups" with a reason (e.g. when the user leaves).
 */
@Component({
  selector: 'fs-pickup-history-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, RequiredMessageDialogComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(own() ? 'pickups.past.title' : 'pickups.past.adminTitle') | t"
      [subtitle]="'pickups.past.subtitle' | t"
      icon="pi pi-history"
      [backLink]="own() ? '/pickups/mine' : ['/users', targetId()]"
    />
    <div class="toolbar">
      <p-button
        [label]="(own() ? 'pickups.past.deleteFutureOwn' : 'pickups.past.deleteFuture') | t"
        icon="pi pi-calendar-times"
        severity="danger"
        [outlined]="true"
        (onClick)="deleteOpen.set(true)"
        data-testid="pickups.past.deleteFuture"
      />
    </div>

    <fs-data-state
      [state]="pickups"
      skeleton="list"
      [emptyTitle]="'pickups.past.empty' | t"
      [emptyText]="'pickups.past.emptyText' | t"
      testId="pickups.past.state"
      data-testid="pickups.past.state"
    >
      <ng-template [fsData]="pickups" let-page>
        <p class="count" data-testid="pickups.past.count">{{ 'pickups.past.count' | t: { count: page.page.totalItems } }}</p>
        <ul class="cards">
          @for (pickup of page.items; track pickup.store.id + pickup.pickupAt) {
            <li class="fs-card item" data-testid="pickups.past.pickup" [attr.data-store]="pickup.store.name" [attr.data-pickup-at]="pickup.pickupAt">
              <a class="item__store" [routerLink]="['/stores', pickup.store.id]">{{ pickup.store.name }}</a>
              <span>{{ pickup.pickupAt | fsDate: 'dateTimeLong' }}</span>
            </li>
          }
        </ul>
        @if (page.items.length < page.page.totalItems) {
          <div class="more">
            <p-button [label]="'pickups.history.more' | t" icon="pi pi-angle-down" severity="secondary" [outlined]="true" (onClick)="loadMore()" data-testid="pickups.past.more" />
          </div>
        }
      </ng-template>
    </fs-data-state>

    <fs-required-message-dialog
      [(visible)]="deleteOpen"
      [title]="'pickups.past.deleteTitle' | t"
      [text]="'pickups.past.deleteText' | t"
      [label]="'pickups.past.reason' | t"
      [confirmLabel]="'pickups.past.deleteConfirm' | t"
      severity="danger"
      [minLength]="3"
      [busy]="deleteFuture.busy()"
      (confirm)="deleteFuture.run($event)"
      testId="pickups.past.deleteDialog"
    />
  `,
  styles: `
    :host { display: block; width: min(100%, 56rem); margin: 0 auto; }
    .toolbar { display: flex; justify-content: flex-end; margin-bottom: 1rem; }
    .count { margin: 0 0 0.6rem; color: var(--fs-text-muted); }
    .cards { display: grid; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
    .item { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.5rem 1rem; padding: 0.8rem 1rem; }
    .item__store { font-weight: 700; color: inherit; }
    .more { display: flex; justify-content: center; margin-top: 1rem; }
  `,
})
export class PickupHistoryPageComponent {
  private readonly api = inject(PickupsService);
  private readonly session = inject(SessionService);

  /** Route parameter of `/users/:userId/pickups`; absent on `/me/pickups/history`. */
  readonly userId = input<string | undefined>(undefined);

  protected readonly targetId = computed(() => this.userId() ?? this.session.user()?.id);
  protected readonly own = computed(() => !this.userId() || this.userId() === this.session.user()?.id);
  protected readonly deleteOpen = signal(false);
  private readonly size = signal(PAGE_SIZE);

  protected readonly pickups = loadResource<UserPickupPage, { userId: string; size: number }>({
    params: () => {
      const userId = this.targetId();
      return userId ? { userId, size: this.size() } : undefined;
    },
    loader: ({ userId, size }) => this.api.listPastPickups({ userId, page: 0, size }),
  });

  protected loadMore(): void {
    this.size.update((size) => size + PAGE_SIZE);
  }

  protected readonly deleteFuture = runMutation(
    (reason: string) => this.api.deleteAllFuturePickups({ userId: this.targetId()!, reasonBody: { reason } }),
    { successToastKey: 'pickups.past.deleted', onSuccess: () => this.deleteOpen.set(false) },
  );
}
