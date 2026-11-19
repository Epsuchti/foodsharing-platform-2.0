import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  HostAttributeToken,
  TemplateRef,
  computed,
  contentChild,
  inject,
  input,
  output,
} from '@angular/core';

import { DataState } from '../../core/data/data-state';
import { TPipe } from '../../core/i18n/t.pipe';
import {
  DataTemplateDirective,
  EmptyTemplateDirective,
  ErrorTemplateDirective,
  SkeletonTemplateDirective,
} from './data-state.directives';
import { EmptyIllustration, EmptyStateComponent } from './empty-state.component';
import { ErrorStateComponent } from './error-state.component';
import { SkeletonPreset, SkeletonPresetComponent } from './skeleton-presets.component';

/**
 * Renders every state of a DataState (plan §12.2): skeleton while loading, content on success, inline spinner
 * while refreshing, empty state, translated error with retry (+ trace id), forbidden, not found and offline.
 * Sets `data-state="<status>"` on the host for tests.
 *
 * ```html
 * <fs-data-state [state]="sessions" skeleton="table" [emptyTitle]="'account.sessions.empty' | t"
 *                data-testid="account.sessions.list">
 *   <ng-template [fsData]="sessions" let-list> @for (s of list; track s.id) { … } </ng-template>
 * </fs-data-state>
 * ```
 */
@Component({
  selector: 'fs-data-state',
  imports: [
    NgTemplateOutlet,
    EmptyStateComponent,
    ErrorStateComponent,
    SkeletonPresetComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-data-state',
    '[class.fs-data-state--compact]': 'compact()',
    '[attr.data-state]': 'status()',
    '[attr.aria-busy]': 'state().isLoading()',
  },
  template: `
    @switch (status()) {
      @case ('loading') {
        <div class="ds__skeleton" role="status" [attr.aria-label]="'shared.dataState.loading' | t">
          @if (skeletonTemplate(); as tpl) {
            <ng-container [ngTemplateOutlet]="tpl" />
          } @else {
            <fs-skeleton [preset]="skeletonPreset()" [rows]="skeletonRows()" />
          }
        </div>
      }
      @case ('empty') {
        @if (emptyTpl(); as empty) {
          <ng-container [ngTemplateOutlet]="empty.template" />
        } @else {
          <fs-empty-state
            [icon]="emptyIcon()"
            [illustration]="emptyIllustration()"
            [title]="emptyTitle() ?? ('shared.dataState.emptyTitle' | t)"
            [text]="emptyText()"
            [actionLabel]="emptyActionLabel()"
            [actionIcon]="emptyActionIcon()"
            [compact]="compact()"
            [testId]="testIdBase()"
            (action)="emptyAction.emit()"
          />
        }
      }
      @case ('idle') {}
      @default {
        @if (showError()) {
          @if (errorTpl(); as custom) {
            <ng-container
              [ngTemplateOutlet]="custom.template"
              [ngTemplateOutletContext]="{
                $implicit: state().error(),
                error: state().error(),
                retry: retry,
              }"
            />
          } @else {
            <fs-error-state
              [error]="state().error()"
              [kind]="status() === 'offline' ? 'offline' : undefined"
              [retryable]="status() === 'error' || status() === 'offline' ? true : undefined"
              [askWho]="askWho()"
              [backLink]="backLink()"
              [compact]="compact() || hasData()"
              [testId]="testIdBase()"
              (retry)="retry()"
            />
          }
        }
        @if (showContent()) {
          @if (status() === 'refreshing') {
            <div class="ds__refreshing" role="status">
              <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
              <span>{{ 'shared.dataState.refreshing' | t }}</span>
            </div>
          }
          <div class="ds__content" [class.ds__content--stale]="state().isStale()">
            @if (dataTpl(); as tpl) {
              <ng-container
                [ngTemplateOutlet]="tpl.template"
                [ngTemplateOutletContext]="{ $implicit: state().data(), data: state().data() }"
              />
            }
            <ng-content />
          </div>
        }
      }
    }
  `,
  styles: `
    :host {
      position: relative;
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    :host(.fs-data-state--compact) {
      gap: 0.5rem;
    }
    .ds__refreshing {
      position: absolute;
      z-index: 1;
      top: -0.5rem;
      right: 0;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.25rem 0.65rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-sm);
      color: var(--fs-text-muted);
      font-size: 0.78rem;
      font-weight: 600;
    }
    /*
     * The content is a one-column grid that takes the host's gap (1rem, compact 0.5rem; a page sets another one with
     * "fs-data-state { gap: … }"). Pages lay out their cards with ":host { display: grid; gap }", but the cards sit in
     * this wrapper, so before it was a grid they touched each other (design review A5, B2, C1, D1). The grid track is
     * "minmax(0, 1fr)" so a wide child cannot widen the page.
     */
    .ds__content {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: inherit;
      min-width: 0;
      transition: filter 200ms ease;
    }
    /*
     * Side effects of the grid, neutralised with zero specificity so page rules still win:
     * - inline-level children (a link button, a select) keep their own width instead of stretching to the row;
     * - browser margins of text blocks between siblings no longer add to the gap (block flow collapsed them);
     * - dialog/popover hosts (empty or only a fixed overlay) do not become rows with a gap of their own;
     * - a page header inside the content keeps its usual distance to the first card.
     */
    :host ::ng-deep :where(.ds__content) > :where(a, button, span, label, img, p-button, p-select, p-multiselect) {
      justify-self: start;
    }
    :host ::ng-deep :where(.ds__content) > :where(p, h1, h2, h3, h4, ul, ol, dl):where(:not(:first-child)) {
      margin-top: 0;
    }
    :host ::ng-deep :where(.ds__content) > :where(p, h1, h2, h3, h4, ul, ol, dl):where(:not(:last-child)) {
      margin-bottom: 0;
    }
    :host ::ng-deep :where(.ds__content) > :where(p-dialog, p-popover, p-drawer, p-confirmdialog, p-toast):where(:not(:has(*))) {
      display: none;
    }
    :host ::ng-deep :where(.ds__content) > :where(p-dialog, p-popover, p-drawer, p-confirmdialog, p-toast):where(:has(*)),
    :host ::ng-deep :where(.ds__content) > :where(:has(> :is(p-dialog, p-popover, p-drawer, p-confirmdialog))):where(:not(:has(> :not(p-dialog, p-popover, p-drawer, p-confirmdialog)))) {
      display: contents;
    }
    :host ::ng-deep :where(.ds__content) > fs-page-header {
      margin-bottom: 0.5rem;
    }
    @media (max-width: 47.99rem) {
      :host ::ng-deep :where(.ds__content) > fs-page-header {
        margin-bottom: 0.1rem;
      }
    }
    /* Stale data is desaturated instead of faded: fading drops muted text below WCAG AA contrast. */
    .ds__content--stale {
      filter: grayscale(0.7);
    }
  `,
})
export class DataStateComponent {
  readonly state = input.required<DataState<unknown>>();
  readonly skeleton = input<SkeletonPreset | TemplateRef<unknown>>('list');
  readonly skeletonRows = input<number | undefined>(undefined);
  readonly emptyTitle = input<string | undefined>(undefined);
  readonly emptyText = input<string | undefined>(undefined);
  readonly emptyIcon = input('pi pi-inbox');
  readonly emptyIllustration = input<EmptyIllustration | undefined>(undefined);
  readonly emptyActionLabel = input<string | undefined>(undefined);
  readonly emptyActionIcon = input<string | undefined>(undefined);
  /** Translated "who to ask" hint for 403. */
  readonly askWho = input<string | undefined>(undefined);
  /** Back link for 404/403. */
  readonly backLink = input<string | unknown[] | undefined>(undefined);
  /** Small variant for table bodies, select overlays and cards. */
  readonly compact = input(false);
  readonly emptyAction = output<void>();
  /** Prefix for the inner test ids (retry, empty action) when the host's `data-testid` is bound dynamically. */
  readonly testId = input<string | undefined>(undefined);

  private readonly hostTestId = inject(new HostAttributeToken('data-testid'), { optional: true });

  protected readonly dataTpl = contentChild(DataTemplateDirective);
  protected readonly emptyTpl = contentChild(EmptyTemplateDirective);
  protected readonly errorTpl = contentChild(ErrorTemplateDirective);
  private readonly skeletonTpl = contentChild(SkeletonTemplateDirective);

  protected readonly status = computed(() => this.state().status());
  protected readonly hasData = computed(() => this.state().data() !== undefined);
  protected readonly testIdBase = computed(() => this.testId() ?? this.hostTestId ?? undefined);

  protected readonly skeletonTemplate = computed<TemplateRef<unknown> | null>(() => {
    const custom = this.skeletonTpl()?.template;
    if (custom) {
      return custom;
    }
    const skeleton = this.skeleton();
    return skeleton instanceof TemplateRef ? skeleton : null;
  });
  protected readonly skeletonPreset = computed<SkeletonPreset>(() => {
    const skeleton = this.skeleton();
    return skeleton instanceof TemplateRef ? 'list' : skeleton;
  });

  /** Error/offline message; with cached data it is shown compact above the (stale) content. */
  protected readonly showError = computed(() =>
    ['error', 'forbidden', 'notFound', 'offline'].includes(this.status()),
  );

  protected readonly showContent = computed(() => {
    const status = this.status();
    if (status === 'success' || status === 'refreshing') {
      return true;
    }
    return (status === 'error' || status === 'offline') && this.hasData();
  });

  protected readonly retry = () => this.state().reload();
}
