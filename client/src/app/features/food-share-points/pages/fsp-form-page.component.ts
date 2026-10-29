import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';
import { firstValueFrom, map } from 'rxjs';

import { ApiErrorCode, FoodSharePointsService, Fsp, RegionDetail, RegionsService, Upload, UploadsService } from '../../../api';
import { ApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { ImageCropComponent } from '../../../shared/image-crop/image-crop.component';
import { MapViewComponent } from '../../../shared/map-view/map-view.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import {
  CITY_MAX,
  DESCRIPTION_MAX,
  FspFormValue,
  NAME_MAX,
  POSTCODE_MAX,
  STREET_MAX,
  fspCreateBody,
  fspUpdateBody,
  photoVariant,
} from '../fsp.util';

/** What the form needs: the FSP when editing, else the region it is proposed in. */
interface FormData {
  fsp: Fsp | null;
  region: RegionDetail | null;
}

/** Without `?regionId=` there is no region to propose the FSP in. */
const MISSING_REGION: ApiErrorView = {
  status: 404,
  code: ApiErrorCode.RegionNotFound,
  message: 'regionId missing',
  retryable: false,
  fieldErrors: [],
};

/** Map centre without a pin (Zürich HB). */
const FALLBACK_CENTER: [number, number] = [47.3769, 8.5417];

/**
 * /food-share-points/new?regionId= and /food-share-points/:fspId/edit (FS1): name, address, description, photo
 * (`fs-image-crop` → JPEG → `createUpload`, A41) and an optional pin (without one the server geocodes the address).
 * A region member's new FSP waits for activation by the FSP admins (explained on the form); the creator becomes its
 * first manager. Managers are changed on the detail page.
 */
@Component({
  selector: 'fs-fsp-form-page',
  imports: [...DATA_STATE_IMPORTS, Button, ImageCropComponent, InputText, MapViewComponent, PageHeaderComponent, ReactiveFormsModule, RouterLink, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(fspId() ? 'foodSharePoints.form.editTitle' : 'foodSharePoints.form.createTitle') | t"
      [subtitle]="regionName()"
      icon="pi pi-box"
      [backLink]="backLink()"
      [backLabel]="(fspId() ? 'foodSharePoints.form.backToFsp' : 'foodSharePoints.form.backToRegion') | t"
    />

    <fs-data-state [state]="data" skeleton="form" backLink="/food-share-points/mine" data-testid="foodSharePoints.form.state">
      <ng-template [fsData]="data" let-loaded>
        @if (loaded.fsp && !loaded.fsp.permissions.mayEdit) {
          <section class="fs-card card" data-testid="foodSharePoints.form.notEditable">
            <p class="muted">{{ 'foodSharePoints.form.notEditable' | t }}</p>
            <div><p-button [label]="'foodSharePoints.form.backToFsp' | t" icon="pi pi-arrow-left" severity="secondary" [outlined]="true" [routerLink]="['/food-share-points', loaded.fsp.id]" /></div>
          </section>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate data-testid="foodSharePoints.form">
            @if (!loaded.fsp) {
              <p class="note" data-testid="foodSharePoints.form.activationNote"><i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'foodSharePoints.form.activationNote' | t }}</p>
            }
            <section class="fs-card card">
              <h2 class="card__title"><i class="pi pi-box" aria-hidden="true"></i>{{ 'foodSharePoints.form.basics' | t }}</h2>
              <div class="field">
                <label for="ff-name">{{ 'foodSharePoints.form.name' | t }}</label>
                <input pInputText id="ff-name" formControlName="name" [maxlength]="nameMax" autocomplete="off" aria-describedby="ff-name-hint" [attr.aria-invalid]="invalid('name')" data-testid="foodSharePoints.form.name" />
                <span class="field__hint" id="ff-name-hint">
                  @if (invalid('name')) {
                    <span class="error">{{ 'foodSharePoints.form.required' | t }}</span>
                  } @else {
                    {{ 'foodSharePoints.form.nameHint' | t }}
                  }
                </span>
              </div>
              <div class="field">
                <label for="ff-description">{{ 'foodSharePoints.form.description' | t }}</label>
                <textarea pTextarea id="ff-description" rows="5" formControlName="description" [maxlength]="descriptionMax" aria-describedby="ff-description-hint" data-testid="foodSharePoints.form.description"></textarea>
                <span class="field__hint" id="ff-description-hint">{{ 'foodSharePoints.form.descriptionHint' | t }}</span>
              </div>
            </section>

            <section class="fs-card card" data-testid="foodSharePoints.form.addressSection">
              <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i>{{ 'foodSharePoints.form.address' | t }}</h2>
              <div class="form-grid">
                <div class="field field--wide">
                  <label for="ff-street">{{ 'foodSharePoints.form.street' | t }}</label>
                  <input pInputText id="ff-street" formControlName="street" [maxlength]="streetMax" autocomplete="address-line1" [attr.aria-invalid]="invalid('street')" aria-describedby="ff-street-error" data-testid="foodSharePoints.form.street" />
                  @if (invalid('street')) {
                    <span class="field__hint error" id="ff-street-error">{{ 'foodSharePoints.form.required' | t }}</span>
                  }
                </div>
                <div class="field">
                  <label for="ff-postcode">{{ 'foodSharePoints.form.postcode' | t }}</label>
                  <input pInputText id="ff-postcode" formControlName="postcode" [maxlength]="postcodeMax" autocomplete="postal-code" [attr.aria-invalid]="invalid('postcode')" aria-describedby="ff-postcode-error" data-testid="foodSharePoints.form.postcode" />
                  @if (invalid('postcode')) {
                    <span class="field__hint error" id="ff-postcode-error">{{ 'foodSharePoints.form.required' | t }}</span>
                  }
                </div>
                <div class="field">
                  <label for="ff-city">{{ 'foodSharePoints.form.city' | t }}</label>
                  <input pInputText id="ff-city" formControlName="city" [maxlength]="cityMax" autocomplete="address-level2" [attr.aria-invalid]="invalid('city')" aria-describedby="ff-city-error" data-testid="foodSharePoints.form.city" />
                  @if (invalid('city')) {
                    <span class="field__hint error" id="ff-city-error">{{ 'foodSharePoints.form.required' | t }}</span>
                  }
                </div>
              </div>
              <p class="muted">{{ 'foodSharePoints.form.pinHint' | t }}</p>
              <fs-map-view
                [editable]="true"
                [position]="position()"
                (positionChange)="position.set($event)"
                [center]="position() ?? fallbackCenter"
                [zoom]="position() ? 15 : 12"
                [fitMarkers]="false"
                height="18rem"
                [ariaLabel]="'foodSharePoints.form.mapLabel' | t"
                testId="foodSharePoints.form.map"
              />
              @if (position(); as pos) {
                <div class="pin">
                  <p class="muted" data-testid="foodSharePoints.form.position">{{ 'foodSharePoints.form.positionChosen' | t: { lat: pos[0].toFixed(5), lon: pos[1].toFixed(5) } }}</p>
                  <p-button [label]="'foodSharePoints.form.clearPin' | t" icon="pi pi-times" size="small" severity="secondary" [text]="true" (onClick)="position.set(null)" data-testid="foodSharePoints.form.clearPin" />
                </div>
              }
            </section>

            <section class="fs-card card" data-testid="foodSharePoints.form.photoSection">
              <h2 class="card__title"><i class="pi pi-image" aria-hidden="true"></i>{{ 'foodSharePoints.form.photo' | t }}</h2>
              <p class="muted">{{ 'foodSharePoints.form.photoHint' | t }}</p>
              <div class="photo-row">
                @if (photoPreview(); as src) {
                  <figure class="photo">
                    <img [src]="src" alt="" width="160" height="160" data-testid="foodSharePoints.form.photoPreview" />
                    <figcaption class="muted">{{ (photoUploadId() ? 'foodSharePoints.form.photoReady' : 'foodSharePoints.form.photoCurrent') | t }}</figcaption>
                  </figure>
                }
                <fs-image-crop [outputSize]="1024" testId="foodSharePoints.form.photo" (cropped)="uploadPhoto($event)" />
              </div>
              @if (upload.busy()) {
                <p class="muted" role="status" data-testid="foodSharePoints.form.photoUploading"><i class="pi pi-spin pi-spinner" aria-hidden="true"></i> {{ 'foodSharePoints.form.photoUploading' | t }}</p>
              }
            </section>

            @if (save.error(); as error) {
              <p class="error" role="alert" [attr.data-code]="error.code" data-testid="foodSharePoints.form.error">{{ errorText(error) }}</p>
            }
            <div class="actions">
              <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" [routerLink]="backLink()" data-testid="foodSharePoints.form.cancel" />
              <p-button
                type="submit"
                [label]="(loaded.fsp ? 'foodSharePoints.form.save' : 'foodSharePoints.form.create') | t"
                icon="pi pi-check"
                [loading]="save.busy()"
                [disabled]="upload.busy()"
                data-testid="foodSharePoints.form.submit"
              />
            </div>
          </form>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; width: min(100%, 52rem); margin: 0 auto; }
    .stack { display: grid; gap: 1.25rem; min-width: 0; }
    .card { display: grid; gap: 0.85rem; min-width: 0; }
    .card__title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    .card__title .pi { color: var(--fs-primary-strong); }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .note {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      margin: 0;
      padding: 0.6rem 0.8rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
    }
    .field { display: grid; gap: 0.35rem; min-width: 0; }
    .field > label { font-size: 0.85rem; font-weight: 650; }
    .field__hint { color: var(--fs-text-muted); font-size: 0.82rem; }
    .field--wide { grid-column: 1 / -1; }
    .form-grid { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr)); }
    .error { color: var(--fs-danger); margin: 0; }
    .pin { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; justify-content: space-between; }
    .photo-row { display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-start; }
    .photo { display: grid; gap: 0.3rem; margin: 0; }
    .photo img { width: 10rem; height: 10rem; object-fit: cover; border-radius: var(--fs-radius-md); }
    fs-image-crop { flex: 1 1 18rem; min-width: 0; }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
  `,
})
export class FspFormPageComponent {
  private readonly api = inject(FoodSharePointsService);
  private readonly regions = inject(RegionsService);
  private readonly uploads = inject(UploadsService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly nameMax = NAME_MAX;
  protected readonly streetMax = STREET_MAX;
  protected readonly postcodeMax = POSTCODE_MAX;
  protected readonly cityMax = CITY_MAX;
  protected readonly descriptionMax = DESCRIPTION_MAX;
  protected readonly fallbackCenter = FALLBACK_CENTER;

  protected readonly fspId = toSignal(this.route.paramMap.pipe(map((p) => p.get('fspId'))), { initialValue: null });
  private readonly regionId = toSignal(this.route.queryParamMap.pipe(map((p) => p.get('regionId'))), { initialValue: null });

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(NAME_MAX)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(DESCRIPTION_MAX)] }),
    street: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(STREET_MAX)] }),
    postcode: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(POSTCODE_MAX)] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(CITY_MAX)] }),
  });
  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  protected readonly position = signal<[number, number] | null>(null);
  protected readonly photoUploadId = signal<string | null>(null);
  private readonly localPreview = signal<string | null>(null);
  private readonly submitted = signal(false);
  private readonly patched = signal<string | null>(null);

  /** Edit: the FSP. Create: the region from `?regionId=` (missing → not found). */
  protected readonly data = loadResource<FormData, string>({
    params: () => this.fspId() ?? (this.regionId() ? `new:${this.regionId()}` : 'new:'),
    loader: async (key) => {
      if (!key.startsWith('new:')) {
        return { fsp: await firstValueFrom(this.api.getFoodSharePoint({ fspId: key })), region: null };
      }
      const regionId = key.slice('new:'.length);
      if (!regionId) throw MISSING_REGION;
      return { fsp: null, region: await firstValueFrom(this.regions.getRegion({ regionId })) };
    },
    isEmpty: () => false,
  });

  protected readonly regionName = computed(() => {
    const loaded = this.data.data();
    return loaded?.fsp?.region.name ?? loaded?.region?.name;
  });
  protected readonly backLink = computed(() => {
    const id = this.fspId();
    if (id) return ['/food-share-points', id];
    const regionId = this.regionId();
    return regionId ? ['/regions', regionId, 'food-share-points'] : ['/food-share-points/mine'];
  });
  protected readonly photoPreview = computed(() => this.localPreview() ?? photoVariant(this.data.data()?.fsp?.photoUrl, 'md') ?? null);

  constructor() {
    effect(() => {
      const loaded = this.data.data();
      const key = loaded ? (loaded.fsp?.id ?? 'new') : null;
      if (!loaded || this.patched() === key) return;
      this.patched.set(key);
      if (loaded.fsp) this.patchFrom(loaded.fsp);
    });
    inject(DestroyRef).onDestroy(() => this.revokePreview());
  }

  protected readonly upload = runMutation((file: File) => this.uploads.createUpload({ file }), {
    onSuccess: (upload: Upload) => this.photoUploadId.set(upload.id),
  });

  protected readonly save = runMutation(
    () => {
      const id = this.fspId();
      const value = this.value();
      return id
        ? this.api.updateFoodSharePoint({ fspId: id, fspUpdate: fspUpdateBody(value) })
        : this.api.createFoodSharePoint({ regionId: this.data.data()!.region!.id, fspCreate: fspCreateBody(value) });
    },
    {
      form: this.form,
      errorToast: false,
      successToastKey: this.fspId() ? 'foodSharePoints.toast.saved' : 'foodSharePoints.toast.created',
      onSuccess: (fsp: Fsp) => void this.router.navigate(['/food-share-points', fsp.id]),
    },
  );

  protected invalid(name: 'name' | 'street' | 'postcode' | 'city'): boolean {
    this.formValue();
    this.submitted();
    const control = this.form.controls[name];
    return (control.touched || this.submitted()) && (control.invalid || !control.value.trim());
  }

  protected uploadPhoto(blob: Blob): void {
    this.revokePreview();
    this.localPreview.set(URL.createObjectURL(blob));
    this.photoUploadId.set(null);
    void this.upload.run(new File([blob], 'food-share-point.jpg', { type: 'image/jpeg' }));
  }

  protected submit(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    const blank = (['name', 'street', 'postcode', 'city'] as const).some((name) => !this.form.controls[name].value.trim());
    if (this.form.invalid || blank) return;
    void this.save.run();
  }

  protected errorText(error: ApiErrorView): string {
    return this.i18n.error(error.code, { ...(error.args ?? {}) });
  }

  private value(): FspFormValue {
    return { ...this.form.getRawValue(), position: this.position(), photoUploadId: this.photoUploadId() };
  }

  private patchFrom(fsp: Fsp): void {
    this.form.patchValue({
      name: fsp.name,
      description: fsp.description ?? '',
      street: fsp.address.street,
      postcode: fsp.address.postcode,
      city: fsp.address.city,
    });
    if (fsp.lat != null && fsp.lon != null) this.position.set([fsp.lat, fsp.lon]);
  }

  private revokePreview(): void {
    const url = this.localPreview();
    if (url) URL.revokeObjectURL(url);
  }
}
