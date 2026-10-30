import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { forkJoin } from 'rxjs';

import { MyResourceLimits, Resource, ResourcesService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { ResourceFormDialogComponent } from '../components/resource-form-dialog.component';
import { ResourceListComponent } from '../components/resource-list.component';
import { mayAddMore } from '../resources.util';

interface MyResources {
  items: Resource[];
  limits: MyResourceLimits;
}

/**
 * /resources/mine (C6; `/resources`, the bell deep link, redirects here): the own resources (`listMyResources`) with
 * the "x of max" limit hint (`getMyResourceLimits`, loaded together so both refresh after a change); adding without a
 * region makes the resource visible in every region the user is a member of (A55). The server enforces the limit
 * (409 RESOURCE_LIMIT_REACHED).
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

    @if (resources.data(); as mine) {
      <p class="count" [class.count--full]="!canAdd()" data-testid="resources.mine.count" [attr.data-count]="mine.items.length" [attr.data-max]="limit()">
        {{ (canAdd() ? 'resources.mine.count' : 'resources.mine.limitReached') | t: { count: mine.items.length, max: limit() } }}
      </p>
    }

    <fs-data-state
      [state]="resources"
      skeleton="list"
      [emptyTitle]="'resources.mine.empty' | t"
      [emptyText]="'resources.mine.emptyHint' | t: { max: limit() }"
      emptyIcon="pi pi-th-large"
      [emptyActionLabel]="'resources.mine.add' | t"
      emptyActionIcon="pi pi-plus"
      (emptyAction)="adding.set(true)"
      data-testid="resources.mine.state"
    >
      <ng-template [fsData]="resources" let-mine>
        <fs-resource-list [resources]="mine.items" testId="resources.mine.list" (changed)="resources.reload()" />
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

  protected readonly adding = signal(false);
  protected readonly homeRegionId = computed(() => this.session.user()?.homeRegionId ?? null);

  protected readonly resources = loadResource<MyResources, true>({
    params: () => true,
    loader: () => forkJoin({ items: this.api.listMyResources(), limits: this.api.getMyResourceLimits() }),
    isEmpty: (mine) => mine.items.length === 0,
  });

  /** `app.resources.max-per-user` from the server (no region needed). */
  protected readonly limit = computed(() => this.resources.data()?.limits.maxPerUser ?? null);
  /** Adding needs the loaded limit; the count is the listed resources (the server answers 409 beyond it). */
  protected readonly canAdd = computed(() => {
    const mine = this.resources.data();
    return !!mine && mayAddMore(mine.items.length, mine.limits.maxPerUser);
  });
}
