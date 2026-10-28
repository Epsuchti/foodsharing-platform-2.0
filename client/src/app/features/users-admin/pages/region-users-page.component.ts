import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';
import { Select } from '@openng/optimus-ui/select';
import { debounceTime, distinctUntilChanged, map, Subject, throwError } from 'rxjs';

import { ApiErrorCode, RegionDetail, RegionMemberPage, RegionsService, Role } from '../../../api';
import { hasPermission, isOrga } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { ApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

export const USERS_PAGE_SIZE = 25;

/** Shown as the forbidden state (ask your ambassadors) when the viewer may not manage users here. */
const FORBIDDEN: ApiErrorView = {
  status: 403,
  code: ApiErrorCode.AccessDenied,
  message: 'user management',
  retryable: false,
  fieldErrors: [],
};

interface UsersQuery {
  regionId: string;
  /** Ambassador of the region, CAN_MANAGE_USERS for it, or ORGA. */
  allowed: boolean;
  search?: string;
  verified?: boolean;
  role?: Role;
  admin?: boolean;
  page: number;
}

/**
 * /regions/:regionId/users (AD1): user management of a region for ambassadors and CAN_MANAGE_USERS: search,
 * verified/role/admin filters, last pickup, link to the admin view of each user.
 */
@Component({
  selector: 'fs-region-users-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    DateTimePipe,
    FormsModule,
    InputText,
    PageHeaderComponent,
    Paginator,
    RouterLink,
    Select,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'usersAdmin.list.title' | t: { region: region.data()?.name ?? '' }"
      [subtitle]="'usersAdmin.list.subtitle' | t"
      icon="pi pi-id-card"
      [backLink]="regionId() ? ['/regions', regionId()] : undefined"
      [backLabel]="region.data()?.name"
    />

    <div class="filters">
      <div class="field field--grow">
        <label for="ua-search">{{ 'usersAdmin.list.search' | t }}</label>
        <input pInputText id="ua-search" type="search" [placeholder]="'usersAdmin.list.searchPlaceholder' | t" (input)="searchInput.next($any($event.target).value)" data-testid="usersAdmin.list.search" />
      </div>
      <div class="field">
        <label for="ua-verified">{{ 'usersAdmin.list.verified' | t }}</label>
        <p-select inputId="ua-verified" [options]="verifiedOptions()" optionLabel="label" optionValue="value" [ngModel]="verified()" (ngModelChange)="setFilter(verified, $event)" data-testid="usersAdmin.list.verified" />
      </div>
      <div class="field">
        <label for="ua-role">{{ 'usersAdmin.list.role' | t }}</label>
        <p-select inputId="ua-role" [options]="roleOptions()" optionLabel="label" optionValue="value" [showClear]="true" [placeholder]="'usersAdmin.list.allRoles' | t" [ngModel]="role()" (ngModelChange)="setFilter(role, $event)" data-testid="usersAdmin.list.role" />
      </div>
      <div class="field">
        <label for="ua-admin">{{ 'usersAdmin.list.admins' | t }}</label>
        <p-select inputId="ua-admin" [options]="adminOptions()" optionLabel="label" optionValue="value" [ngModel]="admin()" (ngModelChange)="setFilter(admin, $event)" data-testid="usersAdmin.list.admin" />
      </div>
    </div>

    <fs-data-state [state]="users" skeleton="table" [emptyTitle]="'usersAdmin.list.empty' | t" testId="usersAdmin.list.state" data-testid="usersAdmin.list.state">
      <ng-template [fsData]="users" let-result>
        <p class="muted" data-testid="usersAdmin.list.count">{{ 'usersAdmin.list.count' | t: { count: result.page.totalItems } }}</p>
        <div class="table-wrap">
          <table class="users">
            <thead>
              <tr>
                <th scope="col">{{ 'usersAdmin.list.name' | t }}</th>
                <th scope="col">{{ 'usersAdmin.list.role' | t }}</th>
                <th scope="col">{{ 'usersAdmin.list.verified' | t }}</th>
                <th scope="col">{{ 'usersAdmin.list.lastPickup' | t }}</th>
                <th scope="col"><span class="sr-only">{{ 'usersAdmin.list.actions' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (member of result.items; track member.user.id) {
                <tr data-testid="usersAdmin.list.row">
                  <td>
                    <fs-user-chip [name]="member.user.displayName" [userId]="member.user.id" [avatarUrl]="member.user.avatarUrl" [verified]="member.verified" [foodsaverNumber]="member.user.foodsaverNumber" />
                    @if (member.admin) {
                      <span class="tag">{{ 'usersAdmin.list.ambassador' | t }}</span>
                    }
                  </td>
                  <td>{{ 'common.role.' + member.user.role | t }}</td>
                  <td>
                    <i class="pi" role="img" [class.pi-check-circle]="member.verified" [class.pi-minus-circle]="!member.verified" [attr.aria-label]="(member.verified ? 'usersAdmin.list.isVerified' : 'usersAdmin.list.notVerified') | t"></i>
                  </td>
                  <td class="number">{{ member.lastPickupAt ? (member.lastPickupAt | fsDate: 'date') : '–' }}</td>
                  <td class="actions">
                    <a [routerLink]="['/users', member.user.id]" [attr.aria-label]="'usersAdmin.list.open' | t: { name: member.user.displayName }" data-testid="usersAdmin.list.open">
                      <i class="pi pi-angle-right" aria-hidden="true"></i>
                    </a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (result.page.totalPages > 1) {
          <p-paginator [rows]="pageSize" [totalRecords]="result.page.totalItems" [first]="page() * pageSize" (onPageChange)="onPage($event)" />
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: grid;
      gap: 1.25rem;
      width: min(100%, 72rem);
      margin: 0 auto;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 0.75rem;
    }
    .field {
      display: grid;
      gap: 0.35rem;
    }
    .field--grow {
      flex: 1 1 14rem;
    }
    .field label {
      font-size: 0.85rem;
      font-weight: 650;
    }
    .field input {
      width: 100%;
    }
    .muted {
      margin: 0 0 0.5rem;
      color: var(--fs-text-muted);
    }
    .table-wrap {
      overflow-x: auto;
    }
    .users {
      width: 100%;
      border-collapse: collapse;
    }
    .users th,
    .users td {
      padding: 0.55rem 0.6rem;
      border-bottom: 1px solid var(--fs-border);
      text-align: left;
      vertical-align: middle;
    }
    .users th {
      color: var(--fs-text-muted);
      font-size: 0.8rem;
      font-weight: 650;
    }
    .tag {
      display: inline-block;
      margin-top: 0.25rem;
      padding: 0.05rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
      font-size: 0.75rem;
      font-weight: 650;
    }
    .pi-check-circle {
      color: var(--fs-primary-strong);
    }
    .pi-minus-circle {
      color: var(--fs-text-muted);
    }
    .actions a {
      display: inline-grid;
      width: 2.25rem;
      height: 2.25rem;
      place-items: center;
      border-radius: 999px;
      color: var(--fs-text);
    }
    .actions a:hover {
      background: var(--fs-surface-muted);
    }
    .number {
      font-variant-numeric: tabular-nums;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class RegionUsersPageComponent {
  private readonly api = inject(RegionsService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);

  protected readonly regionId = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('regionId') ?? undefined)),
  );
  protected readonly pageSize = USERS_PAGE_SIZE;
  protected readonly searchInput = new Subject<string>();
  private readonly search = signal('');
  protected readonly verified = signal<'all' | 'yes' | 'no'>('all');
  protected readonly role = signal<Role | null>(null);
  protected readonly admin = signal<'all' | 'yes' | 'no'>('all');
  protected readonly page = signal(0);

  protected readonly region = loadResource<RegionDetail, string>({
    params: () => this.regionId(),
    loader: (regionId) => this.api.getRegion({ regionId }),
  });

  private readonly options = (prefix: string) => {
    this.i18n.language();
    return (['all', 'yes', 'no'] as const).map((value) => ({ value, label: this.i18n.t(prefix + value) }));
  };
  protected readonly verifiedOptions = computed(() => this.options('usersAdmin.list.verifiedFilter.'));
  protected readonly adminOptions = computed(() => this.options('usersAdmin.list.adminFilter.'));
  protected readonly roleOptions = computed(() => {
    this.i18n.language();
    return Object.values(Role).map((value) => ({ value, label: this.i18n.t(`common.role.${value}`) }));
  });

  protected readonly users = loadResource<RegionMemberPage, UsersQuery>({
    params: () => {
      const regionId = this.regionId();
      const region = this.region.data();
      if (!regionId || !region) {
        return undefined;
      }
      const session = this.session.session();
      const allowed =
        region.membership.admin || isOrga(session) || hasPermission(session, 'CAN_MANAGE_USERS', regionId);
      const tri = (value: 'all' | 'yes' | 'no') => (value === 'all' ? undefined : value === 'yes');
      return {
        regionId,
        allowed,
        search: this.search() || undefined,
        verified: tri(this.verified()),
        role: this.role() ?? undefined,
        admin: tri(this.admin()),
        page: this.page(),
      };
    },
    // the member list itself is open to all members; user management only to the roles above
    loader: ({ allowed, ...query }) =>
      allowed ? this.api.listRegionMembers({ ...query, size: USERS_PAGE_SIZE }) : throwError(() => FORBIDDEN),
  });

  constructor() {
    this.searchInput.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe((value) => {
      this.page.set(0);
      this.search.set(value.trim());
    });
  }

  protected setFilter<T>(filter: { set(value: T): void }, value: T): void {
    this.page.set(0);
    filter.set(value);
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }
}
