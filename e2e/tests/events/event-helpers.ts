import type { Locator, Page } from '@playwright/test';

/** Seed event ids (docs/seed-ids.md module 0044): `01` public brunch (bot, foodsaver INVITED), `06` past. */
export const event = (n: number): string => `00000044-0002-4000-8000-${String(n).padStart(12, '0')}`;

/** RSVP option buttons in order yes / maybe / no. */
export function rsvpOption(page: Page, index: 0 | 1 | 2): Locator {
  return page.getByTestId('events.event.rsvp').locator('.p-togglebutton').nth(index);
}
