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
    });
  });
});
