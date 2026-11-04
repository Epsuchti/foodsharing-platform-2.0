import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';
import { Popover } from '@openng/optimus-ui/popover';

import { StoreTeam, StoreTeamService, TeamListStatus, TeamMember, TeamMemberPage, TeamSort } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { errorParams } from '../../../core/data/api-error';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { EmptyStateComponent } from '../../../shared/data-state/empty-state.component';
import { PhoneButtonComponent } from '../../../shared/phone/phone-button.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { StoreInviteDialogComponent } from '../components/invite-dialog.component';
import { StatusChip, TeamFilterBarComponent } from '../components/team-filter-bar.component';
import { StorePageContext } from '../store-page.context';

export const TEAM_PAGE_SIZE = 20;

/** Actions that need a required message (A21: delivered as bell + mail and stored in the store log). */
type MessageAction = 'remove' | 'standby' | 'removeManager' | 'decline';

interface PendingMessage {
  action: MessageAction;
  userId: string;
  name: string;
}

interface TeamQuery {
  storeId: string;
  status: TeamListStatus;
  search?: string;
  sort: TeamSort;
  page: number;
}

/**
 * /stores/:storeId/team (S5, S7, S8, S9, resume item 14): one paged list of members and jumpers (status chips, name
 * search, sort), then applications and invitations. Managers (mayEditTeam) get a row menu (appoint/remove manager,
 * standby/back to team, remove with a required message), accept/decline for applications and an invite dialog.
 * Distance only with maySeeMemberDistance (server omits it otherwise).
 */
@Component({
  selector: 'fs-store-team-tab',
  imports: [
    PhoneButtonComponent,
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    EmptyStateComponent,
    NgTemplateOutlet,
    Paginator,
    Popover,
    RequiredMessageDialogComponent,
    StoreInviteDialogComponent,
    TeamFilterBarComponent,
    TPipe,
    UserAvatarComponent,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack" data-testid="stores.team">
      @if (mayEditTeam()) {
        <div class="toolbar">
          <p-button
            [label]="'stores.team.invite' | t"
            icon="pi pi-user-plus"
            (onClick)="inviteOpen.set(true)"
            data-testid="stores.team.invite"
          />
        </div>
      }

      <section #membersCard class="fs-card card" data-testid="stores.team.members">
        <h2 class="card__title"><i class="pi pi-users" aria-hidden="true"></i>{{ 'stores.team.members' | t }}</h2>
        <fs-team-filter-bar
          idPrefix="store-team"
          testIdPrefix="stores.team"
          [chips]="chips()"
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
          testId="stores.team.list"
          data-testid="stores.team.list"
        >
          <ng-template [fsData]="members" let-result>
            <div class="list">
              <p class="muted" aria-live="polite" data-testid="stores.team.count">
                {{ 'stores.team.count' | t: { count: result.page.totalItems } }}
              </p>
              <ul class="rows" [attr.aria-label]="'stores.team.list' | t">
                @for (member of result.items; track member.user.id) {
                  <ng-container *ngTemplateOutlet="memberRow; context: { $implicit: member }" />
                }
              </ul>
              @if (result.page.totalPages > 1) {
                <p-paginator
                  [first]="result.page.page * result.page.size"
                  [rows]="result.page.size"
                  [totalRecords]="result.page.totalItems"
                  [pageLinkSize]="3"
                  (onPageChange)="onPage($event)"
                  data-testid="stores.team.paginator"
                />
              }
            </div>
          </ng-template>
          <ng-template fsEmpty>
            @if (filtered()) {
              <div class="empty-filter" data-testid="stores.team.emptyFilter">
                <fs-empty-state illustration="search" icon="pi pi-search" [title]="'stores.team.emptyFilter' | t" [compact]="true" />
                <p-button
                  [label]="'stores.team.resetFilter' | t"
                  icon="pi pi-filter-slash"
                  severity="secondary"
                  [outlined]="true"
                  (onClick)="resetFilter()"
                  data-testid="stores.team.resetFilter"
                />
              </div>
            } @else {
              <p class="muted">{{ 'stores.team.noMembers' | t }}</p>
            }
          </ng-template>
        </fs-data-state>
      </section>

      <fs-data-state
        [state]="team"
        skeleton="list"
        [askWho]="'stores.page.askWho' | t"
        testId="stores.team.state"
        data-testid="stores.team.state"
      >
        <ng-template [fsData]="team" let-team>
          <div class="stack">
            @if (team.applications; as applications) {
              <section class="fs-card card" data-testid="stores.team.applications">
                <h2 class="card__title">
                  <i class="pi pi-send" aria-hidden="true"></i>{{ 'stores.team.applications' | t }}
                  <span class="count">{{ applications.length }}</span>
                </h2>
                <ul class="rows">
                  @for (application of applications; track application.user.id) {
                    <li class="row row--block" data-testid="stores.team.application" [attr.data-user]="application.user.displayName">
                      <div class="row__main">
                        <fs-user-chip
                          [name]="application.user.displayName"
                          [userId]="application.user.id"
                          [avatarUrl]="application.user.avatarUrl"
                          [verified]="application.user.verified"
                          [subtitle]="'stores.team.appliedAt' | t: { date: (application.appliedAt | fsDate: 'date') }"
                        />
                      </div>
                      @if (application.text) {
                        <p class="text" data-testid="stores.team.applicationText">{{ application.text }}</p>
                      }
                      <div class="row__actions">
                        <p-button
                          [label]="'stores.team.accept' | t"
                          icon="pi pi-check"
                          size="small"
                          [loading]="accept.busy()"
                          (onClick)="accept.run(application.user.id, false)"
                          data-testid="stores.team.accept"
                        />
                        <p-button
                          [label]="'stores.team.acceptStandby' | t"
                          icon="pi pi-clock"
                          size="small"
                          severity="secondary"
                          [outlined]="true"
                          [disabled]="accept.busy()"
                          (onClick)="accept.run(application.user.id, true)"
                          data-testid="stores.team.acceptStandby"
                        />
                        <p-button
                          [label]="'stores.team.decline' | t"
                          icon="pi pi-times"
                          size="small"
                          severity="danger"
                          [text]="true"
                          (onClick)="ask('decline', application.user.id, application.user.displayName)"
                          data-testid="stores.team.decline"
                        />
                      </div>
                    </li>
                  } @empty {
                    <li class="muted">{{ 'stores.team.noApplications' | t }}</li>
                  }
                </ul>
              </section>
            }

            @if (team.invitations; as invitations) {
              <section class="fs-card card" data-testid="stores.team.invitations">
                <h2 class="card__title">
                  <i class="pi pi-envelope" aria-hidden="true"></i>{{ 'stores.team.invitations' | t }}
                  <span class="count">{{ invitations.length }}</span>
                </h2>
                <ul class="rows">
                  @for (invitation of invitations; track invitation.user.id) {
                    <li class="row" data-testid="stores.team.invitation" [attr.data-user]="invitation.user.displayName">
                      <div class="row__main">
                        <fs-user-chip
                          [name]="invitation.user.displayName"
                          [userId]="invitation.user.id"
                          [avatarUrl]="invitation.user.avatarUrl"
                          [verified]="invitation.user.verified"
                          [subtitle]="'stores.team.invitedAt' | t: { date: (invitation.invitedAt | fsDate: 'date') }"
                        />
                      </div>
                      <p-button
                        [label]="'stores.team.withdraw' | t"
                        icon="pi pi-undo"
                        size="small"
                        severity="secondary"
                        [text]="true"
                        [loading]="withdraw.busy()"
                        (onClick)="withdraw.run(invitation.user.id)"
                        data-testid="stores.team.withdraw"
                      />
                    </li>
                  } @empty {
                    <li class="muted">{{ 'stores.team.noInvitations' | t }}</li>
                  }
                </ul>
              </section>
            }
          </div>
        </ng-template>
      </fs-data-state>
    </div>

    <ng-template #memberRow let-member>
      <li
        class="person"
        data-testid="stores.team.member"
        [attr.data-user]="member.user.displayName"
        [attr.data-status]="member.status"
        [attr.data-manager]="member.isManager"
      >
        <fs-user-avatar
          class="person__avatar"
          [name]="member.user.displayName"
          [avatarUrl]="member.user.avatarUrl"
          [colorSeed]="member.user.id"
          size="md"
        />
        <div class="person__head">
          <span class="person__name">
            {{ member.user.displayName }}
            @if (member.verified) {
              <i class="pi pi-verified person__verified" role="img" [attr.aria-label]="'stores.team.verified' | t"></i>
            }
          </span>
          @if (member.isManager || member.status === 'JUMPER' || member.sleeping || !member.verified) {
            <span class="flags">
              @if (member.isManager) {
                <span class="flag flag--manager" data-testid="stores.team.manager">
                  <i class="pi pi-crown" aria-hidden="true"></i>{{ 'stores.team.manager' | t }}
                </span>
              }
              @if (member.status === 'JUMPER') {
                <span class="flag" data-testid="stores.team.jumperBadge">
                  <i class="pi pi-clock" aria-hidden="true"></i>{{ 'stores.team.jumper' | t }}
                </span>
              }
              @if (member.sleeping) {
                <span class="flag" data-testid="stores.team.sleeping">
                  <i class="pi pi-moon" aria-hidden="true"></i>{{ 'stores.team.sleeping' | t }}
                </span>
              }
              @if (!member.verified) {
                <span class="flag flag--warn" data-testid="stores.team.unverified">
                  <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>{{ 'stores.team.unverified' | t }}
                </span>
              }
            </span>
          }
        </div>
        <div class="person__btns">
          @if (member.phone && member.user.id !== myId()) {
            <fs-phone-button [phone]="member.phone" [label]="member.user.displayName" testId="stores.team.call" />
          }
          @if (mayEditTeam() && member.user.id !== myId()) {
            <p-button
              icon="pi pi-ellipsis-v"
              [rounded]="true"
              [text]="true"
              severity="secondary"
              [ariaLabel]="'stores.team.actions' | t: { name: member.user.displayName }"
              (onClick)="openMenu($event, member)"
              data-testid="stores.team.menu"
            />
          }
        </div>
        <dl class="tiles">
          <div class="tile">
            <i class="pi pi-shopping-bag" aria-hidden="true"></i>
            <dt>{{ 'stores.team.pickupCount' | t }}</dt>
            <dd data-testid="stores.team.pickupCount">{{ member.pickupCount }}</dd>
          </div>
          <div class="tile">
            <i class="pi pi-calendar" aria-hidden="true"></i>
            <dt>{{ 'stores.team.lastPickup' | t }}</dt>
            <dd>{{ member.lastPickupAt ? (member.lastPickupAt | fsDate: 'date') : '–' }}</dd>
          </div>
          @if (member.addedAt) {
            <div class="tile">
              <i class="pi pi-calendar-plus" aria-hidden="true"></i>
              <dt>{{ 'stores.team.addedAt' | t }}</dt>
              <dd>{{ member.addedAt | fsDate: 'date' }}</dd>
            </div>
          }
          @if (member.distanceKm !== undefined && member.distanceKm !== null) {
            <div class="tile" data-testid="stores.team.distance">
              <i class="pi pi-map-marker" aria-hidden="true"></i>
              <dt>{{ 'stores.team.distance' | t }}</dt>
              <dd>{{ 'stores.team.km' | t: { km: member.distanceKm.toFixed(1) } }}</dd>
            </div>
          }
        </dl>
      </li>
    </ng-template>

    <p-popover #menu data-testid="stores.team.menuPanel">
      @if (menuTarget(); as member) {
        <div class="menu">
          @if (member.isManager) {
            <button type="button" class="menu__item" (click)="menuAsk('removeManager', member)" data-testid="stores.team.removeManager">
              <i class="pi pi-star" aria-hidden="true"></i>{{ 'stores.team.removeManager' | t }}
            </button>
          } @else if (member.mayBeAppointed) {
            <button type="button" class="menu__item" (click)="menuAppoint(member)" data-testid="stores.team.appointManager">
              <i class="pi pi-star-fill" aria-hidden="true"></i>{{ 'stores.team.appointManager' | t }}
            </button>
          } @else if (member.appointBlocker === 'TOO_MANY_MANAGERS') {
            <!-- possible again once a manager is removed, so it stays visible with the reason (S9) -->
            <button type="button" class="menu__item" disabled aria-describedby="store-team-appoint-hint" data-testid="stores.team.appointManager">
              <i class="pi pi-star-fill" aria-hidden="true"></i>
              <span class="menu__text">
                {{ 'stores.team.appointManager' | t }}
                <small id="store-team-appoint-hint" class="menu__hint" data-testid="stores.team.appointBlocked">
                  {{ 'stores.team.appointBlocked.TOO_MANY_MANAGERS' | t }}
                </small>
              </span>
            </button>
          }
          @if (member.status === 'JUMPER') {
            <button type="button" class="menu__item" (click)="menuRegular(member)" data-testid="stores.team.toTeam">
              <i class="pi pi-arrow-up" aria-hidden="true"></i>{{ 'stores.team.toTeam' | t }}
            </button>
          } @else {
            <button type="button" class="menu__item" (click)="menuAsk('standby', member)" data-testid="stores.team.toStandby">
              <i class="pi pi-clock" aria-hidden="true"></i>{{ 'stores.team.toStandby' | t }}
            </button>
          }
          <button type="button" class="menu__item menu__item--danger" (click)="menuAsk('remove', member)" data-testid="stores.team.remove">
            <i class="pi pi-user-minus" aria-hidden="true"></i>{{ 'stores.team.remove' | t }}
          </button>
        </div>
      }
    </p-popover>

    @if (pending(); as p) {
      <fs-required-message-dialog
        [(visible)]="messageOpen"
        [title]="'stores.team.message.' + p.action + 'Title' | t: { name: p.name }"
        [text]="'stores.team.message.' + p.action + 'Text' | t"
        [confirmLabel]="'stores.team.message.' + p.action + 'Confirm' | t"
        [severity]="p.action === 'standby' ? 'warn' : 'danger'"
        [busy]="withMessage.busy()"
        (confirm)="withMessage.run(p, $event)"
        testId="stores.team.messageDialog"
      />
    }

    @if (ctx.data(); as store) {
      <fs-store-invite-dialog [(visible)]="inviteOpen" [storeId]="store.id" (invited)="refresh()" />
    }
  `,
  styleUrl: '../stores.scss',
  styles: `
    .toolbar { display: flex; justify-content: flex-end; }
    .count { margin-left: auto; color: var(--fs-text-muted); font-size: 0.85rem; font-weight: 600; }
    .list { display: grid; gap: 0.6rem; }
    .rows { display: grid; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .row {
      display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0.4rem 0.8rem; align-items: center;
      padding: 0.6rem 0.7rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md, 0.6rem);
    }
    .row--block { grid-template-columns: minmax(0, 1fr); }
    .row__main { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; min-width: 0; }
    .row__actions { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .row > p-button { grid-column: 2; grid-row: 1 / span 2; }
    .person {
      display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 0.45rem 0.75rem; align-items: start;
      padding: 0.75rem 0.85rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md, 0.6rem);
    }
    .person__avatar { grid-row: 1 / span 2; }
    .person__head { display: grid; gap: 0.3rem; min-width: 0; padding-top: 0.15rem; }
    .person__name { display: inline-flex; align-items: center; gap: 0.3rem; min-width: 0; font-weight: 650; line-height: 1.25; overflow-wrap: anywhere; }
    .person__verified { color: var(--fs-primary); font-size: 0.85em; }
    .person__btns { grid-column: 3; grid-row: 1 / span 2; display: flex; gap: 0.2rem; }
    .flags { display: inline-flex; flex-wrap: wrap; gap: 0.3rem; }
    .flag {
      display: inline-flex; gap: 0.3rem; align-items: center; padding: 0.1rem 0.5rem; border-radius: 999px;
      background: var(--fs-surface-muted); font-size: 0.78rem; font-weight: 600;
    }
    .flag--manager { color: var(--fs-primary-strong); }
    .flag--warn { background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .tiles { grid-column: 2; display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0; }
    .tile {
      display: grid; grid-template-columns: auto auto; column-gap: 0.45rem; align-items: center; min-height: 2.5rem;
      padding: 0.3rem 0.65rem 0.3rem 0.55rem; border-radius: var(--fs-radius-sm, 0.5rem); background: var(--fs-surface-muted);
    }
    .tile .pi { grid-row: 1 / span 2; color: var(--fs-text-muted); font-size: 1.1em; }
    .tile dd { grid-column: 2; grid-row: 1; margin: 0; font-weight: 650; font-variant-numeric: tabular-nums; line-height: 1.2; }
    .tile dt { grid-column: 2; grid-row: 2; color: var(--fs-text-muted); font-size: 0.72rem; line-height: 1.2; }
    @media (max-width: 47.99rem) {
      .tiles { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .tile { min-height: 2.75rem; }
    }
    .empty-filter { display: grid; justify-items: center; gap: 0.5rem; }
    .text { margin: 0; white-space: pre-line; color: var(--fs-text); }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .menu { display: grid; min-width: 14rem; }
    .menu__item {
      display: flex; gap: 0.6rem; align-items: center; padding: 0.55rem 0.75rem; border: 0; border-radius: 0.4rem;
      background: none; color: inherit; font: inherit; text-align: left; cursor: pointer;
    }
    .menu__item:hover, .menu__item:focus-visible { background: var(--fs-surface-muted); }
    .menu__item--danger { color: var(--fs-danger); }
    .menu__item:disabled { color: var(--fs-text-muted); cursor: default; background: none; }
    .menu__text { display: grid; gap: 0.1rem; }
    .menu__hint { font-size: 0.78rem; }
  `,
})
export class StoreTeamTab {
  protected readonly ctx = inject(StorePageContext);
  private readonly api = inject(StoreTeamService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);
  private readonly messages = inject(MessageService);

  protected readonly myId = computed(() => this.session.user()?.id);
  protected readonly mayEditTeam = computed(() => this.ctx.may('mayEditTeam'));

  protected readonly status = signal<TeamListStatus>(TeamListStatus.All);
  protected readonly sort = signal<TeamSort>(TeamSort.Name);
  private readonly search = signal('');
  private readonly page = signal(0);
  protected readonly filtered = computed(() => this.status() !== TeamListStatus.All || this.search() !== '');

  /** Counts from the store's StoreTeamCounts: Team = members incl. managers, Alle = Team + Springer. */
  protected readonly chips = computed<StatusChip<TeamListStatus>[]>(() => {
    const counts = this.ctx.data()?.teamCounts;
    return [
      { value: TeamListStatus.All, labelKey: 'stores.team.filter.all', count: counts && counts.members + counts.jumpers },
      { value: TeamListStatus.Manager, labelKey: 'stores.team.filter.manager' },
      { value: TeamListStatus.Member, labelKey: 'stores.team.filter.member', count: counts?.members },
      { value: TeamListStatus.Jumper, labelKey: 'stores.team.filter.jumper', count: counts?.jumpers },
    ];
  });

  protected readonly members = loadResource<TeamMemberPage, TeamQuery>({
    params: () => {
      const storeId = this.ctx.may('mayAccess') ? this.ctx.storeId() : null;
      return storeId
        ? { storeId, status: this.status(), search: this.search() || undefined, sort: this.sort(), page: this.page() }
        : undefined;
    },
    loader: (query) => this.api.listStoreTeamMembers({ ...query, size: TEAM_PAGE_SIZE }),
  });

  /** Applications and invitations (only with mayEditTeam the server sends them). */
  protected readonly team = loadResource<StoreTeam, string>({
    params: () => (this.ctx.may('mayAccess') ? (this.ctx.storeId() ?? undefined) : undefined),
    loader: (storeId) => this.api.getStoreTeam({ storeId }),
    isEmpty: () => false,
  });

  protected readonly inviteOpen = signal(false);
  protected readonly messageOpen = signal(false);
  protected readonly pending = signal<PendingMessage | null>(null);
  protected readonly menuTarget = signal<TeamMember | null>(null);
  private readonly menu = viewChild<Popover>('menu');
  private readonly filterBar = viewChild(TeamFilterBarComponent);
  private readonly membersCard = viewChild<ElementRef<HTMLElement>>('membersCard');

  protected refresh(): void {
    this.members.reload();
    this.team.reload();
    this.ctx.reload();
  }

  protected setStatus(status: TeamListStatus): void {
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
    this.status.set(TeamListStatus.All);
    this.search.set('');
    this.page.set(0);
    this.filterBar()?.reset();
  }

  protected readonly accept = runMutation(
    (userId: string, moveToStandby: boolean) =>
      this.api.acceptApplication({ storeId: this.ctx.storeId()!, userId, moveToStandby }),
    { successToastKey: 'stores.team.accepted', onSuccess: () => this.refresh() },
  );

  protected readonly withdraw = runMutation(
    (userId: string) => this.api.withdrawInvitation({ storeId: this.ctx.storeId()!, userId }),
    { successToastKey: 'stores.team.withdrawn', onSuccess: () => this.refresh() },
  );

  protected readonly appoint = runMutation(
    (userId: string) => this.api.appointManager({ storeId: this.ctx.storeId()!, userId }),
    {
      successToastKey: 'stores.team.appointed',
      onSuccess: () => this.refresh(),
      // the menu only offers appointable members; ROLE_TOO_LOW here means the role changed meanwhile (S9)
      errorToast: false,
      onError: (error) => {
        this.messages.add({
          severity: 'error',
          summary: this.i18n.t('common.error.title'),
          detail:
            error.code === 'ROLE_TOO_LOW'
              ? this.i18n.t('stores.team.appointError.ROLE_TOO_LOW')
              : this.i18n.error(error.code, errorParams(error)),
          life: 8000,
        });
        this.refresh();
      },
    },
  );

  protected readonly toRegular = runMutation(
    (userId: string) => this.api.moveToRegularTeam({ storeId: this.ctx.storeId()!, userId }),
    { successToastKey: 'stores.team.movedToTeam', onSuccess: () => this.refresh() },
  );

  protected readonly withMessage = runMutation(
    (pending: PendingMessage, message: string) => {
      const params = { storeId: this.ctx.storeId()!, userId: pending.userId, requiredMessage: { message } };
      switch (pending.action) {
        case 'remove':
          return this.api.removeMember(params);
        case 'standby':
          return this.api.moveToStandby(params);
        case 'removeManager':
          return this.api.removeManager(params);
        case 'decline':
          return this.api.declineApplication(params);
      }
    },
    {
      successToastKey: 'stores.team.saved',
      onSuccess: () => {
        this.messageOpen.set(false);
        this.refresh();
      },
    },
  );

  protected openMenu(event: Event, member: TeamMember): void {
    this.menuTarget.set(member);
    this.menu()?.toggle(event);
  }

  protected ask(action: MessageAction, userId: string, name: string): void {
    this.pending.set({ action, userId, name });
    this.messageOpen.set(true);
  }

  protected menuAsk(action: MessageAction, member: TeamMember): void {
    this.menu()?.hide();
    this.ask(action, member.user.id, member.user.displayName);
  }

  protected menuRegular(member: TeamMember): void {
    this.menu()?.hide();
    void this.toRegular.run(member.user.id);
  }

  protected menuAppoint(member: TeamMember): void {
    this.menu()?.hide();
    this.confirm.confirm({
      header: this.i18n.t('stores.team.appointTitle', { name: member.user.displayName }),
      message: this.i18n.t('stores.team.appointText'),
      acceptLabel: this.i18n.t('stores.team.appointManager'),
      rejectLabel: this.i18n.t('common.cancel'),
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.appoint.run(member.user.id),
    });
  }
}
