import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { BasketSummary, BasketsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { BasketCardComponent } from '../components/basket-card.component';

/** /baskets/mine (B1, B2): own baskets with their request counts and the baskets the user requested. */
@Component({
  selector: 'fs-my-baskets-page',
  imports: [...DATA_STATE_IMPORTS, BasketCardComponent, Button, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'baskets.mine.title' | t" icon="pi pi-shopping-cart" backLink="/baskets" [backLabel]="'baskets.nearby.title' | t">
      <p-button [label]="'baskets.nearby.offer' | t" icon="pi pi-plus" routerLink="/baskets/new" data-testid="baskets.mine.offer" />
    </fs-page-header>

    <div class="cols">
      <section class="block" aria-labelledby="baskets-mine-offered">
        <h2 id="baskets-mine-offered" class="block__title"><i class="pi pi-upload" aria-hidden="true"></i> {{ 'baskets.mine.offered' | t }}</h2>
        <fs-data-state
          [state]="offered"
          skeleton="list"
          [emptyTitle]="'baskets.mine.offeredEmpty' | t"
          emptyIcon="pi pi-shopping-cart"
          [emptyActionLabel]="'baskets.nearby.offer' | t"
          emptyActionIcon="pi pi-plus"
          (emptyAction)="offer()"
          data-testid="baskets.mine.offeredState"
        >
          <ng-template [fsData]="offered" let-baskets>
            <ul class="list" data-testid="baskets.mine.offeredList">
              @for (basket of baskets; track basket.id) {
                <li><fs-basket-card [basket]="basket" [showOwner]="false" [showRequests]="true" /></li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </section>

      <section class="block" aria-labelledby="baskets-mine-requested">
        <h2 id="baskets-mine-requested" class="block__title"><i class="pi pi-send" aria-hidden="true"></i> {{ 'baskets.mine.requested' | t }}</h2>
        <fs-data-state
          [state]="requested"
          skeleton="list"
          [emptyTitle]="'baskets.mine.requestedEmpty' | t"
          emptyIcon="pi pi-send"
          data-testid="baskets.mine.requestedState"
        >
          <ng-template [fsData]="requested" let-baskets>
            <ul class="list" data-testid="baskets.mine.requestedList">
              @for (basket of baskets; track basket.id) {
                <li><fs-basket-card [basket]="basket" /></li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </section>
    </div>
  `,
  styles: `
    :host { display: block; }
    .cols { display: grid; gap: 1.5rem; }
    @media (min-width: 60rem) { .cols { grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; } }
    .block { display: grid; gap: 0.75rem; min-width: 0; }
    .block__title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.1rem; }
    .block__title .pi { color: var(--fs-primary-strong); }
    .list { display: grid; gap: 0.75rem; margin: 0; padding: 0; list-style: none; }
  `,
})
export class MyBasketsPageComponent {
  private readonly api = inject(BasketsService);
  private readonly router = inject(Router);

  protected readonly offered = loadResource<BasketSummary[], true>({
    params: () => true,
    loader: () => this.api.listMyBaskets(),
  });
  protected readonly requested = loadResource<BasketSummary[], true>({
    params: () => true,
    loader: () => this.api.listRequestedBaskets(),
  });

  protected offer(): void {
    void this.router.navigate(['/baskets/new']);
  }
}
