import {
  BasketCreate,
  BasketKind,
  BasketLocationType,
  BasketRequestAnswerStatusEnum,
  BasketRequestStatus,
  BasketSummary,
  BasketUpdate,
} from '../../api';
import { MapMarker } from '../../shared/map-view/map-view.component';

/** Server limits (plan-m6 B5, `BasketCreate`, `listNearbyBaskets`). */
export const MAX_DISTANCE_KM = 50;
export const DEFAULT_DISTANCE_KM = 10;
export const MIN_LIFETIME_DAYS = 1;
export const MAX_LIFETIME_DAYS = 21;
export const DEFAULT_LIFETIME_DAYS = 7;
export const DESCRIPTION_MAX = 4000;
export const REQUEST_MESSAGE_MAX = 2000;

export const BASKET_KINDS: readonly BasketKind[] = Object.values(BasketKind);

/** Statuses the owner can set on a request (`BasketRequestAnswer.status`). */
export const ANSWER_STATUSES: readonly BasketRequestAnswerStatusEnum[] = Object.values(BasketRequestAnswerStatusEnum);

/** A request the owner has not answered yet (the requester may still withdraw it). */
export function isOpenRequest(status: BasketRequestStatus): boolean {
  return status === BasketRequestStatus.Requested || status === BasketRequestStatus.Read;
}

/** Answers the owner can still give: every answer except the current status; none for withdrawn requests. */
export function answerActions(status: BasketRequestStatus): BasketRequestAnswerStatusEnum[] {
  if (status === BasketRequestStatus.Withdrawn) return [];
  return ANSWER_STATUSES.filter((answer) => (answer as string) !== (status as string));
}

/** Clamps a slider value to 1..MAX_DISTANCE_KM (integer). */
export function clampDistance(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_DISTANCE_KM;
  return Math.min(MAX_DISTANCE_KM, Math.max(1, Math.round(value)));
}

/** Coordinates of a user profile, or null when the profile has none (the server treats 0/0 as missing too). */
export function profilePosition(profile: { latitude?: number | null; longitude?: number | null } | null | undefined): [number, number] | null {
  const lat = profile?.latitude;
  const lon = profile?.longitude;
  if (lat == null || lon == null || (lat === 0 && lon === 0)) return null;
  return [lat, lon];
}

/** Map pins of basket summaries (kind `basket`, label = description). */
export function basketMarkers(baskets: readonly Pick<BasketSummary, 'id' | 'lat' | 'lon' | 'description'>[]): MapMarker[] {
  return baskets.map((basket) => ({ id: basket.id, kind: 'basket', lat: basket.lat, lon: basket.lon, label: basket.description }));
}

/** Photo URL in another variant size (the server returns `…?size=md`; A41 sizes sm|md|lg). */
export function photoVariant(url: string | null | undefined, size: 'sm' | 'md' | 'lg'): string | undefined {
  if (!url) return undefined;
  if (!url.startsWith('/api/uploads/')) return url;
  return /[?&]size=/.test(url) ? url.replace(/([?&]size=)[a-z]+/, `$1${size}`) : `${url}${url.includes('?') ? '&' : '?'}size=${size}`;
}

/** Distance for cards: one decimal below 10 km, whole kilometres above. */
export function formatDistance(km: number | null | undefined, locale: string): string | undefined {
  if (km == null) return undefined;
  const digits = km < 10 ? 1 : 0;
  return new Intl.NumberFormat(locale, { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(km);
}

/** Values of the basket form (see BasketFormPageComponent). */
export interface BasketFormValue {
  description: string;
  weightKg: number | null;
  kinds: BasketKind[];
  groceryIds: string[];
  locationType: BasketLocationType;
  position: [number, number] | null;
  contactMessage: boolean;
  contactPhone: boolean;
  contactMobile: boolean;
  phone: string | null;
  mobile: string | null;
  lifetimeDays: number;
  photoUploadId: string | null;
}

/** Server `BasketRules.validCoordinates`: in range and not (0, 0). */
function validPin(position: [number, number] | null): boolean {
  if (!position) return false;
  const [lat, lon] = position;
  return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 && !(lat === 0 && lon === 0);
}

/**
 * Client-side copy of the server rules (BasketRules.locate + validate) so the form explains itself before sending:
 * `BASKET_CONTACT_REQUIRED` without any contact type, `BASKET_LOCATION_REQUIRED` for CUSTOM without a valid pin and for
 * HOME when the own profile has no coordinates (`homeKnown`). Returns the `ApiErrorCode`s that apply. Pinned to the
 * server by the shared cases in `server/src/test/resources/rule-cases/basket-form.json` (both test suites run them).
 */
export function basketFormProblems(value: BasketFormValue, homeKnown: boolean): ('BASKET_CONTACT_REQUIRED' | 'BASKET_LOCATION_REQUIRED')[] {
  const problems: ('BASKET_CONTACT_REQUIRED' | 'BASKET_LOCATION_REQUIRED')[] = [];
  const location = value.locationType === BasketLocationType.Custom ? validPin(value.position) : homeKnown;
  if (!location) problems.push('BASKET_LOCATION_REQUIRED');
  if (!value.contactMessage && !value.contactPhone && !value.contactMobile) problems.push('BASKET_CONTACT_REQUIRED');
  return problems;
}

function common(value: BasketFormValue): BasketUpdate {
  const custom = value.locationType === BasketLocationType.Custom && value.position;
  const text = (s: string | null) => (s && s.trim() ? s.trim() : undefined);
  return {
    description: value.description.trim(),
    weightGrams: value.weightKg != null && value.weightKg > 0 ? Math.round(value.weightKg * 1000) : undefined,
    kinds: [...value.kinds],
    groceryIds: [...value.groceryIds],
    locationType: value.locationType,
    lat: custom ? value.position![0] : undefined,
    lon: custom ? value.position![1] : undefined,
    contactMessage: value.contactMessage,
    contactPhone: value.contactPhone,
    contactMobile: value.contactMobile,
    // numbers only travel with their contact type (the server ignores unflagged ones anyway)
    phone: value.contactPhone ? text(value.phone) : undefined,
    mobile: value.contactMobile ? text(value.mobile) : undefined,
    photoUploadId: value.photoUploadId ?? undefined,
  };
}

/** `POST /api/baskets` body. */
export function basketCreateBody(value: BasketFormValue): BasketCreate {
  const body = common(value);
  return {
    ...body,
    description: body.description!,
    locationType: body.locationType!,
    contactMessage: body.contactMessage!,
    contactPhone: body.contactPhone!,
    contactMobile: body.contactMobile!,
    lifetimeDays: Math.min(MAX_LIFETIME_DAYS, Math.max(MIN_LIFETIME_DAYS, Math.round(value.lifetimeDays))),
  };
}

/** `PATCH /api/baskets/{id}` body (the lifetime cannot change after creation). */
export function basketUpdateBody(value: BasketFormValue): BasketUpdate {
  return common(value);
}
