import type { Page } from '@playwright/test';
import { acceptPendingConsents, expectDataState, pickOption, regionId } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';
import { fillDate } from '../../support/date-inputs';

/**
 * Ambassador Daniel (bot@local, administers Zürich Stadt) looks after the members of his region. He defines a
 * regional achievement with translations, icon, visibility and validity, awards it to Julien (guide@local), who sees
 * it on his profile until Daniel makes it HIDDEN; he edits the award's end date and notice, revokes it and deletes
 * the definition (AC1). Then he keeps an internal note about the sleeping member Lena (sleepy@local) on her admin
 * page and deletes it again (A7). Seed (`168-achievement-seed.yaml`): Putzheld, Sommerfest-Helfer and the HIDDEN
 * Vertrauensperson awarded to guide; bot's note "Seit Monaten inaktiv" about sleepy. Visibility, validity and
 * automatic awards (hygiene quiz Q2, KAM appointment S3) are unit tests: AchievementVisibilityTest,
 * AchievementServiceTest, AutoAwardListenerTest; who reads notes: ProfilePermissionMatrixTest, AccountAdminRulesTest.
 */
test.use({ role: 'bot' });

const ZURICH = regionId('zuerich-stadt');
const TRUST = '00000051-0001-4000-8000-000000000005';
const SLEEPY_ID = '00000001-0001-4000-8000-000000000008';
const NOTE = 'Hat sich telefonisch gemeldet, kommt im Dezember zurück.';

/** Accepts the shared confirm dialog and waits until it is gone (an Escape right after would hit it instead). */
async function confirm(page: Page): Promise<void> {
  await page.locator('.p-confirmdialog-accept-button').click();
  await expect(page.locator('.p-confirmdialog')).toHaveCount(0);
}

const card = (page: Page, id: string) => page.locator(`[data-testid="achievements.card"][data-achievement-id="${id}"]`);

test('ambassador defines, awards and revokes an achievement and keeps a note about a member @AC1 @A7', async ({
  page,
  as,
  apiOf,
  profiles,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'ambassador');
  await acceptPendingConsents(apiOf(page));
  const guideId = await profiles.userId('guide');
  let achievementId = '';

  await walk.step('opens the achievements of his region', async () => {
    await page.goto(`/regions/${ZURICH}`);
    await walk.click(page.getByTestId('regions.page.tab.achievements'));
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/achievements$`));
    await expectDataState(page, 'achievements.board.state', 'success');
    // as administrator he sees the HIDDEN one too
    await expect(card(page, TRUST)).toHaveAttribute('data-visibility', 'HIDDEN');
    await checkpoint(page, 'achievements-board');
  });

  await walk.step('defines "Velokurier-Held" in three languages', async () => {
    await walk.click(page.getByTestId('achievements.board.define'));
    await walk.fill(page.getByTestId('achievements.form.names.field.de'), 'Velokurier-Held');
    await walk.click(page.getByTestId('achievements.form.names.tab.fr'));
    await walk.fill(page.getByTestId('achievements.form.names.field.fr'), 'Héros coursier à vélo');
    await walk.click(page.getByTestId('achievements.form.names.tab.en'));
    await walk.fill(page.getByTestId('achievements.form.names.field.en'), 'Bike courier hero');
    await walk.fill(page.getByTestId('achievements.form.descriptions.field.de'), 'Liefert gerettete Lebensmittel mit dem Velo aus.');
    await walk.click(page.locator('[data-testid="achievements.form.icon"][data-icon="truck"]'));
    walk.count(2);
    await pickOption(page, 'achievements.form.visibility', 'SCOPE');
    await walk.fill(page.getByTestId('achievements.form.validity'), '30');
    await checkpoint(page, 'define-dialog');
    await walk.click(page.getByTestId('achievements.form.submit'));
    await expect(page.getByTestId('achievements.form.names.field.de')).toBeHidden();
    const created = page.getByTestId('achievements.card').filter({ hasText: 'Velokurier-Held' });
    await expect(created).toHaveCount(1);
    await expect(created.locator('i.pi-truck')).toBeVisible();
    achievementId = (await created.getAttribute('data-achievement-id'))!;
  });

  await walk.step('awards it to Julien, who sees it on his profile', async () => {
    await walk.click(card(page, achievementId).getByTestId('achievements.card.award'));
    await walk.fill(page.getByTestId('achievements.award.picker.search'), 'Julien');
    await walk.click(page.locator(`[data-testid="achievements.award.picker.hit"][data-user-id="${guideId}"]`).getByTestId('achievements.award.picker.add'));
    await expect(page.getByTestId('achievements.award.recipient')).toHaveAttribute('data-user-id', guideId);
    await walk.fill(page.getByTestId('achievements.award.notice'), 'Danke für die Velotouren!');
    await checkpoint(page, 'award-dialog');
    await walk.click(page.getByTestId('achievements.award.submit'));
    await expect(page.getByTestId('achievements.award.submit')).toBeHidden();

    const guide = await as('guide');
    await acceptPendingConsents(apiOf(guide));
    await guide.goto(`/profile/${guideId}`);
    const grid = guide.getByTestId('profiles.page.achievements');
    await expect(grid).toContainText('Velokurier-Held');
    // never the HIDDEN "Vertrauensperson" award
    await expect(grid).not.toContainText('Vertrauensperson');
    await checkpoint(guide, 'profile-with-award');

    // made HIDDEN, the award disappears for the recipient
    await walk.click(card(page, achievementId).getByTestId('achievements.card.edit'));
    await expect(page.getByTestId('achievements.form.names.field.de')).toHaveValue('Velokurier-Held');
    walk.count(2);
    await pickOption(page, 'achievements.form.visibility', 'HIDDEN');
    await walk.click(page.getByTestId('achievements.form.submit'));
    await expect(page.getByTestId('achievements.form.names.field.de')).toBeHidden();
    await expect(card(page, achievementId)).toHaveAttribute('data-visibility', 'HIDDEN');
    await guide.reload();
    await expect(guide.getByTestId('profiles.page.achievements')).toBeVisible();
    await expect(guide.getByTestId('profiles.page.achievements')).not.toContainText('Velokurier-Held');
  });

  await walk.step('changes the award, revokes it and deletes the achievement', async () => {
    await walk.click(card(page, achievementId).getByTestId('achievements.card.awards'));
    await expectDataState(page, 'achievements.awards.state', 'success');
    const item = page.locator(`[data-testid="achievements.awards.item"][data-user-id="${guideId}"]`);
    await expect(item).toHaveAttribute('data-status', 'active');
    await expect(item.getByTestId('achievements.awards.notice')).toHaveText('Danke für die Velotouren!');
    // 30 validity days from the seed day (2026-11-02)
    await expect(item.getByTestId('achievements.awards.validUntil')).toHaveAttribute('data-valid-until', '2026-12-02');
    await walk.click(item.getByTestId('achievements.awards.edit'));
    walk.count(1);
    await fillDate(item.getByTestId('achievements.awards.editValidUntil'), '2027-03-31');
    await walk.fill(item.getByTestId('achievements.awards.editNotice'), 'Velotouren 2026');
    await walk.click(item.getByTestId('achievements.awards.editSave'));
    await expect(item.getByTestId('achievements.awards.validUntil')).toHaveAttribute('data-valid-until', '2027-03-31');
    await expect(item.getByTestId('achievements.awards.notice')).toHaveText('Velotouren 2026');
    await checkpoint(page, 'awards-list');
    await walk.click(item.getByTestId('achievements.awards.revoke'));
    walk.count(1);
    await confirm(page);
    await expect(item).toHaveAttribute('data-status', 'revoked');
    await expect(item.getByTestId('achievements.awards.revoke')).toHaveCount(0);
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('achievements.awards.list')).toBeHidden();

    await walk.click(card(page, achievementId).getByTestId('achievements.card.delete'));
    walk.count(1);
    await confirm(page);
    await expect(card(page, achievementId)).toHaveCount(0);
  });

  await walk.step('keeps a note about Lena and deletes it again', async () => {
    await page.goto(`/users/${SLEEPY_ID}`);
    const posts = page.getByTestId('usersAdmin.user.notes.post');
    await expect(posts).toHaveCount(1);
    await expect(posts.first()).toContainText('Seit Monaten inaktiv');
    await walk.fill(page.getByTestId('usersAdmin.user.notes.text'), NOTE);
    await walk.click(page.getByTestId('usersAdmin.user.notes.submit'));
    await expect(posts).toHaveCount(2);
    const added = posts.filter({ hasText: NOTE });
    await expect(added).toBeVisible();
    await checkpoint(page, 'member-notes');
    await walk.click(added.getByTestId('usersAdmin.user.notes.delete'));
    walk.count(1);
    await confirm(page);
    await expect(posts).toHaveCount(1);
    await expect(page.getByText(NOTE)).toHaveCount(0);
  });

  // measured 2026-11-15 (rework step 3): 26 clicks on desktop and phone
  walk.finish({ steps: 5, clicks: 26, fields: 9 });
});
