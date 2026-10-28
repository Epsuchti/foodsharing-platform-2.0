import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Popover } from '@openng/optimus-ui/popover';

import { StoreTeam, StoreTeamService, TeamMember } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { StoreInviteDialogComponent } from '../components/invite-dialog.component';
import { PhoneButtonComponent } from '../../../shared/phone/phone-button.component';
import { StorePageContext } from '../store-page.context';

/** Actions that need a required message (A21: delivered as bell + mail and stored in the store log). */
type MessageAction = 'remove' | 'standby' | 'removeManager' | 'decline';

interface PendingMessage {
  action: MessageAction;
  userId: string;
  name: string;
}

/**
 * /stores/:storeId/team (S5, S7, S8, S9): members, jumpers, applications and invitations. Managers (mayEditTeam) get
 * a row menu (appoint/remove manager, standby/back to team, remove with a required message), accept/decline for
 * applications and an invite dialog. Distance only with maySeeMemberDistance (server omits it otherwise).
 */
@Component({
  selector: 'fs-store-team-tab',
  imports: [
    PhoneButtonComponent,
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    NgTemplateOutlet,
    Popover,
    RequiredMessageDialogComponent,
    StoreInviteDialogComponent,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state
      [state]="team"
      skeleton="list"
      [askWho]="'stores.page.askWho' | t"
      testId="stores.team.state"
      data-testid="stores.team.state"
    >
      <ng-template [fsData]="team" let-team>
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

          <section class="fs-card card" data-testid="stores.team.members">
            <h2 class="card__title">
              <i class="pi pi-users" aria-hidden="true"></i>{{ 'stores.team.members' | t }}
              <span class="count">{{ team.members.length }}</span>
            </h2>
            <ul class="rows">
              @for (member of team.members; track member.user.id) {
                <ng-container *ngTemplateOutlet="memberRow; context: { $implicit: member }" />
              } @empty {
                <li class="muted">{{ 'stores.team.noMembers' | t }}</li>
              }
            </ul>
          </section>

          <section class="fs-card card" data-testid="stores.team.jumpers">
            <h2 class="card__title">
              <i class="pi pi-clock" aria-hidden="true"></i>{{ 'stores.team.jumpers' | t }}
              <span class="count">{{ team.jumpers.length }}</span>
            </h2>
            <ul class="rows">
              @for (member of team.jumpers; track member.user.id) {
                <ng-container *ngTemplateOutlet="memberRow; context: { $implicit: member }" />
              } @empty {
                <li class="muted">{{ 'stores.team.noJumpers' | t }}</li>
              }
            </ul>
          </section>

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

    <ng-template #memberRow let-member>
      <li
        class="row"
        data-testid="stores.team.member"
        [attr.data-user]="member.user.displayName"
        [attr.data-status]="member.status"
        [attr.data-manager]="member.isManager"
      >
        <div class="row__main">
          <fs-user-chip
            [name]="member.user.displayName"
            [userId]="member.user.id"
            [avatarUrl]="member.user.avatarUrl"
            [verified]="member.verified"
          />
          <span class="flags">
            @if (member.isManager) {
              <span class="flag flag--manager" data-testid="stores.team.manager">
                <i class="pi pi-crown" aria-hidden="true"></i>{{ 'stores.team.manager' | t }}
              </span>
            }
            @if (member.sleeping) {
              <span class="flag" data-testid="stores.team.sleeping">
                <i class="pi pi-moon" aria-hidden="true"></i>{{ 'stores.team.sleeping' | t }}
              </span>
            }
            @if (!member.verified) {
              <span class="flag flag--warn" data-testid="stores.team.unverified">{{ 'stores.team.unverified' | t }}</span>
            }
          </span>
        </div>
        <dl class="stats">
          <div>
            <dt>{{ 'stores.team.pickupCount' | t }}</dt>
            <dd data-testid="stores.team.pickupCount">{{ member.pickupCount }}</dd>
          </div>
          <div>
            <dt>{{ 'stores.team.lastPickup' | t }}</dt>
            <dd>{{ member.lastPickupAt ? (member.lastPickupAt | fsDate: 'date') : '–' }}</dd>
          </div>
          @if (member.addedAt) {
            <div>
              <dt>{{ 'stores.team.addedAt' | t }}</dt>
              <dd>{{ member.addedAt | fsDate: 'date' }}</dd>
            </div>
          }
          @if (member.distanceKm !== undefined && member.distanceKm !== null) {
            <div data-testid="stores.team.distance">
              <dt>{{ 'stores.team.distance' | t }}</dt>
              <dd>{{ 'stores.team.km' | t: { km: member.distanceKm.toFixed(1) } }}</dd>
            </div>
          }
        </dl>
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
      </li>
    </ng-template>

    <p-popover #menu data-testid="stores.team.menuPanel">
      @if (menuTarget(); as member) {
        <div class="menu">
          @if (member.isManager) {
            <button type="button" class="menu__item" (click)="menuAsk('removeManager', member)" data-testid="stores.team.removeManager">
              <i class="pi pi-star" aria-hidden="true"></i>{{ 'stores.team.removeManager' | t }}
            </button>
          } @else {
            <button type="button" class="menu__item" (click)="menuAppoint(member)" data-testid="stores.team.appointManager">
              <i class="pi pi-star-fill" aria-hidden="true"></i>{{ 'stores.team.appointManager' | t }}
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
      <fs-store-invite-dialog [(visible)]="inviteOpen" [storeId]="store.id" [regionId]="store.region.id" (invited)="team.reload()" />
    }
  `,
  styleUrl: '../stores.scss',
  styles: `
    .toolbar { display: flex; justify-content: flex-end; }
    .count { margin-left: auto; color: var(--fs-text-muted); font-size: 0.85rem; font-weight: 600; }
    .rows { display: grid; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .row {
      display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0.4rem 0.8rem; align-items: center;
      padding: 0.6rem 0.7rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md, 0.6rem);
    }
    .row--block { grid-template-columns: minmax(0, 1fr); }
    .row__main { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; min-width: 0; }
    .row__actions { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .row .stats { grid-column: 1; }
    .row > p-button { grid-column: 2; grid-row: 1 / span 2; }
    .flags { display: inline-flex; flex-wrap: wrap; gap: 0.3rem; }
    .flag {
      display: inline-flex; gap: 0.3rem; align-items: center; padding: 0.1rem 0.5rem; border-radius: 999px;
      background: var(--fs-surface-muted); font-size: 0.78rem; font-weight: 600;
    }
    .flag--manager { color: var(--fs-primary-strong); }
    .flag--warn { background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .stats { display: flex; flex-wrap: wrap; gap: 0.2rem 1.2rem; margin: 0; font-size: 0.82rem; }
    .stats div { display: flex; gap: 0.3rem; }
    .stats dt { color: var(--fs-text-muted); }
    .stats dd { margin: 0; font-weight: 600; }
    .text { margin: 0; white-space: pre-line; color: var(--fs-text); }
    .muted { color: var(--fs-text-muted); }
    .menu { display: grid; min-width: 14rem; }
    .menu__item {
      display: flex; gap: 0.6rem; align-items: center; padding: 0.55rem 0.75rem; border: 0; border-radius: 0.4rem;
      background: none; color: inherit; font: inherit; text-align: left; cursor: pointer;
    }
    .menu__item:hover, .menu__item:focus-visible { background: var(--fs-surface-muted); }
    .menu__item--danger { color: var(--fs-danger); }
  `,
})
export class StoreTeamTab {
  protected readonly ctx = inject(StorePageContext);
  private readonly api = inject(StoreTeamService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);

  protected readonly myId = computed(() => this.session.user()?.id);
  protected readonly mayEditTeam = computed(() => this.ctx.may('mayEditTeam'));

  protected readonly team = loadResource<StoreTeam, string>({
    params: () => (this.ctx.may('mayAccess') ? (this.ctx.storeId() ?? undefined) : undefined),
    loader: (storeId) => this.api.getStoreTeam({ storeId }),
  });

  protected readonly inviteOpen = signal(false);
  protected readonly messageOpen = signal(false);
  protected readonly pending = signal<PendingMessage | null>(null);
  protected readonly menuTarget = signal<TeamMember | null>(null);
  private readonly menu = viewChild<Popover>('menu');

  private refresh(): void {
    this.team.reload();
    this.ctx.reload();
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
    { successToastKey: 'stores.team.appointed', onSuccess: () => this.refresh() },
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
