import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BasketSummary, BasketsService, FeatureToggleKey, UserProfile, UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { FeatureToggleService } from '../../../core/feature-toggles/feature-toggle.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { BasketCardComponent } from '../../baskets/components/basket-card.component';
import { profilePosition } from '../../baskets/baskets.util';
import { NEARBY_BASKETS_KM, nearestBaskets } from '../dashboard.util';

/**
 * Dashboard card (A19, B2): the three nearest open food baskets around the profile's home coordinates
 * (`listNearbyBaskets`, nearest first). Without coordinates it links to the profile address; hidden while the BASKETS
 * feature is off.
 */
@Component({
  selector: 'fs-nearby-baskets-card',
  imports: [...DATA_STATE_IMPORTS, BasketCardComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (enabled()) {
      <section class="fs-card card" data-testid="dashboard.baskets" aria-labelledby="dashboard-baskets-title">
        <h2 class="title" id="dashboard-baskets-title"><i class="pi pi-shopping-cart" aria-hidden="true"></i>{{ 'dashboard.baskets.title' | t }}</h2>
        <fs-data-state [state]="me" skeleton="list" [skeletonRows]="1" [compact]="true" data-testid="dashboard.baskets.profileState">
          <ng-template [fsData]="me">
            @if (position()) {
              <fs-data-state
                [state]="nearby"
                skeleton="list"
                [skeletonRows]="2"
                [compact]="true"
                [emptyTitle]="'dashboard.baskets.empty' | t: { km }"
                emptyIcon="pi pi-shopping-cart"
                data-testid="dashboard.baskets.state"
              >
                <ng-template [fsData]="nearby">
                  <ul class="list" data-testid="dashboard.baskets.list">
                    @for (basket of nearest(); track basket.id) {
                      <li><fs-basket-card [basket]="basket" /></li>
                    }
                  </ul>
                </ng-template>
              </fs-data-state>
            } @else {
              <p class="muted" data-testid="dashboard.baskets.noPosition">{{ 'dashboard.baskets.noPosition' | t }}</p>
              <a class="more" routerLink="/account/profile" data-testid="dashboard.baskets.addAddress">{{ 'dashboard.baskets.addAddress' | t }}</a>
            }
          </ng-template>
        </fs-data-state>
        <div class="links">
          <a routerLink="/baskets" data-testid="dashboard.baskets.all">{{ 'dashboard.baskets.all' | t }}</a>
          <a routerLink="/baskets/new" data-testid="dashboard.baskets.offer">{{ 'dashboard.baskets.offer' | t }}</a>
        </div>
      </section>
    }
  `,
  styles: `
    .card {
      display: grid;
      gap: 0.75rem;
      padding: 1.1rem 1.2rem;
    }
    .title {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      margin: 0;
      font-size: 1.05rem;
    }
    .list {
      display: grid;
      gap: 0.6rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .muted {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .more,
    .links a {
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
    .links {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
    }
  `,
})
export class NearbyBasketsCardComponent {
  private readonly users = inject(UsersService);
  private readonly baskets = inject(BasketsService);
  private readonly toggles = inject(FeatureToggleService);

  protected readonly km = NEARBY_BASKETS_KM;
  protected readonly enabled = computed(() => this.toggles.isEnabled(FeatureToggleKey.Baskets));

  protected readonly me = loadResource<UserProfile, true>({
    params: () => (this.enabled() ? true : undefined),
    loader: () => this.users.getCurrentUser(),
    isEmpty: () => false,
  });
  protected readonly position = computed(() => profilePosition(this.me.data()));
  protected readonly nearby = loadResource<BasketSummary[], [number, number]>({
    params: () => this.position() ?? undefined,
    loader: ([lat, lon]) => this.baskets.listNearbyBaskets({ lat, lon, distanceKm: NEARBY_BASKETS_KM }),
  });
  protected readonly nearest = computed(() => nearestBaskets(this.nearby.data() ?? []));
}
