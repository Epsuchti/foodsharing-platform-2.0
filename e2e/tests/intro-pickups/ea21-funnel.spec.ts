import { expect, test } from '../../fixtures';
import { ZH } from './_intro';

/** EA21: newcomer funnel – names for user managers, counts only for guides, gap severities. */
test.use({ role: 'bot' });

test('EA21 funnel with names for admin, counts for guide, gap severities @EA21 @page', async ({ page, as, api }) => {
  await api.loginAs('bot');
  const funnel = await api.getJson<{
    targetIntroPickups: number;
    steps: { stage: string; introPickupCount?: number; userCount: number }[];
    gaps: { severity: string }[];
  }>(
    `/api/regions/${ZH}/newcomer-funnel?days=365`,
  );
  const people = funnel.steps.reduce((sum, step) => sum + step.userCount, 0);
  expect(people).toBeGreaterThan(0);

  // user manager (bot) via the navigation link with the home region
  const bot = page;
  await bot.goto('/regions/home/newcomers');
  await expect(bot).toHaveURL(new RegExp(`/regions/${ZH}/newcomers$`));
  await expect(bot.getByTestId('introPickups.funnel.state')).toHaveAttribute('data-state', 'success');
  await bot.getByTestId('introPickups.funnel.window').click();
  await bot.getByRole('option').nth(3).click(); // 365 days
  await expect(bot.getByTestId('introPickups.funnel.step')).toHaveCount(funnel.steps.length);
  // review 2026-11-10 A5: one IN_PROGRESS step per EinAb count, each labelled with its count (bot speaks gws:
  // "I de Iifüehrig: 1 vo 3")
  const inProgress = funnel.steps.filter((step) => step.stage === 'IN_PROGRESS');
  expect(inProgress.length).toBeGreaterThan(0);
  const labels = bot.locator('[data-testid="introPickups.funnel.step"][data-stage="IN_PROGRESS"] strong');
  await expect(labels).toHaveText(
    inProgress.map((step) => new RegExp(`: ${step.introPickupCount} \\S+ ${funnel.targetIntroPickups}\\s*$`)),
  );
  await expect(bot.getByTestId('introPickups.funnel.names').first()).toBeVisible();
  await bot.getByTestId('introPickups.funnel.names').first().locator('summary').click();
  await expect(bot.getByTestId('introPickups.funnel.person').first()).toBeVisible();
  const gaps = bot.getByTestId('introPickups.funnel.gap');
  await expect(gaps).toHaveCount(funnel.gaps.length);
  for (const severity of new Set(funnel.gaps.map((gap) => gap.severity))) {
    await expect(bot.locator(`[data-testid="introPickups.funnel.gap"][data-severity="${severity}"]`).first()).toBeVisible();
  }

  // guide: counts only, no names
  const guide = await as('guide');
  await guide.goto(`/regions/${ZH}/newcomers`);
  await expect(guide.getByTestId('introPickups.funnel.state')).toHaveAttribute('data-state', 'success');
  await expect(guide.getByTestId('introPickups.funnel.step').first()).toBeVisible();
  await expect(guide.getByTestId('introPickups.funnel.names')).toHaveCount(0);
});
