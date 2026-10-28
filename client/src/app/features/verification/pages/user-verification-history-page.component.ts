import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import {
  HistorySubjectType,
  IdentityStatus,
  ProfilesService,
  PublicProfile,
  VerificationService,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { VerificationResources } from '../verification.resource';

/**
 * `/users/:userId/verification-history` (Ve2, A8, D10.3): verification state of one user with verify / unverify
 * (required message), identity confirmation, and the verification and passport history timelines.
 */
@Component({
  selector: 'fs-user-verification-history-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    HistoryTimelineComponent,
    PageHeaderComponent,
    RequiredMessageDialogComponent,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'verification.history.title' | t" [subtitle]="'verification.history.subtitle' | t" icon="pi pi-history" />

    <fs-data-state [state]="profile" skeleton="card" [skeletonRows]="1" [compact]="true" data-testid="verification.history.userState">
      <ng-template [fsData]="profile" let-p>
        <section class="fs-card card" data-testid="verification.history.user" [attr.data-verified]="verified(p)">
          <div class="head">
            <fs-user-chip [name]="p.user.displayName" [userId]="p.user.id" [avatarUrl]="p.user.avatarUrl" [foodsaverNumber]="p.user.foodsaverNumber" [verified]="verified(p)" size="md" />
            <span class="tags">
              @if (verified(p)) {
                <span class="tag tag--ok" data-testid="verification.history.verifiedTag"><i class="pi pi-verified" aria-hidden="true"></i>{{ 'verification.queue.verified' | t }}</span>
              } @else {
                <span class="tag tag--warn" data-testid="verification.history.unverifiedTag">{{ 'verification.history.notVerified' | t }}</span>
              }
            </span>
          </div>
          @if (p.user.id !== viewerId()) {
            <div class="actions">
              @if (verified(p)) {
                <p-button [label]="'verification.queue.unverify' | t" icon="pi pi-undo" severity="danger" [outlined]="true" size="small" (onClick)="unverifyOpen.set(true)" data-testid="verification.history.unverify" />
              } @else {
                <p-button [label]="'verification.queue.verify' | t" icon="pi pi-verified" size="small" (onClick)="verifyOpen.set(true)" data-testid="verification.history.verify" />
              }
            </div>
          }
          <fs-required-message-dialog
            [(visible)]="verifyOpen"
            [title]="'verification.verifyDialog.title' | t"
            [text]="'verification.verifyDialog.text' | t: { name: p.user.displayName }"
            [label]="'verification.verifyDialog.label' | t"
            [confirmLabel]="'verification.queue.verify' | t"
            [busy]="verify.busy()"
            (confirm)="verify.run(p.user.id, $event)"
            testId="verification.verifyDialog"
          />
          <fs-required-message-dialog
            [(visible)]="unverifyOpen"
            [title]="'verification.unverifyDialog.title' | t"
            [text]="'verification.unverifyDialog.text' | t: { name: p.user.displayName }"
            [label]="'verification.unverifyDialog.label' | t"
            [confirmLabel]="'verification.queue.unverify' | t"
            severity="danger"
            [busy]="unverify.busy()"
            (confirm)="unverify.run(p.user.id, $event)"
            testId="verification.unverifyDialog"
          />
        </section>
      </ng-template>
    </fs-data-state>

    <fs-data-state [state]="identity" skeleton="card" [skeletonRows]="1" [compact]="true" data-testid="verification.history.identityState">
      <ng-template [fsData]="identity" let-id>
        <section class="fs-card card" data-testid="verification.history.identity" [attr.data-confirmed]="id.confirmed">
          <h2 class="card__title"><i class="pi pi-id-card" aria-hidden="true"></i>{{ 'verification.history.identity' | t }}</h2>
          @if (id.confirmed) {
            <p class="muted">
              {{ 'verification.history.identityConfirmedBy' | t: { name: id.confirmedBy?.displayName ?? '–', date: (id.confirmedAt | fsDate: 'date') } }}
            </p>
          } @else {
            <p class="muted">{{ 'verification.history.identityOpen' | t }}</p>
            @if (userId() !== viewerId()) {
              <div class="actions">
                <p-button [label]="'verification.queue.confirmIdentity' | t" icon="pi pi-id-card" size="small" [outlined]="true" [loading]="confirmIdentity.busy()" (onClick)="confirmIdentity.run()" data-testid="verification.history.confirmIdentity" />
              </div>
            }
          }
        </section>
      </ng-template>
    </fs-data-state>

    <h2 class="section-title"><i class="pi pi-verified" aria-hidden="true"></i>{{ 'verification.history.verificationTitle' | t }}</h2>
    @if (verificationSource(); as source) {
      <fs-history-timeline [source]="source" [subjectType]="subjectType" [viewerId]="viewerId()" [emptyTitle]="'verification.history.empty' | t" testId="verification.history.verification" />
    }
    <h2 class="section-title"><i class="pi pi-id-card" aria-hidden="true"></i>{{ 'verification.history.passportTitle' | t }}</h2>
    @if (passportSource(); as source) {
      <fs-history-timeline [source]="source" [subjectType]="subjectType" [viewerId]="viewerId()" [emptyTitle]="'verification.history.passportEmpty' | t" testId="verification.history.passport" />
    }
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
  `,
})
export class UserVerificationHistoryPageComponent {
  private readonly api = inject(VerificationService);
  private readonly profiles = inject(ProfilesService);
  private readonly resources = inject(VerificationResources);
  private readonly session = inject(SessionService);

  readonly userId = input.required<string>();
  protected readonly viewerId = computed(() => this.session.user()?.id);
  protected readonly subjectType = HistorySubjectType.User;
  protected readonly verifyOpen = signal(false);
  protected readonly unverifyOpen = signal(false);
  private readonly verifiedOverride = signal<boolean | undefined>(undefined);
  /** Bumped after a change so the timelines load again. */
  private readonly revision = signal(0);

  protected readonly profile = loadResource<PublicProfile, string>({
    params: () => this.userId(),
    loader: (userId) => this.profiles.getPublicProfile({ userId }),
  });

  protected readonly identity = loadResource<IdentityStatus, string>({
    params: () => this.userId(),
    loader: (userId) => this.api.getIdentityStatus({ userId }),
  });

  protected readonly verificationSource = computed(() => {
    this.revision();
    return this.resources.verificationHistory(this.userId());
  });

  protected readonly passportSource = computed(() => this.resources.passportHistory(this.userId()));

  protected readonly verify = runMutation(
    (userId: string, message: string) => this.api.verifyUser({ userId, requiredMessage: { message } }),
    { successToastKey: 'verification.queue.verifiedToast', onSuccess: () => this.changed(true) },
  );

  protected readonly unverify = runMutation(
    (userId: string, message: string) => this.api.unverifyUser({ userId, requiredMessage: { message } }),
    { successToastKey: 'verification.queue.unverifiedToast', onSuccess: () => this.changed(false) },
  );

  protected readonly confirmIdentity = runMutation(() => this.api.confirmIdentity({ userId: this.userId() }), {
    successToastKey: 'verification.queue.identityToast',
    onSuccess: () => {
      this.identity.reload();
      this.revision.update((r) => r + 1);
    },
  });

  protected verified(profile: PublicProfile): boolean {
    return this.verifiedOverride() ?? profile.user.verified;
  }

  private changed(verified: boolean): void {
    this.verifyOpen.set(false);
    this.unverifyOpen.set(false);
    this.verifiedOverride.set(verified);
    this.revision.update((r) => r + 1);
  }
}
