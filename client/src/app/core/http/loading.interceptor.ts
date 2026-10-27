import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { defer, finalize } from 'rxjs';

import { HttpActivityService } from './http-activity.service';
import { NO_LOADING_BAR } from './http-context';

/** Counts requests in flight for the global request bar; released on success, error and unsubscribe. */
export const loadingInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.context.get(NO_LOADING_BAR)) {
    return next(request);
  }
  const activity = inject(HttpActivityService);
  return defer(() => {
    activity.start();
    return next(request).pipe(finalize(() => activity.end()));
  });
};
