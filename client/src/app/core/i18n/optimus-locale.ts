import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';
import { Optimus } from '@openng/optimus-ui/config';

import { whenIdle } from '../theme/web-font';
import { I18nService } from './i18n.service';

type Translation = Parameters<Optimus['setTranslation']>[0];

export interface CalendarNames {
  dayNames: string[];
  dayNamesShort: string[];
  dayNamesMin: string[];
  monthNames: string[];
  monthNamesShort: string[];
}

const calendarCache = new Map<string, CalendarNames>();

/**
 * Weekday/month names from Intl for a locale (Sunday first, as Optimus expects). Cached per locale and built with
 * one formatter per style: creating an `Intl.DateTimeFormat` is expensive, and the translation is rebuilt on every
 * catalog change (start-up catalog, complete catalog) — 38 formatters per call were a long task on slow phones.
 */
export function calendarNames(locale: string): CalendarNames {
  const cached = calendarCache.get(locale);
  if (cached) {
    return cached;
  }
  const day = (style: 'long' | 'short') => {
    const format = new Intl.DateTimeFormat(locale, { weekday: style, timeZone: 'UTC' });
    // 2026-11-01 is a Sunday
    return Array.from({ length: 7 }, (_, i) => format.format(new Date(Date.UTC(2026, 10, 1 + i))));
  };
  const month = (style: 'long' | 'short') => {
    const format = new Intl.DateTimeFormat(locale, { month: style, timeZone: 'UTC' });
    return Array.from({ length: 12 }, (_, i) => format.format(new Date(Date.UTC(2026, i, 15))));
  };
  const short = day('short');
  const names: CalendarNames = {
    dayNames: day('long'),
    dayNamesShort: short,
    // two letters read better than Intl's one-letter "narrow" form (M D M D F S S)
    dayNamesMin: short.map((name) => name.replace(/\.$/, '').slice(0, 2)),
    monthNames: month('long'),
    monthNamesShort: month('short'),
  };
  calendarCache.set(locale, names);
  return names;
}

/**
 * Keeps the Optimus (PrimeNG) locale in sync with the UI language: datepicker month/day names, "Today"/"Clear",
 * empty/selection messages and common aria labels. Swiss date format (dd.mm.yyyy, Monday first) for every
 * language. Instantiated once by the app initializer.
 *
 * The month/day names follow once the browser is idle (D11.5.3): the first `Intl` date formatting of a locale loads
 * its data and sat in the start-up render before the first page's LCP. Until then Optimus keeps its defaults; no
 * datepicker is open that early.
 */
@Injectable({ providedIn: 'root' })
export class OptimusLocaleService {
  private readonly i18n = inject(I18nService);
  private readonly optimus = inject(Optimus);
  private readonly view = inject(DOCUMENT).defaultView;
  /** Bumped when the names of a locale have been built. */
  private readonly calendarVersion = signal(0);
  private readonly calendarPending = new Set<string>();

  /** Names of the locale when already built; otherwise schedules them for an idle moment and returns nothing. */
  private calendar(locale: string): CalendarNames | undefined {
    this.calendarVersion();
    const cached = calendarCache.get(locale);
    if (cached || !this.view) {
      return cached ?? calendarNames(locale);
    }
    if (!this.calendarPending.has(locale)) {
      this.calendarPending.add(locale);
      whenIdle(this.view, () => {
        calendarNames(locale);
        this.calendarVersion.update((version) => version + 1);
      });
    }
    return undefined;
  }

  constructor() {
    effect(() => {
      if (!this.i18n.ready()) {
        return;
      }
      this.optimus.setTranslation(this.translation());
    });
  }

  /** Applies the current language right away (the effect keeps it in sync afterwards). */
  start(): void {
    if (this.i18n.ready()) {
      this.optimus.setTranslation(this.translation());
    }
  }

  /** The translation for the current language (reads the language/catalog signals). */
  translation(): Translation {
    const t = (key: string) => this.i18n.t(key);
    return {
      ...this.calendar(this.i18n.locale()),
      dateFormat: 'dd.mm.yy',
      firstDayOfWeek: 1,
      today: t('common.optimus.today'),
      clear: t('common.optimus.clear'),
      apply: t('common.optimus.apply'),
      accept: t('common.optimus.accept'),
      reject: t('common.optimus.reject'),
      choose: t('common.optimus.choose'),
      cancel: t('common.cancel'),
      emptyMessage: t('common.optimus.emptyMessage'),
      emptySearchMessage: t('common.optimus.emptyMessage'),
      emptyFilterMessage: t('common.optimus.emptyFilterMessage'),
      selectionMessage: t('common.optimus.selectionMessage'),
      chooseYear: t('common.optimus.chooseYear'),
      chooseMonth: t('common.optimus.chooseMonth'),
      chooseDate: t('common.optimus.chooseDate'),
      prevMonth: t('common.optimus.prevMonth'),
      nextMonth: t('common.optimus.nextMonth'),
      prevYear: t('common.optimus.prevYear'),
      nextYear: t('common.optimus.nextYear'),
      prevDecade: t('common.optimus.prevDecade'),
      nextDecade: t('common.optimus.nextDecade'),
      weekHeader: t('common.optimus.weekHeader'),
      // setTranslation merges only the top level: keep the other aria labels
      aria: {
        ...(this.optimus.getTranslation('aria') as object),
        close: t('common.close'),
        removeLabel: t('common.optimus.removeLabel'),
        selectAll: t('common.optimus.selectAll'),
        unselectAll: t('common.optimus.unselectAll'),
      },
    } as Translation;
  }
}
