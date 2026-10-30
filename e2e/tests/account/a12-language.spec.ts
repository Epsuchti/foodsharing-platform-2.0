import { LANGUAGES, PHONE_MAX, expect, expectNoMissingKeys, test, type Language } from '../../fixtures';
import { skipPasskeyNudge } from '../auth/_auth';

/** A12: language selection (de, en, gws, fr, it), applied at once and saved with the account. */

test.use({ role: 'foodsaver' });

const HTML_LANG: Record<Language, string> = { de: 'de', en: 'en', gws: 'de-CH', fr: 'fr', it: 'it' };
const HEADER_CODE: Record<Language, string> = { de: 'DE', en: 'EN', gws: 'CH', fr: 'FR', it: 'IT' };
/** `lang.<code>`: every language is named in its own language. */
const LANGUAGE_NAME: Record<Language, string> = {
  de: 'Deutsch',
  en: 'English',
  gws: 'Schwiizerdütsch',
  fr: 'Français',
  it: 'Italiano',
};

test('A12 language switch updates UI and is persisted @A12 @page', async ({ page, apiOf }) => {
  await skipPasskeyNudge(page);
  const api = apiOf(page);
  await page.goto('/account/settings');
  await expect(page.getByTestId('account.settings.methods')).toHaveAttribute('data-state', 'success');

  // start from the other end of the list so every language is a real switch
  for (const language of [...LANGUAGES].reverse()) {
    const option = page.getByTestId(`account.settings.language.${language}`);
    if ((await option.getAttribute('aria-checked')) !== 'true') {
      const saved = page.waitForResponse(
        (response) => response.url().endsWith('/api/users/me/settings') && response.request().method() === 'PATCH',
      );
      await option.click();
      expect((await saved).status()).toBe(200);
    }
    await expect(option).toHaveAttribute('aria-checked', 'true');
    await expect(page.locator('html')).toHaveAttribute('lang', HTML_LANG[language]);
    if ((page.viewportSize()?.width ?? 1280) <= PHONE_MAX) {
      await expect(page.getByTestId('shell.languageMenu')).toContainText(HEADER_CODE[language]);
    } else {
      // desktop (decision 133): the user menu's language row names the current language
      await expect(page.getByTestId('shell.userMenu.language')).toContainText(LANGUAGE_NAME[language]);
    }
    await expectNoMissingKeys(page);
    expect((await api.getJson<{ language: Language }>('/api/users/me')).language).toBe(language);

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('lang', HTML_LANG[language]);
    await expect(page.getByTestId(`account.settings.language.${language}`)).toHaveAttribute('aria-checked', 'true');
  }
});
