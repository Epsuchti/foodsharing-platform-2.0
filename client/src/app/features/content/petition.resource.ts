import { Observable, catchError, of, throwError } from 'rxjs';

import { ApiErrorCode, Petition, PublicContentService } from '../../api';
import { toApiErrorView } from '../../core/data/api-error';

export const PETITION_DISMISSED_KEY = 'fs.petitionDismissed';

/**
 * The running petition (C8), or null while none runs (503 PETITION_INACTIVE). A switched-off PETITION_BANNER
 * toggle stays an error (404 FEATURE_DISABLED → not found).
 */
export function loadPetition(api: PublicContentService): Observable<Petition | null> {
  return api
    .getPetition()
    .pipe(
      catchError((error: unknown) =>
        toApiErrorView(error).code === ApiErrorCode.PetitionInactive
          ? of(null)
          : throwError(() => error),
      ),
    );
}

/**
 * The shell's petition banner stays off the blocking consent page (`/consent`, design review M7 R1 #20: it competed
 * with "Zustimmen und weiter") and off the petition page, which shows the petition itself.
 */
export function petitionBannerAllowedOn(url: string): boolean {
  return !/^\/(?:consent|petition)(?:[/?#]|$)/.test(url);
}

/** Only http(s) links leave the page; anything else (javascript:, data:) is not rendered as a link. */
export function safePetitionHref(href: string | null | undefined): string | null {
  return href && /^https?:\/\//i.test(href.trim()) ? href.trim() : null;
}
