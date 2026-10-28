import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { MultiSelect } from '@openng/optimus-ui/multiselect';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { debounceTime, map } from 'rxjs';

import { CooperationStatus, Role, StoreListItem, StoreListPage, StoresService, TeamSearchStatus } from '../../../api';
import { isMemberOf, isOrga } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { STORE_BADGES } from '../../../shared/store-badges/store-badges.component';
import { ApplyTarget, StoreApplyDialogComponent } from '../components/apply-dialog.component';
import { COOPERATION_STATUSES, DEFAULT_COOPERATION_FILTER, TEAM_STATUSES } from '../stores.util';

interface Params {
  regionId: string;
  cooperation: CooperationStatus[];
  team: TeamSearchStatus[];
  search: string;
  includeSubregions: boolean;
}

/**
 * /regions/:regionId/stores (S1, S15): the stores of a region with filters (cooperation status, team status,
 * search, sub-regions) as list or map; a row opens the store. Store managers of the region may create stores.
 */
@Component({
  selector: 'fs-region-stores-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    ...STORE_BADGES,
    Button,
    FormsModule,
    InputText,
    MapViewComponent,
    MultiSelect,
    PageHeaderComponent,
    RouterLink,
    StoreApplyDialogComponent,
    ToggleSwitch,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'stores.region.title' | t" [subtitle]="regionName()" icon="pi pi-shopping-bag" [backLink]="['/regions', regionId()]" />

    <section class="fs-card card filters" data-testid="stores.region.filters">
      <div class="field">
        <label for="stores-search">{{ 'stores.region.search' | t }}</label>
        <input pInputText id="stores-search" type="search" [ngModel]="searchInput()" (ngModelChange)="searchInput.set($event)" data-testid="stores.region.search" />
      </div>
      <div class="field">
        <label for="stores-coop">{{ 'stores.region.cooperation' | t }}</label>
        <p-multiselect
          inputId="stores-coop"
          [options]="cooperationOptions()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="cooperation()"
          (ngModelChange)="cooperation.set($event)"
          [showToggleAll]="false"
          appendTo="body"
          [style]="{ width: '100%' }"
          data-testid="stores.region.cooperation"
        />
      </div>
      <div class="field">
        <label for="stores-team">{{ 'stores.region.teamStatus' | t }}</label>
        <p-multiselect
          inputId="stores-team"
          [options]="teamOptions()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="team()"
          (ngModelChange)="team.set($event)"
          [showToggleAll]="false"
          [placeholder]="'stores.region.allTeams' | t"
          appendTo="body"
          [style]="{ width: '100%' }"
          data-testid="stores.region.teamStatus"
        />
      </div>
      <div class="switches">
        <span class="switch">
          <p-toggleswitch [ngModel]="includeSubregions()" (ngModelChange)="includeSubregions.set($event)" [ariaLabel]="'stores.region.subregions' | t" data-testid="stores.region.subregions" />
          {{ 'stores.region.subregions' | t }}
        </span>
        <span class="switch">
          <p-toggleswitch [ngModel]="showMap()" (ngModelChange)="showMap.set($event)" [ariaLabel]="'stores.region.showMap' | t" data-testid="stores.region.showMap" />
          {{ 'stores.region.showMap' | t }}
        </span>
        @if (mayCreate()) {
          <p-button
            [label]="'stores.region.create' | t"
            icon="pi pi-plus"
            size="small"
            [routerLink]="['/stores/new']"
            [queryParams]="{ regionId: regionId() }"
            data-testid="stores.region.create"
          />
        }
      </div>
    </section>

    <fs-data-state
      [state]="stores"
      skeleton="table"
      [emptyTitle]="'stores.region.empty' | t"
      [emptyText]="'stores.region.emptyText' | t"
      testId="stores.region.state"
      data-testid="stores.region.state"
    >
      <ng-template [fsData]="stores" let-page>
        @if (showMap()) {
          <section class="fs-card card" data-testid="stores.region.map">
            <fs-map-view [markers]="markers()" height="24rem" [ariaLabel]="'stores.region.mapLabel' | t" testId="stores.region.mapView" (markerClick)="open($event.id)" />
          </section>
        }
        <div class="fs-card table-wrap">
          <table class="table" data-testid="stores.region.table">
            <caption class="sr-only">{{ 'stores.region.title' | t }}</caption>
            <thead>
              <tr>
                <th scope="col">{{ 'stores.region.colName' | t }}</th>
                <th scope="col">{{ 'stores.region.colAddress' | t }}</th>
                <th scope="col">{{ 'stores.region.colStatus' | t }}</th>
                <th scope="col">{{ 'stores.region.colTeam' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (store of page.items; track store.id) {
                <tr data-testid="stores.region.row" [attr.data-store]="store.name" (click)="open(store.id)">
                  <td>
                    <a [routerLink]="['/stores', store.id]" (click)="$event.stopPropagation()" class="name">{{ store.name }}</a>
                    @if (store.membership !== 'NO_MEMBER') {
                      <fs-membership-badge [status]="store.membership" />
                    } @else if (store.teamStatus !== 'CLOSED') {
                      <p-button
                        [label]="'stores.apply.open' | t"
                        icon="pi pi-user-plus"
                        size="small"
                        [text]="true"
                        (onClick)="openApply($event, store)"
                        data-testid="stores.region.apply"
                      />
                    }
                  </td>
                  <td class="muted">{{ store.address.street }}, {{ store.address.postcode }} {{ store.address.city }}</td>
                  <td><fs-cooperation-status-badge [status]="store.cooperationStatus" /></td>
                  <td><fs-team-status-badge [status]="store.teamStatus" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="muted total" data-testid="stores.region.total">{{ 'stores.region.total' | t: { count: page.page.totalItems } }}</p>
      </ng-template>
    </fs-data-state>
    <fs-store-apply-dialog [(visible)]="applyOpen" [store]="applyTarget()" (applied)="stores.reload()" />
  `,
  styleUrl: '../stores.scss',
  styles: `
    :host { display: block; width: min(100%, 76rem); margin: 0 auto; }
    .filters { display: grid; gap: 0.75rem 1rem; grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr)); margin-bottom: 1rem; }
    .switches { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem 1.25rem; grid-column: 1 / -1; }
    .switch { display: inline-flex; align-items: center; gap: 0.5rem; }
    .switches p-button { margin-left: auto; }
    .table-wrap { overflow-x: auto; padding: 0; margin-top: 1rem; }
    .table { width: 100%; border-collapse: collapse; }
    .table th, .table td { padding: 0.6rem 0.75rem; text-align: left; border-bottom: 1px solid var(--fs-border); vertical-align: middle; }
    .table th { font-size: 0.82rem; color: var(--fs-text-muted); font-weight: 650; }
    .table tbody tr { cursor: pointer; }
    .table tbody tr:hover { background: var(--fs-surface-muted); }
    .table td:first-child { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; }
    .name { font-weight: 650; color: inherit; }
    .total { margin-top: 0.5rem; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    @media (max-width: 40rem) {
      .table thead { display: none; }
      .table tr { display: grid; padding: 0.5rem 0; border-bottom: 1px solid var(--fs-border); }
      .table td { border: 0; padding: 0.2rem 0.75rem; }
    }
  `,
})
export class RegionStoresPageComponent {
  private readonly api = inject(StoresService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected readonly regionId = toSignal(inject(ActivatedRoute).paramMap.pipe(map((p) => p.get('regionId') ?? '')), {
    initialValue: '',
  });
  protected readonly searchInput = signal('');
  private readonly search = toSignal(toObservable(this.searchInput).pipe(debounceTime(250)), { initialValue: '' });
  protected readonly cooperation = signal<CooperationStatus[]>([...DEFAULT_COOPERATION_FILTER]);
  protected readonly team = signal<TeamSearchStatus[]>([]);
  protected readonly includeSubregions = signal(false);
  protected readonly showMap = signal(false);

  protected readonly regionName = computed(() => {
    const id = this.regionId();
    return (this.session.session().units ?? []).find((unit) => unit.id === id)?.name ?? '';
  });

  protected readonly mayCreate = computed(() => {
    const session = this.session.session();
    return isOrga(session) || (this.session.hasRole(Role.StoreManager) && isMemberOf(session, this.regionId()));
  });

  protected readonly cooperationOptions = computed(() =>
    COOPERATION_STATUSES.map((value) => ({ value, label: this.i18n.t('shared.store.cooperation.' + value) })),
  );
  protected readonly teamOptions = computed(() =>
    TEAM_STATUSES.map((value) => ({ value, label: this.i18n.t('shared.store.team.' + value) })),
  );

  protected readonly stores = loadResource<StoreListPage, Params>({
    params: () =>
      this.regionId()
        ? {
            regionId: this.regionId(),
            cooperation: this.cooperation(),
            team: this.team(),
            search: this.search().trim(),
            includeSubregions: this.includeSubregions(),
          }
        : undefined,
    loader: (p) =>
      this.api.listRegionStores({
        regionId: p.regionId,
        cooperationStatus: p.cooperation.length ? p.cooperation : undefined,
        teamStatus: p.team.length ? p.team : undefined,
        search: p.search || undefined,
        includeSubregions: p.includeSubregions,
        size: 200,
      }),
  });

  protected readonly markers = computed<MapMarker[]>(() =>
    (this.stores.data()?.items ?? [])
      .filter((store) => store.latitude != null && store.longitude != null)
      .map((store) => ({ id: store.id, kind: 'store', lat: store.latitude!, lon: store.longitude!, label: store.name })),
  );

  protected readonly applyOpen = signal(false);
  protected readonly applyTarget = signal<ApplyTarget | null>(null);

  /** Apply straight from the list (S6), e.g. when the store page is not readable for non-members. */
  protected openApply(event: Event, store: StoreListItem): void {
    event.stopPropagation();
    this.applyTarget.set({ id: store.id, name: store.name });
    this.applyOpen.set(true);
  }

  protected open(storeId: string): void {
    void this.router.navigate(['/stores', storeId]);
  }
}
