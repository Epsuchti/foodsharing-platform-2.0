import { expect, type Page } from '@playwright/test';
import type { Language } from './types';

export const LANGUAGES: readonly Language[] = ['de', 'en', 'gws', 'fr', 'it'];
export const LANG_STORAGE_KEY = 'fs.lang';
export const THEME_STORAGE_KEY = 'fs.theme';

/** Namespaces that always exist (A7) in case the catalogs cannot be fetched. */
const BASE_NAMESPACES = ['common', 'errors', 'nav', 'lang', 'theme', 'auth', 'account', 'history', 'dashboard', 'dev', 'public', 'layout', 'shared'];

/**
 * Sets the UI language the way a returning visitor has it (`localStorage['fs.lang']`, read by I18nService at
 * start). Applies to every later navigation of the page; reloads when the page already shows the app.
 */
export async function setLanguage(page: Page, lang: Language): Promise<void> {
  await page.addInitScript(
    ([key, value]) => {
      try {
        window.localStorage.setItem(key, value);
      } catch {
        /* storage disabled */
      }
    },
    [LANG_STORAGE_KEY, lang] as const,
  );
  if (/^https?:/.test(page.url())) {
    await page.evaluate(([key, value]) => window.localStorage.setItem(key, value), [LANG_STORAGE_KEY, lang] as const);
    await page.reload();
  }
}

export interface MissingKeyOptions {
  /** Tokens that look like keys but are legitimate text. */
  allow?: (string | RegExp)[];
  /** Scope of the scan (default `body`). */
  selector?: string;
}

/**
 * Fails when visible text contains raw translation keys (`auth.login.title`, `errors.ACCESS_DENIED`), i.e. keys
 * missing in the active catalog. Key namespaces are taken from the merged catalogs (`/i18n/<lang>.json`).
 */
export async function expectNoMissingKeys(page: Page, options: MissingKeyOptions = {}): Promise<void> {
  const namespaces = new Set([...BASE_NAMESPACES, ...(await catalogNamespaces(page))]);
  const text = await page.locator(options.selector ?? 'body').innerText();
  const candidates = [...text.matchAll(/(?<![\w@/.:-])([a-z][a-zA-Z0-9]*(?:\.[A-Za-z0-9_-]+)+)(?![\w@/-])/g)].map(
    (match) => match[1],
  );
  const raw = candidates.filter(
    (token) =>
      namespaces.has(token.split('.')[0]) &&
      !(options.allow ?? []).some((allowed) => (typeof allowed === 'string' ? allowed === token : allowed.test(token))),
  );
  expect([...new Set(raw)], `untranslated keys visible on ${page.url()}`).toEqual([]);
}

async function catalogNamespaces(page: Page): Promise<string[]> {
  if (!/^https?:/.test(page.url())) return [];
  const origin = new URL(page.url()).origin;
  const namespaces = new Set<string>();
  for (const lang of LANGUAGES) {
    try {
      const response = await page.request.get(`${origin}/i18n/${lang}.json`);
      if (!response.ok()) continue;
      const catalog = (await response.json()) as Record<string, unknown>;
      for (const key of Object.keys(catalog)) namespaces.add(key.split('.')[0]);
    } catch {
      /* catalog not served yet */
    }
  }
  return [...namespaces];
}

export const i18n = { LANGUAGES, setLanguage, expectNoMissingKeys };
export type I18nHelpers = typeof i18n;
