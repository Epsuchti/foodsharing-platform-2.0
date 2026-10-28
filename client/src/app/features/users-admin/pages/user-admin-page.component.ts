import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { map } from 'rxjs';

import { AdminUsersService, Gender, HistorySubjectType, RegionRef, UserProfile } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PhoneInputComponent } from '../../../shared/phone/phone-input.component';
import { RegionPickerComponent } from '../../../shared/region-picker/region-picker.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { UsersAdminResources } from '../users-admin.resource';

/**
 * /users/:userId (AD1, A4 admin part): a user's profile for ambassadors/ORGA: facts, admin-editable fields
 * (name, address, phone, birthday, gender, home region) with a reason, enable/disable with a reason and the
 * change history.
 */
@Component({
  selector: 'fs-user-admin-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    HistoryTimelineComponent,
    InputText,
    PageHeaderComponent,
    PhoneInputComponent,
    ReactiveFormsModule,
    RegionPickerComponent,
    RequiredMessageDialogComponent,
    RouterLink,
    Select,
    TPipe,
    UserAvatarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './user-admin-page.component.html',
  styleUrl: './user-admin-page.component.scss',
})
export class UserAdminPageComponent {
  private readonly api = inject(AdminUsersService);
  private readonly resources = inject(UsersAdminResources);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);

  protected readonly userId = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('userId') ?? undefined)),
  );
  protected readonly viewerId = computed(() => this.session.user()?.id);
  protected readonly subjectType = HistorySubjectType.User;

  protected readonly user = loadResource<UserProfile, string>({
    params: () => this.userId(),
    loader: (userId) => this.api.getUserProfileAsAdmin({ userId }),
  });

  protected readonly history = computed(() => {
    const userId = this.userId();
    return userId ? this.resources.changeHistory(userId) : null;
  });

  protected readonly genders = computed(() => {
    this.i18n.language();
    return Object.values(Gender).map((value) => ({ value, label: this.i18n.t(`usersAdmin.gender.${value}`) }));
  });

  // --- edit ---
  protected readonly editOpen = signal(false);
  protected readonly newHome = signal<RegionRef | null>(null);
  protected readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true }),
    lastName: new FormControl('', { nonNullable: true }),
    street: new FormControl('', { nonNullable: true }),
    postcode: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true }),
    mobilePhone: new FormControl<string | null>(null),
    dateOfBirth: new FormControl('', { nonNullable: true }),
    gender: new FormControl<Gender>(Gender.NotSelected, { nonNullable: true }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3)] }),
  });

  protected openEdit(user: UserProfile): void {
    this.newHome.set(null);
    this.form.reset({
      firstName: user.firstName ?? '',
      lastName: user.lastName ?? '',
      street: user.street ?? '',
      postcode: user.postcode ?? '',
      city: user.city ?? '',
      mobilePhone: user.mobilePhone ?? null,
      dateOfBirth: user.dateOfBirth ?? '',
      gender: user.gender ?? Gender.NotSelected,
      reason: '',
    });
    this.editOpen.set(true);
  }

  protected readonly save = runMutation(
    () => {
      const value = this.form.getRawValue();
      return this.api.updateUserProfileAsAdmin({
        userId: this.userId()!,
        adminUserProfileUpdate: {
          ...value,
          mobilePhone: value.mobilePhone ?? '',
          dateOfBirth: value.dateOfBirth || undefined,
          homeRegionId: this.newHome()?.id,
          reason: value.reason.trim(),
        },
      });
    },
    {
      form: this.form,
      successToastKey: 'usersAdmin.user.saved',
      onSuccess: (user) => {
        this.user.set(user);
        this.editOpen.set(false);
      },
    },
  );

  // --- enable / disable ---
  protected readonly activeOpen = signal(false);

  protected readonly setActive = runMutation(
    (reason: string) =>
      this.api.setUserActive({
        userId: this.userId()!,
        activeUpdate: { active: this.user.data()?.active === false, reason },
      }),
    {
      successToastKey: 'usersAdmin.user.activeSaved',
      onSuccess: (user) => {
        this.user.set(user);
        this.activeOpen.set(false);
      },
    },
  );
}
