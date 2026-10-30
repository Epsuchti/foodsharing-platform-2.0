import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { InputText } from '@openng/optimus-ui/inputtext';
import { MultiSelect } from '@openng/optimus-ui/multiselect';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { firstValueFrom, map } from 'rxjs';

import {
  ConvinceStatus,
  CooperationStatus,
  PublicPickupTime,
  PublicRegionNode,
  PublicRegionsService,
  PublicityStatus,
  StickerStatus,
  StoreCreate,
  StoreDetail,
  StoreMetadata,
  StorePatch,
  StoresService,
  TeamSearchStatus,
} from '../../../api';
import { isAmbassador, isOrga } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { AddressFormComponent, addressFormGroup } from '../../../shared/address-form/address-form.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { COOPERATION_STATUSES, TEAM_STATUSES } from '../stores.util';

interface FormData {
  metadata: StoreMetadata;
  store: StoreDetail | null;
}

const optional = () => new FormControl<string | null>(null);

/**
 * /stores/new?regionId and /stores/:storeId/edit (S2, S16): all store fields in sections (basics, address and map
 * position, cooperation, contact, pickups, team and requirements); coordinators may move the store to another
 * region (409 TEAM_MEMBERS_NOT_IN_TARGET_REGION names the members); a first wall post on create.
 */
@Component({
  selector: 'fs-store-form-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    AddressFormComponent,
    Button,
    InputNumber,
    InputText,
    MultiSelect,
    PageHeaderComponent,
    ReactiveFormsModule,
    RequiredMessageDialogComponent,
    RouterLink,
    Select,
    Textarea,
    ToggleSwitch,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(storeId() ? 'stores.form.editTitle' : 'stores.form.createTitle') | t"
      [subtitle]="data.data()?.store?.name"
      icon="pi pi-shopping-bag"
      [backLink]="storeId() ? ['/stores', storeId()] : '/stores/mine'"
    />

    <fs-data-state [state]="data" skeleton="form" testId="stores.form.state" data-testid="stores.form.state">
      <ng-template [fsData]="data" let-loaded>
        <form [formGroup]="form" (ngSubmit)="submit()" class="stack" data-testid="stores.form">
          <section class="fs-card card" data-testid="stores.form.basics">
            <h2 class="card__title"><i class="pi pi-shopping-bag" aria-hidden="true"></i>{{ 'stores.form.basics' | t }}</h2>
            <div class="form-grid">
              <div class="field field--wide">
                <label for="sf-name">{{ 'stores.form.name' | t }}</label>
                <input pInputText id="sf-name" formControlName="name" data-testid="stores.form.name" />
                @if (form.controls.name.touched && form.controls.name.invalid) {
                  <small class="error">{{ 'stores.form.nameRequired' | t }}</small>
                }
              </div>
              <div class="field">
                <label for="sf-category">{{ 'stores.form.category' | t }}</label>
                <p-select inputId="sf-category" [ariaLabel]="'stores.form.category' | t" formControlName="categoryId" [options]="loaded.metadata.categories" optionLabel="name" optionValue="id" [showClear]="true" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.category" />
              </div>
              <div class="field">
                <label for="sf-chain">{{ 'stores.form.chain' | t }}</label>
                <p-select inputId="sf-chain" [ariaLabel]="'stores.form.chain' | t" formControlName="chainId" [options]="loaded.metadata.chains" optionLabel="name" optionValue="id" [showClear]="true" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.chain" />
              </div>
              <div class="field field--wide">
                <label for="sf-groceries">{{ 'stores.form.groceries' | t }}</label>
                <p-multiselect inputId="sf-groceries" formControlName="groceryIds" [options]="loaded.metadata.groceries" optionLabel="name" optionValue="id" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.groceries" />
              </div>
              <div class="field field--wide">
                <label for="sf-description">{{ 'stores.form.description' | t }}</label>
                <textarea pTextarea id="sf-description" rows="3" formControlName="description" data-testid="stores.form.description"></textarea>
              </div>
              <div class="field field--wide">
                <label for="sf-public">{{ 'stores.form.publicInfo' | t }}</label>
                <textarea pTextarea id="sf-public" rows="2" formControlName="publicInfo" data-testid="stores.form.publicInfo"></textarea>
                <span class="field__hint">{{ 'stores.form.publicInfoHint' | t }}</span>
              </div>
            </div>
          </section>

          <section class="fs-card card" data-testid="stores.form.addressSection">
            <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i>{{ 'stores.form.address' | t }}</h2>
            <fs-address-form [group]="form.controls.address" testId="stores.form.address" />
            @if (mayMove()) {
              <div class="field">
                <label for="sf-region">{{ 'stores.form.region' | t }}</label>
                <p-select inputId="sf-region" formControlName="regionId" [options]="regions()" optionLabel="name" optionValue="id" [filter]="true" filterBy="name" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.region" />
                <span class="field__hint">{{ 'stores.form.regionHint' | t }}</span>
              </div>
            }
          </section>

          <section class="fs-card card" data-testid="stores.form.cooperationSection">
            <h2 class="card__title"><i class="pi pi-handshake" aria-hidden="true"></i>{{ 'stores.form.cooperation' | t }}</h2>
            <div class="form-grid">
              <div class="field">
                <label for="sf-coop">{{ 'stores.form.cooperationStatus' | t }}</label>
                <p-select inputId="sf-coop" formControlName="cooperationStatus" [options]="options().cooperation" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.cooperationStatus" />
              </div>
              <div class="field">
                <label for="sf-start">{{ 'stores.form.cooperationStart' | t }}</label>
                <input pInputText id="sf-start" type="date" formControlName="cooperationStart" data-testid="stores.form.cooperationStart" />
              </div>
              <div class="field">
                <label for="sf-convince">{{ 'stores.form.convinceStatus' | t }}</label>
                <p-select inputId="sf-convince" formControlName="convinceStatus" [options]="options().convince" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.convinceStatus" />
              </div>
              <div class="field">
                <label for="sf-publicity">{{ 'stores.form.publicity' | t }}</label>
                <p-select inputId="sf-publicity" formControlName="publicity" [options]="options().publicity" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.publicity" />
              </div>
              <div class="field">
                <label for="sf-sticker">{{ 'stores.form.sticker' | t }}</label>
                <p-select inputId="sf-sticker" formControlName="sticker" [options]="options().sticker" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.sticker" />
              </div>
            </div>
          </section>

          <section class="fs-card card" formGroupName="contact" data-testid="stores.form.contactSection">
            <h2 class="card__title"><i class="pi pi-id-card" aria-hidden="true"></i>{{ 'stores.form.contact' | t }}</h2>
            <p class="muted">{{ 'stores.form.contactHint' | t }}</p>
            <div class="form-grid">
              <div class="field">
                <label for="sf-person">{{ 'stores.form.contactPerson' | t }}</label>
                <input pInputText id="sf-person" formControlName="person" data-testid="stores.form.contactPerson" />
              </div>
              <div class="field">
                <label for="sf-phone">{{ 'stores.form.contactPhone' | t }}</label>
                <input pInputText id="sf-phone" type="tel" formControlName="phone" data-testid="stores.form.contactPhone" />
              </div>
              <div class="field">
                <label for="sf-fax">{{ 'stores.form.contactFax' | t }}</label>
                <input pInputText id="sf-fax" type="tel" formControlName="fax" data-testid="stores.form.contactFax" />
              </div>
              <div class="field">
                <label for="sf-email">{{ 'stores.form.contactEmail' | t }}</label>
                <input pInputText id="sf-email" type="email" formControlName="email" data-testid="stores.form.contactEmail" />
              </div>
            </div>
          </section>

          <section class="fs-card card" data-testid="stores.form.pickupSection">
            <h2 class="card__title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'stores.form.pickups' | t }}</h2>
            <div class="form-grid">
              <div class="field">
                <label for="sf-time">{{ 'stores.form.publicPickupTime' | t }}</label>
                <p-select inputId="sf-time" formControlName="publicPickupTime" [options]="options().pickupTime" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.publicPickupTime" />
              </div>
              <div class="field">
                <label for="sf-weight">{{ 'stores.form.weightCategory' | t }}</label>
                <p-select inputId="sf-weight" formControlName="weightCategory" [options]="weightOptions()" optionLabel="label" optionValue="value" appendTo="body" [style]="{ width: '100%' }" data-testid="stores.form.weightCategory" />
                <span class="field__hint">{{ 'stores.form.weightHint' | t }}</span>
              </div>
              <div class="field">
                <label for="sf-prefetch">{{ 'stores.form.prefetchDays' | t }}</label>
                <p-inputnumber inputId="sf-prefetch" formControlName="prefetchDays" [min]="1" [max]="42" [showButtons]="true" data-testid="stores.form.prefetchDays" />
                <span class="field__hint">{{ 'stores.form.prefetchHint' | t }}</span>
              </div>
            </div>
            <div class="switch-row">
              <span class="switch-row__text">
                <label class="switch-row__label" for="sf-rule">{{ 'stores.form.useRegionPickupRule' | t }}</label>
                <span class="muted">{{ 'stores.form.useRegionPickupRuleHint' | t }}</span>
              </span>
              <p-toggleswitch inputId="sf-rule" formControlName="useRegionPickupRule" data-testid="stores.form.useRegionPickupRule" />
            </div>
          </section>

          <section class="fs-card card" data-testid="stores.form.teamSection">
            <h2 class="card__title"><i class="pi pi-users" aria-hidden="true"></i>{{ 'stores.form.team' | t }}</h2>
            <div class="radios" role="radiogroup" [attr.aria-label]="'stores.form.teamStatus' | t">
              @for (status of teamStatuses; track status) {
                <label class="radio">
                  <input type="radio" formControlName="teamStatus" [value]="status" [attr.data-testid]="'stores.form.teamStatus.' + status" />
                  <span>{{ 'shared.store.team.' + status | t }}</span>
                </label>
              }
            </div>
            @for (req of requirementControls; track req.control) {
              <div class="switch-row">
                <label class="switch-row__label" [for]="'sf-' + req.control">{{ req.labelKey | t }}</label>
                <p-toggleswitch [inputId]="'sf-' + req.control" [formControlName]="req.control" [attr.data-testid]="'stores.form.' + req.control" />
              </div>
            }
            <div class="field">
              <label for="sf-notes">{{ 'stores.form.specialNotes' | t }}</label>
              <textarea pTextarea id="sf-notes" rows="2" formControlName="specialNotes" data-testid="stores.form.specialNotes"></textarea>
              <span class="field__hint">{{ 'stores.form.specialNotesHint' | t }}</span>
            </div>
          </section>

          @if (!storeId()) {
            <section class="fs-card card" data-testid="stores.form.wallSection">
              <h2 class="card__title"><i class="pi pi-comments" aria-hidden="true"></i>{{ 'stores.form.firstWallPost' | t }}</h2>
              <textarea pTextarea id="sf-wall" rows="3" formControlName="firstWallPost" [attr.aria-label]="'stores.form.firstWallPost' | t" data-testid="stores.form.firstWallPost"></textarea>
            </section>
          } @else {
            <section class="fs-card card">
              <div class="field">
                <label for="sf-reason">{{ 'stores.form.reason' | t }}</label>
                <input pInputText id="sf-reason" formControlName="reason" data-testid="stores.form.reason" />
              </div>
            </section>
          }

          @if (save.error(); as error) {
            <p class="error" role="alert" data-testid="stores.form.error">{{ errorText(error) }}</p>
          }
          <div class="actions">
            <p-button
              [label]="'common.cancel' | t"
              severity="secondary"
              [text]="true"
              [routerLink]="storeId() ? ['/stores', storeId()] : '/stores/mine'"
              data-testid="stores.form.cancel"
            />
            <p-button
              type="submit"
              [label]="(storeId() ? 'stores.form.save' : 'stores.form.create') | t"
              icon="pi pi-check"
              [loading]="save.busy()"
              data-testid="stores.form.submit"
            />
          </div>
        </form>

        @if (loaded.store?.permissions?.mayDelete) {
          <section class="fs-card danger" data-testid="stores.form.danger">
            <h2 class="danger__title">{{ 'stores.delete.title' | t }}</h2>
            <p class="danger__text">{{ 'stores.delete.text' | t }}</p>
            @if (remove.error(); as error) {
              <p class="error" role="alert" data-testid="stores.delete.error">
                {{ errorText(error) }}
                @if (error.code === 'STORE_HAS_PICKUPS') {
                  {{ 'stores.delete.hasPickupsHint' | t }}
                }
              </p>
            }
            <div>
              <p-button
                [label]="'stores.delete.button' | t"
                icon="pi pi-trash"
                severity="danger"
                [outlined]="true"
                (onClick)="deleteOpen.set(true)"
                data-testid="stores.delete.open"
              />
            </div>
          </section>
          <fs-required-message-dialog
            [(visible)]="deleteOpen"
            [title]="'stores.delete.confirmTitle' | t: { name: loaded.store.name }"
            [text]="'stores.delete.confirmText' | t"
            [label]="'stores.delete.reason' | t"
            [confirmLabel]="'stores.delete.button' | t"
            severity="danger"
            [minLength]="3"
            [busy]="remove.busy()"
            (confirm)="remove.run($event)"
            testId="stores.delete.dialog"
          />
        }
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../stores.scss',
  styles: `
    :host { display: block; width: min(100%, 60rem); margin: 0 auto; }
    .field--wide { grid-column: 1 / -1; }
    .error { color: var(--fs-danger); margin: 0; }
    .radios { display: flex; flex-wrap: wrap; gap: 0.5rem 1.25rem; }
    .radio { display: inline-flex; gap: 0.4rem; align-items: center; }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
    .danger { display: grid; gap: 0.6rem; margin-top: 2rem; padding: 1rem; border: 1px solid var(--fs-danger); }
    .danger__title { margin: 0; font-size: 1.05rem; color: var(--fs-danger); }
    .danger__text { margin: 0; }
  `,
})
export class StoreFormPageComponent {
  private readonly api = inject(StoresService);
  private readonly publicRegionsApi = inject(PublicRegionsService);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);

  protected readonly storeId = toSignal(this.route.paramMap.pipe(map((p) => p.get('storeId'))), { initialValue: null });
  private readonly queryRegionId = toSignal(this.route.queryParamMap.pipe(map((p) => p.get('regionId'))), {
    initialValue: null,
  });
  protected readonly teamStatuses = TEAM_STATUSES;
  private readonly patched = signal<string | null>(null);

  protected readonly requirementControls = [
    { control: 'requiresVerified', labelKey: 'stores.requirement.verified' },
    { control: 'requiresHygieneCertificate', labelKey: 'stores.requirement.hygiene' },
    { control: 'requiresPhone', labelKey: 'stores.requirement.phone' },
    { control: 'requiresApplicationText', labelKey: 'stores.requirement.applicationText' },
  ] as const;

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    address: addressFormGroup(),
    regionId: optional(),
    categoryId: optional(),
    chainId: optional(),
    groceryIds: new FormControl<string[]>([], { nonNullable: true }),
    description: optional(),
    publicInfo: optional(),
    cooperationStatus: new FormControl<CooperationStatus>(CooperationStatus.Unclear, { nonNullable: true }),
    cooperationStart: optional(),
    convinceStatus: new FormControl<ConvinceStatus>(ConvinceStatus.NotSet, { nonNullable: true }),
    publicity: new FormControl<PublicityStatus>(PublicityStatus.NotChosen, { nonNullable: true }),
    sticker: new FormControl<StickerStatus>(StickerStatus.NotChosen, { nonNullable: true }),
    contact: new FormGroup({ person: optional(), phone: optional(), fax: optional(), email: optional() }),
    publicPickupTime: new FormControl<PublicPickupTime>(PublicPickupTime.NotSet, { nonNullable: true }),
    weightCategory: new FormControl<string>('NOT_SET', { nonNullable: true }),
    prefetchDays: new FormControl<number>(14, { nonNullable: true, validators: [Validators.min(1), Validators.max(42)] }),
    useRegionPickupRule: new FormControl(false, { nonNullable: true }),
    teamStatus: new FormControl<TeamSearchStatus>(TeamSearchStatus.Open, { nonNullable: true }),
    requiresVerified: new FormControl(true, { nonNullable: true }),
    requiresHygieneCertificate: new FormControl(false, { nonNullable: true }),
    requiresPhone: new FormControl(false, { nonNullable: true }),
    requiresApplicationText: new FormControl(false, { nonNullable: true }),
    specialNotes: optional(),
    firstWallPost: optional(),
    reason: optional(),
  });

  protected readonly data = loadResource<FormData, string>({
    params: () => this.storeId() ?? 'new',
    loader: async (id) => {
      const [metadata, store] = await Promise.all([
        firstValueFrom(this.api.getStoreMetadata()),
        id === 'new' ? Promise.resolve(null) : firstValueFrom(this.api.getStore({ storeId: id })),
      ]);
      return { metadata, store };
    },
    isEmpty: () => false,
  });

  protected readonly mayMove = computed(() => {
    const store = this.data.data()?.store;
    const session = this.session.session();
    return !!store && (isOrga(session) || isAmbassador(session, store.region.id));
  });

  /** Region tree for the move select (coordinators only, S16). */
  private readonly regionTree = loadResource<PublicRegionNode[], true>({
    params: () => (this.mayMove() ? true : undefined),
    loader: () => this.publicRegionsApi.getPublicRegionTree({}),
  });

  protected readonly regions = computed(() => {
    const store = this.data.data()?.store;
    const list: { id: string; name: string }[] = [];
    const walk = (nodes: PublicRegionNode[], depth: number) => {
      for (const node of nodes) {
        list.push({ id: node.id, name: '\u00a0\u00a0'.repeat(depth) + node.name });
        walk(node.children ?? [], depth + 1);
      }
    };
    walk(this.regionTree.data() ?? [], 0);
    if (store && !list.some((r) => r.id === store.region.id)) list.unshift({ id: store.region.id, name: store.region.name });
    return list;
  });

  protected readonly options = computed(() => {
    const opts = <T extends string>(values: T[], prefix: string) =>
      values.map((value) => ({ value, label: this.i18n.t(prefix + value) }));
    return {
      cooperation: opts(COOPERATION_STATUSES, 'shared.store.cooperation.'),
      convince: opts(Object.values(ConvinceStatus), 'stores.convince.'),
      publicity: opts(Object.values(PublicityStatus), 'stores.publicity.'),
      sticker: opts(Object.values(StickerStatus), 'stores.sticker.'),
      pickupTime: opts(Object.values(PublicPickupTime), 'stores.pickupTime.'),
    };
  });

  protected readonly weightOptions = computed(() =>
    (this.data.data()?.metadata.weightCategories ?? []).map((w) => ({
      value: w.code,
      label: w.code === 'NOT_SET' ? this.i18n.t('stores.weight.NOT_SET') : this.i18n.t('stores.form.weightOption', {
        label: this.i18n.t('stores.weight.' + w.code),
        kg: w.averageKg,
      }),
    })),
  );

  constructor() {
    effect(() => {
      const loaded = this.data.data();
      const key = loaded?.store?.id ?? (loaded ? 'new' : null);
      if (!loaded || this.patched() === key) return;
      this.patched.set(key);
      if (loaded.store) this.patchFrom(loaded.store);
    });
  }

  protected readonly save = runMutation(
    () => {
      const id = this.storeId();
      return id
        ? this.api.updateStore({ storeId: id, storePatch: this.patchBody() })
        : this.api.createStore({ regionId: this.queryRegionId() ?? '', storeCreate: this.createBody() });
    },
    {
      form: this.form,
      successToastKey: this.storeId() ? 'stores.form.saved' : 'stores.form.created',
      errorToast: false,
      onSuccess: (store) => void this.router.navigate(['/stores', store.id]),
    },
  );

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    void this.save.run();
  }

  protected readonly deleteOpen = signal(false);

  /** Delete (S13, coordinators): soft delete; 409 STORE_HAS_PICKUPS when the store ever had a sign-up. */
  protected readonly remove = runMutation(
    (reason: string) => this.api.deleteStore({ storeId: this.storeId()!, reasonBody: { reason } }),
    {
      successToastKey: 'stores.delete.done',
      onSuccess: () => {
        this.deleteOpen.set(false);
        const regionId = this.data.data()?.store?.region.id;
        void this.router.navigate(regionId ? ['/regions', regionId, 'stores'] : ['/stores/mine']);
      },
      onError: () => this.deleteOpen.set(false),
    },
  );

  protected errorText(error: { code: string; args?: Record<string, string> }): string {
    return this.i18n.error(error.code, { ...(error.args ?? {}) });
  }

  private patchFrom(store: StoreDetail): void {
    this.form.patchValue({
      name: store.name,
      address: {
        street: store.address.street ?? '',
        postcode: store.address.postcode ?? '',
        city: store.address.city ?? '',
        country: store.address.country ?? 'CH',
        latitude: store.latitude ?? null,
        longitude: store.longitude ?? null,
      },
      regionId: store.region.id,
      categoryId: store.category?.id ?? null,
      chainId: store.chain?.id ?? null,
      groceryIds: (store.groceries ?? []).map((g) => g.id),
      description: store.description ?? null,
      publicInfo: store.publicInfo ?? null,
      cooperationStatus: store.cooperationStatus,
      cooperationStart: store.cooperationStart ?? null,
      convinceStatus: store.convinceStatus ?? ConvinceStatus.NotSet,
      publicity: store.publicity ?? PublicityStatus.NotChosen,
      sticker: store.sticker ?? StickerStatus.NotChosen,
      contact: {
        person: store.contact?.person ?? null,
        phone: store.contact?.phone ?? null,
        fax: store.contact?.fax ?? null,
        email: store.contact?.email ?? null,
      },
      publicPickupTime: store.publicPickupTime ?? PublicPickupTime.NotSet,
      weightCategory: store.weightCategory ?? 'NOT_SET',
      prefetchDays: store.prefetchDays,
      useRegionPickupRule: !!store.useRegionPickupRule,
      teamStatus: store.teamStatus,
      requiresVerified: !!store.requiresVerified,
      requiresHygieneCertificate: !!store.requiresHygieneCertificate,
      requiresPhone: !!store.requiresPhone,
      requiresApplicationText: !!store.requiresApplicationText,
      specialNotes: store.specialNotes ?? null,
    });
    this.form.markAsPristine();
  }

  private common(): Omit<StoreCreate, 'firstWallPost'> {
    const v = this.form.getRawValue();
    const text = (value: string | null) => (value?.trim() ? value.trim() : undefined);
    return {
      name: v.name.trim(),
      address: { street: v.address.street, postcode: v.address.postcode, city: v.address.city, country: v.address.country || undefined },
      latitude: v.address.latitude ?? undefined,
      longitude: v.address.longitude ?? undefined,
      categoryId: v.categoryId ?? undefined,
      chainId: v.chainId ?? undefined,
      groceryIds: v.groceryIds,
      cooperationStatus: v.cooperationStatus,
      cooperationStart: v.cooperationStart || undefined,
      convinceStatus: v.convinceStatus,
      contact: { person: text(v.contact.person), phone: text(v.contact.phone), fax: text(v.contact.fax), email: text(v.contact.email) },
      publicity: v.publicity,
      sticker: v.sticker,
      publicInfo: text(v.publicInfo),
      publicPickupTime: v.publicPickupTime,
      weightCategory: v.weightCategory,
      prefetchDays: v.prefetchDays,
      useRegionPickupRule: v.useRegionPickupRule,
      requiresHygieneCertificate: v.requiresHygieneCertificate,
      requiresVerified: v.requiresVerified,
      requiresPhone: v.requiresPhone,
      requiresApplicationText: v.requiresApplicationText,
      teamStatus: v.teamStatus,
      specialNotes: text(v.specialNotes),
      description: text(v.description),
    };
  }

  private createBody(): StoreCreate {
    return { ...this.common(), firstWallPost: this.form.controls.firstWallPost.value?.trim() || undefined };
  }

  private patchBody(): StorePatch {
    const store = this.data.data()?.store;
    const regionId = this.form.controls.regionId.value;
    return {
      ...this.common(),
      regionId: store && regionId && regionId !== store.region.id ? regionId : undefined,
      reason: this.form.controls.reason.value?.trim() || undefined,
    };
  }
}
