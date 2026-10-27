import { Routes } from '@angular/router';

/**
 * Mounted at '' in app.routes.ts; paths inside are relative to that mount.
 * `regions/:regionId/verification`, `me/passport`, `regions/:regionId/passports` (M2 T23).
 * Empty until the page task fills it: unmatched URLs fall through to the next module and finally to not-found.
 */
export const VERIFICATION_ROUTES: Routes = [];
