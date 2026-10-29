import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';

import { ProfilesService, UserSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

/**
 * /profile/me/buddies (A16 "Ich kenne"): incoming buddy requests (accept = `requestBuddy`, decline = `removeBuddy`)
 * and the confirmed buddies (remove after a confirmation). Both lists link to the profiles.
 */
@Component({
  selector: 'fs-buddies-page',
  imports: [...DATA_STATE_IMPORTS, Button, PageHeaderComponent, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'profiles.buddies.title' | t"
      icon="pi pi-users"
      [backLink]="'/profile/me'"
      [backLabel]="'profiles.buddies.back' | t"
    />

    <section class="block" aria-labelledby="buddy-requests-title">
      <h2 id="buddy-requests-title">{{ 'profiles.buddies.requests' | t }}</h2>
      <fs-data-state
        [state]="requests"
        skeleton="list"
        [skeletonRows]="2"
        emptyIcon="pi pi-user-plus"
        [emptyTitle]="'profiles.buddies.requestsEmpty' | t"
        [compact]="true"
        data-testid="profiles.buddies.requests.state"
      >
        <ng-template [fsData]="requests" let-list>
          <ul class="list">
            @for (u of list; track u.id) {
              <li class="fs-card row" data-testid="profiles.buddies.request" [attr.data-user-id]="u.id">
                <fs-user-chip
                  [name]="u.displayName"
                  [userId]="u.id"
                  [avatarUrl]="u.avatarUrl"
                  [role]="u.role"
                  [showRole]="true"
                  [sleepMode]="u.sleepMode"
                  [link]="['/profile', u.id]"
                />
                <div class="row__actions">
                  <p-button
                    icon="pi pi-check"
                    [label]="'profiles.buddy.accept' | t"
                    size="small"
                    [loading]="accept.busy()"
                    (onClick)="accept.run(u)"
                    data-testid="profiles.buddies.accept"
                  />
                  <p-button
                    icon="pi pi-times"
                    [label]="'profiles.buddy.decline' | t"
                    size="small"
                    severity="secondary"
                    [text]="true"
                    (onClick)="decline.run(u)"
                    data-testid="profiles.buddies.decline"
                  />
                </div>
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    </section>

    <section class="block" aria-labelledby="buddies-title">
      <h2 id="buddies-title">{{ 'profiles.buddies.list' | t }}</h2>
      <fs-data-state
        [state]="buddies"
        skeleton="list"
        emptyIcon="pi pi-users"
        [emptyTitle]="'profiles.buddies.empty' | t"
        [emptyText]="'profiles.buddies.emptyText' | t"
        data-testid="profiles.buddies.state"
      >
        <ng-template [fsData]="buddies" let-list>
          <ul class="list">
            @for (u of list; track u.id) {
              <li class="fs-card row" data-testid="profiles.buddies.buddy" [attr.data-user-id]="u.id">
                <fs-user-chip
                  [name]="u.displayName"
                  [userId]="u.id"
                  [avatarUrl]="u.avatarUrl"
                  [role]="u.role"
                  [showRole]="true"
                  [sleepMode]="u.sleepMode"
                  [sleepMessage]="u.sleepMessage"
                  [link]="['/profile', u.id]"
                />
                <p-button
                  icon="pi pi-user-minus"
                  [ariaLabel]="'profiles.buddy.removeNamed' | t: { name: u.displayName }"
                  severity="secondary"
                  [text]="true"
                  [rounded]="true"
                  (onClick)="confirmRemove(u)"
                  data-testid="profiles.buddies.remove"
                />
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styles: `
    :host { display: grid; gap: 1.25rem; width: min(100%, 48rem); margin: 0 auto; }
    .block { display: grid; gap: 0.6rem; }
    h2 { margin: 0; font-size: 1.1rem; }
    .list { display: grid; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
    .row { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem; padding: 0.6rem 0.8rem; margin: 0; }
    .row__actions { display: flex; flex-wrap: wrap; gap: 0.4rem; }
  `,
})
export class BuddiesPageComponent {
  private readonly api = inject(ProfilesService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly requests = loadResource<UserSummary[]>({ loader: () => this.api.listBuddyRequests() });
  protected readonly buddies = loadResource<UserSummary[]>({ loader: () => this.api.listMyBuddies() });

  private readonly reloadBoth = () => {
    this.requests.reload();
    this.buddies.reload();
  };

  protected readonly accept = runMutation((user: UserSummary) => this.api.requestBuddy({ userId: user.id }), {
    successToastKey: 'profiles.toast.buddyAccepted',
    onSuccess: this.reloadBoth,
  });
  protected readonly decline = runMutation((user: UserSummary) => this.api.removeBuddy({ userId: user.id }), {
    successToastKey: 'profiles.toast.buddyDeclined',
    onSuccess: this.reloadBoth,
  });
  protected readonly remove = runMutation((user: UserSummary) => this.api.removeBuddy({ userId: user.id }), {
    successToastKey: 'profiles.toast.buddyRemoved',
    onSuccess: this.reloadBoth,
  });

  protected confirmRemove(user: UserSummary): void {
    this.confirm.confirm({
      header: this.i18n.t('profiles.buddy.removeTitle'),
      message: this.i18n.t('profiles.buddy.removeText', { name: user.displayName }),
      acceptLabel: this.i18n.t('profiles.buddy.remove'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(user),
    });
  }
}
