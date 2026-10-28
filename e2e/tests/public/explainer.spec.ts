import { expect, injectApiError, test } from '../../fixtures';

/** §11.2 public explainer on `/`: live counters, journey preview, find your region, FAQ, business contact. */

interface ExplainerDto {
  counters: { foodsavers: number; regions: number };
  journeyPreview: { key: string }[];
}

test('C1 explainer sections render with live counters and region card @C1 @page', async ({ page, api }) => {
  const live = await api.getJson<ExplainerDto>('/api/public/onboarding/explainer');
  await page.goto('/');
  await expect(page.getByTestId('public.explainer.state')).toHaveAttribute('data-state', 'success');

  // counters show the live numbers (animated up to their value)
  const foodsavers = page.getByTestId('public.explainer.counter.foodsavers');
  await expect(foodsavers).toHaveAttribute('data-value', String(live.counters.foodsavers));
  expect(live.counters.foodsavers).toBeGreaterThan(0);
  await expect(foodsavers).toHaveText(new Intl.NumberFormat('de-CH').format(live.counters.foodsavers));

  // the journey preview lists every step
  await expect(page.locator('[data-testid="public.explainer.preview"] [data-step]')).toHaveCount(live.journeyPreview.length);

  // find your region → region card with the next EinAb dates
  await page.getByTestId('public.explainer.region').scrollIntoViewIfNeeded();
  await page.getByTestId('public.explainer.picker.postcode').fill('8004');
  await page.getByTestId('public.explainer.picker.suggestion.zuerich-stadt').click();
  const card = page.getByTestId('public.explainer.regionCard');
  await expect(card).toContainText('Zürich');
  await expect(card.getByTestId('public.explainer.regionCard.date').first()).toBeVisible();

  // FAQ opens, business contact is a mail link, Google CTA goes straight to Google with the journey as return URL
  await page.getByTestId('public.explainer.faq.cost').locator('summary').click();
  await expect(page.getByTestId('public.explainer.faq.cost').locator('p')).toBeVisible();
  await expect(page.getByTestId('public.explainer.businessMail')).toHaveAttribute('href', /^mailto:/);
  await expect(page.getByTestId('public.landing.joinGoogle')).toHaveAttribute('href', '/oauth2/authorization/google?returnUrl=/onboarding');
});

test('C1 explainer error keeps the hero and offers retry @C1', async ({ page }) => {
  await injectApiError(page, { url: '**/api/public/onboarding/explainer**', status: 400, code: 'VALIDATION_FAILED', times: 1 });
  await page.goto('/');
  await expect(page.getByTestId('public.landing.hero')).toBeVisible();
  const state = page.getByTestId('public.explainer.state');
  await expect(state).toHaveAttribute('data-state', 'error');
  await page.getByTestId('public.explainer.state.retry').getByRole('button').click();
  await expect(state).toHaveAttribute('data-state', 'success');
});
