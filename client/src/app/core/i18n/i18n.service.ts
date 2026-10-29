import { DOCUMENT, PlatformLocation, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Language, UsersService } from '../../api';
import { SessionService } from '../auth/session.service';
import { readStorage, writeStorage } from '../storage';
import {
  DEFAULT_LANGUAGE,
  UiLanguage,
  htmlLang,
  intlLocale,
  isUiLanguage,
  matchBrowserLanguage,
} from './languages';
import { MessageParams, formatMessage } from './message-format';

export const LANGUAGE_STORAGE_KEY = 'fs.lang';
/** Mirrors the chosen language for the server, which picks the prerendered landing page by it (D11.5.3). */
export const LANGUAGE_COOKIE = 'fs.lang';

export type Catalog = Readonly<Record<string, string>>;

/**
 * Loads one merged catalog. Default: GET /i18n/<lang>.json; the prerender (app.config.server.ts) reads the
 * build output instead.
 */
export const CATALOG_LOADER = new InjectionToken<(language: UiLanguage) => Promise<Catalog>>(
  'CATALOG_LOADER',
);

/** `/en`, `/fr`, … are the landing page in that language (hreflang alternates, prerendered). */
export function languageFromPath(path: string): UiLanguage | null {
  const segment = path.replace(/^\/+|\/+$/g, '');
  return isUiLanguage(segment) ? segment : null;
}

/**
 * Translations from the merged catalogs in /i18n/<lang>.json (built by scripts/build-i18n.mjs, which fills keys
 * missing in a language from German, so only the active catalog is ever loaded).
 * Initial language: URL `/<lang>` → localStorage['fs.lang'] → navigator.languages → de. Unknown keys render as
 * the key itself. Usage: `i18n.t('common.save')`, `i18n.t('x.items', { count: 3 })`, `{{ 'key' | t }}`.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  private readonly session = inject(SessionService);
  private readonly usersApi = inject(UsersService);
  private readonly location = inject(PlatformLocation);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly loader = inject(CATALOG_LOADER, { optional: true });

  private readonly catalogs = new Map<UiLanguage, Promise<Catalog>>();
  private readonly startCatalogs = new Map<UiLanguage, Promise<Catalog>>();
  /** Languages whose complete catalog is in memory (not only the start-up part). */
  private readonly complete = new Set<UiLanguage>();
  private readonly current = signal<Catalog>({});
  private readonly fallback = signal<Catalog>({});

  readonly language = signal<UiLanguage>(this.initialLanguage());
  readonly locale = computed(() => intlLocale(this.language()));
  /** True after the first catalog has been loaded. */
  readonly ready = signal(false);

  /** Loads the catalogs; `preferred` (e.g. the account language) overrides the device default. */
  async initialize(preferred?: UiLanguage | null): Promise<void> {
    if (preferred && preferred !== this.language()) {
      this.language.set(preferred);
      writeStorage(LANGUAGE_STORAGE_KEY, preferred);
    }
    const language = this.language();
    const catalog = this.split ? await this.loadStart(language) : await this.load(language);
    if (!this.split) {
      this.complete.add(language);
    }
    this.current.set(catalog);
    this.fallback.set(catalog);
    this.applyDocumentLanguage(language);
    if (!languageFromPath(this.location.pathname)) {
      this.writeCookie(language);
    }
    this.ready.set(true);
  }

  /**
   * Starts loading the catalog of a language chosen on this device (URL `/<lang>` or stored) while the session
   * loads; `initialize()` reuses it. A mere browser-language guess is not preloaded: for a logged-in user the
   * account language decides, and a wrong guess would download a second catalog.
   */
  preload(): Promise<unknown> {
    const chosen = languageFromPath(this.location.pathname) ?? readStorage(LANGUAGE_STORAGE_KEY);
    if (!isUiLanguage(chosen)) {
      return Promise.resolve();
    }
    return this.split ? this.loadStart(chosen) : this.load(chosen);
  }

  /**
   * Loads the complete catalog of the current language (D11.5.3): start-up loads only `<lang>.start.json` (shell,
   * shared components and the start-up pages, see scripts/build-i18n.mjs START_MODULES); the rest follows once the
   * first page is shown (ShellComponent) or before a route of another module activates (`catalogGuard`).
   */
  ensureComplete(): Promise<void> {
    const language = this.language();
    if (this.complete.has(language)) {
      return Promise.resolve();
    }
    return this.load(language).then((catalog) => {
      if (Object.keys(catalog).length === 0) {
        return; // failed: the next guard or page retries
      }
      this.complete.add(language);
      if (this.language() === language) {
        this.current.set(catalog);
        this.fallback.set(catalog);
      }
    });
  }

  /**
   * Switches the UI language. Stored per device; when logged in, also saved as the account setting
   * (PATCH /api/users/me/settings) unless `persist` is false (e.g. when applying the account setting itself).
   */
  async setLanguage(
    language: UiLanguage,
    options: { persist?: boolean; remember?: boolean } = {},
  ): Promise<void> {
    const { persist = true, remember = true } = options;
    const catalog = await this.load(language);
    this.complete.add(language);
    this.current.set(catalog);
    this.fallback.set(catalog);
    this.language.set(language);
    this.ready.set(true);
    this.applyDocumentLanguage(language);
    if (remember) {
      writeStorage(LANGUAGE_STORAGE_KEY, language);
      this.writeCookie(language);
    }
    const user = this.session.user();
    if (persist && user && user.language !== (language as string)) {
      this.session.patchUser({ language: language as Language });
      this.usersApi
        .updateMySettings({ userSettingsUpdate: { language: language as Language } })
        .subscribe({
          error: () => undefined, // best effort; the device setting already applies
        });
    }
  }

  /** Translates a key; params fill `{name}` placeholders and ICU-lite plural/select blocks. */
  t(key: string, params?: MessageParams): string {
    const template = this.current()[key] ?? this.fallback()[key];
    if (template === undefined) {
      return key;
    }
    return formatMessage(template, params, this.locale());
  }

  has(key: string): boolean {
    return key in this.current() || key in this.fallback();
  }

  /** Translation of `errors.<code>`, with `errors.UNKNOWN` as fallback. */
  error(code: string | undefined, params?: MessageParams): string {
    const key = `errors.${code ?? 'UNKNOWN'}`;
    return this.has(key) ? this.t(key, params) : this.t('errors.UNKNOWN', params);
  }

  /** Replaces the catalogs in memory (unit tests, gallery). */
  useCatalog(
    catalog: Catalog,
    language: UiLanguage = this.language(),
    fallback: Catalog = catalog,
  ): void {
    this.catalogs.set(language, Promise.resolve(catalog));
    this.complete.add(language);
    this.current.set(catalog);
    this.fallback.set(fallback);
    this.language.set(language);
    this.ready.set(true);
  }

  /** Browser builds load the catalog in two parts; tests and the prerender (custom loader) load it at once. */
  private get split(): boolean {
    return this.browser && !this.loader;
  }

  private loadStart(language: UiLanguage): Promise<Catalog> {
    let catalog = this.startCatalogs.get(language);
    if (!catalog) {
      catalog = firstValueFrom(this.http.get<Catalog>(`/i18n/${language}.start.json`)).catch((error: unknown) => {
        this.startCatalogs.delete(language);
        console.error(`Could not load the ${language} start-up translations`, error);
        return this.load(language);
      });
      this.startCatalogs.set(language, catalog);
    }
    return catalog;
  }

  private load(language: UiLanguage): Promise<Catalog> {
    let catalog = this.catalogs.get(language);
    if (!catalog) {
      const request = this.loader
        ? this.loader(language)
        : firstValueFrom(this.http.get<Catalog>(`/i18n/${language}.json`));
      catalog = request.catch((error: unknown) => {
        this.catalogs.delete(language);
        console.error(`Could not load the ${language} translations`, error);
        return {};
      });
      this.catalogs.set(language, catalog);
    }
    return catalog;
  }

  private applyDocumentLanguage(language: UiLanguage): void {
    const root = this.document.documentElement;
    root.lang = htmlLang(language);
    root.setAttribute('data-language', language);
  }

  private writeCookie(language: UiLanguage): void {
    if (this.browser) {
      this.document.cookie = `${LANGUAGE_COOKIE}=${language}; path=/; max-age=31536000; samesite=lax`;
    }
  }

  private initialLanguage(): UiLanguage {
    const fromPath = languageFromPath(this.location.pathname);
    if (fromPath) {
      return fromPath;
    }
    if (!this.browser) {
      return DEFAULT_LANGUAGE;
    }
    const stored = readStorage(LANGUAGE_STORAGE_KEY);
    if (isUiLanguage(stored)) {
      return stored;
    }
    const navigatorLanguages = typeof navigator === 'undefined' ? [] : navigator.languages;
    return matchBrowserLanguage(navigatorLanguages) ?? DEFAULT_LANGUAGE;
  }
}
