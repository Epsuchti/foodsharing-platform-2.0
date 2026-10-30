import {
  expect,
  expectDataState,
  expectErrorState,
  expectNoMissingKeys,
  injectApiError,
  setLanguage,
  test,
} from '../../fixtures';

/**
 * C16 (plan-m7 Q10): `/team` lists the working groups flagged for the team page with their active members; ORGA
 * switches the flag in the region administration (R9: working-group settings).
 */

test.use({ seedReset: 'test' });

const group = (id: string) => `00000011-0001-4000-8000-0000000000${id}`;
/** Seed groups on the team page: begruessungsteam-global, it, oeffentlichkeitsarbeit-global, begruessungsteam-zuerich, betriebskoordination-zuerich. */
const TEAM_GROUPS = ['02', '04', '05', '20', '23'].map(group);
const PR_ZURICH = group('28');

test('C16 team page lists groups flagged for the team page @C16 @R9 @page', async ({ as, page }) => {
  await setLanguage(page, 'de');
  await page.goto('/team');
  await expectDataState(page, 'content.team.state', 'success');
  const groups = page.getByTestId('content.team.group');
  await expect(groups).toHaveCount(TEAM_GROUPS.length);
  for (const id of TEAM_GROUPS) {
    await expect(page.locator(`[data-testid="content.team.group"][data-group="${id}"]`)).toBeVisible();
  }
  await expect(page.locator(`[data-group="${group('04')}"]`).getByTestId('content.team.groupName')).toHaveText('IT');
  await expect(groups.getByTestId('content.team.member').first()).toBeVisible();
  await expectNoMissingKeys(page);
  await page.getByTestId('content.team.about').click();
  await expect(page).toHaveURL(/\/about\/team$/);
  await expect(page.getByTestId('content.page.article')).toHaveAttribute('data-slug', 'team');

  // ORGA puts "Öffentlichkeitsarbeit Zürich" on the team page and takes "IT" off
  const orga = await as('orga');
  await orga.goto('/admin/regions');
  await expectDataState(orga, 'regionAdmin.list', 'success');
  const toggle = async (search: string, slug: string, on: boolean) => {
    await orga.getByTestId('regionAdmin.search').fill(search);
    const row = orga.locator(`[data-region="${slug}"]`);
    await row.getByTestId('regionAdmin.edit').click();
    const teamPage = orga.getByTestId('regionAdmin.form.teamPage').locator('input');
    await expect(teamPage).toBeChecked({ checked: !on });
    await orga.getByTestId('regionAdmin.form.teamPage').click();
    await expect(teamPage).toBeChecked({ checked: on });
    await orga.getByTestId('regionAdmin.form.reason').fill('Teamseite e2e');
    await orga.getByTestId('regionAdmin.form.save').click();
    await expect(orga.getByTestId('regionAdmin.form')).toBeHidden();
  };
  await toggle('Öffentlichkeitsarbeit Zürich', 'oeffentlichkeitsarbeit-zuerich', true);
  await toggle('IT', 'it', false);

  await page.goto('/team');
  await expectDataState(page, 'content.team.state', 'success');
  await expect(groups).toHaveCount(TEAM_GROUPS.length);
  await expect(page.locator(`[data-group="${PR_ZURICH}"]`)).toContainText('Öffentlichkeitsarbeit Zürich');
  await expect(page.locator(`[data-group="${group('04')}"]`)).toHaveCount(0);
});

test('C16 team page: error with retry @C16', async ({ page }) => {
  const failure = await injectApiError(page, { url: '/api/public/team', status: 500 });
  await page.goto('/team');
  const { retry } = await expectErrorState(page, 'content.team.state');
  await failure.remove();
  await retry.click();
  await expectDataState(page, 'content.team.state', 'success');
});
