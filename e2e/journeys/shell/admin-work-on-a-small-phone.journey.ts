import { expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { expectFitsScreen } from '../../fixtures/flows/shell';
import { ZURICH, openStore } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';

/**
 * Pages that were once wider than a phone, on a 360 px screen in every browser: nothing may push the layout viewport
 * past the screen (it moves the fixed tab bar). Mila (bot@local, ambassador of Zürich Stadt) drafts a poll and looks
 * at the intro pickups admin, the orga reviews the store categories, and Nina (bieb@local, manager of S-01) reads her
 * store's statistics, whose month labels must not overlap.
 */
const SMALL = { width: 360, height: 740 };

test('ambassador, orga and store manager do their admin work on a 360 px phone @V1 @EA18 @S4 @S14', async ({ as }, testInfo) => {
  test.setTimeout(120_000);

  const ambassador = await as('bot');
  await ambassador.setViewportSize(SMALL);
  const mila = new Walk(ambassador, testInfo, 'ambassador');
  await mila.step('drafts a poll', async () => {
    await ambassador.goto(`/polls/new?regionId=${ZURICH}`);
    await expect(ambassador.getByTestId('polls.form.option').first()).toBeVisible();
    await mila.fill(ambassador.getByTestId('polls.form.name'), 'Neuer Treffpunkt für die Abholungen');
    await mila.click(ambassador.getByTestId('polls.form.addOption'));
    await expectFitsScreen(ambassador, 'poll form');
    await checkpoint(ambassador, 'poll-form');
  });
  await mila.step('looks at the intro pickups admin', async () => {
    await ambassador.goto(`/regions/${ZURICH}/intro-pickups/admin`);
    await expect(ambassador.getByTestId('introPickups.admin.einab').first()).toBeVisible();
    await expectFitsScreen(ambassador, 'intro pickups admin');
    await checkpoint(ambassador, 'intro-pickups-admin');
  });
  mila.finish({ steps: 2, clicks: 1, fields: 1 });

  const orga = await as('orga');
  await orga.setViewportSize(SMALL);
  await test.step('orga reviews the store categories', async () => {
    await orga.goto('/admin/store-categories');
    await expect(orga.locator('table').first()).toBeVisible();
    await expectFitsScreen(orga, 'store categories');
    await checkpoint(orga, 'store-categories');
  });

  const manager = await as('bieb');
  await manager.setViewportSize(SMALL);
  await test.step('store manager reads the statistics of her store', async () => {
    await openStore(manager, 'S-01', 'statistics');
    await expectDataState(manager, 'stores.statistics.state', 'success');
    await expect(manager.getByTestId('stores.statistics.member').first()).toBeVisible();
    await expectFitsScreen(manager, 'store statistics');
    // the month labels of the chart do not overlap
    const labels = manager.locator('[data-testid="stores.statistics.chart.bar"] .chart__month');
    await expect(labels.first()).toBeVisible();
    const boxes = await labels.evaluateAll((items) =>
      items.map((item) => {
        const range = document.createRange();
        range.selectNodeContents(item);
        const { left, right } = range.getBoundingClientRect();
        return { left, right };
      }),
    );
    expect(boxes.length).toBeGreaterThan(1);
    for (let i = 1; i < boxes.length; i++) expect(boxes[i].left).toBeGreaterThanOrEqual(boxes[i - 1].right);
    await checkpoint(manager, 'store-statistics');
  });
});
