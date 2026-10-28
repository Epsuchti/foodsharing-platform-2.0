import { expect, injectApiError, test } from '../../fixtures';
import { ZH, acceptConfirm, bookableCards, openBookable } from './_intro';

/** Error, retry and forbidden states of the intro pickup pages (DataState + mutation errors). */
test.use({ seedReset: 'test' });

test('intro pickups: list error with retry, slot taken meanwhile @EA7', async ({ as }) => {
  const page = await as('einab1');
  await injectApiError(page, { url: `**/api/regions/${ZH}/intro-pickups/bookable**`, status: 400, code: 'VALIDATION_FAILED', times: 1 });
  await page.goto('/intro-pickups');
  const state = page.getByTestId('introPickups.bookable.state');
  await expect(state).toHaveAttribute('data-state', 'error');
  await page.getByTestId('introPickups.bookable.state.retry').getByRole('button').click();
  await expect(state).toHaveAttribute('data-state', 'success');

  // the online slot is taken by someone else between listing and booking → translated error, list reloads
  await openBookable(page);
  await page.route('**/api/intro-pickups/slots/*/book', (route) =>
    route.fulfill({ status: 409, json: { status: 409, code: 'SLOT_NOT_AVAILABLE', message: 'x', retryable: false } }),
  );
  await bookableCards(page, 'ONLINE').getByTestId('introPickups.bookable.book').click();
  await acceptConfirm(page);
  await expect(page.getByTestId('shell.toast')).toBeVisible();
  await expect(page.getByTestId('introPickups.bookable.success')).toHaveCount(0);
});

test('intro pickups: guide pages are forbidden or empty for non-guides @EA10', async ({ as }) => {
  const page = await as('einab1');
  await page.goto('/guide');
  await expect(page.getByTestId('introPickups.guide.regionsState')).toHaveAttribute('data-state', 'empty');
  await page.goto('/guide/settings');
  await expect(page.getByTestId('introPickups.settings.profileState')).toHaveAttribute('data-state', /forbidden|empty|success/);
  await page.goto(`/regions/${ZH}/intro-pickups/import`);
  await page.getByTestId('introPickups.import.csv.input').setInputFiles({ name: 'a.csv', mimeType: 'text/csv', buffer: Buffer.from('ID;Datum\n1;01.01.2026\n') });
  await page.getByTestId('introPickups.import.previewButton').click();
  await expect(page.getByTestId('introPickups.import.error')).toBeVisible();
  await page.goto(`/regions/${ZH}/newcomers`);
  await expect(page.getByTestId('introPickups.funnel.state')).toHaveAttribute('data-state', 'forbidden');
});

test('intro pickups: my bookings error with retry @EA7', async ({ as }) => {
  const page = await as('einab1');
  await injectApiError(page, { url: /\/api\/users\/me\/intro-pickups\?past=false/, status: 400, code: 'VALIDATION_FAILED', times: 1 });
  await page.goto('/me/intro-pickups');
  const state = page.getByTestId('introPickups.my.state');
  await expect(state).toHaveAttribute('data-state', 'error');
  await page.getByTestId('introPickups.my.state.retry').getByRole('button').click();
  await expect(state).toHaveAttribute('data-state', 'success');
});
