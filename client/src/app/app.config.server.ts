import { ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';

import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { CATALOG_LOADER, Catalog } from './core/i18n/i18n.service';
import { UiLanguage } from './core/i18n/languages';

/** Build-time catalogs (public/i18n is written by build-i18n.mjs before `ng build`). */
const CATALOGS: Record<UiLanguage, () => Promise<{ default: Catalog }>> = {
  de: () => import('../../public/i18n/de.json'),
  gws: () => import('../../public/i18n/gws.json'),
  fr: () => import('../../public/i18n/fr.json'),
  it: () => import('../../public/i18n/it.json'),
  en: () => import('../../public/i18n/en.json'),
};

/** Prerender (SSG, `outputMode: static`) configuration — see app.routes.server.ts. */
const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    {
      provide: CATALOG_LOADER,
      useValue: async (language: UiLanguage) => (await CATALOGS[language]()).default,
    },
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
