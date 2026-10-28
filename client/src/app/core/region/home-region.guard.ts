import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { homeRegion } from '../auth/permission.util';
import { SessionService } from '../auth/session.service';

/**
 * `/regions/home/...` (navigation links that do not know the region) → the same URL with the active or home
 * region's id; users without a region go to `/find-region`. Put it on routes with a `:regionId` param.
 */
export const homeRegionGuard: CanActivateFn = (route, state) => {
  if (route.paramMap.get('regionId') !== 'home') {
    return true;
  }
  const session = inject(SessionService);
  const router = inject(Router);
  const home = homeRegion(session.session());
  if (!home) {
    return router.createUrlTree(['/find-region']);
  }
  return router.parseUrl(state.url.replace('/regions/home', `/regions/${home.id}`));
};
