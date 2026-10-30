import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {
  Permission,
  RegionMember,
  RegionMemberPage,
  RegionPermissionGrant,
  RegionsService,
  Role,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PhoneButtonComponent } from '../../../shared/phone/phone-button.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { RegionPageContext } from '../region-page.context';

export const MEMBERS_PAGE_SIZE = 25;
const PERMISSIONS: Permission[] = Object.values(Permission);

interface MemberQuery {
  regionId: string;
  search?: string;
  verified?: boolean;
  role?: Role;
  page: number;
}

/**
 * Members tab (R8): search, verified/role filters, paging; admins remove members (required message), see and
 * change scoped permissions (matrix dialog with source), ORGA grants/revokes ambassadors. Phone numbers come only
 * when the server allows it (contact visibility).
 */
@Component({
  selector: 'fs-region-members-tab',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    FormsModule,
    InputText,
    Paginator,
    PhoneButtonComponent,
    RequiredMessageDialogComponent,
    Select,
    TPipe,
    ToggleSwitch,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './region-members.tab.html',
  styleUrl: './tab.scss',
  styles: `
    .filters {
      display: grid;
      gap: 0.75rem;
      @media (min-width: 48rem) {
        grid-template-columns: minmax(0, 2fr) repeat(2, minmax(0, 1fr));
      }
    }
    .filters input {
      width: 100%;
    }
    .list {
      display: grid;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .member {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 0.6rem;
      padding: 0.85rem 0;
      border-top: 1px solid var(--fs-border);
      @media (min-width: 48rem) {
        grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) auto;
        align-items: center;
      }
    }
    .member:first-child {
      border-top: 0;
    }
    .member__meta {
      display: flex;
      flex-wrap: wrap;
      gap: 0.3rem 0.75rem;
      color: var(--fs-text-muted);
      font-size: 0.84rem;
    }
    .tag {
      padding: 0.05rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.75rem;
      font-weight: 650;
    }
    .tag--admin {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .tag--perm {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .member__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
      justify-content: flex-start;
      @media (min-width: 48rem) {
        justify-content: flex-end;
      }
    }
    .matrix {
      display: grid;
      gap: 0.25rem;
    }
    .matrix__row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.6rem 0;
      border-top: 1px solid var(--fs-border);
    }
    .matrix__row:first-child {
      border-top: 0;
    }
    .matrix__text {
      display: grid;
      gap: 0.1rem;
    }
    .matrix__source {
      color: var(--fs-text-muted);
      font-size: 0.8rem;
    }
  `,
})
export class RegionMembersTab {
  protected readonly ctx = inject(RegionPageContext);
  private readonly api = inject(RegionsService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly pageSize = MEMBERS_PAGE_SIZE;
  protected readonly permissions = PERMISSIONS;
  protected readonly search = signal('');
  private readonly appliedSearch = signal('');
  protected readonly verified = signal<'all' | 'yes' | 'no'>('all');
  protected readonly role = signal<Role | null>(null);
  protected readonly page = signal(0);

  protected readonly verifiedOptions = computed(() => {
    this.i18n.language();
    return (['all', 'yes', 'no'] as const).map((value) => ({
      value,
      label: this.i18n.t(`regions.members.verified.${value}`),
    }));
  });
  protected readonly roleOptions = computed(() => {
    this.i18n.language();
    return Object.values(Role).map((value) => ({ value, label: this.i18n.t(`common.role.${value}`) }));
  });

  private readonly query = computed<MemberQuery | undefined>(() => {
    const regionId = this.ctx.regionId();
    if (!regionId) {
      return undefined;
    }
    const verified = this.verified();
    return {
      regionId,
      search: this.appliedSearch() || undefined,
      verified: verified === 'all' ? undefined : verified === 'yes',
      role: this.role() ?? undefined,
      page: this.page(),
    };
  });

  protected readonly members = loadResource<RegionMemberPage, MemberQuery>({
    params: () => this.query(),
    loader: (query) => this.api.listRegionMembers({ ...query, size: MEMBERS_PAGE_SIZE }),
  });

  /** Scoped permissions of the region (admins only; drives the permission dialog). */
  protected readonly grants = loadResource<RegionPermissionGrant[], string>({
    params: () => (this.ctx.manageUsers() ? (this.ctx.regionId() ?? undefined) : undefined),
    loader: (regionId) => this.api.listRegionPermissions({ regionId }),
  });

  protected readonly removeTarget = signal<RegionMember | null>(null);
  protected readonly removeOpen = signal(false);
  protected readonly permissionTarget = signal<RegionMember | null>(null);
  protected readonly permissionOpen = signal(false);

  private readonly searchInput = new Subject<string>();

  constructor() {
    this.searchInput
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => {
        this.appliedSearch.set(value.trim());
        this.page.set(0);
      });
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.searchInput.next(value);
  }

  protected setVerified(value: 'all' | 'yes' | 'no'): void {
    this.verified.set(value);
    this.page.set(0);
  }

  protected setRole(value: Role | null): void {
    this.role.set(value);
    this.page.set(0);
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }

  /** Grants of one user (for the dialog), including the source (direct or through a function group). */
  protected grantsOf(userId: string): RegionPermissionGrant[] {
    return (this.grants.data() ?? []).filter((grant) => grant.user.id === userId);
  }

  protected grantOf(userId: string, permission: Permission): RegionPermissionGrant | undefined {
    return this.grantsOf(userId).find((grant) => grant.permission === permission);
  }

  protected sourceText(grant: RegionPermissionGrant | undefined): string {
    if (!grant) {
      return this.i18n.t('regions.members.permission.none');
    }
    if (grant.source.startsWith('FUNCTION:')) {
      return this.i18n.t('regions.members.permission.viaFunction', {
        function: this.i18n.t(`regions.function.${grant.source.slice('FUNCTION:'.length)}`),
      });
    }
    return this.i18n.t('regions.members.permission.direct');
  }

  protected openRemove(member: RegionMember): void {
    this.removeTarget.set(member);
    this.removeOpen.set(true);
  }

  protected openPermissions(member: RegionMember): void {
    this.permissionTarget.set(member);
    this.permissionOpen.set(true);
  }

  protected readonly remove = runMutation(
    (reason: string) =>
      this.api.removeRegionMember({
        regionId: this.ctx.regionId()!,
        userId: this.removeTarget()!.user.id,
        reasonBody: { reason },
      }),
    {
      successToastKey: 'regions.members.removed',
      onSuccess: () => {
        this.removeOpen.set(false);
        this.members.reload();
        this.ctx.reload();
      },
    },
  );

  protected readonly togglePermission = runMutation(
    (userId: string, permission: Permission, grant: boolean) => {
      const params = { regionId: this.ctx.regionId()!, userId, permission };
      return grant ? this.api.grantRegionPermission(params) : this.api.revokeRegionPermission(params);
    },
    {
      successToastKey: 'regions.members.permissionSaved',
      onSuccess: () => {
        this.grants.reload();
        this.members.reload();
      },
    },
  );

  protected readonly setAdmin = runMutation(
    (userId: string, admin: boolean) => {
      const params = { regionId: this.ctx.regionId()!, userId };
      return admin ? this.api.setRegionAdmin(params) : this.api.removeRegionAdmin(params);
    },
    {
      successToastKey: 'regions.members.adminSaved',
      onSuccess: () => {
        this.members.reload();
        this.ctx.reload();
      },
    },
  );

  protected confirmAdmin(member: RegionMember, admin: boolean): void {
    this.confirm.confirm({
      header: this.i18n.t(admin ? 'regions.members.makeAdminTitle' : 'regions.members.removeAdminTitle', {
        name: member.user.displayName,
      }),
      message: this.i18n.t(admin ? 'regions.members.makeAdminText' : 'regions.members.removeAdminText'),
      acceptLabel: this.i18n.t(admin ? 'regions.members.makeAdmin' : 'regions.members.removeAdmin'),
      rejectLabel: this.i18n.t('common.cancel'),
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.setAdmin.run(member.user.id, admin),
    });
  }
}
