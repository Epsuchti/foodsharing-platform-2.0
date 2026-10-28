import { Routes } from '@angular/router';

import { landingGuard } from '../../core/auth/landing.guard';
import { LANGUAGES } from '../../core/i18n/languages';

const loadLanding = () =>
  import('./pages/landing-page.component').then((m) => m.LandingPageComponent);

/**
 * Mounted at `''`: the landing page, plus `/de`, `/gws`, `/fr`, `/it`, `/en` — the landing page in that language
 * (hreflang alternates, prerendered; I18nService reads the language from the URL, the page gets it as the
 * `lang` input from the route data). `r/:slug` and `find-region`
 * live in the regions routes.
 */
export const PUBLIC_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [landingGuard],
    title: 'public.landing.pageTitle',
    loadComponent: loadLanding,
  },
  ...LANGUAGES.map((lang) => ({
    path: lang,
    canActivate: [landingGuard],
    title: 'public.landing.pageTitle',
    data: { lang },
    loadComponent: loadLanding,
  })),
];
