import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { forkJoin, map } from 'rxjs';

import { FspCleaningService, PickupsService, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { DATE_TILE } from '../../../shared/date-tile/date-tile.component';

interface NextEntry {
  name: string;
  link: string[];
  at: string;
  confirmed: boolean;
  cleaning: boolean;
}

/**
 * Dashboard card (A19 part of M3, decision 154, home rework 2026-11-04): my next three pickups or cleaning shifts as
 * date-tile rows (store, time, cleaning marker, confirmed / unconfirmed with icon and word) and the link to all of
 * them. The free-slot count moved to the "Help needed soon" card. Only for verified foodsavers and above (same rule
 * as the pickups navigation).
 */
@Component({
  selector: 'fs-my-next-pickups-card',
  imports: [...DATA_STATE_IMPORTS, ...DATE_TILE, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <section class="fs-card card" data-testid="dashboard.pickups" aria-labelledby="dashboard-pickups-title">
        <h2 class="title" id="dashboard-pickups-title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'pickups.dashboard.title' | t }}</h2>
        <fs-data-state
          [state]="mine"
          skeleton="list"
          [skeletonRows]="3"
          [compact]="true"
          [emptyTitle]="'pickups.dashboard.empty' | t"
          emptyIcon="pi pi-calendar"
          testId="dashboard.pickups.state"
          data-testid="dashboard.pickups.state"
        >
          <ng-template [fsData]="mine">
            <ul class="list" data-testid="dashboard.pickups.list">
              @for (pickup of next(); track pickup.link + pickup.at) {
                <li
                  class="row"
                  [attr.data-testid]="pickup.cleaning ? 'dashboard.pickups.cleaning' : 'dashboard.pickups.pickup'"
                  [attr.data-store]="pickup.name"
                  [attr.data-confirmed]="pickup.confirmed"
                >
                  <fs-date-tile [at]="pickup.at" />
                  <div class="row__body">
                    <a class="row__title" [routerLink]="pickup.link">{{ pickup.name }}</a>
                    <span class="row__meta">
                      <fs-tile-time [at]="pickup.at" />
                      @if (pickup.cleaning) {
                        <span class="kind"><i class="pi pi-sparkles" aria-hidden="true"></i>{{ 'pickups.cleaning.label' | t }}</span>
                      }
                      @if (pickup.confirmed) {
                        <span class="state state--ok" data-testid="dashboard.pickups.confirmed"><i class="pi pi-check" aria-hidden="true"></i>{{ 'pickups.tab.state.confirmed' | t }}</span>
                      } @else {
                        <span class="state state--wait" data-testid="dashboard.pickups.unconfirmed"><i class="pi pi-hourglass" aria-hidden="true"></i>{{ 'pickups.tab.state.unconfirmed' | t }}</span>
                      }
                    </span>
                  </div>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
        <div class="links">
          <a routerLink="/pickups" fragment="mine" data-testid="dashboard.pickups.all">{{ 'pickups.dashboard.all' | t }}</a>
        </div>
      </section>
    }
  `,
  styleUrl: '../../../shared/date-tile/date-rows.scss',
  styles: `
    .card { display: grid; gap: 0.75rem; padding: 1.1rem 1.2rem; }
    .title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    .title .pi { color: var(--fs-primary-strong); }
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

  protected readonly next = computed(() =>
    [...(this.mine.data() ?? [])].sort((a, b) => a.at.localeCompare(b.at)).slice(0, 3),
  );
}
