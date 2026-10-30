import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';
import { Select } from '@openng/optimus-ui/select';

import { AdminAdminsService, AdminListEntry, AdminListPage, AdminUnitKind, AdminUsersService, RegionRef, UnitType } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

export const ADMINS_PAGE_SIZE = 25;
export type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';
export type KindFilter = 'ALL' | AdminUnitKind;

export interface AdminsQuery {
  q: string;
  kind: KindFilter;
  active: ActiveFilter;
  page: number;
}

/** Request parameters of `listAdmins` for the filter state (no parameter = no filter). */
export function adminsRequest(query: AdminsQuery): { q?: string; unitKind?: AdminUnitKind; active?: boolean; page: number; size: number } {
  const q = query.q.trim();
  return {
    q: q.length > 0 ? q : undefined,
    unitKind: query.kind === 'ALL' ? undefined : query.kind,
    active: query.active === 'ALL' ? undefined : query.active === 'ACTIVE',
    page: query.page,
    size: ADMINS_PAGE_SIZE,
  };
}

/** Working groups open on the group page, every other unit on the region page. */
export function unitLink(unit: RegionRef): string[] {
  return unit.type === UnitType.WorkingGroup ? ['/groups', unit.id] : ['/regions', unit.id];
}

/**
 * `/admin/admins` (AD1, ORGA): every ambassador and working-group admin with email, administered units, last login
 * and the active flag; search by name or email, filter by unit kind and active state, paging. Disabling or
 * enabling an admin goes through the user endpoint (`setUserActive`) with a required reason (decision 94).
 */
@Component({
  selector: 'fs-admins-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    FormsModule,
    InputText,
    PageHeaderComponent,
    Paginator,
    RequiredMessageDialogComponent,
    RouterLink,
    Select,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminSettings.admins.title' | t" [subtitle]="'adminSettings.admins.subtitle' | t" icon="pi pi-star" />
    <!--
      The filter selects do not move the active option on hover ([focusOnHover]=false; hovering still highlights it):
      a filter change re-renders the list, the page shifts under the resting pointer and Optimus took the hover on the
      closing overlay as the active option, so the closed combobox kept aria-activedescendant pointing at an option
      that no longer exists (axe aria-valid-attr-value, AD1 flake).
    -->
    <div class="filters">
      <div class="field">
        <label for="admins-search">{{ 'adminSettings.admins.search' | t }}</label>
        <input pInputText id="admins-search" type="search" [ngModel]="q()" (ngModelChange)="setQuery($event)" data-testid="admin.admins.search" />
      </div>
      <div class="field">
        <label for="admins-kind">{{ 'adminSettings.admins.kind' | t }}</label>
        <p-select
          inputId="admins-kind"
          [options]="kindOptions()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="kind()"
          (ngModelChange)="setKind($event)"
          appendTo="body"
          [focusOnHover]="false"
          [style]="{ width: '100%' }"
          data-testid="admin.admins.kind"
        >
          <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
        </p-select>
      </div>
      <div class="field">
        <label for="admins-active">{{ 'adminSettings.admins.active' | t }}</label>
        <p-select
          inputId="admins-active"
          [options]="activeOptions()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="active()"
          (ngModelChange)="setActive($event)"
          appendTo="body"
          [focusOnHover]="false"
          [style]="{ width: '100%' }"
          data-testid="admin.admins.activeFilter"
        >
          <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
        </p-select>
      </div>
    </div>

    <fs-data-state
      [state]="admins"
      skeleton="list"
      [skeletonRows]="6"
      emptyIcon="pi pi-users"
      [emptyTitle]="'adminSettings.admins.empty' | t"
      testId="admin.admins.state"
      data-testid="admin.admins.state"
    >
      <ng-template [fsData]="admins" let-result>
        <p class="muted" data-testid="admin.admins.count">{{ 'adminSettings.admins.count' | t: { count: result.page.totalItems } }}</p>
        <ul class="list fs-card">
          @for (entry of result.items; track entry.user.id) {
            <li class="row" data-testid="admin.admins.row" [attr.data-user-id]="entry.user.id" [attr.data-active]="entry.active">
              <div class="admin">
                <div class="admin__head">
                  <fs-user-chip
                    [name]="entry.user.displayName"
                    [userId]="entry.user.id"
                    [avatarUrl]="entry.user.avatarUrl"
                    [role]="entry.user.role"
                    [verified]="entry.user.verified"
                    [link]="['/users', entry.user.id]"
                  />
                  @if (!entry.active) {
                    <span class="chip chip--off" data-testid="admin.admins.inactive">{{ 'adminSettings.admins.inactive' | t }}</span>
                  }
                </div>
                <a class="muted email" [href]="'mailto:' + entry.email" data-testid="admin.admins.email">{{ entry.email }}</a>
                <ul class="units" [attr.aria-label]="'adminSettings.admins.units' | t">
                  @for (unit of entry.units; track unit.region.id) {
                    <li>
                      <a
                        class="chip"
                        [class.chip--on]="unit.region.type === 'WORKING_GROUP'"
                        [routerLink]="link(unit.region)"
                        data-testid="admin.admins.unit"
                        [attr.data-unit-id]="unit.region.id"
                        [attr.data-unit-type]="unit.region.type"
                      >{{ unit.region.name }}</a>
                    </li>
                  }
                </ul>
                <span class="muted" data-testid="admin.admins.lastLogin">
                  {{ entry.lastLoginAt ? ('adminSettings.admins.lastLogin' | t: { date: (entry.lastLoginAt | fsDate: 'dateTime') }) : ('adminSettings.admins.neverLoggedIn' | t) }}
                </span>
              </div>
              <div class="row__actions">
                <p-button
                  [label]="(entry.active ? 'adminSettings.admins.disable' : 'adminSettings.admins.enable') | t"
                  [icon]="entry.active ? 'pi pi-ban' : 'pi pi-check-circle'"
                  [severity]="entry.active ? 'danger' : 'primary'"
                  [outlined]="entry.active"
                  size="small"
                  [ariaLabel]="(entry.active ? 'adminSettings.admins.disableFor' : 'adminSettings.admins.enableFor') | t: { name: entry.user.displayName }"
                  (onClick)="target.set(entry)"
                  data-testid="admin.admins.toggleActive"
                />
              </div>
            </li>
          }
        </ul>
        @if (result.page.totalPages > 1) {
          <p-paginator
            [rows]="pageSize"
            [totalRecords]="result.page.totalItems"
            [first]="page() * pageSize"
            (onPageChange)="onPage($event)"
            data-testid="admin.admins.paginator"
          />
        }
      </ng-template>
    </fs-data-state>

    <fs-required-message-dialog
      [visible]="!!target()"
      (visibleChange)="!$event && target.set(null)"
      [title]="(target()?.active ? 'adminSettings.admins.disableTitle' : 'adminSettings.admins.enableTitle') | t: { name: target()?.user?.displayName ?? '' }"
      [text]="(target()?.active ? 'adminSettings.admins.disableText' : 'adminSettings.admins.enableText') | t"
      [label]="'adminSettings.admins.reason' | t"
      [confirmLabel]="(target()?.active ? 'adminSettings.admins.disable' : 'adminSettings.admins.enable') | t"
      [severity]="target()?.active ? 'danger' : 'primary'"
      [minLength]="3"
      [maxLength]="1000"
      [busy]="toggle.busy()"
      (confirm)="toggle.run($event)"
      testId="admin.admins.activeDialog"
    />
  `,
  styleUrl: '../admin-settings.scss',
  styles: `
    .admin {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }
    .admin__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    .email {
      /* WCAG 2.5.8: at least a 24 px touch target */
      display: inline-flex;
      align-items: center;
      min-height: 24px;
      overflow-wrap: anywhere;
    }
    .units {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .units a {
      color: inherit;
      text-decoration: none;
    }
    .units a:hover {
      text-decoration: underline;
    }
  `,
})
export class AdminsPageComponent {
  private readonly api = inject(AdminAdminsService);
  private readonly usersApi = inject(AdminUsersService);
  private readonly i18n = inject(I18nService);

  protected readonly pageSize = ADMINS_PAGE_SIZE;
  protected readonly q = signal('');
  protected readonly kind = signal<KindFilter>('ALL');
  protected readonly active = signal<ActiveFilter>('ALL');
  protected readonly page = signal(0);
  protected readonly target = signal<AdminListEntry | null>(null);

  protected readonly kindOptions = computed(() => {
    this.i18n.language();
    return (['ALL', ...Object.values(AdminUnitKind)] as KindFilter[]).map((value) => ({
      value,
      label: this.i18n.t(`adminSettings.admins.kind.${value}`),
    }));
  });

  protected readonly activeOptions = computed(() => {
    this.i18n.language();
    return (['ALL', 'ACTIVE', 'INACTIVE'] as ActiveFilter[]).map((value) => ({
      value,
      label: this.i18n.t(`adminSettings.admins.active.${value}`),
    }));
  });

  protected readonly admins = loadResource<AdminListPage, AdminsQuery>({
    params: () => ({ q: this.q(), kind: this.kind(), active: this.active(), page: this.page() }),
    loader: (query) => this.api.listAdmins(adminsRequest(query)),
    isEmpty: (result) => result.items.length === 0,
  });

  protected readonly toggle = runMutation(
    (reason: string) => {
      const entry = this.target()!;
      return this.usersApi.setUserActive({ userId: entry.user.id, activeUpdate: { active: !entry.active, reason } });
    },
    {
      successToastKey: 'adminSettings.admins.activeSaved',
      onSuccess: () => {
        this.target.set(null);
        this.admins.reload();
      },
    },
  );

  protected link(unit: RegionRef): string[] {
    return unitLink(unit);
  }

  protected setQuery(value: string): void {
    this.q.set(value ?? '');
    this.page.set(0);
  }

  protected setKind(value: KindFilter): void {
    this.kind.set(value ?? 'ALL');
    this.page.set(0);
  }

  protected setActive(value: ActiveFilter): void {
    this.active.set(value ?? 'ALL');
    this.page.set(0);
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }
}
