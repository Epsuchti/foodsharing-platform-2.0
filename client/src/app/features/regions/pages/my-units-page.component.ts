import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { RegionsService, UnitMembership, UnitType } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/**
 * /regions/mine (R2): the user's regions (home region first) and working groups with their role, links to the
 * region/group pages and to the region finder.
 */
@Component({
  selector: 'fs-my-units-page',
  imports: [...DATA_STATE_IMPORTS, Button, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'regions.mine.title' | t" [subtitle]="'regions.mine.subtitle' | t" icon="pi pi-map" />

    <fs-data-state
      [state]="units"
      skeleton="list"
      [emptyTitle]="'regions.mine.empty' | t"
      [emptyText]="'regions.mine.emptyText' | t"
      [emptyActionLabel]="'regions.mine.findRegion' | t"
      emptyActionIcon="pi pi-search"
      (emptyAction)="findRegion()"
      testId="regions.mine.state"
      data-testid="regions.mine.state"
    >
      <ng-template [fsData]="units">
        <div class="cols">
          <section class="fs-card card" data-testid="regions.mine.regions">
            <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i>{{ 'regions.mine.regions' | t }}</h2>
            @if (regions().length) {
              <ul class="units">
                @for (unit of regions(); track unit.id) {
                  <li>
                    <a class="unit" [routerLink]="['/regions', unit.id]" data-testid="regions.mine.region">
                      <span class="unit__name">{{ unit.name }}</span>
                      <span class="unit__meta">{{ 'regions.type.' + unit.type | t }}</span>
                      <span class="unit__tags">
                        @if (unit.homeRegion) {
                          <span class="tag tag--home">{{ 'regions.page.isHome' | t }}</span>
                        }
                        @if (unit.admin) {
                          <span class="tag">{{ 'regions.page.isAdmin' | t }}</span>
                        }
                      </span>
                      <i class="pi pi-angle-right" aria-hidden="true"></i>
                    </a>
                  </li>
                }
              </ul>
            } @else {
              <p class="muted">{{ 'regions.mine.noRegions' | t }}</p>
            }
            <div>
              <p-button
                [label]="'regions.mine.findRegion' | t"
                icon="pi pi-search"
                severity="secondary"
                [outlined]="true"
                routerLink="/find-region"
                data-testid="regions.mine.findRegion"
              />
            </div>
          </section>

          <section class="fs-card card" data-testid="regions.mine.groups">
            <h2 class="card__title"><i class="pi pi-sitemap" aria-hidden="true"></i>{{ 'regions.mine.groups' | t }}</h2>
            @if (groups().length) {
              <ul class="units">
                @for (unit of groups(); track unit.id) {
                  <li>
                    <a class="unit" [routerLink]="['/groups', unit.id]" data-testid="regions.mine.group">
                      <span class="unit__name">{{ unit.name }}</span>
                      @if (unit.function) {
                        <span class="unit__meta">{{ 'regions.function.' + unit.function | t }}</span>
                      }
                      <span class="unit__tags">
                        @if (unit.admin) {
                          <span class="tag">{{ 'regions.groups.admin' | t }}</span>
                        }
                      </span>
                      <i class="pi pi-angle-right" aria-hidden="true"></i>
                    </a>
                  </li>
                }
              </ul>
            } @else {
              <p class="muted">{{ 'regions.mine.noGroups' | t }}</p>
            }
          </section>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../tabs/tab.scss',
  styles: `
    :host {
      width: min(100%, 72rem);
      margin: 0 auto;
    }
    .units {
      display: grid;
      gap: 0.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .unit {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      grid-template-areas: 'name icon' 'meta icon' 'tags icon';
      align-items: center;
      gap: 0.15rem 0.75rem;
      padding: 0.65rem 0.75rem;
      border-radius: var(--fs-radius-md);
      color: inherit;
      text-decoration: none;
    }
    .unit:hover,
    .unit:focus-visible {
      background: var(--fs-primary-soft);
    }
    .unit__name {
      grid-area: name;
      font-weight: 650;
    }
    .unit__meta {
      grid-area: meta;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .unit__tags {
      display: flex;
      grid-area: tags;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .unit__tags:empty {
      display: none;
    }
    .unit .pi {
      grid-area: icon;
      color: var(--fs-text-muted);
    }
    .tag {
      padding: 0.1rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.75rem;
      font-weight: 650;
    }
    .tag--home {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
  `,
})
export class MyUnitsPageComponent {
  private readonly api = inject(RegionsService);
  private readonly router = inject(Router);

  protected findRegion(): void {
    void this.router.navigateByUrl('/find-region');
  }

  protected readonly units = loadResource<UnitMembership[]>({ loader: () => this.api.listMyUnits() });

  protected readonly regions = computed(() =>
    (this.units.data() ?? [])
      .filter((unit) => unit.type !== UnitType.WorkingGroup)
      .sort((a, b) => Number(b.homeRegion) - Number(a.homeRegion) || a.name.localeCompare(b.name)),
  );
  protected readonly groups = computed(() =>
    (this.units.data() ?? [])
      .filter((unit) => unit.type === UnitType.WorkingGroup)
      .sort((a, b) => a.name.localeCompare(b.name)),
  );
}
