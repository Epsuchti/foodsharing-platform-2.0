import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { map } from 'rxjs';

import { RegionAdminItem, RegionJoinMode, UnitType } from '../../../api';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';

/** Form value; `reason` is required for edits (history), optional when creating. */
export interface RegionFormValue {
  name: string;
  slug?: string;
  type: UnitType;
  emailName?: string;
  teaser?: string;
  description?: string;
  timeZone: string;
  country?: string;
  joinMode: RegionJoinMode;
  moderated: boolean;
  /** Working groups only: shown on the public team page (C16, ORGA). */
  teamPage?: boolean;
  reason?: string;
}

const ZONES = ['Europe/Zurich', 'Europe/Berlin', 'Europe/Vienna', 'Europe/Luxembourg', 'Europe/Rome', 'Europe/Paris'];

/** Create / edit a region or working group (ORGA, R7). */
@Component({
  selector: 'fs-region-form-dialog',
  imports: [Button, Dialog, InputText, ReactiveFormsModule, Select, TPipe, Textarea, ToggleSwitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="(region() ? 'regionAdmin.form.editTitle' : 'regionAdmin.form.createTitle') | t: { parent: parentName() ?? '' }"
      [style]="{ width: 'min(44rem, 94vw)' }"
      data-testid="regionAdmin.form"
    >
      <form class="form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="grid">
          <div class="field">
            <label for="ra-name">{{ 'regionAdmin.form.name' | t }}</label>
            <input pInputText id="ra-name" formControlName="name" maxlength="120" data-testid="regionAdmin.form.name" />
          </div>
          <div class="field">
            <label for="ra-slug">{{ 'regionAdmin.form.slug' | t }}</label>
            <input pInputText id="ra-slug" formControlName="slug" maxlength="120" [placeholder]="'regionAdmin.form.slugAuto' | t" data-testid="regionAdmin.form.slug" />
          </div>
          <div class="field">
            <label for="ra-type">{{ 'regionAdmin.form.type' | t }}</label>
            <p-select inputId="ra-type" formControlName="type" [options]="types()" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="regionAdmin.form.type" />
          </div>
          <div class="field">
            <label for="ra-email">{{ 'regionAdmin.form.emailName' | t }}</label>
            <input pInputText id="ra-email" formControlName="emailName" maxlength="120" placeholder="zuerich" />
          </div>
          <div class="field">
            <label for="ra-zone">{{ 'regionAdmin.form.timeZone' | t }}</label>
            <p-select inputId="ra-zone" formControlName="timeZone" [options]="zones" [editable]="true" appendTo="body" [style]="{ width: '100%' }" />
          </div>
          <div class="field">
            <label for="ra-country">{{ 'regionAdmin.form.country' | t }}</label>
            <input pInputText id="ra-country" formControlName="country" maxlength="2" placeholder="CH" />
          </div>
          <div class="field">
            <label for="ra-join">{{ 'regionAdmin.form.joinMode' | t }}</label>
            <p-select inputId="ra-join" formControlName="joinMode" [options]="joinModes()" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" />
          </div>
          <div class="switch">
            <label for="ra-moderated">{{ 'regionAdmin.form.moderated' | t }}</label>
            <p-toggleswitch inputId="ra-moderated" formControlName="moderated" />
          </div>
          @if (isWorkingGroup()) {
            <div class="switch">
              <label for="ra-team-page">{{ 'regionAdmin.form.teamPage' | t }}</label>
              <p-toggleswitch inputId="ra-team-page" formControlName="teamPage" data-testid="regionAdmin.form.teamPage" />
            </div>
          }
        </div>
        <div class="field">
          <label for="ra-teaser">{{ 'regionAdmin.form.teaser' | t }}</label>
          <input pInputText id="ra-teaser" formControlName="teaser" maxlength="500" />
        </div>
        <div class="field">
          <label for="ra-description">{{ 'regionAdmin.form.description' | t }}</label>
          <textarea pTextarea id="ra-description" rows="4" formControlName="description"></textarea>
        </div>
        <div class="field">
          <label for="ra-reason">{{ (region() ? 'regionAdmin.form.reasonRequired' : 'regionAdmin.form.reason') | t }}</label>
          <input pInputText id="ra-reason" formControlName="reason" maxlength="500" data-testid="regionAdmin.form.reason" />
        </div>
        <div class="actions">
          <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
          <p-button type="submit" [label]="'common.save' | t" icon="pi pi-check" [disabled]="form.invalid" [loading]="busy()" data-testid="regionAdmin.form.save" />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    .form {
      display: grid;
      gap: 1rem;
    }
    .grid {
      display: grid;
      gap: 1rem;
      @media (min-width: 40rem) {
        grid-template-columns: 1fr 1fr;
      }
    }
    .field {
      display: grid;
      gap: 0.35rem;
    }
    .field label,
    .switch label {
      font-size: 0.85rem;
      font-weight: 650;
    }
    .field input,
    .field textarea {
      width: 100%;
    }
    .switch {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
    }
  `,
})
export class RegionFormDialogComponent {
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  /** null: create a new child of `parentName`. */
  readonly region = input<RegionAdminItem | null>(null);
  readonly parentName = input<string | null>(null);
  readonly busy = input(false);
  readonly save = output<RegionFormValue>();

  protected readonly zones = ZONES;
  protected readonly types = computed(() => {
    this.i18n.language();
    return Object.values(UnitType).map((value) => ({ value, label: this.i18n.t(`regionAdmin.type.${value}`) }));
  });
  protected readonly joinModes = computed(() => {
    this.i18n.language();
    return Object.values(RegionJoinMode).map((value) => ({
      value,
      label: this.i18n.t(`regionAdmin.joinMode.${value}`),
    }));
  });

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    slug: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^([a-z0-9]+(-[a-z0-9]+)*)?$/)] }),
    type: new FormControl<UnitType>(UnitType.City, { nonNullable: true }),
    emailName: new FormControl('', { nonNullable: true }),
    teaser: new FormControl('', { nonNullable: true }),
    description: new FormControl('', { nonNullable: true }),
    timeZone: new FormControl('Europe/Zurich', { nonNullable: true, validators: [Validators.required] }),
    country: new FormControl('', { nonNullable: true }),
    joinMode: new FormControl<RegionJoinMode>(RegionJoinMode.Open, { nonNullable: true }),
    moderated: new FormControl(false, { nonNullable: true }),
    teamPage: new FormControl(false, { nonNullable: true }),
    reason: new FormControl('', { nonNullable: true }),
  });

  /** The team-page switch only applies to working groups (C16). */
  protected readonly isWorkingGroup = toSignal(
    this.form.controls.type.valueChanges.pipe(map((type) => type === UnitType.WorkingGroup)),
    { initialValue: false },
  );

  constructor() {
    effect(() => {
      if (!this.visible()) {
        return;
      }
      const item = this.region();
      this.form.reset({
        name: item?.region.name ?? '',
        slug: item?.region.slug ?? '',
        type: item?.region.type ?? UnitType.City,
        emailName: item?.emailName ?? '',
        teaser: item?.teaser ?? '',
        description: item?.description ?? '',
        timeZone: item?.timeZone ?? 'Europe/Zurich',
        country: item?.country ?? '',
        joinMode: item?.joinMode ?? RegionJoinMode.Open,
        moderated: item?.moderated ?? false,
        teamPage: item?.teamPage ?? false,
        reason: '',
      });
      this.form.controls.reason.setValidators(item ? [Validators.required, Validators.minLength(3)] : []);
      this.form.controls.reason.updateValueAndValidity();
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    const blank = (text: string) => text.trim() || undefined;
    this.save.emit({
      name: value.name.trim(),
      slug: blank(value.slug),
      type: value.type,
      emailName: blank(value.emailName),
      teaser: blank(value.teaser),
      description: blank(value.description),
      timeZone: value.timeZone,
      country: blank(value.country)?.toUpperCase(),
      joinMode: value.joinMode,
      moderated: value.moderated,
      teamPage: value.type === UnitType.WorkingGroup ? value.teamPage : undefined,
      reason: blank(value.reason),
    });
  }
}
