import type { Page } from '@playwright/test';

import { expect, expectDataState, expectNoSeriousViolations, seedStore, test } from '../../fixtures';
import { loginWithSwitcher, skipPasskeyNudge } from '../auth/_auth';

/**
 * SV1 surveys (A36, docs/seed-ids.md): seed survey `0001-…01` by bot (co-author orga) with STORE, SINGLE, MULTIPLE,
 * BOOLEAN, TEXT questions and 6 responses; foodsaver has not answered.
 */
const SEED_SURVEY = '00000046-0001-4000-8000-000000000001';

const TYPES = ['TEXT', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'BOOLEAN', 'STORE'];

/** Picks a question type by its position in the select (bot uses gws, so no labels). */
async function chooseType(page: Page, index: number, type: string): Promise<void> {
  await page.getByTestId('surveys.editor.questionType').nth(index).click();
  await page.locator('.p-select-overlay .p-select-option').nth(TYPES.indexOf(type)).click();
  await expect(page.getByTestId('surveys.editor.question').nth(index)).toHaveAttribute('data-type', type);
  await expect(page.locator('.p-select-overlay')).toHaveCount(0);
}

test.use({ seedReset: 'test' });

test('SV1 manager creates survey with all question types and co-author; link with store prefills; respondent submits once; results show rows @SV1 @page', async ({ as, apiOf }) => {
  const bot = await as('bot');
  const s01 = seedStore('S-01');
  const candidates = (await (await apiOf(bot).get('/api/surveys/coauthor-candidates')).json()) as { id: string; displayName: string }[];
  expect(candidates.length).toBeGreaterThan(0);
  const coauthor = candidates[0];

  await bot.goto('/surveys');
  await expectDataState(bot, 'surveys.list.state', 'success');
  await expect(bot.locator(`[data-testid="surveys.list.item"][data-survey-id="${SEED_SURVEY}"]`)).toBeVisible();
  await bot.getByTestId('surveys.list.new').click();
  await expect(bot).toHaveURL(/\/surveys\/new$/);

  // validation: empty title
  await bot.getByTestId('surveys.editor.submit').click();
  await expect(bot.getByTestId('surveys.editor.error')).toHaveAttribute('data-code', 'title');
  await bot.getByTestId('surveys.editor.title').fill('Rückmeldung Lagerraum');
  const labels = bot.getByTestId('surveys.editor.questionLabel');
  await labels.nth(0).fill('Was fehlt dir?');
  for (let i = 0; i < 4; i++) await bot.getByTestId('surveys.editor.addQuestion').click();
  await chooseType(bot, 1, 'SINGLE_CHOICE');
  await labels.nth(1).fill('Wie oft holst du ab?');
  await chooseType(bot, 2, 'MULTIPLE_CHOICE');
  await labels.nth(2).fill('Welche Tage passen?');
  await chooseType(bot, 3, 'BOOLEAN');
  await labels.nth(3).fill('Würdest du mithelfen?');
  await chooseType(bot, 4, 'STORE');
  await labels.nth(4).fill('Betrieb');
  await expect(bot.getByTestId('surveys.editor.storeHint')).toBeVisible();
  const choice = (n: number) => bot.getByTestId('surveys.editor.question').nth(n).getByTestId('surveys.editor.optionInput');
  await choice(1).nth(0).fill('Wöchentlich');
  await choice(1).nth(1).fill('Monatlich');
  await bot.getByTestId('surveys.editor.submit').click();
  await expect(bot.getByTestId('surveys.editor.error')).toHaveAttribute('data-code', 'options');
  await choice(2).nth(0).fill('Montag');
  await choice(2).nth(1).fill('Freitag');
  await bot.getByTestId('surveys.editor.question').nth(0).getByTestId('surveys.editor.questionRequired').click();

  await bot.getByTestId('surveys.editor.coauthorPicker.search').fill(coauthor.displayName.slice(0, 3));
  await bot.locator(`[data-testid="surveys.editor.coauthorPicker.hit"][data-user-id="${coauthor.id}"] [data-testid="surveys.editor.coauthorPicker.add"]`).click();
  await expect(bot.getByTestId('surveys.editor.coauthor')).toHaveCount(1);
  await expectNoSeriousViolations(bot);
  await bot.getByTestId('surveys.editor.submit').click();
  await expect(bot).toHaveURL(/\/surveys\/[0-9a-f-]{36}$/);
  const surveyId = bot.url().split('/').pop()!;
  await expect(bot.getByTestId('surveys.editor.question')).toHaveCount(5);
  await expect(bot.getByTestId('surveys.editor.coauthor')).toHaveAttribute('data-user-id', coauthor.id);

  // the respondent opens the store link: the store is prefilled read-only
  const fs = await as('foodsaver');
  await fs.goto(`/survey/${surveyId}?storeId=${s01.id}`);
  await expectDataState(fs, 'surveys.fill.state', 'success');
  await expect(fs.getByTestId('surveys.fill.store')).toContainText(s01.name);
  await fs.getByTestId('surveys.fill.submit').click();
  await expect(fs.getByTestId('surveys.fill.requiredError')).toBeVisible();
  await fs.getByTestId('surveys.fill.text').fill('Mehr Kisten');
  const q = (n: number) => fs.getByTestId('surveys.fill.question').nth(n);
  await q(1).getByTestId('surveys.fill.option').nth(1).check();
  await q(2).getByTestId('surveys.fill.option').nth(0).check();
  await q(2).getByTestId('surveys.fill.option').nth(1).check();
  await q(3).getByTestId('surveys.fill.yes').check();
  await expectNoSeriousViolations(fs);
  await fs.getByTestId('surveys.fill.submit').click();
  await expect(fs.getByTestId('surveys.fill.thanks')).toBeVisible();
  await fs.reload();
  await expect(fs.getByTestId('surveys.fill.already')).toBeVisible();
  const again = await apiOf(fs).post(`/api/surveys/${surveyId}/responses`, { storeId: s01.id, answers: [] });
  expect(again.status()).toBe(409);

  await bot.getByTestId('surveys.editor.results').click();
  await expectDataState(bot, 'surveys.results.state', 'success');
  const row = bot.getByTestId('surveys.results.row');
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('Mehr Kisten');
  await expect(row).toContainText(s01.name);
  await expect(row).toContainText('Montag, Freitag');
  const download = bot.waitForEvent('download');
  await bot.getByTestId('surveys.results.csv').click();
  expect((await download).suggestedFilename()).toBe(`survey-${surveyId}.csv`);
});

test('SV1 co-author sees results; others are forbidden; delete by owner @SV1', async ({ as }) => {
  const orga = await as('orga');
  await orga.goto(`/surveys/${SEED_SURVEY}/results`);
  await expectDataState(orga, 'surveys.results.state', 'success');
  await expect(orga.getByTestId('surveys.results.row')).toHaveCount(6);
  await expect(orga.getByTestId('surveys.results.count')).toContainText('6');

  const fs = await as('foodsaver');
  await fs.goto(`/surveys/${SEED_SURVEY}/results`);
  await expectDataState(fs, 'surveys.results.state', 'forbidden');

  const bot = await as('bot');
  await bot.goto('/surveys/00000046-0001-4000-8000-000000000002');
  await expectDataState(bot, 'surveys.editor.state', 'notFound');
  await orga.goto('/surveys/00000046-0001-4000-8000-000000000002');
  await orga.getByTestId('surveys.editor.delete').click();
  await orga.locator('.p-confirmdialog .p-confirmdialog-accept-button').click();
  await expect(orga).toHaveURL(/\/surveys$/);
  await expect(orga.locator('[data-testid="surveys.list.item"][data-survey-id="00000046-0001-4000-8000-000000000002"]')).toHaveCount(0);
});

test('SV1 fill page has no app menu and returns after login @SV1 @A2', async ({ as }) => {
  const page = await as(null);
  await skipPasskeyNudge(page);
  const s02 = seedStore('S-02');
  await page.goto(`/survey/${SEED_SURVEY}?storeId=${s02.id}`);
  await expect(page).toHaveURL(/\/login\?returnUrl=/);
  await loginWithSwitcher(page, 'foodsaver', new RegExp(`/survey/${SEED_SURVEY}\\?storeId=${s02.id}$`));
  await expect(page.locator('fs-shell[data-chrome="minimal"]')).toBeVisible();
  await expect(page.getByTestId('nav.dashboard')).toHaveCount(0);
  await expect(page.getByTestId('shell.tabBar')).toHaveCount(0);
  await expectDataState(page, 'surveys.fill.state', 'success');
  await expect(page.getByTestId('surveys.fill.title')).toHaveText('Zufriedenheit Abholungen 2026');
  await expect(page.getByTestId('surveys.fill.store')).toContainText(s02.name);
  await expectNoSeriousViolations(page);

  // without the store link the STORE question cannot be answered
  await page.goto(`/survey/${SEED_SURVEY}`);
  await expect(page.getByTestId('surveys.fill.storeMissing')).toBeVisible();
  await expect(page.getByTestId('surveys.fill.submit').locator('button')).toBeDisabled();

  // a respondent who already answered sees the info state; unknown survey → not found
  const jumper = await as('jumper');
  await jumper.goto(`/survey/${SEED_SURVEY}?storeId=${s02.id}`);
  await expect(jumper.getByTestId('surveys.fill.already')).toBeVisible();
  await jumper.goto('/survey/00000046-0001-4000-8000-0000000000ff');
  await expectDataState(jumper, 'surveys.fill.state', 'notFound');
});
