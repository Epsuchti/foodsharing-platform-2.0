/** Limits of `SupportTicketCreate` (support.yaml). */
export const SUPPORT_LIMITS = {
  subject: 200,
  body: 32600,
  page: 500,
  userAgent: 500,
  attachments: 5,
} as const;

/**
 * The page a support request is about (C9): the `?from=` path when it is an app path, else the referrer when it is
 * on this site; never another site's address. Cut to the contract's 500 characters.
 */
export function supportSourcePage(
  from: string | null | undefined,
  referrer: string | null | undefined,
  origin: string,
): string {
  const candidate = from?.trim();
  if (candidate && candidate.startsWith('/') && !candidate.startsWith('//')) {
    return candidate.slice(0, SUPPORT_LIMITS.page);
  }
  if (referrer) {
    try {
      const url = new URL(referrer);
      if (url.origin === origin && !url.pathname.startsWith('/support')) {
        return `${url.pathname}${url.search}`.slice(0, SUPPORT_LIMITS.page);
      }
    } catch {
      // not a URL: ignored
    }
  }
  return '';
}

/** Loose email check, same as the server (`[^@\s]+@[^@\s]+`). */
export function plausibleEmail(value: string): boolean {
  return /^[^@\s]+@[^@\s]+$/.test(value.trim());
}
