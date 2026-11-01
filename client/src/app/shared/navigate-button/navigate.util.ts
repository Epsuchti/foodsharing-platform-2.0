/** Directions deep links for `fs-navigate-button` (resume item 15). */
export interface NavigationLinks {
  google: string;
  apple: string;
}

export interface NavigationTarget {
  lat?: number | null;
  lon?: number | null;
  street?: string | null;
  postcode?: string | null;
  city?: string | null;
  /** Shown as the pin name in Apple Maps; never pass personal text (a basket description). */
  label?: string | null;
}

/**
 * Google and Apple Maps directions to the coordinates when both are set (`0` counts), otherwise to the address
 * (street, postcode and city, empty parts skipped); `null` when there is neither.
 */
export function navigationLinks(target: NavigationTarget): NavigationLinks | null {
  const { lat, lon } = target;
  let destination: string;
  if (lat != null && lon != null) {
    destination = `${lat},${lon}`;
  } else {
    const address = [target.street, target.postcode, target.city].filter(Boolean).join(' ');
    if (!address) return null;
    destination = encodeURIComponent(address);
  }
  const label = target.label ? `&q=${encodeURIComponent(target.label)}` : '';
  return {
    google: `https://www.google.com/maps/dir/?api=1&destination=${destination}`,
    apple: `https://maps.apple.com/?daddr=${destination}${label}`,
  };
}
