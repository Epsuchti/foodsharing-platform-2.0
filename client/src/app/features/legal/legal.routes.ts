import { Routes } from '@angular/router';

/**
 * Mounted at '' in app.routes.ts; paths inside are relative to that mount.
 * `legal/:kind` documents and `consent` (pending consents, target of consentGuard) (M2 T20).
 * Empty until the page task fills it: unmatched URLs fall through to the next module and finally to not-found.
 */
export const LEGAL_ROUTES: Routes = [];
