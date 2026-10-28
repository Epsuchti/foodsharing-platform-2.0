import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Observable } from 'rxjs';

import { StoreCategoriesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

type Kind = 'category' | 'grocery';

interface Item {
  id: string;
  name: string;
  storeCount?: number;
}

interface MergeTarget {
  kind: Kind;
  item: Item;
}

/**
 * /admin/store-categories (S4, ORGA): store categories and grocery types — add, inline rename, merge ("move N stores
 * to …", then the source is deleted) and delete (409 CATEGORY_IN_USE when still used).
 */
@Component({
  selector: 'fs-store-categories-admin-page',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, FormsModule, InputText, PageHeaderComponent, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'storeCategories.title' | t" [subtitle]="'storeCategories.subtitle' | t" icon="pi pi-tags" />
    <div class="grid">
      @for (kind of kinds; track kind) {
        <section class="fs-card card" [attr.data-testid]="'storeCategories.' + kind + '.section'">
          <h2 class="card__title">{{ 'storeCategories.' + kind + '.title' | t }}</h2>
          <form class="add" (ngSubmit)="add.run(kind)">
            <input
              pInputText
              [name]="'new-' + kind"
              [(ngModel)]="newName[kind]"
              maxlength="100"
              [attr.aria-label]="'storeCategories.' + kind + '.new' | t"
              [placeholder]="'storeCategories.' + kind + '.new' | t"
              [attr.data-testid]="'storeCategories.' + kind + '.newName'"
            />
            <p-button type="submit" icon="pi pi-plus" [label]="'storeCategories.add' | t" size="small" [disabled]="!newName[kind].trim()" [attr.data-testid]="'storeCategories.' + kind + '.add'" />
          </form>
          <fs-data-state [state]="state(kind)" skeleton="table" [emptyTitle]="'storeCategories.empty' | t" [testId]="'storeCategories.' + kind + '.state'" [attr.data-testid]="'storeCategories.' + kind + '.state'">
            <ng-template [fsData]="state(kind)" let-items>
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">{{ 'storeCategories.name' | t }}</th>
                    <th scope="col" class="num">{{ 'storeCategories.stores' | t }}</th>
                    <th scope="col"><span class="sr-only">{{ 'storeCategories.actions' | t }}</span></th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of items; track item.id) {
                    <tr [attr.data-testid]="'storeCategories.' + kind + '.row'" [attr.data-name]="item.name">
                      <td>
                        @if (editing() === item.id) {
                          <form class="rename" (ngSubmit)="rename.run(kind, item)">
                            <input pInputText name="rename" [(ngModel)]="renameValue" maxlength="100" [attr.aria-label]="'storeCategories.rename' | t" [attr.data-testid]="'storeCategories.' + kind + '.renameInput'" />
                            <p-button type="submit" icon="pi pi-check" size="small" [ariaLabel]="'storeCategories.save' | t" [attr.data-testid]="'storeCategories.' + kind + '.renameSave'" />
                            <p-button icon="pi pi-times" size="small" severity="secondary" [text]="true" [ariaLabel]="'common.cancel' | t" (onClick)="editing.set(null)" />
                          </form>
                        } @else {
                          {{ item.name }}
                        }
                      </td>
                      <td class="num" [attr.data-testid]="'storeCategories.' + kind + '.count'">{{ item.storeCount ?? 0 }}</td>
                      <td class="row-actions">
                        <p-button icon="pi pi-pencil" size="small" severity="secondary" [text]="true" [rounded]="true" [ariaLabel]="'storeCategories.rename' | t" (onClick)="startRename(item)" [attr.data-testid]="'storeCategories.' + kind + '.rename'" />
                        <p-button icon="pi pi-arrow-right-arrow-left" size="small" severity="secondary" [text]="true" [rounded]="true" [ariaLabel]="'storeCategories.merge' | t" (onClick)="openMerge(kind, item)" [attr.data-testid]="'storeCategories.' + kind + '.merge'" />
                        <p-button icon="pi pi-trash" size="small" severity="danger" [text]="true" [rounded]="true" [ariaLabel]="'storeCategories.delete' | t" (onClick)="askDelete(kind, item)" [attr.data-testid]="'storeCategories.' + kind + '.delete'" />
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </ng-template>
          </fs-data-state>
        </section>
      }
    </div>

    <p-dialog
      [visible]="!!mergeSource()"
      (visibleChange)="$event ? null : mergeSource.set(null)"
      [modal]="true"
      [header]="'storeCategories.merge' | t"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(28rem, calc(100vw - 2rem))' }"
      data-testid="storeCategories.mergeDialog"
    >
      @if (mergeSource(); as source) {
        <div class="form">
          <label for="merge-target" class="field">{{ 'storeCategories.mergeInto' | t: { name: source.item.name } }}</label>
          <p-select
            inputId="merge-target"
            [options]="mergeOptions()"
            optionLabel="name"
            optionValue="id"
            [(ngModel)]="mergeTargetId"
            appendTo="body"
            data-testid="storeCategories.mergeTarget"
          />
          <p class="hint" data-testid="storeCategories.mergeHint">
            {{ 'storeCategories.mergeHint' | t: { count: source.item.storeCount ?? 0 } }}
          </p>
          <div class="actions">
            <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="mergeSource.set(null)" />
            <p-button [label]="'storeCategories.mergeConfirm' | t" icon="pi pi-check" [disabled]="!mergeTargetId" [loading]="merge.busy()" (onClick)="merge.run()" data-testid="storeCategories.mergeConfirm" />
          </div>
        </div>
      }
    </p-dialog>
  `,
  styles: `
    :host { display: block; width: min(100%, 64rem); margin: 0 auto; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 24rem), 1fr)); gap: 1rem; }
    .card { display: grid; gap: 0.75rem; align-content: start; padding: 1rem; }
    .card__title { margin: 0; font-size: 1.05rem; }
    .add, .rename { display: flex; gap: 0.4rem; align-items: center; }
    .add input, .rename input { flex: 1; min-width: 0; }
    .table { width: 100%; border-collapse: collapse; }
    .table th, .table td { padding: 0.35rem 0.25rem; border-bottom: 1px solid var(--fs-border); text-align: left; }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .row-actions { display: flex; justify-content: flex-end; gap: 0.1rem; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    .form { display: grid; gap: 0.75rem; }
    .field { font-weight: 600; }
    .hint { margin: 0; color: var(--fs-text-muted); }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
  `,
})
export class StoreCategoriesAdminPageComponent {
  private readonly api = inject(StoreCategoriesService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly kinds: Kind[] = ['category', 'grocery'];
  protected readonly newName: Record<Kind, string> = { category: '', grocery: '' };
  protected readonly editing = signal<string | null>(null);
  protected renameValue = '';
  protected readonly mergeSource = signal<MergeTarget | null>(null);
  protected mergeTargetId: string | null = null;

  private readonly categories = loadResource<Item[]>({
    loader: () => this.api.listStoreCategories(),
    isEmpty: (list) => list.length === 0,
  });
  private readonly groceries = loadResource<Item[]>({
    loader: () => this.api.listGroceries(),
    isEmpty: (list) => list.length === 0,
  });

  protected state(kind: Kind) {
    return kind === 'category' ? this.categories : this.groceries;
  }

  protected readonly mergeOptions = computed(() => {
    const source = this.mergeSource();
    if (!source) return [];
    return (this.state(source.kind).data() ?? []).filter((item) => item.id !== source.item.id);
  });

  private reload(): void {
    this.categories.reload();
    this.groceries.reload();
  }

  protected readonly add = runMutation(
    (kind: Kind): Observable<unknown> => {
      const namedItemUpdate = { name: this.newName[kind].trim() };
      return kind === 'category' ? this.api.createStoreCategory({ namedItemUpdate }) : this.api.createGrocery({ namedItemUpdate });
    },
    {
      successToastKey: 'storeCategories.added',
      onSuccess: () => {
        this.newName.category = '';
        this.newName.grocery = '';
        this.reload();
      },
    },
  );

  protected startRename(item: Item): void {
    this.renameValue = item.name;
    this.editing.set(item.id);
  }

  protected readonly rename = runMutation(
    (kind: Kind, item: Item): Observable<unknown> => {
      const namedItemUpdate = { name: this.renameValue.trim() };
      return kind === 'category'
        ? this.api.updateStoreCategory({ id: item.id, namedItemUpdate })
        : this.api.updateGrocery({ id: item.id, namedItemUpdate });
    },
    {
      successToastKey: 'storeCategories.saved',
      onSuccess: () => {
        this.editing.set(null);
        this.reload();
      },
    },
  );

  protected openMerge(kind: Kind, item: Item): void {
    this.mergeTargetId = null;
    this.mergeSource.set({ kind, item });
  }

  protected readonly merge = runMutation(
    (): Observable<unknown> => {
      const { kind, item } = this.mergeSource()!;
      const mergeRequest = { targetId: this.mergeTargetId! };
      return kind === 'category'
        ? this.api.mergeStoreCategory({ id: item.id, mergeRequest })
        : this.api.mergeGrocery({ id: item.id, mergeRequest });
    },
    {
      successToastKey: 'storeCategories.merged',
      onSuccess: () => {
        this.mergeSource.set(null);
        this.reload();
      },
    },
  );

  protected readonly remove = runMutation(
    (kind: Kind, item: Item): Observable<unknown> =>
      kind === 'category' ? this.api.deleteStoreCategory({ id: item.id }) : this.api.deleteGrocery({ id: item.id }),
    { successToastKey: 'storeCategories.deleted', onSuccess: () => this.reload() },
  );

  protected askDelete(kind: Kind, item: Item): void {
    this.confirm.confirm({
      header: this.i18n.t('storeCategories.deleteTitle', { name: item.name }),
      message: this.i18n.t('storeCategories.deleteText'),
      acceptLabel: this.i18n.t('storeCategories.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(kind, item),
    });
  }
}
