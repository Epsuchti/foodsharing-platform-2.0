import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { forkJoin, map } from 'rxjs';

import { FspCleaningService, PickupOption, PickupsService, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

interface NextEntry {
  name: string;
  link: string[];
  at: string;
  confirmed: boolean;
  cleaning: boolean;
}

/**
 * Dashboard card (A19 part of M3, decision 154): my next three pickups or cleaning shifts and the number of pickups with free slots in my stores.
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
              @for (pickup of next(); track pickup.link + pickup.at) {
                <li [attr.data-testid]="pickup.cleaning ? 'dashboard.pickups.cleaning' : 'dashboard.pickups.pickup'" [attr.data-store]="pickup.name">
                  <a [routerLink]="pickup.link">{{ pickup.name }}</a>
                  @if (pickup.cleaning) {
                    <span class="when">{{ 'pickups.cleaning.label' | t }}</span>
                  }
                  <span class="when">{{ pickup.at | fsDate: 'dateTime' }}</span>
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
    .links { display: flex; flex-wrap: wrap; gap: 0 1rem; font-weight: 600; }
    @media (pointer: coarse) {
      .links a { display: inline-flex; align-items: center; min-height: 2.75rem; }
    }
  `,
})
export class MyNextPickupsCardComponent {
  private readonly api = inject(PickupsService);
  private readonly cleaningApi = inject(FspCleaningService);
  private readonly session = inject(SessionService);

  protected readonly visible = computed(
    () => this.session.hasAnyRole([Role.Foodsaver]) && !!this.session.user()?.verified,
  );

  protected readonly mine = loadResource<NextEntry[], boolean>({
    params: () => (this.visible() ? true : undefined),
    loader: () =>
      forkJoin([this.api.listMyPickups(), this.cleaningApi.listMyCleaningShifts()]).pipe(
        map(([pickups, shifts]) => [
          ...pickups.map((pickup) => ({
            name: pickup.store.name,
            link: ['/stores', pickup.store.id, 'pickups'],
            at: pickup.pickupAt,
            confirmed: pickup.confirmed,
            cleaning: false,
          })),
          ...shifts.map((shift) => ({
            name: shift.fsp.name,
            link: ['/food-share-points', shift.fsp.id, 'cleaning'],
            at: shift.shiftAt,
            confirmed: shift.confirmed,
            cleaning: true,
          })),
        ]),
      ),
    isEmpty: (list) => list.length === 0,
  });

  private readonly options = loadResource<PickupOption[], boolean>({
    params: () => (this.visible() ? true : undefined),
    loader: () => this.api.listPickupOptions(),
  });

  protected readonly next = computed(() =>
    [...(this.mine.data() ?? [])].sort((a, b) => a.at.localeCompare(b.at)).slice(0, 3),
  );
  protected readonly freeCount = computed(
    () => (this.options.data() ?? []).filter((option) => option.occupiedSlots.length < option.slotCount).length,
  );
}
