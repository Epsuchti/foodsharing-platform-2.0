import { DestroyRef, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { MapService } from '../../api';
import { backgroundRequest } from '../../core/http/http-context';

/** The address fields a lookup needs. */
export interface AddressQuery {
  street: string;
  postcode: string;
  city: string;
  /** ISO 3166-1 alpha-2; the server defaults to CH. */
  country?: string;
}

/**
 * State of the last lookup: `incomplete` (button without postcode), `searching`, `found`, `notFound` (204) and
 * `unavailable` (503 GEOCODING_UNAVAILABLE or any other failure; saving still works).
 */
export type GeocodeStatus = 'idle' | 'incomplete' | 'searching' | 'found' | 'notFound' | 'unavailable';

/** Typing pause before the automatic lookup (Photon's public instance is rate limited). */
export const AUTO_LOOKUP_DELAY_MS = 600;

/** A normalised key of the address, or null while street, postcode or city is blank. */
export function addressKey(query: AddressQuery): string | null {
  const parts = [query.street, query.postcode, query.city].map((part) => (part ?? '').trim().replace(/\s+/g, ' '));
  if (parts.some((part) => !part)) return null;
  return [(query.country ?? 'CH').trim().toUpperCase() || 'CH', ...parts].join('|').toLowerCase();
}

/**
 * Decision 171 (D2): the pin follows the typed address automatically only when street, postcode and city are filled
 * in, nobody set the pin by hand (a stored pin counts as set by hand) and the address changed since the last lookup.
 */
export function shouldAutoLookup(key: string | null, pinSetByHand: boolean, lastKey: string | null): boolean {
  return key !== null && !pinSetByHand && key !== lastKey;
}

/**
 * Address → pin for the store and Fairteiler forms (`GET /api/map/geocode-address`). Create it in an injection
 * context; `onFound` receives the position to put the pin on. The form reports typing with {@link addressChanged}
 * (debounced automatic lookup), the "find address on map" button calls {@link lookup}, and manual pins (drag, click,
 * "use my location", a loaded pin) are reported with {@link markPinSetByHand} so they are never overwritten
 * automatically.
 */
export class AddressGeocoder {
  private readonly map = inject(MapService);
  readonly status = signal<GeocodeStatus>('idle');
  readonly pinSetByHand = signal(false);
  private lastKey: string | null = null;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private request = 0;

  constructor(private readonly onFound: (position: [number, number]) => void) {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.timer);
      this.request++;
    });
  }

  /** The user changed the address: look it up after a typing pause when {@link shouldAutoLookup} allows it. */
  addressChanged(query: AddressQuery): void {
    clearTimeout(this.timer);
    if (!shouldAutoLookup(addressKey(query), this.pinSetByHand(), this.lastKey)) return;
    this.timer = setTimeout(() => void this.run(query), AUTO_LOOKUP_DELAY_MS);
  }

  /** The button: looks the address up right away, also over a pin set by hand (the user asked for it). */
  lookup(query: AddressQuery): Promise<void> {
    clearTimeout(this.timer);
    if (!query.postcode?.trim()) {
      this.status.set('incomplete');
      return Promise.resolve();
    }
    return this.run(query);
  }

  /** The pin was placed by hand or loaded with the record: a pending or running lookup no longer moves it. */
  markPinSetByHand(): void {
    clearTimeout(this.timer);
    this.request++;
    if (this.status() === 'searching') this.status.set('idle');
    this.pinSetByHand.set(true);
  }

  /** The pin was removed: the address may place it again. */
  pinCleared(): void {
    this.pinSetByHand.set(false);
    this.lastKey = null;
  }

  private async run(query: AddressQuery): Promise<void> {
    const id = ++this.request;
    this.lastKey = addressKey(query);
    this.status.set('searching');
    try {
      const result = await firstValueFrom(
        this.map.geocodeAddress({
          street: query.street?.trim() || undefined,
          postcode: query.postcode.trim(),
          city: query.city?.trim() || undefined,
          country: query.country?.trim() || undefined,
        }, 'body', false, {
          // a hint under the map, not a page load: no global bar, no automatic retry against the rate limit
          context: backgroundRequest(),
        }),
      );
      if (id !== this.request) return;
      if (!result) {
        this.status.set('notFound');
        return;
      }
      this.pinSetByHand.set(false);
      this.status.set('found');
      this.onFound([result.lat, result.lon]);
    } catch {
      if (id !== this.request) return;
      this.status.set('unavailable');
    }
  }
}
