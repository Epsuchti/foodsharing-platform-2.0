import { Journey, JourneyStepKey, JourneyStepStatus } from '../../api';

/** Read state of the learning chapters as far as the journey tells it (LEARN step, §11.1 step 3). */
export interface LearningProgress {
  read: number;
  total: number;
  done: boolean;
  optional: boolean;
  /** Key of the first unread chapter (from the step's next action `/learn/<key>`). */
  nextKey?: string;
  /** Keys of read chapters as listed by the server (`details.readChapters`); all of them once the step is done. */
  readKeys: ReadonlySet<string>;
}

export type ChapterState = 'read' | 'next' | 'open';

export function learningProgress(
  journey: Journey | undefined,
  chapterKeys: readonly string[],
): LearningProgress | undefined {
  const step = journey?.steps.find((candidate) => candidate.key === JourneyStepKey.Learn);
  if (!step) {
    return undefined;
  }
  const total = step.progress?.required ?? chapterKeys.length;
  const read = step.progress?.done ?? 0;
  const route = step.nextAction?.route ?? '';
  const nextKey = route.startsWith('/learn/')
    ? decodeURIComponent(route.slice('/learn/'.length))
    : undefined;
  // the server lists the read chapters (details.readChapters); chapters can be read in any order
  const listed = step.details?.['readChapters'];
  const done = read >= total && total > 0;
  const readKeys = Array.isArray(listed)
    ? new Set(listed.filter((key): key is string => typeof key === 'string'))
    : new Set(done ? chapterKeys : []);
  // a LOCKED step has no next action: continue with the first unread chapter
  const fallbackNext = chapterKeys.find((key) => !readKeys.has(key));
  return {
    read,
    total,
    done,
    optional: step.optional ?? false,
    nextKey: step.status === JourneyStepStatus.Done && done ? undefined : (nextKey ?? fallbackNext),
    readKeys,
  };
}

export function chapterState(progress: LearningProgress | undefined, key: string): ChapterState {
  if (progress?.readKeys.has(key)) {
    return 'read';
  }
  return progress?.nextKey === key ? 'next' : 'open';
}
