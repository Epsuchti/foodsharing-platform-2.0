import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';

import { AutomationService, LinkCandidate, LinkCandidatePage, StoreRef } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/** A store chosen in the picker; `mayEdit`/`automationEnabled` are unknown for stores linked before the dialog opened. */
export interface PickedStore {
  store: StoreRef;
  mayEdit?: boolean;
  automationEnabled?: boolean;
}

/** Minimum search length (server: VALIDATION_FAILED below it). */
export const PICKER_MIN_QUERY = 2;
const DEBOUNCE_MS = 300;

interface ResultGroup {
  region: string;
  items: LinkCandidate[];
}

/** "Zürich › Winterthur": the last two names of the region path. */
export function regionLabel(path: readonly string[]): string {
  return path.slice(-2).join(' › ');
}

/** Results grouped by region in the server's order (by name). */
export function groupByRegion(items: readonly LinkCandidate[]): ResultGroup[] {
  const groups: ResultGroup[] = [];
  for (const item of items) {
    const region = regionLabel(item.regionPath);
    let group = groups.find((g) => g.region === region);
    if (!group) {
      group = { region, items: [] };
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
}

/**
 * "Betrieb verknüpfen" (decision 167 S4, proposal §3.5): selected chips, a debounced search over every store the person
 * may know (StoreVisibility, across regions), results grouped by region with the rules badge. No lock here: any known
 * store links one-way; the bidirectional checkbox of the rule card is locked for stores the person does not edit.
 */
@Component({
  selector: 'fs-store-picker-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, Dialog, FormsModule, InputText, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="'automation.picker.title' | t"
      [style]="{ width: 'min(36rem, 94vw)' }"
      [breakpoints]="{ '48rem': '100vw' }"
      data-testid="automation.picker.dialog"
    >
      <div class="picker">
        <div class="top">
          <ul class="chips" [attr.aria-label]="'automation.picker.selected' | t: { max: max() }">
            @for (picked of picked(); track picked.store.id) {
              <li class="chip chip--on" data-testid="automation.picker.picked" [attr.data-store]="picked.store.id">
                {{ picked.store.name }}
                <button type="button" class="chip-remove" [attr.aria-label]="'automation.picker.remove' | t: { store: picked.store.name }" (click)="toggle(picked, false)">×</button>
              </li>
            } @empty {
              <li class="muted">{{ 'automation.picker.none' | t }}</li>
            }
          </ul>
          <span class="search">
            <i class="pi pi-search" aria-hidden="true"></i>
            <input
              pInputText
              type="search"
              class="search__input"
              [ngModel]="query()"
              (ngModelChange)="onQuery($event)"
              [placeholder]="'automation.picker.search' | t"
              [attr.aria-label]="'automation.picker.search' | t"
              maxlength="100"
              data-testid="automation.picker.search"
            />
          </span>
        </div>
        @if (term().length < minQuery) {
          <div data-testid="automation.picker.state" data-state="idle">
            <p class="muted">{{ 'automation.picker.idle' | t }}</p>
            @if (recent().length) {
              <p class="group">{{ 'automation.picker.recent' | t }}</p>
              <ul class="results">
                @for (store of recent(); track store.id) {
                  <li>
                    <div class="result" data-testid="automation.picker.result" [attr.data-store]="store.id">
                      <p-checkbox
                        [inputId]="'pick-recent-' + store.id"
                        [binary]="true"
                        [ngModel]="isPicked(store.id)"
                        (ngModelChange)="toggle({ store }, $event)"
                        [disabled]="full() && !isPicked(store.id)"
                        data-testid="automation.picker.select"
                      />
                      <label class="result__name" [for]="'pick-recent-' + store.id">{{ store.name }}</label>
                    </div>
                  </li>
                }
              </ul>
            }
          </div>
        } @else {
          <fs-data-state
            [state]="results"
            skeleton="list"
            [skeletonRows]="4"
            [emptyTitle]="'automation.picker.empty' | t: { query: term() }"
            [emptyText]="'automation.picker.emptyHint' | t"
            emptyIcon="pi pi-search"
            testId="automation.picker.state"
            data-testid="automation.picker.state"
          >
            <ng-template [fsData]="results" let-page>
              <p class="muted" aria-live="polite" data-testid="automation.picker.count">{{ 'automation.picker.count' | t: { count: page.page.totalItems } }}</p>
              <ul class="results">
                @for (group of groups(); track group.region) {
                  <li class="group">{{ group.region }}</li>
                  @for (candidate of group.items; track candidate.store.id) {
                    <li>
                      <div class="result" data-testid="automation.picker.result" [attr.data-store]="candidate.store.id" [attr.data-may-edit]="candidate.mayEdit">
                        <p-checkbox
                          [inputId]="'pick-' + candidate.store.id"
                          [binary]="true"
                          [ngModel]="isPicked(candidate.store.id)"
                          (ngModelChange)="toggle(candidate, $event)"
                          [disabled]="full() && !isPicked(candidate.store.id)"
                          data-testid="automation.picker.select"
                        />
                        <span class="result__main">
                          <label class="result__name" [for]="'pick-' + candidate.store.id">{{ candidate.store.name }}</label>
                          @if (candidate.automationEnabled) {
                            <span class="badge badge--ok"><i class="pi pi-shield" aria-hidden="true"></i>{{ 'automation.picker.badge.rules' | t }}</span>
                          } @else {
                            <span class="badge"><i class="pi pi-circle" aria-hidden="true"></i>{{ 'automation.picker.badge.noRules' | t }}</span>
                          }
                          @if (candidate.city) {
                            <span class="muted result__city">{{ candidate.city }}</span>
                          }
                        </span>
                      </div>
                    </li>
                  }
                }
              </ul>
            </ng-template>
          </fs-data-state>
        }
        <p class="muted hint"><i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'automation.picker.hint' | t }}</p>
      </div>
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" [text]="true" (onClick)="visible.set(false)" data-testid="automation.picker.cancel" />
        <p-button [label]="'automation.picker.done' | t: { count: picked().length }" icon="pi pi-check" [disabled]="picked().length === 0" (onClick)="onDone()" data-testid="automation.picker.done" />
      </ng-template>
    </p-dialog>
  `,
  styleUrl: '../automation.scss',
  styles: `
    :host { display: contents; }
    .picker { display: grid; gap: 0.75rem; }
    .top { display: grid; gap: 0.6rem; position: sticky; top: 0; z-index: 1; padding-bottom: 0.25rem; background: var(--fs-surface); }
    .search { display: flex; align-items: center; gap: 0.5rem; }
    .search__input { flex: 1; min-width: 0; }
    .chip-remove { margin-left: 0.25rem; border: 0; background: none; color: inherit; cursor: pointer; font-weight: 700; }
    .results { display: grid; gap: 0.25rem; margin: 0; padding: 0; list-style: none; }
    .group { margin: 0.4rem 0 0; color: var(--fs-text-muted); font-size: 0.8rem; font-weight: 700; }
    .result {
      display: flex; align-items: center; gap: 0.6rem; min-height: var(--fs-target-md, 2.75rem);
      padding: 0.35rem 0.5rem; border-radius: var(--fs-radius-md); cursor: pointer;
    }
    .result:hover { background: var(--fs-surface-muted); }
    .result__main { display: flex; flex-wrap: wrap; align-items: center; gap: 0.2rem 0.6rem; min-width: 0; }
    .result__name { font-weight: 650; overflow-wrap: anywhere; cursor: pointer; }
    .result__city { width: 100%; }
    .badge { display: inline-flex; gap: 0.3rem; align-items: center; }
    .hint { margin: 0; }
  `,
})
export class StorePickerDialogComponent {
  private readonly api = inject(AutomationService);

  readonly visible = model(false);
  readonly storeId = input.required<string | undefined>();
  /** The rule's stores when the dialog opens. */
  readonly selected = input<PickedStore[]>([]);
  /** Stores linked in this store's rules (shown before typing). */
  readonly recent = input<StoreRef[]>([]);
  readonly max = input(5);
  readonly done = output<PickedStore[]>();

  protected readonly minQuery = PICKER_MIN_QUERY;
  protected readonly query = signal('');
  /** The debounced search term. */
  protected readonly term = signal('');
  protected readonly picked = signal<PickedStore[]>([]);
  protected readonly full = computed(() => this.picked().length >= this.max());
  private timer: ReturnType<typeof setTimeout> | undefined;

  protected readonly results = loadResource<LinkCandidatePage, { storeId: string; q: string }>({
    params: () => {
      const storeId = this.storeId();
      const q = this.term();
      return storeId && q.length >= PICKER_MIN_QUERY ? { storeId, q } : undefined;
    },
    loader: (p) => this.api.searchLinkCandidates({ ...p, size: 20 }),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly groups = computed(() => groupByRegion(this.results.data()?.items ?? []));

  constructor() {
    // every opening starts from the rule's stores and an empty search
    effect(() => {
      if (!this.visible()) return;
      untracked(() => {
        this.picked.set(this.selected().map((p) => ({ ...p })));
        this.query.set('');
        this.term.set('');
      });
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.term.set(value.trim()), DEBOUNCE_MS);
  }

  protected isPicked(id: string): boolean {
    return this.picked().some((p) => p.store.id === id);
  }

  protected toggle(store: PickedStore | LinkCandidate, on: boolean): void {
    const id = store.store.id;
    if (!on) {
      this.picked.update((list) => list.filter((p) => p.store.id !== id));
    } else if (!this.isPicked(id) && !this.full()) {
      const picked: PickedStore =
        'regionPath' in store ? { store: store.store, mayEdit: store.mayEdit, automationEnabled: store.automationEnabled } : { ...store };
      this.picked.update((list) => [...list, picked]);
    }
  }

  protected onDone(): void {
    this.done.emit(this.picked());
    this.visible.set(false);
  }
}
