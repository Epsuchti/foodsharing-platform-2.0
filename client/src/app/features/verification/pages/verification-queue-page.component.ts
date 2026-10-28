import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { VerificationCandidateCardComponent } from '../components/verification-candidate-card.component';

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
    FormsModule,
    InputText,
    PageHeaderComponent,
    RequiredMessageDialogComponent,
    RouterLink,
    SelectButton,
    TPipe,
    VerificationCandidateCardComponent,
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
              <fs-verification-candidate-card
                [candidate]="c"
                [verified]="isVerified(c)"
                [canConfirmIdentity]="c.user.id !== viewerId()"
                [confirmBusy]="confirmIdentity.busy()"
                (verify)="openVerify(c)"
                (unverify)="openUnverify(c)"
                (confirmIdentity)="confirmIdentity.run(c.user.id)"
              />
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

  protected openVerify(candidate: VerificationCandidate): void {
    this.target.set(candidate);
    this.verifyOpen.set(true);
  }

  protected openUnverify(candidate: VerificationCandidate): void {
    this.target.set(candidate);
    this.unverifyOpen.set(true);
  }
}
