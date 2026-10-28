import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { InputText } from '@openng/optimus-ui/inputtext';
import { MultiSelect } from '@openng/optimus-ui/multiselect';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import {
  IntroPickupCategory,
  RegionJoinMode,
  RegionOnboardingStep,
  RegionSettings,
  RegionsService,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  LanguageTabsEditorComponent,
  TranslatedText,
} from '../../../shared/language-tabs-editor/language-tabs-editor.component';
import { RegionPageContext } from '../region-page.context';

/** Zones of the regions foodsharing runs in (others can be typed by ORGA in the region admin). */
export const TIME_ZONES = ['Europe/Zurich', 'Europe/Berlin', 'Europe/Vienna', 'Europe/Luxembourg', 'Europe/Rome', 'Europe/Paris'];

type StepForm = FormGroup<{
  id: FormControl<string | null>;
  key: FormControl<string>;
  titles: FormControl<TranslatedText>;
  descriptions: FormControl<TranslatedText>;
}>;

/**
 * Settings tab (ambassadors/ORGA): intro-pickup booking rules, onboarding (learning pages mandatory, extra steps
 * in five languages), join mode, time zone and conference link. Saved together (`RegionAction.SETTINGS_CHANGED`).
 */
@Component({
  selector: 'fs-region-settings-tab',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    InputNumber,
    InputText,
    LanguageTabsEditorComponent,
    MultiSelect,
    ReactiveFormsModule,
    Select,
    TPipe,
    ToggleSwitch,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './region-settings.tab.html',
  styleUrl: './tab.scss',
  styles: `
    .step {
      display: grid;
      gap: 0.75rem;
      padding: 1rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
    }
    .step__head {
      display: flex;
      align-items: flex-end;
      gap: 0.75rem;
    }
    .step__head .field {
      flex: 1;
    }
    input {
      width: 100%;
    }
  `,
})
export class RegionSettingsTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly api = inject(RegionsService);
  private readonly i18n = inject(I18nService);

  protected readonly timeZones = TIME_ZONES.map((zone) => ({ value: zone, label: zone }));
  protected readonly joinModes = computed(() => {
    this.i18n.language();
    return Object.values(RegionJoinMode).map((value) => ({ value, label: this.i18n.t(`regions.joinMode.${value}`) }));
  });
  protected readonly categories = computed(() => {
    this.i18n.language();
    return Object.values(IntroPickupCategory).map((value) => ({ value, label: this.i18n.t(`common.introCategory.${value}`) }));
  });

  protected readonly form = new FormGroup({
    introPickups: new FormGroup({
      maxActiveBookingsPerUser: new FormControl(3, { nonNullable: true, validators: [Validators.min(1)] }),
      preventDuplicateGuideBookings: new FormControl(true, { nonNullable: true }),
      preventDuplicateCategoryBookings: new FormControl(true, { nonNullable: true }),
      requireFairteilerVisitByThirdIntroPickup: new FormControl(false, { nonNullable: true }),
      ignoredCategories: new FormControl<IntroPickupCategory[]>([], { nonNullable: true }),
    }),
    learningPagesMandatory: new FormControl(true, { nonNullable: true }),
    steps: new FormArray<StepForm>([]),
    joinMode: new FormControl<RegionJoinMode>(RegionJoinMode.Open, { nonNullable: true }),
    timeZone: new FormControl('Europe/Zurich', { nonNullable: true }),
    conferenceUrl: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^(https:\/\/.+)?$/)] }),
  });

  protected readonly settings = loadResource<RegionSettings, string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: (regionId) => this.api.getRegionSettings({ regionId }),
    isEmpty: () => false,
  });

  /** Original intro pickup settings (fields the form does not edit, e.g. the cleaning store, are kept). */
  private original: RegionSettings | null = null;

  constructor() {
    effect(() => {
      const value = this.settings.data();
      if (value) {
        this.original = value;
        this.fill(value);
      }
    });
  }

  private fill(value: RegionSettings): void {
    this.form.controls.steps.clear();
    for (const step of [...value.onboarding.extraSteps].sort((a, b) => a.sortOrder - b.sortOrder)) {
      this.form.controls.steps.push(this.stepForm(step));
    }
    this.form.patchValue({
      introPickups: { ...value.introPickups, ignoredCategories: value.introPickups.ignoredCategories ?? [] },
      learningPagesMandatory: value.onboarding.learningPagesMandatory,
      joinMode: value.joinMode,
      timeZone: value.timeZone,
      conferenceUrl: value.conferenceUrl ?? '',
    });
    this.form.markAsPristine();
  }

  private stepForm(step?: RegionOnboardingStep): StepForm {
    return new FormGroup({
      id: new FormControl<string | null>(step?.id ?? null),
      key: new FormControl(step?.key ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(/^[A-Za-z0-9_-]{2,60}$/)],
      }),
      titles: new FormControl<TranslatedText>(step?.titles ?? {}, { nonNullable: true }),
      descriptions: new FormControl<TranslatedText>(step?.descriptions ?? {}, { nonNullable: true }),
    });
  }

  protected addStep(): void {
    this.form.controls.steps.push(this.stepForm());
    this.form.markAsDirty();
  }

  protected removeStep(index: number): void {
    this.form.controls.steps.removeAt(index);
    this.form.markAsDirty();
  }

  protected moveStep(index: number, delta: -1 | 1): void {
    const steps = this.form.controls.steps;
    const target = index + delta;
    if (target < 0 || target >= steps.length) {
      return;
    }
    const step = steps.at(index);
    steps.removeAt(index);
    steps.insert(target, step);
    this.form.markAsDirty();
  }

  protected readonly save = runMutation(
    () => {
      const value = this.form.getRawValue();
      return this.api.updateRegionSettings({
        regionId: this.ctx.regionId()!,
        regionSettingsPatch: {
          introPickups: { ...this.original!.introPickups, ...value.introPickups },
          onboarding: {
            learningPagesMandatory: value.learningPagesMandatory,
            extraSteps: value.steps.map((step, index) => ({
              id: step.id ?? undefined,
              key: step.key,
              titles: step.titles as Record<string, string>,
              descriptions: step.descriptions as Record<string, string>,
              sortOrder: index + 1,
            })),
          },
          joinMode: value.joinMode,
          timeZone: value.timeZone,
          conferenceUrl: value.conferenceUrl.trim() || undefined,
        },
      });
    },
    {
      form: this.form,
      successToastKey: 'regions.settings.saved',
      onSuccess: (settings) => {
        this.settings.set(settings);
        this.ctx.reload();
      },
    },
  );
}
