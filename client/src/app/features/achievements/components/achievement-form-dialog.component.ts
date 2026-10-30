import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { map, startWith } from 'rxjs';

import { AchievementVisibility, DuplicateMode } from '../../../api';
import { I18nService } from '../../../core/i18n/i18n.service';
import { UiLanguage } from '../../../core/i18n/languages';
import { TPipe } from '../../../core/i18n/t.pipe';
import { LanguageTabsEditorComponent, TranslatedText } from '../../../shared/language-tabs-editor/language-tabs-editor.component';
import { ACHIEVEMENT_ICON_CHOICES, achievementIcon } from '../../profiles/profiles.util';
import {
  AchievementFormStart,
  AchievementFormValue,
  DESCRIPTION_MAX,
  DUPLICATE_MODES,
  NAME_MAX,
  NEW_ACHIEVEMENT,
  VALIDITY_MAX,
  VISIBILITIES,
  translationsMissingName,
} from '../achievements.util';

const GERMAN: readonly UiLanguage[] = ['de'];

const tooLong = (max: number) => (control: AbstractControl<TranslatedText>): ValidationErrors | null =>
  Object.values(control.value ?? {}).some((text) => (text ?? '').trim().length > max) ? { tooLong: max } : null;

/**
 * Define or edit an achievement (AC1): name and description per language (`fs-language-tabs-editor`, German is the
 * base row), icon picker over PrimeIcons, visibility, duplicate mode and validity in days. Emits the form value; the
 * parent saves it and closes the dialog.
 */
@Component({
  selector: 'fs-achievement-form-dialog',
  imports: [Button, Dialog, InputText, LanguageTabsEditorComponent, ReactiveFormsModule, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="(editing() ? 'achievements.form.editTitle' : 'achievements.form.createTitle') | t"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(44rem, 94vw)' }"
      data-testid="achievements.form"
    >
      <!-- re-created per opening, so the language tabs start on German again -->
      @if (visible()) {
      <form class="form" [formGroup]="form" (ngSubmit)="submit()">
        @if (!baseKnown()) {
          <p class="note" data-testid="achievements.form.baseNote">{{ 'achievements.form.baseUnknown' | t }}</p>
        }
        <fs-language-tabs-editor
          formControlName="names"
          [label]="'achievements.form.name' | t"
          [requiredLanguages]="requiredNames()"
          testId="achievements.form.names"
        />
        @if (form.controls.names.hasError('tooLong')) {
          <small class="error">{{ 'achievements.form.tooLong' | t: { max: nameMax } }}</small>
        }
        <fs-language-tabs-editor
          formControlName="descriptions"
          [label]="'achievements.form.description' | t"
          [multiline]="true"
          [rows]="3"
          [requiredLanguages]="none"
          testId="achievements.form.descriptions"
        />
        @if (form.controls.descriptions.hasError('tooLong')) {
          <small class="error">{{ 'achievements.form.tooLong' | t: { max: descriptionMax } }}</small>
        }
        @if (missingNames().length) {
          <small class="error" data-testid="achievements.form.translationNameMissing">
            {{ 'achievements.form.translationNameMissing' | t: { languages: missingNames() } }}
          </small>
        }

        <fieldset class="icons">
          <legend>{{ 'achievements.form.icon' | t }}</legend>
          <div class="icons__grid">
            @for (name of iconChoices(); track name) {
              <label class="icon" [class.icon--active]="selectedIcon() === name" [attr.data-icon]="name" data-testid="achievements.form.icon">
                <input type="radio" class="fs-visually-hidden" formControlName="icon" [value]="name" />
                <i [class]="iconClass(name)" aria-hidden="true"></i>
                <span class="fs-visually-hidden">{{ name }}</span>
              </label>
            }
          </div>
        </fieldset>

        <div class="row">
          <div class="field">
            <label for="achievement-visibility">{{ 'achievements.form.visibility' | t }}</label>
            <p-select
              inputId="achievement-visibility"
              formControlName="visibility"
              [options]="visibilityOptions()"
              optionLabel="label"
              optionValue="value"
              appendTo="body"
              [style]="{ width: '100%' }"
              data-testid="achievements.form.visibility"
            >
              <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
            </p-select>
            <small class="hint" data-testid="achievements.form.visibilityHint">{{ 'achievements.visibilityHint.' + form.controls.visibility.value | t }}</small>
          </div>
          <div class="field">
            <label for="achievement-duplicate">{{ 'achievements.form.duplicate' | t }}</label>
            <p-select
              inputId="achievement-duplicate"
              formControlName="duplicateMode"
              [options]="duplicateOptions()"
              optionLabel="label"
              optionValue="value"
              appendTo="body"
              [style]="{ width: '100%' }"
              data-testid="achievements.form.duplicate"
            >
              <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
            </p-select>
            <small class="hint">{{ 'achievements.duplicateHint.' + form.controls.duplicateMode.value | t }}</small>
          </div>
        </div>
        <div class="field field--narrow">
          <label for="achievement-validity">{{ 'achievements.form.validity' | t }}</label>
          <input
            pInputText
            id="achievement-validity"
            type="number"
            min="1"
            [max]="validityMax"
            inputmode="numeric"
            formControlName="validityDays"
            [attr.aria-invalid]="form.controls.validityDays.invalid"
            data-testid="achievements.form.validity"
          />
          <small class="hint">{{ 'achievements.form.validityHint' | t }}</small>
        </div>
      </form>
      }
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
        <p-button
          (onClick)="submit()"
          [label]="(editing() ? 'common.save' : 'achievements.form.create') | t"
          icon="pi pi-check"
          [loading]="busy()"
          [disabled]="invalid()"
          data-testid="achievements.form.submit"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .form { display: grid; gap: 1rem; }
    .note {
      margin: 0; padding: 0.6rem 0.8rem; border: 1px solid var(--fs-warning-border); border-radius: var(--fs-radius-sm);
      background: var(--fs-warning-soft); color: var(--fs-warning-text); font-size: 0.88rem;
    }
    .field { display: grid; gap: 0.35rem; min-width: 0; }
    .field label, legend { font-weight: 650; font-size: 0.88rem; }
    .field--narrow { max-width: 14rem; }
    .field input { width: 100%; }
    .hint { color: var(--fs-text-muted); font-size: 0.8rem; }
    .error { color: var(--fs-danger); font-size: 0.82rem; font-weight: 600; }
    .row { display: grid; gap: 1rem; }
    @media (min-width: 36rem) { .row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
    .icons { margin: 0; padding: 0; border: 0; min-width: 0; }
    .icons legend { margin-bottom: 0.4rem; padding: 0; }
    .icons__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(2.6rem, 1fr)); gap: 0.35rem; }
    .icon {
      display: grid; place-items: center; height: 2.6rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-sm);
      color: var(--fs-text-muted); font-size: 1.1rem; cursor: pointer;
    }
    .icon:hover { border-color: var(--fs-border-strong); color: var(--fs-text); }
    .icon--active { border: 2px solid var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .icon:has(input:focus-visible) { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
  `,
})
export class AchievementFormDialogComponent {
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  /** Start values; null = define a new achievement. */
  readonly start = input<AchievementFormStart | null>(null);
  readonly busy = input(false);
  readonly save = output<AchievementFormValue>();

  protected readonly nameMax = NAME_MAX;
  protected readonly descriptionMax = DESCRIPTION_MAX;
  protected readonly validityMax = VALIDITY_MAX;
  protected readonly iconClass = achievementIcon;

  protected readonly form = new FormGroup({
    names: new FormControl<TranslatedText>({}, { nonNullable: true, validators: [tooLong(NAME_MAX)] }),
    descriptions: new FormControl<TranslatedText>({}, { nonNullable: true, validators: [tooLong(DESCRIPTION_MAX)] }),
    icon: new FormControl(NEW_ACHIEVEMENT.icon, { nonNullable: true, validators: [Validators.required] }),
    visibility: new FormControl<AchievementVisibility>(NEW_ACHIEVEMENT.visibility, { nonNullable: true }),
    duplicateMode: new FormControl<DuplicateMode>(NEW_ACHIEVEMENT.duplicateMode, { nonNullable: true }),
    validityDays: new FormControl<number | null>(null, [Validators.min(1), Validators.max(VALIDITY_MAX)]),
  });

  /** Value and validity: `form.events` also reports status changes (a child validator that changed its rules). */
  private readonly value = toSignal(
    this.form.events.pipe(
      startWith(null),
      map(() => ({ ...this.form.getRawValue(), valid: this.form.valid })),
    ),
    { requireSync: true },
  );
  private readonly originalIcon = signal<string | null>(null);

  protected readonly editing = computed(() => !!this.start());
  protected readonly baseKnown = computed(() => this.start()?.baseKnown ?? true);
  protected readonly none: readonly UiLanguage[] = [];
  protected readonly requiredNames = computed<readonly UiLanguage[]>(() => (this.baseKnown() ? GERMAN : this.none));
  protected readonly selectedIcon = computed(() => this.value().icon);
  protected readonly missingNames = computed(() => translationsMissingName(this.value()).join(', ').toUpperCase());
  protected readonly invalid = computed(() => !this.value().valid || this.missingNames().length > 0);
  /** A seed icon outside the picker (Lucide names like `handshake`) stays selectable as the first choice. */
  protected readonly iconChoices = computed(() => {
    const current = this.originalIcon();
    return current && !ACHIEVEMENT_ICON_CHOICES.includes(current) ? [current, ...ACHIEVEMENT_ICON_CHOICES] : ACHIEVEMENT_ICON_CHOICES;
  });

  protected readonly visibilityOptions = computed(() => {
    this.i18n.language();
    return VISIBILITIES.map((value) => ({ value, label: this.i18n.t(`achievements.visibility.${value}`) }));
  });
  protected readonly duplicateOptions = computed(() => {
    this.i18n.language();
    return DUPLICATE_MODES.map((value) => ({ value, label: this.i18n.t(`achievements.duplicate.${value}`) }));
  });

  constructor() {
    effect(() => {
      if (!this.visible()) return;
      const value = this.start()?.value ?? NEW_ACHIEVEMENT;
      this.originalIcon.set(this.start() ? value.icon : null);
      this.form.reset({ ...value, names: { ...value.names }, descriptions: { ...value.descriptions } });
    });
  }

  protected submit(): void {
    if (this.invalid()) return;
    this.save.emit(this.form.getRawValue());
  }
}
