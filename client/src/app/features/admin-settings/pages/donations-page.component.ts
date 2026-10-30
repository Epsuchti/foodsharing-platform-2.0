import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { AdminSettingsService, DonationSettings } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { SettingsTabsComponent } from '../components/settings-tabs.component';

/** The URL fields of the donation settings, in form order. */
export const DONATION_URL_FIELDS = [
  'infoUrl',
  'iframeOneTimeUrl',
  'iframeCampaignUrl',
  'iframeFriendshipCircleUrl',
  'iframeSelfServiceUrl',
] as const;
type UrlField = (typeof DONATION_URL_FIELDS)[number];

/** Comma-separated slugs → list (trimmed, no empty entries); the server stores them the same way. */
export function parseSlugs(value: string): string[] {
  return value
    .split(',')
    .map((slug) => slug.trim())
    .filter((slug) => slug.length > 0);
}

/**
 * `/admin/donations` (C7/C14, ORGA): the typed view on the `donation.*` configuration keys — campaign card and
 * modal switches, modal frequency per login state, info and iframe URLs, campaign page slugs. Server field errors
 * (invalid URL) are shown at the field.
 */
@Component({
  selector: 'fs-donations-page',
  imports: [...DATA_STATE_IMPORTS, Button, InputText, PageHeaderComponent, ReactiveFormsModule, SettingsTabsComponent, TPipe, ToggleSwitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminSettings.donations.title' | t" [subtitle]="'adminSettings.donations.subtitle' | t" icon="pi pi-heart" />
    <fs-settings-tabs />
    <fs-data-state [state]="settings" skeleton="form" [skeletonRows]="6" testId="adminSettings.donations.state" data-testid="adminSettings.donations.state">
      <ng-template [fsData]="settings">
        <form class="fs-card form" [formGroup]="form" (ngSubmit)="save.run()" data-testid="adminSettings.donations.form">
          <div class="field">
            <label for="donation-card">{{ 'adminSettings.donations.showCampaignCard' | t }}</label>
            <p-toggleswitch inputId="donation-card" formControlName="showCampaignCard" data-testid="adminSettings.donations.showCampaignCard" />
          </div>
          <div class="field">
            <label for="donation-modal">{{ 'adminSettings.donations.showModal' | t }}</label>
            <p-toggleswitch inputId="donation-modal" formControlName="showModal" data-testid="adminSettings.donations.showModal" />
          </div>
          <div class="field">
            <label for="donation-hours-in">{{ 'adminSettings.donations.modalHoursLoggedIn' | t }}</label>
            <input pInputText id="donation-hours-in" type="number" min="0" inputmode="numeric" formControlName="modalHoursLoggedIn" data-testid="adminSettings.donations.modalHoursLoggedIn" />
          </div>
          <div class="field">
            <label for="donation-hours-out">{{ 'adminSettings.donations.modalHoursLoggedOut' | t }}</label>
            <input pInputText id="donation-hours-out" type="number" min="0" inputmode="numeric" formControlName="modalHoursLoggedOut" data-testid="adminSettings.donations.modalHoursLoggedOut" />
          </div>
          @for (field of urlFields; track field) {
            <div class="field">
              <label [for]="'donation-' + field">{{ 'adminSettings.donations.' + field | t }}</label>
              <input
                pInputText
                type="url"
                [id]="'donation-' + field"
                [formControlName]="field"
                placeholder="https://"
                [attr.aria-invalid]="!!form.controls[field].errors?.['server']"
                [attr.data-testid]="'adminSettings.donations.' + field"
              />
              @if (form.controls[field].errors?.['server']; as code) {
                <p class="field-error" role="alert" data-testid="adminSettings.donations.fieldError">{{ 'errors.' + code | t }}</p>
              }
            </div>
          }
          <div class="field">
            <label for="donation-slugs">{{ 'adminSettings.donations.campaignPageSlugs' | t }}</label>
            <input pInputText id="donation-slugs" formControlName="campaignPageSlugs" aria-describedby="donation-slugs-hint" data-testid="adminSettings.donations.campaignPageSlugs" />
            <span class="muted" id="donation-slugs-hint">{{ 'adminSettings.donations.slugsHint' | t }}</span>
          </div>
          <div class="row__actions">
            <p-button type="submit" [label]="'common.save' | t" icon="pi pi-check" [loading]="save.busy()" [disabled]="form.invalid" data-testid="adminSettings.donations.save" />
          </div>
        </form>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../admin-settings.scss',
})
export class DonationsPageComponent {
  private readonly api = inject(AdminSettingsService);

  protected readonly urlFields = DONATION_URL_FIELDS;

  protected readonly form = new FormGroup({
    showCampaignCard: new FormControl(false, { nonNullable: true }),
    showModal: new FormControl(false, { nonNullable: true }),
    modalHoursLoggedIn: new FormControl<number>(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] }),
    modalHoursLoggedOut: new FormControl<number>(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] }),
    infoUrl: new FormControl('', { nonNullable: true }),
    iframeOneTimeUrl: new FormControl('', { nonNullable: true }),
    iframeCampaignUrl: new FormControl('', { nonNullable: true }),
    iframeFriendshipCircleUrl: new FormControl('', { nonNullable: true }),
    iframeSelfServiceUrl: new FormControl('', { nonNullable: true }),
    campaignPageSlugs: new FormControl('', { nonNullable: true }),
  });

  protected readonly settings = loadResource<DonationSettings>({
    loader: () => this.api.getDonationSettings(),
  });

  protected readonly save = runMutation(
    () => {
      const value = this.form.getRawValue();
      const urls = Object.fromEntries(DONATION_URL_FIELDS.map((field) => [field, value[field].trim()])) as Record<UrlField, string>;
      return this.api.updateDonationSettings({
        donationSettings: {
          showCampaignCard: value.showCampaignCard,
          showModal: value.showModal,
          modalHoursLoggedIn: Number(value.modalHoursLoggedIn),
          modalHoursLoggedOut: Number(value.modalHoursLoggedOut),
          ...urls,
          campaignPageSlugs: parseSlugs(value.campaignPageSlugs),
        },
      });
    },
    { form: this.form, successToastKey: 'adminSettings.donations.saved', onSuccess: (saved) => this.settings.set(saved) },
  );

  constructor() {
    effect(() => {
      const settings = this.settings.data();
      if (settings) {
        this.form.reset({
          showCampaignCard: settings.showCampaignCard,
          showModal: settings.showModal,
          modalHoursLoggedIn: settings.modalHoursLoggedIn,
          modalHoursLoggedOut: settings.modalHoursLoggedOut,
          infoUrl: settings.infoUrl ?? '',
          iframeOneTimeUrl: settings.iframeOneTimeUrl ?? '',
          iframeCampaignUrl: settings.iframeCampaignUrl ?? '',
          iframeFriendshipCircleUrl: settings.iframeFriendshipCircleUrl ?? '',
          iframeSelfServiceUrl: settings.iframeSelfServiceUrl ?? '',
          campaignPageSlugs: (settings.campaignPageSlugs ?? []).join(', '),
        });
      }
    });
  }
}
