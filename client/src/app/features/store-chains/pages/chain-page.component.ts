import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { of } from 'rxjs';

import { StoreChain, StoreChainStatus, StoreChainUpdate, StoreChainsService, StoreListItem, UserSummary, UsersService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { CHAIN_STATUSES, isKamOf, mayAdministrateChains } from '../store-chains.util';

interface ChainForm {
  name: string;
  status: StoreChainStatus;
  pressAllowed: boolean;
  estimatedStoreCount: number;
  street: string;
  postcode: string;
  city: string;
  country: string;
  commonStoreInformation: string;
  notes: string;
}

/**
 * /store-chains/:chainId (S3): chain details, common store information (markdown), notes (admins and KAMs), KAMs
 * (chain admins add and remove them), stores of the chain. Admins and KAMs edit the chain; KAMs not the KAM list.
 */
@Component({
  selector: 'fs-chain-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Dialog,
    FormsModule,
    InputText,
    MarkdownComponent,
    PageHeaderComponent,
    RouterLink,
    Select,
    Textarea,
    ToggleSwitch,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="chain" skeleton="detail" backLink="/store-chains" testId="storeChains.chain.state" data-testid="storeChains.chain.state">
      <ng-template [fsData]="chain" let-c>
        <fs-page-header [title]="c.name" [subtitle]="'storeChains.status.' + c.status | t" icon="pi pi-link" backLink="/store-chains" [backLabel]="'common.back' | t" />
        @if (mayEdit()) {
          <div class="toolbar">
            <p-button [label]="'storeChains.chain.edit' | t" icon="pi pi-pencil" (onClick)="openEdit(c)" data-testid="storeChains.chain.edit" />
          </div>
        }

        <div class="grid">
          <section class="fs-card card" data-testid="storeChains.chain.details">
            <h2 class="card__title">{{ 'storeChains.chain.details' | t }}</h2>
            <dl class="facts">
              <dt>{{ 'storeChains.field.status' | t }}</dt>
              <dd data-testid="storeChains.chain.status">{{ 'storeChains.status.' + c.status | t }}</dd>
              <dt>{{ 'storeChains.field.press' | t }}</dt>
              <dd>{{ (c.pressAllowed ? 'storeChains.press.yes' : 'storeChains.press.no') | t }}</dd>
              <dt>{{ 'storeChains.field.stores' | t }}</dt>
              <dd data-testid="storeChains.chain.count">{{ c.storeCount }} / {{ c.estimatedStoreCount }}</dd>
              @if (c.headquarters; as hq) {
                <dt>{{ 'storeChains.field.headquarters' | t }}</dt>
                <dd>{{ [hq.street, [hq.postcode, hq.city].join(' ').trim(), hq.country].filter(present).join(', ') }}</dd>
              }
            </dl>
          </section>

          <section class="fs-card card" data-testid="storeChains.chain.kams">
            <h2 class="card__title">{{ 'storeChains.field.kams' | t }}</h2>
            <ul class="kams">
              @for (kam of c.kams; track kam.id) {
                <li data-testid="storeChains.chain.kam" [attr.data-user]="kam.displayName">
                  <fs-user-chip [name]="kam.displayName" [userId]="kam.id" [avatarUrl]="kam.avatarUrl" />
                  @if (mayAdmin()) {
                    <p-button
                      icon="pi pi-times"
                      severity="danger"
                      [text]="true"
                      [rounded]="true"
                      size="small"
                      [ariaLabel]="'storeChains.chain.removeKam' | t: { name: kam.displayName }"
                      (onClick)="saveKams.run(c, without(c, kam))"
                      data-testid="storeChains.chain.removeKam"
                    />
                  }
                </li>
              } @empty {
                <li class="muted">{{ 'storeChains.chain.noKams' | t }}</li>
              }
            </ul>
            @if (mayAdmin()) {
              <label class="field" for="chain-kam-search">{{ 'storeChains.chain.addKam' | t }}</label>
              <input pInputText id="chain-kam-search" type="search" [ngModel]="kamSearch()" (ngModelChange)="kamSearch.set($event)" data-testid="storeChains.chain.kamSearch" />
              @if (kamSearch().trim().length >= 2) {
                <fs-data-state [state]="candidates" skeleton="list" [compact]="true" [emptyTitle]="'storeChains.chain.noMatch' | t" testId="storeChains.chain.candidates">
                  <ng-template [fsData]="candidates" let-users>
                    <ul class="kams">
                      @for (user of users; track user.id) {
                        <li>
                          <fs-user-chip [name]="user.displayName" [userId]="user.id" [avatarUrl]="user.avatarUrl" />
                          <p-button
                            icon="pi pi-plus"
                            [label]="'storeChains.chain.add' | t"
                            size="small"
                            [outlined]="true"
                            (onClick)="saveKams.run(c, with(c, user))"
                            data-testid="storeChains.chain.addKam"
                          />
                        </li>
                      }
                    </ul>
                  </ng-template>
                </fs-data-state>
              }
            }
          </section>
        </div>

        @if (c.commonStoreInformation) {
          <section class="fs-card card" data-testid="storeChains.chain.info">
            <h2 class="card__title">{{ 'storeChains.field.commonInfo' | t }}</h2>
            <fs-markdown [markdown]="c.commonStoreInformation" />
          </section>
        }
        @if (mayEdit() && c.notes) {
          <section class="fs-card card" data-testid="storeChains.chain.notes">
            <h2 class="card__title">{{ 'storeChains.field.notes' | t }}</h2>
            <p class="pre">{{ c.notes }}</p>
          </section>
        }

        <section class="fs-card card">
          <h2 class="card__title">{{ 'storeChains.chain.stores' | t }}</h2>
          <fs-data-state [state]="stores" skeleton="list" [compact]="true" [emptyTitle]="'storeChains.chain.noStores' | t" testId="storeChains.chain.storesState" data-testid="storeChains.chain.storesState">
            <ng-template [fsData]="stores" let-list>
              <ul class="stores">
                @for (store of list; track store.id) {
                  <li data-testid="storeChains.chain.store">
                    <a [routerLink]="['/stores', store.id]">{{ store.name }}</a>
                    <span class="muted">{{ store.address.city }} · {{ store.region.name }}</span>
                  </li>
                }
              </ul>
            </ng-template>
          </fs-data-state>
        </section>
      </ng-template>
    </fs-data-state>

    <p-dialog
      [visible]="editOpen()"
      (visibleChange)="editOpen.set($event)"
      [modal]="true"
      [header]="'storeChains.chain.edit' | t"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(40rem, calc(100vw - 2rem))' }"
      data-testid="storeChains.edit.dialog"
    >
      @if (form(); as f) {
        <form class="form" (ngSubmit)="save.run()">
          <label class="field"><span>{{ 'storeChains.field.name' | t }}</span>
            <input pInputText name="name" [(ngModel)]="f.name" required maxlength="120" data-testid="storeChains.edit.name" /></label>
          <label class="field" for="chain-status"><span>{{ 'storeChains.field.status' | t }}</span></label>
          <p-select inputId="chain-status" name="status" [options]="statusOptions()" optionLabel="label" optionValue="value" [(ngModel)]="f.status" appendTo="body" data-testid="storeChains.edit.status" />
          <div class="row">
            <p-toggleswitch inputId="chain-press" name="press" [(ngModel)]="f.pressAllowed" data-testid="storeChains.edit.press" />
            <label for="chain-press">{{ 'storeChains.field.press' | t }}</label>
          </div>
          <label class="field"><span>{{ 'storeChains.field.estimated' | t }}</span>
            <input pInputText type="number" min="0" name="estimated" [(ngModel)]="f.estimatedStoreCount" data-testid="storeChains.edit.estimated" /></label>
          <fieldset class="hq">
            <legend>{{ 'storeChains.field.headquarters' | t }}</legend>
            <input pInputText name="street" [(ngModel)]="f.street" [attr.aria-label]="'storeChains.field.street' | t" [placeholder]="'storeChains.field.street' | t" />
            <input pInputText name="postcode" [(ngModel)]="f.postcode" [attr.aria-label]="'storeChains.field.postcode' | t" [placeholder]="'storeChains.field.postcode' | t" />
            <input pInputText name="city" [(ngModel)]="f.city" [attr.aria-label]="'storeChains.field.city' | t" [placeholder]="'storeChains.field.city' | t" />
            <input pInputText name="country" [(ngModel)]="f.country" [attr.aria-label]="'storeChains.field.country' | t" [placeholder]="'storeChains.field.country' | t" />
          </fieldset>
          <label class="field"><span>{{ 'storeChains.field.commonInfo' | t }}</span>
            <textarea pTextarea rows="5" name="info" [(ngModel)]="f.commonStoreInformation" data-testid="storeChains.edit.info"></textarea></label>
          <label class="field"><span>{{ 'storeChains.field.notes' | t }}</span>
            <textarea pTextarea rows="3" name="notes" [(ngModel)]="f.notes" data-testid="storeChains.edit.notes"></textarea></label>
          <div class="actions">
            <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="editOpen.set(false)" />
            <p-button type="submit" [label]="'storeChains.chain.save' | t" icon="pi pi-check" [loading]="save.busy()" data-testid="storeChains.edit.submit" />
          </div>
        </form>
      }
    </p-dialog>
  `,
  styles: `
    :host { display: block; width: min(100%, 64rem); margin: 0 auto; }
    .toolbar { display: flex; justify-content: flex-end; margin-bottom: 1rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr)); gap: 1rem; margin-bottom: 1rem; }
    .card { display: grid; gap: 0.6rem; align-content: start; padding: 1rem; margin-bottom: 1rem; }
    .grid .card { margin-bottom: 0; }
    .card__title { margin: 0; font-size: 1.05rem; }
    .facts { display: grid; grid-template-columns: auto 1fr; gap: 0.35rem 1rem; margin: 0; }
    .facts dt { font-weight: 600; }
    .facts dd { margin: 0; }
    .kams, .stores { display: grid; gap: 0.4rem; margin: 0; padding: 0; list-style: none; }
    .kams li { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }
    .stores li { display: flex; flex-wrap: wrap; gap: 0.25rem 0.75rem; }
    .muted { color: var(--fs-text-muted); }
    .pre { margin: 0; white-space: pre-wrap; }
    .field { display: grid; gap: 0.3rem; font-weight: 600; }
    .form { display: grid; gap: 0.8rem; }
    .form textarea { width: 100%; }
    .row { display: flex; gap: 0.6rem; align-items: center; }
    .hq { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: 0.5rem; margin: 0; padding: 0.6rem; border: 1px solid var(--fs-border); border-radius: 0.5rem; }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
  `,
})
export class ChainPageComponent {
  private readonly api = inject(StoreChainsService);
  private readonly usersApi = inject(UsersService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);

  /** Route parameter. */
  readonly chainId = input<string | undefined>(undefined);

  protected readonly editOpen = signal(false);
  protected readonly form = signal<ChainForm | null>(null);
  protected readonly kamSearch = signal('');
  protected readonly present = (value: string | undefined) => !!value?.trim();

  protected readonly chain = loadResource<StoreChain, string>({
    params: () => this.chainId(),
    loader: (chainId) => this.api.getStoreChain({ chainId }),
    isEmpty: () => false,
  });
  protected readonly stores = loadResource<StoreListItem[], string>({
    params: () => this.chainId(),
    loader: (chainId) => this.api.listChainStores({ chainId }),
  });
  protected readonly candidates = loadResource<UserSummary[], string>({
    params: () => {
      const q = this.kamSearch().trim();
      return q.length >= 2 ? q : undefined;
    },
    loader: (q) => (q ? this.usersApi.searchUsers({ q, limit: 8 }) : of([])),
  });

  protected readonly mayAdmin = computed(() => mayAdministrateChains(this.session.session()));
  protected readonly mayEdit = computed(() => this.mayAdmin() || isKamOf(this.chain.data(), this.session.user()?.id));
  protected readonly statusOptions = computed(() =>
    CHAIN_STATUSES.map((value) => ({ value, label: this.i18n.t('storeChains.status.' + value) })),
  );

  protected without(chain: StoreChain, kam: UserSummary): string[] {
    return chain.kams.filter((k) => k.id !== kam.id).map((k) => k.id);
  }

  protected with(chain: StoreChain, user: UserSummary): string[] {
    return [...new Set([...chain.kams.map((k) => k.id), user.id])];
  }

  protected openEdit(chain: StoreChain): void {
    const hq = chain.headquarters ?? {};
    this.form.set({
      name: chain.name,
      status: chain.status,
      pressAllowed: chain.pressAllowed,
      estimatedStoreCount: chain.estimatedStoreCount,
      street: hq.street ?? '',
      postcode: hq.postcode ?? '',
      city: hq.city ?? '',
      country: hq.country ?? '',
      commonStoreInformation: chain.commonStoreInformation ?? '',
      notes: chain.notes ?? '',
    });
    this.editOpen.set(true);
  }

  protected readonly saveKams = runMutation(
    (chain: StoreChain, kamIds: string[]) => this.api.updateStoreChain({ chainId: chain.id, storeChainUpdate: { kamIds } }),
    {
      successToastKey: 'storeChains.chain.kamsSaved',
      onSuccess: () => {
        this.kamSearch.set('');
        this.chain.reload();
      },
    },
  );

  /** Never sends `kamIds` (absent = unchanged, decision 56), so KAMs can save too. */
  protected readonly save = runMutation(
    () => {
      const f = this.form()!;
      const update: StoreChainUpdate = {
        name: f.name.trim(),
        status: f.status,
        pressAllowed: f.pressAllowed,
        estimatedStoreCount: Math.max(0, Number(f.estimatedStoreCount) || 0),
        headquarters: { street: f.street.trim(), postcode: f.postcode.trim(), city: f.city.trim(), country: f.country.trim() },
        commonStoreInformation: f.commonStoreInformation,
        notes: f.notes,
      };
      return this.api.updateStoreChain({ chainId: this.chainId()!, storeChainUpdate: update });
    },
    {
      successToastKey: 'storeChains.chain.saved',
      onSuccess: () => {
        this.editOpen.set(false);
        this.chain.reload();
      },
    },
  );
}
