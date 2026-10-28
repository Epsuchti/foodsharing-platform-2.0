import type { Page } from '@playwright/test';
import { type ApiClient, type Role, acceptPendingConsents, expect, expectStatus, test } from '../../fixtures';
import { setIntroSettings } from '../intro-pickups/_intro';
import { playSession, solutionKey } from '../quiz/_quiz';
import { ZH, confirmMessage, openQueue, queueCard } from '../verification/_verification';

/**
 * EA8 / §11: the whole newcomer journey of one seed user, newbie@local → verified foodsaver with passport. The steps
 * a newcomer does in the browser are clicked (profile, journey page, EinAb booking, passport page); what other people
 * do (guide marks EinAbs done, ambassador verifies and creates the passport) uses their own sessions.
 */
test.use({ seedReset: 'test' });

const NEWBIE_ID = '00000001-0001-4000-8000-000000000001';

interface JourneyDto {
  complete: boolean;
  currentStep?: string;
  steps: { key: string; status: string; progress?: { done: number; booked: number; required: number } }[];
}

interface Bookable {
  id: string;
  category: string;
  startAt: string;
  slotId?: string;
  bookable: boolean;
  visitFairteiler: boolean;
  guide?: { id: string };
}

async function journey(api: ApiClient): Promise<JourneyDto> {
  return api.getJson<JourneyDto>('/api/onboarding/journey');
}

/** The journey page shows `step` as the current step. */
async function expectCurrentStep(page: Page, step: string): Promise<void> {
  await page.goto('/onboarding');
  await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', step);
}

test('EA8 newbie becomes a verified foodsaver with passport through every journey step @EA8 @D11.5.2 @page', async ({
  as,
  apiOf,
  dev,
  clock,
}) => {
  test.setTimeout(240_000);
  const page = await as('newbie');
  const me = apiOf(page);
  const orga = apiOf(await as('orga'));
  const bot = await as('bot');
  const users = await dev.users();
  const roleOf = (userId: string): Role => users.find((user) => user.id === userId)!.email.replace('@local', '') as Role;

  // 1. profile and region (UI)
  const consents = await me.getJson<{ kind: string; currentVersion: string; pending: boolean }[]>('/api/legal/consents');
  for (const consent of consents.filter((c) => c.pending && c.kind === 'PRIVACY_POLICY')) {
    await me.postJson('/api/legal/consents', { kind: consent.kind, version: consent.currentVersion });
  }
  await expectCurrentStep(page, 'PROFILE_REGION');
  await page.getByTestId('onboarding.journey.cta').click();
  await expect(page).toHaveURL(/\/onboarding\/profile$/);
  await page.getByTestId('onboarding.profile.firstName').fill('Lea');
  await page.getByTestId('onboarding.profile.lastName').fill('Brunner');
  await page.locator('#ob-phone').fill('079 123 45 67');
  await page.getByTestId('onboarding.profile.picker.postcode').fill('8004');
  await page.getByTestId('onboarding.profile.picker.suggestion.zuerich-stadt').click();
  await page.getByTestId('onboarding.profile.save').click();
  await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'LEARN');

  // 2. learning chapters: the first one through the CTA, the rest as read through the API
  await page.getByTestId('onboarding.journey.cta').click();
  await expect(page).toHaveURL(/\/learn\//);
  const chapters = await me.getJson<{ key: string }[]>('/api/public/content/learning');
  for (const chapter of chapters) {
    await me.postJson(`/api/onboarding/learning/${chapter.key}/read`);
  }
  await expectCurrentStep(page, 'QUIZ');

  // 3. foodsaver quiz passed, 4. legal agreement accepted and quiz confirmed
  await playSession(me, 'FOODSAVER', { correct: true, key: await solutionKey(orga, 'FOODSAVER') });
  await acceptPendingConsents(me);
  await me.postJson('/api/quizzes/FOODSAVER/confirm');
  expect((await journey(me)).steps.filter((s) => ['LEARN', 'QUIZ', 'AGREEMENT'].includes(s.key)).map((s) => s.status)).toEqual([
    'DONE',
    'DONE',
    'DONE',
  ]);
  await expectCurrentStep(page, 'INTRO_PICKUPS');

  // 5. EinAbs: book (first one in the browser), attend, the guide marks it done; until the step is done.
  // The seed has only two Zürich guides with upcoming EinAbs, so the ambassador allows a second EinAb with the same guide.
  await setIntroSettings(apiOf(bot), { preventDuplicateGuideBookings: false });
  for (let round = 0; round < 6; round++) {
    const state = await journey(me);
    const step = state.steps.find((s) => s.key === 'INTRO_PICKUPS')!;
    if (step.status === 'DONE') break;
    const list = await me.getJson<{ items: Bookable[] }>(`/api/regions/${ZH}/intro-pickups/bookable?size=50`);
    const candidates = list.items.filter((item) => item.bookable && item.slotId);
    // the Fairteiler visit must be part of the first three EinAbs: book it first
    const einab = candidates.find((item) => item.visitFairteiler) ?? candidates[0];
    expect(einab, `bookable EinAb in round ${round}: ${JSON.stringify(list.items.map((i) => [i.category, i.startAt, (i as unknown as { notBookableReason?: string }).notBookableReason]))}`).toBeDefined();
    if (round === 0) {
      await page.goto('/intro-pickups');
      const card = page.locator(`[data-testid="introPickups.bookable.card"][data-id="${einab.id}"]`);
      await card.getByTestId('introPickups.bookable.book').click();
      await page.locator('.p-confirmdialog-accept-button').click();
      await expect(page.getByTestId('introPickups.bookable.success')).toBeVisible();
    } else {
      await me.postJson(`/api/intro-pickups/slots/${einab.slotId}/book`);
    }
    await expect.poll(async () => (await journey(me)).currentStep).toBe('INTRO_PICKUPS');

    // two hours after the start the guide marks the booking done
    await clock.fixServer(new Date(Date.parse(einab.startAt) + 2 * 3600_000).toISOString());
    const guide = apiOf(await as(roleOf(einab.guide!.id)));
    await guide.postJson(`/api/intro-pickups/slots/${einab.slotId}/done`);
  }
  // 5b. Zürich's own extra step (seed region setting): the ambassador confirms the newcomer meeting
  await expectCurrentStep(page, 'newcomer-meeting');
  const confirmed = await apiOf(bot).fetch('POST', `/api/onboarding/regions/${ZH}/users/${NEWBIE_ID}/steps/newcomer-meeting/confirm`);
  await expectStatus(confirmed, [200, 204], 'confirm region step');
  await expectCurrentStep(page, 'ID_CHECK');
  await expect(page.getByTestId('onboarding.journey.waiting')).toBeVisible();

  // 6. the ambassador verifies in the queue
  await openQueue(bot);
  const card = queueCard(bot, NEWBIE_ID);
  await expect(card.getByTestId('verification.queue.introPickups')).toHaveText(/^(\d+) \/ \1$/);
  await card.getByTestId('verification.queue.verify').click();
  await confirmMessage(bot, 'verification.verifyDialog', 'Willkommen, Lea!');
  await expect(card.getByTestId('verification.queue.verifiedTag')).toBeVisible();
  await expectCurrentStep(page, 'PASSPORT');

  // 7. the ambassador creates the passport; the newcomer sees it and the journey is complete
  const job = await apiOf(bot).postJson<{ jobId: string }>(`/api/regions/${ZH}/passports`, {
    userIds: [NEWBIE_ID],
    createPdf: true,
    renew: true,
    paperSize: 'A4',
    notifyUsers: false,
  });
  await expect.poll(async () => (await apiOf(bot).getJson<{ status: string }>(`/api/jobs/${job.jobId}`)).status, { timeout: 30_000 }).toBe('DONE');
  await page.goto('/me/passport');
  await expect(page.getByTestId('verification.passport.status')).toHaveAttribute('data-valid', 'true');
  await page.goto('/onboarding');
  await expect(page.getByTestId('onboarding.journey.complete')).toBeVisible();
  expect((await me.session()).user?.role).toBe('FOODSAVER');
  await page.goto('/dashboard');
  await expect(page.getByTestId('onboarding.bar')).toHaveCount(0);

  // the newcomer got a bell each time others unblocked a step (§11 "waiting for others")
  const bells = await me.getJson<{ items: { type: string }[] }>('/api/bells?size=50');
  expect(bells.items.map((bell) => bell.type)).toContain('ONBOARDING_STEP_UNBLOCKED');
});
