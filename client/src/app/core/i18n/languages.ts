/** UI languages. `gws` is Swiss German (the code foodsharing.network uses), formatted with the de-CH locale. */
export type UiLanguage = 'de' | 'en' | 'gws' | 'fr' | 'it';

export const LANGUAGES: readonly UiLanguage[] = ['de', 'gws', 'fr', 'it', 'en'];

export const DEFAULT_LANGUAGE: UiLanguage = 'de';

/** Locale for Intl (dates, numbers, plural rules). */
const INTL_LOCALES: Record<UiLanguage, string> = {
  de: 'de-CH',
  gws: 'de-CH',
  en: 'en-CH',
  fr: 'fr-CH',
  it: 'it-CH',
};

/** Value for `<html lang>`; screen readers know de-CH, but not "gws". */
const HTML_LANGS: Record<UiLanguage, string> = {
  de: 'de',
  gws: 'de-CH',
  en: 'en',
  fr: 'fr',
  it: 'it',
};

export function isUiLanguage(value: unknown): value is UiLanguage {
  return typeof value === 'string' && (LANGUAGES as readonly string[]).includes(value);
}

export function intlLocale(language: UiLanguage): string {
  return INTL_LOCALES[language];
}

export function htmlLang(language: UiLanguage): string {
  return HTML_LANGS[language];
}

/** Picks the first supported language from the browser preferences (e.g. ["fr-CH", "de"] → "fr"). */
export function matchBrowserLanguage(
  preferences: readonly string[] | undefined,
): UiLanguage | null {
  for (const preference of preferences ?? []) {
    const tag = preference.toLowerCase();
    if (tag === 'gsw' || tag.startsWith('gsw-')) {
      return 'gws';
    }
    const base = tag.split('-')[0];
    if (isUiLanguage(base)) {
      return base;
    }
  }
  return null;
}
