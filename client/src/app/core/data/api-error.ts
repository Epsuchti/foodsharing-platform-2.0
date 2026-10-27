import { HttpErrorResponse } from '@angular/common/http';

import { ApiError, ApiErrorCode, FieldError } from '../../api';

/** A normalized error, whatever failed (API error body, network, unexpected client error). */
export interface ApiErrorView {
  /** HTTP status; 0 for network failures, -1 for client-side errors. */
  status: number;
  code: ApiErrorCode | 'NETWORK' | 'UNKNOWN';
  /** English developer message (never shown as-is; the UI translates `errors.<code>`). */
  message: string;
  traceId?: string;
  retryable: boolean;
  retryAfterSeconds?: number;
  fieldErrors: FieldError[];
  /** Interpolation params for `errors.<code>`. */
  args?: Record<string, string>;
}

const API_ERROR_CODES = new Set<string>(Object.values(ApiErrorCode));

export function isApiErrorBody(body: unknown): body is ApiError {
  return (
    !!body &&
    typeof body === 'object' &&
    typeof (body as ApiError).code === 'string' &&
    API_ERROR_CODES.has((body as ApiError).code)
  );
}

export function isApiErrorView(value: unknown): value is ApiErrorView {
  return (
    !!value &&
    typeof value === 'object' &&
    typeof (value as ApiErrorView).status === 'number' &&
    typeof (value as ApiErrorView).code === 'string' &&
    Array.isArray((value as ApiErrorView).fieldErrors)
  );
}

/** HttpErrorResponse enriched by the error interceptor with the normalized view. */
export class ApiHttpErrorResponse extends HttpErrorResponse {
  readonly view: ApiErrorView;

  constructor(source: HttpErrorResponse, view: ApiErrorView) {
    super({
      error: source.error,
      headers: source.headers,
      status: source.status,
      statusText: source.statusText,
      url: source.url ?? undefined,
    });
    this.view = view;
  }
}

function fallbackCode(status: number): ApiErrorView['code'] {
  switch (status) {
    case 0:
      return 'NETWORK';
    case 401:
      return ApiErrorCode.AuthenticationRequired;
    case 403:
      return ApiErrorCode.AccessDenied;
    case 404:
      return ApiErrorCode.ResourceNotFound;
    case 409:
      return ApiErrorCode.ConcurrentModification;
    case 429:
      return ApiErrorCode.TooManyRequests;
    default:
      return status >= 500 ? ApiErrorCode.UnexpectedError : 'UNKNOWN';
  }
}

function retryAfterHeader(error: HttpErrorResponse): number | undefined {
  const value = error.headers?.get('Retry-After');
  const seconds = value ? Number(value) : NaN;
  return Number.isFinite(seconds) ? seconds : undefined;
}

/** Converts anything thrown by an API call (or the app) into an ApiErrorView. */
export function toApiErrorView(error: unknown): ApiErrorView {
  if (error instanceof ApiHttpErrorResponse) {
    return error.view;
  }
  if (isApiErrorView(error)) {
    return error;
  }
  if (error instanceof HttpErrorResponse) {
    const status = error.status;
    const body = parseBody(error.error);
    if (isApiErrorBody(body)) {
      return {
        status,
        code: body.code,
        message: body.message,
        traceId: body.traceId ?? error.headers?.get('X-Trace-Id') ?? undefined,
        retryable: body.retryable,
        retryAfterSeconds: body.retryAfterSeconds ?? retryAfterHeader(error),
        fieldErrors: body.fieldErrors ?? [],
        args: body.args,
      };
    }
    return {
      status,
      code: fallbackCode(status),
      message: error.message,
      traceId: error.headers?.get('X-Trace-Id') ?? undefined,
      retryable: status === 0 || status === 429 || status >= 500,
      retryAfterSeconds: retryAfterHeader(error),
      fieldErrors: [],
    };
  }
  const message = error instanceof Error ? error.message : String(error ?? 'Unknown error');
  return { status: -1, code: 'UNKNOWN', message, retryable: true, fieldErrors: [] };
}

/** Bodies of non-JSON error responses (e.g. responseType text/blob) may contain the JSON as a string. */
function parseBody(body: unknown): unknown {
  if (typeof body === 'string' && body.startsWith('{')) {
    try {
      return JSON.parse(body);
    } catch {
      return body;
    }
  }
  return body;
}

/** Interpolation params for `errors.<code>` (server args + retry-after seconds). */
export function errorParams(view: ApiErrorView): Record<string, unknown> {
  return { ...(view.args ?? {}), seconds: view.retryAfterSeconds ?? 0 };
}
