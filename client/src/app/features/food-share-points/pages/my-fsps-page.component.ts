import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { FoodSharePointsService, FspSummary } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { FspCardComponent } from '../components/fsp-card.component';

/** /food-share-points/mine (FS1): the food share points the user follows or manages (`listMyFoodSharePoints`). */
@Component({
  selector: 'fs-my-fsps-page',
  imports: [...DATA_STATE_IMPORTS, Button, FspCardComponent, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'foodSharePoints.mine.title' | t" [subtitle]="'foodSharePoints.mine.subtitle' | t" icon="pi pi-box">
      @if (homeRegionId(); as regionId) {
        <p-button
          [label]="'foodSharePoints.mine.region' | t"
          icon="pi pi-map"
          severity="secondary"
          [outlined]="true"
          [routerLink]="['/regions', regionId, 'food-share-points']"
          data-testid="foodSharePoints.mine.region"
        />
      }
    </fs-page-header>

    <fs-data-state
      [state]="fsps"
      skeleton="list"
      [emptyTitle]="'foodSharePoints.mine.empty' | t"
      [emptyText]="'foodSharePoints.mine.emptyHint' | t"
      emptyIcon="pi pi-box"
      data-testid="foodSharePoints.mine.state"
    >
      <ng-template [fsData]="fsps" let-list>
        <ul class="list" data-testid="foodSharePoints.mine.list">
          @for (fsp of list; track fsp.id) {
            <li><fs-fsp-card [fsp]="fsp" /></li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; }
    .list { display: grid; gap: 0.75rem; margin: 0; padding: 0; list-style: none; grid-template-columns: repeat(auto-fill, minmax(min(100%, 22rem), 1fr)); }
  `,
})
export class MyFspsPageComponent {
  private readonly api = inject(FoodSharePointsService);
  private readonly session = inject(SessionService);

  protected readonly homeRegionId = computed(() => this.session.user()?.homeRegionId ?? null);

  protected readonly fsps = loadResource<FspSummary[], true>({
    params: () => true,
    loader: () => this.api.listMyFoodSharePoints(),
  });
}
