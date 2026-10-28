import { Journey, JourneyStepKey, JourneyStepStatus } from '../../api';
import { legalKindOf, legalSlug } from '../legal/legal-kind';
import { chapterState, learningProgress } from './learning-progress';

const KEYS = ['rules', 'hygiene', 'pickup', 'etiquette'];

function journey(learn: Partial<Journey['steps'][number]>): Journey {
  return {
    complete: false,
    extraSteps: [],
    steps: [
      {
        key: JourneyStepKey.Learn,
        status: JourneyStepStatus.Available,
        estimatedDuration: 'PT20M',
        ...learn,
      },
    ],
  };
}

describe('learning progress (C1, journey step LEARN)', () => {
  it('is unknown without a journey or LEARN step', () => {
    expect(learningProgress(undefined, KEYS)).toBeUndefined();
    expect(learningProgress({ complete: true, steps: [], extraSteps: [] }, KEYS)).toBeUndefined();
  });

  it('takes the next chapter from the next action and treats earlier chapters as read', () => {
    const progress = learningProgress(
      journey({
        status: JourneyStepStatus.InProgress,
        progress: { done: 2, booked: 0, required: 4 },
        nextAction: { labelKey: 'x', route: '/learn/pickup' },
      }),
      KEYS,
    )!;
    expect(progress).toEqual(
      expect.objectContaining({ read: 2, total: 4, done: false, nextKey: 'pickup' }),
    );
    expect(KEYS.map((key) => chapterState(progress, key))).toEqual([
      'read',
      'read',
      'next',
      'open',
    ]);
  });

  it('uses the listed read chapters when the server sends them', () => {
    const progress = learningProgress(
      journey({
        progress: { done: 1, booked: 0, required: 4 },
        nextAction: { labelKey: 'x', route: '/learn/rules' },
        details: { readChapters: ['hygiene'] },
      }),
      KEYS,
    )!;
    expect(KEYS.map((key) => chapterState(progress, key))).toEqual([
      'next',
      'read',
      'open',
      'open',
    ]);
  });

  it('a locked step without next action starts with the first chapter; all read is done', () => {
    const locked = learningProgress(
      journey({ status: JourneyStepStatus.Locked, progress: { done: 0, booked: 0, required: 4 } }),
      KEYS,
    )!;
    expect(locked.nextKey).toBe('rules');
    const lockedRead = learningProgress(
      journey({ status: JourneyStepStatus.Locked, progress: { done: 1, booked: 0, required: 4 } }),
      KEYS,
    )!;
    expect(KEYS.map((key) => chapterState(lockedRead, key))).toEqual([
      'read',
      'next',
      'open',
      'open',
    ]);
    const done = learningProgress(
      journey({
        status: JourneyStepStatus.Done,
        progress: { done: 4, booked: 0, required: 4 },
        optional: true,
      }),
      KEYS,
    )!;
    expect(done).toEqual(
      expect.objectContaining({ done: true, optional: true, nextKey: undefined }),
    );
    expect(KEYS.every((key) => chapterState(done, key) === 'read')).toBe(true);
  });
});

describe('legal document slugs (Ve1)', () => {
  it('maps URL slugs and enum names to kinds, unknown to undefined', () => {
    expect(legalKindOf('privacy-policy')).toBe('PRIVACY_POLICY');
    expect(legalKindOf('LEGAL_AGREEMENT')).toBe('LEGAL_AGREEMENT');
    expect(legalKindOf('terms')).toBeUndefined();
    expect(legalKindOf(undefined)).toBeUndefined();
    expect(legalSlug('CONFIDENTIALITY_NOTICE' as never)).toBe('confidentiality-notice');
  });
});
