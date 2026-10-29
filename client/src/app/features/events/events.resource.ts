import { inject } from '@angular/core';
import { Observable, from, switchMap } from 'rxjs';

import { EventSummary, EventType, EventsService } from '../../api';

/**
 * Lazy ICS downloads for `fs-calendar-export-button` (called on click, not on render; E3). The generator types the
 * calendar body as a Blob, the button wants text. Exempt from the DataState rule as a `*.resource.ts` file.
 */
export function eventIcs(): (eventId: string) => () => Observable<string> {
  const api = inject(EventsService);
  return (eventId) => () =>
    api.getEventIcs({ eventId }, 'body', false, { httpHeaderAccept: 'text/calendar' }).pipe(switchMap((blob) => from(blobText(blob))));
}

export function myEventsIcs(): () => Observable<string> {
  const api = inject(EventsService);
  return () => api.getMyEventsIcs('body', false, { httpHeaderAccept: 'text/calendar' }).pipe(switchMap((blob) => from(blobText(blob))));
}

function blobText(value: Blob | string): Promise<string> {
  return typeof value === 'string' ? Promise.resolve(value) : value.text();
}

export interface EventDraft {
  type: EventType;
  name: string;
  startsAt: string;
  endsAt: string;
  locationName: string;
  onlineLink: string;
}

/** Client-side validation mirroring A37b: name, start < end, OFFLINE needs a location name, ONLINE an https link. */
export function eventErrors(draft: EventDraft): string[] {
  const errors: string[] = [];
  if (!draft.name.trim()) errors.push('name');
  const start = Date.parse(draft.startsAt);
  const end = Date.parse(draft.endsAt);
  if (Number.isNaN(start)) errors.push('startsAt');
  if (Number.isNaN(end) || (!Number.isNaN(start) && end <= start)) errors.push('endsAt');
  if (draft.type === EventType.Offline && !draft.locationName.trim()) errors.push('location');
  const link = draft.onlineLink.trim();
  if ((draft.type === EventType.Online && !link) || (link && !/^https:\/\/\S+\.\S+/.test(link))) errors.push('onlineLink');
  return errors;
}

/** `<input type="datetime-local">` value (local time, minutes) ↔ ISO instant. */
export function toLocalInput(iso: string | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}

/** Groups events (sorted by start) into months: key `YYYY-MM`, label date = first of month. */
export function groupByMonth(events: readonly EventSummary[]): { key: string; month: string; events: EventSummary[] }[] {
  const groups = new Map<string, EventSummary[]>();
  for (const event of [...events].sort((a, b) => a.startsAt.localeCompare(b.startsAt))) {
    const date = new Date(event.startsAt);
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
    groups.set(key, [...(groups.get(key) ?? []), event]);
  }
  return [...groups].map(([key, list]) => ({ key, month: `${key}-01T12:00:00Z`, events: list }));
}

export const EVENT_TYPE_ICON: Readonly<Record<EventType, string>> = {
  OFFLINE: 'pi pi-map-marker',
  ONLINE: 'pi pi-video',
  OTHER: 'pi pi-calendar',
};
