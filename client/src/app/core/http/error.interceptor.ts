import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { catchError, tap, throwError } from 'rxjs';

import { AuthFacadeService } from '../auth/auth-facade.service';
import { SessionExpiredService } from '../auth/session-expired.service';
import { SessionService } from '../auth/session.service';
import { ApiHttpErrorResponse, toApiErrorView } from '../data/api-error';
import { OnlineStatusService } from '../online/online-status.service';
import { NO_SESSION_EXPIRED_DIALOG } from './http-context';

const SESSION_ENDPOINT = /\/api\/auth\/session$/;

/**
 * Central HTTP error handling (plan §12.3):
 *  - 401 while logged in → session-expired dialog (login with return URL)
 *  - 403 → session refresh (role may have changed), error passed on to the component state
 *  - status 0 → OnlineStatusService.reportFailure() (offline banner, auto-retry later)
 *  - every error is rethrown as ApiHttpErrorResponse carrying the normalized ApiErrorView
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const online = inject(OnlineStatusService);
  const session = inject(SessionService);
  const injector = inject(Injector);

  return next(request).pipe(
    tap((event) => {
      if (event instanceof HttpResponse) {
        online.reportSuccess();
      }
    }),
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const view = toApiErrorView(error);
      if (error.status === 0) {
        online.reportFailure();
      } else {
        online.reportSuccess();
      }
      if (
        error.status === 401 &&
        session.isAuthenticated() &&
        !request.context.get(NO_SESSION_EXPIRED_DIALOG) &&
        !SESSION_ENDPOINT.test(request.url)
      ) {
        injector.get(SessionExpiredService).open();
      }
      if (error.status === 403 && session.isAuthenticated()) {
        injector.get(AuthFacadeService).refreshSession(true);
      }
      return throwError(() => new ApiHttpErrorResponse(error, view));
    }),
  );
};
