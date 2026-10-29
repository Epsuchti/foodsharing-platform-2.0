import { SleepModeUpdate, SleepStatus, UserProfile } from '../../api';

/** Sleep mode form values (A39); dates are `YYYY-MM-DD`, empty when unset. */
export interface SleepForm {
  status: SleepStatus;
  from: string;
  until: string;
  message: string;
}

export const SLEEP_MESSAGE_MAX = 1000;

/** Calendar day (`YYYY-MM-DD`) of `now` in `timeZone` (the server's "today" is Europe/Zurich, A47). */
export function todayIn(timeZone: string, now: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/** The form as stored on the account (NONE when the account has no sleep status yet). */
export function sleepFormOf(user: UserProfile): SleepForm {
  return {
    status: user.sleepStatus ?? SleepStatus.None,
    from: user.sleepFrom ?? '',
    until: user.sleepUntil ?? '',
    message: user.sleepMessage ?? '',
  };
}

/**
 * Client copy of the server rule `SleepRules.validDates` (A39): TEMPORARY needs both dates, `from ≤ until` and
 * `until ≥ today`; NONE and FULL have no dates. Returns the i18n key of the problem, or null.
 */
export function sleepFormError(form: SleepForm, today: string): string | null {
  if (form.message.length > SLEEP_MESSAGE_MAX) {
    return 'account.sleep.messageTooLong';
  }
  if (form.status !== SleepStatus.Temporary) {
    return null;
  }
  if (!form.from || !form.until) {
    return 'account.sleep.datesRequired';
  }
  if (form.from > form.until) {
    return 'account.sleep.datesOrder';
  }
  if (form.until < today) {
    return 'account.sleep.datesPast';
  }
  return null;
}

/** Request body: dates only for TEMPORARY, a blank message clears it. */
export function sleepUpdate(form: SleepForm): SleepModeUpdate {
  const temporary = form.status === SleepStatus.Temporary;
  const message = form.message.trim();
  return {
    status: form.status,
    ...(temporary ? { from: form.from, until: form.until } : {}),
    ...(message ? { message } : {}),
  };
}

/** True when the form differs from what is stored (dates only count for TEMPORARY). */
export function sleepFormChanged(form: SleepForm, stored: SleepForm): boolean {
  if (form.status !== stored.status || form.message.trim() !== stored.message.trim()) {
    return true;
  }
  return form.status === SleepStatus.Temporary && (form.from !== stored.from || form.until !== stored.until);
}

/** Loose address check before asking the server (it decides with `EMAIL_INVALID`). */
export function looksLikeEmail(value: string): boolean {
  return /^[^@\s]+@[^@\s]+$/.test(value.trim());
}
