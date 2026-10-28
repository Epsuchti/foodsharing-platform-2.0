import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MessageResponse, PublicIntroPickupsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/** `/intro-pickups/unsubscribe?token=` (EA9, public): the link in every notification mail; one click unsubscribes. */
@Component({
  selector: 'fs-unsubscribe-page',
  imports: [...DATA_STATE_IMPORTS, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card narrow" data-testid="introPickups.unsubscribe">
      <h1 class="card__title"><i class="pi pi-bell-slash" aria-hidden="true"></i>{{ 'introPickups.unsubscribe.title' | t }}</h1>
      @if (!token()) {
        <p class="error-text" data-testid="introPickups.unsubscribe.missing">{{ 'introPickups.unsubscribe.missing' | t }}</p>
      } @else {
        <fs-data-state [state]="result" skeleton="form" [skeletonRows]="1" data-testid="introPickups.unsubscribe.state">
          <ng-template [fsData]="result">
            <p data-testid="introPickups.unsubscribe.done">{{ 'introPickups.unsubscribe.done' | t }}</p>
          </ng-template>
        </fs-data-state>
      }
      <div class="actions">
        <a routerLink="/" class="fs-btn fs-btn--outlined">{{ 'introPickups.unsubscribe.home' | t }}</a>
      </div>
    </section>
  `,
  styleUrl: '../intro-pickups.scss',
})
export class UnsubscribePageComponent {
  private readonly api = inject(PublicIntroPickupsService);
  private readonly session = inject(SessionService);
  readonly token = input<string | undefined>(undefined);

  protected readonly result = loadResource<MessageResponse, string | undefined>({
    // POST after the session request, which hands out the CSRF cookie to a fresh browser
    params: () => (this.session.loaded() && this.token()) || undefined,
    loader: (token) => this.api.unsubscribeByToken({ unsubscribeRequest: { token: token! } }),
    isEmpty: () => false,
  });
}
