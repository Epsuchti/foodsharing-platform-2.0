import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * Phone number rules, the same as the server (`user.service.PhoneNumberValidator`, ported from the reference app):
 * an optional leading `+`, digits and the separators `space ( ) / . -`, 7 to 15 digits. Stored as E.164 with
 * Switzerland as the default country (`079 123 45 67` → `+41791234567`, `0049…` → `+49…`).
 */
const PHONE_CHARACTERS = /^\+?[0-9 ()/.-]+$/;
const MIN_DIGITS = 7;
const MAX_DIGITS = 15;
export const DEFAULT_COUNTRY_CODE = '41';

export function isValidPhoneNumber(value: string | null | undefined): boolean {
  const trimmed = (value ?? '').trim();
  if (!PHONE_CHARACTERS.test(trimmed)) {
    return false;
  }
  const digits = trimmed.replace(/\D/g, '').length;
  return digits >= MIN_DIGITS && digits <= MAX_DIGITS;
}

/** E.164 form (`+<digits>`) of a valid number; null for blank or invalid input. */
export function normalizePhoneNumber(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim();
  if (!trimmed || !isValidPhoneNumber(trimmed)) {
    return null;
  }
  const digits = trimmed.replace(/\D/g, '');
  if (trimmed.startsWith('+')) {
    return `+${digits}`;
  }
  if (digits.startsWith('00')) {
    return `+${digits.slice(2)}`;
  }
  if (digits.startsWith('0')) {
    return `+${DEFAULT_COUNTRY_CODE}${digits.slice(1)}`;
  }
  return `+${digits}`;
}

/** Swiss mobile numbers (+41 75–79); a hint only, landlines are valid too. */
export function isSwissMobile(value: string | null | undefined): boolean {
  return /^\+417[5-9]\d{7}$/.test(normalizePhoneNumber(value) ?? '');
}

/**
 * Readable form for display: Swiss numbers as `+41 79 123 45 67`, other numbers as stored (E.164).
 * Returns the input unchanged when it is not a valid number.
 */
export function formatPhoneNumber(value: string | null | undefined): string {
  const normalized = normalizePhoneNumber(value);
  if (!normalized) {
    return (value ?? '').trim();
  }
  const swiss = /^\+41(\d{2})(\d{3})(\d{2})(\d{2})$/.exec(normalized);
  return swiss ? `+41 ${swiss[1]} ${swiss[2]} ${swiss[3]} ${swiss[4]}` : normalized;
}

/** Form validator: empty is fine (use `Validators.required` for mandatory fields). */
export function phoneNumberValidator(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  return value.trim() === '' || isValidPhoneNumber(value) ? null : { phoneNumber: true };
}
