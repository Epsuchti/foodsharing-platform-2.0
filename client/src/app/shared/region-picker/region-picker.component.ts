import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { InputText } from '@openng/optimus-ui/inputtext';
import { SelectButton } from '@openng/optimus-ui/selectbutton';
import { FormsModule } from '@angular/forms';

import { PublicRegionNode, PublicRegionsService, RegionRef, RegionSuggestion } from '../../api';
import { loadResource } from '../../core/data/data-state';
import { DateTimePipe } from '../../core/dates/date-time.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../data-state';
import { MapMarker, MapViewComponent } from '../map-view/map-view.component';

export type RegionPickerMode = 'postcode' | 'tree' | 'locate';

type SuggestQuery = { postcode: string; country: string } | { lat: number; lon: number; country: string };

/** Nodes matching the search (and their ancestors), for the tree browser. */
export function filterTree(nodes: readonly PublicRegionNode[], query: string): PublicRegionNode[] {
  const needle = query.trim().toLowerCase();
  if (!needle) {
    return [...nodes];
  }
  const result: PublicRegionNode[] = [];
  for (const node of nodes) {
    const children = filterTree(node.children, needle);
    if (node.name.toLowerCase().includes(needle) || children.length > 0) {
      result.push({ ...node, children });
    }
  }
  return result;
}

/**
 * RegionPicker (§12.4; onboarding step 2, settings, admin): find a region by postcode (`suggestRegions`), browse the
 * public tree (`getPublicRegionTree`, only regions with ambassadors are selectable) or locate the device
 * (Geolocation → suggestions by distance, map preview). Emits the chosen region; the page joins it.
 *
 * `<fs-region-picker (regionSelected)="join.run($event)" testId="onboarding.region.picker" />`
 */
@Component({
  selector: 'fs-region-picker',
  imports: [
    ...DATA_STATE_IMPORTS,
    DateTimePipe,
    FormsModule,
    InputText,
    MapViewComponent,
    NgTemplateOutlet,
    SelectButton,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-region-picker', '[attr.data-testid]': 'testId()' },
  template: `
    <p-selectbutton
      [options]="modes()"
      optionLabel="label"
      optionValue="value"
      [ngModel]="mode()"
      (ngModelChange)="setMode($event)"
      [allowEmpty]="false"
      [ariaLabel]="'shared.regionPicker.how' | t"
      [attr.data-testid]="testId() + '.mode'"
    />

    @switch (mode()) {
      @case ('postcode') {
        <div class="field">
          <label [for]="uid + '-postcode'">{{ 'shared.regionPicker.postcodeLabel' | t }}</label>
          <input
            pInputText
            [id]="uid + '-postcode'"
            inputmode="numeric"
            autocomplete="postal-code"
            maxlength="10"
            [placeholder]="'shared.regionPicker.postcodePlaceholder' | t"
            [ngModel]="postcode()"
            (ngModelChange)="postcode.set($event)"
            [attr.data-testid]="testId() + '.postcode'"
          />
          <small class="hint">{{ 'shared.regionPicker.postcodeHint' | t }}</small>
        </div>
        @if (suggestQuery()) {
          <ng-container [ngTemplateOutlet]="suggestionList" />
        }
      }
      @case ('locate') {
        @if (locateError(); as reason) {
          <p class="notice" role="alert" [attr.data-testid]="testId() + '.locateError'">
            <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
            {{ 'shared.regionPicker.locate.' + reason | t }}
          </p>
        } @else if (locating()) {
          <p class="notice" role="status">
            <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
            {{ 'shared.regionPicker.locating' | t }}
          </p>
        }
        @if (position(); as here) {
          <fs-map-view
            [markers]="locationMarker()"
            [center]="[here.lat, here.lon]"
            [zoom]="11"
            height="12rem"
            [ariaLabel]="'shared.regionPicker.mapLabel' | t"
            [testId]="testId() + '.map'"
          />
          <ng-container [ngTemplateOutlet]="suggestionList" />
        }
        <button type="button" class="fs-btn fs-btn--outlined locate" (click)="locate()" [attr.data-testid]="testId() + '.locate'">
          <i class="pi pi-compass" aria-hidden="true"></i>
          {{ (position() ? 'shared.regionPicker.locateAgain' : 'shared.regionPicker.locate') | t }}
        </button>
      }
      @case ('tree') {
        <div class="field">
          <label [for]="uid + '-search'">{{ 'shared.regionPicker.searchLabel' | t }}</label>
          <input
            pInputText
            [id]="uid + '-search'"
            type="search"
            [ngModel]="search()"
            (ngModelChange)="search.set($event)"
            [attr.data-testid]="testId() + '.search'"
          />
        </div>
        <fs-data-state [state]="tree" skeleton="list" [skeletonRows]="4" [compact]="true" [testId]="testId() + '.tree'" [attr.data-testid]="testId() + '.tree'">
          <ng-template [fsData]="tree">
            <ul class="tree" role="tree">
              @for (node of visibleTree(); track node.id) {
                <ng-container [ngTemplateOutlet]="treeNode" [ngTemplateOutletContext]="{ $implicit: node, depth: 0 }" />
              }
            </ul>
            @if (visibleTree().length === 0) {
              <p class="hint">{{ 'shared.regionPicker.noMatch' | t }}</p>
            }
          </ng-template>
        </fs-data-state>
      }
    }

    <ng-template #treeNode let-node let-depth="depth">
      <li role="treeitem" aria-selected="false" [attr.aria-expanded]="node.children.length ? isOpen(node.id) : null">
        <div class="tree__row" [style.padding-left.rem]="depth * 1.1">
          @if (node.children.length) {
            <button type="button" class="tree__toggle" (click)="toggle(node.id)" [attr.aria-label]="(isOpen(node.id) ? 'shared.regionPicker.collapse' : 'shared.regionPicker.expand') | t: { name: node.name }">
              <i class="pi" [class.pi-chevron-right]="!isOpen(node.id)" [class.pi-chevron-down]="isOpen(node.id)" aria-hidden="true"></i>
            </button>
          } @else {
            <span class="tree__spacer"></span>
          }
          @if (node.active) {
            <button type="button" class="tree__pick" (click)="pick(node)" [attr.data-testid]="testId() + '.node.' + node.slug">
              {{ node.name }}
            </button>
          } @else {
            <span class="tree__label">{{ node.name }}</span>
          }
        </div>
        @if (node.children.length && isOpen(node.id)) {
          <ul role="group">
            @for (child of node.children; track child.id) {
              <ng-container [ngTemplateOutlet]="treeNode" [ngTemplateOutletContext]="{ $implicit: child, depth: depth + 1 }" />
            }
          </ul>
        }
      </li>
    </ng-template>

    <ng-template #suggestionList>
      <fs-data-state
        [state]="suggestions"
        skeleton="list"
        [skeletonRows]="2"
        [compact]="true"
        emptyIcon="pi pi-map"
        [emptyTitle]="'shared.regionPicker.noSuggestion' | t"
        [emptyText]="'shared.regionPicker.noSuggestionText' | t"
        [emptyActionLabel]="'shared.regionPicker.browse' | t"
        (emptyAction)="setMode('tree')"
        [testId]="testId() + '.suggestions'"
        [attr.data-testid]="testId() + '.suggestions'"
      >
        <ng-template [fsData]="suggestions" let-list>
          <ul class="suggestions">
            @for (suggestion of list; track suggestion.region.id) {
              <li>
                <button type="button" class="suggestion" (click)="pick(suggestion.region)" [attr.data-testid]="testId() + '.suggestion.' + suggestion.region.slug">
                  <i class="pi pi-map-marker suggestion__icon" aria-hidden="true"></i>
                  <span class="suggestion__text">
                    <strong>{{ suggestion.region.name }}</strong>
                    <span>{{ matchText(suggestion) }}</span>
                    @if (suggestion.nextIntroPickupAt) {
                      <span>{{ 'shared.regionPicker.nextIntro' | t: { date: (suggestion.nextIntroPickupAt | fsDate: 'date') } }}</span>
                    }
                  </span>
                  <i class="pi pi-chevron-right" aria-hidden="true"></i>
                </button>
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    </ng-template>
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    .field {
      display: grid;
      gap: 0.35rem;
    }
    .field label {
      font-weight: 650;
    }
    .field input {
      width: 100%;
      max-width: 22rem;
    }
    .hint {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .notice {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      color: var(--fs-text-muted);
    }
    .notice[role='alert'] {
      color: var(--fs-warning-text);
      font-weight: 600;
    }
    .locate {
      justify-self: start;
    }
    .suggestions,
    .tree,
    .tree ul {
      display: grid;
      gap: 0.4rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .suggestion {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      width: 100%;
      padding: 0.8rem 1rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .suggestion:hover,
    .suggestion:focus-visible {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
    }
    .suggestion__icon {
      color: var(--fs-primary-strong);
    }
    .suggestion__text {
      display: grid;
      flex: 1;
      min-width: 0;
      gap: 0.1rem;
    }
    .suggestion__text span {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .tree {
      gap: 0.1rem;
    }
    /* long trees scroll inside the picker instead of pushing the page */
    ul.tree[role='tree'] {
      max-height: 22rem;
      overflow: auto;
      padding-right: 0.25rem;
    }
    .tree__row {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      min-height: 2.25rem;
    }
    .tree__toggle,
    .tree__spacer {
      display: grid;
      width: 1.75rem;
      height: 1.75rem;
      flex: 0 0 auto;
      place-items: center;
    }
    .tree__toggle {
      border: 0;
      border-radius: var(--fs-radius-sm);
      background: none;
      color: var(--fs-text-muted);
      cursor: pointer;
    }
    .tree__pick {
      padding: 0.3rem 0.6rem;
      border: 0;
      border-radius: var(--fs-radius-sm);
      background: none;
      color: var(--fs-primary-strong);
      font: inherit;
      font-weight: 650;
      text-align: left;
      cursor: pointer;
    }
    .tree__pick:hover,
    .tree__pick:focus-visible {
      background: var(--fs-primary-soft);
    }
    .tree__label {
      padding: 0.3rem 0.6rem;
      color: var(--fs-text-muted);
    }
  `,
})
export class RegionPickerComponent {
  private readonly regionsApi = inject(PublicRegionsService);
  private readonly i18n = inject(I18nService);
  private static nextId = 0;

  readonly country = input('CH');
  readonly initialMode = input<RegionPickerMode>('postcode');
  /** Root of the tree browser (default: everything). */
  readonly rootSlug = input<string | undefined>(undefined);
  readonly testId = input('shared.regionPicker');
  readonly regionSelected = output<RegionRef>();

  protected readonly uid = `fs-region-picker-${RegionPickerComponent.nextId++}`;
  private readonly chosenMode = signal<RegionPickerMode | null>(null);
  protected readonly mode = computed(() => this.chosenMode() ?? this.initialMode());
  protected readonly modes = computed(() => {
    this.i18n.language();
    return (['postcode', 'tree', 'locate'] as const).map((value) => ({
      value,
      label: this.i18n.t(`shared.regionPicker.mode.${value}`),
    }));
  });

  protected readonly postcode = signal('');
  protected readonly position = signal<{ lat: number; lon: number } | null>(null);
  protected readonly locating = signal(false);
  protected readonly locateError = signal<'denied' | 'unavailable' | 'unsupported' | null>(null);
  protected readonly search = signal('');
  private readonly open = signal<ReadonlySet<string>>(new Set());

  /** Postcodes from 4 digits (CH/AT; DE has 5); a located position. */
  protected readonly suggestQuery = computed<SuggestQuery | undefined>(() => {
    if (this.mode() === 'locate') {
      const here = this.position();
      return here ? { ...here, country: this.country() } : undefined;
    }
    const postcode = this.postcode().trim();
    return /^\d{4,5}$/.test(postcode) ? { postcode, country: this.country() } : undefined;
  });

  protected readonly suggestions = loadResource<RegionSuggestion[], SuggestQuery>({
    params: () => this.suggestQuery(),
    loader: (query) => this.regionsApi.suggestRegions(query),
  });

  private readonly treeRequested = signal(false);
  protected readonly tree = loadResource<PublicRegionNode[], { rootSlug?: string }>({
    params: () => (this.treeRequested() ? { rootSlug: this.rootSlug() } : undefined),
    loader: ({ rootSlug }) => this.regionsApi.getPublicRegionTree(rootSlug ? { rootSlug } : {}),
  });

  protected readonly visibleTree = computed(() => filterTree(this.tree.data() ?? [], this.search()));

  protected readonly locationMarker = computed<MapMarker[]>(() => {
    const here = this.position();
    return here
      ? [{ id: 'here', kind: 'home', lat: here.lat, lon: here.lon, label: this.i18n.t('shared.regionPicker.here') }]
      : [];
  });

  constructor() {
    // the first level of the tree is open (a single collapsed root would hide everything)
    effect(() => {
      const roots = this.tree.data();
      if (roots?.length) {
        untracked(() => this.open.set(new Set([...this.open(), ...roots.map((node) => node.id)])));
      }
    });
    // the tree loads only when it is shown (it is the largest response)
    effect(() => {
      if (this.mode() === 'tree') {
        untracked(() => this.treeRequested.set(true));
      }
    });
  }

  protected setMode(mode: RegionPickerMode): void {
    this.chosenMode.set(mode);
    if (mode === 'tree') {
      this.treeRequested.set(true);
    }
    if (mode === 'locate' && !this.position()) {
      this.locate();
    }
  }

  protected isOpen(id: string): boolean {
    return this.open().has(id) || this.search().trim().length > 0;
  }

  protected toggle(id: string): void {
    const next = new Set(this.open());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.open.set(next);
  }

  protected pick(region: RegionRef | PublicRegionNode): void {
    const { id, slug, name, type, parentId } = region;
    this.regionSelected.emit({ id, slug, name, type, parentId });
  }

  protected matchText(suggestion: RegionSuggestion): string {
    if (suggestion.matchedBy === 'POSTCODE') {
      return this.i18n.t('shared.regionPicker.byPostcode');
    }
    return this.i18n.t('shared.regionPicker.byDistance', {
      km: Math.round(suggestion.distanceKm ?? 0),
    });
  }

  /** Geolocation → suggestions by distance. */
  protected locate(): void {
    const geo = globalThis.navigator?.geolocation;
    if (!geo) {
      this.locateError.set('unsupported');
      return;
    }
    this.locateError.set(null);
    this.locating.set(true);
    geo.getCurrentPosition(
      (result) => {
        this.locating.set(false);
        this.position.set({ lat: result.coords.latitude, lon: result.coords.longitude });
      },
      (error) => {
        this.locating.set(false);
        this.locateError.set(error.code === error.PERMISSION_DENIED ? 'denied' : 'unavailable');
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 600_000 },
    );
  }
}
