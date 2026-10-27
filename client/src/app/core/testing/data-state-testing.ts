import {
  HttpErrorResponse,
  HttpHeaders,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import {
  EnvironmentProviders,
  Injectable,
  Provider,
  computed,
  inject,
  provideEnvironmentInitializer,
  provideZonelessChangeDetection,
  signal,
} from '@angular/core';
import { provideRouter, Routes } from '@angular/router';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Observable, Subject } from 'rxjs';

import { ApiError, ApiErrorCode } from '../../api';
import { provideApi } from '../../api/provide-api';
import { errorInterceptor } from '../http/error.interceptor';
import { I18nService } from '../i18n/i18n.service';
import { OnlineStatusService } from '../online/online-status.service';

/** Online state that tests switch by hand (`setOnline(false)`). */
@Injectable()
export class TestingOnlineStatusService {
  private readonly state = signal(true);
  readonly online = computed(() => this.state());
  readonly serverUnreachable = signal(false).asReadonly();
  failures = 0;

  setOnline(online: boolean): void {
    this.state.set(online);
  }

  reportFailure(): void {
    this.failures++;
    this.state.set(false);
  }

  reportSuccess(): void {
    this.state.set(true);
  }

  probe(): void {
    // no network in tests
  }
}

export interface DataStateTestingOptions {
  /** Translations used by I18nService; by default `t(key)` returns the key itself. */
  translations?: Record<string, string>;
  routes?: Routes;
}

/**
 * Providers for component specs of data-loading components: zoneless, router, HttpClient with the error
 * interceptor + HttpTestingController, Optimus services, a hand-switchable OnlineStatusService and in-memory
 * translations. Inject `OnlineStatusService` and cast to TestingOnlineStatusService to switch offline.
 */
export function provideDataStateTesting(
  options: DataStateTestingOptions = {},
): (Provider | EnvironmentProviders)[] {
  return [
    provideZonelessChangeDetection(),
    provideRouter(options.routes ?? []),
    provideHttpClient(withInterceptors([errorInterceptor])),
    provideHttpClientTesting(),
    provideApi(''),
    MessageService,
    ConfirmationService,
    TestingOnlineStatusService,
    { provide: OnlineStatusService, useExisting: TestingOnlineStatusService },
    provideEnvironmentInitializer(() =>
      inject(I18nService).useCatalog(options.translations ?? {}, 'de'),
    ),
  ];
}

/** An HttpErrorResponse with an ApiError body, as the backend sends it. */
export function apiHttpError(
  status: number,
  code: ApiErrorCode,
  extra: Partial<ApiError> = {},
): HttpErrorResponse {
  const body: ApiError = {
    code,
    message: `${code} (test)`,
    traceId: 'abc123def456',
    retryable: status >= 500 || status === 429,
    fieldErrors: [],
    ...extra,
  };
  return new HttpErrorResponse({
    status,
    statusText: 'Error',
    error: body,
    headers: new HttpHeaders({ 'X-Trace-Id': body.traceId }),
    url: '/api/test',
  });
}

/** A failure without HTTP response (offline / server down). */
export function networkError(): HttpErrorResponse {
  return new HttpErrorResponse({ status: 0, statusText: 'Unknown Error', url: '/api/test' });
}

/** A loader whose results the test pushes by hand: `const l = controlledLoader<T>(); loadResource({ loader: l.loader })`. */
export function controlledLoader<T>() {
  let subject = new Subject<T>();
  let calls = 0;
  return {
    get calls() {
      return calls;
    },
    loader: (): Observable<T> => {
      calls++;
      subject = new Subject<T>();
      return subject.asObservable();
    },
    resolve(value: T) {
      subject.next(value);
      subject.complete();
    },
    reject(error: unknown) {
      subject.error(error);
    },
  };
}
