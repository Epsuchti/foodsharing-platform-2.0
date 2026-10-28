import { IntroPickupCategory } from '../../api';

/** All EinAb categories in display order (EA6). */
export const INTRO_CATEGORIES: readonly IntroPickupCategory[] = Object.values(IntroPickupCategory);

export const CATEGORY_ICONS: Record<IntroPickupCategory, string> = {
  SUPERMARKET: 'pi pi-shopping-cart',
  TAKEOUT: 'pi pi-box',
  MARKET: 'pi pi-shop',
  BAKERY: 'pi pi-sun',
  RESTAURANT: 'pi pi-star',
  FAIRTEILER_CLEANING: 'pi pi-sparkles',
  MESSE: 'pi pi-building',
  GROSSABHOLUNG: 'pi pi-truck',
  ONLINE: 'pi pi-video',
};

/** ONLINE EinAbs have a call link instead of an address (category-driven form, EA6/EA10). */
export function isOnline(category: IntroPickupCategory | null | undefined): boolean {
  return category === IntroPickupCategory.Online;
}

/** Offset (ms) of `timeZone` from UTC at `date`. */
function offsetOf(date: Date, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(date);
  const get = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? 0);
  const asUtc = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'));
  return asUtc - Math.floor(date.getTime() / 1000) * 1000;
}

/** `2026-11-04T18:30` (wall time in the region's zone) → ISO instant; '' for invalid input. */
export function zonedLocalToIso(local: string, timeZone: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(local ?? '');
  if (!match) {
    return '';
  }
  const [, y, mo, d, h, mi] = match.map(Number);
  const guess = Date.UTC(y, mo - 1, d, h, mi);
  const first = guess - offsetOf(new Date(guess), timeZone);
  const second = guess - offsetOf(new Date(first), timeZone);
  return new Date(second).toISOString();
}

/** ISO instant → `2026-11-04T18:30` wall time in the region's zone (for `<input type="datetime-local">`). */
export function isoToZonedLocal(iso: string | null | undefined, timeZone: string): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const shifted = new Date(date.getTime() + offsetOf(date, timeZone));
  return shifted.toISOString().slice(0, 16);
}
