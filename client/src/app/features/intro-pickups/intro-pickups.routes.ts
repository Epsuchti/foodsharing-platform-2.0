import { Routes } from '@angular/router';

/**
 * Mounted at '' in app.routes.ts; paths inside are relative to that mount.
 * `intro-pickups/**`, `me/intro-pickups`, `guide/**`, `regions/:regionId/newcomers`, `regions/:regionId/intro-pickups/**` (M2 T21).
 * Empty until the page task fills it: unmatched URLs fall through to the next module and finally to not-found.
 */
export const INTRO_PICKUPS_ROUTES: Routes = [];
