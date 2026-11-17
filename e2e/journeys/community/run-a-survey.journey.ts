import type { Page } from '@playwright/test';
import { expectDataState, expectNoOverflow, seedStore } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { loginWithSwitcher, skipPasskeyNudge } from '../../fixtures/flows/auth';
import { Walk } from '../../fixtures/persona';

/**
 * Surveys (A36). Bot (bot@local, may manage surveys) builds a survey with every question type and a co-author; the
 * editor refuses a missing title and a choice question without options. Jonas (jumper@local) answers through his own
 * session with the S-01 link, and Bot reads the row in the results and downloads them as CSV. Sarah (foodsaver@local)
 * opens the link of the seeded survey for S-02 while logged out, signs in, answers on the bare fill page and sees that
 * she already answered when she comes back. Definition and answer rules are unit-tested (SurveyServiceTest,
 * surveys.resource.spec.ts); who may read results, delete and answer twice too (SurveyServiceAccessTest).
 */
const SEED_SURVEY = '00000046-0001-4000-8000-000000000001';
const TYPES = ['TEXT', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'BOOLEAN', 'STORE'];

interface SurveyQuestion {
  id: string;
  type: string;
  options: string[];
}

test.describe('survey manager', () => {
  test.use({ role: 'bot' });

  test('survey manager builds a survey with every question type and reads the answers @SV1', async ({ page, as, apiOf }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'survey manager');
    const s01 = seedStore('S-01');
    const labels = page.getByTestId('surveys.editor.questionLabel');
    const question = (n: number) => page.getByTestId('surveys.editor.question').nth(n);

    /** Picks a question type by its position in the select. */
    async function chooseType(p: Page, index: number, type: string): Promise<void> {
      await walk.click(p.getByTestId('surveys.editor.questionType').nth(index));
      await walk.click(p.locator('.p-select-overlay .p-select-option').nth(TYPES.indexOf(type)));
      await expect(question(index)).toHaveAttribute('data-type', type);
      await expect(p.locator('.p-select-overlay')).toHaveCount(0);
    }

    await walk.step('opens the surveys and starts a new one', async () => {
      // the side menu has no survey item for a region-level survey permission: Bot keeps the link
      await page.goto('/surveys');
      await expectDataState(page, 'surveys.list.state', 'success');
      await expect(page.locator(`[data-testid="surveys.list.item"][data-survey-id="${SEED_SURVEY}"]`)).toBeVisible();
      await checkpoint(page, 'surveys-list');
      await walk.click(page.getByTestId('surveys.list.new'));
      await expect(page).toHaveURL(/\/surveys\/new$/);
      await walk.click(page.getByTestId('surveys.editor.submit'));
      await expect(page.getByTestId('surveys.editor.error')).toHaveAttribute('data-code', 'title');
    });

    await walk.step('adds one question of every type', async () => {
      await walk.fill(page.getByTestId('surveys.editor.title'), 'Rückmeldung Lagerraum');
      await walk.fill(labels.nth(0), 'Was fehlt dir?');
      for (let i = 0; i < 4; i++) await walk.click(page.getByTestId('surveys.editor.addQuestion'));
      await chooseType(page, 1, 'SINGLE_CHOICE');
      await walk.fill(labels.nth(1), 'Wie oft holst du ab?');
      await chooseType(page, 2, 'MULTIPLE_CHOICE');
      await walk.fill(labels.nth(2), 'Welche Tage passen?');
      await chooseType(page, 3, 'BOOLEAN');
      await walk.fill(labels.nth(3), 'Würdest du mithelfen?');
      await chooseType(page, 4, 'STORE');
      await walk.fill(labels.nth(4), 'Betrieb');
      await expect(page.getByTestId('surveys.editor.storeHint')).toBeVisible();
      const choice = (n: number) => question(n).getByTestId('surveys.editor.optionInput');
      await walk.fill(choice(1).nth(0), 'Wöchentlich');
      await walk.fill(choice(1).nth(1), 'Monatlich');
      // the multiple-choice question has no options yet
      await walk.click(page.getByTestId('surveys.editor.submit'));
      await expect(page.getByTestId('surveys.editor.error')).toHaveAttribute('data-code', 'options');
      await checkpoint(page, 'editor-options-missing');
      await walk.fill(choice(2).nth(0), 'Montag');
      await walk.fill(choice(2).nth(1), 'Freitag');
      await walk.click(question(0).getByTestId('surveys.editor.questionRequired'));
    });

    let surveyId = '';
    await walk.step('adds a co-author and saves', async () => {
      const candidates = await apiOf(page).getJson<{ id: string; displayName: string }[]>('/api/surveys/coauthor-candidates');
      const coauthor = candidates[0];
      await walk.fill(page.getByTestId('surveys.editor.coauthorPicker.search'), coauthor.displayName.slice(0, 3));
      await walk.click(
        page.locator(`[data-testid="surveys.editor.coauthorPicker.hit"][data-user-id="${coauthor.id}"] [data-testid="surveys.editor.coauthorPicker.add"]`),
      );
      await expect(page.getByTestId('surveys.editor.coauthor')).toHaveCount(1);
      await walk.click(page.getByTestId('surveys.editor.submit'));
      await expect(page).toHaveURL(/\/surveys\/[0-9a-f-]{36}$/);
      surveyId = page.url().split('/').pop()!;
      await expect(page.getByTestId('surveys.editor.question')).toHaveCount(5);
      await expect(page.getByTestId('surveys.editor.coauthor')).toHaveAttribute('data-user-id', coauthor.id);
      await checkpoint(page, 'editor-saved');
    });

    await walk.step('reads Jonas\'s answer and downloads the results', async () => {
      // Jonas answers with the S-01 link (his own session)
      const jumper = apiOf(await as('jumper'));
      const survey = await apiOf(page).getJson<{ questions: SurveyQuestion[] }>(`/api/surveys/${surveyId}`);
      const values: Record<string, string[]> = {
        TEXT: ['Mehr Kisten'],
        SINGLE_CHOICE: ['Monatlich'],
        MULTIPLE_CHOICE: ['Montag', 'Freitag'],
        BOOLEAN: ['true'],
      };
      const answers = survey.questions.filter((q) => q.type !== 'STORE').map((q) => ({ questionId: q.id, values: values[q.type] }));
      const answered = await jumper.post(`/api/surveys/${surveyId}/responses`, { storeId: s01.id, answers });
      expect(answered.status(), await answered.text()).toBeLessThan(300);

      await walk.click(page.getByTestId('surveys.editor.results'));
      await expectDataState(page, 'surveys.results.state', 'success');
      const row = page.getByTestId('surveys.results.row');
      await expect(row).toHaveCount(1);
      await expect(row).toContainText('Mehr Kisten');
      await expect(row).toContainText(s01.name);
      await expect(row).toContainText('Montag, Freitag');
      await checkpoint(page, 'results', { mask: [page.locator('time')] });
      const download = page.waitForEvent('download');
      await walk.click(page.getByTestId('surveys.results.csv'));
      expect((await download).suggestedFilename()).toBe(`survey-${surveyId}.csv`);
    });

    // measured 2026-11-15, desktop and phone
    walk.finish({ steps: 4, clicks: 20, fields: 11 });
  });
});

test.describe('respondent', () => {
  test('respondent opens a survey link, signs in and answers once @SV1 @A2', async ({ page, as }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'respondent');
    const s02 = seedStore('S-02');
    // pins Sarah's account to German and the light theme before she signs in on `page`
    await as('foodsaver');
    await skipPasskeyNudge(page);

    await walk.step('opens the link and signs in', async () => {
      await page.goto(`/survey/${SEED_SURVEY}?storeId=${s02.id}`);
      await expect(page).toHaveURL(/\/login\?returnUrl=/);
      await loginWithSwitcher(page, 'foodsaver', new RegExp(`/survey/${SEED_SURVEY}\\?storeId=${s02.id}$`));
      walk.count(1);
    });

    await walk.step('answers on the bare fill page', async () => {
      // no app menu: the fill page stands on its own
      await expect(page.locator('fs-shell[data-chrome="minimal"]')).toBeVisible();
      await expect(page.getByTestId('nav.dashboard')).toHaveCount(0);
      await expect(page.getByTestId('shell.tabBar')).toHaveCount(0);
      await expectDataState(page, 'surveys.fill.state', 'success');
      await expect(page.getByTestId('surveys.fill.title')).toHaveText('Zufriedenheit Abholungen 2026');
      await expect(page.getByTestId('surveys.fill.store')).toContainText(s02.name);
      await checkpoint(page, 'fill-page');
      // on a 360px phone every question wraps inside the screen, nothing is cut off sideways
      const size = page.viewportSize();
      await page.setViewportSize({ width: 360, height: 740 });
      await expectNoOverflow(page, 'survey fill page at 360px');
      if (size) await page.setViewportSize(size);
      await walk.click(page.getByTestId('surveys.fill.submit'));
      await expect(page.getByTestId('surveys.fill.requiredError').first()).toBeVisible();
      await checkpoint(page, 'fill-required');
      const questions = page.getByTestId('surveys.fill.question');
      const count = await questions.count();
      for (let i = 0; i < count; i++) {
        const q = questions.nth(i);
        const type = await q.getAttribute('data-type');
        if (type === 'SINGLE_CHOICE') await walk.check(q.getByTestId('surveys.fill.option').first());
        if (type === 'MULTIPLE_CHOICE') await walk.check(q.getByTestId('surveys.fill.option').first());
        if (type === 'BOOLEAN') await walk.check(q.getByTestId('surveys.fill.yes'));
        if (type === 'TEXT') await walk.fill(q.getByTestId('surveys.fill.text'), 'Mehr Abholungen am Samstag');
      }
      await walk.click(page.getByTestId('surveys.fill.submit'));
      await expect(page.getByTestId('surveys.fill.thanks')).toBeVisible();
      await checkpoint(page, 'fill-thanks');
    });

    await walk.step('comes back to the link later', async () => {
      await page.reload();
      await expect(page.getByTestId('surveys.fill.already')).toBeVisible();
      await checkpoint(page, 'fill-already');
    });

    // measured 2026-11-15, desktop and phone
    walk.finish({ steps: 3, clicks: 6, fields: 1 });
  });
});
