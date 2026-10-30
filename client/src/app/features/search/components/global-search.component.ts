import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { map } from 'rxjs';

import { SearchIndexData, SearchResults, SearchService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  SEARCH_DEBOUNCE_MS,
  SEARCH_MIN_LENGTH,
  SearchOption,
  SearchOptionGroup,
  countHits,
  filterIndex,
  moveActive,
  resultGroups,
} from '../search.util';

interface RemoteResults {
  q: string;
  results: SearchResults;
}

let nextId = 0;
/** activeKey of the "show all results" option (option keys never start with a NUL). */
const ALL_KEY = '\u0000all';

/**
 * Header search (C5, plan-m7 Q12): an ARIA 1.2 combobox with a grouped listbox popup. While typing, the user's own
 * entities (`getSearchIndex`, loaded once on first focus) are matched in the browser right away; from two characters
 * the server search (`search`) follows after 300 ms and adds the other hits (duplicates of index hits left out).
 * Arrow keys move through the options, Enter opens the active one or `/search?q=`, Escape closes (and then clears).
 * The active option is kept by key, so it stays the same option when the server hits arrive after the index hits, and
 * an arrow key renders it at once: `aria-activedescendant` points at it as soon as the keydown has been handled.
 * The header shows it for logged-in users when GLOBAL_SEARCH is on. Focus stays in the input (aria-activedescendant);
 * the options' tabindex=-1 and keydown.enter only serve pointer users and the template lint.
 */
@Component({
  selector: 'fs-global-search',
  imports: [...DATA_STATE_IMPORTS, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-global-search',
    '[class.fs-global-search--start]': "popupAlign() === 'start'",
    '(focusout)': 'onFocusOut($event)',
  },
  template: `
    @if (catalogReady()) {
      <div class="gs" role="search" [attr.aria-label]="'search.global.landmark' | t">
        <label class="fs-visually-hidden" [for]="inputId">{{ 'search.global.label' | t }}</label>
        <i class="pi pi-search gs__icon" aria-hidden="true"></i>
        <input
          class="gs__input"
          type="text"
          role="combobox"
          autocomplete="off"
          spellcheck="false"
          maxlength="100"
          aria-autocomplete="list"
          [id]="inputId"
          [value]="query()"
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="open() ? listboxId : null"
          [attr.aria-activedescendant]="activeOptionId()"
          [attr.aria-describedby]="hintId"
          [placeholder]="'search.global.placeholder' | t"
          (input)="onInput($any($event.target).value)"
          (focus)="onFocus()"
          (keydown)="onKeydown($event)"
          data-testid="search.global.input"
        />
        <span class="fs-visually-hidden" [id]="hintId">{{ 'search.global.hint' | t }}</span>
        @if (open()) {
          <div class="gs__popup" data-testid="search.global.popup">
            <div class="gs__list" role="listbox" [id]="listboxId" [attr.aria-label]="'search.global.results' | t" data-testid="search.global.listbox">
              @for (group of groups(); track group.id) {
                <div class="gs__group" role="group" [attr.aria-labelledby]="listboxId + '-' + group.id" [attr.data-group]="group.id" data-testid="search.global.group">
                  <div class="gs__group-label" [id]="listboxId + '-' + group.id">{{ group.labelKey | t }}</div>
                  @for (option of group.options; track option.key) {
                    <div
                      class="gs__option"
                      role="option"
                      [id]="optionId(option)"
                      [class.gs__option--active]="isActive(option)"
                      [attr.aria-selected]="isActive(option)"
                      [attr.data-link]="option.link"
                      tabindex="-1"
                      (mousedown)="$event.preventDefault()"
                      (click)="go(option.link)"
                      (keydown.enter)="go(option.link)"
                      data-testid="search.global.option"
                    >
                      <i [class]="option.icon + ' gs__option-icon'" aria-hidden="true"></i>
                      <span class="gs__option-text">
                        <span class="gs__option-title">{{ option.title }}</span>
                        @if (option.subtitle) {
                          <span class="gs__option-sub">{{ option.subtitle }}</span>
                        }
                      </span>
                    </div>
                  }
                </div>
              }
              @if (query().trim()) {
                <div
                  class="gs__option gs__option--all"
                  role="option"
                  [id]="listboxId + '-all'"
                  [class.gs__option--active]="active() === options().length"
                  [attr.aria-selected]="active() === options().length"
                  tabindex="-1"
                  (mousedown)="$event.preventDefault()"
                  (click)="showAll()"
                  (keydown.enter)="showAll()"
                  data-testid="search.global.all"
                >
                  <i class="pi pi-arrow-right gs__option-icon" aria-hidden="true"></i>
                  <span class="gs__option-title">{{ 'search.global.all' | t: { query: query().trim() } }}</span>
                </div>
              }
            </div>
            @if (tooShort()) {
              <p class="gs__status" data-testid="search.global.tooShort">{{ 'search.global.tooShort' | t: { min: minLength } }}</p>
            }
            <fs-data-state [state]="remote" [compact]="true" data-testid="search.global.state">
              <ng-template fsSkeleton>
                <p class="gs__status" role="status"><i class="pi pi-spin pi-spinner" aria-hidden="true"></i> {{ 'search.global.searching' | t }}</p>
              </ng-template>
              <ng-template fsEmpty>
                @if (!groups().length) {
                  <p class="gs__status" role="status" data-testid="search.global.empty">{{ 'search.global.empty' | t: { query: query().trim() } }}</p>
                }
              </ng-template>
              <ng-template [fsData]="remote" let-loaded>
                <p class="fs-visually-hidden" role="status">{{ 'search.global.count' | t: { count: count(loaded) } }}</p>
              </ng-template>
            </fs-data-state>
          </div>
        }
      </div>
    }
  `,
  styles: `
    :host { position: relative; display: block; min-width: 0; }
    .gs { position: relative; }
    .gs__icon { position: absolute; top: 50%; left: 0.75rem; transform: translateY(-50%); color: var(--fs-text-muted); font-size: 0.9rem; pointer-events: none; }
    .gs__input {
      width: 100%;
      height: 2.5rem;
      padding: 0 0.75rem 0 2.2rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface-muted);
      color: var(--fs-text);
      font: inherit;
      font-size: 0.92rem;
    }
    .gs__input::placeholder { color: var(--fs-text-muted); opacity: 1; }
    .gs__input:focus { outline: 2px solid var(--fs-primary); outline-offset: 1px; background: var(--fs-surface); }
    .gs__popup {
      position: absolute;
      z-index: 50;
      top: calc(100% + 0.4rem);
      right: 0;
      width: min(28rem, calc(100vw - 2rem));
      max-height: min(70vh, 34rem);
      overflow-y: auto;
      padding: 0.4rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-lg, 0 12px 32px rgb(0 0 0 / 0.18));
    }
    /* side menu (decision 133): the field sits at the left edge, so the results open towards the content */
    :host(.fs-global-search--start) .gs__popup { right: auto; left: 0; }
    .gs__group + .gs__group, .gs__group + .gs__option--all { margin-top: 0.35rem; }
    .gs__group-label { padding: 0.35rem 0.6rem 0.2rem; color: var(--fs-text-muted); font-size: 0.75rem; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; }
    .gs__option {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      min-height: 2.5rem;
      padding: 0.35rem 0.6rem;
      border-radius: var(--fs-radius-sm);
      cursor: pointer;
    }
    .gs__option:hover { background: var(--fs-surface-muted); }
    .gs__option--active, .gs__option--active:hover { background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .gs__option-icon { width: 1.25rem; color: var(--fs-text-muted); text-align: center; }
    .gs__option--active .gs__option-icon { color: inherit; }
    .gs__option-text { display: grid; min-width: 0; }
    .gs__option-title { overflow: hidden; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
    .gs__option-sub { overflow: hidden; color: var(--fs-text-muted); font-size: 0.8rem; text-overflow: ellipsis; white-space: nowrap; }
    .gs__option--active .gs__option-sub { color: inherit; }
    .gs__option--all { border-top: 1px solid var(--fs-border); border-radius: 0 0 var(--fs-radius-sm) var(--fs-radius-sm); }
    .gs__status { display: flex; align-items: center; gap: 0.4rem; margin: 0; padding: 0.5rem 0.6rem; color: var(--fs-text-muted); font-size: 0.85rem; }
  `,
})
export class GlobalSearchComponent {
  private readonly api = inject(SearchService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  protected readonly inputId = `fs-global-search-${nextId}`;
  protected readonly listboxId = `fs-global-search-${nextId}-list`;
  protected readonly hintId = `fs-global-search-${nextId++}-hint`;
  protected readonly minLength = SEARCH_MIN_LENGTH;
  /** Edge the results popup aligns with: the header field (end) or the side menu field (start). */
  readonly popupAlign = input<'end' | 'start'>('end');

  /** Rendered in the header before the rest of the catalog (search.*) may have arrived: wait for it. */
  protected readonly catalogReady = signal(false);
  readonly query = signal('');
  /** The query the server is asked for (300 ms after the last keystroke, from two characters). */
  private readonly debounced = signal('');
  readonly open = signal(false);
  /** Key of the active option (ALL_KEY = "show all"), null = none. */
  private readonly activeKey = signal<string | null>(null);
  /** Index into options() (options().length = "show all"), -1 = none (also when the active option is gone). */
  readonly active = computed(() => {
    const key = this.activeKey();
    if (key === null) return -1;
    if (key === ALL_KEY) return this.query().trim() ? this.options().length : -1;
    return this.options().findIndex((option) => option.key === key);
  });
  private readonly indexWanted = signal(false);

  protected readonly index = loadResource<SearchIndexData, true>({
    params: () => (this.indexWanted() ? true : undefined),
    loader: () => this.api.getSearchIndex(),
    isEmpty: () => false,
  });

  protected readonly remote = loadResource<RemoteResults, string>({
    params: () => (this.debounced().length >= SEARCH_MIN_LENGTH ? this.debounced() : undefined),
    loader: (q) => this.api.search({ q }).pipe(map((results) => ({ q, results }))),
    isEmpty: (value) => countHits(value.results) === 0,
  });

  private readonly indexGroups = computed(() => filterIndex(this.index.data(), this.query()));

  readonly groups = computed<SearchOptionGroup[]>(() => {
    const own = this.indexGroups();
    const loaded = this.remote.data();
    const q = this.query().trim();
    // results of an older query never show next to the current one
    const fresh = loaded && loaded.q === q ? loaded.results : undefined;
    const seen = new Set(own.flatMap((group) => group.options.map((option) => option.link)));
    return [...own, ...resultGroups(fresh, seen)];
  });

  readonly options = computed<SearchOption[]>(() => this.groups().flatMap((group) => group.options));

  protected readonly tooShort = computed(() => {
    const q = this.query().trim();
    return q.length > 0 && q.length < SEARCH_MIN_LENGTH && this.groups().length === 0;
  });

  protected readonly activeOptionId = computed(() => {
    const index = this.active();
    if (!this.open() || index < 0) return null;
    const option = this.options()[index];
    return option ? this.optionId(option) : `${this.listboxId}-all`;
  });

  constructor() {
    void inject(I18nService)
      .ensureComplete()
      .then(() => this.catalogReady.set(true));
    effect((onCleanup) => {
      const q = this.query().trim();
      if (q.length < SEARCH_MIN_LENGTH) {
        this.debounced.set('');
        return;
      }
      const timer = setTimeout(() => this.debounced.set(q), SEARCH_DEBOUNCE_MS);
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected optionId(option: SearchOption): string {
    return `${this.listboxId}-${option.key.replace(/[^a-zA-Z0-9-]/g, '-')}`;
  }

  protected isActive(option: SearchOption): boolean {
    const index = this.active();
    return index >= 0 && this.options()[index]?.key === option.key;
  }

  protected count(loaded: RemoteResults): number {
    return countHits(loaded.results);
  }

  protected onFocus(): void {
    this.indexWanted.set(true);
    if (this.query().trim()) this.open.set(true);
  }

  onInput(value: string): void {
    this.query.set(value);
    this.activeKey.set(null);
    this.open.set(value.trim().length > 0);
  }

  onKeydown(event: KeyboardEvent): void {
    const total = this.options().length + (this.query().trim() ? 1 : 0);
    switch (event.key) {
      case 'ArrowDown':
      case 'ArrowUp':
        event.preventDefault();
        if (!this.query().trim()) return;
        this.open.set(true);
        this.setActive(moveActive(this.active(), total, event.key === 'ArrowDown' ? 1 : -1));
        // render now, not with the next scheduled change detection: the active option must be in the DOM
        // (aria-activedescendant, aria-selected) once the key event is handled, also while the main thread is busy
        this.changeDetector.detectChanges();
        this.scrollActiveIntoView();
        return;
      case 'Enter': {
        event.preventDefault();
        const option = this.open() ? this.options()[this.active()] : undefined;
        if (option) {
          this.go(option.link);
        } else if (this.query().trim()) {
          this.showAll();
        }
        return;
      }
      case 'Escape':
        if (this.open()) {
          event.preventDefault();
          this.close();
        } else if (this.query()) {
          event.preventDefault();
          this.query.set('');
        }
        return;
      case 'Tab':
        this.close();
        return;
    }
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (!next || !this.host.nativeElement.contains(next)) this.close();
  }

  go(link: string): void {
    this.reset();
    void this.router.navigateByUrl(link);
  }

  showAll(): void {
    const q = this.query().trim();
    this.reset();
    void this.router.navigate(['/search'], { queryParams: { q } });
  }

  private close(): void {
    this.open.set(false);
    this.activeKey.set(null);
  }

  private setActive(index: number): void {
    const options = this.options();
    this.activeKey.set(index < 0 ? null : index < options.length ? options[index].key : ALL_KEY);
  }

  private reset(): void {
    this.close();
    this.query.set('');
    (this.host.nativeElement.querySelector('input') as HTMLInputElement | null)?.blur();
  }

  private scrollActiveIntoView(): void {
    queueMicrotask(() => {
      const id = this.activeOptionId();
      if (id) document.getElementById(id)?.scrollIntoView?.({ block: 'nearest' });
    });
  }
}
