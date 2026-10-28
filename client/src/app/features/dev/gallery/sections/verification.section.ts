import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { Role, VerificationCandidate } from '../../../../api';
import { VerificationCandidateCardComponent } from '../../../verification/components/verification-candidate-card.component';

const NEWCOMER: VerificationCandidate = {
  user: { id: '00000001-0001-4000-8000-000000000005', displayName: 'Pia Pending', role: Role.Foodsharer, verified: false },
  phone: '+41 79 123 45 67',
  journey: {
    introPickupsDone: 3,
    introPickupsRequired: 3,
    fairteilerDone: true,
    learningDone: true,
    quizPassedAt: '2026-10-12T08:00:00Z',
    agreementAcceptedAt: '2026-10-12T08:05:00Z',
  },
  guideComments: [
    {
      id: 'c1',
      author: { id: '00000001-0001-4000-8000-000000000010', displayName: 'Gina Guide', role: Role.Foodsaver, verified: true },
      text: 'Sehr zuverlässig, war pünktlich und hat gut mitgeholfen.',
      adminComment: false,
      createdAt: '2026-10-20T17:00:00Z',
    },
  ],
  lastIntroPickupAt: '2026-10-20T16:00:00Z',
  registeredAt: '2026-09-28T10:00:00Z',
};

const VERIFIED: VerificationCandidate = {
  ...NEWCOMER,
  user: { ...NEWCOMER.user, id: '00000001-0001-4000-8000-000000000006', displayName: 'Fabio Foodsaver', foodsaverNumber: 1042, verified: true },
  identityConfirmedAt: '2026-10-28T09:00:00Z',
  guideComments: [],
};

/** Verification queue card (Ve2): open newcomer with a guide comment, verified with identity confirmed. */
@Component({
  selector: 'fs-gallery-verification-section',
  imports: [VerificationCandidateCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-verification">Verification</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.verificationCard">
        <header class="specimen__head"><code>fs-verification-candidate-card</code> ready</header>
        <div class="fs-card demo-card"><fs-verification-candidate-card [candidate]="newcomer" [verified]="false" /></div>
      </section>
      <section class="specimen" data-testid="gallery.verificationCardVerified">
        <header class="specimen__head"><code>fs-verification-candidate-card</code> verified</header>
        <div class="fs-card demo-card"><fs-verification-candidate-card [candidate]="verified" [verified]="true" /></div>
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
  styles: `
    .demo-card {
      display: grid;
      gap: 0.75rem;
      padding: 1rem;
    }
  `,
})
export class VerificationGallerySection {
  protected readonly newcomer = NEWCOMER;
  protected readonly verified = VERIFIED;
}

/** Gallery sections of the verification feature (one file per module, no conflicts). */
export const VERIFICATION_GALLERY_SECTIONS: readonly Type<unknown>[] = [VerificationGallerySection];
