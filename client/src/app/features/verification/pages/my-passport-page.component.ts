import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { HistorySubjectType, PassportStatus, PassportsService, WalletAvailability } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { VerificationResources, downloadUrl } from '../verification.resource';

/** Where the passport PDF and the Apple pass are served (plain browser downloads, no XHR). */
export const MY_PASSPORT_PDF_URL = '/api/users/me/passport.pdf';
export const MY_APPLE_PASS_URL = '/api/users/me/wallets/apple';

/**
 * `/me/passport` (Ve3, A8): own passport status and validity, PDF download, passport request to the ambassadors,
 * Apple / Google Wallet buttons (with a clear "not available yet" state while the server has no signing keys) and
 * the own passport history. Unverified users see a locked state that points to the journey.
 */
@Component({
  selector: 'fs-my-passport-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, HistoryTimelineComponent, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'verification.passport.title' | t" [subtitle]="'verification.passport.subtitle' | t" icon="pi pi-id-card" />

    <fs-data-state [state]="status" skeleton="card" [skeletonRows]="2" data-testid="verification.passport.state">
      <ng-template [fsData]="status" let-s>
        @if (!s.verified) {
          <section class="fs-card card locked" data-testid="verification.passport.locked">
            <h2 class="card__title"><i class="pi pi-lock" aria-hidden="true"></i>{{ 'verification.passport.lockedTitle' | t }}</h2>
            <p class="muted">{{ 'verification.passport.lockedText' | t }}</p>
            <div class="actions">
              <a routerLink="/onboarding" class="fs-btn fs-btn--sm" data-testid="verification.passport.toJourney">
                <i class="pi pi-flag" aria-hidden="true"></i>{{ 'verification.passport.toJourney' | t }}
              </a>
            </div>
          </section>
        } @else {
          <div class="grid">
            <section class="fs-card card" data-testid="verification.passport.status" [attr.data-valid]="s.valid" [attr.data-pending]="s.requestPending">
              <h2 class="card__title"><i class="pi pi-id-card" aria-hidden="true"></i>{{ 'verification.passport.statusTitle' | t }}</h2>
              <span class="tags">
                @if (s.valid) {
                  <span class="tag tag--ok" data-testid="verification.passport.validTag"><i class="pi pi-check" aria-hidden="true"></i>{{ 'verification.passport.valid' | t }}</span>
                } @else {
                  <span class="tag tag--warn" data-testid="verification.passport.invalidTag">{{ (s.validUntil ? 'verification.passport.expired' : 'verification.passport.none') | t }}</span>
                }
                @if (s.requestPending) {
                  <span class="tag" data-testid="verification.passport.pendingTag"><i class="pi pi-clock" aria-hidden="true"></i>{{ 'verification.passport.requestPending' | t }}</span>
                }
              </span>
              <dl class="facts">
                @if (s.validFrom) {
                  <dt>{{ 'verification.passport.validFrom' | t }}</dt>
                  <dd data-testid="verification.passport.validFrom">{{ s.validFrom | fsDate: 'date' }}</dd>
                }
                @if (s.validUntil) {
                  <dt>{{ 'verification.passport.validUntil' | t }}</dt>
                  <dd data-testid="verification.passport.validUntil">{{ s.validUntil | fsDate: 'date' }}</dd>
                }
                @if (s.lastIssuedAt) {
                  <dt>{{ 'verification.passport.lastIssued' | t }}</dt>
                  <dd>{{ s.lastIssuedAt | fsDate: 'date' }}</dd>
                }
              </dl>
              <div class="actions">
                @if (s.valid) {
                  <p-button [label]="'verification.passport.download' | t" icon="pi pi-download" (onClick)="download()" data-testid="verification.passport.download" />
                } @else {
                  <p class="muted" data-testid="verification.passport.renewHint">{{ 'verification.passport.renewHint' | t }}</p>
                }
                @if (!s.requestPending) {
                  <p-button
                    [label]="'verification.passport.request' | t"
                    icon="pi pi-send"
                    severity="secondary"
                    [outlined]="true"
                    [loading]="request.busy()"
                    (onClick)="request.run()"
                    data-testid="verification.passport.request"
                  />
                }
              </div>
            </section>

            <section class="fs-card card" data-testid="verification.passport.wallets">
              <h2 class="card__title"><i class="pi pi-wallet" aria-hidden="true"></i>{{ 'verification.passport.walletsTitle' | t }}</h2>
              @if (walletsUnavailable(s)) {
                <p class="muted" data-testid="verification.passport.walletsNotConfigured">{{ 'verification.passport.walletsNotConfigured' | t }}</p>
              } @else {
                <p class="muted">{{ 'verification.passport.walletsText' | t }}</p>
              }
              <div class="actions">
                <p-button
                  [label]="'verification.passport.appleWallet' | t"
                  icon="pi pi-apple"
                  severity="contrast"
                  [disabled]="s.walletApple !== available || !s.valid"
                  (onClick)="appleWallet()"
                  data-testid="verification.passport.apple"
                  [attr.data-availability]="s.walletApple"
                />
                <p-button
                  [label]="'verification.passport.googleWallet' | t"
                  icon="pi pi-google"
                  severity="contrast"
                  [disabled]="s.walletGoogle !== available || !s.valid"
                  [loading]="googleWallet.busy()"
                  (onClick)="googleWallet.run()"
                  data-testid="verification.passport.google"
                  [attr.data-availability]="s.walletGoogle"
                />
              </div>
            </section>
          </div>
        }
      </ng-template>
    </fs-data-state>

    @if (historySource(); as source) {
      <h2 class="section-title"><i class="pi pi-history" aria-hidden="true"></i>{{ 'verification.history.passportTitle' | t }}</h2>
      <fs-history-timeline [source]="source" [subjectType]="subjectType" [viewerId]="userId()" [emptyTitle]="'verification.history.passportEmpty' | t" testId="verification.passport.history" />
    }
  `,
  styleUrl: '../verification.scss',
})
export class MyPassportPageComponent {
  private readonly api = inject(PassportsService);
  private readonly resources = inject(VerificationResources);
  private readonly document = inject(DOCUMENT);
  private readonly session = inject(SessionService);

  protected readonly subjectType = HistorySubjectType.User;
  protected readonly available = WalletAvailability.Available;
  protected readonly userId = computed(() => this.session.user()?.id);
  protected readonly historySource = computed(() => {
    const id = this.userId();
    return id ? this.resources.passportHistory(id) : null;
  });

  protected readonly status = loadResource<PassportStatus>({
    loader: () => this.api.getMyPassportStatus(),
    isEmpty: () => false,
  });

  protected readonly request = runMutation(() => this.api.requestPassport(), {
    successToastKey: 'verification.passport.requested',
    onSuccess: () => this.status.reload(),
  });

  protected readonly googleWallet = runMutation(() => this.api.getGoogleWalletLink(), {
    onSuccess: (link) => this.document.defaultView?.open(link.saveUrl, '_blank', 'noopener'),
  });

  protected walletsUnavailable(status: PassportStatus): boolean {
    return status.walletApple === WalletAvailability.NotConfigured && status.walletGoogle === WalletAvailability.NotConfigured;
  }

  protected download(): void {
    downloadUrl(this.document, MY_PASSPORT_PDF_URL, 'foodsharing-passport.pdf');
  }

  protected appleWallet(): void {
    downloadUrl(this.document, MY_APPLE_PASS_URL, 'foodsharing.pkpass');
  }
}
