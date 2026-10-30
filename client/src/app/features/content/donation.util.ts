import { DonationPage } from '../../api';

/** When the donation modal was last shown in this browser (epoch ms). */
export const DONATION_MODAL_SHOWN_KEY = 'fs.donationModalShownAt';

/** Slug of the CMS page shown inside the donation modal (seed `donation-modal`). */
export const DONATION_MODAL_SLUG = 'donation-modal';

/**
 * Is the donation modal due (C7)? Only when the page settings ask for it (`showModal` = DONATION_MODAL toggle and
 * `donation.show-modal`), and at most once per `modalHoursLoggedIn` / `modalHoursLoggedOut` hours in this browser
 * (0 = on every visit). An unreadable stored time counts as never shown.
 */
export function donationModalDue(
  page: Pick<DonationPage, 'showModal' | 'modalHoursLoggedIn' | 'modalHoursLoggedOut'>,
  loggedIn: boolean,
  lastShownAt: string | null,
  now: number,
): boolean {
  if (!page.showModal) {
    return false;
  }
  const hours = Math.max(0, loggedIn ? page.modalHoursLoggedIn : page.modalHoursLoggedOut);
  const last = lastShownAt === null ? NaN : Number(lastShownAt);
  if (!Number.isFinite(last) || last > now) {
    return true;
  }
  return now - last >= hours * 3_600_000;
}

/** The donation iframes of the page, in display order, only https URLs (the CSP `frame-src` allows only those). */
export type DonationFrameKind = 'oneTime' | 'campaign' | 'friendshipCircle' | 'selfService';

export interface DonationFrame {
  kind: DonationFrameKind;
  url: string;
}

export function donationFrames(page: DonationPage): DonationFrame[] {
  const frames: [DonationFrameKind, string | undefined][] = [
    ['oneTime', page.iframeOneTimeUrl],
    ['campaign', page.iframeCampaignUrl],
    ['friendshipCircle', page.iframeFriendshipCircleUrl],
    ['selfService', page.iframeSelfServiceUrl],
  ];
  return frames
    .map(([kind, url]) => ({ kind, url: safeHttpsUrl(url) }))
    .filter((frame): frame is DonationFrame => frame.url !== null);
}

/** Only absolute https URLs; anything else (http, javascript:, relative) is dropped. */
export function safeHttpsUrl(url: string | null | undefined): string | null {
  const value = url?.trim();
  if (!value) {
    return null;
  }
  try {
    return new URL(value).protocol === 'https:' ? value : null;
  } catch {
    return null;
  }
}

/** Nothing to show: no campaign card and no donation form. */
export function donationPageEmpty(page: DonationPage): boolean {
  return (
    !(page.showCampaignCard && page.campaignPages.length > 0) && donationFrames(page).length === 0
  );
}
