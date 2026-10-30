import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';

import { Language, Resource, ResourceCategory, ResourceOpenness, ResourcesService, Upload } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UploadDropzoneComponent } from '../../../shared/upload-dropzone/upload-dropzone.component';
import {
  OPENNESS_VALUES,
  RESOURCE_CATEGORIES_MAX,
  RESOURCE_DESCRIPTION_MAX,
  RESOURCE_IMAGES_MAX,
  RESOURCE_NAME_MAX,
  resourceFormErrors,
  sortCategories,
} from '../resources.util';

/**
 * Add or edit a resource (C6): name, description, 1–5 categories (chips), openness, private (only confirmed buddies
 * see it), images (`fs-upload-dropzone`, max. 5, uploaded right away and attached on save). In a region: "only in
 * this region" (`regionId`) and — for the RESOURCES group (`mayEditCommons`) — "commons of the region" (no owner,
 * the region's members get a bell). Saves itself (`createResource` / `updateResource`) and emits the result.
 */
@Component({
  selector: 'fs-resource-form-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, InputText, Textarea, TPipe, UploadDropzoneComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="(resource() ? 'resources.form.editTitle' : 'resources.form.createTitle') | t"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(40rem, 94vw)' }"
      data-testid="resources.form"
    >
      @if (visible()) {
        <form class="form" (submit)="$event.preventDefault(); submit()" novalidate>
          <div class="field">
            <label for="resource-name">{{ 'resources.form.name' | t }}</label>
            <input
              pInputText
              id="resource-name"
              [attr.maxlength]="nameMax"
              [value]="name()"
              (input)="name.set($any($event.target).value)"
              [attr.aria-invalid]="touched() && errors().includes('nameRequired')"
              data-testid="resources.form.name"
            />
          </div>
          <div class="field">
            <label for="resource-description">{{ 'resources.form.description' | t }}</label>
            <textarea
              pTextarea
              id="resource-description"
              rows="3"
              [attr.maxlength]="descriptionMax"
              [value]="description()"
              (input)="description.set($any($event.target).value)"
              data-testid="resources.form.description"
            ></textarea>
          </div>

          <fieldset class="field">
            <legend>{{ 'resources.form.categories' | t: { max: categoriesMax } }}</legend>
            <fs-data-state [state]="categories" [compact]="true" skeleton="form" data-testid="resources.form.categoriesState">
              <ng-template [fsData]="categories" let-list>
                <div class="chips">
                  @for (category of sorted(list); track category.id) {
                    <button
                      type="button"
                      class="chip"
                      [class.chip--sub]="!!category.parentId"
                      [class.chip--on]="categoryIds().includes(category.id)"
                      [attr.aria-pressed]="categoryIds().includes(category.id)"
                      [disabled]="!categoryIds().includes(category.id) && categoryIds().length >= categoriesMax"
                      [attr.data-category-id]="category.id"
                      (click)="toggleCategory(category.id)"
                      data-testid="resources.form.category"
                    >
                      @if (category.icon) {
                        <i [class]="'pi pi-' + category.icon" aria-hidden="true"></i>
                      }
                      {{ category.name }}
                    </button>
                  }
                </div>
              </ng-template>
            </fs-data-state>
            @if (touched() && errors().includes('categoryRequired')) {
              <small class="error" data-testid="resources.form.categoryRequired">{{ 'resources.form.categoryRequired' | t }}</small>
            }
          </fieldset>

          <fieldset class="field">
            <legend>{{ 'resources.form.openness' | t }}</legend>
            <div class="radios">
              @for (value of opennessValues; track value) {
                <label class="radio">
                  <input type="radio" name="resource-openness" [value]="value" [checked]="openness() === value" (change)="openness.set(value)" [attr.data-value]="value" data-testid="resources.form.openness" />
                  {{ 'resources.openness.' + value | t }}
                </label>
              }
            </div>
          </fieldset>

          <div class="checks">
            @if (!resource() && mayEditCommons()) {
              <label class="radio">
                <input type="checkbox" [checked]="commons()" (change)="commons.set($any($event.target).checked)" data-testid="resources.form.commons" />
                <span>{{ 'resources.form.commons' | t: { region: regionName() ?? '' } }}</span>
              </label>
            }
            @if (!commonsChosen()) {
              <label class="radio">
                <input type="checkbox" [checked]="privateResource()" (change)="privateResource.set($any($event.target).checked)" data-testid="resources.form.private" />
                <span>{{ 'resources.form.private' | t }}</span>
              </label>
              @if (!resource() && regionId()) {
                <label class="radio">
                  <input type="checkbox" [checked]="restrict()" (change)="restrict.set($any($event.target).checked)" data-testid="resources.form.restrict" />
                  <span>{{ 'resources.form.restrict' | t: { region: regionName() ?? '' } }}</span>
                </label>
              }
            }
          </div>

          <div class="field">
            <span class="label">{{ 'resources.form.images' | t }}</span>
            <fs-upload-dropzone [(uploads)]="images" [maxFiles]="imagesMax" accept="image/*" testId="resources.form.images" />
          </div>
        </form>
      }
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
        <p-button
          (onClick)="submit()"
          [label]="(resource() ? 'common.save' : 'resources.form.create') | t"
          icon="pi pi-check"
          [loading]="create.busy() || update.busy()"
          data-testid="resources.form.submit"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .form { display: grid; gap: 1rem; }
    .field { display: grid; gap: 0.4rem; min-width: 0; margin: 0; padding: 0; border: 0; }
    .field label, .field legend, .label { padding: 0; font-weight: 650; font-size: 0.88rem; }
    .field input, .field textarea { width: 100%; }
    .chips { display: flex; flex-wrap: wrap; gap: 0.35rem; }
    .chip {
      display: inline-flex; align-items: center; gap: 0.35rem; min-height: var(--fs-target-sm); padding: 0.25rem 0.75rem;
      border: 1px solid var(--fs-border); border-radius: 999px; background: var(--fs-surface); color: var(--fs-text);
      font: inherit; font-size: 0.85rem; font-weight: 600; cursor: pointer;
    }
    .chip--sub { font-weight: 500; }
    .chip--on { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .chip:disabled { cursor: not-allowed; opacity: 0.6; }
    .chip:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
    .radios, .checks { display: grid; gap: 0.4rem; }
    .radio { display: flex; align-items: center; gap: 0.5rem; min-height: 1.5rem; font-size: 0.92rem; }
    .error { color: var(--fs-danger); font-size: 0.82rem; font-weight: 600; }
  `,
})
export class ResourceFormDialogComponent {
  private readonly api = inject(ResourcesService);
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  /** Resource to edit; null = add a new one. */
  readonly resource = input<Resource | null>(null);
  /** Region the dialog was opened in (offers "only in this region" and commons). */
  readonly regionId = input<string | null>(null);
  readonly regionName = input<string | null>(null);
  readonly mayEditCommons = input(false);
  readonly saved = output<Resource>();

  protected readonly nameMax = RESOURCE_NAME_MAX;
  protected readonly descriptionMax = RESOURCE_DESCRIPTION_MAX;
  protected readonly categoriesMax = RESOURCE_CATEGORIES_MAX;
  protected readonly imagesMax = RESOURCE_IMAGES_MAX;
  protected readonly opennessValues = OPENNESS_VALUES;
  protected readonly sorted = sortCategories;

  protected readonly name = signal('');
  protected readonly description = signal('');
  protected readonly categoryIds = signal<string[]>([]);
  protected readonly openness = signal<ResourceOpenness>(ResourceOpenness.AskFirst);
  protected readonly privateResource = signal(false);
  protected readonly restrict = signal(false);
  protected readonly commons = signal(false);
  protected readonly images = signal<Upload[]>([]);
  protected readonly touched = signal(false);

  protected readonly commonsChosen = computed(() => !this.resource() && this.mayEditCommons() && this.commons());
  protected readonly errors = computed(() =>
    resourceFormErrors({ name: this.name(), description: this.description(), categoryIds: this.categoryIds() }),
  );

  protected readonly categories = loadResource<ResourceCategory[], string>({
    params: () => (this.visible() ? this.i18n.language() : undefined),
    loader: (lang) => this.api.listResourceCategories({ lang: lang as Language }),
  });

  readonly create = runMutation(
    () =>
      this.api.createResource({
        resourceCreate: {
          name: this.name().trim(),
          description: this.description().trim() || undefined,
          categoryIds: this.categoryIds(),
          private: !this.commonsChosen() && this.privateResource(),
          openness: this.openness(),
          commons: this.commonsChosen(),
          regionId: this.commonsChosen() || this.restrict() ? (this.regionId() ?? undefined) : undefined,
          imageUploadIds: this.images().map((image) => image.id),
        },
      }),
    { successToastKey: 'resources.form.created', onSuccess: (resource) => this.done(resource) },
  );

  readonly update = runMutation(
    () =>
      this.api.updateResource({
        resourceId: this.resource()!.id,
        resourceUpdate: {
          name: this.name().trim(),
          description: this.description().trim(),
          categoryIds: this.categoryIds(),
          private: this.privateResource(),
          openness: this.openness(),
          imageUploadIds: this.images().map((image) => image.id),
        },
      }),
    { successToastKey: 'resources.form.updated', onSuccess: (resource) => this.done(resource) },
  );

  constructor() {
    // every opening starts from the resource (or empty)
    effect(() => {
      if (!this.visible()) return;
      const resource = this.resource();
      untracked(() => this.fill(resource));
    });
  }

  protected toggleCategory(id: string): void {
    this.categoryIds.update((ids) => (ids.includes(id) ? ids.filter((other) => other !== id) : [...ids, id]));
  }

  submit(): void {
    this.touched.set(true);
    if (this.errors().length) return;
    void (this.resource() ? this.update.run() : this.create.run());
  }

  private fill(resource: Resource | null): void {
    this.touched.set(false);
    this.name.set(resource?.name ?? '');
    this.description.set(resource?.description ?? '');
    this.categoryIds.set(resource?.categories.map((category) => category.id) ?? []);
    this.openness.set(resource?.openness ?? ResourceOpenness.AskFirst);
    this.privateResource.set(resource?.private ?? false);
    this.restrict.set(false);
    this.commons.set(false);
    this.images.set(
      (resource?.images ?? []).map((image, index) => ({
        id: image.uploadId,
        url: image.url,
        fileName: this.i18n.t('resources.form.imageName', { number: index + 1 }),
        mimeType: 'image/jpeg',
        size: 0,
      })),
    );
  }

  private done(resource: Resource): void {
    this.visible.set(false);
    this.saved.emit(resource);
  }
}
