import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { retry, timer } from 'rxjs';

import { NO_RETRY } from './http-context';

/** Backoff before retry 1 and 2. */
export const RETRY_DELAYS_MS = [300, 900] as const;

function isTransient(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) {
    return false;
  }
  if (error.status === 0) {
    // no point retrying while the browser knows it is offline
    return typeof navigator === 'undefined' || navigator.onLine !== false;
  }
  return error.status >= 500 && error.status !== 501;
}

/** Retries idempotent GETs on network errors and 5xx (max 2, 300/900 ms). Never 4xx, never mutations. */
export const retryInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.method !== 'GET' || request.context.get(NO_RETRY)) {
    return next(request);
  }
  return next(request).pipe(
    retry({
      count: RETRY_DELAYS_MS.length,
      delay: (error, retryCount) => {
        if (!isTransient(error)) {
          throw error;
        }
        return timer(RETRY_DELAYS_MS[retryCount - 1]);
      },
    }),
  );
};
