import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Observable, catchError, concat, last, of, switchMap, tap, throwError } from 'rxjs';

import {
  AuthService,
  RegionRef,
  RegionsService,
  UserProfile,
  UsersService,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PhoneInputComponent } from '../../../shared/phone/phone-input.component';
import { RegionPickerComponent } from '../../../shared/region-picker/region-picker.component';
import { RegionCardComponent } from '../components/region-card.component';

/**
 * `/onboarding/profile` (§11.1 step 2): first and last name, mobile phone and the home region (picker with the
 * region card: ambassadors and next EinAb dates). Saving updates the profile, joins the region and sets it as home
 * region, then returns to the journey.
 */
@Component({
  selector: 'fs-profile-region-step-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    InputText,
    PageHeaderComponent,
    PhoneInputComponent,
    ReactiveFormsModule,
    RegionCardComponent,
    RegionPickerComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'onboarding.profile.title' | t"
      [subtitle]="'onboarding.profile.subtitle' | t"
      icon="pi pi-user-edit"
      backLink="/onboarding"
      [backLabel]="'onboarding.backToJourney' | t"
    />
    <fs-data-state [state]="profile" skeleton="form" data-testid="onboarding.profile.state">
      <ng-template [fsData]="profile" let-p>
        <form class="stack" [formGroup]="form" (ngSubmit)="save.run()" data-testid="onboarding.profile.form">
          <section class="fs-card card">
            <h2 class="card__title"><i class="pi pi-user" aria-hidden="true"></i>{{ 'onboarding.profile.aboutYou' | t }}</h2>
            <div class="row">
              <div class="field">
                <label for="ob-first">{{ 'onboarding.profile.firstName' | t }}</label>
                <input pInputText id="ob-first" formControlName="firstName" autocomplete="given-name" data-testid="onboarding.profile.firstName" />
              </div>
              <div class="field">
                <label for="ob-last">{{ 'onboarding.profile.lastName' | t }}</label>
                <input pInputText id="ob-last" formControlName="lastName" autocomplete="family-name" data-testid="onboarding.profile.lastName" />
              </div>
            </div>
            <div class="field">
              <label for="ob-phone">{{ 'onboarding.profile.mobile' | t }}</label>
              <fs-phone-input inputId="ob-phone" formControlName="mobilePhone" [mobile]="true" testId="onboarding.profile.mobile" />
              <small class="muted">{{ 'onboarding.profile.mobileHint' | t }}</small>
            </div>
          </section>

          <section class="fs-card card" data-testid="onboarding.profile.region">
            <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i>{{ 'onboarding.profile.regionTitle' | t }}</h2>
            @if (region(); as r) {
              <p class="current" data-testid="onboarding.profile.selectedRegion" [attr.data-region]="r.id">
                <i class="pi pi-check-circle" aria-hidden="true"></i>{{ r.name }}
                <button type="button" class="link" (click)="region.set(undefined)" data-testid="onboarding.profile.changeRegion">{{ 'onboarding.profile.changeRegion' | t }}</button>
              </p>
              <fs-region-card [regionId]="r.id" testId="onboarding.profile.regionCard" />
            } @else {
              <p class="muted">{{ 'onboarding.profile.regionHint' | t }}</p>
              <fs-region-picker (regionSelected)="region.set($event)" testId="onboarding.profile.picker" />
            }
          </section>

          <div class="actions">
            <p-button type="submit" [label]="'onboarding.profile.save' | t" icon="pi pi-check" [loading]="save.busy()" [disabled]="form.invalid || !region()" data-testid="onboarding.profile.save" />
          </div>
        </form>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../onboarding.scss',
  styles: `
    .row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr));
      gap: 0.75rem;
    }
    .current {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      font-weight: 650;
    }
    .current .pi {
      color: var(--fs-primary-strong);
    }
    .link {
      border: 0;
      background: none;
      color: var(--fs-primary-strong);
      font: inherit;
      font-weight: 600;
      text-decoration: underline;
      cursor: pointer;
    }
  `,
})
export class ProfileRegionStepPageComponent {
  private readonly users = inject(UsersService);
  private readonly regions = inject(RegionsService);
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly region = signal<RegionRef | undefined>(undefined);
  private homeRegionId: string | undefined;

  protected readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
    mobilePhone: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly profile = loadResource<UserProfile>({
    loader: () => this.users.getCurrentUser(),
    isEmpty: () => false,
  });

  protected readonly save = runMutation(
    () => {
      const value = this.form.getRawValue();
      const region = this.region();
      const steps: Observable<unknown>[] = [
        this.users.updateMyProfile({
          userProfileUpdate: {
            firstName: value.firstName.trim(),
            lastName: value.lastName.trim(),
            mobilePhone: value.mobilePhone,
          },
        }),
      ];
      if (region && region.id !== this.homeRegionId) {
        steps.push(
          this.regions.joinRegion({ regionId: region.id }).pipe(
            catchError((error) => (error?.error?.code === 'ALREADY_MEMBER' ? of(null) : throwError(() => error))),
          ),
          this.regions.setHomeRegion({ homeRegionUpdate: { regionId: region.id } }),
        );
      }
      return concat(...steps).pipe(
        last(),
        switchMap(() => this.auth.getSession()),
        tap((info) => this.session.applySession(info)),
      );
    },
    {
      form: this.form,
      successToastKey: 'onboarding.profile.saved',
      onSuccess: () => void this.router.navigateByUrl('/onboarding'),
    },
  );

  constructor() {
    effect(() => {
      const profile = this.profile.data();
      if (!profile) {
        return;
      }
      this.form.patchValue({
        firstName: profile.firstName ?? '',
        lastName: profile.lastName ?? '',
        mobilePhone: profile.mobilePhone ?? '',
      });
      this.homeRegionId = profile.homeRegion?.id;
      if (profile.homeRegion && !this.region()) {
        this.region.set(profile.homeRegion);
      }
    });
  }
}

