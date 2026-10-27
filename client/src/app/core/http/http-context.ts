import { HttpContextToken } from '@angular/common/http';

/** Disables the automatic GET retry for one request (connection probes, polling). */
export const NO_RETRY = new HttpContextToken<boolean>(() => false);

/** Hides one request from the global request bar (background polling). */
export const NO_LOADING_BAR = new HttpContextToken<boolean>(() => false);

/** Skips the global 401 handling (session-expired dialog) for one request. */
export const NO_SESSION_EXPIRED_DIALOG = new HttpContextToken<boolean>(() => false);
