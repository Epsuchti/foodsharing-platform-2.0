import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { Resource, ResourcesService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { ResourceFormDialogComponent } from '../components/resource-form-dialog.component';
import { ResourceListComponent } from '../components/resource-list.component';
import { RESOURCE_LIMIT, mayAddMore } from '../resources.util';

/**
 * /resources/mine (C6; `/resources`, the bell deep link, redirects here): the own resources (`listMyResources`) with
 * the "x of 10" limit hint; adding without a region makes the resource visible in every region the user is a member
 * of (A55). The server enforces the limit (409 RESOURCE_LIMIT_REACHED).
 */
@Component({
  selector: 'fs-my-resources-page',
  imports: [...DATA_STATE_IMPORTS, Button, PageHeaderComponent, ResourceFormDialogComponent, ResourceListComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'resources.mine.title' | t" [subtitle]="'resources.mine.subtitle' | t" icon="pi pi-th-large">
      <p-button [label]="'resources.mine.favourites' | t" icon="pi pi-star" severity="secondary" [outlined]="true" routerLink="/resources/favourites" data-testid="resources.mine.favourites" />
      @if (homeRegionId(); as regionId) {
        <p-button [label]="'resources.mine.region' | t" icon="pi pi-map" severity="secondary" [outlined]="true" [routerLink]="['/regions', regionId, 'resources']" data-testid="resources.mine.region" />
      }
      <p-button [label]="'resources.mine.add' | t" icon="pi pi-plus" [disabled]="!canAdd()" (onClick)="adding.set(true)" data-testid="resources.mine.add" />
    </fs-page-header>

    @if (resources.data(); as list) {
      <p class="count" [class.count--full]="!canAdd()" data-testid="resources.mine.count" [attr.data-count]="list.length">
        {{ (canAdd() ? 'resources.mine.count' : 'resources.mine.limitReached') | t: { count: list.length, max: limit } }}
      </p>
    }

    <fs-data-state
      [state]="resources"
      skeleton="list"
      [emptyTitle]="'resources.mine.empty' | t"
      [emptyText]="'resources.mine.emptyHint' | t: { max: limit }"
      emptyIcon="pi pi-th-large"
      [emptyActionLabel]="'resources.mine.add' | t"
      emptyActionIcon="pi pi-plus"
      (emptyAction)="adding.set(true)"
      data-testid="resources.mine.state"
    >
      <ng-template [fsData]="resources" let-list>
        <fs-resource-list [resources]="list" testId="resources.mine.list" (changed)="resources.reload()" />
      </ng-template>
    </fs-data-state>

    <fs-resource-form-dialog [(visible)]="adding" (saved)="resources.reload()" />
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
    .count { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .count--full {
      padding: 0.6rem 0.8rem; border: 1px solid var(--fs-warning-border); border-radius: var(--fs-radius-sm);
      background: var(--fs-warning-soft); color: var(--fs-warning-text);
    }
  `,
})
export class MyResourcesPageComponent {
  private readonly api = inject(ResourcesService);
  private readonly session = inject(SessionService);

  protected readonly limit = RESOURCE_LIMIT;
  protected readonly adding = signal(false);
  protected readonly homeRegionId = computed(() => this.session.user()?.homeRegionId ?? null);

  protected readonly resources = loadResource<Resource[], true>({
    params: () => true,
    loader: () => this.api.listMyResources(),
  });

  protected readonly canAdd = computed(() => mayAddMore(this.resources.data()?.length ?? 0));
}
