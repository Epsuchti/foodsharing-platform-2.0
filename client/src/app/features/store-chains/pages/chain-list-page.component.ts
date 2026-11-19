import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { MultiSelect } from '@openng/optimus-ui/multiselect';

import { StoreChainPage, StoreChainStatus, StoreChainsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { CHAIN_STATUSES, mayAdministrateChains } from '../store-chains.util';

/**
 * /store-chains (S3): chains with status, press, actual vs estimated store count and KAMs; status filter and name
 * search; chain admins create chains.
 */
@Component({
  selector: 'fs-chain-list-page',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, FormsModule, InputText, MultiSelect, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- the page action sits in the header, like on every other page (design review D7) -->
    <fs-page-header [title]="'storeChains.list.title' | t" [subtitle]="'storeChains.list.subtitle' | t" icon="pi pi-link">
      @if (mayAdmin()) {
        <p-button [label]="'storeChains.list.create' | t" icon="pi pi-plus" (onClick)="createOpen.set(true)" data-testid="storeChains.list.create" />
      }
    </fs-page-header>
    <div class="toolbar">
      <div class="field">
        <label for="chains-search">{{ 'storeChains.list.search' | t }}</label>
        <input pInputText id="chains-search" type="search" [ngModel]="search()" (ngModelChange)="search.set($event)" data-testid="storeChains.list.search" />
      </div>
      <div class="field">
        <label for="chains-status">{{ 'storeChains.list.status' | t }}</label>
        <p-multiselect
          inputId="chains-status"
          [options]="statusOptions()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="statuses()"
          (ngModelChange)="statuses.set($event)"
          [showToggleAll]="false"
          appendTo="body"
          [style]="{ width: '100%' }"
          data-testid="storeChains.list.statusFilter"
        />
      </div>
    </div>

    <fs-data-state [state]="chains" skeleton="table" [emptyTitle]="'storeChains.list.empty' | t" testId="storeChains.list.state" data-testid="storeChains.list.state">
      <ng-template [fsData]="chains" let-page>
        <div class="fs-card wrap">
          <table class="table" data-testid="storeChains.list.table">
            <thead>
              <tr>
                <th scope="col">{{ 'storeChains.field.name' | t }}</th>
                <th scope="col">{{ 'storeChains.field.status' | t }}</th>
                <th scope="col">{{ 'storeChains.field.press' | t }}</th>
                <th scope="col" class="num">{{ 'storeChains.field.stores' | t }}</th>
                <th scope="col">{{ 'storeChains.field.kams' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (chain of page.items; track chain.id) {
                <tr data-testid="storeChains.list.row" [attr.data-chain]="chain.name">
                  <td><a [routerLink]="['/store-chains', chain.id]" data-testid="storeChains.list.link">{{ chain.name }}</a></td>
                  <td [attr.data-label]="'storeChains.field.status' | t"><span class="badge" [attr.data-status]="chain.status">{{ 'storeChains.status.' + chain.status | t }}</span></td>
                  <td [attr.data-label]="'storeChains.field.press' | t">{{ (chain.pressAllowed ? 'storeChains.press.yes' : 'storeChains.press.no') | t }}</td>
                  <td class="num" [attr.data-label]="'storeChains.field.stores' | t" data-testid="storeChains.list.count">{{ chain.storeCount }} / {{ chain.estimatedStoreCount }}</td>
                  <td [attr.data-label]="'storeChains.field.kams' | t">
                    @for (kam of chain.kams; track kam.id; let last = $last) {
                      <span data-testid="storeChains.list.kam">{{ kam.displayName }}</span>{{ last ? '' : ', ' }}
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </ng-template>
    </fs-data-state>

    <p-dialog
      [visible]="createOpen()"
      (visibleChange)="createOpen.set($event)"
      [modal]="true"
      [header]="'storeChains.list.create' | t"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(28rem, calc(100vw - 2rem))' }"
      data-testid="storeChains.create.dialog"
    >
      <form class="form" (ngSubmit)="create.run()">
        <label class="field">
          <span>{{ 'storeChains.field.name' | t }}</span>
          <input pInputText name="name" [(ngModel)]="newName" required maxlength="120" data-testid="storeChains.create.name" />
        </label>
        <label class="field">
          <span>{{ 'storeChains.field.estimated' | t }}</span>
          <input pInputText type="number" name="estimated" min="0" [(ngModel)]="newEstimated" data-testid="storeChains.create.estimated" />
        </label>
        <div class="actions">
          <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="createOpen.set(false)" />
          <p-button type="submit" [label]="'storeChains.list.createConfirm' | t" icon="pi pi-check" [loading]="create.busy()" data-testid="storeChains.create.submit" />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    @use '../../../shared/styles/table-stack' as table;

    :host { display: block; width: min(100%, 64rem); margin: 0 auto; }
    /* search and status share the row; on phones both are full width, one per line (design review D7) */
    .toolbar { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); align-items: end; gap: 0.75rem; margin-bottom: 1rem; }
    .toolbar input { width: 100%; }
    @media (max-width: 39.99rem) {
      .toolbar { grid-template-columns: minmax(0, 1fr); }
    }
    .field { display: grid; gap: 0.3rem; font-weight: 600; }
    .wrap { padding: 0.5rem 1rem; overflow-x: auto; }
    .table { width: 100%; border-collapse: collapse; @include table.stack($label: 9rem); }
    .table th, .table td { padding: 0.5rem 0.4rem; border-bottom: 1px solid var(--fs-border); text-align: left; }
    .table td:first-child a { font-weight: 650; }
    .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .badge { padding: 0.05rem 0.5rem; border-radius: 999px; background: var(--fs-surface-muted); font-size: 0.8rem; font-weight: 600; white-space: nowrap; }
    .form { display: grid; gap: 0.85rem; }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
  `,
})
export class ChainListPageComponent {
  private readonly api = inject(StoreChainsService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  protected readonly search = signal('');
  protected readonly statuses = signal<StoreChainStatus[]>([]);
  protected readonly createOpen = signal(false);
  protected newName = '';
  protected newEstimated = 0;

  protected readonly mayAdmin = computed(() => mayAdministrateChains(this.session.session()));
  protected readonly statusOptions = computed(() =>
    CHAIN_STATUSES.map((value) => ({ value, label: this.i18n.t('storeChains.status.' + value) })),
  );

  protected readonly chains = loadResource<StoreChainPage, { search: string; status: StoreChainStatus[] }>({
    params: () => ({ search: this.search().trim(), status: this.statuses() }),
    loader: ({ search, status }) =>
      this.api.listStoreChains({ ...(search ? { search } : {}), ...(status.length ? { status } : {}), size: 200 }),
  });

  protected readonly create = runMutation(
    () =>
      this.api.createStoreChain({
        storeChainUpdate: {
          name: this.newName.trim(),
          status: StoreChainStatus.Waiting,
          estimatedStoreCount: Math.max(0, Number(this.newEstimated) || 0),
        },
      }),
    {
      successToastKey: 'storeChains.list.created',
      onSuccess: (chain) => {
        this.createOpen.set(false);
        void this.router.navigate(['/store-chains', chain.id]);
      },
    },
  );
}
