import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';

import { FeatureToggleKey } from '../../api';
import { FeatureToggleService } from './feature-toggle.service';

/**
 * Route guard for toggled features (A43): `canActivate: [featureGuard(FeatureToggleKey.Baskets)]`. Waits for the
 * toggles; a disabled (or not loadable) feature leads to /not-found, so switched-off pages look like missing ones.
 */
export function featureGuard(key: FeatureToggleKey): CanActivateFn & CanMatchFn {
  return async () => {
    const toggles = inject(FeatureToggleService);
    const router = inject(Router);
    await toggles.load();
    return toggles.isEnabled(key) ? true : router.createUrlTree(['/not-found']);
  };
}
