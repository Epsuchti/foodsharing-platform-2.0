import { acceptPendingConsents, expect, injectApiError, regionId, test } from '../../fixtures';

/**
 * A14 business card (plan-m7 Q4, A49): verified foodsavers download an 85 × 55 mm PDF with name, role label and
 * "foodsharing <region>"; the role is at most the own role and the region one of the own regions. Seed: foodsaver@local
 * Sarah Müller, FOODSAVER, home region "Zürich Stadt"; newbie@local is an unverified foodsharer.
 */
test('A14 verified foodsaver downloads business card PDF with name and region @A14', async ({ as, pdf }) => {
  const page = await as('foodsaver');
  await page.goto('/account/settings');
  const card = page.getByTestId('account.settings.businessCard');
  await expect(card.getByTestId('account.settings.businessCard.role')).toContainText(/Foodsaver/i);
  await expect(card.getByTestId('account.settings.businessCard.region')).toContainText('Zürich Stadt');
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    card.getByTestId('account.settings.businessCard.download').click(),
  ]);
  expect(download.suggestedFilename()).toBe('business-card-zuerich-stadt.pdf');
  await pdf.expectPdfContains(download, ['Sarah Müller', 'foodsharing Zürich Stadt'], { pages: 1 });
  await expect(card.getByTestId('account.settings.businessCard.error')).toHaveCount(0);
});

test('A14 unverified users get no card; a refusal is shown with its reason @A14', async ({ as, apiOf }) => {
  const newbie = await as('newbie');
  await acceptPendingConsents(apiOf(newbie));
  await newbie.goto('/account/settings');
  await expect(newbie.getByTestId('account.settings.businessCard.notAllowed')).toBeVisible();
  await expect(newbie.getByTestId('account.settings.businessCard.download')).toHaveCount(0);

  // a role above the own one is refused by the server (the form never offers it)
  const page = await as('foodsaver');
  const api = apiOf(page);
  const refused = await api.get(`/api/users/me/business-card?role=AMBASSADOR&regionId=${regionId('zuerich-stadt')}`);
  expect(refused.status()).toBe(403);
  expect((await refused.json()).code).toBe('BUSINESS_CARD_NOT_ALLOWED');

  // the PDF arrives as blob: the error body inside is still read and translated
  await injectApiError(page, { url: '/api/users/me/business-card', status: 400, code: 'NOT_REGION_MEMBER', times: 1 });
  await page.goto('/account/settings');
  await page.getByTestId('account.settings.businessCard.download').click();
  await expect(page.getByTestId('account.settings.businessCard.error')).toHaveAttribute('data-code', 'NOT_REGION_MEMBER');
});
