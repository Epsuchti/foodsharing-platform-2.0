import { vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Optimus, provideOptimus } from '@openng/optimus-ui/config';

import { provideApi } from '../../api/provide-api';
import { I18nService } from './i18n.service';
import { OptimusLocaleService, calendarNames } from './optimus-locale';

describe('calendarNames', () => {
  it('starts the week on Sunday as Optimus expects and uses the locale', () => {
    const fr = calendarNames('fr-CH');
    expect(fr.dayNames[0]).toBe('dimanche');
    expect(fr.dayNames[1]).toBe('lundi');
    expect(fr.monthNames[0]).toBe('janvier');
    expect(fr.monthNames).toHaveLength(12);
    const de = calendarNames('de-CH');
    expect(de.dayNamesMin[1]).toBe('Mo');
    expect(de.monthNames[2]).toBe('März');
  });

  it('builds the names once per locale with one formatter per style (start-up long task)', () => {
    const spy = vi.spyOn(Intl, 'DateTimeFormat');
    try {
      const first = calendarNames('it-CH');
      expect(spy).toHaveBeenCalledTimes(4);
      expect(calendarNames('it-CH')).toBe(first);
      expect(spy).toHaveBeenCalledTimes(4);
    } finally {
      spy.mockRestore();
    }
  });
});

describe('OptimusLocaleService', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideOptimus({}),
      ],
    });
    const i18n = TestBed.inject(I18nService);
    const optimus = TestBed.inject(Optimus);
    const service = TestBed.inject(OptimusLocaleService);
    return { i18n, optimus, service };
  }

  it('translates datepicker names, Today/Clear and keeps the other aria labels', () => {
    const { i18n, optimus, service } = setup();
    i18n.useCatalog(
      {
        'common.optimus.today': 'Aujourd’hui',
        'common.optimus.clear': 'Effacer',
        'common.close': 'Fermer',
      },
      'fr',
    );
    service.start();
    expect(optimus.getTranslation('today')).toBe('Aujourd’hui');
    expect(optimus.getTranslation('clear')).toBe('Effacer');
    expect(optimus.getTranslation('monthNames')[0]).toBe('janvier');
    expect(optimus.getTranslation('firstDayOfWeek')).toBe(1);
    expect(optimus.getTranslation('dateFormat')).toBe('dd.mm.yy');
    const aria = optimus.getTranslation('aria');
    expect(aria.close).toBe('Fermer');
    expect(aria.firstPageLabel).toBe('First Page');
  });

  it('follows language changes', async () => {
    const { i18n, optimus } = setup();
    i18n.useCatalog({ 'common.optimus.today': 'Heute' }, 'de');
    TestBed.tick();
    expect(optimus.getTranslation('today')).toBe('Heute');
    i18n.useCatalog({ 'common.optimus.today': 'Oggi' }, 'it');
    TestBed.tick();
    expect(optimus.getTranslation('today')).toBe('Oggi');
    expect(optimus.getTranslation('monthNames')[0]).toBe('gennaio');
  });
});
