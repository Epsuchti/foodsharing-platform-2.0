import {
  FaqEntry,
  PublicStatistics,
  ReleaseNote,
  ReleaseNoteCreate,
  ReleaseNoteTranslation,
  ReleaseNoteUpdate,
} from '../../api';

/** Lower case without accents, so "Fairteiler" matches "fairteiler" and "écrire" matches "ecrire". */
export function foldText(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/** FAQ entries whose question or answer contains every word of `query` (any order, case and accents ignored). */
export function filterFaq(entries: readonly FaqEntry[], query: string): FaqEntry[] {
  const words = foldText(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return [...entries];
  }
  return entries.filter((entry) => {
    const text = foldText(`${entry.question} ${entry.answer}`);
    return words.every((word) => text.includes(word));
  });
}

/**
 * Versions of the release notes the visitor has not seen yet (C4). `notes` are newest first; `seen` is the newest
 * version shown on an earlier visit (`localStorage['fs.releaseNotesSeen']`). Without an earlier visit only the newest
 * note counts as new, so a first visit is not a wall of "new" badges; an unknown seen version (deleted note) counts
 * as seen everything older than the newest note.
 */
export function unseenVersions(notes: readonly ReleaseNote[], seen: string | null): Set<string> {
  if (notes.length === 0) {
    return new Set();
  }
  if (!seen) {
    return new Set([notes[0].version]);
  }
  const index = notes.findIndex((note) => note.version === seen);
  const unseen = index < 0 ? notes.slice(0, 1) : notes.slice(0, index);
  return new Set(unseen.map((note) => note.version));
}

/** Whether the header shows the "what's new" dot: the newest note differs from the one seen last. */
export function hasUnseenReleaseNote(
  newestVersion: string | null | undefined,
  seen: string | null,
): boolean {
  return !!newestVersion && newestVersion !== seen;
}

/** Calendar day (`YYYY-MM-DD`) of `now` in Zurich, the default release date of a new note. */
export function zurichDay(now: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Zurich',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

export interface ReleaseNoteForm {
  version: string;
  releasedAt: string;
  titles: Partial<Record<string, string>>;
  bodies: Partial<Record<string, string>>;
}

/**
 * Request body of a release note (C4) from the five-language form: German is the main text, every other language
 * with both a title and a text becomes a translation (added or replaced). On an edit, `saved` are the languages the
 * note has translations for; one emptied completely is removed (`removeTranslations`). `incomplete` lists languages
 * with only one of the two.
 */
export function releaseNoteRequest(
  form: ReleaseNoteForm,
  saved: readonly string[] = [],
): {
  body: ReleaseNoteCreate & Pick<ReleaseNoteUpdate, 'removeTranslations'>;
  incomplete: string[];
} {
  const text = (value: string | undefined) => value?.trim() ?? '';
  const translations: Record<string, ReleaseNoteTranslation> = {};
  const incomplete: string[] = [];
  const languages = new Set([...Object.keys(form.titles), ...Object.keys(form.bodies)]);
  for (const language of languages) {
    if (language === 'de') {
      continue;
    }
    const title = text(form.titles[language]);
    const body = text(form.bodies[language]);
    if (title && body) {
      translations[language] = { title, body };
    } else if (title || body) {
      incomplete.push(language);
    }
  }
  const removeTranslations = saved
    .filter((language) => language !== 'de' && !text(form.titles[language]) && !text(form.bodies[language]))
    .sort();
  return {
    body: {
      version: form.version.trim(),
      releasedAt: form.releasedAt,
      title: text(form.titles['de']),
      body: text(form.bodies['de']),
      ...(Object.keys(translations).length ? { translations } : {}),
      ...(removeTranslations.length ? { removeTranslations } : {}),
    },
    incomplete: incomplete.sort(),
  };
}

export interface StatisticsTile {
  key: keyof Omit<PublicStatistics, 'asOf'>;
  icon: string;
  value: number;
  /** kg are shown without decimals; the counter rounds. */
  fractionDigits: number;
}

/** The seven counters of the statistics page (C3), in display order. */
export function statisticsTiles(stats: PublicStatistics): StatisticsTile[] {
  const tile = (key: StatisticsTile['key'], icon: string): StatisticsTile => ({
    key,
    icon,
    value: Math.max(0, Number(stats[key]) || 0),
    fractionDigits: 0,
  });
  return [
    tile('kgSaved', 'pi pi-box'),
    tile('pickups', 'pi pi-shopping-bag'),
    tile('foodsavers', 'pi pi-users'),
    tile('stores', 'pi pi-shop'),
    tile('cooperations', 'pi pi-link'),
    tile('foodSharePoints', 'pi pi-map-marker'),
    tile('baskets', 'pi pi-shopping-cart'),
  ];
}
