import { ChangeDetectionStrategy, Component, inject, input, model, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { debounceTime } from 'rxjs';

import { RegionMemberPage, RegionsService, StoreTeamService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

interface InviteQuery {
  regionId: string;
  search: string;
}

/** Invite a member of the store's region into the team (S7): user search over `listRegionMembers`. */
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
              @for (member of page.items; track member.user.id) {
                <li class="result" data-testid="stores.invite.result" [attr.data-user]="member.user.displayName">
                  <fs-user-chip
                    [name]="member.user.displayName"
                    [userId]="member.user.id"
                    [avatarUrl]="member.user.avatarUrl"
                    [verified]="member.user.verified"
                  />
                  <p-button
                    [label]="'stores.invite.send' | t"
                    icon="pi pi-envelope"
                    size="small"
                    [loading]="invite.busy()"
                    (onClick)="invite.run(member.user.id)"
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
  private readonly regionsApi = inject(RegionsService);
  private readonly api = inject(StoreTeamService);

  readonly visible = model(false);
  readonly storeId = input.required<string>();
  readonly regionId = input.required<string>();
  readonly invited = output<void>();

  protected readonly search = signal('');
  private readonly debounced = toSignal(toObservable(this.search).pipe(debounceTime(250)), { initialValue: '' });

  protected query(): InviteQuery | undefined {
    const search = this.debounced().trim();
    return this.visible() && search.length >= 2 ? { regionId: this.regionId(), search } : undefined;
  }

  protected readonly results = loadResource<RegionMemberPage, InviteQuery>({
    params: () => this.query(),
    loader: ({ regionId, search }) => this.regionsApi.listRegionMembers({ regionId, search, size: 10 }),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly invite = runMutation(
    (userId: string) => this.api.inviteUser({ storeId: this.storeId(), userId }),
    {
      successToastKey: 'stores.invite.sent',
      onSuccess: () => {
        this.visible.set(false);
        this.search.set('');
        this.invited.emit();
      },
    },
  );
}
