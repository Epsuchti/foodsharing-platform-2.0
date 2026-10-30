import { inject } from '@angular/core';
import { CanActivateChildFn } from '@angular/router';

import { I18nService } from './i18n.service';

/**
 * URLs that render with the start-up catalog alone (scripts/build-i18n.mjs START_MODULES): the landing page and its
 * language variants, login, onboarding, legal pages, error pages and the bells page. Public region pages (`/r/…`)
 * and `/find-region` are not start pages: their texts live in the regions and intro-pickups catalogs, so a deep link
 * showed raw keys (design review M7 round 1).
 */
const START_URL = /^\/(?:(?:de|en|fr|it|gws)\/?)?(?:$|[?#]|(?:login|onboarding|legal|forbidden|bells)(?:[/?#]|$))/;

export function isStartUrl(url: string): boolean {
  return START_URL.test(url);
}

/**
 * Every other route waits for the complete catalog before it activates (D11.5.3: start-up loads only the start-up
 * part, I18nService.ensureComplete). Usually the catalog is already there: the shell loads it after the first page.
 */
export const catalogGuard: CanActivateChildFn = async (_route, state) => {
  if (!isStartUrl(state.url)) {
    await inject(I18nService).ensureComplete();
  }
  return true;
};
