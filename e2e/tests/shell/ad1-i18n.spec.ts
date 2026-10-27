import { LANGUAGES, expect, expectNoMissingKeys, setLanguage, test } from '../../fixtures';
import { schema } from '../../support/openapi';

/** AD1 (§14.4 "translations with i18n:check"): the packaged jar serves complete catalogs for all five languages. */

test.use({ seedReset: 'off' });

const HTML_LANG: Record<string, string> = { de: 'de', gws: 'de-CH', fr: 'fr', it: 'it', en: 'en' };

test('AD1 i18n catalogs load for all five languages without missing keys @AD1', async ({ api }) => {
  const catalogs: Record<string, Record<string, unknown>> = {};
  for (const lang of LANGUAGES) {
    const response = await api.get(`/i18n/${lang}.json`);
    expect(response.status(), `/i18n/${lang}.json`).toBe(200);
    catalogs[lang] = (await response.json()) as Record<string, unknown>;
  }

  const reference = Object.keys(catalogs.de).sort();
  expect(reference.length).toBeGreaterThan(100);
  for (const lang of LANGUAGES) {
    const keys = Object.keys(catalogs[lang]).sort();
    expect(
      reference.filter((key) => !keys.includes(key)),
      `keys missing in ${lang}`,
    ).toEqual([]);
    expect(
      keys.filter((key) => !reference.includes(key)),
      `keys only in ${lang}`,
    ).toEqual([]);
    const blank = keys.filter((key) => typeof catalogs[lang][key] !== 'string' || !(catalogs[lang][key] as string).trim());
    expect(blank, `empty values in ${lang}`).toEqual([]);
  }

  // Every ApiErrorCode of the contract has a message (§12.3).
  const codes = (schema('common', 'ApiErrorCode').enum ?? []) as string[];
  expect(codes.length).toBeGreaterThan(5);
  for (const lang of LANGUAGES) {
    expect(
      codes.filter((code) => !(`errors.${code}` in catalogs[lang])),
      `errors.<CODE> missing in ${lang}`,
    ).toEqual([]);
  }

  // Not just German copied everywhere: the navigation reads differently per language.
  const homeLabels = new Set(LANGUAGES.filter((lang) => lang !== 'gws').map((lang) => catalogs[lang]['nav.dashboard']));
  expect(homeLabels.size).toBeGreaterThan(1);
});

for (const lang of LANGUAGES) {
  test(`AD1 shell renders in ${lang} without untranslated keys @AD1`, async ({ page }) => {
    await setLanguage(page, lang);
    for (const path of ['/', '/dev/gallery', '/does-not-exist']) {
      await page.goto(path);
      await expect(page.locator('html')).toHaveAttribute('lang', HTML_LANG[lang]);
      await expect(page.getByTestId('shell.languageMenu')).toBeVisible();
      await expectNoMissingKeys(page);
    }
  });
}
