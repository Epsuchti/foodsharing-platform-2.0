import { expectDataState, seedStore } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { NAMES, memberRow } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';

/**
 * Store manager Nina (bieb@local, manager of S-01 Coop Bahnhofbrücke) keeps up with her store: she opens the route
 * menu and the store's contact on the overview, posts on the store wall and deletes Sarah's 40-day-old note (managers
 * delete any post, decision 54 as amended), takes Jonas from the standby list into the team, finds that change in the
 * store log by its action group and with the details in words, reads the statistics (pickups and kg per month,
 * pickups per member) and the store's pickup history (PU6). Who may see or delete what, the log filters and the kg
 * arithmetic are unit tests (StoreWallServiceTest, StoreLogServiceTest, StoreStatisticsServiceTest).
 */
test.use({ role: 'bieb' });

const S01 = seedStore('S-01');
/** Seed store wall of S-01 (docs/seed-ids.md): a post by foodsaver, 40 days old. */
const OLD_FOODSAVER_POST = '00000020-0008-4000-8000-000000000083';

test('store manager posts on the wall, reads the store log, the statistics and the pickup history @S11 @S12 @S14 @S15 @PU6 @D10.3', async ({ page }, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'store manager');

  await walk.step('finds the way and the contact on the overview', async () => {
    await page.goto(`/stores/${S01.id}/overview`);
    await expect(page.getByTestId('stores.page.name')).toHaveText(S01.name);
    await expect(page.getByTestId('stores.overview.mapView').locator('.leaflet-marker-icon')).toHaveCount(1);
    await expect(page.getByTestId('stores.page.call')).toHaveAttribute('href', 'tel:+41440000001');
    await expect(page.getByTestId('stores.overview.contact')).toContainText('Filialleitung');
    const trigger = page.getByTestId('stores.page.navigate');
    await expect(trigger).toHaveAttribute('aria-expanded', 'false');
    await walk.click(trigger);
    await expect(trigger).toHaveAttribute('aria-expanded', 'true');
    const google = page.getByTestId('stores.page.navigate.google');
    await expect(google).toHaveAttribute('href', /google\.com\/maps\/dir\/\?api=1&destination=47\.3779,8\.5427/);
    await expect(google).toHaveAttribute('target', '_blank');
    await expect(page.getByTestId('stores.page.navigate.apple')).toHaveAttribute('href', /maps\.apple\.com\/\?daddr=47\.3779,8\.5427/);
    // the open menu overlays the tab row, whose covered tabs axe would count as obscured targets
    await checkpoint(page, 'navigate-menu', { fullPage: false, a11y: { include: ['.p-popover'] } });
    await page.keyboard.press('Escape');
    await expect(google).toBeHidden();
    await expect(trigger).toBeFocused();
  });

  await walk.step('posts on the wall and deletes an old note', async () => {
    await walk.click(page.getByTestId('stores.page.tab.wall'));
    await expectDataState(page, 'stores.wall.state', 'success');
    // milestones in words (the seed stores COOPERATION_ESTABLISHED as text)
    await expect(page.locator('[data-testid="stores.wall.post"][data-milestone="COOPERATION_STARTED"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="stores.wall.post"][data-milestone="STORE_CREATED"]')).toHaveCount(1);
    await expect(page.getByTestId('stores.wall.list')).not.toContainText('COOPERATION_ESTABLISHED');
    const text = 'Schlüssel liegt ab Montag beim Hintereingang.';
    await walk.fill(page.getByTestId('stores.wall.text'), text);
    await walk.click(page.getByTestId('stores.wall.submit'));
    await expect(page.getByTestId('stores.wall.text')).toHaveValue('');
    await expect(page.getByTestId('stores.wall.post').first().getByTestId('stores.wall.postText')).toHaveText(text);
    await checkpoint(page, 'store-wall');
    const old = page.locator(`[data-testid="stores.wall.post"][data-post-id="${OLD_FOODSAVER_POST}"]`);
    await walk.click(old.getByTestId('stores.wall.delete'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(old).toHaveCount(0);
  });

  await walk.step('takes Jonas from the standby list into the team', async () => {
    await walk.click(page.getByTestId('stores.page.tab.team'));
    await expectDataState(page, 'stores.team.list', 'success');
    const jonas = memberRow(page, NAMES.jumper);
    await jonas.scrollIntoViewIfNeeded();
    await walk.click(jonas.getByTestId('stores.team.menu'));
    await walk.click(page.getByTestId('stores.team.toTeam'));
    await expect(jonas).toHaveAttribute('data-status', 'MEMBER');
  });

  await walk.step('finds the change in the store log', async () => {
    await walk.click(page.getByTestId('stores.page.tab.log'));
    await expectDataState(page, 'stores.log.list', 'success');
    const moved = page.locator('[data-testid="stores.log.entry"][data-action="STORE.MOVED_TO_TEAM"]').first();
    await expect(moved).toBeVisible();
    await walk.click(page.getByTestId('stores.log.preset.team'));
    await expectDataState(page, 'stores.log.list', 'success');
    await expect(moved).toBeVisible();
    // the details name the action in words: no SUBJECT.ACTION codes, no raw ids or ISO timestamps
    await walk.click(moved.getByTestId('stores.log.entry.details'));
    const details = moved.getByTestId('stores.log.entry.detailsPanel');
    await expect(details.getByTestId('stores.log.entry.action')).toHaveText(/^[^_.]+$/);
    await expect(details).not.toContainText('STORE.');
    await expect(details).not.toContainText('MOVED_TO_TEAM');
    await expect(details).not.toContainText(/\d{4}-\d{2}-\d{2}T/);
    await checkpoint(page, 'store-log-details');
    await walk.click(page.getByTestId('stores.log.preset.pickups'));
    await expect(page.getByTestId('stores.log.list')).toHaveAttribute('data-state', /success|empty/);
    await expect(page.locator('[data-testid="stores.log.entry"][data-action="STORE.MOVED_TO_TEAM"]')).toHaveCount(0);
  });

  await walk.step('reads the statistics', async () => {
    await walk.click(page.getByTestId('stores.page.tab.statistics'));
    await expectDataState(page, 'stores.statistics.state', 'success');
    await expect(page.getByTestId('stores.statistics.totalPickups')).toContainText(/\d+/);
    const bars = page.getByTestId('stores.statistics.chart.bar');
    await expect(bars).toHaveCount(12);
    await expectDataState(page, 'stores.statistics.members.state', 'success');
    await expect(page.getByTestId('stores.statistics.member').first()).toBeVisible();
    await checkpoint(page, 'statistics');
    await walk.click(page.getByTestId('stores.statistics.metric.kg'));
    await expect(page.getByTestId('stores.statistics.weightHint')).toContainText('10');
    // the per-member list: search by name, accent- and case-insensitive
    await walk.fill(page.getByTestId('stores.statistics.members.search'), 'MULLER');
    await expect(page.getByTestId('stores.statistics.member')).toHaveCount(1);
    await expect(page.getByTestId('stores.statistics.member')).toHaveAttribute('data-user', NAMES.foodsaver);
    await checkpoint(page, 'statistics-kg-member-search');
  });

  await walk.step('looks up who picked up lately', async () => {
    await walk.click(page.getByTestId('stores.page.tab.pickups'));
    await expectDataState(page, 'stores.pickups.state', 'success');
    await walk.click(page.getByTestId('stores.pickups.history'));
    await expect(page).toHaveURL(new RegExp(`/stores/${S01.id}/pickups/history`));
    await expectDataState(page, 'stores.pickupHistory.state', 'success');
    await expect(page.getByTestId('stores.pickupHistory.row').first()).toBeVisible();
    await expect(page.getByTestId('stores.pickupHistory.person').first()).toBeVisible();
    await checkpoint(page, 'pickup-history');
  });

  walk.finish({ steps: 6, clicks: 16, fields: 2 });
});
