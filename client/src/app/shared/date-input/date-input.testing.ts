/**
 * Unit-test helper: types `text` into the field of an `fs-date-input`/`fs-datetime-input`/`fs-time-input` host like
 * a user (keydown, input, blur), so the wrapper reads it. `text` is the display format (`30.06.2027`, `18:30`).
 */
export function typeIntoDateInput(host: Element | null, text: string): void {
  const input = host?.querySelector('input');
  if (!input) {
    throw new Error('typeIntoDateInput: no date input field found');
  }
  input.dispatchEvent(new Event('focus'));
  input.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }));
  input.value = text;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}
