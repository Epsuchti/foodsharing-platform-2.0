import { ChangeDetectionStrategy, Component, inject, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';

import { Conversation, MessagingService, UserSummary } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';

/**
 * "New message" (N1): pick one or more people; one person without a group name reuses the direct conversation
 * (`lookup`), otherwise a new group is created (optional name).
 */
@Component({
  selector: 'fs-new-conversation-dialog',
  imports: [Button, Dialog, FormsModule, InputText, TPipe, UserChipComponent, UserPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [header]="'messaging.new.title' | t"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(30rem, calc(100vw - 2rem))' }"
      data-testid="messaging.new.dialog"
    >
      <div class="form">
        @if (selected().length > 0) {
          <ul class="chips" [attr.aria-label]="'messaging.new.recipients' | t">
            @for (user of selected(); track user.id) {
              <li data-testid="messaging.new.recipient">
                <fs-user-chip [name]="user.displayName" [userId]="user.id" [avatarUrl]="user.avatarUrl" />
                <p-button
                  icon="pi pi-times"
                  size="small"
                  [text]="true"
                  [rounded]="true"
                  severity="secondary"
                  [ariaLabel]="('messaging.new.remove' | t) + ': ' + user.displayName"
                  (onClick)="remove(user)"
                />
              </li>
            }
          </ul>
        }
        <fs-user-picker [excludeIds]="selectedIds()" (picked)="add($event)" testId="messaging.new.picker" />
        @if (selected().length > 1) {
          <label for="new-conversation-name">{{ 'messaging.new.groupName' | t }}</label>
          <input pInputText id="new-conversation-name" maxlength="100" [(ngModel)]="name" data-testid="messaging.new.name" />
        }
        <div class="actions">
          <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
          <p-button
            [label]="'messaging.new.start' | t"
            icon="pi pi-send"
            [disabled]="selected().length === 0"
            [loading]="start.busy()"
            (onClick)="start.run()"
            data-testid="messaging.new.start"
          />
        </div>
      </div>
    </p-dialog>
  `,
  styles: `
    .form {
      display: grid;
      gap: 0.75rem;
    }
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .chips li {
      display: inline-flex;
      align-items: center;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
    }
  `,
})
export class NewConversationDialogComponent {
  private readonly api = inject(MessagingService);

  readonly visible = model(false);
  readonly created = output<Conversation>();

  protected readonly selected = signal<UserSummary[]>([]);
  protected name = '';

  protected selectedIds(): string[] {
    return this.selected().map((user) => user.id);
  }

  protected add(user: UserSummary): void {
    this.selected.update((list) => [...list, user]);
  }

  protected remove(user: UserSummary): void {
    this.selected.update((list) => list.filter((u) => u.id !== user.id));
  }

  protected readonly start = runMutation(
    () => {
      const memberIds = this.selectedIds();
      const name = this.name.trim();
      return memberIds.length === 1 || !name
        ? this.api.lookupConversation({ conversationLookup: { memberIds } })
        : this.api.createConversation({ conversationCreate: { memberIds, name } });
    },
    {
      onSuccess: (conversation: Conversation) => {
        this.selected.set([]);
        this.name = '';
        this.visible.set(false);
        this.created.emit(conversation);
      },
    },
  );
}
