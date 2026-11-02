import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';

import { StatisticsMemberStatus, StoreMemberPickupsPage, StoreStatistics, StoresService, TeamSort } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { EmptyStateComponent } from '../../../shared/data-state/empty-state.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { MonthBarChartComponent } from '../components/month-bar-chart.component';
import { StatusChip, TeamFilterBarComponent } from '../components/team-filter-bar.component';
import { StorePageContext } from '../store-page.context';

type StatsRange = 'year' | 'all';
type Metric = 'pickups' | 'kg';

/** First day the "all time" range asks for (older than any foodsharing pickup). */
const ALL_TIME_FROM = '2012-01-01';
export const STATISTICS_MEMBERS_PAGE_SIZE = 20;

interface MembersQuery {
  storeId: string;
  range: StatsRange;
  status: StatisticsMemberStatus;
  search?: string;
  sort: TeamSort;
  page: number;
}

/** No counts on the statistics chips (they would need a per-range query); the count line says how many match. */
const MEMBER_CHIPS: StatusChip<StatisticsMemberStatus>[] = [
  { value: StatisticsMemberStatus.All, labelKey: 'stores.team.filter.all' },
  { value: StatisticsMemberStatus.Manager, labelKey: 'stores.team.filter.manager' },
  { value: StatisticsMemberStatus.Member, labelKey: 'stores.team.filter.member' },
  { value: StatisticsMemberStatus.Jumper, labelKey: 'stores.team.filter.jumper' },
  { value: StatisticsMemberStatus.Former, labelKey: 'stores.statistics.filter.former' },
];

/**
 * /stores/:storeId/statistics (S14, team members): totals, a month bar chart of pickups or kg (kg = pickups × the
 * average of the store's weight category) and, for managers (mayEditStore), one page of pickups per member in the
 * range (status chips incl. former members, name search, sort; resume item 14).
 */
@Component({
  selector: 'fs-store-statistics-tab',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    DecimalPipe,
    EmptyStateComponent,
    MonthBarChartComponent,
    Paginator,
    TeamFilterBarComponent,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toolbar">
      <div class="chips" role="group" [attr.aria-label]="'stores.statistics.range' | t">
        @for (r of ranges; track r) {
          <button type="button" class="chip" [class.chip--active]="range() === r" [attr.aria-pressed]="range() === r" (click)="setRange(r)" [attr.data-testid]="'stores.statistics.range.' + r">
            {{ 'stores.statistics.range.' + r | t }}
          </button>
        }
      </div>
      <div class="chips" role="group" [attr.aria-label]="'stores.statistics.metric' | t">
        @for (m of metrics; track m) {
          <button type="button" class="chip" [class.chip--active]="metric() === m" [attr.aria-pressed]="metric() === m" (click)="metric.set(m)" [attr.data-testid]="'stores.statistics.metric.' + m">
            {{ 'stores.statistics.metric.' + m | t }}
          </button>
        }
      </div>
    </div>

    <fs-data-state [state]="stats" skeleton="card" [askWho]="'stores.page.askWho' | t" testId="stores.statistics.state" data-testid="stores.statistics.state">
      <ng-template [fsData]="stats" let-s>
        <div class="tiles">
          <div class="fs-card tile" data-testid="stores.statistics.totalPickups">
            <span class="tile__value">{{ s.totalPickups | number }}</span>
            <span class="tile__label">{{ 'stores.statistics.totalPickups' | t }}</span>
          </div>
          <div class="fs-card tile" data-testid="stores.statistics.totalKg">
            <span class="tile__value">{{ s.totalKg | number: '1.0-1' }} kg</span>
            <span class="tile__label">{{ 'stores.statistics.totalKg' | t }}</span>
          </div>
        </div>

        <section class="fs-card card">
          <fs-month-bar-chart
            [bars]="bars()"
            [label]="('stores.statistics.chart.' + metric()) | t"
            [unit]="metric() === 'kg' ? 'kg' : ''"
            testId="stores.statistics.chart"
          />
          <p class="hint" data-testid="stores.statistics.weightHint">
            <i class="pi pi-info-circle" aria-hidden="true"></i>
            @if (s.weightCategory; as category) {
              {{ 'stores.statistics.weightHint' | t: { category: category.label, kg: (category.averageKg | number: '1.0-1') ?? '' } }}
            } @else {
              {{ 'stores.statistics.noWeight' | t }}
            }
          </p>
        </section>
      </ng-template>
    </fs-data-state>

    @if (mayEditStore()) {
      <section #membersCard class="fs-card card" data-testid="stores.statistics.members">
        <h2 class="card__title">{{ 'stores.statistics.perMember' | t }}</h2>
        <fs-team-filter-bar
          idPrefix="store-statistics-members"
          testIdPrefix="stores.statistics.members"
          [chips]="memberChips"
          [status]="status()"
          [sort]="sort()"
          (statusChange)="setStatus($event)"
          (sortChange)="setSort($event)"
          (searchChange)="setSearch($event)"
        />
        <fs-data-state
          [state]="members"
          skeleton="list"
          [skeletonRows]="6"
          [askWho]="'stores.page.askWho' | t"
          testId="stores.statistics.members.state"
          data-testid="stores.statistics.members.state"
        >
          <ng-template [fsData]="members" let-result>
            <div class="list">
              <p class="muted" aria-live="polite" data-testid="stores.statistics.members.count">
                {{ 'stores.team.count' | t: { count: result.page.totalItems } }}
              </p>
              <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'stores.statistics.scrollTable' | t">
                <table class="members">
                  <thead>
                    <tr>
                      <th scope="col">{{ 'stores.statistics.member' | t }}</th>
                      <th scope="col" class="num">{{ 'stores.statistics.metric.pickups' | t }}</th>
                      <th scope="col" class="num">{{ 'stores.statistics.lastPickup' | t }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of result.items; track row.user.id) {
                      <tr data-testid="stores.statistics.member" [attr.data-user]="row.user.displayName" [attr.data-status]="row.status">
                        <td>
                          <div class="cell">
                            <fs-user-chip [name]="row.user.displayName" [userId]="row.user.id" [avatarUrl]="row.user.avatarUrl" />
                            @if (row.isManager) {
                              <span class="flag flag--manager"><i class="pi pi-crown" aria-hidden="true"></i>{{ 'stores.team.manager' | t }}</span>
                            }
                            @if (row.status === 'JUMPER') {
                              <span class="flag" data-testid="stores.statistics.jumperBadge"><i class="pi pi-clock" aria-hidden="true"></i>{{ 'stores.team.jumper' | t }}</span>
                            }
                            @if (row.status === 'FORMER') {
                              <span class="flag flag--former" data-status="FORMER" data-testid="stores.statistics.formerBadge"><i class="pi pi-sign-out" aria-hidden="true"></i>{{ 'stores.statistics.former' | t }}</span>
                            }
                          </div>
                        </td>
                        <td class="num">{{ row.pickups | number }}</td>
                        <td class="num date">{{ row.lastPickupAt ? (row.lastPickupAt | fsDate: 'date') : '–' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              @if (result.page.totalPages > 1) {
                <p-paginator
                  [first]="result.page.page * result.page.size"
                  [rows]="result.page.size"
                  [totalRecords]="result.page.totalItems"
                  [pageLinkSize]="3"
                  (onPageChange)="onPage($event)"
                  data-testid="stores.statistics.members.paginator"
                />
              }
            </div>
          </ng-template>
          <ng-template fsEmpty>
            @if (filtered()) {
              <div class="empty-filter" data-testid="stores.statistics.members.emptyFilter">
                <fs-empty-state illustration="search" icon="pi pi-search" [title]="'stores.team.emptyFilter' | t" [compact]="true" />
                <p-button
                  [label]="'stores.team.resetFilter' | t"
                  icon="pi pi-filter-slash"
                  severity="secondary"
                  [outlined]="true"
                  (onClick)="resetFilter()"
                  data-testid="stores.statistics.members.resetFilter"
                />
              </div>
            } @else {
              <p class="muted" data-testid="stores.statistics.members.empty">{{ 'stores.statistics.membersEmpty' | t }}</p>
            }
          </ng-template>
        </fs-data-state>
      </section>
    }
  `,
  styles: `
    :host { display: grid; grid-template-columns: minmax(0, 1fr); gap: 1rem; }
    .toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.5rem; }
    .chips { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .chip {
      padding: 0.3rem 0.8rem; border: 1px solid var(--fs-border); border-radius: 999px; background: var(--fs-surface);
      color: inherit; font: inherit; font-size: 0.88rem; cursor: pointer;
    }
    /* soft fill + strong text: white on --fs-primary fails contrast in the dark theme (F6 sweep) */
    .chip--active { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: 0.75rem; margin-bottom: 1rem; }
    .tile { display: grid; gap: 0.2rem; padding: 1rem; }
    .tile__value { font-size: 1.6rem; font-weight: 700; font-variant-numeric: tabular-nums; }
    .tile__label { color: var(--fs-text-muted); }
    .card { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.75rem; padding: 1rem; margin-bottom: 1rem; }
    .card__title { margin: 0; font-size: 1.05rem; }
    .hint { display: flex; gap: 0.4rem; align-items: baseline; margin: 0; color: var(--fs-text-muted); font-size: 0.88rem; }
    .list { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.6rem; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .table-wrap { position: relative; overflow-x: auto; }
    .members { width: 100%; border-collapse: collapse; }
    .members th, .members td { padding: 0.45rem 0.25rem; border-bottom: 1px solid var(--fs-border); text-align: left; vertical-align: middle; }
    .members th { color: var(--fs-text-muted); font-size: 0.85rem; }
    .members .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .members td.date { color: var(--fs-text-muted); }
    .cell { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; min-width: 0; }
    .flag {
      display: inline-flex; gap: 0.3rem; align-items: center; padding: 0.1rem 0.5rem; border-radius: 999px;
      background: var(--fs-surface-muted); font-size: 0.78rem; font-weight: 600;
    }
    .flag--manager { color: var(--fs-primary-strong); }
    .flag--former { color: var(--fs-text-muted); }
    .empty-filter { display: grid; justify-items: center; gap: 0.5rem; }
  `,
})
export class StoreStatisticsTab {
  protected readonly ctx = inject(StorePageContext);
  private readonly api = inject(StoresService);

  protected readonly ranges: StatsRange[] = ['year', 'all'];
  protected readonly metrics: Metric[] = ['pickups', 'kg'];
  protected readonly memberChips = MEMBER_CHIPS;
  protected readonly range = signal<StatsRange>('year');
  protected readonly metric = signal<Metric>('pickups');
  protected readonly status = signal<StatisticsMemberStatus>(StatisticsMemberStatus.All);
  protected readonly sort = signal<TeamSort>(TeamSort.PickupCount);
  private readonly search = signal('');
  private readonly page = signal(0);
  protected readonly mayEditStore = computed(() => this.ctx.may('mayEditStore'));
  protected readonly filtered = computed(() => this.status() !== StatisticsMemberStatus.All || this.search() !== '');
  private readonly membersCard = viewChild<ElementRef<HTMLElement>>('membersCard');
  private readonly filterBar = viewChild(TeamFilterBarComponent);

  protected readonly stats = loadResource<StoreStatistics, { storeId: string; range: StatsRange }>({
    params: () => {
      const storeId = this.ctx.storeId();
      return storeId ? { storeId, range: this.range() } : undefined;
    },
    // the server's default is the last 12 months up to its own today
    loader: ({ storeId, range }) =>
      this.api.getStoreStatistics({ storeId, ...(range === 'all' ? { from: ALL_TIME_FROM } : {}) }),
    isEmpty: () => false,
  });

  /** Loads only for managers (mayEditStore); the range chips feed from/to as for the chart. */
  protected readonly members = loadResource<StoreMemberPickupsPage, MembersQuery>({
    params: () => {
      const storeId = this.mayEditStore() ? this.ctx.storeId() : null;
      return storeId
        ? {
            storeId,
            range: this.range(),
            status: this.status(),
            search: this.search() || undefined,
            sort: this.sort(),
            page: this.page(),
          }
        : undefined;
    },
    loader: ({ range, ...query }) =>
      this.api.listStoreStatisticsMembers({
        ...query,
        ...(range === 'all' ? { from: ALL_TIME_FROM } : {}),
        size: STATISTICS_MEMBERS_PAGE_SIZE,
      }),
  });

  protected readonly bars = computed(() => {
    const metric = this.metric();
    const months = this.stats.data()?.months ?? [];
    // "all time" starts long before the store's first pickup: drop the empty months before it
    const used = this.range() === 'all' ? months.findIndex((month) => month.pickups > 0) : 0;
    const first = used < 0 ? months.length - 12 : used;
    return months.slice(Math.max(0, first)).map((month) => ({ month: month.month, value: month[metric] }));
  });

  protected setRange(range: StatsRange): void {
    this.range.set(range);
    this.page.set(0);
  }

  protected setStatus(status: StatisticsMemberStatus): void {
    this.status.set(status);
    this.page.set(0);
  }

  protected setSort(sort: TeamSort): void {
    this.sort.set(sort);
    this.page.set(0);
  }

  protected setSearch(search: string): void {
    this.search.set(search);
    this.page.set(0);
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
    this.membersCard()?.nativeElement.scrollIntoView?.({ block: 'start', behavior: 'smooth' });
  }

  protected resetFilter(): void {
    this.status.set(StatisticsMemberStatus.All);
    this.search.set('');
    this.page.set(0);
    this.filterBar()?.reset();
  }
}
