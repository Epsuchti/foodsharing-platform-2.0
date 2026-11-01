import {
  Directive,
  ElementRef,
  afterRenderEffect,
  booleanAttribute,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor } from '@angular/forms';

import { I18nService } from '../../core/i18n/i18n.service';
import { OptimusLocaleService, calendarNames } from '../../core/i18n/optimus-locale';
import { withinRange } from './date-input.util';

let nextId = 0;

/**
 * Shared logic of `fs-date-input`, `fs-datetime-input` and `fs-time-input` (decision 150): a `p-datepicker` with a
 * typed field and a calendar behind the icon. The form value is an ISO string (see `date-input.util.ts`); typed text
 * is read on blur/Enter, calendar picks right away. Text that can't be read shows an inline error and keeps the last
 * valid value. `min`/`max` limit the calendar; a typed value outside them is kept (as native inputs did, so the
 * feature rules still give their own message) and only marks the field invalid.
 */
@Directive()
export abstract class DateInputBase implements ControlValueAccessor {
  protected readonly i18n = inject(I18nService);
  private readonly optimusLocale = inject(OptimusLocaleService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** `id` of the inner text field, so an outer `<label for>` keeps working. */
  readonly inputId = input<string>();
  readonly min = input<string | null | undefined>();
  readonly max = input<string | null | undefined>();
  readonly required = input(false, { transform: booleanAttribute });
  readonly ariaLabel = input<string>();
  readonly ariaInvalid = input<boolean | string | null | undefined>();
  readonly ariaDescribedBy = input<string>();
  /** Passed to the inner field (e.g. `bday`); the date picker otherwise turns autocomplete off. */
  readonly autocomplete = input<string>();
  readonly placeholder = input<string>();
  /** Rendered as `data-testid` on the host element. */
  readonly testId = input<string>();

  /** The form value. */
  private readonly value = signal('');
  protected readonly errorId = `fs-date-input-error-${nextId++}`;
  /** Date shown by the picker. A new object on every change so the picker always re-renders it. */
  protected readonly pickerValue = signal<Date | null>(null);
  protected readonly disabled = signal(false);
  protected readonly parseError = signal(false);
  protected readonly invalid = computed(
    () => this.parseError() || isTrue(this.ariaInvalid()) || (!!this.value() && !withinRange(this.value(), this.min(), this.max())),
  );
  protected readonly minDate = computed(() => this.toDate(this.min()));
  protected readonly maxDate = computed(() => this.toDate(this.max()));
  protected readonly placeholderText = computed(() => {
    this.i18n.language();
    return this.placeholder() ?? this.defaultPlaceholder();
  });
  protected readonly invalidText = computed(() => {
    this.i18n.language();
    return this.i18n.t('common.dateInput.invalid', { format: this.defaultPlaceholder() });
  });
  protected readonly openCalendarText = computed(() => {
    this.i18n.language();
    return this.i18n.t('common.dateInput.openCalendar');
  });

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;
  private namesReady = false;

  constructor() {
    // the date picker renders its own field: pass the a11y attributes it has no input for
    afterRenderEffect(() => {
      const field = this.field();
      if (!field) {
        return;
      }
      const describedBy = [this.ariaDescribedBy(), this.parseError() ? this.errorId : undefined]
        .filter(Boolean)
        .join(' ');
      setAttribute(field, 'aria-describedby', describedBy || null);
      setAttribute(field, 'aria-invalid', this.invalid() ? 'true' : null);
      setAttribute(field, 'autocomplete', this.autocomplete() ?? 'off');
    });
  }

  /** Mode flags of the picker. */
  protected abstract readonly timeOnly: boolean;
  protected abstract readonly showTime: boolean;
  /** Mode: text of the field → value (`''` blank, `null` unreadable). */
  protected abstract parse(text: string): string | null;
  /** Mode: value → picker date. */
  protected abstract toDate(value: string | null | undefined): Date | null;
  /** Mode: picker date → value. */
  protected abstract fromDate(date: Date): string;
  /** Mode: format hint, also the default placeholder. */
  protected abstract defaultPlaceholder(): string;

  writeValue(value: string | null | undefined): void {
    this.value.set(value ?? '');
    this.parseError.set(false);
    this.pickerValue.set(this.toDate(this.value()));
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  /** Builds the calendar names of the UI language before the first open (decision 123 defers them to idle). */
  protected onFocus(): void {
    if (this.namesReady) {
      return;
    }
    this.namesReady = true;
    calendarNames(this.i18n.locale());
    this.optimusLocale.start();
  }

  /** Reads the typed text (blur, Enter). */
  protected commitText(): void {
    const field = this.field();
    if (!field) {
      return;
    }
    const parsed = this.parse(field.value);
    if (parsed === null) {
      this.parseError.set(true);
      return;
    }
    this.parseError.set(false);
    this.set(parsed);
    // re-render in the display format (e.g. `1.2.26` → `01.02.2026`)
    this.pickerValue.set(this.toDate(parsed));
  }

  protected onBlur(): void {
    this.commitText();
    this.onTouched();
  }

  /** Calendar day, Today button or time spinner. */
  protected onSelect(date: Date): void {
    this.parseError.set(false);
    this.set(this.fromDate(date));
    this.pickerValue.set(new Date(date.getTime()));
  }

  /** Clear icon or Clear button. */
  protected onClear(): void {
    this.parseError.set(false);
    this.set('');
    this.pickerValue.set(null);
  }

  private set(value: string): void {
    if (value !== this.value()) {
      this.value.set(value);
      this.onChange(value);
    }
  }

  private field(): HTMLInputElement | null {
    return this.host.nativeElement.querySelector('input');
  }
}

function isTrue(value: boolean | string | null | undefined): boolean {
  return value === true || value === 'true';
}

function setAttribute(element: HTMLElement, name: string, value: string | null): void {
  if (value === null) {
    element.removeAttribute(name);
  } else if (element.getAttribute(name) !== value) {
    element.setAttribute(name, value);
  }
}
