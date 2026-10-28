import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';

import { StoreTeamService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { StorePageContext } from '../store-page.context';
import { StoreApplyDialogComponent } from './apply-dialog.component';

/**
 * The viewer's own team actions on a store page (S6, S7, S8): apply, withdraw the application, accept or decline an
 * invitation, leave the team (managers get an explanation instead, MANAGER_CANNOT_LEAVE).
 */
@Component({
  selector: 'fs-store-membership-actions',
  imports: [Button, StoreApplyDialogComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (ctx.data(); as store) {
      <div class="actions" data-testid="stores.membership" [attr.data-status]="store.membership">
        @switch (store.membership) {
          @case ('NO_MEMBER') {
            @if (store.permissions.mayApply) {
              <p-button
                [label]="'stores.apply.open' | t"
                icon="pi pi-user-plus"
                (onClick)="applyOpen.set(true)"
                data-testid="stores.membership.apply"
              />
            }
          }
          @case ('APPLIED') {
            <p class="state" data-testid="stores.membership.applied">
              <i class="pi pi-send" aria-hidden="true"></i>{{ 'stores.membership.applied' | t }}
            </p>
            <p-button
              [label]="'stores.membership.cancelApplication' | t"
              severity="secondary"
              [outlined]="true"
              [loading]="cancelApplication.busy()"
              (onClick)="cancelApplication.run()"
              data-testid="stores.membership.cancelApplication"
            />
          }
          @case ('INVITED') {
            <p class="state" data-testid="stores.membership.invited">
              <i class="pi pi-envelope" aria-hidden="true"></i>{{ 'stores.membership.invited' | t }}
            </p>
            <div class="row">
              <p-button
                [label]="'stores.membership.acceptInvitation' | t"
                icon="pi pi-check"
                [loading]="invitation.busy()"
                (onClick)="invitation.run(true)"
                data-testid="stores.membership.acceptInvitation"
              />
              <p-button
                [label]="'stores.membership.declineInvitation' | t"
                severity="secondary"
                [outlined]="true"
                [disabled]="invitation.busy()"
                (onClick)="invitation.run(false)"
                data-testid="stores.membership.declineInvitation"
              />
            </div>
          }
          @default {
            @if (store.permissions.isManager) {
              <p class="muted" data-testid="stores.membership.managerCannotLeave">{{ 'errors.MANAGER_CANNOT_LEAVE' | t }}</p>
            } @else if (store.permissions.mayLeave) {
              <p-button
                [label]="'stores.membership.leave' | t"
                icon="pi pi-sign-out"
                severity="danger"
                [outlined]="true"
                [loading]="leave.busy()"
                (onClick)="confirmLeave(store.name)"
                data-testid="stores.membership.leave"
              />
            }
          }
        }
      </div>
      <fs-store-apply-dialog [(visible)]="applyOpen" [store]="store" (applied)="ctx.reload()" />
    }
  `,
  styles: `
    .actions { display: grid; gap: 0.6rem; justify-items: start; }
    .row { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .state { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-weight: 600; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
  `,
})
export class StoreMembershipActionsComponent {
  protected readonly ctx = inject(StorePageContext);
  private readonly api = inject(StoreTeamService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly applyOpen = signal(false);

  protected readonly cancelApplication = runMutation(
    () => this.api.cancelMyStoreApplication({ storeId: this.ctx.storeId()! }),
    { successToastKey: 'stores.membership.applicationCancelled', onSuccess: () => this.ctx.reload() },
  );

  protected readonly invitation = runMutation(
    (accept: boolean) => {
      const params = { storeId: this.ctx.storeId()! };
      return accept ? this.api.acceptInvitation(params) : this.api.declineInvitation(params);
    },
    { successToastKey: 'stores.membership.invitationAnswered', onSuccess: () => this.ctx.reload() },
  );

  protected readonly leave = runMutation(
    () => this.api.removeMember({ storeId: this.ctx.storeId()!, userId: this.session.user()!.id }),
    {
      successToastKey: 'stores.membership.left',
      onSuccess: () => void this.router.navigate(['/stores/mine']),
    },
  );

  protected confirmLeave(name: string): void {
    this.confirm.confirm({
      header: this.i18n.t('stores.membership.leaveTitle', { name }),
      message: this.i18n.t('stores.membership.leaveText'),
      acceptLabel: this.i18n.t('stores.membership.leave'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.leave.run(),
    });
  }
}
