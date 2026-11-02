import { expect, Locator } from '@playwright/test';

/**
 * Helpers for the shared date inputs (decision 150): `fs-date-input`, `fs-datetime-input` and `fs-time-input` show
 * `dd.mm.yyyy` / 24h `HH:mm` in every UI language and read typed text on blur. The helpers take the ISO value the
 * form stores, type its display format into the field like a user and leave it with Tab; the calendar popup must be
 * closed afterwards, so it can't cover later clicks. `host` is the element with the field's `data-testid`.
 */

/** `2026-11-05` → `05.11.2026`. */
export function displayDate(iso: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!match) {
    throw new Error(`displayDate: not an ISO date: ${iso}`);
  }
  return `${match[3]}.${match[2]}.${match[1]}`;
}

/** `2026-11-05T18:30` → `05.11.2026 18:30`. */
export function displayDateTime(isoLocal: string): string {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(isoLocal);
  if (!match) {
    throw new Error(`displayDateTime: not an ISO local date-time: ${isoLocal}`);
  }
  return `${displayDate(match[1])} ${match[2]}`;
}

/** The text field of a date input host. */
export function dateField(host: Locator): Locator {
  return host.locator('input').first();
}

async function typeAndLeave(host: Locator, text: string): Promise<void> {
  const field = dateField(host);
  await field.fill(text);
  await field.press('Tab');
  await expect(host.page().locator('.p-datepicker-panel')).toHaveCount(0);
}

/** Types an ISO date (`yyyy-MM-dd`, `''` clears) into an `fs-date-input`. */
export async function fillDate(host: Locator, iso: string): Promise<void> {
  await typeAndLeave(host, iso ? displayDate(iso) : '');
}

/** Types an ISO local date-time (`yyyy-MM-ddTHH:mm`, `''` clears) into an `fs-datetime-input`. */
export async function fillDateTime(host: Locator, isoLocal: string): Promise<void> {
  await typeAndLeave(host, isoLocal ? displayDateTime(isoLocal) : '');
}

/** Types a 24h time (`HH:mm`, `''` clears) into an `fs-time-input`. */
export async function fillTime(host: Locator, time: string): Promise<void> {
  await typeAndLeave(host, time);
}
