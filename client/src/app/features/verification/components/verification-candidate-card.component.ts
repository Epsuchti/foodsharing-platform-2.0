import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { VerificationCandidate } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

/**
 * Content of one verification queue card (Ve2): user, verified/identity tags, journey summary, guide comments and the
 * actions. The page owns the dialogs and mutations; this component only emits.
 */
@Component({
  selector: 'fs-verification-candidate-card',
  imports: [Button, DateTimePipe, RouterLink, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let c = candidate();
    <div class="head">
      <fs-user-chip [name]="c.user.displayName" [userId]="c.user.id" [avatarUrl]="c.user.avatarUrl" [foodsaverNumber]="c.user.foodsaverNumber" [verified]="verified()" size="md" />
      <span class="tags">
        @if (verified()) {
          <span class="tag tag--ok" data-testid="verification.queue.verifiedTag"><i class="pi pi-verified" aria-hidden="true"></i>{{ 'verification.queue.verified' | t }}</span>
        }
        @if (c.identityConfirmedAt) {
          <span class="tag tag--ok" data-testid="verification.queue.identityTag"><i class="pi pi-id-card" aria-hidden="true"></i>{{ 'verification.queue.identityConfirmed' | t }}</span>
        } @else {
          <span class="tag tag--warn" data-testid="verification.queue.identityOpen">{{ 'verification.queue.identityOpen' | t }}</span>
        }
      </span>
    </div>

    <dl class="facts" data-testid="verification.queue.journey">
      <dt>{{ 'verification.queue.introPickups' | t }}</dt>
      <dd data-testid="verification.queue.introPickups">{{ c.journey.introPickupsDone }} / {{ c.journey.introPickupsRequired }}</dd>
      <dt>{{ 'verification.queue.fairteiler' | t }}</dt>
      <dd>{{ (c.journey.fairteilerDone ? 'verification.yes' : 'verification.no') | t }}</dd>
      <dt>{{ 'verification.queue.learning' | t }}</dt>
      <dd>{{ (c.journey.learningDone ? 'verification.yes' : 'verification.no') | t }}</dd>
      <dt>{{ 'verification.queue.quiz' | t }}</dt>
      <dd>{{ c.journey.quizPassedAt ? (c.journey.quizPassedAt | fsDate: 'date') : ('verification.queue.notYet' | t) }}</dd>
      <dt>{{ 'verification.queue.agreement' | t }}</dt>
      <dd>{{ c.journey.agreementAcceptedAt ? (c.journey.agreementAcceptedAt | fsDate: 'date') : ('verification.queue.notYet' | t) }}</dd>
      @if (c.lastIntroPickupAt) {
        <dt>{{ 'verification.queue.lastIntroPickup' | t }}</dt>
        <dd>{{ c.lastIntroPickupAt | fsDate: 'date' }}</dd>
      }
      @if (c.phone) {
        <dt>{{ 'verification.queue.phone' | t }}</dt>
        <dd><a [href]="'tel:' + c.phone">{{ c.phone }}</a></dd>
      }
      @if (c.registeredAt) {
        <dt>{{ 'verification.queue.registered' | t }}</dt>
        <dd>{{ c.registeredAt | fsDate: 'date' }}</dd>
      }
    </dl>

    @if (c.guideComments.length) {
      <details class="comments" data-testid="verification.queue.comments">
        <summary>{{ 'verification.queue.comments' | t: { count: c.guideComments.length } }}</summary>
        <ul>
          @for (comment of c.guideComments; track comment.id) {
            <li data-testid="verification.queue.comment">
              <strong>{{ comment.author.displayName }}</strong>
              <span class="muted">{{ comment.createdAt | fsDate: 'date' }}</span>
              <p>{{ comment.text }}</p>
            </li>
          }
        </ul>
      </details>
    } @else {
      <p class="muted">{{ 'verification.queue.noComments' | t }}</p>
    }

    @let identityFirst = !verified() && !c.identityConfirmedAt && canConfirmIdentity();
    @if (identityFirst) {
      <!-- the expected order (design review M7 R1 #18): check the ID first, then verify; verifying stays possible -->
      <p class="identity-hint" data-testid="verification.queue.identityFirst">
        <i class="pi pi-info-circle" aria-hidden="true"></i>{{ 'verification.queue.identityFirstHint' | t }}
      </p>
    }
    <div class="actions">
      @if (!c.identityConfirmedAt && canConfirmIdentity()) {
        <p-button
          [label]="'verification.queue.confirmIdentity' | t"
          icon="pi pi-id-card"
          [severity]="identityFirst ? undefined : 'secondary'"
          [outlined]="!identityFirst"
          size="small"
          [loading]="confirmBusy()"
          (onClick)="confirmIdentity.emit()"
          data-testid="verification.queue.confirmIdentity"
        />
      }
      @if (!verified()) {
        <p-button
          [label]="'verification.queue.verify' | t"
          icon="pi pi-verified"
          [severity]="identityFirst ? 'secondary' : undefined"
          [outlined]="identityFirst"
          size="small"
          (onClick)="verify.emit()"
          data-testid="verification.queue.verify"
        />
      } @else {
        <p-button [label]="'verification.queue.unverify' | t" icon="pi pi-undo" severity="danger" [outlined]="true" size="small" (onClick)="unverify.emit()" data-testid="verification.queue.unverify" />
      }
      <a [routerLink]="['/users', c.user.id, 'verification-history']" class="fs-btn fs-btn--text fs-btn--sm" data-testid="verification.queue.history">
        <i class="pi pi-history" aria-hidden="true"></i>{{ 'verification.queue.history' | t }}
      </a>
    </div>
  `,
  styleUrl: '../verification.scss',
  styles: `
    :host {
      display: contents;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .identity-hint {
      display: flex;
      align-items: flex-start;
      gap: 0.45rem;
      margin: 0;
      padding: 0.5rem 0.75rem;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
      font-size: 0.88rem;
      font-weight: 600;
    }
    .identity-hint .pi {
      margin-top: 0.2rem;
    }
    .comments summary {
      cursor: pointer;
      font-weight: 600;
    }
    .comments ul {
      display: grid;
      gap: 0.5rem;
      margin: 0.5rem 0 0;
      padding: 0;
      list-style: none;
    }
    .comments li {
      padding: 0.5rem 0.75rem;
      border-radius: 0.5rem;
      background: var(--fs-surface-muted);
    }
    .comments p {
      margin: 0.25rem 0 0;
      white-space: pre-line;
    }
  `,
})
export class VerificationCandidateCardComponent {
  readonly candidate = input.required<VerificationCandidate>();
  /** Verified state shown (the page overrides it after verify/unverify until reload). */
  readonly verified = input.required<boolean>();
  /** False for the viewer's own card. */
  readonly canConfirmIdentity = input(true);
  readonly confirmBusy = input(false);
  readonly verify = output<void>();
  readonly unverify = output<void>();
  readonly confirmIdentity = output<void>();
}
