import { Achievement, AchievementSystemKey, AchievementVisibility, Award, DuplicateMode, Role } from '../../api';
import {
  NEW_ACHIEVEMENT,
  achievementFormValue,
  awardExpired,
  isoDay,
  mayDelete,
  sortAwards,
  toAchievementCreate,
  toAchievementUpdate,
  translationsMissingName,
} from './achievements.util';

const ZH = '00000010-0001-4000-8000-000000000040';

function achievement(overrides: Partial<Achievement> = {}): Achievement {
  return {
    id: 'a3',
    regionId: ZH,
    name: 'Fairteiler-Putzheld',
    description: 'Hat beim Putztag mitgeholfen.',
    icon: 'sparkles',
    visibility: AchievementVisibility.Scope,
    duplicateMode: DuplicateMode.Multiple,
    awardedCount: 1,
    mayAdminister: true,
    translations: {
      en: { name: 'Cleaning hero', description: 'Helped at the cleaning day.' },
      fr: { name: 'Héros du nettoyage' },
    },
    ...overrides,
  };
}

function award(id: string, overrides: Partial<Award> = {}): Award {
  return {
    id,
    achievementId: 'a3',
    achievementName: 'Fairteiler-Putzheld',
    icon: 'sparkles',
    user: { id: `u-${id}`, displayName: id, role: Role.Foodsaver, verified: true },
    awardedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  };
}

describe('achievements.util', () => {
  describe('achievementFormValue', () => {
    it('knows the German base text for German viewers and fills the translations', () => {
      const start = achievementFormValue(achievement(), 'de');
      expect(start.baseKnown).toBe(true);
      expect(start.value.names).toEqual({ de: 'Fairteiler-Putzheld', en: 'Cleaning hero', fr: 'Héros du nettoyage' });
      expect(start.value.descriptions).toEqual({ de: 'Hat beim Putztag mitgeholfen.', en: 'Helped at the cleaning day.' });
      expect(start.value.icon).toBe('sparkles');
      expect(start.value.validityDays).toBeNull();
    });

    it('knows the base text when the viewer language has no translation', () => {
      expect(achievementFormValue(achievement(), 'it').baseKnown).toBe(true);
    });

    it('leaves German empty when the server answered in the viewer language', () => {
      const start = achievementFormValue(achievement({ name: 'Cleaning hero', description: 'Helped at the cleaning day.' }), 'en');
      expect(start.baseKnown).toBe(false);
      expect(start.value.names.de).toBeUndefined();
      expect(start.value.descriptions.de).toBeUndefined();
      expect(start.value.names.en).toBe('Cleaning hero');
    });
  });

  it('requires a name for every translated description', () => {
    expect(translationsMissingName({ names: { de: 'X', en: 'Y' }, descriptions: { en: 'd', fr: 'd' } })).toEqual(['fr']);
    expect(translationsMissingName({ names: { de: 'X' }, descriptions: { de: 'only German' } })).toEqual([]);
  });

  it('builds the create body with the region and only filled translations', () => {
    const body = toAchievementCreate(
      {
        ...NEW_ACHIEVEMENT,
        names: { de: ' Velokurier ', en: 'Bike courier', fr: '  ' },
        descriptions: { de: '', en: 'Rides bikes.' },
        icon: 'truck',
        validityDays: 30,
      },
      ZH,
    );
    expect(body).toEqual({
      regionId: ZH,
      name: 'Velokurier',
      icon: 'truck',
      visibility: AchievementVisibility.Scope,
      duplicateMode: DuplicateMode.Override,
      validityDays: 30,
      translations: { en: { name: 'Bike courier', description: 'Rides bikes.' } },
    });
    expect(toAchievementCreate({ ...NEW_ACHIEVEMENT, names: { de: 'Global' } }, null)).not.toHaveProperty('regionId');
  });

  it('builds the update body: an unknown base text is only sent when entered', () => {
    const value = { ...NEW_ACHIEVEMENT, names: { en: 'Cleaning hero' }, descriptions: {} };
    expect(toAchievementUpdate(value, false)).toEqual({
      icon: 'trophy',
      visibility: AchievementVisibility.Scope,
      duplicateMode: DuplicateMode.Override,
      translations: { en: { name: 'Cleaning hero' } },
    });
    // a known base: an emptied German description clears it
    expect(toAchievementUpdate({ ...value, names: { de: 'Putzheld' } }, true)).toMatchObject({ name: 'Putzheld', description: '', translations: {} });
  });

  it('sorts revoked awards last and recognises expired ones', () => {
    const list = [award('r', { revokedAt: '2026-10-02T10:00:00Z' }), award('a'), award('b', { validUntil: '2026-02-20' })];
    expect(sortAwards(list).map((a) => a.id)).toEqual(['a', 'b', 'r']);
    expect(awardExpired(list[2], '2026-11-02')).toBe(true);
    expect(awardExpired(list[1], '2026-11-02')).toBe(false);
    expect(awardExpired({ validUntil: '2026-02-20', revokedAt: '2026-03-01T00:00:00Z' }, '2026-11-02')).toBe(false);
  });

  it('formats the calendar day in the region time zone', () => {
    expect(isoDay(new Date('2026-11-01T23:30:00Z'), 'Europe/Zurich')).toBe('2026-11-02');
    expect(isoDay(new Date('2026-11-01T22:30:00Z'), 'UTC')).toBe('2026-11-01');
  });

  it('protects system achievements from deletion', () => {
    expect(mayDelete(achievement())).toBe(true);
    expect(mayDelete(achievement({ systemKey: AchievementSystemKey.HygieneCertificate }))).toBe(false);
    expect(mayDelete(achievement({ mayAdminister: false }))).toBe(false);
  });
});
