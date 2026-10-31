import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { map } from 'rxjs';

import { SearchKind, SearchResults, SearchService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { SEARCH_GROUPS, SEARCH_MIN_LENGTH, countHits, resultGroups } from '../search.util';

/**
 * /search?q= (C5): the full server search (`search`, up to 20 hits per kind, only what the user may see, A55),
 * grouped by kind with filter chips. The header search sends Enter here; the query lives in the URL.
 */
@Component({
  selector: 'fs-search-page',
  imports: [...DATA_STATE_IMPORTS, Button, InputText, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'search.page.title' | t" [subtitle]="'search.page.subtitle' | t" icon="pi pi-search" />

    <form class="bar" role="search" (submit)="submit($event)" [attr.aria-label]="'search.page.title' | t">
      <label class="fs-visually-hidden" for="search-page-input">{{ 'search.page.label' | t }}</label>
      <input
        pInputText
        id="search-page-input"
        type="search"
        name="q"
        maxlength="100"
        autocomplete="off"
        [value]="draft()"
        (input)="draft.set($any($event.target).value)"
        [placeholder]="'search.global.placeholder' | t"
        data-testid="search.page.input"
      />
      <p-button type="submit" [label]="'search.page.submit' | t" icon="pi pi-search" data-testid="search.page.submit" />
    </form>

    <div class="kinds" role="group" [attr.aria-label]="'search.page.filter' | t">
      @for (group of groups; track group.kind) {
        <button
          type="button"
          class="kind"
          [class.kind--on]="kinds().has(group.kind)"
          [attr.aria-pressed]="kinds().has(group.kind)"
          [attr.data-kind]="group.kind"
          (click)="toggleKind(group.kind)"
          data-testid="search.page.kind"
        >
          <i [class]="group.icon" aria-hidden="true"></i>
          {{ 'search.group.' + group.key | t }}
          @if (results.data(); as loaded) {
            <span class="kind__count">{{ loaded[group.key].length }}</span>
          }
        </button>
      }
    </div>

    @if (q().length < minLength) {
      <p class="hint" data-testid="search.page.hint">{{ 'search.page.hint' | t: { min: minLength } }}</p>
    } @else {
      <fs-data-state
        [state]="results"
        skeleton="list"
        [emptyTitle]="'search.page.empty' | t: { query: q() }"
        [emptyText]="'search.page.emptyHint' | t"
        emptyIcon="pi pi-search"
        data-testid="search.page.state"
      >
        <ng-template [fsData]="results" let-loaded>
          @let shown = visibleGroups(loaded);
          @if (shown.length) {
            @for (group of shown; track group.id) {
              <section class="group" [attr.data-group]="group.id" data-testid="search.page.group">
                <h2 class="group__title">{{ group.labelKey | t }} <span class="group__count">{{ group.options.length }}</span></h2>
                <ul class="hits">
                  @for (hit of group.options; track hit.key) {
                    <li>
                      <a class="hit" [routerLink]="hit.link" [attr.data-link]="hit.link" data-testid="search.page.hit">
                        @if (hit.imageUrl) {
                          <img class="hit__img" [src]="hit.imageUrl" alt="" loading="lazy" />
                        } @else {
                          <i [class]="hit.icon + ' hit__icon'" aria-hidden="true"></i>
                        }
                        <span class="hit__text">
                          <span class="hit__title">{{ hit.title }}</span>
                          @if (hit.subtitle) {
                            <span class="hit__sub">{{ hit.subtitle }}</span>
                          }
                        </span>
                        <i class="pi pi-angle-right hit__go" aria-hidden="true"></i>
                      </a>
                    </li>
                  }
                </ul>
              </section>
            }
          } @else {
            <p class="hint" data-testid="search.page.filteredEmpty">{{ 'search.page.filteredEmpty' | t }}</p>
          }
        </ng-template>
      </fs-data-state>
    }
  `,
  styles: `
    /* minmax(0, 1fr): the single column never grows past the page because of a child's intrinsic width
       (WebKit sizes the search input and the hit rows wider than Chromium and stretched the whole page by 20px) */
    :host { display: grid; grid-template-columns: minmax(0, 1fr); gap: 1rem; }
    .bar { display: flex; gap: 0.5rem; max-width: 40rem; }
    .bar input { flex: 1; min-width: 0; }
    .kinds { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .kind {
      display: inline-flex; align-items: center; gap: 0.4rem; min-height: 2.5rem; padding: 0.3rem 0.8rem;
      border: 1px solid var(--fs-border); border-radius: 999px; background: var(--fs-surface); color: var(--fs-text);
      font: inherit; font-size: 0.88rem; font-weight: 600; cursor: pointer;
    }
    .kind:hover { border-color: var(--fs-border-strong); }
    .kind--on { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .kind:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
    .kind__count { padding: 0 0.4rem; border-radius: 999px; background: var(--fs-surface-muted); color: var(--fs-text-muted); font-size: 0.75rem; }
    .hint { margin: 0; color: var(--fs-text-muted); }
    .group + .group { margin-top: 1.25rem; }
    .group__title { display: flex; align-items: center; gap: 0.5rem; margin: 0 0 0.5rem; font-size: 1.05rem; }
    .group__count { padding: 0 0.5rem; border-radius: 999px; background: var(--fs-surface-muted); color: var(--fs-text-muted); font-size: 0.78rem; }
    .hits { display: grid; gap: 0.4rem; margin: 0; padding: 0; list-style: none; grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr)); }
    .hit {
      display: flex; align-items: center; gap: 0.75rem; min-height: 3rem; padding: 0.5rem 0.75rem;
      border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md); background: var(--fs-surface);
      color: var(--fs-text); text-decoration: none;
    }
    .hit:hover, .hit:focus-visible { border-color: var(--fs-primary); background: var(--fs-primary-soft); }
    .hit__img { width: 2.25rem; height: 2.25rem; border-radius: 50%; object-fit: cover; }
    .hit__icon { width: 2.25rem; color: var(--fs-text-muted); font-size: 1.1rem; text-align: center; }
    .hit__text { display: grid; flex: 1; min-width: 0; }
    .hit__title { overflow: hidden; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
    .hit__sub { overflow: hidden; color: var(--fs-text-muted); font-size: 0.82rem; text-overflow: ellipsis; white-space: nowrap; }
    .hit__go { color: var(--fs-text-muted); }
  `,
})
export class SearchPageComponent {
  private readonly api = inject(SearchService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly groups = SEARCH_GROUPS;
  protected readonly minLength = SEARCH_MIN_LENGTH;

  /** The query in the URL (`?q=`). */
  protected readonly q = toSignal(this.route.queryParamMap.pipe(map((params) => (params.get('q') ?? '').trim())), {
    initialValue: '',
  });
  /** The text in the field (submitted into the URL). */
  protected readonly draft = signal('');
  protected readonly kinds = signal<ReadonlySet<SearchKind>>(new Set());

  protected readonly results = loadResource<SearchResults, string>({
    params: () => (this.q().length >= SEARCH_MIN_LENGTH ? this.q() : undefined),
    loader: (q) => this.api.search({ q }),
    isEmpty: (value) => countHits(value) === 0,
  });

  protected readonly visibleGroups = (loaded: SearchResults) => resultGroups(loaded, new Set(), this.kinds());

  constructor() {
    effect(() => this.draft.set(this.q()));
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const q = this.draft().trim();
    if (q === this.q()) {
      // same query again: search again (new content may have been indexed)
      if (q.length >= SEARCH_MIN_LENGTH) this.results.reload();
      return;
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams: { q: q || null } });
  }

  protected toggleKind(kind: SearchKind): void {
    this.kinds.update((current) => {
      const next = new Set(current);
      if (next.has(kind)) next.delete(kind);
      else next.add(kind);
      return next;
    });
  }
}
