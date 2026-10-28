import { LANGUAGES, expectNoMissingKeys, setLanguage, test, expect } from '../../fixtures';

/** Login and magic-link pages have no untranslated keys in any of the five languages (review-m1 §2). */

test.use({ seedReset: 'off' });

const PAGES = [
  { path: '/login', ready: 'auth.login.email' },
  { path: '/login/sent?email=anna.beispiel%40example.org', ready: null },
  { path: '/login/verify?token=not-a-token', ready: null },
];

for (const lang of LANGUAGES) {
  test(`A2 login and magic link pages are fully translated in ${lang} @A2`, async ({ page }) => {
    await setLanguage(page, lang);
    for (const { path, ready } of PAGES) {
      await page.goto(path);
      await expect(page.locator('html')).toHaveAttribute('data-language', lang);
      if (ready) await expect(page.getByTestId(ready)).toBeVisible();
      await expect(page.locator('main h1').first()).toBeVisible();
      // the verify page settles into its error view for the invalid token
      await expect(page.locator('[data-state="loading"]')).toHaveCount(0);
      await expectNoMissingKeys(page);
    }
  });
}
