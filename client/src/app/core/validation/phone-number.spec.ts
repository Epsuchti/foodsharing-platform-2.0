import { FormControl } from '@angular/forms';

import {
  formatPhoneNumber,
  isSwissMobile,
  isValidPhoneNumber,
  normalizePhoneNumber,
  phoneNumberValidator,
} from './phone-number';

describe('phone numbers (same rules as the server)', () => {
  it('accepts 7–15 digits with common separators', () => {
    expect(isValidPhoneNumber('079 123 45 67')).toBe(true);
    expect(isValidPhoneNumber('+41 (0)79/123.45-67')).toBe(true);
    expect(isValidPhoneNumber('123456')).toBe(false);
    expect(isValidPhoneNumber('1234567890123456')).toBe(false);
    expect(isValidPhoneNumber('079 abc')).toBe(false);
    expect(isValidPhoneNumber('')).toBe(false);
  });

  it('normalizes to E.164 with Switzerland as default country', () => {
    expect(normalizePhoneNumber('079 123 45 67')).toBe('+41791234567');
    expect(normalizePhoneNumber('0049 30 1234567')).toBe('+49301234567');
    expect(normalizePhoneNumber('+43 1 234 5678')).toBe('+4312345678');
    expect(normalizePhoneNumber('  ')).toBeNull();
    expect(normalizePhoneNumber('12')).toBeNull();
  });

  it('recognises Swiss mobiles and formats Swiss numbers', () => {
    expect(isSwissMobile('079 123 45 67')).toBe(true);
    expect(isSwissMobile('044 123 45 67')).toBe(false);
    expect(formatPhoneNumber('+41791234567')).toBe('+41 79 123 45 67');
    expect(formatPhoneNumber('0049301234567')).toBe('+49301234567');
    expect(formatPhoneNumber('xx')).toBe('xx');
  });

  it('validates form controls (empty is fine)', () => {
    expect(phoneNumberValidator(new FormControl(''))).toBeNull();
    expect(phoneNumberValidator(new FormControl('079 123 45 67'))).toBeNull();
    expect(phoneNumberValidator(new FormControl('12'))).toEqual({ phoneNumber: true });
  });
});
