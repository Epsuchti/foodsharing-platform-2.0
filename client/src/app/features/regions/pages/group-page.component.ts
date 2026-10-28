import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';
import { map } from 'rxjs';

import {
  GroupApplication,
  GroupApplicationStatus,
  GroupMember,
  GroupsService,
  WorkingGroupDetail,
} from '../../../api';
import { isOrga } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { ApplyDialogComponent } from '../shared/apply-dialog.component';
import { GroupFormDialogComponent, GroupFormValue } from '../shared/group-form-dialog.component';

/**
 * /groups/:groupId (R9–R12): working group detail with description, requirements checklist, join/apply/cancel,
 * members (admins manage them), pending applications (accept / decline with message), edit, group mail, archive
 * and the conference room.
 */
@Component({
  selector: 'fs-group-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    ApplyDialogComponent,
    Button,
    DateTimePipe,
    Dialog,
    GroupFormDialogComponent,
    InputText,
    MarkdownComponent,
    ReactiveFormsModule,
    RequiredMessageDialogComponent,
    RouterLink,
    TPipe,
    Textarea,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './group-page.component.html',
  styleUrls: ['../tabs/tab.scss', './group-page.component.scss'],
})
export class GroupPageComponent {
  private readonly api = inject(GroupsService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  private readonly groupId = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('groupId') ?? undefined)),
  );

  protected readonly group = loadResource<WorkingGroupDetail, string>({
    params: () => this.groupId(),
    loader: (groupId) => this.api.getGroup({ groupId }),
  });

  protected readonly isOrga = computed(() => isOrga(this.session.session()));
  protected readonly mayManage = computed(() => !!this.group.data()?.mayEdit);
  protected readonly seesMembers = computed(() => {
    const group = this.group.data();
    return !!group && (group.membership.member || group.mayEdit);
  });

  protected readonly members = loadResource<GroupMember[], string>({
    params: () => (this.seesMembers() ? this.groupId() : undefined),
    loader: (groupId) => this.api.listGroupMembers({ groupId }),
  });

  protected readonly applications = loadResource<GroupApplication[], string>({
    params: () => (this.mayManage() ? this.groupId() : undefined),
    loader: (groupId) => this.api.listGroupApplications({ groupId, status: GroupApplicationStatus.Pending }),
  });

  // --- membership ---
  protected readonly applyOpen = signal(false);

  protected readonly join = runMutation(() => this.api.joinOpenGroup({ groupId: this.groupId()! }), {
    successToastKey: 'regions.groups.joined',
    onSuccess: () => this.reloadAll(),
  });

  protected readonly cancelApplication = runMutation(
    () => this.api.cancelMyApplication({ groupId: this.groupId()! }),
    { successToastKey: 'regions.group.applicationCancelled', onSuccess: () => this.group.reload() },
  );

  // --- edit ---
  protected readonly editOpen = signal(false);

  protected readonly update = runMutation(
    (value: GroupFormValue) => {
      // the function cannot change after creation
      const { function: _function, ...update } = value;
      return this.api.updateGroup({ groupId: this.groupId()!, workingGroupUpdate: update });
    },
    {
      successToastKey: 'regions.group.saved',
      onSuccess: (group) => {
        this.group.set(group);
        this.editOpen.set(false);
      },
    },
  );

  // --- archive ---
  protected confirmArchive(group: WorkingGroupDetail): void {
    this.confirm.confirm({
      header: this.i18n.t('regions.group.archiveTitle', { name: group.name }),
      message: this.i18n.t('regions.group.archiveText'),
      acceptLabel: this.i18n.t('regions.group.archive'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.archive.run(),
    });
  }

  protected readonly archive = runMutation(() => this.api.archiveGroup({ groupId: this.groupId()! }), {
    successToastKey: 'regions.group.archived',
    onSuccess: () => this.group.reload(),
  });

  // --- mail ---
  protected readonly mailOpen = signal(false);
  protected readonly mailForm = new FormGroup({
    subject: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    body: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(20000)] }),
  });

  protected openMail(): void {
    this.mailForm.reset();
    this.mailOpen.set(true);
  }

  protected readonly sendMail = runMutation(
    () => this.api.sendGroupMail({ groupId: this.groupId()!, groupMail: this.mailForm.getRawValue() }),
    {
      form: this.mailForm,
      successToastKey: 'regions.group.mailSent',
      onSuccess: () => this.mailOpen.set(false),
    },
  );

  // --- members ---
  protected readonly removeTarget = signal<GroupMember | null>(null);
  protected readonly removeOpen = signal(false);

  protected askRemove(member: GroupMember): void {
    this.removeTarget.set(member);
    this.removeOpen.set(true);
  }

  protected readonly removeMember = runMutation(
    (reason: string) =>
      this.api.removeGroupMember({
        groupId: this.groupId()!,
        userId: this.removeTarget()!.user.id,
        reasonBody: { reason },
      }),
    {
      successToastKey: 'regions.group.memberRemoved',
      onSuccess: () => {
        this.removeOpen.set(false);
        this.reloadAll();
      },
    },
  );

  protected readonly toggleAdmin = runMutation(
    (member: GroupMember) => {
      const params = { groupId: this.groupId()!, userId: member.user.id };
      return member.admin ? this.api.removeGroupAdmin(params) : this.api.setGroupAdmin(params);
    },
    { successToastKey: 'regions.group.adminSaved', onSuccess: () => this.reloadAll() },
  );

  // --- applications ---
  protected readonly declineTarget = signal<GroupApplication | null>(null);
  protected readonly declineOpen = signal(false);

  protected askDecline(application: GroupApplication): void {
    this.declineTarget.set(application);
    this.declineOpen.set(true);
  }

  protected readonly decide = runMutation(
    (application: GroupApplication, accept: boolean, message?: string) =>
      this.api.decideGroupApplication({
        groupId: this.groupId()!,
        applicationId: application.id,
        groupApplicationDecision: { accept, message },
      }),
    {
      successToastKey: 'regions.group.decided',
      onSuccess: () => {
        this.declineOpen.set(false);
        this.applications.reload();
        this.reloadAll();
      },
    },
  );

  protected readonly requirementRows = computed(() => {
    const group = this.group.data();
    if (!group) {
      return [];
    }
    const { requirements: req, requirementCheck: check } = group;
    return [
      { key: 'minPickups', min: req.minPickups, have: check?.pickups, ok: check?.pickupsOk },
      { key: 'minBananas', min: req.minBananas, have: check?.bananas, ok: check?.bananasOk },
      { key: 'minWeeks', min: req.minWeeks, have: check?.weeks, ok: check?.weeksOk },
    ].filter((row) => row.min > 0);
  });

  /** A requirement the user does not meet yet: applying is not offered (the server would refuse it). */
  protected readonly requirementsMissing = computed(() => this.requirementRows().some((row) => row.ok === false));

  private reloadAll(): void {
    this.group.reload();
    if (this.seesMembers()) {
      this.members.reload();
    }
  }
}
