import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
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

type Catalog = Readonly<Record<string, string>>;

/**
 * Translations from the merged catalogs in /i18n/<lang>.json (built by scripts/build-i18n.mjs).
 * Initial language: localStorage['fs.lang'] → navigator.languages → de. Missing keys fall back to German,
 * then to the key itself. Usage: `i18n.t('common.save')`, `i18n.t('x.items', { count: 3 })`, `{{ 'key' | t }}`.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  private readonly session = inject(SessionService);
  private readonly usersApi = inject(UsersService);

  private readonly catalogs = new Map<UiLanguage, Promise<Catalog>>();
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
    const [current, fallback] = await Promise.all([
      this.load(language),
      language === DEFAULT_LANGUAGE ? null : this.load(DEFAULT_LANGUAGE),
    ]);
    this.current.set(current);
    this.fallback.set(fallback ?? current);
    this.applyDocumentLanguage(language);
    this.ready.set(true);
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
    if (language === DEFAULT_LANGUAGE) {
      this.fallback.set(catalog);
    } else if (!Object.keys(this.fallback()).length) {
      this.fallback.set(await this.load(DEFAULT_LANGUAGE));
    }
    this.current.set(catalog);
    this.language.set(language);
    this.ready.set(true);
    this.applyDocumentLanguage(language);
    if (remember) {
      writeStorage(LANGUAGE_STORAGE_KEY, language);
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
    this.current.set(catalog);
    this.fallback.set(fallback);
    this.language.set(language);
    this.ready.set(true);
  }

  private load(language: UiLanguage): Promise<Catalog> {
    let catalog = this.catalogs.get(language);
    if (!catalog) {
      catalog = firstValueFrom(this.http.get<Catalog>(`/i18n/${language}.json`)).catch(
        (error: unknown) => {
          this.catalogs.delete(language);
          console.error(`Could not load the ${language} translations`, error);
          return {};
        },
      );
      this.catalogs.set(language, catalog);
    }
    return catalog;
  }

  private applyDocumentLanguage(language: UiLanguage): void {
    const root = this.document.documentElement;
    root.lang = htmlLang(language);
    root.dataset['language'] = language;
  }

  private initialLanguage(): UiLanguage {
    const stored = readStorage(LANGUAGE_STORAGE_KEY);
    if (isUiLanguage(stored)) {
      return stored;
    }
    const navigatorLanguages = typeof navigator === 'undefined' ? [] : navigator.languages;
    return matchBrowserLanguage(navigatorLanguages) ?? DEFAULT_LANGUAGE;
  }
}
