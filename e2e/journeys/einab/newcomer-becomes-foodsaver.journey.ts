import type { Page } from '@playwright/test';
import { type ApiClient, type Role, acceptPendingConsents, expectStatus, journeyIndicator } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { ZH, setIntroSettings } from '../../fixtures/flows/einab';
import { acceptPrivacy, playSession, solutionKey } from '../../fixtures/flows/quiz';
import { confirmMessage, openQueue, queueCard } from '../../fixtures/flows/verification';
import { Walk } from '../../fixtures/persona';

/**
 * EA8 / §11: the whole newcomer journey of one seed user, newbie@local → verified foodsaver with passport. Lea first
 * looks for intro pickups without a region and gets the region picker. What she does in the browser is clicked
 * (profile, journey page, first EinAb booking, her progress, passport page); the learning chapters, the quiz (the
 * newcomer quiz journey's business) and what other people do (guides mark EinAbs done, the ambassador confirms the
 * newcomer meeting, verifies and creates the passport) go through their own sessions or the API. Rules behind the
 * progress: NewcomerProgressServiceTest (stages and next steps).
 */

const NEWBIE_ID = '00000001-0001-4000-8000-000000000001';

interface JourneyDto {
  complete: boolean;
  currentStep?: string;
  steps: { key: string; status: string }[];
}

interface Bookable {
  id: string;
  startAt: string;
  slotId?: string;
  bookable: boolean;
  visitFairteiler: boolean;
  notBookableReason?: string;
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

test.use({ role: 'newbie' });

test('newcomer goes from sign-up through her intro pickups to a verified foodsaver with passport @EA1 @EA8 @D11.5.2', async ({
  page,
  as,
  apiOf,
  dev,
  clock,
}, testInfo) => {
  test.setTimeout(240_000);
  const walk = new Walk(page, testInfo, 'newcomer');
  const me = apiOf(page);
  const orga = apiOf(await as('orga'));
  const bot = await as('bot');
  const users = await dev.users();
  const roleOf = (userId: string): Role => users.find((user) => user.id === userId)!.email.replace('@local', '') as Role;
  // the legal agreement stays for the quiz confirmation step
  await acceptPrivacy(me);

  await walk.step('looks for intro pickups before choosing a region', async () => {
    await page.goto('/intro-pickups');
    await expect(page.getByTestId('introPickups.bookable.noRegion')).toBeVisible();
    await expect(page.getByTestId('introPickups.bookable.regionPicker')).toBeVisible();
    await checkpoint(page, 'no-region');
  });

  await walk.step('fills in her profile and region', async () => {
    await expectCurrentStep(page, 'PROFILE_REGION');
    await checkpoint(page, 'journey-start');
    await walk.click(page.getByTestId('onboarding.journey.cta'));
    await expect(page).toHaveURL(/\/onboarding\/profile$/);
    await walk.fill(page.getByTestId('onboarding.profile.firstName'), 'Lea');
    await walk.fill(page.getByTestId('onboarding.profile.lastName'), 'Brunner');
    await walk.fill(page.locator('#ob-phone'), '079 123 45 67');
    await walk.fill(page.getByTestId('onboarding.profile.picker.postcode'), '8004');
    await walk.click(page.getByTestId('onboarding.profile.picker.suggestion.zuerich-stadt'));
    await walk.click(page.getByTestId('onboarding.profile.save'));
    await expect(page.getByTestId('onboarding.journey.current')).toHaveAttribute('data-step', 'LEARN');
  });

  await walk.step('starts learning', async () => {
    await walk.click(page.getByTestId('onboarding.journey.cta'));
    await expect(page).toHaveURL(/\/learn\//);
  });
  walk.note('learning chapters and the foodsaver quiz are done through the API here (newcomer quiz journey)');
  for (const chapter of await me.getJson<{ key: string }[]>('/api/public/content/learning')) {
    await me.postJson(`/api/onboarding/learning/${chapter.key}/read`);
  }
  await playSession(me, 'FOODSAVER', { correct: true, key: await solutionKey(orga, 'FOODSAVER') });
  await acceptPendingConsents(me);
  await me.postJson('/api/quizzes/FOODSAVER/confirm');
  // two seed guides give Zürich's upcoming EinAbs: the ambassador allows a second one with the same guide
  await setIntroSettings(apiOf(bot), { preventDuplicateGuideBookings: false });

  /** Books an EinAb (the Fairteiler visit first), then the guide marks it done two hours after its start. */
  async function attend(inBrowser: boolean): Promise<void> {
    const list = await me.getJson<{ items: Bookable[] }>(`/api/regions/${ZH}/intro-pickups/bookable?size=50`);
    const candidates = list.items.filter((item) => item.bookable && item.slotId);
    const einab = candidates.find((item) => item.visitFairteiler) ?? candidates[0];
    expect(einab, `bookable EinAb: ${JSON.stringify(list.items.map((i) => [i.startAt, i.notBookableReason]))}`).toBeDefined();
    if (inBrowser) {
      const card = page.locator(`[data-testid="introPickups.bookable.card"][data-id="${einab.id}"]`);
      await walk.click(card.getByTestId('introPickups.bookable.book'));
      await walk.click(page.locator('.p-confirmdialog-accept-button'));
      await expect(page.getByTestId('introPickups.bookable.success')).toBeVisible();
      await checkpoint(page, 'first-booking');
    } else {
      await me.postJson(`/api/intro-pickups/slots/${einab.slotId}/book`);
    }
    await expect.poll(async () => (await journey(me)).currentStep).toBe('INTRO_PICKUPS');
    await clock.fixServer(new Date(Date.parse(einab.startAt) + 2 * 3600_000).toISOString());
    const guide = apiOf(await as(roleOf(einab.guide!.id)));
    await guide.postJson(`/api/intro-pickups/slots/${einab.slotId}/done`);
  }

  await walk.step('books her first intro pickup from the journey page', async () => {
    await expectCurrentStep(page, 'INTRO_PICKUPS');
    await walk.click(page.getByTestId('onboarding.journey.cta'));
    await expect(page.getByTestId('introPickups.bookable.state')).toHaveAttribute('data-state', 'success');
    await attend(true);
  });

  await walk.step('sees her progress after the first one', async () => {
    await walk.click(page.getByTestId('introPickups.bookable.toMyBookings'));
    await expect(page).toHaveURL(/\/me\/intro-pickups$/);
    const progress = page.getByTestId('introPickups.my.progress');
    await expect(progress).toHaveAttribute('data-done', '1');
    await expect(progress).toHaveAttribute('data-required', '3');
    await expect(page.getByTestId('introPickups.my.nextStep')).toHaveAttribute('data-step', 'BOOK');
    await expect(page.getByTestId('introPickups.my.pastRow')).toHaveCount(1);
    await expect(page.getByTestId('introPickups.my.counted')).toHaveCount(1);
    await checkpoint(page, 'progress-one-done');
  });

  // the next ones are booked and attended like the first
  for (let round = 1; round < 6 && (await journey(me)).steps.find((s) => s.key === 'INTRO_PICKUPS')!.status !== 'DONE'; round++) {
    await attend(false);
  }

  await walk.step('waits for the newcomer meeting and the ID check', async () => {
    await page.goto('/me/intro-pickups');
    await expect(page.getByTestId('introPickups.my.progress')).toHaveAttribute('data-done', '3');
    await expect(page.getByTestId('introPickups.my.nextStep')).toHaveAttribute('data-step', 'WAIT');
    await expectCurrentStep(page, 'newcomer-meeting');
    // Zürich's own extra step (seed region setting): the ambassador confirms the newcomer meeting
    const confirmed = await apiOf(bot).fetch('POST', `/api/onboarding/regions/${ZH}/users/${NEWBIE_ID}/steps/newcomer-meeting/confirm`);
    await expectStatus(confirmed, [200, 204], 'confirm region step');
    await expectCurrentStep(page, 'ID_CHECK');
    await expect(page.getByTestId('onboarding.journey.waiting')).toBeVisible();
    await checkpoint(page, 'waiting-for-id-check');
  });

  await test.step('the ambassador checks her ID and verifies her in the queue', async () => {
    await openQueue(bot);
    const card = queueCard(bot, NEWBIE_ID);
    await expect(card.getByTestId('verification.queue.introPickups')).toHaveText(/^(\d+) \/ \1$/);
    // changed on purpose (C1): the ID check comes first, verifying needs a confirmed identity
    await card.getByTestId('verification.queue.confirmIdentity').click();
    await expect(card.getByTestId('verification.queue.identityTag')).toBeVisible();
    await card.getByTestId('verification.queue.verify').click();
    await confirmMessage(bot, 'verification.verifyDialog', 'Willkommen, Lea!');
    await expect(card.getByTestId('verification.queue.verifiedTag')).toBeVisible();
    await expectCurrentStep(page, 'PASSPORT');
    const job = await apiOf(bot).postJson<{ jobId: string }>(`/api/regions/${ZH}/passports`, {
      userIds: [NEWBIE_ID],
      createPdf: true,
      renew: true,
      paperSize: 'A4',
      notifyUsers: false,
    });
    await expect.poll(async () => (await apiOf(bot).getJson<{ status: string }>(`/api/jobs/${job.jobId}`)).status, { timeout: 30_000 }).toBe('DONE');
  });

  await walk.step('finds her passport and the journey complete', async () => {
    await page.goto('/me/passport');
    await expect(page.getByTestId('verification.passport.status')).toHaveAttribute('data-valid', 'true');
    await page.goto('/onboarding');
    await expect(page.getByTestId('onboarding.journey.complete')).toBeVisible();
    await checkpoint(page, 'journey-complete');
    expect((await me.session()).user?.role).toBe('FOODSAVER');
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    // no journey progress any more: the bar on phones, the side menu's journey item and meter on desktop
    await expect(journeyIndicator(page)).toHaveCount(0);
    await expect(page.getByTestId('nav.onboarding')).toHaveCount(0);
  });

  // she got a bell each time others unblocked a step (§11 "waiting for others")
  const bells = await me.getJson<{ items: { type: string }[] }>('/api/bells?size=50');
  expect(bells.items.map((bell) => bell.type)).toContain('ONBOARDING_STEP_UNBLOCKED');

  walk.finish({ steps: 7, clicks: 8, fields: 4 });
});
