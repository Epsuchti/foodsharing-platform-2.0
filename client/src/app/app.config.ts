import {
  provideHttpClient,
  withFetch,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import { PlatformLocation, isPlatformServer } from '@angular/common';
import {
  ApplicationConfig,
  ErrorHandler,
  PLATFORM_ID,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import {
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { provideClientHydration, withNoHttpTransferCache } from '@angular/platform-browser';
import { provideServiceWorker } from '@angular/service-worker';
import { provideOptimus } from '@openng/optimus-ui/config';

import { provideApi } from './api/provide-api';
import { routes, warmInitialRoute } from './app.routes';
import { AppInfoService } from './core/app-info.service';
import { AuthFacadeService } from './core/auth/auth-facade.service';
import { SessionService } from './core/auth/session.service';
import { GlobalErrorHandler } from './core/errors/global-error-handler';
import { provideBufferedOverlayServices } from './core/overlays/buffered-overlay.services';
import { errorInterceptor } from './core/http/error.interceptor';
import { loadingInterceptor } from './core/http/loading.interceptor';
import { retryInterceptor } from './core/http/retry.interceptor';
import { I18nTitleStrategy } from './core/i18n/i18n-title.strategy';
import { I18nService } from './core/i18n/i18n.service';
import { isUiLanguage } from './core/i18n/languages';
import { SeoService } from './core/i18n/seo.service';
import { OptimusLocaleService } from './core/i18n/optimus-locale';
import { FoodsharingPreset } from './core/theme/foodsharing-preset';
import { ThemeService, themeFromApi } from './core/theme/theme.service';

/**
 * Start-up: session (GET /api/auth/session) and app info in parallel, then the translations in the account's
 * language (or the device's). Theme is applied by ThemeService on construction.
 * The prerender (app.config.server.ts) has no backend: it renders anonymously with the catalogs only.
 */
async function initializeApp(): Promise<void> {
  inject(SeoService);
  if (isPlatformServer(inject(PLATFORM_ID))) {
    await inject(I18nService).initialize();
    return;
  }
  warmInitialRoute(inject(PlatformLocation).pathname);
  const session = inject(SessionService);
  const appInfo = inject(AppInfoService);
  const i18n = inject(I18nService);
  const theme = inject(ThemeService);
  const auth = inject(AuthFacadeService);
  const locale = inject(OptimusLocaleService);

  // the device language's catalog loads in parallel with the session; the account language (usually the same)
  // is applied afterwards — one round trip less before the first render (D11.5.3)
  const [info] = await Promise.all([session.bootstrap(), appInfo.load(), i18n.preload()]);
  const user = info.authenticated ? info.user : undefined;
  if (user && themeFromApi(user.theme) !== theme.preference()) {
    theme.setPreference(themeFromApi(user.theme), { persist: false });
  }
  await i18n.initialize(user && isUiLanguage(user.language) ? user.language : null);
  locale.start();
  auth.startSessionRefresh();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    provideHttpClient(
      withFetch(),
      // order: outermost first — the error interceptor sees the final result after retries
      withInterceptors([loadingInterceptor, errorInterceptor, retryInterceptor]),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    provideApi(''),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),
    { provide: TitleStrategy, useExisting: I18nTitleStrategy },
    provideOptimus({
      ripple: false,
      theme: {
        preset: FoodsharingPreset,
        options: {
          darkModeSelector: '.fs-dark',
          cssLayer: { name: 'optimus', order: 'base, optimus, app' },
        },
      },
    }),
    provideBufferedOverlayServices(),
    provideAppInitializer(initializeApp),
    // public routes are prerendered (app.routes.server.ts); data is never fetched at build time
    provideClientHydration(withNoHttpTransferCache()),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
