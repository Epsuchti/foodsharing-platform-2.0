import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { Resource, ResourcesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { ResourceListComponent } from '../components/resource-list.component';

/** /resources/favourites (C6): resources marked with the star (`listFavouriteResources`; invisible ones are left out). */
@Component({
  selector: 'fs-favourite-resources-page',
  imports: [...DATA_STATE_IMPORTS, Button, PageHeaderComponent, ResourceListComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'resources.favourites.title' | t" [subtitle]="'resources.favourites.subtitle' | t" icon="pi pi-star">
      <p-button [label]="'resources.favourites.mine' | t" icon="pi pi-user" severity="secondary" [outlined]="true" routerLink="/resources/mine" data-testid="resources.favourites.mine" />
    </fs-page-header>

    <fs-data-state
      [state]="resources"
      skeleton="list"
      [emptyTitle]="'resources.favourites.empty' | t"
      [emptyText]="'resources.favourites.emptyHint' | t"
      emptyIcon="pi pi-star"
      data-testid="resources.favourites.state"
    >
      <ng-template [fsData]="resources" let-list>
        <fs-resource-list [resources]="list" testId="resources.favourites.list" (changed)="resources.reload()" />
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
  `,
})
export class FavouriteResourcesPageComponent {
  private readonly api = inject(ResourcesService);

  protected readonly resources = loadResource<Resource[], true>({
    params: () => true,
    loader: () => this.api.listFavouriteResources(),
  });
}
