import { RenderMode, ServerRoute } from '@angular/ssr';

import { LANGUAGES } from './core/i18n/languages';

/**
 * Prerendered at build time (plan §11.2 SEO, D11.5.3): the landing page `/` and its per-language variants
 * `/de`, `/gws`, `/fr`, `/it`, `/en` (hreflang alternates). SpaForwardingController serves `<lang>/index.html`
 * to anonymous visitors and the client hydrates it.
 *
 * Everything else is client-rendered (`index.csr.html`): its content (region pages `/r/:slug`, CMS pages, legal
 * texts) comes from the API, which does not exist at build time, and the jar has no Node runtime for
 * request-time rendering.
 */
export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Prerender },
  ...LANGUAGES.map((lang): ServerRoute => ({ path: lang, renderMode: RenderMode.Prerender })),
  { path: '**', renderMode: RenderMode.Client },
];
