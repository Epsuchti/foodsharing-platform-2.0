import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';

/**
 * Leaves the app for the OAuth client's redirect URI (consent decision). A token so unit tests can see the target
 * instead of navigating jsdom.
 */
export const OAUTH_REDIRECT = new InjectionToken<(url: string) => void>('OAUTH_REDIRECT', {
  providedIn: 'root',
  factory: () => {
    const document = inject(DOCUMENT);
    return (url: string) => document.location.assign(url);
  },
});
