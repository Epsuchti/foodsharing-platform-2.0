import { injectApiError, regionId } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/** Event screens (M5 E1–E3): region list, event page, form, my events, error. Baselines later (decision 69). */
const zs = regionId('zuerich-stadt');
const event = (n: number) => `/events/00000044-0002-4000-8000-${String(n).padStart(12, '0')}`;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`events ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('region events and event page @E1 @E2 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${zs}/events`);
      await expect(page.getByTestId('events.list.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `events-list-${variant.name}`);
      await page.goto(event(1));
      await expect(page.getByTestId('events.event.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `events-event-${variant.name}`);
      await page.goto(event(6));
      await expect(page.getByTestId('events.event.rsvpClosed')).toBeVisible();
      await visual.expectScreen(page, `events-past-${variant.name}`);
    });

    screenshotTest('form, my events and error @E1 @E3 @screenshot', async ({ page, visual }) => {
      await page.goto(`/events/new?regionId=${zs}`);
      await page.getByTestId('events.form.submit').click();
      await expect(page.getByTestId('events.form.error.name')).toBeVisible();
      await visual.expectScreen(page, `events-form-${variant.name}`);
      await page.goto('/events/mine');
      await expect(page.getByTestId('events.mine.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `events-mine-${variant.name}`);
      await injectApiError(page, { url: /\/api\/regions\/[^/]+\/events/, status: 500 });
      await page.goto(`/regions/${zs}/events`);
      await expect(page.getByTestId('events.list.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `events-error-${variant.name}`);
    });
  });
});
