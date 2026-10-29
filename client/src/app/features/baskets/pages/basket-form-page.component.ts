import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { MultiSelect } from '@openng/optimus-ui/multiselect';
import { Textarea } from '@openng/optimus-ui/textarea';
import { firstValueFrom, map } from 'rxjs';

import {
  Basket,
  BasketKind,
  BasketLocationType,
  BasketsService,
  Grocery,
  Role,
  StoresService,
  Upload,
  UploadsService,
  UserProfile,
  UsersService,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { ApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { ImageCropComponent } from '../../../shared/image-crop/image-crop.component';
import { MapViewComponent } from '../../../shared/map-view/map-view.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PhoneInputComponent } from '../../../shared/phone/phone-input.component';
import {
  BASKET_KINDS,
  BasketFormValue,
  DEFAULT_LIFETIME_DAYS,
  DESCRIPTION_MAX,
  MAX_LIFETIME_DAYS,
  MIN_LIFETIME_DAYS,
  basketCreateBody,
  basketFormProblems,
  basketUpdateBody,
  photoVariant,
  profilePosition,
} from '../baskets.util';

interface FormData {
  me: UserProfile;
  basket: Basket | null;
  groceries: Grocery[];
}

/** Map centre when neither the basket nor the profile has coordinates (Zürich HB). */
const FALLBACK_CENTER: [number, number] = [47.3769, 8.5417];

/**
 * /baskets/new and /baskets/:basketId/edit (B1): description, weight, kinds, groceries (store metadata, foodsavers
 * and up), photo (`fs-image-crop` → JPEG ≤ 1 MB → `createUpload`, A41), location HOME (profile coordinates,
 * rounded for others) or CUSTOM (pin on an editable map), contact types with numbers, lifetime 1..21 days (create
 * only). Missing location/contact are explained before sending; the server answers the same codes.
 */
@Component({
  selector: 'fs-basket-form-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    ImageCropComponent,
    InputText,
    MapViewComponent,
    MultiSelect,
    PageHeaderComponent,
    PhoneInputComponent,
    ReactiveFormsModule,
    RouterLink,
    Textarea,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(basketId() ? 'baskets.form.editTitle' : 'baskets.form.createTitle') | t"
      icon="pi pi-shopping-cart"
      [backLink]="backLink()"
      [backLabel]="(basketId() ? 'baskets.detail.title' : 'baskets.nearby.title') | t"
    />

    <fs-data-state [state]="data" skeleton="form" backLink="/baskets" data-testid="baskets.form.state">
      <ng-template [fsData]="data" let-loaded>
        @if (loaded.basket && !loaded.basket.permissions.mayEdit) {
          <section class="fs-card card" data-testid="baskets.form.notEditable">
            <p class="muted">{{ 'baskets.form.notEditable' | t }}</p>
            <div><p-button [label]="'baskets.detail.title' | t" icon="pi pi-arrow-left" severity="secondary" [outlined]="true" [routerLink]="['/baskets', loaded.basket.id]" /></div>
          </section>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate data-testid="baskets.form">
            <section class="fs-card card">
              <h2 class="card__title"><i class="pi pi-shopping-cart" aria-hidden="true"></i>{{ 'baskets.form.basics' | t }}</h2>
              <div class="field">
                <label for="bf-description">{{ 'baskets.form.description' | t }}</label>
                <textarea
                  pTextarea
                  id="bf-description"
                  rows="4"
                  formControlName="description"
                  [maxlength]="descriptionMax"
                  aria-describedby="bf-description-hint"
                  [attr.aria-invalid]="descriptionInvalid()"
                  data-testid="baskets.form.description"
                ></textarea>
                <span class="field__hint" id="bf-description-hint">
                  @if (descriptionInvalid()) {
                    <span class="error">{{ 'baskets.form.descriptionRequired' | t }}</span>
                  } @else {
                    {{ 'baskets.form.descriptionHint' | t }}
                  }
                  {{ 'baskets.form.count' | t: { count: form.controls.description.value.length, max: descriptionMax } }}
                </span>
              </div>
              <fieldset class="field">
                <legend class="field__label">{{ 'baskets.form.kinds' | t }}</legend>
                <div class="kinds">
                  @for (kind of kinds; track kind) {
                    <label class="kind" [class.kind--on]="hasKind(kind)">
                      <input type="checkbox" [checked]="hasKind(kind)" (change)="toggleKind(kind)" [attr.data-testid]="'baskets.form.kind.' + kind" />
                      {{ 'baskets.kind.' + kind | t }}
                    </label>
                  }
                </div>
              </fieldset>
              <div class="form-grid">
                <div class="field">
                  <label for="bf-weight">{{ 'baskets.form.weight' | t }}</label>
                  <input pInputText id="bf-weight" type="number" min="0" max="100" step="0.1" inputmode="decimal" formControlName="weightKg" data-testid="baskets.form.weight" />
                </div>
                @if (!loaded.basket) {
                  <div class="field">
                    <label for="bf-lifetime">{{ 'baskets.form.lifetime' | t }}</label>
                    <input
                      pInputText
                      id="bf-lifetime"
                      type="number"
                      [min]="minLifetime"
                      [max]="maxLifetime"
                      step="1"
                      formControlName="lifetimeDays"
                      aria-describedby="bf-lifetime-hint"
                      [attr.aria-invalid]="form.controls.lifetimeDays.invalid"
                      data-testid="baskets.form.lifetime"
                    />
                    <span class="field__hint" id="bf-lifetime-hint" [class.error]="form.controls.lifetimeDays.invalid">
                      {{ (form.controls.lifetimeDays.invalid ? 'baskets.form.lifetimeInvalid' : 'baskets.form.lifetimeHint') | t: { min: minLifetime, max: maxLifetime } }}
                    </span>
                  </div>
                }
                @if (loaded.groceries.length) {
                  <div class="field field--wide">
                    <label for="bf-groceries">{{ 'baskets.form.groceries' | t }}</label>
                    <p-multiselect inputId="bf-groceries" formControlName="groceryIds" [options]="loaded.groceries" optionLabel="name" optionValue="id" [filter]="true" appendTo="body" [style]="{ width: '100%' }" data-testid="baskets.form.groceries" />
                  </div>
                }
              </div>
            </section>

            <section class="fs-card card" data-testid="baskets.form.photoSection">
              <h2 class="card__title"><i class="pi pi-image" aria-hidden="true"></i>{{ 'baskets.form.photo' | t }}</h2>
              <p class="muted">{{ 'baskets.form.photoHint' | t }}</p>
              <div class="photo-row">
                @if (photoPreview(); as src) {
                  <figure class="photo">
                    <img [src]="src" alt="" width="160" height="160" data-testid="baskets.form.photoPreview" />
                    <figcaption class="muted">{{ (photoUploadId() ? 'baskets.form.photoReady' : 'baskets.form.photoCurrent') | t }}</figcaption>
                  </figure>
                }
                <fs-image-crop [outputSize]="1024" testId="baskets.form.photo" (cropped)="uploadPhoto($event)" />
              </div>
              @if (upload.busy()) {
                <p class="muted" role="status" data-testid="baskets.form.photoUploading"><i class="pi pi-spin pi-spinner" aria-hidden="true"></i> {{ 'baskets.form.photoUploading' | t }}</p>
              }
            </section>

            <section class="fs-card card" data-testid="baskets.form.locationSection">
              <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i>{{ 'baskets.form.location' | t }}</h2>
              <fieldset class="field">
                <legend class="fs-visually-hidden">{{ 'baskets.form.location' | t }}</legend>
                <label class="radio">
                  <input type="radio" formControlName="locationType" [value]="home" data-testid="baskets.form.locationHome" />
                  <span>
                    {{ 'baskets.form.locationHome' | t }}
                    <span class="field__hint block">
                      @if (homePosition()) {
                        {{ 'baskets.form.locationHomeHint' | t }}
                      } @else {
                        <span class="error" data-testid="baskets.form.locationHomeMissing">{{ 'baskets.form.locationHomeMissing' | t }}</span>
                        <a routerLink="/account/profile">{{ 'baskets.nearby.editAddress' | t }}</a>
                      }
                    </span>
                  </span>
                </label>
                <label class="radio">
                  <input type="radio" formControlName="locationType" [value]="custom" data-testid="baskets.form.locationCustom" />
                  <span>{{ 'baskets.form.locationCustom' | t }}</span>
                </label>
              </fieldset>
              @if (form.controls.locationType.value === custom) {
                <p class="muted">{{ 'baskets.form.locationCustomHint' | t }}</p>
                <fs-map-view
                  [editable]="true"
                  [position]="position()"
                  (positionChange)="position.set($event)"
                  [center]="position() ?? homePosition() ?? fallbackCenter"
                  [zoom]="14"
                  [fitMarkers]="false"
                  height="20rem"
                  [ariaLabel]="'baskets.form.mapLabel' | t"
                  testId="baskets.form.map"
                />
                @if (position(); as pos) {
                  <p class="muted" data-testid="baskets.form.position">{{ 'baskets.form.positionChosen' | t: { lat: pos[0].toFixed(5), lon: pos[1].toFixed(5) } }}</p>
                }
              }
            </section>

            <section class="fs-card card" data-testid="baskets.form.contactSection">
              <h2 class="card__title"><i class="pi pi-comments" aria-hidden="true"></i>{{ 'baskets.form.contact' | t }}</h2>
              <div class="checks">
                <label class="radio"><input type="checkbox" formControlName="contactMessage" data-testid="baskets.form.contactMessage" /> {{ 'baskets.form.contactMessage' | t }}</label>
                <label class="radio"><input type="checkbox" formControlName="contactPhone" data-testid="baskets.form.contactPhone" /> {{ 'baskets.form.contactPhone' | t }}</label>
                <label class="radio"><input type="checkbox" formControlName="contactMobile" data-testid="baskets.form.contactMobile" /> {{ 'baskets.form.contactMobile' | t }}</label>
              </div>
              <div class="form-grid">
                @if (form.controls.contactPhone.value) {
                  <div class="field">
                    <label for="bf-phone">{{ 'baskets.form.phone' | t }}</label>
                    <fs-phone-input formControlName="phone" inputId="bf-phone" testId="baskets.form.phone" />
                  </div>
                }
                @if (form.controls.contactMobile.value) {
                  <div class="field">
                    <label for="bf-mobile">{{ 'baskets.form.mobile' | t }}</label>
                    <fs-phone-input formControlName="mobile" inputId="bf-mobile" [mobile]="true" testId="baskets.form.mobile" />
                  </div>
                }
              </div>
            </section>

            @if (shownProblems().length) {
              <ul class="problems" role="alert" data-testid="baskets.form.problems">
                @for (code of shownProblems(); track code) {
                  <li [attr.data-code]="code">{{ 'errors.' + code | t }}</li>
                }
              </ul>
            }
            @if (save.error(); as error) {
              <p class="error" role="alert" [attr.data-code]="error.code" data-testid="baskets.form.error">{{ errorText(error) }}</p>
            }
            <div class="actions">
              <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" [routerLink]="backLink()" data-testid="baskets.form.cancel" />
              <p-button
                type="submit"
                [label]="(loaded.basket ? 'baskets.form.save' : 'baskets.form.create') | t"
                icon="pi pi-check"
                [loading]="save.busy()"
                [disabled]="upload.busy()"
                data-testid="baskets.form.submit"
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
    .field { display: grid; gap: 0.35rem; min-width: 0; margin: 0; padding: 0; border: 0; }
    .field > label, .field__label { padding: 0; font-size: 0.85rem; font-weight: 650; }
    .field__hint { color: var(--fs-text-muted); font-size: 0.82rem; }
    .field__hint.block { display: block; }
    .field--wide { grid-column: 1 / -1; }
    .form-grid { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr)); }
    .error { color: var(--fs-danger); margin: 0; }
    .kinds { display: flex; flex-wrap: wrap; gap: 0.45rem; }
    .kind {
      display: inline-flex;
      gap: 0.4rem;
      align-items: center;
      padding: 0.3rem 0.75rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      cursor: pointer;
      font-size: 0.9rem;
    }
    .kind--on { border-color: var(--fs-primary); background: var(--fs-primary-soft); }
    .kind input, .radio input { accent-color: var(--fs-primary); }
    .radio { display: flex; gap: 0.5rem; align-items: flex-start; font-size: 0.95rem; }
    .radio input { margin-top: 0.25rem; }
    .checks { display: flex; flex-wrap: wrap; gap: 0.5rem 1.5rem; }
    .photo-row { display: flex; flex-wrap: wrap; gap: 1rem; align-items: flex-start; }
    .photo { display: grid; gap: 0.3rem; margin: 0; }
    .photo img { width: 10rem; height: 10rem; object-fit: cover; border-radius: var(--fs-radius-md); }
    fs-image-crop { flex: 1 1 18rem; min-width: 0; }
    .problems {
      margin: 0;
      padding: 0.75rem 1rem 0.75rem 2rem;
      border: 1px solid var(--fs-danger-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
    }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
  `,
})
export class BasketFormPageComponent {
  private readonly api = inject(BasketsService);
  private readonly stores = inject(StoresService);
  private readonly uploads = inject(UploadsService);
  private readonly users = inject(UsersService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly kinds = BASKET_KINDS;
  protected readonly home = BasketLocationType.Home;
  protected readonly custom = BasketLocationType.Custom;
  protected readonly descriptionMax = DESCRIPTION_MAX;
  protected readonly minLifetime = MIN_LIFETIME_DAYS;
  protected readonly maxLifetime = MAX_LIFETIME_DAYS;
  protected readonly fallbackCenter = FALLBACK_CENTER;

  protected readonly basketId = toSignal(this.route.paramMap.pipe(map((p) => p.get('basketId'))), { initialValue: null });
  protected readonly backLink = computed(() => (this.basketId() ? ['/baskets', this.basketId()] : ['/baskets']));

  protected readonly form = new FormGroup({
    description: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(DESCRIPTION_MAX)] }),
    weightKg: new FormControl<number | null>(null, { validators: [Validators.min(0), Validators.max(100)] }),
    kinds: new FormControl<BasketKind[]>([], { nonNullable: true }),
    groceryIds: new FormControl<string[]>([], { nonNullable: true }),
    locationType: new FormControl<BasketLocationType>(BasketLocationType.Home, { nonNullable: true }),
    contactMessage: new FormControl(true, { nonNullable: true }),
    contactPhone: new FormControl(false, { nonNullable: true }),
    contactMobile: new FormControl(false, { nonNullable: true }),
    phone: new FormControl<string | null>(null),
    mobile: new FormControl<string | null>(null),
    lifetimeDays: new FormControl(DEFAULT_LIFETIME_DAYS, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(MIN_LIFETIME_DAYS), Validators.max(MAX_LIFETIME_DAYS)],
    }),
  });
  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** CUSTOM pin; uploaded photo and its local preview (blob: URL, allowed by the CSP img-src, A58). */
  protected readonly position = signal<[number, number] | null>(null);
  protected readonly photoUploadId = signal<string | null>(null);
  private readonly localPreview = signal<string | null>(null);
  private readonly submitted = signal(false);
  private readonly patched = signal<string | null>(null);

  protected readonly data = loadResource<FormData, string>({
    params: () => this.basketId() ?? 'new',
    loader: async (id) => {
      // groceries come from the store metadata, which needs the FOODSAVER role; others offer without them
      const groceries = this.session.hasRole(Role.Foodsaver)
        ? firstValueFrom(this.stores.getStoreMetadata()).then((m) => m.groceries, () => [] as Grocery[])
        : Promise.resolve([] as Grocery[]);
      const [me, basket, list] = await Promise.all([
        firstValueFrom(this.users.getCurrentUser()),
        id === 'new' ? Promise.resolve(null) : firstValueFrom(this.api.getBasket({ basketId: id })),
        groceries,
      ]);
      return { me, basket, groceries: list };
    },
    isEmpty: () => false,
  });

  protected readonly homePosition = computed(() => profilePosition(this.data.data()?.me));
  protected readonly photoPreview = computed(
    () => this.localPreview() ?? photoVariant(this.data.data()?.basket?.photoUrl, 'md') ?? null,
  );
  protected readonly descriptionInvalid = computed(() => {
    this.formValue();
    const control = this.form.controls.description;
    return (control.touched || this.submitted()) && (control.invalid || !control.value.trim());
  });
  private readonly problems = computed(() => {
    this.formValue();
    return basketFormProblems(this.value(), this.homePosition() !== null);
  });
  /** Location/contact problems, shown after the first submit attempt. */
  protected readonly shownProblems = computed(() => (this.submitted() ? this.problems() : []));

  constructor() {
    effect(() => {
      const loaded = this.data.data();
      const key = loaded ? (loaded.basket?.id ?? 'new') : null;
      if (!loaded || this.patched() === key) return;
      this.patched.set(key);
      if (loaded.basket) this.patchFrom(loaded.basket);
      else this.prefill(loaded.me);
    });
    inject(DestroyRef).onDestroy(() => this.revokePreview());
  }

  protected readonly upload = runMutation((file: File) => this.uploads.createUpload({ file }), {
    onSuccess: (upload: Upload) => this.photoUploadId.set(upload.id),
  });

  protected readonly save = runMutation(
    () => {
      const id = this.basketId();
      const value = this.value();
      return id
        ? this.api.updateBasket({ basketId: id, basketUpdate: basketUpdateBody(value) })
        : this.api.createBasket({ basketCreate: basketCreateBody(value) });
    },
    {
      form: this.form,
      errorToast: false,
      successToastKey: this.basketId() ? 'baskets.toast.saved' : 'baskets.toast.created',
      onSuccess: (basket: Basket) => void this.router.navigate(['/baskets', basket.id]),
    },
  );

  protected hasKind(kind: BasketKind): boolean {
    return this.form.controls.kinds.value.includes(kind);
  }

  protected toggleKind(kind: BasketKind): void {
    const current = this.form.controls.kinds.value;
    this.form.controls.kinds.setValue(current.includes(kind) ? current.filter((k) => k !== kind) : [...current, kind]);
  }

  protected uploadPhoto(blob: Blob): void {
    this.revokePreview();
    this.localPreview.set(URL.createObjectURL(blob));
    this.photoUploadId.set(null);
    void this.upload.run(new File([blob], 'basket.jpg', { type: 'image/jpeg' }));
  }

  protected submit(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid || !this.form.controls.description.value.trim() || this.problems().length) return;
    void this.save.run();
  }

  protected errorText(error: ApiErrorView): string {
    return this.i18n.error(error.code, { ...(error.args ?? {}) });
  }

  private value(): BasketFormValue {
    const v = this.form.getRawValue();
    return { ...v, position: this.position(), photoUploadId: this.photoUploadId() };
  }

  /** New basket: contact numbers from the profile (they are only sent when their type is ticked). */
  private prefill(me: UserProfile): void {
    this.form.patchValue({ phone: me.landlinePhone ?? null, mobile: me.mobilePhone ?? null });
    if (!profilePosition(me)) this.form.controls.locationType.setValue(BasketLocationType.Custom);
  }

  private patchFrom(basket: Basket): void {
    this.form.patchValue({
      description: basket.description,
      weightKg: basket.weightGrams != null ? basket.weightGrams / 1000 : null,
      kinds: [...basket.kinds],
      groceryIds: basket.groceries.map((g) => g.id),
      locationType: basket.locationType,
      contactMessage: basket.contact.message,
      contactPhone: !!basket.contact.phone,
      contactMobile: !!basket.contact.mobile,
      phone: basket.contact.phone ?? null,
      mobile: basket.contact.mobile ?? null,
    });
    if (basket.locationType === BasketLocationType.Custom) this.position.set([basket.lat, basket.lon]);
  }

  private revokePreview(): void {
    const url = this.localPreview();
    if (url) URL.revokeObjectURL(url);
  }
}
