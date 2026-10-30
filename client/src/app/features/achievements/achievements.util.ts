import {
  Achievement,
  AchievementCreate,
  AchievementTranslation,
  AchievementUpdate,
  AchievementVisibility,
  Award,
  DuplicateMode,
} from '../../api';
import { LANGUAGES, UiLanguage } from '../../core/i18n/languages';
import { TranslatedText } from '../../shared/language-tabs-editor/language-tabs-editor.component';

/** Order of the visibility select, most open first (A52). */
export const VISIBILITIES: readonly AchievementVisibility[] = [
  AchievementVisibility.Global,
  AchievementVisibility.Scope,
  AchievementVisibility.StoreManagers,
  AchievementVisibility.Private,
  AchievementVisibility.Hidden,
];

export const DUPLICATE_MODES: readonly DuplicateMode[] = [DuplicateMode.Override, DuplicateMode.Multiple];

export const NAME_MAX = 200;
export const DESCRIPTION_MAX = 4000;
export const NOTICE_MAX = 1000;
export const VALIDITY_MAX = 3650;

/** Values of the define/edit dialog: names and descriptions per language (German = the base row, A52). */
export interface AchievementFormValue {
  names: TranslatedText;
  descriptions: TranslatedText;
  icon: string;
  visibility: AchievementVisibility;
  duplicateMode: DuplicateMode;
  validityDays: number | null;
}

/** Dialog start values plus whether the German base text is known (see {@link achievementFormValue}). */
export interface AchievementFormStart {
  value: AchievementFormValue;
  baseKnown: boolean;
}

export const NEW_ACHIEVEMENT: AchievementFormValue = {
  names: {},
  descriptions: {},
  icon: 'trophy',
  visibility: AchievementVisibility.Scope,
  duplicateMode: DuplicateMode.Override,
  validityDays: null,
};

/**
 * Form values of an existing achievement. The server resolves `name`/`description` in the viewer's preferred language
 * and `translations` holds only the non-German rows, so the German base text is only known when the viewer reads German
 * or has no translation of their own; otherwise the German fields start empty and are only sent when filled.
 */
export function achievementFormValue(achievement: Achievement, viewerLanguage: string): AchievementFormStart {
  const translations = achievement.translations ?? {};
  const baseKnown = viewerLanguage === 'de' || !translations[viewerLanguage];
  const names: TranslatedText = {};
  const descriptions: TranslatedText = {};
  if (baseKnown) {
    names.de = achievement.name;
    if (achievement.description) descriptions.de = achievement.description;
  }
  for (const lang of LANGUAGES) {
    const text = lang === 'de' ? undefined : translations[lang];
    if (text) {
      names[lang] = text.name;
      if (text.description) descriptions[lang] = text.description;
    }
  }
  return {
    baseKnown,
    value: {
      names,
      descriptions,
      icon: achievement.icon,
      visibility: achievement.visibility,
      duplicateMode: achievement.duplicateMode,
      validityDays: achievement.validityDays ?? null,
    },
  };
}

/** Languages with a description but no name: a translation needs its name (`AchievementTranslation.name`). */
export function translationsMissingName(value: Pick<AchievementFormValue, 'names' | 'descriptions'>): UiLanguage[] {
  return LANGUAGES.filter((lang) => lang !== 'de' && !!value.descriptions[lang]?.trim() && !value.names[lang]?.trim());
}

function translations(value: AchievementFormValue): Record<string, AchievementTranslation> {
  const result: Record<string, AchievementTranslation> = {};
  for (const lang of LANGUAGES) {
    const name = value.names[lang]?.trim();
    if (lang === 'de' || !name) continue;
    const description = value.descriptions[lang]?.trim();
    result[lang] = description ? { name, description } : { name };
  }
  return result;
}

export function toAchievementCreate(value: AchievementFormValue, regionId: string | null): AchievementCreate {
  const description = value.descriptions.de?.trim();
  return {
    ...(regionId ? { regionId } : {}),
    name: (value.names.de ?? '').trim(),
    icon: value.icon,
    visibility: value.visibility,
    duplicateMode: value.duplicateMode,
    ...(description ? { description } : {}),
    ...(value.validityDays ? { validityDays: value.validityDays } : {}),
    translations: translations(value),
  };
}

/**
 * PATCH body: translations are always sent in full (the server replaces the set). With a known base text an empty
 * German description clears it; with an unknown one the German fields are only sent when the editor filled them.
 */
export function toAchievementUpdate(value: AchievementFormValue, baseKnown: boolean): AchievementUpdate {
  const name = value.names.de?.trim();
  const description = value.descriptions.de?.trim() ?? '';
  return {
    ...(name ? { name } : {}),
    ...(baseKnown || description ? { description } : {}),
    icon: value.icon,
    visibility: value.visibility,
    duplicateMode: value.duplicateMode,
    ...(value.validityDays ? { validityDays: value.validityDays } : {}),
    translations: translations(value),
  };
}

/** Active awards first (newest first, as the server sends them), revoked ones after. */
export function sortAwards(awards: readonly Award[]): Award[] {
  return [...awards.filter((award) => !award.revokedAt), ...awards.filter((award) => !!award.revokedAt)];
}

/** Calendar day (`YYYY-MM-DD`) of `date` in `timeZone`, the format of `Award.validUntil`. */
export function isoDay(date: Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}

/** An award whose end date lies before `today` (`YYYY-MM-DD`, region time) has expired but is not revoked. */
export function awardExpired(award: Pick<Award, 'validUntil' | 'revokedAt'>, today: string): boolean {
  return !award.revokedAt && !!award.validUntil && award.validUntil < today;
}

/** System achievements (hygiene, KAM certificate) cannot be deleted (409 ACHIEVEMENT_SYSTEM_PROTECTED). */
export function mayDelete(achievement: Pick<Achievement, 'mayAdminister' | 'systemKey'>): boolean {
  return achievement.mayAdminister && !achievement.systemKey;
}
