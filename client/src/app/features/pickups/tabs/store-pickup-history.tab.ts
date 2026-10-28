import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';

import { PickupHistoryPage, PickupsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { StorePageContext } from '../../stores/store-page.context';
import { zonedToUtc } from '../pickups.util';

const PAGE_SIZE = 50;

interface HistoryQuery {
  storeId: string;
  from?: string;
  to?: string;
  size: number;
}

/**
 * /stores/:storeId/pickups/history (PU6, maySeePickupHistory: managers, coordinators, KAMs): who picked up when, for a
 * date range (default: the last 30 days, decided by the server); newest first.
 */
@Component({
  selector: 'fs-store-pickup-history-tab',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, InputText, RouterLink, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="toolbar" (ngSubmit)="apply()" data-testid="stores.pickupHistory.filter">
      <label class="field">
        <span>{{ 'pickups.history.from' | t }}</span>
        <input pInputText type="date" name="from" [(ngModel)]="fromDate" data-testid="stores.pickupHistory.from" />
      </label>
      <label class="field">
        <span>{{ 'pickups.history.to' | t }}</span>
        <input pInputText type="date" name="to" [(ngModel)]="toDate" data-testid="stores.pickupHistory.to" />
      </label>
      <p-button type="submit" [label]="'pickups.history.apply' | t" icon="pi pi-filter" size="small" data-testid="stores.pickupHistory.apply" />
      @if (ctx.may('maySeePickups')) {
        <p-button
          class="toolbar__back"
          [label]="'pickups.history.upcoming' | t"
          icon="pi pi-calendar"
          severity="secondary"
          [text]="true"
          size="small"
          [routerLink]="['/stores', ctx.storeId(), 'pickups']"
          data-testid="stores.pickupHistory.upcoming"
        />
      }
    </form>

    <fs-data-state
      [state]="history"
      skeleton="table"
      [emptyTitle]="'pickups.history.empty' | t"
      [emptyText]="'pickups.history.emptyText' | t"
      testId="stores.pickupHistory.state"
      data-testid="stores.pickupHistory.state"
    >
      <ng-template [fsData]="history">
        <section class="fs-card card">
          <table class="table" data-testid="stores.pickupHistory.table">
            <thead>
              <tr>
                <th scope="col">{{ 'pickups.history.date' | t }}</th>
                <th scope="col">{{ 'pickups.history.people' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (pickup of items(); track pickup.pickupAt) {
                <tr data-testid="stores.pickupHistory.row" [attr.data-pickup-at]="pickup.pickupAt">
                  <td class="when">{{ pickup.pickupAt | fsDate: 'dateTime' : ctx.timeZone() }}</td>
                  <td>
                    <ul class="people">
                      @for (slot of pickup.occupiedSlots; track slot.user.id) {
                        <li data-testid="stores.pickupHistory.person" [attr.data-user]="slot.user.displayName">
                          <fs-user-chip [name]="slot.user.displayName" [userId]="slot.user.id" [avatarUrl]="slot.user.avatarUrl" />
                        </li>
                      } @empty {
                        <li class="muted">{{ 'pickups.history.nobody' | t }}</li>
                      }
                    </ul>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </section>
        @if (hasMore()) {
          <div class="more">
            <p-button [label]="'pickups.history.more' | t" icon="pi pi-angle-down" severity="secondary" [outlined]="true" (onClick)="loadMore()" data-testid="stores.pickupHistory.more" />
          </div>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
    .toolbar { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.6rem; }
    .toolbar__back { margin-left: auto; }
    .field { display: grid; gap: 0.25rem; font-size: 0.88rem; font-weight: 600; }
    .card { padding: 0.5rem 1rem; overflow-x: auto; }
    .table { width: 100%; border-collapse: collapse; }
    .table th, .table td { padding: 0.5rem 0.25rem; border-bottom: 1px solid var(--fs-border); text-align: left; vertical-align: top; }
    .when { white-space: nowrap; font-variant-numeric: tabular-nums; }
    .people { display: flex; flex-wrap: wrap; gap: 0.3rem 0.8rem; margin: 0; padding: 0; list-style: none; }
    .muted { color: var(--fs-text-muted); }
    .more { display: flex; justify-content: center; }
  `,
})
export class StorePickupHistoryTab {
  protected readonly ctx = inject(StorePageContext);
  private readonly api = inject(PickupsService);

  protected fromDate = '';
  protected toDate = '';
  private readonly range = signal<{ from?: string; to?: string }>({});
  private readonly size = signal(PAGE_SIZE);

  protected readonly history = loadResource<PickupHistoryPage, HistoryQuery>({
    params: () => {
      const storeId = this.ctx.storeId();
      return storeId ? { storeId, ...this.range(), size: this.size() } : undefined;
    },
    loader: (query) => this.api.getStorePickupHistory({ ...query, page: 0 }),
  });

  /** Newest first. */
  protected readonly items = computed(() =>
    [...(this.history.data()?.items ?? [])].sort((a, b) => b.pickupAt.localeCompare(a.pickupAt)),
  );
  protected readonly hasMore = computed(() => {
    const page = this.history.data();
    return !!page && page.items.length < page.page.totalItems;
  });

  /** Range in the store's time zone: start of the first day … end of the last day. */
  protected apply(): void {
    const tz = this.ctx.timeZone();
    this.size.set(PAGE_SIZE);
    this.range.set({
      ...(this.fromDate ? { from: zonedToUtc(this.fromDate, '00:00', tz) } : {}),
      ...(this.toDate ? { to: zonedToUtc(this.toDate, '23:59', tz) } : {}),
    });
  }

  protected loadMore(): void {
    this.size.update((size) => size + PAGE_SIZE);
  }
}
