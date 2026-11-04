import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Role, StoreListItem, StoresService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { storesNeedingPeople } from '../dashboard.util';

/**
 * Home card "Your stores" (owner feedback 2026-11-04, planner's addition): my store teams with free slots in the next
 * 7 days (`listMyStores`: `freeSlotStatus`, `freeSlotCount`, `nextPickupAt`), most urgent first, at most five; the
 * coloured dot always comes with the words. Verified foodsavers only; hidden when no team needs people.
 */
@Component({
  selector: 'fs-your-stores-card',
  imports: [...DATA_STATE_IMPORTS, DateTimePipe, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible() && stores.status() !== 'empty') {
      <section class="fs-card card" data-testid="dashboard.stores" aria-labelledby="dashboard-stores-title">
        <h2 class="title" id="dashboard-stores-title"><i class="pi pi-shopping-bag" aria-hidden="true"></i>{{ 'dashboard.stores.title' | t }}</h2>
        <fs-data-state [state]="stores" skeleton="list" [skeletonRows]="3" [compact]="true" testId="dashboard.stores.state" data-testid="dashboard.stores.state">
          <ng-template [fsData]="stores">
            <ul class="stores" data-testid="dashboard.stores.list">
              @for (store of needing(); track store.id) {
                <li class="store" data-testid="dashboard.stores.store" [attr.data-store]="store.name" [attr.data-status]="store.freeSlotStatus">
                  <div class="store__body">
                    <a class="store__name" [routerLink]="['/stores', store.id, 'pickups']">{{ store.name }}</a>
                    <span class="store__meta">
                      <span><span class="dot" [attr.data-status]="store.freeSlotStatus" aria-hidden="true"></span>{{ 'dashboard.stores.freeSlots' | t: { count: store.freeSlotCount } }}</span>
                      @if (store.nextPickupAt) {
                        <span>{{ 'dashboard.stores.next' | t: { date: (store.nextPickupAt | fsDate: 'dateTime') } }}</span>
                      }
                    </span>
                  </div>
                  <a class="store__open" [routerLink]="['/stores', store.id, 'pickups']" tabindex="-1" aria-hidden="true">
                    {{ 'dashboard.stores.open' | t }}<i class="pi pi-chevron-right" aria-hidden="true"></i>
                  </a>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
        <div class="links">
          <a routerLink="/stores/mine" data-testid="dashboard.stores.all">{{ 'dashboard.stores.all' | t }}</a>
        </div>
      </section>
    }
  `,
  styleUrl: './home-card.scss',
  styles: `
    .stores { display: grid; margin: 0; padding: 0; list-style: none; }
    .store { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 0.5rem 0.8rem; padding: 0.45rem 0; }
    .store + .store { border-top: 1px solid var(--fs-border); }
    .store__body { display: grid; gap: 0.1rem; min-width: 0; }
    .store__name { overflow: hidden; color: inherit; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
    .store__meta { display: flex; flex-wrap: wrap; gap: 0.15rem 0.6rem; color: var(--fs-text-muted); font-size: 0.88rem; }
    .store__open { display: inline-flex; gap: 0.3rem; align-items: center; color: var(--fs-primary-strong); font-size: 0.85rem; font-weight: 650; }
    .store__open .pi { font-size: 0.7rem; }
    .dot { display: inline-block; width: 0.55rem; height: 0.55rem; margin-right: 0.3rem; border-radius: 50%; vertical-align: middle; }
    .dot[data-status='RED'] { background: var(--fs-danger); }
    .dot[data-status='ORANGE'] { background: var(--fs-warning); }
    .dot[data-status='YELLOW'] { background: var(--fs-accent); }
    .dot[data-status='GREEN'] { background: var(--fs-primary); }
  `,
})
export class YourStoresCardComponent {
  private readonly api = inject(StoresService);
  private readonly session = inject(SessionService);

  protected readonly visible = computed(() => this.session.hasRole(Role.Foodsaver) && !!this.session.user()?.verified);

  protected readonly stores = loadResource<StoreListItem[], boolean>({
    params: () => (this.visible() ? true : undefined),
    loader: () => this.api.listMyStores({ excludeInactive: true }),
    isEmpty: (list) => storesNeedingPeople(list).length === 0,
  });

  protected readonly needing = computed(() => storesNeedingPeople(this.stores.data() ?? []));
}
