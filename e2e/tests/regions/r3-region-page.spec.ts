import { expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';

/**
 * R3/R5/R6/R13 and the region settings/history tabs (M2 T18) on /regions/:id. Seed: Zürich Stadt
 * (`…0040`), bot@local is its ambassador and admin of the PR group (pin), foodsaver@local a plain member.
 */
const ZURICH = '00000010-0001-4000-8000-000000000040';

test.describe('member', () => {
  test.use({ role: 'foodsaver' });

  test('R3 region page shows overview, ambassadors, statistics and later-release tabs @R3 @R13', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}`);
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/overview$`));
    await expect(page.getByTestId('regions.page.name')).toHaveText('Zürich Stadt');
    await expect(page.getByTestId('regions.overview.ambassadors')).toContainText('Reto Baumann');
    // members don't see the ambassador tabs
    await expect(page.getByTestId('regions.page.tab.settings')).toHaveCount(0);
    await expect(page.getByTestId('regions.page.tab.history')).toHaveCount(0);

    await page.getByTestId('regions.page.tab.statistics').click();
    await expect(page.getByTestId('regions.statistics.gender')).toBeVisible();

    await page.getByTestId('regions.page.tab.wall').click();
    await expect(page.getByTestId('regions.later.empty')).toContainText('Pinnwand');
    await expectNoSeriousViolations(page);
  });

  test('R5 R6 options and pin are read-only for members @R5 @R6', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/options`);
    await expectDataState(page, 'regions.options.state', 'success');
    await expect(page.getByTestId('regions.options.pickupRuleLocked')).toBeVisible();
    await expect(page.getByTestId('regions.options.savePickupRule')).toHaveCount(0);

    await page.goto(`/regions/${ZURICH}/pin`);
    await expectDataState(page, 'regions.pin.state', 'success');
    await expect(page.getByTestId('regions.pin.save')).toHaveCount(0);
    await expect(page.getByTestId('regions.pin.description')).toBeDisabled();
  });

  test('R8 members tab searches and filters the member list @R8', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/members`);
    await expectDataState(page, 'regions.members.list', 'success');
    await page.getByTestId('regions.members.search').fill('Baumann');
    await expect(page.getByTestId('regions.members.member')).toHaveCount(1);
    await expect(page.getByTestId('regions.members.member')).toContainText('Reto Baumann');
  });
});

test.describe('store coordination', () => {
  test.use({ role: 'bieb', seedReset: 'test' });

  test('R5 the store coordination group changes the pickup rule @R5', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/options`);
    await expectDataState(page, 'regions.options.state', 'success');
    // type like a user (p-inputnumber parses key input; fill() is not picked up by every engine)
    const timespan = page.locator('#opt-timespan');
    await timespan.click();
    await timespan.press('ControlOrMeta+a');
    await timespan.pressSequentially('14');
    await timespan.blur();
    await page.getByTestId('regions.options.savePickupRule').click();
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
    await page.reload();
    await expect(page.locator('#opt-timespan')).toHaveValue('14');
  });
});

test.describe('ambassador', () => {
  test.use({ role: 'bot', seedReset: 'test' });

  test('R6 the PR group moves the pin and switches it off @R6', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/pin`);
    await expectDataState(page, 'regions.pin.state', 'success');
    const before = await page.getByTestId('regions.pin.coordinates').textContent();
    const map = page.getByTestId('regions.pin.map');
    const box = (await map.boundingBox())!;
    const at = { x: Math.round(box.width * 0.25), y: Math.round(box.height * 0.25) };
    // touch devices tap the map, desktops click it
    if (test.info().project.use.hasTouch) await map.tap({ position: at });
    else await map.click({ position: at });
    await expect(page.getByTestId('regions.pin.coordinates')).not.toHaveText(before ?? '');
    await page.getByTestId('regions.pin.active').click();
    await page.getByTestId('regions.pin.save').click();
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();

    // inactive pins are not on the public page
    await page.goto('/r/zuerich-stadt');
    await expect(page.getByTestId('regions.public.name')).toHaveText('Zürich Stadt');
    await expect(page.getByTestId('regions.public.map')).toHaveCount(0);
  });

  test('region settings: an extra onboarding step is added and recorded in the history @R7', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/settings`);
    await expectDataState(page, 'regions.settings.state', 'success');
    const steps = page.getByTestId('regions.settings.step');
    const count = await steps.count();
    await page.getByTestId('regions.settings.addStep').click();
    await expect(steps).toHaveCount(count + 1);
    await page.getByTestId(`regions.settings.stepKey.${count}`).fill('GARDEN_TOUR');
    await page.getByTestId(`regions.settings.stepTitles.${count}.field.de`).fill('Gartenrundgang');
    await page.getByTestId('regions.settings.save').click();
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();

    await page.getByTestId('regions.page.tab.history').click();
    await expectDataState(page, 'regions.history.list', 'success');
    await expect(page.getByTestId('regions.history.entry').first()).toBeVisible();
  });
});
