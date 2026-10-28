import { Injectable, inject } from '@angular/core';

import { PassportsService, VerificationService } from '../../api';
import { HistorySource } from '../../shared/history-timeline/history-timeline.types';

/** History sources of the verification feature (A8, D10.3), wrapped in loadResource by `fs-history-timeline`. */
@Injectable({ providedIn: 'root' })
export class VerificationResources {
  private readonly verification = inject(VerificationService);
  private readonly passports = inject(PassportsService);

  verificationHistory(userId: string): HistorySource {
    return (filter, page) =>
      this.verification.getVerificationHistory({
        userId,
        actions: filter.actions.length ? filter.actions : undefined,
        from: filter.from,
        to: filter.to,
        ...page,
      });
  }

  passportHistory(userId: string): HistorySource {
    return (filter, page) =>
      this.passports.getPassportHistory({
        userId,
        actions: filter.actions.length ? filter.actions : undefined,
        from: filter.from,
        to: filter.to,
        ...page,
      });
  }
}

/** Browser download of a server file by plain navigation (passport PDF, job result); not an app XHR. */
export function downloadUrl(document: Document, url: string, fileName: string): void {
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.rel = 'noopener';
  document.body.appendChild(link);
  link.click();
  link.remove();
}
