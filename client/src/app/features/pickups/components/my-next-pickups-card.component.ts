import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PickupOption, PickupsService, Role, UserPickup } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/**
 * Dashboard card (A19 part of M3): my next three pickups and the number of pickups with free slots in my stores.
 * Only for verified foodsavers and above (same rule as the pickups navigation).
 */
@Component({
  selector: 'fs-my-next-pickups-card',
  imports: [...DATA_STATE_IMPORTS, DateTimePipe, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <section class="fs-card card" data-testid="dashboard.pickups">
        <h2 class="title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'pickups.dashboard.title' | t }}</h2>
        <fs-data-state
          [state]="mine"
          skeleton="list"
          [emptyTitle]="'pickups.dashboard.empty' | t"
          testId="dashboard.pickups.state"
          data-testid="dashboard.pickups.state"
        >
          <ng-template [fsData]="mine">
            <ul class="list">
              @for (pickup of next(); track pickup.store.id + pickup.pickupAt) {
                <li data-testid="dashboard.pickups.pickup" [attr.data-store]="pickup.store.name">
                  <a [routerLink]="['/stores', pickup.store.id, 'pickups']">{{ pickup.store.name }}</a>
                  <span class="when">{{ pickup.pickupAt | fsDate: 'dateTime' }}</span>
                  @if (!pickup.confirmed) {
                    <i class="pi pi-hourglass wait" role="img" [attr.aria-label]="'pickups.tab.waiting' | t"></i>
                  }
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
        <div class="links">
          <a routerLink="/pickups/mine" data-testid="dashboard.pickups.all">{{ 'pickups.dashboard.all' | t }}</a>
          @if (freeCount(); as count) {
            <a routerLink="/pickups" data-testid="dashboard.pickups.free">{{ 'pickups.dashboard.free' | t: { count } }}</a>
          }
        </div>
      </section>
    }
  `,
  styles: `
    .card { display: grid; gap: 0.75rem; padding: 1.1rem 1.2rem; }
    .title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    .list { display: grid; gap: 0.4rem; margin: 0; padding: 0; list-style: none; }
    .list li { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: baseline; }
    .when { color: var(--fs-text-muted); font-size: 0.88rem; font-variant-numeric: tabular-nums; }
    .wait { color: var(--fs-warning-text); }
    .links { display: flex; flex-wrap: wrap; gap: 1rem; font-weight: 600; }
  `,
})
export class MyNextPickupsCardComponent {
  private readonly api = inject(PickupsService);
  private readonly session = inject(SessionService);

  protected readonly visible = computed(
    () => this.session.hasAnyRole([Role.Foodsaver]) && !!this.session.user()?.verified,
  );

  protected readonly mine = loadResource<UserPickup[], boolean>({
    params: () => (this.visible() ? true : undefined),
    loader: () => this.api.listMyPickups(),
    isEmpty: (list) => list.length === 0,
  });

  private readonly options = loadResource<PickupOption[], boolean>({
    params: () => (this.visible() ? true : undefined),
    loader: () => this.api.listPickupOptions(),
  });

  protected readonly next = computed(() =>
    [...(this.mine.data() ?? [])].sort((a, b) => a.pickupAt.localeCompare(b.pickupAt)).slice(0, 3),
  );
  protected readonly freeCount = computed(
    () => (this.options.data() ?? []).filter((option) => option.occupiedSlots.length < option.slotCount).length,
  );
}
