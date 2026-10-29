import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { firstValueFrom } from 'rxjs';

import { FoodSharePointsService, FspSummary, MapMarker as ApiMapMarker, PublicMapService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { RegionPageContext } from '../../regions/region-page.context';
import { FspCardComponent } from '../components/fsp-card.component';
import { fspMarkers } from '../fsp.util';

interface RegionFsps {
  fsps: FspSummary[];
  markers: ApiMapMarker[];
}

/**
 * /regions/:regionId/food-share-points (FS1, R3): the region's food share points as cards next to a map. The list
 * asks for inactive ones too; the server only returns them to people who may activate or manage them or who created
 * them ("awaiting activation" tag). `FspSummary` has no coordinates, so the pins come from the public FSP markers
 * (active FSPs); without them the list still shows. Region members propose new FSPs.
 */
@Component({
  selector: 'fs-region-fsps-tab',
  imports: [...DATA_STATE_IMPORTS, Button, FspCardComponent, MapViewComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <p class="muted">{{ 'foodSharePoints.region.intro' | t }}</p>
      <div class="head__actions">
        <p-button [label]="'foodSharePoints.region.mine' | t" icon="pi pi-list" severity="secondary" [outlined]="true" routerLink="/food-share-points/mine" data-testid="foodSharePoints.region.mine" />
        @if (mayPropose()) {
          <p-button [label]="'foodSharePoints.region.add' | t" icon="pi pi-plus" (onClick)="add()" data-testid="foodSharePoints.region.add" />
        }
      </div>
    </div>
    <fs-data-state
      [state]="data"
      skeleton="list"
      [emptyTitle]="'foodSharePoints.region.empty' | t"
      [emptyText]="(mayPropose() ? 'foodSharePoints.region.emptyHint' : 'foodSharePoints.region.emptyHintGuest') | t"
      emptyIcon="pi pi-box"
      data-testid="foodSharePoints.region.state"
    >
      <ng-template [fsData]="data" let-loaded>
        <div class="split">
          <ul class="list" data-testid="foodSharePoints.region.list">
            @for (fsp of loaded.fsps; track fsp.id) {
              <li><fs-fsp-card [fsp]="fsp" [highlighted]="selected() === fsp.id" /></li>
            }
          </ul>
          <div class="split__map">
            <fs-map-view
              [markers]="markers()"
              height="min(28rem, 60vh)"
              [ariaLabel]="'foodSharePoints.region.mapLabel' | t"
              testId="foodSharePoints.region.map"
              (markerClick)="pick($event)"
            />
          </div>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; }
    .head { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: center; justify-content: space-between; margin-bottom: 1rem; }
    .head__actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; max-width: 40rem; }
    .split { display: grid; gap: 1rem; }
    @media (min-width: 60rem) {
      .split { grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr); align-items: start; }
      .split__map { position: sticky; top: calc(var(--fs-header-h, 4rem) + 1rem); }
    }
    .list { display: grid; gap: 0.75rem; margin: 0; padding: 0; list-style: none; }
  `,
})
export class RegionFspsTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly api = inject(FoodSharePointsService);
  private readonly publicMap = inject(PublicMapService);
  private readonly router = inject(Router);

  protected readonly selected = signal<string | null>(null);
  /** Region members propose FSPs (INACTIVE until activated); FSP admins create active ones. */
  protected readonly mayPropose = computed(() => this.ctx.isMember() || this.ctx.isOrga());

  protected readonly data = loadResource<RegionFsps, string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: async (regionId) => {
      const fsps = await firstValueFrom(this.api.listRegionFoodSharePoints({ regionId, includeInactive: true }));
      // pins are a bonus: a failing marker request leaves the list usable
      const markers = fsps.length ? await firstValueFrom(this.publicMap.listPublicFspMarkers()).catch(() => [] as ApiMapMarker[]) : [];
      return { fsps, markers };
    },
    isEmpty: (value) => value.fsps.length === 0,
  });

  protected readonly markers = computed<MapMarker[]>(() => {
    const loaded = this.data.data();
    return loaded ? fspMarkers(loaded.fsps, loaded.markers) : [];
  });

  protected pick(marker: MapMarker): void {
    if (marker.kind !== 'fsp') return;
    this.selected.set(marker.id);
    queueMicrotask(() =>
      document.querySelector(`fs-fsp-card[data-fsp-id="${marker.id}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }),
    );
  }

  protected add(): void {
    void this.router.navigate(['/food-share-points/new'], { queryParams: { regionId: this.ctx.regionId() } });
  }
}
