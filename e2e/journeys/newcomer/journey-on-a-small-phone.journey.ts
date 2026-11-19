import { LANGUAGES, type Language, acceptPendingConsents, expectDataState, expectNoMissingKeys, setLanguage } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';
import type { Page } from '@playwright/test';

/**
 * D11.5.4: every step works on a 360 px wide screen and in all five languages. Noah (einab1@local, quiz passed, at
 * his intro pickups) follows his journey on a small phone in each language: journey page, profile step and dashboard
 * load without missing texts or sideways scrolling. The journey page and the dashboard carry the one call to action
 * themselves (the bar only shows the step, design review M7 R2, B18); everywhere else the bar carries it. German is captured, the other
 * languages are checked only (checkpoints are light-de, decision 180); text completeness is `npm run i18n:check`.
 * On the way he looks at the quizzes, named in his language (Q1: the names are data of the quiz module, not i18n
 * keys, so no catalog check can find a missing one).
 */
test.use({ role: 'einab1' });

const QUIZ_NAMES: Record<Language, Record<string, string>> = {
  de: { FOODSAVER: 'Foodsaver-Quiz', STORE_MANAGER: 'Betriebsverantwortlichen-Quiz', AMBASSADOR: 'Botschafter-Quiz', HYGIENE: 'Hygiene-Quiz', FOODSHARER: 'Foodsharer-Quiz', SAVING_FOOD: 'Lebensmittel retten', REPORTS: 'Meldeteam-Quiz', EINAB_GUIDE: 'EinAb-Begleitungs-Quiz' },
  en: { FOODSAVER: 'Foodsaver quiz', STORE_MANAGER: 'Store manager quiz', AMBASSADOR: 'Ambassador quiz', HYGIENE: 'Hygiene quiz', FOODSHARER: 'Foodsharer quiz', SAVING_FOOD: 'Saving food', REPORTS: 'Report team quiz', EINAB_GUIDE: 'EinAb guide quiz' },
  gws: { FOODSAVER: 'Foodsaver-Quiz', STORE_MANAGER: 'Betriebsverantwortliche-Quiz', AMBASSADOR: 'Botschafter-Quiz', HYGIENE: 'Hygiene-Quiz', FOODSHARER: 'Foodsharer-Quiz', SAVING_FOOD: 'Läbensmittel rette', REPORTS: 'Meldeteam-Quiz', EINAB_GUIDE: 'EinAb-Begleitigs-Quiz' },
  fr: { FOODSAVER: 'Quiz foodsaver', STORE_MANAGER: 'Quiz responsable de commerce', AMBASSADOR: 'Quiz ambassadeur', HYGIENE: 'Quiz hygiène', FOODSHARER: 'Quiz foodsharer', SAVING_FOOD: 'Sauver la nourriture', REPORTS: 'Quiz équipe de signalement', EINAB_GUIDE: 'Quiz accompagnateur·rice EinAb' },
  it: { FOODSAVER: 'Quiz foodsaver', STORE_MANAGER: 'Quiz responsabile di negozio', AMBASSADOR: 'Quiz ambasciatore', HYGIENE: 'Quiz igiene', FOODSHARER: 'Quiz foodsharer', SAVING_FOOD: 'Salvare il cibo', REPORTS: 'Quiz team segnalazioni', EINAB_GUIDE: 'Quiz accompagnatore EinAb' },
};

const PAGES = ['/onboarding', '/onboarding/profile', '/dashboard'] as const;
const NAMES: Record<(typeof PAGES)[number], string> = {
  '/onboarding': 'journey-360',
  '/onboarding/profile': 'profile-360',
  '/dashboard': 'dashboard-360',
};

async function noSidewaysScroll(page: Page, what: string): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, what).toBeLessThanOrEqual(0);
}

test('newcomer follows his journey and looks at the quizzes on a 360 px phone in all five languages @D11.5.4 @Q1', async ({ page, apiOf }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'newcomer');
  const me = apiOf(page);
  await acceptPendingConsents(me);
  await page.setViewportSize({ width: 360, height: 780 });

  for (const lang of LANGUAGES) {
    await walk.step(`follows his journey in ${lang}`, async () => {
      // the account's language wins over the device's, so both change (as in the settings)
      await me.patchJson('/api/users/me/settings', { language: lang });
      await setLanguage(page, lang);
      for (const path of PAGES) {
        await page.goto(path);
        await expect(page.locator('fs-data-state[data-state="success"]').first()).toBeVisible();
        await expectNoMissingKeys(page);
        await noSidewaysScroll(page, `${lang} ${path}`);
        if (lang === 'de') await checkpoint(page, NAMES[path]);
      }
      await page.goto('/onboarding');
      await expect(page.getByTestId('onboarding.journey.cta')).toBeVisible();
      await expect(page.getByTestId('onboarding.bar.stepper')).toBeVisible();
      await expect(page.getByTestId('onboarding.bar.stepper.next')).toHaveCount(0);
      // the dashboard shows the next step in its own card; the bar there shows progress only (design review B18)
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.page.nextStepAction')).toBeVisible();
      await expect(page.getByTestId('onboarding.bar.stepper')).toBeVisible();
      await expect(page.getByTestId('onboarding.bar.stepper.next')).toHaveCount(0);

      await page.goto('/quizzes');
      await expectDataState(page, 'quiz.list.state', 'success');
      await expect(page.getByTestId('onboarding.bar.stepper.next')).toBeVisible();
      const cards = page.getByTestId('quiz.list.card');
      const kinds = await cards.evaluateAll((list) => list.map((card) => card.getAttribute('data-kind')!));
      expect(kinds).toEqual(expect.arrayContaining(['FOODSAVER', 'STORE_MANAGER', 'AMBASSADOR', 'HYGIENE', 'FOODSHARER', 'SAVING_FOOD', 'REPORTS']));
      for (const kind of kinds) {
        await expect(page.locator(`[data-testid="quiz.list.card"][data-kind="${kind}"]`).getByTestId('quiz.list.name'), `${lang} ${kind}`).toHaveText(
          QUIZ_NAMES[lang][kind],
          { useInnerText: true },
        );
      }
      await expectNoMissingKeys(page);
      await noSidewaysScroll(page, `${lang} /quizzes`);
    });
  }

  walk.finish({ steps: LANGUAGES.length, clicks: 0, fields: 0 });
});
