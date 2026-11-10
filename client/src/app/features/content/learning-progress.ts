import { Journey, JourneyStepKey, JourneyStepStatus } from '../../api';

/** Read state of the learning chapters as far as the journey tells it (LEARN step, §11.1 step 3, C1). */
export interface LearningProgress {
  /** Required chapters read (only required chapters count, C1). */
  read: number;
  total: number;
  /** Every required chapter is read. */
  done: boolean;
  /** The step is DONE: all required chapters read, learning not mandatory, or already a foodsaver (C1 rule). */
  stepDone: boolean;
  /** Reading is voluntary: the region does not require it, or the step is done without reading everything. */
  optional: boolean;
  /** Key of the first unread required chapter (from the step's next action `/learn/<key>`). */
  nextKey?: string;
  /** Keys of read chapters (`details.readKeys`, optional ones too; else `details.readChapters`). */
  readKeys: ReadonlySet<string>;
}

export type ChapterState = 'read' | 'next' | 'open';

function stringList(value: unknown): string[] | undefined {
  return Array.isArray(value) ? value.filter((key): key is string => typeof key === 'string') : undefined;
}

/** `requiredKeys`: keys of the required chapters in reading order (optional chapters never count). */
export function learningProgress(
  journey: Journey | undefined,
  requiredKeys: readonly string[],
): LearningProgress | undefined {
  const step = journey?.steps.find((candidate) => candidate.key === JourneyStepKey.Learn);
  if (!step) {
    return undefined;
  }
  const total = step.progress?.required ?? requiredKeys.length;
  const read = step.progress?.done ?? 0;
  const route = step.nextAction?.route ?? '';
  const nextKey = route.startsWith('/learn/')
    ? decodeURIComponent(route.slice('/learn/'.length))
    : undefined;
  const done = read >= total && total > 0;
  const stepDone = step.status === JourneyStepStatus.Done;
  // the server lists the read chapters; chapters can be read in any order
  const listed = stringList(step.details?.['readKeys']) ?? stringList(step.details?.['readChapters']);
  const readKeys = new Set(listed ?? (done ? requiredKeys : []));
  // a LOCKED or DONE step has no next action: continue with the first unread required chapter
  const fallbackNext = requiredKeys.find((key) => !readKeys.has(key));
  return {
    read,
    total,
    done,
    stepDone,
    optional: (step.optional ?? false) || (stepDone && !done),
    nextKey: done ? undefined : (nextKey ?? fallbackNext),
    readKeys,
  };
}

export function chapterState(progress: LearningProgress | undefined, key: string): ChapterState {
  if (progress?.readKeys.has(key)) {
    return 'read';
  }
  return progress?.nextKey === key ? 'next' : 'open';
}
