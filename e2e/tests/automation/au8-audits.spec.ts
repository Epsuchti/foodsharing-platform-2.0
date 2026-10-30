import { expect, expectDataState, seedRegion, seedStore, test } from '../../fixtures';

/** AU8: automation decisions — region coordinators see all stores, a manager only the stores they may automate. */
const ZH = seedRegion('zuerich-stadt').id;

test('AU8 region coordinator sees all decisions, member only own decisions @AU8 @D10.3 @page', async ({ as }) => {
  // bot: ambassador of Stadt Zürich with CAN_SEE_ALL_AUTOMATION_DECISIONS → decisions of several stores
  const bot = await as('bot');
  await bot.goto(`/regions/${ZH}`);
  await bot.getByTestId('regions.page.tab.automation').click();
  await expect(bot).toHaveURL(new RegExp(`/regions/${ZH}/automation/audits$`));
  await expectDataState(bot, 'automation.region.audits.state', 'success');
  const rows = bot.getByTestId('automation.region.audits.row');
  await expect(rows.first()).toBeVisible();
  const stores = new Set(await rows.locator('.audit__title strong').allTextContents());
  expect(stores.size).toBeGreaterThan(1);

  // filters: dry-run decisions only of kind PICKUP, expandable reasons
  await bot.getByTestId('automation.region.audits.kind.PICKUP').click();
  await expect(rows.first()).toHaveAttribute('data-kind', 'PICKUP');
  await rows.first().getByTestId('automation.region.audits.toggle').click();
  await expect(rows.first().getByTestId('automation.region.audits.details')).toBeVisible();

  // the seeded team-request decisions belong to S-07 (managed by welcome): the coordinator sees them …
  await bot.getByTestId('automation.region.audits.kind.REQUEST').click();
  await expect(rows.first()).toHaveAttribute('data-kind', 'REQUEST');
  await expect(rows.first().locator('.audit__title strong')).toHaveText(seedStore('S-07').name);

  // … a plain region member (jumper) only sees the decisions about themselves
  const jumper = await as('jumper');
  await jumper.goto(`/regions/${ZH}/automation/audits`);
  await expectDataState(jumper, 'automation.region.audits.state', 'success');
  const own = jumper.getByTestId('automation.region.audits.row');
  await expect(own.first()).toBeVisible();
  for (const title of await own.locator('.audit__title').allTextContents()) {
    expect(title).toContain('Jonas Weber');
  }
  await jumper.getByTestId('automation.region.audits.kind.REQUEST').click();
  await expectDataState(jumper, 'automation.region.audits.state', 'empty');
});
