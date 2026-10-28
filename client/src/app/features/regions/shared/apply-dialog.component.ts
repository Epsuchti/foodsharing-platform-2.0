import { ChangeDetectionStrategy, Component, inject, input, model, output } from '@angular/core';

import { GroupsService, WorkingGroupDetail, WorkingGroupSummary } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';

/** Application to a working group (R9): a short introduction for the group admins. */
@Component({
  selector: 'fs-apply-dialog',
  imports: [RequiredMessageDialogComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-required-message-dialog
      [(visible)]="visible"
      [title]="'regions.apply.title' | t: { name: group()?.name ?? '' }"
      [text]="'regions.apply.text' | t"
      [label]="'regions.apply.label' | t"
      [confirmLabel]="'regions.apply.send' | t"
      [minLength]="1"
      [maxLength]="4000"
      [busy]="apply.busy()"
      (confirm)="apply.run($event)"
      testId="regions.apply"
    />
  `,
})
export class ApplyDialogComponent {
  private readonly api = inject(GroupsService);

  readonly visible = model(false);
  readonly group = input<WorkingGroupSummary | WorkingGroupDetail | null>(null);
  readonly applied = output<void>();

  protected readonly apply = runMutation(
    (text: string) => this.api.applyToGroup({ groupId: this.group()!.id, groupApplicationCreate: { text } }),
    {
      successToastKey: 'regions.apply.sent',
      onSuccess: () => {
        this.visible.set(false);
        this.applied.emit();
      },
    },
  );
}
