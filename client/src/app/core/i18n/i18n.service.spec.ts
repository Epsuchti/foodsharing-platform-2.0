import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Language, Role, Theme } from '../../api';
import { provideApi } from '../../api/provide-api';
import { SessionService } from '../auth/session.service';
import { I18nService, LANGUAGE_STORAGE_KEY, languageFromPath } from './i18n.service';
import { matchBrowserLanguage } from './languages';
import { formatMessage } from './message-format';

describe('formatMessage', () => {
  it('interpolates named params and keeps unknown ones visible', () => {
    expect(formatMessage('Hallo {name}!', { name: 'Anna' }, 'de-CH')).toBe('Hallo Anna!');
    expect(formatMessage('Hallo {name}!', {}, 'de-CH')).toBe('Hallo {name}!');
  });

  it('formats numbers with the locale', () => {
    expect(formatMessage('{n} kg', { n: 1234.5 }, 'de-CH')).toBe(
      `${new Intl.NumberFormat('de-CH').format(1234.5)} kg`,
    );
    expect(formatMessage('{n} kg', { n: 1234.5 }, 'de-CH')).not.toBe('1234.5 kg');
  });

  it('picks plural forms with exact matches, plural rules and #', () => {
    const template = '{count, plural, =0 {Keine Abholung} one {# Abholung} other {# Abholungen}}';
    expect(formatMessage(template, { count: 0 }, 'de-CH')).toBe('Keine Abholung');
    expect(formatMessage(template, { count: 1 }, 'de-CH')).toBe('1 Abholung');
    expect(formatMessage(template, { count: 5 }, 'de-CH')).toBe('5 Abholungen');
  });

  it('uses the locale plural rules (French treats 0 and 1 as one)', () => {
    const template = '{count, plural, one {# collecte} other {# collectes}}';
    expect(formatMessage(template, { count: 0 }, 'fr-CH')).toBe('0 collecte');
    expect(formatMessage(template, { count: 2 }, 'fr-CH')).toBe('2 collectes');
  });

  it('supports nested placeholders and select', () => {
    const template = '{count, plural, one {{name} hat # Platz} other {{name} hat # Plätze}}';
    expect(formatMessage(template, { count: 3, name: 'Luca' }, 'de-CH')).toBe('Luca hat 3 Plätze');
    expect(
      formatMessage('{kind, select, store {Betrieb} other {Ort}}', { kind: 'store' }, 'de-CH'),
    ).toBe('Betrieb');
    expect(
      formatMessage('{kind, select, store {Betrieb} other {Ort}}', { kind: 'x' }, 'de-CH'),
    ).toBe('Ort');
  });
});

describe('matchBrowserLanguage', () => {
  it('maps browser preferences to UI languages', () => {
    expect(matchBrowserLanguage(['fr-CH', 'de'])).toBe('fr');
    expect(matchBrowserLanguage(['gsw-CH'])).toBe('gws');
    expect(matchBrowserLanguage(['es', 'it-IT'])).toBe('it');
    expect(matchBrowserLanguage(['es'])).toBeNull();
  });
});

describe('I18nService', () => {
  let backend: HttpTestingController;

  function setup(): I18nService {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    return TestBed.inject(I18nService);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => backend.verify());

  it('loads only the stored language, sets <html lang> and mirrors the language into a cookie', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
    const i18n = setup();
    const ready = i18n.initialize();
    // German gaps are filled by build-i18n.mjs, so the German catalog is never requested (D11.5.3)
    backend.expectOne('/i18n/fr.json').flush({ 'x.hello': 'Bonjour {name}' });
    await ready;
    backend.expectNone('/i18n/de.json');

    expect(i18n.language()).toBe('fr');
    expect(i18n.locale()).toBe('fr-CH');
    expect(i18n.t('x.hello', { name: 'Chloé' })).toBe('Bonjour Chloé');
    expect(i18n.t('x.missing')).toBe('x.missing');
    expect(document.documentElement.lang).toBe('fr');
    expect(document.cookie).toContain('fs.lang=fr');
  });

  it('reads the language of the /<lang> landing URLs', () => {
    expect(languageFromPath('/en')).toBe('en');
    expect(languageFromPath('/gws/')).toBe('gws');
    expect(languageFromPath('/')).toBeNull();
    expect(languageFromPath('/items')).toBeNull();
    expect(languageFromPath('/en/stores')).toBeNull();
  });

  it('maps gws to de-CH for <html lang> and Intl', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'gws');
    const i18n = setup();
    const ready = i18n.initialize();
    backend.expectOne('/i18n/gws.json').flush({});
    await ready;
    expect(document.documentElement.lang).toBe('de-CH');
    expect(document.documentElement.dataset['language']).toBe('gws');
    expect(i18n.locale()).toBe('de-CH');
  });

  it('switches language, remembers it and saves it to the account when logged in', async () => {
    const i18n = setup();
    i18n.useCatalog({ 'x.hello': 'Hallo' }, 'de');
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: 'u1',
        email: 'a@b.ch',
        displayName: 'Anna',
        role: Role.Foodsaver,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });

    const switched = i18n.setLanguage('it');
    backend.expectOne('/i18n/it.json').flush({ 'x.hello': 'Ciao' });
    await switched;

    expect(i18n.t('x.hello')).toBe('Ciao');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('it');
    const patch = backend.expectOne('/api/users/me/settings');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ language: 'it' });
    patch.flush({});
    expect(TestBed.inject(SessionService).user()?.language).toBe('it');
  });

  it('does not call the API when logged out or persist is false', async () => {
    const i18n = setup();
    i18n.useCatalog({}, 'de');
    const switched = i18n.setLanguage('en', { persist: false });
    backend.expectOne('/i18n/en.json').flush({});
    await switched;
    backend.expectNone('/api/users/me/settings');
  });

  it('error() falls back to errors.UNKNOWN', () => {
    const i18n = setup();
    i18n.useCatalog({
      'errors.UNKNOWN': 'Unbekannt',
      'errors.TOO_MANY_REQUESTS': 'Warte {seconds} s',
    });
    expect(i18n.error('TOO_MANY_REQUESTS', { seconds: 5 })).toBe('Warte 5 s');
    expect(i18n.error('SOMETHING_NEW')).toBe('Unbekannt');
  });
});
