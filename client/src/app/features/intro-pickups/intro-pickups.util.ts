import { ApiErrorCode, BookableIntroPickup } from '../../api';

/**
 * Why the newcomer's EinAb list is empty (review 2026-11-10 B1: only bookable dates are listed):
 * - `none`: no EinAb at all in the region
 * - `enough`: the booking limit is reached, or every date is one the user already booked
 * - `fairteiler`: the next EinAb needs a Fairteiler visit first
 * - `noMatch`: dates exist but none fits the bookings so far (guide, category, …)
 */
export type BookableEmptyKind = 'none' | 'enough' | 'fairteiler' | 'noMatch';

const ENOUGH: ReadonlySet<ApiErrorCode> = new Set([
  ApiErrorCode.UserBookingLimitReached,
  ApiErrorCode.UserAlreadyBookedInIntroPickup,
]);

/** The empty kind of the list, or null when at least one EinAb is bookable. */
export function bookableEmptyKind(items: readonly BookableIntroPickup[]): BookableEmptyKind | null {
  if (items.length === 0) {
    return 'none';
  }
  if (items.some((item) => item.bookable)) {
    return null;
  }
  const reasons = items.map((item) => item.notBookableReason);
  if (reasons.includes(ApiErrorCode.UserBookingLimitReached) || reasons.every((reason) => reason && ENOUGH.has(reason))) {
    return 'enough';
  }
  if (reasons.includes(ApiErrorCode.FairteilerVisitRequiredForThirdIntroPickup)) {
    return 'fairteiler';
  }
  return 'noMatch';
}
