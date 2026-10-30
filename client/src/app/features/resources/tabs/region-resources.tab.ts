import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { firstValueFrom } from 'rxjs';

import { Resource, ResourcePermissions, ResourcesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { ErrorStateComponent } from '../../../shared/data-state/error-state.component';
import { RegionPageContext } from '../../regions/region-page.context';
import { ResourceFormDialogComponent } from '../components/resource-form-dialog.component';
import { ResourceListComponent } from '../components/resource-list.component';
import { inCategory, splitCommons, usedCategories } from '../resources.util';

interface RegionResources {
  resources: Resource[];
  permissions: ResourcePermissions;
}

/**
 * /regions/:regionId/resources (C6, R3): the resource mosaic of a region — its commons (managed by the RESOURCES
 * group) and what its members lend or give away (`listRegionResources`), filtered by category chips. Adding follows
 * `getResourcePermissions` (`mayAdd` = below the per-user limit, `mayEditCommons` = RESOURCES group). Regions of other
 * types answer 403 RESOURCES_NOT_AVAILABLE (forbidden state); the region page hides the tab there.
 */
@Component({
  selector: 'fs-region-resources-tab',
  imports: [...DATA_STATE_IMPORTS, Button, ErrorStateComponent, ResourceFormDialogComponent, ResourceListComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <p class="muted">{{ 'resources.region.intro' | t }}</p>
      <div class="head__actions">
        <p-button [label]="'resources.region.mine' | t" icon="pi pi-user" severity="secondary" [outlined]="true" routerLink="/resources/mine" data-testid="resources.region.mine" />
        <p-button [label]="'resources.region.favourites' | t" icon="pi pi-star" severity="secondary" [outlined]="true" routerLink="/resources/favourites" data-testid="resources.region.favourites" />
        @if (permissions(); as perms) {
          @if (perms.mayAdd || perms.mayEditCommons) {
            <p-button [label]="'resources.region.add' | t" icon="pi pi-plus" (onClick)="adding.set(true)" data-testid="resources.region.add" />
          }
        }
      </div>
    </div>
    @if (permissions(); as perms) {
      @if (!perms.mayAdd) {
        <p class="note" role="note" data-testid="resources.region.limit">{{ 'resources.region.limitReached' | t }}</p>
      }
    }

    <fs-data-state
      [state]="data"
      skeleton="list"
      [emptyTitle]="'resources.region.empty' | t"
      [emptyText]="'resources.region.emptyHint' | t"
      emptyIcon="pi pi-th-large"
      data-testid="resources.region.state"
    >
      <!-- a region without a mosaic (canton, country, …) says so instead of the generic "no access" -->
      <ng-template fsError let-error let-retry="retry">
        @let unavailable = error?.code === 'RESOURCES_NOT_AVAILABLE';
        <fs-error-state
          [error]="error"
          [heading]="unavailable ? ('resources.region.notAvailable' | t) : undefined"
          [description]="unavailable ? ('errors.RESOURCES_NOT_AVAILABLE' | t) : undefined"
          [kind]="data.status() === 'offline' ? 'offline' : undefined"
          [retryable]="data.status() === 'error' || data.status() === 'offline' ? true : undefined"
          testId="resources.region.state"
          (retry)="retry()"
        />
      </ng-template>
      <ng-template [fsData]="data" let-loaded>
        @let categories = categoriesOf(loaded.resources);
        @if (categories.length > 1) {
          <div class="chips" role="group" [attr.aria-label]="'resources.region.filter' | t" data-testid="resources.region.categories">
            <button type="button" class="chip" [class.chip--on]="!category()" [attr.aria-pressed]="!category()" (click)="category.set(null)" data-testid="resources.region.category.all">
              {{ 'resources.region.allCategories' | t }}
            </button>
            @for (item of categories; track item.id) {
              <button
                type="button"
                class="chip"
                [class.chip--on]="category() === item.id"
                [attr.aria-pressed]="category() === item.id"
                [attr.data-category-id]="item.id"
                (click)="category.set(category() === item.id ? null : item.id)"
                data-testid="resources.region.category"
              >
                {{ item.name }}
              </button>
            }
          </div>
        }
        @let sections = sectionsOf(loaded.resources);
        @if (sections.commons.length) {
          <section class="section" data-testid="resources.region.commons">
            <h2 class="section__title"><i class="pi pi-building" aria-hidden="true"></i> {{ 'resources.region.commonsTitle' | t: { region: regionName() } }}</h2>
            <p class="muted">{{ 'resources.region.commonsText' | t }}</p>
            <fs-resource-list [resources]="sections.commons" testId="resources.region.commonsList" (changed)="data.reload()" />
          </section>
        }
        @if (sections.members.length) {
          <section class="section" data-testid="resources.region.members">
            <h2 class="section__title"><i class="pi pi-users" aria-hidden="true"></i> {{ 'resources.region.membersTitle' | t }}</h2>
            <fs-resource-list [resources]="sections.members" testId="resources.region.membersList" (changed)="data.reload()" />
          </section>
        }
        @if (!sections.commons.length && !sections.members.length) {
          <p class="muted" data-testid="resources.region.filteredEmpty">{{ 'resources.region.filteredEmpty' | t }}</p>
        }
      </ng-template>
    </fs-data-state>

    <fs-resource-form-dialog
      [(visible)]="adding"
      [regionId]="ctx.regionId()"
      [regionName]="regionName()"
      [mayEditCommons]="permissions()?.mayEditCommons ?? false"
      (saved)="data.reload()"
    />
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
    .head { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: center; justify-content: space-between; }
    .head__actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; max-width: 44rem; }
    .note {
      margin: 0; padding: 0.6rem 0.8rem; border: 1px solid var(--fs-warning-border); border-radius: var(--fs-radius-sm);
      background: var(--fs-warning-soft); color: var(--fs-warning-text); font-size: 0.88rem;
    }
    .chips { display: flex; flex-wrap: wrap; gap: 0.35rem; margin-bottom: 1rem; }
    .chip {
      min-height: 2.25rem; padding: 0.25rem 0.8rem; border: 1px solid var(--fs-border); border-radius: 999px;
      background: var(--fs-surface); color: var(--fs-text); font: inherit; font-size: 0.85rem; font-weight: 600; cursor: pointer;
    }
    .chip--on { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .chip:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
    .section { display: grid; gap: 0.6rem; }
    .section + .section { margin-top: 1.5rem; }
    .section__title { display: flex; align-items: center; gap: 0.5rem; margin: 0; font-size: 1.1rem; }
  `,
})
export class RegionResourcesTab {
  protected readonly ctx = inject(RegionPageContext);
  private readonly api = inject(ResourcesService);

  protected readonly adding = signal(false);
  protected readonly category = signal<string | null>(null);
  protected readonly regionName = computed(() => this.ctx.data()?.name ?? '');

  protected readonly data = loadResource<RegionResources, string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: async (regionId) => {
      const [resources, permissions] = await Promise.all([
        firstValueFrom(this.api.listRegionResources({ regionId })),
        firstValueFrom(this.api.getResourcePermissions({ regionId })),
      ]);
      return { resources, permissions };
    },
    isEmpty: (value) => value.resources.length === 0,
  });

  protected readonly permissions = computed(() => this.data.data()?.permissions);

  protected readonly categoriesOf = usedCategories;
  protected sectionsOf(resources: readonly Resource[]) {
    return splitCommons(inCategory(resources, this.category()));
  }
}
