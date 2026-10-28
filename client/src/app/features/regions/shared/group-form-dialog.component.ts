import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';

import {
  ApplyType,
  GroupCategory,
  WorkingGroupCreate,
  WorkingGroupDetail,
  WorkingGroupFunction,
} from '../../../api';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';

/** Values of the form; `function` only when creating (it cannot change afterwards). */
export type GroupFormValue = WorkingGroupCreate;

/** Restricted functions (only ORGA assigns them; server `WorkingGroupFunction.isRestricted`). */
export const RESTRICTED_FUNCTIONS: readonly WorkingGroupFunction[] = [
  WorkingGroupFunction.Report,
  WorkingGroupFunction.Arbitration,
  WorkingGroupFunction.Fsmanagement,
  WorkingGroupFunction.Board,
];

/**
 * Create or edit a working group (R9): name, teaser, description, category, who may join (apply type),
 * requirements for applications and, when creating, an optional function (restricted ones only for ORGA).
 */
@Component({
  selector: 'fs-group-form-dialog',
  imports: [Button, Dialog, InputNumber, InputText, ReactiveFormsModule, Select, TPipe, Textarea],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="(group() ? 'regions.groupForm.editTitle' : 'regions.groupForm.createTitle') | t"
      [style]="{ width: 'min(40rem, 94vw)' }"
      data-testid="regions.groupForm"
    >
      <form class="form" [formGroup]="form" (ngSubmit)="submit()" id="group-form">
        <div class="field">
          <label for="group-name">{{ 'regions.groupForm.name' | t }}</label>
          <input pInputText id="group-name" formControlName="name" maxlength="200" data-testid="regions.groupForm.name" />
        </div>
        <div class="field">
          <label for="group-teaser">{{ 'regions.groupForm.teaser' | t }}</label>
          <input pInputText id="group-teaser" formControlName="teaser" maxlength="2000" data-testid="regions.groupForm.teaser" />
        </div>
        <div class="field">
          <label for="group-description">{{ 'regions.groupForm.description' | t }}</label>
          <textarea pTextarea id="group-description" rows="4" formControlName="description" data-testid="regions.groupForm.description"></textarea>
          <small class="hint">{{ 'regions.groupForm.markdownHint' | t }}</small>
        </div>
        <div class="row">
          <div class="field">
            <label for="group-category">{{ 'regions.groupForm.category' | t }}</label>
            <p-select inputId="group-category" formControlName="category" [options]="categoryOptions()" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="regions.groupForm.category" />
          </div>
          <div class="field">
            <label for="group-apply">{{ 'regions.groupForm.applyType' | t }}</label>
            <p-select inputId="group-apply" formControlName="applyType" [options]="applyOptions()" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="regions.groupForm.applyType" />
          </div>
        </div>
        @if (!group()) {
          <div class="field">
            <label for="group-function">{{ 'regions.groupForm.function' | t }}</label>
            <p-select inputId="group-function" formControlName="function" [options]="functionOptions()" optionLabel="label" optionValue="value" [showClear]="true" [placeholder]="'regions.groupForm.noFunction' | t" appendTo="body" [style]="{ width: '100%' }" data-testid="regions.groupForm.function" />
            <small class="hint">{{ 'regions.groupForm.functionHint' | t }}</small>
          </div>
        }
        @if (form.controls.applyType.value === 'EVERYBODY') {
          <fieldset class="requirements" formGroupName="requirements">
            <legend>{{ 'regions.groupForm.requirements' | t }}</legend>
            <div class="row row--3">
              <div class="field">
                <label for="req-pickups">{{ 'regions.groupForm.minPickups' | t }}</label>
                <p-inputnumber inputId="req-pickups" formControlName="minPickups" [min]="0" [showButtons]="true" />
              </div>
              <div class="field">
                <label for="req-bananas">{{ 'regions.groupForm.minBananas' | t }}</label>
                <p-inputnumber inputId="req-bananas" formControlName="minBananas" [min]="0" [showButtons]="true" />
              </div>
              <div class="field">
                <label for="req-weeks">{{ 'regions.groupForm.minWeeks' | t }}</label>
                <p-inputnumber inputId="req-weeks" formControlName="minWeeks" [min]="0" [showButtons]="true" />
              </div>
            </div>
          </fieldset>
        }
      </form>
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
        <p-button
          (onClick)="submit()"
          [label]="(group() ? 'common.save' : 'regions.groupForm.create') | t"
          [loading]="busy()"
          [disabled]="form.invalid"
          data-testid="regions.groupForm.submit"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .form {
      display: grid;
      gap: 1rem;
    }
    .field {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }
    .field label {
      font-weight: 650;
      font-size: 0.88rem;
    }
    .field input,
    .field textarea {
      width: 100%;
    }
    .hint {
      color: var(--fs-text-muted);
      font-size: 0.8rem;
    }
    .row {
      display: grid;
      gap: 1rem;
      @media (min-width: 36rem) {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    .row--3 {
      @media (min-width: 36rem) {
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }
    }
    .requirements {
      margin: 0;
      padding: 0.75rem 1rem 1rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
    }
    .requirements legend {
      padding: 0 0.35rem;
      font-weight: 650;
    }
  `,
})
export class GroupFormDialogComponent {
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  /** The group to edit; empty = create. */
  readonly group = input<WorkingGroupDetail | null>(null);
  readonly allowRestricted = input(false);
  readonly busy = input(false);
  readonly save = output<GroupFormValue>();

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    teaser: new FormControl('', { nonNullable: true }),
    description: new FormControl('', { nonNullable: true }),
    category: new FormControl<GroupCategory>(GroupCategory.Project, { nonNullable: true }),
    applyType: new FormControl<ApplyType>(ApplyType.Everybody, { nonNullable: true }),
    function: new FormControl<WorkingGroupFunction | null>(null),
    requirements: new FormGroup({
      minPickups: new FormControl(0, { nonNullable: true }),
      minBananas: new FormControl(0, { nonNullable: true }),
      minWeeks: new FormControl(0, { nonNullable: true }),
    }),
  });

  protected readonly categoryOptions = computed(() => {
    this.i18n.language();
    return Object.values(GroupCategory)
      .filter((value) => value !== GroupCategory.Archived)
      .map((value) => ({ value, label: this.i18n.t(`regions.category.${value}`) }));
  });
  protected readonly applyOptions = computed(() => {
    this.i18n.language();
    return Object.values(ApplyType).map((value) => ({ value, label: this.i18n.t(`regions.applyType.${value}`) }));
  });
  protected readonly functionOptions = computed(() => {
    this.i18n.language();
    return Object.values(WorkingGroupFunction)
      .filter((value) => this.allowRestricted() || !RESTRICTED_FUNCTIONS.includes(value))
      .map((value) => ({ value, label: this.i18n.t(`regions.function.${value}`) }));
  });

  constructor() {
    effect(() => {
      if (!this.visible()) {
        return;
      }
      const group = this.group();
      this.form.reset({
        name: group?.name ?? '',
        teaser: group?.teaser ?? '',
        description: group?.description ?? '',
        category: group?.category && group.category !== GroupCategory.Archived ? group.category : GroupCategory.Project,
        applyType: group?.applyType ?? ApplyType.Everybody,
        function: null,
        requirements: {
          minPickups: group?.requirements.minPickups ?? 0,
          minBananas: group?.requirements.minBananas ?? 0,
          minWeeks: group?.requirements.minWeeks ?? 0,
        },
      });
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.save.emit({
      name: value.name.trim(),
      teaser: value.teaser.trim() || undefined,
      description: value.description.trim() || undefined,
      category: value.category,
      applyType: value.applyType,
      requirements: value.requirements,
      function: value.function ?? undefined,
    });
  }
}
