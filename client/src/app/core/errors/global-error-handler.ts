import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler, Injectable, Injector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';

import { PublicService } from '../../api';
import { I18nService } from '../i18n/i18n.service';

/** Same message at most once per minute (toast and report). */
export const ERROR_DEDUP_MS = 60_000;

/**
 * Unexpected client errors: logged, shown as a non-blocking toast (`errors.CLIENT_ERROR`) and reported to
 * POST /api/public/client-errors with route and user agent. HTTP errors are not handled here; DataState,
 * runMutation and the error interceptor take care of them.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  // resolved lazily: the ErrorHandler is created before most of the app
  private readonly injector = inject(Injector);
  private readonly lastSeen = new Map<string, number>();

  handleError(error: unknown): void {
    const unwrapped = this.unwrap(error);
    console.error(unwrapped);
    if (unwrapped instanceof HttpErrorResponse) {
      return;
    }
    const message = unwrapped instanceof Error ? unwrapped.message : String(unwrapped);
    const now = Date.now();
    const last = this.lastSeen.get(message);
    if (last !== undefined && now - last < ERROR_DEDUP_MS) {
      return;
    }
    this.lastSeen.set(message, now);
    try {
      this.toast();
      this.report(message, unwrapped instanceof Error ? unwrapped.stack : undefined);
    } catch {
      // never let the error handler throw
    }
  }

  private unwrap(error: unknown): unknown {
    const maybe = error as { rejection?: unknown; ngOriginalError?: unknown } | null;
    return maybe?.rejection ?? maybe?.ngOriginalError ?? error;
  }

  private toast(): void {
    const i18n = this.injector.get(I18nService);
    this.injector.get(MessageService).add({
      severity: 'warn',
      summary: i18n.t('common.error.title'),
      detail: i18n.t('errors.CLIENT_ERROR'),
      life: 8000,
    });
  }

  private report(message: string, stack: string | undefined): void {
    const route = this.injector.get(Router).url || '/';
    this.injector
      .get(PublicService)
      .reportClientError({
        clientErrorReport: {
          message: message.slice(0, 2000),
          stack: stack?.slice(0, 16000),
          route: route.slice(0, 500),
          userAgent: (globalThis.navigator?.userAgent ?? 'unknown').slice(0, 500),
        },
      })
      .subscribe({ error: () => undefined });
  }
}
