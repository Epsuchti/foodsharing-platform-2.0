import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map } from 'rxjs';

import { FeatureToggleKey, Petition, PublicContentService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { FeatureToggleService } from '../../../core/feature-toggles/feature-toggle.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { readStorage, writeStorage } from '../../../core/storage';
import { PETITION_DISMISSED_KEY, loadPetition, petitionBannerAllowedOn } from '../petition.resource';
import { PetitionNoticeComponent } from './petition-notice.component';

/**
 * Petition banner of the shell (C8). Loads only while PETITION_BANNER is on (toggles load after the first page) and
 * shows nothing while no petition runs (503 PETITION_INACTIVE), when loading fails, or after the visitor dismissed
 * this petition (`localStorage['fs.petitionDismissed']` = its link). The shell places it in the page flow above the
 * footer, so it never moves content that is already on screen or covers controls.
 */
@Component({
  selector: 'fs-petition-banner',
  imports: [PetitionNoticeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.hidden]': 'petition() ? null : ""' },
  template: `
    @if (petition(); as p) {
      <fs-petition-notice [petition]="p" (dismiss)="dismiss(p)" />
    }
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, var(--fs-page-max));
      margin: 0 auto;
      padding: 0 var(--fs-gutter);
    }
    :host([hidden]) {
      display: none;
    }
  `,
})
export class PetitionBannerComponent {
  private readonly api = inject(PublicContentService);
  private readonly toggles = inject(FeatureToggleService);

  private readonly dismissed = signal<string | null>(readStorage(PETITION_DISMISSED_KEY));

  private readonly state = loadResource<Petition | null, true>({
    params: () => (this.toggles.isEnabled(FeatureToggleKey.PetitionBanner) ? true : undefined),
    loader: () => loadPetition(this.api),
    isEmpty: (value) => !value,
    keepDataOnError: false,
  });

  /** The shell renders this before the rest of the catalog (content.*) may have arrived: wait for it. */
  private readonly catalogReady = signal(false);

  private readonly router = inject(Router);
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly petition = computed(() => {
    const value = this.state.status() === 'success' ? this.state.data() : null;
    return value && this.catalogReady() && value.href !== this.dismissed() && petitionBannerAllowedOn(this.url()) ? value : null;
  });

  constructor() {
    void inject(I18nService)
      .ensureComplete()
      .then(() => this.catalogReady.set(true));
  }

  protected dismiss(p: Petition): void {
    this.dismissed.set(p.href);
    writeStorage(PETITION_DISMISSED_KEY, p.href);
  }
}
