import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { filter } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentPage, DonationPage, FeatureToggleKey, Language, PublicContentService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { FeatureToggleService } from '../../../core/feature-toggles/feature-toggle.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { readStorage, writeStorage } from '../../../core/storage';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { DONATION_MODAL_SHOWN_KEY, DONATION_MODAL_SLUG, donationModalDue } from '../donation.util';

/**
 * Donation modal of the shell (C7). Loads the donation settings only while DONATION_MODAL is on; opens when
 * `showModal` is set and the last showing in this browser (`localStorage['fs.donationModalShownAt']`) is at least
 * `modalHoursLoggedIn` / `modalHoursLoggedOut` hours ago — never on /donate itself. Its text is the CMS page
 * `donation-modal` (fallback: a translated default). Opening it stores the time, whatever the visitor clicks.
 */
@Component({
  selector: 'fs-donation-modal',
  imports: [Button, Dialog, MarkdownComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (catalogReady()) {
      <p-dialog [closeAriaLabel]="'common.close' | t"
        [visible]="open()"
        (visibleChange)="open.set($event)"
        [modal]="true"
        [draggable]="false"
        [dismissableMask]="true"
        [header]="text()?.title ?? ('content.donationModal.title' | t)"
        [style]="{ width: 'min(34rem, 94vw)' }"
        data-testid="content.donationModal"
      >
        <div class="body" data-testid="content.donationModal.body">
          @if (text(); as t) {
            <fs-markdown [markdown]="t.body" />
          } @else {
            <p>{{ 'content.donationModal.text' | t }}</p>
          }
        </div>
        <ng-template #footer>
          <p-button
            [label]="'content.donationModal.later' | t"
            severity="secondary"
            [text]="true"
            (onClick)="open.set(false)"
            data-testid="content.donationModal.later"
          />
          <p-button
            [label]="'content.donationModal.donate' | t"
            icon="pi pi-heart-fill"
            routerLink="/donate"
            (onClick)="open.set(false)"
            data-testid="content.donationModal.donate"
          />
        </ng-template>
      </p-dialog>
    }
  `,
  styles: `
    .body {
      display: grid;
      gap: 0.75rem;
    }
  `,
})
export class DonationModalComponent {
  private readonly api = inject(PublicContentService);
  private readonly toggles = inject(FeatureToggleService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  protected readonly open = signal(false);
  /** At most once per app load, also with 0 hours. */
  private shown = false;
  protected readonly catalogReady = signal(false);

  private readonly url = toSignal(
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    { initialValue: null },
  );

  private readonly settings = loadResource<DonationPage, true>({
    params: () => (this.toggles.isEnabled(FeatureToggleKey.DonationModal) ? true : undefined),
    loader: () => this.api.getDonationPage({}),
    keepDataOnError: false,
  });

  private readonly page = loadResource<ContentPage, string>({
    params: () => (this.open() ? this.i18n.language() : undefined),
    loader: (lang) => this.api.getContentPage({ slug: DONATION_MODAL_SLUG, lang: lang as Language }),
    keepDataOnError: false,
  });

  protected readonly text = computed(() =>
    this.page.status() === 'success' ? (this.page.data() ?? null) : null,
  );

  constructor() {
    void this.i18n.ensureComplete().then(() => this.catalogReady.set(true));
    effect(() => {
      const settings = this.settings.status() === 'success' ? this.settings.data() : undefined;
      this.url();
      if (!settings || !this.catalogReady()) {
        return;
      }
      untracked(() => {
        if (this.shown || this.router.url.split(/[?#]/)[0] === '/donate') {
          return;
        }
        const loggedIn = this.session.isAuthenticated();
        if (donationModalDue(settings, loggedIn, readStorage(DONATION_MODAL_SHOWN_KEY), Date.now())) {
          this.shown = true;
          writeStorage(DONATION_MODAL_SHOWN_KEY, String(Date.now()));
          this.open.set(true);
        }
      });
    });
  }
}
