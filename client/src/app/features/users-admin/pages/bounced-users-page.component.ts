import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';

import { AdminUsersService, BouncedUser, BouncedUserPage, UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

export const BOUNCED_PAGE_SIZE = 25;

/**
 * /admin/bounced-emails (A18, MB5): users whose address bounces, newest first. Without `?regionId` the list is
 * global (ORGA); with it the users of that home region (its administrators). "Clear" removes the flag after the
 * address was fixed or works again; the server then sends mails again.
 */
@Component({
  selector: 'fs-bounced-users-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, Paginator, RouterLink, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'usersAdmin.bounced.title' | t"
      [subtitle]="'usersAdmin.bounced.subtitle' | t"
      icon="pi pi-exclamation-circle"
    />
    <fs-data-state
      [state]="list"
      skeleton="table"
      [emptyTitle]="'usersAdmin.bounced.empty' | t"
      [emptyText]="'usersAdmin.bounced.emptyText' | t"
      emptyIcon="pi pi-check-circle"
      testId="usersAdmin.bounced.state"
      data-testid="usersAdmin.bounced.state"
    >
      <ng-template [fsData]="list" let-result>
        <p class="muted" data-testid="usersAdmin.bounced.count">
          {{ 'usersAdmin.bounced.count' | t: { count: result.page.totalItems } }}
        </p>
        <div class="table-wrap">
          <table class="bounced">
            <thead>
              <tr>
                <th scope="col">{{ 'usersAdmin.list.name' | t }}</th>
                <th scope="col">{{ 'usersAdmin.bounced.since' | t }}</th>
                <th scope="col"><span class="fs-visually-hidden">{{ 'usersAdmin.list.actions' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (row of result.items; track row.user.id) {
                <tr data-testid="usersAdmin.bounced.row" [attr.data-user-id]="row.user.id">
                  <td>
                    <fs-user-chip
                      [name]="row.user.displayName"
                      [userId]="row.user.id"
                      [avatarUrl]="row.user.avatarUrl"
                      [link]="['/users', row.user.id]"
                    />
                  </td>
                  <td class="number">{{ row.bouncedAt | fsDate: 'dateTime' }}</td>
                  <td class="actions">
                    <p-button
                      [label]="'usersAdmin.bounced.clear' | t"
                      icon="pi pi-check"
                      size="small"
                      severity="secondary"
                      [outlined]="true"
                      [loading]="clearing() === row.user.id"
                      [ariaLabel]="'usersAdmin.bounced.clearFor' | t: { name: row.user.displayName }"
                      (onClick)="clear.run(row)"
                      data-testid="usersAdmin.bounced.clear"
                    />
                    <a
                      class="open"
                      [routerLink]="['/users', row.user.id]"
                      [attr.aria-label]="'usersAdmin.list.open' | t: { name: row.user.displayName }"
                      data-testid="usersAdmin.bounced.open"
                    >
                      <i class="pi pi-angle-right" aria-hidden="true"></i>
                    </a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (result.page.totalPages > 1) {
          <p-paginator
            [rows]="pageSize"
            [totalRecords]="result.page.totalItems"
            [first]="page() * pageSize"
            (onPageChange)="onPage($event)"
          />
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: grid;
      gap: 1.25rem;
      width: min(100%, 60rem);
      margin: 0 auto;
    }
    .muted {
      margin: 0 0 0.5rem;
      color: var(--fs-text-muted);
    }
    .table-wrap {
      overflow-x: auto;
    }
    .bounced {
      width: 100%;
      border-collapse: collapse;
    }
    .bounced th,
    .bounced td {
      padding: 0.55rem 0.6rem;
      border-bottom: 1px solid var(--fs-border);
      text-align: left;
      vertical-align: middle;
    }
    .bounced th {
      color: var(--fs-text-muted);
      font-size: 0.8rem;
      font-weight: 650;
    }
    .actions {
      white-space: nowrap;
      text-align: right;
    }
    .open {
      display: inline-grid;
      width: 2.25rem;
      height: 2.25rem;
      margin-left: 0.25rem;
      place-items: center;
      border-radius: 999px;
      color: var(--fs-text);
      vertical-align: middle;
    }
    .open:hover {
      background: var(--fs-surface-muted);
    }
    .number {
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class BouncedUsersPageComponent {
  private readonly api = inject(AdminUsersService);
  private readonly usersApi = inject(UsersService);

  /** `?regionId=` limits the list to users of that home region (region administrators). */
  readonly regionId = input<string | undefined>(undefined);

  protected readonly pageSize = BOUNCED_PAGE_SIZE;
  protected readonly page = signal(0);
  protected readonly clearing = signal<string | null>(null);

  private readonly query = computed(() => ({ regionId: this.regionId() || undefined, page: this.page() }));

  protected readonly list = loadResource<BouncedUserPage, { regionId?: string; page: number }>({
    params: () => this.query(),
    loader: ({ regionId, page }) => this.api.listBouncedUsers({ regionId, page, size: BOUNCED_PAGE_SIZE }),
  });

  protected readonly clear = runMutation(
    (row: BouncedUser) => {
      this.clearing.set(row.user.id);
      return this.usersApi.clearEmailBounce({ userId: row.user.id });
    },
    {
      successToastKey: 'usersAdmin.bounced.cleared',
      onSuccess: () => {
        this.clearing.set(null);
        this.list.reload();
      },
      onError: () => this.clearing.set(null),
    },
  );

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }
}
