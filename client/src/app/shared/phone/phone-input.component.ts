import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { InputText } from '@openng/optimus-ui/inputtext';

import { TPipe } from '../../core/i18n/t.pipe';
import {
  formatPhoneNumber,
  isSwissMobile,
  isValidPhoneNumber,
  normalizePhoneNumber,
} from '../../core/validation/phone-number';

let nextId = 0;

/**
 * Phone number field (EA4/A4): accepts what people type (`079 123 45 67`, `+41 79…`, `0049…`), validates with the
 * server's rules (core/validation/phone-number.ts) and writes the E.164 form (`+41791234567`) to the form control.
 * Shows the recognised number below the field. `<fs-phone-input formControlName="mobilePhone" [mobile]="true" />`
 */
@Component({
  selector: 'fs-phone-input',
  imports: [InputText, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PhoneInputComponent), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => PhoneInputComponent), multi: true },
  ],
  host: { class: 'fs-phone-input' },
  template: `
    <input
      pInputText
      type="tel"
      autocomplete="tel"
      inputmode="tel"
      [id]="inputId()"
      [value]="text()"
      [disabled]="disabled()"
      [attr.aria-invalid]="showError()"
      [attr.aria-describedby]="inputId() + '-hint'"
      [placeholder]="(mobile() ? 'shared.phone.placeholderMobile' : 'shared.phone.placeholder') | t"
      (input)="onInput($any($event.target).value)"
      (blur)="onBlur()"
      [attr.data-testid]="testId()"
    />
    <small class="hint" [id]="inputId() + '-hint'" [class.hint--error]="showError()">
      @if (showError()) {
        <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
        <span>{{ 'shared.phone.invalid' | t }}</span>
      } @else if (normalized(); as number) {
        <i class="pi pi-check-circle" aria-hidden="true"></i>
        <span>
          {{ 'shared.phone.recognised' | t: { number: formatted() } }}
          @if (mobile() && !swissMobile() && number.startsWith('+41')) {
            · {{ 'shared.phone.notMobile' | t }}
          }
        </span>
      } @else {
        <span>{{ 'shared.phone.help' | t }}</span>
      }
    </small>
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.35rem;
    }
    input {
      width: 100%;
    }
    .hint {
      display: flex;
      align-items: flex-start;
      gap: 0.35rem;
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .hint .pi {
      flex: 0 0 auto;
      margin-top: 0.15rem;
    }
    .hint .pi-check-circle {
      color: var(--fs-primary);
    }
    .hint--error {
      color: var(--fs-danger);
      font-weight: 600;
    }
  `,
})
export class PhoneInputComponent implements ControlValueAccessor, Validator {
  readonly inputId = input(`fs-phone-${nextId++}`);
  /** Mobile number expected (hint when a Swiss landline is entered). */
  readonly mobile = input(false);
  readonly testId = input<string | undefined>(undefined);

  protected readonly text = signal('');
  protected readonly disabled = signal(false);
  private readonly touched = signal(false);

  protected readonly valid = computed(() => !this.text().trim() || isValidPhoneNumber(this.text()));
  protected readonly normalized = computed(() => normalizePhoneNumber(this.text()));
  protected readonly formatted = computed(() => formatPhoneNumber(this.text()));
  protected readonly swissMobile = computed(() => isSwissMobile(this.text()));
  protected readonly showError = computed(() => this.touched() && !this.valid());

  private onChange: (value: string | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null | undefined): void {
    this.text.set(value ? formatPhoneNumber(value) : '');
    // an invalid number that is already stored is shown as an error right away
    this.touched.set(!!value && !isValidPhoneNumber(value));
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  validate(_control: AbstractControl): ValidationErrors | null {
    return this.valid() ? null : { phoneNumber: true };
  }

  protected onInput(value: string): void {
    this.text.set(value);
    const trimmed = value.trim();
    // valid → E.164; empty → null; invalid → the raw text (the validator reports it)
    this.onChange(!trimmed ? null : (normalizePhoneNumber(trimmed) ?? trimmed));
  }

  protected onBlur(): void {
    this.touched.set(true);
    this.onTouched();
  }
}
