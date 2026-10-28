import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { legalKindAt } from './legal-kind';

/**
 * Mounted at '' in app.routes.ts; paths inside are full paths.
 * `consent` (pending legal consents, target of consentGuard, EA4), `legal/:kind` (public documents, Ve1) and
 * `admin/legal` (versions, ORGA; the server answers 403 for everybody else).
 */
export const LEGAL_ROUTES: Routes = [
  {
    path: 'consent',
    canActivate: [authGuard],
    title: 'legal.consent.pageTitle',
    loadComponent: () =>
      import('./pages/consent-page.component').then((m) => m.ConsentPageComponent),
  },
  {
    path: 'legal/:kind',
    canMatch: [legalKindAt(1)],
    title: 'legal.document.pageTitle',
    loadComponent: () =>
      import('./pages/legal-document-page.component').then((m) => m.LegalDocumentPageComponent),
  },
  {
    path: 'admin/legal',
    canActivate: [authGuard],
    title: 'legal.admin.pageTitle',
    loadComponent: () =>
      import('./pages/legal-versions-page.component').then((m) => m.LegalVersionsPageComponent),
  },
];
