import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { ContactVisibility, Gender, UserProfile, UserProfileUpdate, UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PhoneInputComponent } from '../../../shared/phone/phone-input.component';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';

const text = (max: number) => new FormControl('', { nonNullable: true, validators: [Validators.maxLength(max)] });

/**
 * /account/profile (A4, plan §8.3): the own profile with all fields the user edits (name, gender, birthday,
 * phones, homepage, contact visibility, address, about me, newsletter). The home region changes on the region
 * page; fields admins correct (address, birthday) are recorded in the history like the user's own changes.
 */
@Component({
  selector: 'fs-profile-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    InputText,
    PageHeaderComponent,
    PhoneInputComponent,
    ReactiveFormsModule,
    RouterLink,
    Select,
    TPipe,
    Textarea,
    ToggleSwitch,
    UserAvatarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile-page.component.html',
  styleUrl: './profile-page.component.scss',
})
export class ProfilePageComponent {
  private readonly usersApi = inject(UsersService);
  private readonly i18n = inject(I18nService);

  protected readonly profile = loadResource<UserProfile>({ loader: () => this.usersApi.getCurrentUser() });

  protected readonly genders = computed(() => {
    this.i18n.language();
    return Object.values(Gender).map((value) => ({ value, label: this.i18n.t(`account.profile.gender.${value}`) }));
  });
  protected readonly visibilities = computed(() => {
    this.i18n.language();
    return Object.values(ContactVisibility).map((value) => ({
      value,
      label: this.i18n.t(`account.profile.visibility.${value}`),
    }));
  });

  protected readonly form = new FormGroup({
    firstName: text(100),
    lastName: text(100),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    gender: new FormControl<Gender>(Gender.NotSelected, { nonNullable: true }),
    dateOfBirth: new FormControl('', { nonNullable: true }),
    mobilePhone: new FormControl<string | null>(null),
    landlinePhone: new FormControl<string | null>(null),
    homepage: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^(https?:\/\/\S+)?$/)] }),
    contactVisibility: new FormControl<ContactVisibility>(ContactVisibility.Team, { nonNullable: true }),
    street: text(200),
    postcode: text(20),
    city: text(100),
    country: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^([A-Za-z]{2})?$/)] }),
    aboutMePublic: text(32600),
    aboutMeInternal: text(32600),
    positionDescription: text(500),
    newsletterOptIn: new FormControl(false, { nonNullable: true }),
  });

  constructor() {
    effect(() => {
      const user = this.profile.data();
      if (user) {
        this.fill(user);
      }
    });
  }

  private fill(user: UserProfile): void {
    this.form.reset({
      firstName: user.firstName ?? '',
      lastName: user.lastName ?? '',
      displayName: user.displayName,
      gender: user.gender ?? Gender.NotSelected,
      dateOfBirth: user.dateOfBirth ?? '',
      mobilePhone: user.mobilePhone ?? null,
      landlinePhone: user.landlinePhone ?? null,
      homepage: user.homepage ?? '',
      contactVisibility: user.contactVisibility ?? ContactVisibility.Team,
      street: user.street ?? '',
      postcode: user.postcode ?? '',
      city: user.city ?? '',
      country: user.country ?? '',
      aboutMePublic: user.aboutMePublic ?? '',
      aboutMeInternal: user.aboutMeInternal ?? '',
      positionDescription: user.positionDescription ?? '',
      newsletterOptIn: user.newsletterOptIn ?? false,
    });
  }

  /** Empty text clears a field (PATCH: absent = unchanged, so cleared fields are sent as empty strings). */
  private patch(): UserProfileUpdate {
    const value = this.form.getRawValue();
    return {
      ...value,
      displayName: value.displayName.trim(),
      dateOfBirth: value.dateOfBirth || undefined,
      mobilePhone: value.mobilePhone ?? '',
      landlinePhone: value.landlinePhone ?? '',
      country: value.country.toUpperCase(),
    };
  }

  protected reset(): void {
    const user = this.profile.data();
    if (user) {
      this.fill(user);
    }
  }

  protected readonly save = runMutation(() => this.usersApi.updateMyProfile({ userProfileUpdate: this.patch() }), {
    form: this.form,
    successToastKey: 'account.profile.saved',
    onSuccess: (user) => this.profile.set(user),
  });
}
