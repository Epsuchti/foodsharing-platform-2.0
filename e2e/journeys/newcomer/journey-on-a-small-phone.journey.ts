import { LANGUAGES, acceptPendingConsents, expectNoMissingKeys, setLanguage } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';
import type { Page } from '@playwright/test';

/**
 * D11.5.4: every step works on a 360 px wide screen and in all five languages. Noah (einab1@local, quiz passed, at
 * his intro pickups) follows his journey on a small phone in each language: journey page, profile step and dashboard
 * load without missing texts or sideways scrolling. The journey page carries the one call to action itself (the bar
 * only shows the step, design review M7 R2); everywhere else the bar carries it. German is captured, the other
 * languages are checked only (checkpoints are light-de, decision 180); text completeness is `npm run i18n:check`.
 */
test.use({ role: 'einab1' });

const PAGES = ['/onboarding', '/onboarding/profile', '/dashboard'] as const;
const NAMES: Record<(typeof PAGES)[number], string> = {
  '/onboarding': 'journey-360',
  '/onboarding/profile': 'profile-360',
  '/dashboard': 'dashboard-360',
};

async function noSidewaysScroll(page: Page, what: string): Promise<void> {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, what).toBeLessThanOrEqual(0);
}

test('newcomer follows his journey on a 360 px phone in all five languages @D11.5.4', async ({ page, apiOf }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'newcomer');
  const me = apiOf(page);
  await acceptPendingConsents(me);
  await page.setViewportSize({ width: 360, height: 780 });

  for (const lang of LANGUAGES) {
    await walk.step(`follows his journey in ${lang}`, async () => {
      // the account's language wins over the device's, so both change (as in the settings)
      await me.patchJson('/api/users/me/settings', { language: lang });
      await setLanguage(page, lang);
      for (const path of PAGES) {
        await page.goto(path);
        await expect(page.locator('fs-data-state[data-state="success"]').first()).toBeVisible();
        await expectNoMissingKeys(page);
        await noSidewaysScroll(page, `${lang} ${path}`);
        if (lang === 'de') await checkpoint(page, NAMES[path]);
      }
      await page.goto('/onboarding');
      await expect(page.getByTestId('onboarding.journey.cta')).toBeVisible();
      await expect(page.getByTestId('onboarding.bar.stepper')).toBeVisible();
      await expect(page.getByTestId('onboarding.bar.stepper.next')).toHaveCount(0);
      await page.goto('/dashboard');
      await expect(page.getByTestId('onboarding.bar.stepper.next')).toBeVisible();
    });
  }

  walk.finish({ steps: LANGUAGES.length, clicks: 0, fields: 0 });
});
