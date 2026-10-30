import type { Page } from '@playwright/test';
import {
  acceptPendingConsents,
  expect,
  expectDataState,
  expectErrorState,
  injectApiError,
  pickOption,
  regionId,
  test,
} from '../../fixtures';
import { playSession, solutionKey } from '../quiz/_quiz';

/**
 * AC1 achievements (plan-m7 Q9, A52). Seed (`168-achievement-seed.yaml`): global HYGIENE_CERTIFICATE `…01` and
 * KAM_CERTIFICATE `…02` (system, by orga); zuerich-stadt `…03` Fairteiler-Putzheld (SCOPE), `…04` Sommerfest-Helfer
 * (STORE_MANAGERS), `…05` Vertrauensperson (HIDDEN, awarded to guide by bot). bot administers zuerich-stadt (UI in
 * gws, no gws text for new achievements, so it reads the German base); guide reads French, foodsaver German.
 */
const ZURICH = regionId('zuerich-stadt');
const ACHIEVEMENTS = {
  hygiene: '00000051-0001-4000-8000-000000000001',
  kam: '00000051-0001-4000-8000-000000000002',
  putzheld: '00000051-0001-4000-8000-000000000003',
  sommerfest: '00000051-0001-4000-8000-000000000004',
  trust: '00000051-0001-4000-8000-000000000005',
} as const;
const COOP = '00000020-0003-4000-8000-000000000001';

const card = (page: Page, id: string) => page.locator(`[data-testid="achievements.card"][data-achievement-id="${id}"]`);
const awardItem = (page: Page, userId: string) => page.locator(`[data-testid="achievements.awards.item"][data-user-id="${userId}"]`);

async function confirm(page: Page): Promise<void> {
  await page.locator('.p-confirmdialog-accept-button').click();
  await expect(page.locator('.p-confirmdialog')).toHaveCount(0);
}

test.describe('region administrator', () => {
  test.use({ seedReset: 'test', role: 'bot' });

  test('AC1 region admin defines achievement with translations, awards and revokes it; visibility respected on profile @AC1 @page', async ({
    page,
    as,
    apiOf,
    profiles,
  }) => {
    await acceptPendingConsents(apiOf(page));
    const guideId = await profiles.userId('guide');

    // bot sees every regional achievement, the HIDDEN one included
    await page.goto(`/regions/${ZURICH}`);
    await page.getByTestId('regions.page.tab.achievements').click();
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/achievements$`));
    await expectDataState(page, 'achievements.board.state', 'success');
    for (const id of [ACHIEVEMENTS.putzheld, ACHIEVEMENTS.sommerfest, ACHIEVEMENTS.trust]) await expect(card(page, id)).toBeVisible();
    await expect(card(page, ACHIEVEMENTS.trust)).toHaveAttribute('data-visibility', 'HIDDEN');

    // define with German base, French and English translations, an icon, visibility and validity
    await page.getByTestId('achievements.board.define').click();
    await page.getByTestId('achievements.form.names.field.de').fill('Velokurier-Held');
    await page.getByTestId('achievements.form.names.tab.fr').click();
    await page.getByTestId('achievements.form.names.field.fr').fill('Héros coursier à vélo');
    await page.getByTestId('achievements.form.names.tab.en').click();
    await page.getByTestId('achievements.form.names.field.en').fill('Bike courier hero');
    await page.getByTestId('achievements.form.descriptions.field.de').fill('Liefert gerettete Lebensmittel mit dem Velo aus.');
    await page.locator('[data-testid="achievements.form.icon"][data-icon="truck"]').click();
    await pickOption(page, 'achievements.form.visibility', 'SCOPE');
    await page.getByTestId('achievements.form.validity').fill('30');
    await page.getByTestId('achievements.form.submit').click();
    await expect(page.getByTestId('achievements.form.names.field.de')).toBeHidden();
    const created = page.getByTestId('achievements.card').filter({ hasText: 'Velokurier-Held' });
    await expect(created).toHaveCount(1);
    await expect(created.locator('i.pi-truck')).toBeVisible();
    const achievementId = (await created.getAttribute('data-achievement-id'))!;

    // award it to guide
    await created.getByTestId('achievements.card.award').click();
    await page.getByTestId('achievements.award.picker.search').fill('Julien');
    await page.locator(`[data-testid="achievements.award.picker.hit"][data-user-id="${guideId}"]`).getByTestId('achievements.award.picker.add').click();
    await expect(page.getByTestId('achievements.award.recipient')).toHaveAttribute('data-user-id', guideId);
    await page.getByTestId('achievements.award.notice').fill('Danke für die Velotouren!');
    await page.getByTestId('achievements.award.submit').click();
    await expect(page.getByTestId('achievements.award.submit')).toBeHidden();

    // guide (French) sees it on the own profile, but never the HIDDEN "Vertrauensperson" award
    const guide = await as('guide');
    await acceptPendingConsents(apiOf(guide));
    await guide.goto(`/profile/${guideId}`);
    const grid = guide.getByTestId('profiles.page.achievements');
    await expect(grid).toContainText('Héros coursier à vélo');
    await expect(grid).not.toContainText('Personne de confiance');
    await expect(grid).not.toContainText('Vertrauensperson');
    // a member of the region (German) sees the SCOPE award on guide's profile, and no HIDDEN definition on the tab
    const foodsaver = await as('foodsaver');
    await acceptPendingConsents(apiOf(foodsaver));
    await foodsaver.goto(`/profile/${guideId}`);
    await expect(foodsaver.getByTestId('profiles.page.achievements')).toContainText('Velokurier-Held');
    await foodsaver.goto(`/regions/${ZURICH}/achievements`);
    await expectDataState(foodsaver, 'achievements.board.state', 'success');
    await expect(card(foodsaver, achievementId)).toBeVisible();
    await expect(card(foodsaver, ACHIEVEMENTS.trust)).toHaveCount(0);
    await expect(foodsaver.getByTestId('achievements.board.define')).toHaveCount(0);
    await expect(foodsaver.getByTestId('achievements.card.award')).toHaveCount(0);

    // made HIDDEN, the award disappears for the recipient and for members
    await card(page, achievementId).getByTestId('achievements.card.edit').click();
    await expect(page.getByTestId('achievements.form.names.field.de')).toHaveValue('Velokurier-Held');
    await pickOption(page, 'achievements.form.visibility', 'HIDDEN');
    await page.getByTestId('achievements.form.submit').click();
    await expect(page.getByTestId('achievements.form.names.field.de')).toBeHidden();
    await expect(card(page, achievementId)).toHaveAttribute('data-visibility', 'HIDDEN');
    await guide.reload();
    await expect(guide.getByTestId('profiles.page.achievements')).toBeVisible();
    await expect(guide.getByTestId('profiles.page.achievements')).not.toContainText('Héros coursier à vélo');
    await foodsaver.goto(`/profile/${guideId}`);
    await expect(foodsaver.getByTestId('profiles.page.achievements')).toBeVisible();
    await expect(foodsaver.getByTestId('profiles.page.achievements')).not.toContainText('Velokurier-Held');

    // the awards list: edit end date and notice, then revoke
    await card(page, achievementId).getByTestId('achievements.card.awards').click();
    await expectDataState(page, 'achievements.awards.state', 'success');
    const item = awardItem(page, guideId);
    await expect(item).toHaveAttribute('data-status', 'active');
    await expect(item.getByTestId('achievements.awards.notice')).toHaveText('Danke für die Velotouren!');
    // 30 validity days from the seed day (2026-11-02)
    await expect(item.getByTestId('achievements.awards.validUntil')).toHaveAttribute('data-valid-until', '2026-12-02');
    await item.getByTestId('achievements.awards.edit').click();
    await item.getByTestId('achievements.awards.editValidUntil').fill('2027-03-31');
    await item.getByTestId('achievements.awards.editNotice').fill('Velotouren 2026');
    await item.getByTestId('achievements.awards.editSave').click();
    await expect(item.getByTestId('achievements.awards.validUntil')).toHaveAttribute('data-valid-until', '2027-03-31');
    await expect(item.getByTestId('achievements.awards.notice')).toHaveText('Velotouren 2026');
    await item.getByTestId('achievements.awards.revoke').click();
    await confirm(page);
    await expect(item).toHaveAttribute('data-status', 'revoked');
    await expect(item.getByTestId('achievements.awards.revoke')).toHaveCount(0);
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('achievements.awards.list')).toBeHidden();

    // delete the definition
    await card(page, achievementId).getByTestId('achievements.card.delete').click();
    await confirm(page);
    await expect(card(page, achievementId)).toHaveCount(0);
  });

  test('AC1 achievement and award lists show error states with retry @AC1', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    // the retry interceptor repeats GETs twice: three failures reach the error state
    const injection = await injectApiError(page, { url: '/api/achievements', status: 503, times: 3 });
    await page.goto(`/regions/${ZURICH}/achievements`);
    const board = await expectErrorState(page, 'achievements.board.state');
    expect(injection.calls()).toBe(3);
    await board.retry.click();
    await expectDataState(page, 'achievements.board.state', 'success');

    await injectApiError(page, { url: `/api/achievements/${ACHIEVEMENTS.putzheld}/awards`, status: 503, times: 3 });
    await card(page, ACHIEVEMENTS.putzheld).getByTestId('achievements.card.awards').click();
    const awards = await expectErrorState(page, 'achievements.awards.state');
    await awards.retry.click();
    await expectDataState(page, 'achievements.awards.state', 'success');
    await expect(page.getByTestId('achievements.awards.item')).not.toHaveCount(0);
  });
});

test.describe('automatic awards', () => {
  test.use({ seedReset: 'test', role: 'orga' });

  test('AC1 hygiene quiz pass auto-awards certificate, KAM appointment awards KAM certificate @AC1 @Q2 @S3', async ({
    page,
    api,
    as,
    apiOf,
    profiles,
  }) => {
    const newbieId = await profiles.userId('newbie');
    const botId = await profiles.userId('bot');
    const biebId = await profiles.userId('bieb');

    // newbie passes the hygiene quiz
    const key = await solutionKey(api, 'HYGIENE');
    const newbie = await as('newbie');
    const newbieApi = apiOf(newbie);
    await acceptPendingConsents(newbieApi);
    await playSession(newbieApi, 'HYGIENE', { correct: true, key });

    // orga appoints bot as second KAM of Coop
    const chain = await api.getJson<{ kams: { id: string }[] }>(`/api/store-chains/${COOP}`);
    expect(chain.kams.map((kam) => kam.id)).toEqual([biebId]);
    await api.patchJson(`/api/store-chains/${COOP}`, { kamIds: [biebId, botId] });

    // the global admin page lists both automatic awards
    await page.goto('/admin/achievements');
    await expectDataState(page, 'achievements.board.state', 'success');
    await expect(card(page, ACHIEVEMENTS.hygiene).getByTestId('achievements.card.system')).toBeVisible();
    await expect(card(page, ACHIEVEMENTS.hygiene).getByTestId('achievements.card.delete')).toHaveCount(0);

    await card(page, ACHIEVEMENTS.hygiene).getByTestId('achievements.card.awards').click();
    await expectDataState(page, 'achievements.awards.state', 'success');
    const hygiene = awardItem(page, newbieId);
    await expect(hygiene).toHaveAttribute('data-status', 'active');
    await expect(hygiene.getByTestId('achievements.awards.by')).toHaveAttribute('data-automatic', 'true');
    // app.achievements.hygiene-validity-days = 365 from the seed day
    await expect(hygiene.getByTestId('achievements.awards.validUntil')).toHaveAttribute('data-valid-until', '2027-11-02');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('achievements.awards.list')).toBeHidden();

    await card(page, ACHIEVEMENTS.kam).getByTestId('achievements.card.awards').click();
    await expectDataState(page, 'achievements.awards.state', 'success');
    await expect(awardItem(page, botId)).toHaveAttribute('data-status', 'active');
    await expect(awardItem(page, botId).getByTestId('achievements.awards.by')).toHaveAttribute('data-automatic', 'true');
    await expect(awardItem(page, biebId)).toBeVisible();

    // the certificate shows on newbie's profile
    await newbie.goto(`/profile/${newbieId}`);
    await expect(newbie.getByTestId('profiles.page.achievement')).toHaveCount(1);
    await expect(newbie.getByTestId('profiles.page.achievements')).toContainText('Hygiene-Zertifikat');
  });
});
