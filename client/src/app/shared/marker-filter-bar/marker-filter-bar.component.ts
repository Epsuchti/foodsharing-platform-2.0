import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Select } from '@openng/optimus-ui/select';

import { TPipe } from '../../core/i18n/t.pipe';

export interface MarkerFilterOption {
  value: string;
  /** Already translated. */
  label: string;
}

/** One select of the bar; `value` of the bar holds the chosen option per `key` ('' = no choice, if offered). */
export interface MarkerFilter {
  key: string;
  /** Already translated. */
  label: string;
  options: readonly MarkerFilterOption[];
}

/**
 * `<fs-marker-filter-bar [filters]="…" [(values)]="…" label="…" testId="map.stores.filter" />` (M1): a row of labelled
 * selects that filter a map layer (stores: status, team help, scope, category; users: region, role, activity, home
 * region). Each select has `data-testid="<testId>.<key>"`; the host translates labels and maps the values to its API.
 *
 * `collapseOnPhone` (design review M7 R2, finding 10): below 48rem the selects fold behind one toggle button
 * (`<testId>.toggle`, `aria-expanded`) that names the group and counts the filters differing from `defaults`, so the
 * map starts on the first phone screen. From 48rem the selects are always shown and the toggle is hidden.
 */
@Component({
  selector: 'fs-marker-filter-bar',
  imports: [FormsModule, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-testid]': 'testId()', '[class.collapsible]': 'collapseOnPhone()' },
  template: `
    @if (collapseOnPhone()) {
      <button
        type="button"
        class="toggle"
        [attr.aria-expanded]="open()"
        [attr.aria-controls]="testId() + '-fields'"
        (click)="open.set(!open())"
        [attr.data-testid]="testId() + '.toggle'"
      >
        <i class="pi pi-sliders-h" aria-hidden="true"></i>
        <span class="toggle__label">{{ label() }}</span>
        @if (activeCount() > 0) {
          <span class="toggle__count" [attr.data-testid]="testId() + '.activeCount'">{{ 'shared.markerFilter.active' | t: { count: activeCount() } }}</span>
        }
        <i class="pi toggle__chevron" [class.pi-chevron-down]="!open()" [class.pi-chevron-up]="open()" aria-hidden="true"></i>
      </button>
    }
    <fieldset class="bar" [id]="testId() + '-fields'" [class.bar--closed]="collapseOnPhone() && !open()">
      <legend class="bar__legend">{{ label() }}</legend>
      @for (filter of filters(); track filter.key) {
        <div class="field">
          <label class="field__label" [for]="testId() + '-' + filter.key">{{ filter.label }}</label>
          <p-select
            [inputId]="testId() + '-' + filter.key"
            [options]="$any(filter.options)"
            optionLabel="label"
            optionValue="value"
            [ngModel]="values()[filter.key] ?? ''"
            (ngModelChange)="set(filter.key, $event)"
            appendTo="body"
            [attr.data-testid]="testId() + '.' + filter.key"
          />
        </div>
      }
    </fieldset>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .bar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem 1rem;
      margin: 0;
      padding: 0;
      border: 0;
      min-width: 0;
    }
    .bar__legend {
      width: 100%;
      margin-bottom: 0.25rem;
      padding: 0;
      font-size: 0.9rem;
      font-weight: 700;
    }
    .field {
      display: grid;
      gap: 0.25rem;
      flex: 1 1 11rem;
      min-width: 0;
      max-width: 16rem;
    }
    .field__label {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
      font-weight: 650;
    }
    .field p-select {
      width: 100%;
    }
    .toggle {
      display: none;
    }
    @media (max-width: 47.99rem) {
      :host(.collapsible) .toggle {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        width: 100%;
        min-height: 2.75rem;
        padding: 0.5rem 0.85rem;
        border: 1px solid var(--fs-border-strong);
        border-radius: var(--fs-radius-md);
        background: var(--fs-surface);
        color: var(--fs-text);
        font: inherit;
        font-size: 0.92rem;
        font-weight: 650;
        text-align: left;
        cursor: pointer;
      }
      :host(.collapsible) .toggle[aria-expanded='true'] {
        border-color: var(--fs-primary);
      }
      .toggle:focus-visible {
        outline: 2px solid var(--fs-primary);
        outline-offset: 2px;
      }
      .toggle__label {
        flex: 1;
        min-width: 0;
      }
      .toggle__count {
        padding: 0.1rem 0.55rem;
        border-radius: 999px;
        background: var(--fs-primary-soft);
        color: var(--fs-primary-strong);
        font-size: 0.8rem;
        white-space: nowrap;
      }
      .toggle__chevron {
        color: var(--fs-text-muted);
        font-size: 0.85rem;
      }
      :host(.collapsible) .bar {
        margin-top: 0.75rem;
      }
      :host(.collapsible) .bar__legend {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
        white-space: nowrap;
      }
      .bar--closed {
        display: none;
      }
      :host(.collapsible) .field {
        flex-basis: 100%;
        max-width: none;
      }
    }
  `,
})
export class MarkerFilterBarComponent {
  readonly filters = input.required<readonly MarkerFilter[]>();
  readonly values = model<Record<string, string>>({});
  /** Legend of the group (already translated). */
  readonly label = input.required<string>();
  readonly testId = input('shared.markerFilter');
  /** Below 48rem the selects fold behind a toggle button (see the class comment). */
  readonly collapseOnPhone = input(false);
  /** Values that mean "not filtered" for the toggle's count; keys missing here are not counted. */
  readonly defaults = input<Record<string, string>>({});

  protected readonly open = signal(false);
  protected readonly activeCount = computed(() => {
    const values = this.values();
    return Object.entries(this.defaults()).filter(([key, value]) => (values[key] ?? '') !== value).length;
  });

  protected set(key: string, value: string | null): void {
    this.values.set({ ...this.values(), [key]: value ?? '' });
  }
}
