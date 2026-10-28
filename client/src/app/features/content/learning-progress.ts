import { Journey, JourneyStepKey, JourneyStepStatus } from '../../api';

/** Read state of the learning chapters as far as the journey tells it (LEARN step, §11.1 step 3). */
export interface LearningProgress {
  read: number;
  total: number;
  done: boolean;
  optional: boolean;
  /** Key of the first unread chapter (from the step's next action `/learn/<key>`). */
  nextKey?: string;
  /** Keys of read chapters when the server lists them (`details.readChapters`), otherwise derived where certain. */
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
  const listed = step.details?.['readChapters'];
  let readKeys: Set<string>;
  if (Array.isArray(listed)) {
    readKeys = new Set(listed.filter((key): key is string => typeof key === 'string'));
  } else if (read >= total && total > 0) {
    readKeys = new Set(chapterKeys);
  } else if (nextKey ? read === chapterKeys.indexOf(nextKey) : read > 0) {
    // chapters are read in order ("mark as read" continues with the next one): the first `read` ones are read.
    // Without a next action (LOCKED step) this is the best guess until the server lists them (details.readChapters).
    readKeys = new Set(chapterKeys.slice(0, read));
  } else {
    readKeys = new Set();
  }
  const done = read >= total && total > 0;
  // a LOCKED step has no next action: continue with the first chapter not known to be read
  const fallbackNext =
    readKeys.size === read ? chapterKeys.find((key) => !readKeys.has(key)) : undefined;
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
