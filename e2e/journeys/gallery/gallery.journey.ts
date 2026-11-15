import type { Locator, Page } from '@playwright/test';
import { expectDataState, expectErrorState, injectApiError } from '../../fixtures';
import { checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';

/**
 * Component gallery (/dev/gallery, decision 180 #2 and #5): a developer looks through the shared components once per
 * project, one element capture per shared component group, so a design change to a shared component shows up here
 * once instead of on every page. Every `<fs-data-state>` state is captured on its own (skeleton, refreshing, empty,
 * error with retry, forbidden, not found, offline); the live specimen (a real `loadResource` on GET
 * /api/public/app-info) fails with a 500 and recovers through its retry button.
 *
 * The feature sections further down the page (account, stores, quiz, …) are not captured: their screens are captured
 * at the checkpoints of the persona journeys. The axe check runs once on the whole page (first capture); the a11y
 * route sweep also checks /dev/gallery in light and dark.
 */

const AXE_DONE = 'axe checked the whole gallery page at the first capture (and the a11y sweep checks /dev/gallery)';

/**
 * One gallery capture. The static loading/refreshing specimens never settle on purpose, so no capture waits for the
 * data states (the live one is asserted before its capture); axe runs on the first capture only.
 */
let axeDone = false;
async function capture(locator: Locator, name: string, options: { toasts?: boolean } = {}): Promise<void> {
  await checkpointElement(locator, name, { ...options, waitForData: false, skipAxe: axeDone ? AXE_DONE : undefined });
  axeDone = true;
}

/** The grid of specimens right after a group heading (`<h2 class="group" id="g-…">`). */
function group(page: Page, id: string): Locator {
  return page.locator(`#${id} + .grid`);
}

test('developer looks through the shared components and every data state @gallery', async ({ page }) => {
  test.setTimeout(150_000);
  axeDone = false;
  await page.goto('/dev/gallery');
  const live = page.getByTestId('gallery.live.state');
  await expect(live).toHaveAttribute('data-state', 'success');

  // Element captures scroll their element into view: the sticky header would be painted over its top and the phone
  // tab bar over its bottom. A tall viewport (same width, so the same breakpoints) keeps every group fully in view.
  await page.addStyleTag({ content: 'fs-header { position: static !important; }' });
  const viewport = page.viewportSize()!;
  await page.setViewportSize({ width: viewport.width, height: 2400 });

  await test.step('data states', async () => {
    const state = (label: string) => page.getByTestId(`gallery.dataState.${label}`);
    await capture(state('loading'), 'data-state-skeleton');
    for (const label of ['refreshing', 'empty', 'error', 'forbidden', 'notFound', 'offline']) {
      const name = `data-state-${label.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`;
      await capture(state(label), name);
    }
  });

  await test.step('the live data state fails and recovers through retry', async () => {
    const failure = await injectApiError(page, { url: '/api/public/app-info', status: 500 });
    await page.getByTestId('gallery.live.reload').click();
    // a failed reload keeps the languages visible (stale) under a compact error with retry
    const { retry } = await expectErrorState(page, 'gallery.live.state');
    await expect(page.getByTestId('gallery.live.languages')).toBeVisible();
    await capture(page.getByTestId('gallery.dataState.live'), 'data-state-live-error');
    await failure.remove();
    await retry.click();
    await expectDataState(page, 'gallery.live.state', 'success');
    await expect(live.getByRole('alert')).toHaveCount(0);
  });

  await test.step('skeletons, empty and error states, people, header, history', async () => {
    await capture(group(page, 'g-skeleton'), 'skeletons');
    await capture(group(page, 'g-empty'), 'empty-states');
    await capture(group(page, 'g-error'), 'error-states');
    await capture(group(page, 'g-users'), 'user-chips-and-avatars');
    await capture(page.getByTestId('gallery.pageHeader.default'), 'page-header');
    await capture(page.getByTestId('gallery.historyTimeline.filled'), 'history-timeline');
    await capture(page.locator('[data-testid="gallery.historyTimeline.filled"] + .grid'), 'history-timeline-variants');
  });

  await test.step('buttons, forms, tags and a toast', async () => {
    await capture(page.getByTestId('gallery.buttons.default'), 'buttons');
    await capture(page.getByTestId('gallery.forms.default'), 'forms');
    await capture(page.getByTestId('gallery.tags.default'), 'tags');

    await page.getByTestId('gallery.toasts.success').click();
    const toast = page.locator('.p-toast-message').first();
    await expect(toast).toBeVisible();
    // the pointer on the toast keeps it open (it closes itself after 6 s) while the capture waits for rest
    await toast.hover();
    await capture(toast, 'toast-success', { toasts: true });
  });

  await test.step('journey stepper, region picker and map, inputs, people, navigate, shared M6', async () => {
    await capture(page.locator('fs-gallery-journey-section .grid'), 'journey-stepper');
    await capture(group(page, 'g-regions'), 'region-picker-and-map');
    await capture(group(page, 'g-inputs'), 'inputs');
    await capture(group(page, 'g-people'), 'people-phone-calendar-job');
    await capture(group(page, 'g-navigate'), 'navigate-button');
    await capture(group(page, 'g-shared-m6'), 'wall-crop-counter-uploads');
  });
});
