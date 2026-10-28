import { ChangeDetectionStrategy, Component, computed, inject, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';

import { RegularPickup, Weekday } from '../../api';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';

export const WEEKDAYS: readonly Weekday[] = Object.values(Weekday);

/** Keys `weekday|HH:mm` that occur more than once (DUPLICATE_PICKUP_DAY_TIME, PU1). */
export function duplicateRows(rows: readonly RegularPickup[]): Set<number> {
  const seen = new Map<string, number>();
  const duplicates = new Set<number>();
  rows.forEach((row, index) => {
    const key = `${row.weekday}|${row.startTime}`;
    if (seen.has(key)) {
      duplicates.add(index);
      duplicates.add(seen.get(key)!);
    } else {
      seen.set(key, index);
    }
  });
  return duplicates;
}

/**
 * Editor rows of a regular pickup schedule (PU1): weekday, local start time, slot count (0–50), description (≤ 100).
 * Duplicate weekday/time rows are marked inline.
 *
 * ```html
 * <fs-weekday-time-picker [(rows)]="schedule" testId="stores.regular" />
 * ```
 */
@Component({
  selector: 'fs-weekday-time-picker',
  imports: [Button, FormsModule, InputText, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rows">
      @for (row of rows(); track $index; let i = $index) {
        <div class="row" [class.row--duplicate]="duplicates().has(i)" [attr.data-testid]="testId() + '.row'">
          <div class="field">
            <p-select
              [options]="weekdayOptions()"
              optionLabel="label"
              optionValue="value"
              [ngModel]="row.weekday"
              (ngModelChange)="update(i, { weekday: $event })"
              [ariaLabel]="'shared.weekdayTime.weekday' | t"
              [attr.data-testid]="testId() + '.weekday'"
            />
          </div>
          <label class="field">
            <span class="sr-only">{{ 'shared.weekdayTime.time' | t }}</span>
            <input
              pInputText
              type="text"
              inputmode="numeric"
              maxlength="5"
              size="5"
              placeholder="HH:MM"
              [class.invalid]="!validTime(row.startTime)"
              [value]="row.startTime"
              (input)="update(i, { startTime: value($event) })"
              [attr.aria-label]="'shared.weekdayTime.time' | t"
              [attr.data-testid]="testId() + '.time'"
            />
          </label>
          <div class="field field--slots">
            <span class="field__label" aria-hidden="true">{{ 'shared.weekdayTime.slots' | t }}</span>
            <input
              pInputText
              type="number"
              min="0"
              max="50"
              class="slots"
              [value]="row.slotCount"
              (input)="update(i, { slotCount: slots($event) })"
              [attr.aria-label]="'shared.weekdayTime.slots' | t"
              [attr.data-testid]="testId() + '.slots'"
            />
          </div>
          <label class="field field--grow">
            <span class="sr-only">{{ 'shared.weekdayTime.description' | t }}</span>
            <input
              pInputText
              maxlength="100"
              [placeholder]="'shared.weekdayTime.description' | t"
              [value]="row.description ?? ''"
              (input)="update(i, { description: value($event) })"
              [attr.data-testid]="testId() + '.description'"
            />
          </label>
          <p-button
            icon="pi pi-trash"
            severity="danger"
            [text]="true"
            [rounded]="true"
            [ariaLabel]="'shared.weekdayTime.remove' | t"
            (onClick)="remove(i)"
            [attr.data-testid]="testId() + '.remove'"
          />
          @if (duplicates().has(i)) {
            <small class="error" role="alert" [attr.data-testid]="testId() + '.duplicate'">{{ 'errors.DUPLICATE_PICKUP_DAY_TIME' | t }}</small>
          }
        </div>
      } @empty {
        <p class="muted">{{ 'shared.weekdayTime.empty' | t }}</p>
      }
    </div>
    <p-button
      [label]="'shared.weekdayTime.add' | t"
      icon="pi pi-plus"
      severity="secondary"
      [outlined]="true"
      size="small"
      (onClick)="add()"
      [attr.data-testid]="testId() + '.add'"
    />
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; }
    .rows { display: grid; gap: 0.5rem; }
    .row {
      display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; padding: 0.5rem;
      border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md, 0.6rem);
    }
    .row--duplicate { border-color: var(--fs-danger); }
    .field { display: inline-flex; gap: 0.35rem; align-items: center; }
    .field--grow { flex: 1 1 10rem; }
    .field--grow input { width: 100%; }
    .field__label { color: var(--fs-text-muted); font-size: 0.85rem; }
    .slots { width: 4.5rem; }
    input.invalid { border-color: var(--fs-danger); }
    .error { flex-basis: 100%; color: var(--fs-danger); font-weight: 600; }
    .muted { margin: 0; color: var(--fs-text-muted); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `,
})
export class WeekdayTimePickerComponent {
  private readonly i18n = inject(I18nService);

  readonly rows = model<RegularPickup[]>([]);
  readonly testId = input('shared.weekdayTime');

  protected readonly duplicates = computed(() => duplicateRows(this.rows()));
  protected readonly weekdayOptions = computed(() => {
    this.i18n.language();
    return WEEKDAYS.map((value) => ({ value, label: this.i18n.t(`shared.weekday.${value}`) }));
  });

  protected update(index: number, change: Partial<RegularPickup>): void {
    this.rows.update((rows) => rows.map((row, i) => (i === index ? { ...row, ...change } : row)));
  }

  protected validTime(time: string): boolean {
    return /^([01]\d|2[0-3]):[0-5]\d$/.test(time);
  }

  protected slots(event: Event): number {
    const value = Number((event.target as HTMLInputElement).value);
    return Number.isFinite(value) ? Math.min(50, Math.max(0, Math.round(value))) : 0;
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected add(): void {
    this.rows.update((rows) => [...rows, { weekday: Weekday.Monday, startTime: '18:00', slotCount: 2 }]);
  }

  protected remove(index: number): void {
    this.rows.update((rows) => rows.filter((_, i) => i !== index));
  }
}
