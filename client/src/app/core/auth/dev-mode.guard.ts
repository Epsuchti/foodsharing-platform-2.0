import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AppInfoService } from '../app-info.service';

/** Dev pages (gallery, …) only exist when the backend runs the local or e2e profile; otherwise "not found". */
export const devModeGuard: CanActivateFn = async () => {
  const appInfo = inject(AppInfoService);
  const router = inject(Router);
  await appInfo.load();
  return appInfo.devMode() ? true : router.createUrlTree(['/not-found']);
};
