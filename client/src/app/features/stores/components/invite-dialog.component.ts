import { ChangeDetectionStrategy, Component, inject, input, model, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { debounceTime } from 'rxjs';

import { InviteCandidateList, StoreTeamService, UserSummary } from '../../../api';
import { errorParams } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

interface InviteQuery {
  storeId: string;
  search: string;
}

/** Codes the server answers when someone in the list can no longer be invited (race with another manager). */
const NOT_ELIGIBLE = new Set(['ROLE_TOO_LOW', 'NOT_VERIFIED', 'ALREADY_IN_TEAM']);

/**
 * Invite a member of the store's region into the team (S7). The list comes from `listInviteCandidates`: only people
 * the server would accept (verified, at least foodsaver, not in the team yet).
 */
@Component({
  selector: 'fs-store-invite-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, FormsModule, InputText, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="'stores.invite.title' | t"
      [style]="{ width: 'min(34rem, 94vw)' }"
      data-testid="stores.invite.dialog"
    >
      <label class="label" for="store-invite-search">{{ 'stores.invite.search' | t }}</label>
      <input
        pInputText
        id="store-invite-search"
        type="search"
        autocomplete="off"
        [ngModel]="search()"
        (ngModelChange)="search.set($event)"
        data-testid="stores.invite.search"
      />
      @if (query()) {
        <fs-data-state
          [state]="results"
          skeleton="list"
          [emptyTitle]="'stores.invite.empty' | t"
          testId="stores.invite.state"
          data-testid="stores.invite.state"
        >
          <ng-template [fsData]="results" let-page>
            <ul class="results">
              @for (user of page.items; track user.id) {
                <li class="result" data-testid="stores.invite.result" [attr.data-user]="user.displayName">
                  <fs-user-chip
                    [name]="user.displayName"
                    [userId]="user.id"
                    [avatarUrl]="user.avatarUrl"
                    [verified]="user.verified"
                  />
                  <p-button
                    [label]="'stores.invite.send' | t"
                    icon="pi pi-envelope"
                    size="small"
                    [loading]="invite.busy()"
                    (onClick)="invite.run(user)"
                    data-testid="stores.invite.send"
                  />
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      } @else {
        <p class="muted">{{ 'stores.invite.hint' | t }}</p>
      }
    </p-dialog>
  `,
  styles: `
    .label { display: block; margin-bottom: 0.35rem; font-weight: 650; }
    input { width: 100%; margin-bottom: 0.75rem; }
    .results { display: grid; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .result { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }
    .muted { color: var(--fs-text-muted); }
  `,
})
export class StoreInviteDialogComponent {
  private readonly api = inject(StoreTeamService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  readonly storeId = input.required<string>();
  readonly invited = output<void>();

  protected readonly search = signal('');
  /** The person of the running invitation, named in the not-eligible toast. */
  private lastInvited?: UserSummary;
  private readonly debounced = toSignal(toObservable(this.search).pipe(debounceTime(250)), { initialValue: '' });

  protected query(): InviteQuery | undefined {
    const search = this.debounced().trim();
    return this.visible() && search.length >= 2 ? { storeId: this.storeId(), search } : undefined;
  }

  protected readonly results = loadResource<InviteCandidateList, InviteQuery>({
    params: () => this.query(),
    loader: ({ storeId, search }) => this.api.listInviteCandidates({ storeId, search, size: 10 }),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly invite = runMutation(
    (user: UserSummary) => {
      this.lastInvited = user;
      return this.api.inviteUser({ storeId: this.storeId(), userId: user.id });
    },
    {
      successToastKey: 'stores.invite.sent',
      errorToast: false,
      onError: (error) => {
        this.messages.add({
          severity: 'error',
          summary: this.i18n.t('common.error.title'),
          detail: NOT_ELIGIBLE.has(error.code)
            ? this.i18n.t('stores.invite.notEligible', {
                name: this.lastInvited?.displayName ?? '',
                reason: this.i18n.error(error.code, errorParams(error)),
              })
            : this.i18n.error(error.code, errorParams(error)),
          life: 8000,
        });
        this.results.reload();
      },
      onSuccess: () => {
        this.visible.set(false);
        this.search.set('');
        this.invited.emit();
      },
    },
  );
}
