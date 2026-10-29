import { Role, SleepModeUpdate, SleepStatus, UnitMembership, UnitType, UserProfile } from '../../api';

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

// --- calendar feed (A11, A49) ---

/** `events` parameter of the feed: which events go into the calendar (server default `maybe`). */
export type CalendarEventFilter = 'every' | 'invitations' | 'maybe' | 'accepted' | 'none';
export const CALENDAR_EVENT_FILTERS: readonly CalendarEventFilter[] = ['every', 'invitations', 'maybe', 'accepted', 'none'];
/** Reminder choices in minutes before the start (server: at most 5 values, 0..10080). */
export const CALENDAR_REMINDER_CHOICES: readonly number[] = [15, 60, 180, 1440, 2880];
export const CALENDAR_REMINDERS_MAX = 5;

export interface CalendarFeedOptions {
  events: CalendarEventFilter;
  pickups: boolean;
  introPickups: boolean;
  history: boolean;
  reminders: readonly number[];
}

/** The server defaults: nothing has to be added to the URL for them. */
export const CALENDAR_FEED_DEFAULTS: CalendarFeedOptions = {
  events: 'maybe',
  pickups: true,
  introPickups: true,
  history: false,
  reminders: [],
};

/** Feed URL (`CalendarToken.url`) plus the query parameters that differ from the server defaults. */
export function calendarFeedUrl(baseUrl: string, options: CalendarFeedOptions): string {
  const params: string[] = [];
  if (options.events !== CALENDAR_FEED_DEFAULTS.events) {
    params.push(`events=${options.events}`);
  }
  for (const key of ['pickups', 'introPickups', 'history'] as const) {
    if (options[key] !== CALENDAR_FEED_DEFAULTS[key]) {
      params.push(`${key}=${options[key]}`);
    }
  }
  const reminders = [...new Set(options.reminders)].sort((a, b) => a - b).slice(0, CALENDAR_REMINDERS_MAX);
  if (reminders.length) {
    params.push(`reminders=${reminders.join(',')}`);
  }
  if (!params.length) {
    return baseUrl;
  }
  return baseUrl + (baseUrl.includes('?') ? '&' : '?') + params.join('&');
}

/** The same feed as `webcal://` link, which calendar apps open as a subscription. */
export function webcalUrl(url: string): string {
  return url.replace(/^https?:\/\//i, 'webcal://');
}

// --- business card (A14) ---

/** Roles a card can show: FOODSAVER up to the own role, never above AMBASSADOR (server `BusinessCardService.check`). */
export function businessCardRoles(own: Role | null | undefined): Role[] {
  const cardRoles = [Role.Foodsaver, Role.StoreManager, Role.Ambassador];
  const order: Role[] = [Role.Foodsharer, ...cardRoles, Role.Orga];
  const ownIndex = own ? order.indexOf(own) : -1;
  return cardRoles.filter((role) => order.indexOf(role) <= ownIndex);
}

/** Regions offered for a role: member regions (no working groups), for AMBASSADOR only those the user leads. */
export function businessCardRegions(units: readonly UnitMembership[] | undefined, role: Role | null): UnitMembership[] {
  return (units ?? [])
    .filter((unit) => unit.member && unit.type !== UnitType.WorkingGroup && (role !== Role.Ambassador || unit.admin))
    .sort((a, b) => Number(b.homeRegion) - Number(a.homeRegion) || a.name.localeCompare(b.name));
}

/** File name of the card PDF. */
export function businessCardFileName(slug: string | undefined): string {
  return `business-card-${slug || 'foodsharing'}.pdf`;
}

// --- account deletion (A13) ---

export const DELETE_REASON_MAX = 1000;

/** The re-typed address must equal the account address (case and surrounding blanks ignored). */
export function deletionConfirmed(typed: string, email: string | undefined): boolean {
  return !!email && typed.trim().toLowerCase() === email.trim().toLowerCase();
}

// --- downloads ---

/** Starts a plain browser download of a same-origin URL (the session cookie goes along). */
export function downloadUrl(document: Document, url: string, fileName: string): void {
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.rel = 'noopener';
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
}

/** Saves a blob the app already received (business card PDF) under `fileName`. */
export function saveBlob(document: Document, blob: Blob, fileName: string): void {
  const view = document.defaultView;
  if (!view?.URL?.createObjectURL) {
    return;
  }
  const url = view.URL.createObjectURL(blob);
  downloadUrl(document, url, fileName);
  view.setTimeout(() => view.URL.revokeObjectURL(url), 1000);
}
