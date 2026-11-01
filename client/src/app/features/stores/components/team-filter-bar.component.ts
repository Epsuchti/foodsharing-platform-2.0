import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

import { TeamSort } from '../../../api';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';

/** One chip of the single-select status group; `count` is shown inside the button text when set. */
export interface StatusChip<S extends string> {
  value: S;
  labelKey: string;
  count?: number;
}

const SORTS: { value: TeamSort; key: string }[] = [
  { value: TeamSort.Name, key: 'stores.team.sort.name' },
  { value: TeamSort.PickupCount, key: 'stores.team.sort.pickupCount' },
  { value: TeamSort.LastPickup, key: 'stores.team.sort.lastPickup' },
];

/**
 * Filter bar of the store team list and the per-member statistics (resume item 14): name search (debounced 300 ms),
 * sort select and one single-select status chip group. Test ids `<testIdPrefix>.search|sort|filter.<status>`.
 */
@Component({
  selector: 'fs-team-filter-bar',
  imports: [FormsModule, InputText, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="filters">
      <div class="field">
        <label [for]="idPrefix() + '-search'">{{ 'stores.team.search' | t }}</label>
        <input
          #searchInput
          pInputText
          [id]="idPrefix() + '-search'"
          type="search"
          maxlength="100"
          [ngModel]="searchText()"
          (ngModelChange)="onSearch($event)"
          [placeholder]="'stores.team.searchPlaceholder' | t"
          [attr.data-testid]="testIdPrefix() + '.search'"
        />
      </div>
      <div class="field">
        <label [for]="idPrefix() + '-sort'">{{ 'stores.team.sort' | t }}</label>
        <p-select
          [inputId]="idPrefix() + '-sort'"
          [options]="sortOptions()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="sort()"
          (ngModelChange)="sortChange.emit($event)"
          appendTo="body"
          [style]="{ width: '100%' }"
          [attr.data-testid]="testIdPrefix() + '.sort'"
        />
      </div>
      <div class="chips" role="group" [attr.aria-label]="'stores.team.statusFilter' | t">
        @for (chip of chips(); track chip.value) {
          <button
            type="button"
            class="chip"
            [class.chip--active]="status() === chip.value"
            [attr.aria-pressed]="status() === chip.value"
            (click)="statusChange.emit(chip.value)"
            [attr.data-testid]="testIdPrefix() + '.filter.' + chip.value.toLowerCase()"
          >
            {{ chip.labelKey | t }}
            @if (chip.count !== undefined) {
              <span class="chip__n">{{ chip.count }}</span>
            }
          </button>
        }
      </div>
    </div>
  `,
  styles: `
    .filters {
      display: grid;
      gap: 0.75rem;
      @media (min-width: 48rem) {
        grid-template-columns: minmax(0, 1fr) 14rem;
      }
    }
    .filters input {
      width: 100%;
    }
    .field {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }
    .field > label {
      font-size: 0.85rem;
      font-weight: 650;
    }
    .chips {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
      grid-column: 1 / -1;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      min-height: var(--fs-target-sm);
      padding: 0.3rem 0.8rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      color: inherit;
      font: inherit;
      font-size: 0.88rem;
      cursor: pointer;
    }
    /* soft fill + strong text: white on --fs-primary fails contrast in the dark theme (F6 sweep) */
    .chip--active {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
    .chip__n {
      padding: 0 0.45rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.78rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .chip--active .chip__n {
      background: color-mix(in srgb, var(--fs-primary) 16%, transparent);
    }
  `,
})
export class TeamFilterBarComponent<S extends string> {
  private readonly i18n = inject(I18nService);

  /** Prefix of the element ids (label `for`). */
  readonly idPrefix = input.required<string>();
  readonly testIdPrefix = input.required<string>();
  readonly chips = input.required<StatusChip<S>[]>();
  readonly status = input.required<S>();
  readonly sort = input.required<TeamSort>();

  readonly statusChange = output<S>();
  readonly sortChange = output<TeamSort>();
  /** The trimmed search, 300 ms after the last keystroke. */
  readonly searchChange = output<string>();

  protected readonly searchText = signal('');
  private readonly searchField = viewChild<ElementRef<HTMLInputElement>>('searchInput');
  private readonly searchInput = new Subject<string>();

  protected readonly sortOptions = computed(() => {
    this.i18n.language();
    return SORTS.map((sort) => ({ value: sort.value, label: this.i18n.t(sort.key) }));
  });

  constructor() {
    this.searchInput
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.searchChange.emit(value.trim()));
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    this.searchInput.next(value);
  }

  /** Clears the search field (no debounce) and focuses it; the parent resets status, sort and page itself. */
  reset(): void {
    this.searchText.set('');
    this.searchInput.next('');
    this.searchField()?.nativeElement.focus();
  }
}
