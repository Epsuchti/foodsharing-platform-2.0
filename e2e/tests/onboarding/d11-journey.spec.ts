import {
  LANGUAGES,
  acceptPendingConsents,
  expect,
  expectNoMissingKeys,
  journeyIndicator,
  journeyProgress,
  setLanguage,
  test,
  type Role,
} from '../../fixtures';
import { acceptConsentPage, newEmail, skipPasskeyNudge } from '../auth/_auth';

/** §11 onboarding journey: landing → account in two clicks, current step + next action, 360 px × 5 languages. */
test.use({ seedReset: 'test' });

interface JourneyDto {
  complete: boolean;
  currentStep?: string;
  steps: { key: string; status: string; details?: { readChapters?: string[] } }[];
}

test('D11.5.1 landing to account created in two clicks with Google @D11.5.1 @page', async ({ page, apiOf }) => {
  await skipPasskeyNudge(page);
  await page.goto('/');
  await expect(page.getByTestId('public.landing.hero')).toBeVisible();

  // click 1: the Google CTA goes straight to Google (no login page in between)
  await page.getByTestId('public.landing.joinGoogle').click();
  await expect(page.getByTestId('dev.oidc.page')).toBeVisible();
  await page.getByTestId('dev.oidc.new-email').fill(newEmail('journey'));
  await page.getByTestId('dev.oidc.new-name').fill('Mia Neu');
  // click 2: consent at (fake) Google → the account exists
  await page.getByTestId('dev.oidc.new-submit').click();
  await expect.poll(async () => (await apiOf(page).session()).authenticated).toBe(true);

  // privacy policy, then straight into the journey
  await acceptConsentPage(page, /\/onboarding$/);
  await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'PROFILE_REGION');
  // the journey progress: the bar on phones, the meter of the side menu's journey item on desktop (decision 133)
  await expect(journeyIndicator(page)).toBeVisible();
});

const STAGES: { role: Role; step: RegExp }[] = [
  { role: 'newbie', step: /^PROFILE_REGION$/ },
  { role: 'profile', step: /^PROFILE_REGION$/ },
  // quiz@local passed the quiz; accepting the pending LEGAL_AGREEMENT (needed to use the app) completes AGREEMENT
  { role: 'quiz', step: /^INTRO_PICKUPS$/ },
  { role: 'einab1', step: /^INTRO_PICKUPS$/ },
  { role: 'pending', step: /^ID_CHECK$/ },
];

test('D11.5.2 every seed stage shows the current step and one next action @D11.5.2', async ({ as, apiOf }) => {
  for (const { role, step } of STAGES) {
    const page = await as(role);
    await acceptPendingConsents(apiOf(page));
    const journey = await apiOf(page).getJson<JourneyDto>('/api/onboarding/journey');
    expect(journey.currentStep, role).toMatch(step);

    await page.goto('/onboarding');
    const current = page.getByTestId('onboarding.journey.current');
    await expect(current, role).toHaveAttribute('data-step', step);
    // exactly one next action: a CTA, or (waiting for others) who we are waiting for
    const actions = page.getByTestId('onboarding.journey.cta').or(page.getByTestId('onboarding.journey.waiting'));
    await expect(actions, role).toHaveCount(1);
    await expect(page.getByTestId(`onboarding.journey.stepper.step.${journey.currentStep}`), role).toHaveAttribute('aria-current', 'step');

    // the journey progress names the same step everywhere in the app: the header bar on phones, the meter on
    // "Mein Einstieg" in the desktop side menu (decision 133)
    await page.goto('/dashboard');
    await expect(journeyProgress(page), role).toHaveAttribute('data-current', journey.currentStep!);
    await expect(page.getByTestId('dashboard.page.nextStep'), role).toHaveAttribute('data-journey-step', journey.currentStep!);
  }

  // a verified foodsaver has no journey bar and sees the role card
  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/dashboard');
  await expect(foodsaver.getByTestId('dashboard.page.nextStep')).toHaveAttribute('data-step', 'profile');
  await expect(foodsaver.getByTestId('onboarding.bar')).toHaveCount(0);
  await expect(foodsaver.getByTestId('nav.onboarding')).toHaveCount(0);
  await expect(foodsaver.getByTestId('nav.onboarding.progress')).toHaveCount(0);
  await foodsaver.goto('/onboarding');
  await expect(foodsaver.getByTestId('onboarding.journey.complete')).toBeVisible();
});

test('D11.5.4 journey works at 360px in all five languages @D11.5.4', async ({ as, apiOf }) => {
  const page = await as('einab1');
  await acceptPendingConsents(apiOf(page));
  await page.setViewportSize({ width: 360, height: 780 });
  for (const lang of LANGUAGES) {
    await setLanguage(page, lang);
    for (const path of ['/onboarding', '/onboarding/profile', '/dashboard']) {
      await page.goto(path);
      await expect(page.locator('fs-data-state[data-state="success"]').first()).toBeVisible();
      await expectNoMissingKeys(page);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${lang} ${path}`).toBeLessThanOrEqual(0);
    }
    // the journey page has one call to action: the bar shows the step but leaves the action to the page
    // (design review M7 R2); on every other page the bar carries the action
    await page.goto('/onboarding');
    await expect(page.getByTestId('onboarding.journey.cta')).toBeVisible();
    await expect(page.getByTestId('onboarding.bar.stepper')).toBeVisible();
    await expect(page.getByTestId('onboarding.bar.stepper.next')).toHaveCount(0);
    await page.goto('/dashboard');
    await expect(page.getByTestId('onboarding.bar.stepper.next')).toBeVisible();
  }
});

test('EA8 newbie completes profile and region, then reads the chapters @EA8 @A19', async ({ as, apiOf }) => {
  const page = await as('newbie');
  const api = apiOf(page);
  await acceptPendingConsents(api);
  await page.goto('/dashboard');
  await page.getByTestId('dashboard.page.nextStepAction').click();
  await expect(page).toHaveURL(/\/onboarding\/profile$/);

  await page.getByTestId('onboarding.profile.firstName').fill('Lea');
  await page.getByTestId('onboarding.profile.lastName').fill('Brunner');
  await page.locator('#ob-phone').fill('079 123 45 67');
  await page.getByTestId('onboarding.profile.picker.postcode').fill('8004');
  await page.getByTestId('onboarding.profile.picker.suggestion.zuerich-stadt').click();
  await expect(page.getByTestId('onboarding.profile.regionCard')).toBeVisible();
  await page.getByTestId('onboarding.profile.save').click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await expect(page.getByTestId('onboarding.journey.stepper.step.PROFILE_REGION')).toHaveAttribute('data-status', 'DONE');
  await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'LEARN');

  // the LEARN step lists the chapters actually read (any order)
  const before = await api.getJson<JourneyDto>('/api/onboarding/journey');
  const learn = before.steps.find((step) => step.key === 'LEARN')!;
  expect(learn.details?.readChapters ?? []).toEqual([]);
  await page.getByTestId('onboarding.journey.cta').click();
  await expect(page).toHaveURL(/\/learn\//);
});
