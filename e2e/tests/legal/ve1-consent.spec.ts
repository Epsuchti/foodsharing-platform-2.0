import { type ApiClient, expect, test } from '../../fixtures';
import { acceptConfirm } from '../auth/_auth';
import { acceptPrivacy, playSession, singleQuestionQuiz, solutionKey } from '../quiz/_quiz';

/**
 * Ve1: versioned legal documents (privacy policy for everybody, legal agreement for foodsavers, confidentiality notice
 * for store managers), re-consent after a new version that requires it, only the current version can be accepted.
 */

test.use({ seedReset: 'test' });

interface Consent {
  kind: string;
  currentVersion: string;
  acceptedVersion?: string;
  required: boolean;
  pending: boolean;
}

const consents = (api: ApiClient) => api.getJson<Consent[]>('/api/legal/consents');
const consentOf = async (api: ApiClient, kind: string) => (await consents(api)).find((c) => c.kind === kind)!;

async function publishVersion(orga: ApiClient, kind: string, version: string, requireReconsent: boolean): Promise<void> {
  await orga.postJson(`/api/legal/${kind}/versions`, {
    version,
    summaries: { de: 'Neu: kürzere Aufbewahrung.' },
    bodies: { de: `## Datenschutz ${version}\n\nWir speichern weniger.`, en: `## Privacy ${version}\n\nWe keep less.` },
    requireReconsent,
  });
}

test('Ve1 first login shows privacy consent and stores version @Ve1 @page', async ({ as, apiOf }) => {
  const page = await as('newbie');
  const api = apiOf(page);
  const before = await consentOf(api, 'PRIVACY_POLICY');
  expect(before).toMatchObject({ required: true, pending: true });

  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/consent\?returnUrl=%2Fdashboard$/);
  const doc = page.locator('[data-testid="legal.consent.document"][data-kind="PRIVACY_POLICY"]');
  await expect(doc).toContainText(before.currentVersion);
  // the legal agreement is not asked of a newcomer (it belongs to the foodsaver quiz)
  await expect(page.locator('[data-testid="legal.consent.document"][data-kind="LEGAL_AGREEMENT"]')).toHaveCount(0);

  // the public document is reachable from the consent step without accepting first
  await page.goto('/legal/privacy-policy');
  await expect(page.getByTestId('legal.document.state')).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('legal.document.version')).toContainText(before.currentVersion);
  await page.goBack();

  await doc.locator('[data-testid^="legal.consent.check."] input').check();
  await page.getByTestId('legal.consent.phoneInput').fill('0791112233');
  await page.getByTestId('legal.consent.submit').click();
  await expect(page).toHaveURL(/\/dashboard$/);

  expect(await consentOf(api, 'PRIVACY_POLICY')).toMatchObject({ acceptedVersion: before.currentVersion, pending: false });
});

test('Ve1 publishing a new version with reconsent re-prompts @Ve1', async ({ as, apiOf }) => {
  const orga = await as('orga');
  await orga.goto('/admin/legal');
  const versions = orga.getByTestId('legal.admin.versions');
  await expect(versions).toHaveAttribute('data-state', 'success');
  await expect(orga.getByTestId('legal.admin.version')).toHaveCount(1);

  const publish = orga.getByTestId('legal.admin.publish').locator('button');
  await expect(publish).toBeDisabled();
  await orga.getByTestId('legal.admin.version.input').fill('2026-11');
  await orga.getByTestId('legal.admin.bodies.field.de').fill('## Datenschutz\n\nNeue Fassung mit kürzerer Aufbewahrung.');
  await orga.getByTestId('legal.admin.reconsent').locator('input').check();
  await expect(publish).toBeEnabled();
  await publish.click();
  await acceptConfirm(orga);

  await expect(orga.locator('[data-testid="legal.admin.version"][data-version="2026-11"]')).toBeVisible();
  await expect(
    orga.locator('[data-testid="legal.admin.version"][data-version="2026-11"]').getByTestId('legal.admin.reconsentChip'),
  ).toBeVisible();
  // the form is reset after publishing
  await expect(orga.getByTestId('legal.admin.version.input')).toHaveValue('');

  // a foodsaver who accepted 2026-01 is asked again before anything else
  const page = await as('foodsaver');
  await page.goto('/me/activity');
  await expect(page).toHaveURL(/\/consent\?returnUrl=%2Fme%2Factivity$/);
  const doc = page.locator('[data-testid="legal.consent.document"][data-kind="PRIVACY_POLICY"]');
  await expect(doc).toContainText('2026-11');
  await expect(page.getByTestId('legal.consent.phone')).toHaveCount(0);
  await doc.locator('[data-testid^="legal.consent.check."] input').check();
  await page.getByTestId('legal.consent.submit').click();
  await expect(page).toHaveURL(/\/me\/activity$/);
  expect(await consentOf(apiOf(page), 'PRIVACY_POLICY')).toMatchObject({ acceptedVersion: '2026-11', pending: false });

  // the public page shows the new version
  await page.goto('/legal/privacy-policy');
  await expect(page.getByTestId('legal.document.version')).toContainText('2026-11');
});

test('Ve1 legal agreement accepted during quiz confirmation @Ve1', async ({ as, apiOf, api }) => {
  await api.loginAs('orga');
  await singleQuestionQuiz(api, 'FOODSAVER');
  const key = await solutionKey(api, 'FOODSAVER');

  const page = await as('newbie');
  const own = apiOf(page);
  await acceptPrivacy(own);
  expect(await consentOf(own, 'LEGAL_AGREEMENT')).toMatchObject({ required: false, pending: false });
  await playSession(own, 'FOODSAVER', { correct: true, key });

  await page.goto('/quizzes/foodsaver/results');
  const confirm = page.getByTestId('quiz.results.confirm');
  await expect(confirm).toBeVisible();
  await confirm.getByTestId('quiz.results.agree').click();
  await confirm.getByTestId('quiz.results.confirmButton').click();
  await expect(page.getByTestId('quiz.results.confirmed')).toBeVisible();

  const agreement = await consentOf(own, 'LEGAL_AGREEMENT');
  expect(agreement).toMatchObject({ required: true, pending: false, acceptedVersion: agreement.currentVersion });
  // no second prompt for the agreement on the consent gate
  await page.goto('/me/activity');
  await expect(page).toHaveURL(/\/me\/activity$/);
});

test('Ve1 confidentiality notice required for store manager @Ve1', async ({ as, apiOf, dev }) => {
  // bieb@local is a store manager who accepted it: never asked
  const bieb = await as('bieb');
  await bieb.goto('/dashboard');
  await expect(bieb.getByTestId('dashboard.page.welcome')).toBeVisible();
  expect(await consentOf(apiOf(bieb), 'CONFIDENTIALITY_NOTICE')).toMatchObject({ required: true, pending: false });

  // a foodsaver who becomes store manager has to accept it first
  const before = await consentOf(apiOf(await as('foodsaver')), 'CONFIDENTIALITY_NOTICE');
  expect(before).toMatchObject({ required: false, pending: false });
  await dev.setRole('foodsaver', 'STORE_MANAGER');
  const page = await as('foodsaver');
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/consent\?/);
  const doc = page.locator('[data-testid="legal.consent.document"][data-kind="CONFIDENTIALITY_NOTICE"]');
  await expect(doc).toBeVisible();
  await expect(page.getByTestId('legal.consent.document')).toHaveCount(1);
  await doc.locator('[data-testid^="legal.consent.check."] input').check();
  await page.getByTestId('legal.consent.submit').click();
  await expect(page).toHaveURL(/\/dashboard$/);
  expect(await consentOf(apiOf(page), 'CONFIDENTIALITY_NOTICE')).toMatchObject({ required: true, pending: false });
});

test('Ve1 outdated version is refused @Ve1', async ({ as, apiOf, api }) => {
  const page = await as('newbie');
  const own = apiOf(page);
  await page.goto('/consent');
  const doc = page.locator('[data-testid="legal.consent.document"][data-kind="PRIVACY_POLICY"]');
  await expect(doc).toContainText('2026-01');

  // meanwhile a new version is published (no re-consent required)
  await api.loginAs('orga');
  await publishVersion(api, 'PRIVACY_POLICY', '2026-12', false);

  // accepting the version on screen fails: the user stays on the consent page with an error
  await doc.locator('[data-testid^="legal.consent.check."] input').check();
  await page.getByTestId('legal.consent.phoneInput').fill('079 111 22 44');
  await page.getByTestId('legal.consent.submit').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
  await expect(page).toHaveURL(/\/consent/);
  const outdated = await own.fetch('POST', '/api/legal/consents', { kind: 'PRIVACY_POLICY', version: '2026-01' });
  expect(outdated.status()).toBe(409);
  expect(await outdated.json()).toMatchObject({ code: 'LEGAL_VERSION_OUTDATED' });

  // after reloading the current version is shown and can be accepted
  await page.reload();
  await expect(doc).toContainText('2026-12');
  await doc.locator('[data-testid^="legal.consent.check."] input').check();
  await page.getByTestId('legal.consent.submit').click();
  await expect(page).toHaveURL(/\/dashboard$/);
  expect(await consentOf(own, 'PRIVACY_POLICY')).toMatchObject({ acceptedVersion: '2026-12', pending: false });
});
