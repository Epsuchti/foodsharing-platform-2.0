import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { SelectButton } from '@openng/optimus-ui/selectbutton';

import {
  VerificationCandidate,
  VerificationCandidatePage,
  VerificationQueueFilter,
  VerificationService,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

/**
 * `/regions/:regionId/verification` (Ve2): newcomers of the region waiting for verification. Filter READY (EinAbs
 * complete) or all unverified, search; one card per candidate with journey summary (EinAbs x/N, Fairteiler, learning,
 * quiz, agreement), expandable guide comments, identity confirmation and "Verify" with a required message. A verified
 * card offers "Unverify" (undo, also with a message) and the verification history.
 */
@Component({
  selector: 'fs-verification-queue-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    FormsModule,
    InputText,
    PageHeaderComponent,
    RequiredMessageDialogComponent,
    RouterLink,
    SelectButton,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'verification.queue.title' | t"
      [subtitle]="'verification.queue.subtitle' | t"
      icon="pi pi-verified"
      [backLink]="['/regions', regionId()]"
      [backLabel]="'verification.backToRegion' | t"
    >
      <a [routerLink]="['/regions', regionId(), 'passports']" class="fs-btn fs-btn--outlined fs-btn--sm" data-testid="verification.queue.passports">
        <i class="pi pi-id-card" aria-hidden="true"></i>{{ 'verification.queue.passportsLink' | t }}
      </a>
    </fs-page-header>

    <div class="filters">
      <div class="field">
        <span id="vq-filter">{{ 'verification.queue.filterLabel' | t }}</span>
        <p-selectbutton
          [options]="filters()"
          optionLabel="label"
          optionValue="value"
          [allowEmpty]="false"
          [ngModel]="filter()"
          (ngModelChange)="filter.set($event)"
          ariaLabelledBy="vq-filter"
          data-testid="verification.queue.filter"
        />
      </div>
      <div class="field">
        <label for="vq-search">{{ 'verification.queue.search' | t }}</label>
        <input pInputText id="vq-search" type="search" [ngModel]="search()" (ngModelChange)="search.set($event)" data-testid="verification.queue.search" />
      </div>
    </div>

    <fs-data-state
      [state]="queue"
      skeleton="card"
      [skeletonRows]="3"
      emptyIcon="pi pi-check-circle"
      [emptyTitle]="'verification.queue.empty' | t"
      [emptyText]="'verification.queue.emptyText' | t"
      data-testid="verification.queue.state"
    >
      <ng-template [fsData]="queue" let-page>
        <ul class="grid">
          @for (c of page.items; track c.user.id) {
            <li class="fs-card card" data-testid="verification.queue.card" [attr.data-user]="c.user.id" [attr.data-verified]="isVerified(c)">
              <div class="head">
                <fs-user-chip [name]="c.user.displayName" [userId]="c.user.id" [avatarUrl]="c.user.avatarUrl" [foodsaverNumber]="c.user.foodsaverNumber" [verified]="isVerified(c)" size="md" />
                <span class="tags">
                  @if (isVerified(c)) {
                    <span class="tag tag--ok" data-testid="verification.queue.verifiedTag"><i class="pi pi-verified" aria-hidden="true"></i>{{ 'verification.queue.verified' | t }}</span>
                  }
                  @if (identityConfirmed(c)) {
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

              <div class="actions">
                @if (!isVerified(c)) {
                  <p-button [label]="'verification.queue.verify' | t" icon="pi pi-verified" size="small" (onClick)="openVerify(c)" data-testid="verification.queue.verify" />
                } @else {
                  <p-button [label]="'verification.queue.unverify' | t" icon="pi pi-undo" severity="danger" [outlined]="true" size="small" (onClick)="openUnverify(c)" data-testid="verification.queue.unverify" />
                }
                @if (!identityConfirmed(c) && c.user.id !== viewerId()) {
                  <p-button
                    [label]="'verification.queue.confirmIdentity' | t"
                    icon="pi pi-id-card"
                    severity="secondary"
                    [outlined]="true"
                    size="small"
                    [loading]="confirmIdentity.busy()"
                    (onClick)="confirmIdentity.run(c.user.id)"
                    data-testid="verification.queue.confirmIdentity"
                  />
                }
                <a [routerLink]="['/users', c.user.id, 'verification-history']" class="fs-btn fs-btn--text fs-btn--sm" data-testid="verification.queue.history">
                  <i class="pi pi-history" aria-hidden="true"></i>{{ 'verification.queue.history' | t }}
                </a>
              </div>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>

    <fs-required-message-dialog
      [(visible)]="verifyOpen"
      [title]="'verification.verifyDialog.title' | t"
      [text]="'verification.verifyDialog.text' | t: { name: target()?.user?.displayName ?? '' }"
      [label]="'verification.verifyDialog.label' | t"
      [confirmLabel]="'verification.queue.verify' | t"
      [busy]="verify.busy()"
      (confirm)="verify.run(target()!.user.id, $event)"
      testId="verification.verifyDialog"
    />
    <fs-required-message-dialog
      [(visible)]="unverifyOpen"
      [title]="'verification.unverifyDialog.title' | t"
      [text]="'verification.unverifyDialog.text' | t: { name: target()?.user?.displayName ?? '' }"
      [label]="'verification.unverifyDialog.label' | t"
      [confirmLabel]="'verification.queue.unverify' | t"
      severity="danger"
      [busy]="unverify.busy()"
      (confirm)="unverify.run(target()!.user.id, $event)"
      testId="verification.unverifyDialog"
    />
  `,
  styleUrl: '../verification.scss',
  styles: `
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
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
export class VerificationQueuePageComponent {
  private readonly api = inject(VerificationService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);

  readonly regionId = input.required<string>();
  protected readonly viewerId = computed(() => this.session.user()?.id);
  protected readonly filter = signal<VerificationQueueFilter>(VerificationQueueFilter.Ready);
  protected readonly search = signal('');
  protected readonly target = signal<VerificationCandidate | undefined>(undefined);
  protected readonly verifyOpen = signal(false);
  protected readonly unverifyOpen = signal(false);
  /** Verification changes made on this page (the card stays visible with its new state until reload). */
  private readonly verifiedOverride = signal<ReadonlyMap<string, boolean>>(new Map());

  protected readonly filters = computed(() => {
    this.i18n.language();
    return Object.values(VerificationQueueFilter).map((value) => ({
      value,
      label: this.i18n.t(`verification.queue.filter.${value}`),
    }));
  });

  protected readonly queue = loadResource<
    VerificationCandidatePage,
    { regionId: string; filter: VerificationQueueFilter; search: string }
  >({
    params: () => ({ regionId: this.regionId(), filter: this.filter(), search: this.search().trim() }),
    loader: ({ regionId, filter, search }) =>
      this.api.listVerificationQueue({ regionId, filter, search: search || undefined, size: 50 }),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly verify = runMutation(
    (userId: string, message: string) => this.api.verifyUser({ userId, requiredMessage: { message } }),
    {
      successToastKey: 'verification.queue.verifiedToast',
      onSuccess: (user) => {
        this.verifyOpen.set(false);
        this.verifiedOverride.update((map) => new Map(map).set(user.id, true));
      },
    },
  );

  protected readonly unverify = runMutation(
    (userId: string, message: string) => this.api.unverifyUser({ userId, requiredMessage: { message } }),
    {
      successToastKey: 'verification.queue.unverifiedToast',
      onSuccess: (user) => {
        this.unverifyOpen.set(false);
        this.verifiedOverride.update((map) => new Map(map).set(user.id, false));
      },
    },
  );

  protected readonly confirmIdentity = runMutation((userId: string) => this.api.confirmIdentity({ userId }), {
    successToastKey: 'verification.queue.identityToast',
    onSuccess: () => this.queue.reload(),
  });

  protected isVerified(candidate: VerificationCandidate): boolean {
    return this.verifiedOverride().get(candidate.user.id) ?? candidate.user.verified;
  }

  protected identityConfirmed(candidate: VerificationCandidate): boolean {
    return !!candidate.identityConfirmedAt;
  }

  protected openVerify(candidate: VerificationCandidate): void {
    this.target.set(candidate);
    this.verifyOpen.set(true);
  }

  protected openUnverify(candidate: VerificationCandidate): void {
    this.target.set(candidate);
    this.unverifyOpen.set(true);
  }
}
