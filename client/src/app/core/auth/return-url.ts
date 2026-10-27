const MAX_LENGTH = 500;
/** Pages that must never be the target after signing in (would loop back into the login flow). */
const LOGIN_PAGES = /^\/login(\/|$)/;

/**
 * Accepts only in-app absolute paths ("/dashboard?tab=1") as target after signing in, so a manipulated link
 * can never forward to another site: must start with a single "/", no "//", no "\", no control characters,
 * not a login page, at most 500 characters. Mirrors the backend ReturnUrlValidator.
 */
export function safeReturnUrl(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const url = value.trim();
  if (
    !url.startsWith('/') ||
    url.includes('//') ||
    url.includes('\\') ||
    url.length > MAX_LENGTH ||
    // eslint-disable-next-line no-control-regex
    /[\u0000-\u001f\u007f]/.test(url)
  ) {
    return null;
  }
  const path = url.split(/[?#]/)[0];
  if (LOGIN_PAGES.test(path)) {
    return null;
  }
  return url;
}
