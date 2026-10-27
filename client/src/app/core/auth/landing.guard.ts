import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from './session.service';

/** The public landing page is for visitors; logged-in users go straight to their dashboard. */
export const landingGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  return session.isAuthenticated() ? inject(Router).createUrlTree(['/dashboard']) : true;
};
