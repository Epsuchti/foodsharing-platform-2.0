import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Select } from '@openng/optimus-ui/select';

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
 */
@Component({
  selector: 'fs-marker-filter-bar',
  imports: [FormsModule, Select],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-testid]': 'testId()' },
  template: `
    <fieldset class="bar">
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
  `,
})
export class MarkerFilterBarComponent {
  readonly filters = input.required<readonly MarkerFilter[]>();
  readonly values = model<Record<string, string>>({});
  /** Legend of the group (already translated). */
  readonly label = input.required<string>();
  readonly testId = input('shared.markerFilter');

  protected set(key: string, value: string | null): void {
    this.values.set({ ...this.values(), [key]: value ?? '' });
  }
}
