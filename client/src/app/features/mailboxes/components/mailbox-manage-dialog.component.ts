import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';

import { Mailbox, MailboxOwnerType, MailboxesService, Role, UserSummary } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';

/** Manage a mailbox (MB1, `mayManage`): display name, readers (region mailboxes), delete (ORGA). */
@Component({
  selector: 'fs-mailbox-manage-dialog',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Dialog,
    FormsModule,
    InputText,
    TPipe,
    UserChipComponent,
    UserPickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="!!mailbox()"
      (visibleChange)="$event ? null : closed.emit()"
      [modal]="true"
      [header]="'mailboxes.manage.title' | t"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(32rem, calc(100vw - 2rem))' }"
    >
      @if (mailbox(); as box) {
        <div class="form">
          <p class="address" data-testid="mailboxes.manage.address">{{ box.address }}</p>
          <form class="row" (ngSubmit)="rename.run(box)">
            <label for="mailbox-name">{{ 'mailboxes.manage.name' | t }}</label>
            <input
              pInputText
              id="mailbox-name"
              name="name"
              maxlength="100"
              [(ngModel)]="name"
              data-testid="mailboxes.manage.name"
            />
            <p-button
              type="submit"
              [label]="'common.save' | t"
              size="small"
              [loading]="rename.busy()"
              data-testid="mailboxes.manage.saveName"
            />
          </form>
          @if (box.ownerType === owner.Region) {
            <h3>{{ 'mailboxes.manage.members' | t }}</h3>
            <fs-data-state
              [state]="members"
              skeleton="list"
              [compact]="true"
              [emptyTitle]="'mailboxes.manage.noMembers' | t"
              data-testid="mailboxes.manage.members"
            >
              <ng-template [fsData]="members" let-users>
                <ul class="members">
                  @for (u of users; track u.id) {
                    <li data-testid="mailboxes.manage.member" [attr.data-user-id]="u.id">
                      <fs-user-chip
                        [name]="u.displayName"
                        [userId]="u.id"
                        [avatarUrl]="u.avatarUrl"
                      />
                      <p-button
                        icon="pi pi-times"
                        size="small"
                        [text]="true"
                        [rounded]="true"
                        severity="danger"
                        [ariaLabel]="('mailboxes.manage.removeMember' | t) + ': ' + u.displayName"
                        (onClick)="saveMembers.run(without(users, u))"
                        data-testid="mailboxes.manage.removeMember"
                      />
                    </li>
                  }
                </ul>
              </ng-template>
            </fs-data-state>
            <fs-user-picker
              [label]="'mailboxes.manage.addMember' | t"
              [excludeIds]="memberIds()"
              (picked)="saveMembers.run(with($event))"
              testId="mailboxes.manage.picker"
            />
          }
          @if (isOrga()) {
            <div class="danger">
              <p-button
                [label]="'mailboxes.manage.delete' | t"
                icon="pi pi-trash"
                severity="danger"
                [outlined]="true"
                (onClick)="askDelete(box)"
                data-testid="mailboxes.manage.delete"
              />
            </div>
          }
        </div>
      }
    </p-dialog>
  `,
  styles: `
    .form {
      display: grid;
      gap: 0.75rem;
    }
    .address {
      margin: 0;
      font-weight: 700;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    .row input {
      flex: 1;
      min-width: 10rem;
    }
    h3 {
      margin: 0.5rem 0 0;
      font-size: 1rem;
    }
    .members {
      display: grid;
      gap: 0.3rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .members li {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .danger {
      display: flex;
      justify-content: flex-end;
      padding-top: 0.75rem;
      border-top: 1px solid var(--fs-border);
    }
  `,
})
export class MailboxManageDialogComponent {
  private readonly api = inject(MailboxesService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);

  readonly mailbox = input<Mailbox | null>(null);
  readonly closed = output<void>();
  readonly changed = output<void>();

  protected readonly owner = MailboxOwnerType;
  /** Deleting a mailbox is ORGA only (A35). */
  protected readonly isOrga = computed(() => this.session.hasRole(Role.Orga));
  protected name = '';
  private readonly version = signal(0);

  protected readonly members = loadResource<UserSummary[], { id: string; version: number }>({
    params: () => {
      const box = this.mailbox();
      return box && box.ownerType === MailboxOwnerType.Region
        ? { id: box.id, version: this.version() }
        : undefined;
    },
    loader: ({ id }) => this.api.listMailboxMembers({ mailboxId: id }),
    isEmpty: (list) => list.length === 0,
  });

  protected readonly memberIds = computed(() => (this.members.data() ?? []).map((u) => u.id));

  constructor() {
    effect(() => {
      const box = this.mailbox();
      untracked(() => (this.name = box?.name ?? ''));
    });
  }

  protected with(user: UserSummary): string[] {
    return [...this.memberIds(), user.id];
  }

  protected without(users: UserSummary[], user: UserSummary): string[] {
    return users.filter((u) => u.id !== user.id).map((u) => u.id);
  }

  protected readonly saveMembers = runMutation(
    (userIds: string[]) =>
      this.api.setMailboxMembers({
        mailboxId: this.mailbox()!.id,
        mailboxMembersUpdate: { userIds },
      }),
    {
      successToastKey: 'mailboxes.manage.saved',
      onSuccess: () => this.version.update((v) => v + 1),
    },
  );

  protected readonly rename = runMutation(
    (box: Mailbox) =>
      this.api.updateMailbox({ mailboxId: box.id, mailboxUpdate: { name: this.name.trim() } }),
    { successToastKey: 'mailboxes.manage.saved', onSuccess: () => this.changed.emit() },
  );

  private readonly remove = runMutation(
    (box: Mailbox) => this.api.deleteMailbox({ mailboxId: box.id, force: true }),
    {
      successToastKey: 'mailboxes.manage.deleted',
      onSuccess: () => {
        this.closed.emit();
        this.changed.emit();
      },
    },
  );

  protected askDelete(box: Mailbox): void {
    this.confirm.confirm({
      header: this.i18n.t('mailboxes.manage.deleteTitle', { address: box.address }),
      message: this.i18n.t('mailboxes.manage.deleteText'),
      acceptLabel: this.i18n.t('mailboxes.manage.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(box),
    });
  }
}
