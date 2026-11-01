import { seedRegion, seedStore } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Automation screens (M4 F3/F4, AU1–AU10): store policy form, linked-store editor, preview dialog, decisions,
 * adverts, sign-out alerts, region overview, grouping, metrics — × light/dark × de/fr. Baselines are generated in
 * Docker at the end of the batch (decision 69).
 */
screenshotTest.use({ seedReset: 'test' });

const S01 = seedStore('S-01').id;
const S06 = seedStore('S-06').id;
const ZH = seedRegion('zuerich-stadt').id;

forEachVariant((variant) => {
  screenshotTest.describe(`automation ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'bieb' });

    screenshotTest('store policy, linked-store editor and preview @AU1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/stores/${S01}/automation/policy`);
      await expect(page.getByTestId('automation.store.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `automation-policy-${variant.name}`, { fullPage: true });
      await page.getByTestId('automation.policy.addLink').click();
      await expect(page.getByTestId('automation.policy.link.0')).toBeVisible();
      await visual.expectElement(page.getByTestId('automation.policy.links'), `automation-links-${variant.name}`);
      await page.getByTestId('automation.policy.preview').click();
      await visual.expectScreen(page, `automation-preview-${variant.name}`);
    });

    screenshotTest('gap rule with a shared store list: list, warnings, preview, phone @AU1 @screenshot', async ({ page, visual }) => {
      // seed (decision 151): S-01 + S-03, S-03 locked for bieb and without automation
      await page.goto(`/stores/${S01}/automation/policy`);
      await expect(page.getByTestId('automation.store.state')).toHaveAttribute('data-state', 'success');
      const card = page.getByTestId('automation.policy.gapCard');
      await expect(card.getByTestId('automation.policy.gapMember')).toHaveCount(1);
      await expect(card.getByTestId('automation.policy.gapPreview.day')).toHaveCount(11);
      await visual.expectElement(card, `automation-gap-list-${variant.name}`);

      // conflict: S-06 links S-05 on the same day and puts S-05 on its list (not saved)
      await page.goto(`/stores/${S06}/automation/policy`);
      await expect(page.getByTestId('automation.store.state')).toHaveAttribute('data-state', 'success');
      await page.getByTestId('automation.policy.addLink').click();
      const stores = page.getByTestId('automation.policy.link.0.stores');
      await stores.click();
      await page.locator('.p-multiselect-overlay').getByText(seedStore('S-05').name, { exact: true }).click();
      await stores.locator('.p-multiselect-dropdown').click();
      await expect(page.locator('.p-multiselect-overlay')).toHaveCount(0);
      const picker = page.getByTestId('automation.policy.gapStores');
      await picker.click();
      await page.locator('.p-multiselect-overlay').getByText(seedStore('S-05').name, { exact: true }).click();
      // the gap picker's list overlays the field (appendTo body): Escape closes it
      await expect(async () => {
        await page.keyboard.press('Escape');
        await expect(page.locator('.p-multiselect-overlay')).toHaveCount(0, { timeout: 1_000 });
      }).toPass();
      await expect(page.locator('[data-testid="automation.policy.gapWarning"][data-kind="CONFLICT"]')).toBeVisible();
      await visual.expectElement(page.getByTestId('automation.policy.gapCard'), `automation-gap-conflict-${variant.name}`);

      // phone width: the 11 cells stay in one row
      await page.setViewportSize({ width: 412, height: 915 });
      await page.goto(`/stores/${S01}/automation/policy`);
      await expect(page.getByTestId('automation.store.state')).toHaveAttribute('data-state', 'success');
      await visual.expectElement(page.getByTestId('automation.policy.gapCard'), `automation-gap-list-phone-${variant.name}`);
    });

    screenshotTest('store decisions, adverts and sign-out alerts @AU4 @AU5 @AU8 @screenshot', async ({ page, visual }) => {
      await page.goto(`/stores/${S01}/automation/audits`);
      await expect(page.getByTestId('automation.audits.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `automation-audits-${variant.name}`);
      await page.goto(`/stores/${S01}/automation/adverts`);
      await expect(page.getByTestId('automation.adverts.card').first()).toBeVisible();
      await visual.expectScreen(page, `automation-adverts-${variant.name}`, { fullPage: true });
      await page.goto(`/stores/${S06}/automation/signout-alerts`);
      await expect(page.getByTestId('automation.signout.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `automation-signout-${variant.name}`, { fullPage: true });
    });
  });

  screenshotTest.describe(`region automation ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'bot' });

    screenshotTest('region overview, grouping and metrics @AU7 @AU9 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZH}/automation/overview`);
      await expect(page.getByTestId('automation.region.overview')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `automation-region-overview-${variant.name}`);
      await page.goto(`/regions/${ZH}/automation/grouping`);
      await expect(page.getByTestId('automation.grouping.state')).toHaveAttribute('data-state', /success|empty/);
      await visual.expectScreen(page, `automation-grouping-${variant.name}`);
      await page.goto(`/regions/${ZH}/automation/metrics`);
      await expect(page.getByTestId('automation.metrics.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `automation-metrics-${variant.name}`);
      await page.getByTestId('automation.metrics.group.DECLINE_REASONS').click();
      await expect(page.locator('[data-testid="automation.metrics.totals.row"][data-key^="SLOT_DECLINE_RULE."]').first()).toBeVisible();
      await visual.expectScreen(page, `automation-metrics-decline-reasons-${variant.name}`);
    });
  });
});
