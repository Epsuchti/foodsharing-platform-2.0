import { LANGUAGES, expect, expectNoMissingKeys, setLanguage, test } from '../../fixtures';
import { acceptPrivacy } from './_quiz';

/** Q1: the seven quizzes in five languages, locked quizzes explain why. */

test.use({ seedReset: 'test' });

const NAMES: Record<string, string[]> = {
  de: ['Foodsaver-Quiz', 'Betriebsverantwortlichen-Quiz', 'Botschafter-Quiz', 'Hygiene-Quiz', 'Foodsharer-Quiz', 'Lebensmittel retten', 'Meldeteam-Quiz'],
  en: ['Foodsaver quiz', 'Store manager quiz', 'Ambassador quiz', 'Hygiene quiz', 'Foodsharer quiz', 'Saving food', 'Report team quiz'],
  gws: ['Foodsaver-Quiz', 'Betriebsverantwortliche-Quiz', 'Botschafter-Quiz', 'Hygiene-Quiz', 'Foodsharer-Quiz', 'Läbensmittel rette', 'Meldeteam-Quiz'],
  fr: ['Quiz foodsaver', 'Quiz responsable de commerce', 'Quiz ambassadeur', 'Quiz hygiène', 'Quiz foodsharer', 'Sauver la nourriture', 'Quiz équipe de signalement'],
  it: ['Quiz foodsaver', 'Quiz responsabile di negozio', 'Quiz ambasciatore', 'Quiz igiene', 'Quiz foodsharer', 'Salvare il cibo', 'Quiz team segnalazioni'],
};

test('Q1 all seven quizzes are listed in five languages @Q1 @page', async ({ as, apiOf }) => {
  const page = await as('orga');
  const api = apiOf(page);
  for (const lang of LANGUAGES) {
    await api.patchJson('/api/users/me/settings', { language: lang });
    await setLanguage(page, lang);
    await page.goto('/quizzes');
    await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'success');
    const cards = page.getByTestId('quiz.list.card');
    await expect(cards).toHaveCount(7);
    await expect(page.getByTestId('quiz.list.name')).toHaveText(NAMES[lang], { useInnerText: true, ignoreCase: false });
    await expectNoMissingKeys(page);
  }
});

test('Q1 store manager quiz locked for unverified foodsharer @Q1', async ({ as, apiOf }) => {
  const page = await as('newbie');
  await acceptPrivacy(apiOf(page));
  await page.goto('/quizzes');
  const storeManager = page.locator('[data-testid="quiz.list.card"][data-kind="STORE_MANAGER"]');
  await expect(storeManager.getByTestId('quiz.list.locked')).toBeVisible();
  await expect(storeManager.getByTestId('quiz.list.open')).toHaveCount(0);
  const foodsaver = page.locator('[data-testid="quiz.list.card"][data-kind="FOODSAVER"]');
  await expect(foodsaver.getByTestId('quiz.list.status')).toHaveAttribute('data-phase', 'failed'); // seed: one failed try yesterday
  await foodsaver.getByTestId('quiz.list.open').click();
  await expect(page).toHaveURL(/\/quizzes\/foodsaver$/);
  await expect(page.getByTestId('quiz.intro.start')).toBeVisible();

  // the API agrees: starting the locked quiz is refused
  const refused = await apiOf(page).fetch('POST', '/api/quizzes/STORE_MANAGER/sessions', { timed: false });
  expect(refused.status()).toBe(403);
});
