import { UserCleaningShift, UserPickup } from '../../api';

/** Date helpers of the pickup pages: pickups are UTC instants shown in the store region's time zone (A20). */

/** Offset of `timeZone` from UTC at `instant`, in milliseconds. */
function offsetMs(instant: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(new Date(instant));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const asUtc = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'));
  return asUtc - Math.floor(instant / 1000) * 1000;
}

/** `2026-11-05` + `19:00` in `Europe/Zurich` → `2026-11-05T18:00:00Z` (the API's pickupAt). */
export function zonedToUtc(date: string, time: string, timeZone: string): string {
  const [y, m, d] = date.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const guess = Date.UTC(y, m - 1, d, hh, mm);
  let instant = guess - offsetMs(guess, timeZone);
  instant = guess - offsetMs(instant, timeZone);
  return new Date(instant).toISOString().replace('.000Z', 'Z');
}

/** Calendar day (`YYYY-MM-DD`) of an instant in `timeZone` (day grouping, agenda). */
export function dayOf(instant: string | number | Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(
    new Date(instant),
  );
}

/** Wall time (`HH:mm`, 24 h) of an instant in `timeZone` (move dialog preset). */
export function timeOf(instant: string | number | Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-GB', { timeZone, hourCycle: 'h23', hour: '2-digit', minute: '2-digit' }).format(
    new Date(instant),
  );
}

export interface MoveSummary {
  /** People signed up who get PICKUP_MOVED (everyone but the mover). */
  notified: number;
  /** Linked EinAbs that move along (EA16), and the newcomers booked on them. */
  introPickups: number;
  booked: number;
}

/** PU10 move dialog summary from the pickup's sign-ups (managers see every linked EinAb on them). */
export function moveSummary(
  slots: readonly { user: { id: string }; linkedIntroPickup?: { bookedCount: number } }[],
  myId: string | null | undefined,
): MoveSummary {
  const linked = slots.filter((slot) => !!slot.linkedIntroPickup);
  return {
    notified: slots.filter((slot) => slot.user.id !== myId).length,
    introPickups: linked.length,
    booked: linked.reduce((sum, slot) => sum + (slot.linkedIntroPickup?.bookedCount ?? 0), 0),
  };
}

/** `YYYY-MM-DD` plus `days`. */
export function addDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

export interface DayGroup<T> {
  day: string;
  items: T[];
}

/** Groups items (sorted by `pickupAt`) by their local calendar day. */
export function groupByDay<T extends { pickupAt: string }>(items: readonly T[], timeZone: string): DayGroup<T>[] {
  const groups: DayGroup<T>[] = [];
  for (const item of [...items].sort((a, b) => a.pickupAt.localeCompare(b.pickupAt))) {
    const day = dayOf(item.pickupAt, timeZone);
    const last = groups.at(-1);
    if (last?.day === day) last.items.push(item);
    else groups.push({ day, items: [item] });
  }
  return groups;
}

// --- slot states (pickup slots proposal §2) --------------------------------------------------------------------

export type SlotState = 'confirmed' | 'unconfirmed' | 'mineConfirmed' | 'mineUnconfirmed';

/** State of a taken slot as the viewer sees it: own slots are "mine", the rest confirmed or unconfirmed. */
export function slotState(slot: { user: { id: string }; confirmed: boolean }, myId: string | null | undefined): SlotState {
  const mine = !!myId && slot.user.id === myId;
  if (mine) return slot.confirmed ? 'mineConfirmed' : 'mineUnconfirmed';
  return slot.confirmed ? 'confirmed' : 'unconfirmed';
}

export interface SlotSummary {
  confirmed: number;
  unconfirmed: number;
  total: number;
}

/** Counts from `taken` and `confirmed` sign-ups (the options page only has the counts); never below 0. */
export function countSummary(taken: number, confirmed: number, total: number): SlotSummary {
  const safeConfirmed = Math.max(0, Math.min(confirmed, taken));
  return { confirmed: safeConfirmed, unconfirmed: Math.max(0, taken - safeConfirmed), total };
}

/** Summary of one pickup: "✓ 2 von 3 bestätigt · ⧗ 1 unbestätigt". */
export function pickupSummary(slots: readonly { confirmed: boolean }[], slotCount: number): SlotSummary {
  return countSummary(slots.length, slots.filter((slot) => slot.confirmed).length, slotCount);
}

/** Unconfirmed sign-ups across pickups (the manager's "Unbestätigt (n)" chip). */
export function unconfirmedCount(pickups: readonly { occupiedSlots: readonly { confirmed: boolean }[] }[]): number {
  return pickups.reduce((sum, pickup) => sum + pickup.occupiedSlots.filter((slot) => !slot.confirmed).length, 0);
}

/** `displayName` up to the first space; lives with the slot component, which shows it in the manager's split pill. */
export { firstName } from '../../shared/pickup-slot/pickup-slot.component';

/**
 * Whether the user is signed up for this pickup option: the server sets `isConfirmed` only then (review fix A2). Such
 * an option shows a "Signed up" chip instead of the sign-up button.
 */
export function isSignedUp(option: { isConfirmed?: boolean | null }): boolean {
  return option.isConfirmed != null;
}

/**
 * Pickups with at least one free slot plus the user's own sign-ups, by time (PU7: full pickups are never offered;
 * /pickups and the home card). The server already left out what the sign-up would refuse (review fix A1).
 */
export function openOptions<
  T extends { pickupAt: string; slotCount: number; occupiedSlots: readonly unknown[]; isConfirmed?: boolean | null },
>(options: readonly T[]): T[] {
  return options
    .filter((option) => option.occupiedSlots.length < option.slotCount || isSignedUp(option))
    .sort((a, b) => a.pickupAt.localeCompare(b.pickupAt));
}

/** An entry of "My pickups": a pickup or a cleaning shift (decision 154). */
export interface MyPickupEntry {
  key: string;
  at: string;
  pickup?: UserPickup;
  shift?: UserCleaningShift;
}

/** Pickups and cleaning shifts in one list by time; on the same instant a pickup comes first. */
export function mergeByTime(pickups: UserPickup[], shifts: UserCleaningShift[]): MyPickupEntry[] {
  return [
    ...pickups.map((pickup) => ({ key: `p${pickup.store.id}${pickup.pickupAt}`, at: pickup.pickupAt, pickup })),
    ...shifts.map((shift) => ({ key: `c${shift.fsp.id}${shift.shiftAt}`, at: shift.shiftAt, shift })),
  ].sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());
}
