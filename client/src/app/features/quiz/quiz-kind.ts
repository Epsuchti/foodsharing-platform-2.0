import { CanMatchFn, UrlSegment } from '@angular/router';

import { QuizKind, QuizStatus } from '../../api';

/** URL slug of a quiz kind: `STORE_MANAGER` ↔ `store-manager`. */
export function quizSlug(kind: QuizKind): string {
  return kind.toLowerCase().replace(/_/g, '-');
}

export function quizKindOf(slug: string | null | undefined): QuizKind | undefined {
  const value = (slug ?? '').toUpperCase().replace(/-/g, '_');
  return (Object.values(QuizKind) as string[]).includes(value) ? (value as QuizKind) : undefined;
}

/** Route `canMatch`: the URL segment at `index` must be a quiz slug, otherwise the URL falls through to not-found. */
export function quizKindAt(index: number): CanMatchFn {
  return (_route, segments: UrlSegment[]) => quizKindOf(segments[index]?.path) !== undefined;
}

export const QUIZ_ICONS: Record<QuizKind, string> = {
  FOODSAVER: 'pi pi-shopping-bag',
  STORE_MANAGER: 'pi pi-shop',
  AMBASSADOR: 'pi pi-megaphone',
  HYGIENE: 'pi pi-shield',
  FOODSHARER: 'pi pi-heart',
  SAVING_FOOD: 'pi pi-sparkles',
  REPORTS: 'pi pi-flag',
};

/** What the status chip shows for a quiz (plan T19, quizzes page). */
export type QuizPhase =
  | 'notStarted'
  | 'running'
  | 'confirmPending'
  | 'passed'
  | 'expiring'
  | 'failed'
  | 'wait'
  | 'disqualified';

export function quizPhase(status: QuizStatus): QuizPhase {
  if (status.running) {
    return 'running';
  }
  if ((status.currentWaitDays ?? 0) < 0) {
    return 'disqualified';
  }
  if ((status.currentWaitDays ?? 0) > 0) {
    return 'wait';
  }
  if (status.requiresConfirmation) {
    return 'confirmPending';
  }
  if (status.passedAt && status.expiresInDays != null) {
    return 'expiring';
  }
  if (status.passedAt && status.lastSessionStatus !== 'FAILED') {
    return 'passed';
  }
  if (status.lastSessionStatus === 'FAILED') {
    return 'failed';
  }
  return status.passedAt ? 'passed' : 'notStarted';
}

/** A translated text in the UI language, falling back to German, then any language. */
export function localized(texts: Record<string, string> | undefined, language: string): string {
  if (!texts) {
    return '';
  }
  return texts[language] || texts['de'] || Object.values(texts).find((value) => !!value) || '';
}

/** Chip colour per phase. */
export const PHASE_TONE: Record<QuizPhase, 'neutral' | 'info' | 'success' | 'warning' | 'danger'> = {
  notStarted: 'neutral',
  running: 'info',
  confirmPending: 'warning',
  passed: 'success',
  expiring: 'warning',
  failed: 'danger',
  wait: 'warning',
  disqualified: 'danger',
};
