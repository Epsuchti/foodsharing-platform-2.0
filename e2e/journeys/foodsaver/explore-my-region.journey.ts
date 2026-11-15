import { acceptPendingConsents, expectDataState, regionId } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Foodsaver Julien (guide@local, Zürich Stadt) looks around his region: "my regions" with the home region first and
 * his working groups, the overview, the statistics (counters, monthly chart in pickups and kg, gender list of all and
 * of home-region members), the options and pin he may only read, then the working groups: he joins the open
 * Kochgruppe (members get its video room), applies to the PR group, withdraws and applies again, and checks that he meets the requirements of the
 * store coordination (10 pickups, 4 weeks). Rules behind it: GroupServiceTest (apply types, requirements), RegionOptionsServiceTest (who edits
 * which option), regions-pages.spec (requirements hide apply, no member list for non-members), region-statistics.spec.
 */
test.use({ role: 'guide' });

const ZURICH = regionId('zuerich-stadt');
const PR_GROUP = '00000011-0001-4000-8000-000000000028';
const KOCHGRUPPE = '00000011-0001-4000-8000-000000000033';
const STORES_COORDINATION = '00000011-0001-4000-8000-000000000023';

interface Counters {
  activeFoodSharePoints: number;
  foodBasketsLastMonth: number;
}
interface GenderCount {
  gender: string;
  count: number;
}

test('foodsaver explores his region, its statistics and working groups and applies to one @R2 @R3 @R5 @R6 @R9 @R12 @R13', async ({
  page,
  apiOf,
}, testInfo) => {
  test.setTimeout(150_000);
  const api = apiOf(page);
  await acceptPendingConsents(api);
  const walk = new Walk(page, testInfo, 'foodsaver');

  await walk.step('opens his regions', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('regions');
    await expectDataState(page, 'regions.mine.state', 'success');
    const first = page.getByTestId('regions.mine.region').first();
    await expect(first).toContainText('Zürich Stadt');
    await expect(first).toContainText(/Stammbezirk/);
    await checkpoint(page, 'my-regions');
    await walk.click(first);
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/overview$`));
    await expect(page.getByTestId('regions.overview.ambassadors')).toContainText('Reto Baumann');
    // members don't get the ambassador tabs
    await expect(page.getByTestId('regions.page.tab.settings')).toHaveCount(0);
    await expect(page.getByTestId('regions.page.tab.history')).toHaveCount(0);
  });

  await walk.step('reads the statistics', async () => {
    const stats = await api.getJson<Counters>(`/api/regions/${ZURICH}/statistics`);
    const genders = await api.getJson<GenderCount[]>(`/api/regions/${ZURICH}/statistics/gender`);
    const homeGenders = await api.getJson<GenderCount[]>(`/api/regions/${ZURICH}/statistics/gender?onlyHomeRegion=true`);
    const months = await api.getJson<{ pickups: number; kg: number }[]>(`/api/regions/${ZURICH}/statistics/pickups?months=12`);
    expect(genders.length).toBeGreaterThan(0);
    expect(months.length).toBeGreaterThan(0);

    await walk.click(page.getByTestId('regions.page.tab.statistics'));
    await expectDataState(page, 'regions.statistics.state', 'success');
    await expect(page.getByTestId('regions.statistics.fsps')).toContainText(String(stats.activeFoodSharePoints));
    await expect(page.getByTestId('regions.statistics.baskets')).toContainText(String(stats.foodBasketsLastMonth));
    await expectDataState(page, 'regions.statistics.pickupState', 'success');
    const bars = page.getByTestId('regions.statistics.chart.bar');
    await expect(bars).toHaveCount(months.length);
    await expect(bars.first()).toHaveAttribute('data-value', String(months[0].pickups));
    await expectDataState(page, 'regions.statistics.genderState', 'success');
    await expectDataState(page, 'regions.statistics.ageState', 'success');
    const row = (gender: string) => page.locator(`[data-testid="regions.statistics.genderRow"][data-gender="${gender}"]`);
    await expect(page.getByTestId('regions.statistics.genderRow')).toHaveCount(genders.length);
    for (const entry of genders) await expect(row(entry.gender)).toContainText(String(entry.count));
    await checkpoint(page, 'region-statistics');

    await walk.click(page.getByTestId('regions.statistics.metric.kg'));
    await expect(bars.first()).toHaveAttribute('data-value', String(months[0].kg));
    await walk.click(page.getByTestId('regions.statistics.onlyHomeRegion'));
    await expect(page.getByTestId('regions.statistics.genderRow')).toHaveCount(homeGenders.length);
    for (const entry of homeGenders) await expect(row(entry.gender)).toContainText(String(entry.count));
  });

  await walk.step('looks at the options and the pin, which he may only read', async () => {
    await walk.click(page.getByTestId('regions.page.tab.options'));
    await expectDataState(page, 'regions.options.state', 'success');
    await expect(page.getByTestId('regions.options.pickupRuleLocked')).toBeVisible();
    await expect(page.getByTestId('regions.options.savePickupRule')).toHaveCount(0);
    await expect(page.getByTestId('regions.options.saveReport')).toHaveCount(0);
    await checkpoint(page, 'options-read-only');
    await walk.click(page.getByTestId('regions.page.tab.pin'));
    await expectDataState(page, 'regions.pin.state', 'success');
    await expect(page.getByTestId('regions.pin.save')).toHaveCount(0);
    await expect(page.getByTestId('regions.pin.description')).toBeDisabled();
  });

  await walk.step('joins the open Kochgruppe and finds its video room', async () => {
    await walk.click(page.getByTestId('regions.page.tab.groups'));
    await expectDataState(page, 'regions.groups.list', 'success');
    await checkpoint(page, 'groups');
    await walk.click(page.getByTestId('regions.groups.link.kochgruppe-zuerich'));
    await expect(page).toHaveURL(new RegExp(`/groups/${KOCHGRUPPE}$`));
    await expectDataState(page, 'regions.group.state', 'success');
    // the member list and the conference room are for members only
    await expect(page.getByTestId('regions.group.members')).toHaveCount(0);
    await expect(page.getByTestId('regions.group.conference')).toHaveCount(0);
    await walk.click(page.getByTestId('regions.group.join'));
    await expect(page.getByTestId('regions.group.memberTag')).toBeVisible();
    await expect(page.getByTestId('regions.group.members')).toBeVisible();
    const room = page.getByTestId('regions.group.conference');
    await expect(room).toHaveAttribute('href', 'https://meet.jit.si/foodsharing-kochgruppe-zuerich');
    await expect(room).toHaveAttribute('rel', /noopener/);
    await page.goBack();
    await expectDataState(page, 'regions.groups.list', 'success');
    await expect(page.locator('[data-group="kochgruppe-zuerich"]')).toContainText('Mitglied');
  });

  await walk.step('applies to the PR group, withdraws and applies again', async () => {
    await walk.click(page.getByTestId('regions.groups.link.oeffentlichkeitsarbeit-zuerich'));
    await expect(page).toHaveURL(new RegExp(`/groups/${PR_GROUP}$`));
    await expectDataState(page, 'regions.group.state', 'success');
    const apply = page.getByTestId('regions.group.apply');
    const appliedTag = page.getByTestId('regions.group.appliedTag');
    await walk.click(apply);
    await walk.fill(page.getByTestId('regions.apply.message'), 'Ich helfe gern an Ständen mit.');
    await checkpoint(page, 'apply-dialog');
    await walk.click(page.getByTestId('regions.apply.confirm'));
    await expect(appliedTag).toBeVisible();
    await walk.click(page.getByTestId('regions.group.cancelApplication'));
    await expect(appliedTag).toHaveCount(0);
    await walk.click(apply);
    await walk.fill(page.getByTestId('regions.apply.message'), 'Zweiter Versuch, ich habe jetzt mehr Zeit.');
    await walk.click(page.getByTestId('regions.apply.confirm'));
    await expect(appliedTag).toBeVisible();
    await checkpointElement(page.getByTestId('regions.group.head'), 'applied');
  });

  await walk.step('checks the requirements of the store coordination', async () => {
    await page.goto(`/groups/${STORES_COORDINATION}`);
    await expectDataState(page, 'regions.group.state', 'success');
    // a missing requirement would hide "apply" (regions-pages.spec, GroupServiceTest)
    await expect(page.locator('[data-requirement="minPickups"]')).toHaveClass(/ok/);
    await expect(page.locator('[data-requirement="minWeeks"]')).toHaveClass(/ok/);
    await expect(page.getByTestId('regions.group.apply')).toBeVisible();
    // non-members don't see the member list
    await expect(page.getByTestId('regions.group.members')).toHaveCount(0);
    await checkpoint(page, 'group-requirements');
  });

  walk.finish({ steps: 6, clicks: 17, fields: 2 });
});
