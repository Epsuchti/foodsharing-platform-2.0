import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { NewsletterUnsubscribeInfo, PublicNewsletterService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/**
 * Public `/newsletter/unsubscribe/:token` (N7, no app menu): shows the masked address, one button opts out of all
 * newsletters, then a done state. Unknown or expired tokens show the not-found state.
 */
@Component({
  selector: 'fs-newsletter-unsubscribe-page',
  imports: [...DATA_STATE_IMPORTS, Button, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="unsubscribe fs-card" data-testid="newsletters.unsubscribe.page">
      <h1>{{ 'newsletters.unsubscribe.title' | t }}</h1>
      <fs-data-state [state]="info" skeleton="form" [skeletonRows]="1" [backLink]="'/'" data-testid="newsletters.unsubscribe.state">
        <ng-template [fsData]="info" let-i>
          @if (done()) {
            <p class="done" role="status" data-testid="newsletters.unsubscribe.done">
              <i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'newsletters.unsubscribe.done' | t: { email: i.email } }}
            </p>
            <a routerLink="/account/notifications" class="fs-btn fs-btn--text">{{ 'newsletters.unsubscribe.settings' | t }}</a>
          } @else {
            <p>{{ 'newsletters.unsubscribe.text' | t: { email: i.email } }}</p>
            <p-button
              [label]="'newsletters.unsubscribe.confirm' | t"
              icon="pi pi-times"
              severity="danger"
              [loading]="confirm.busy()"
              (onClick)="confirm.run()"
              data-testid="newsletters.unsubscribe.confirm"
            />
          }
        </ng-template>
      </fs-data-state>
    </main>
  `,
  styles: `
    :host {
      display: grid;
      place-items: start center;
      padding: 2rem 1rem;
    }
    .unsubscribe {
      display: grid;
      gap: 1rem;
      width: min(100%, 32rem);
      padding: 1.5rem;
    }
    h1 {
      margin: 0;
      font-size: 1.35rem;
    }
    .done {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      font-weight: 600;
    }
  `,
})
export class UnsubscribePageComponent {
  private readonly api = inject(PublicNewsletterService);
  readonly token = input.required<string>();
  protected readonly done = signal(false);

  protected readonly info = loadResource<NewsletterUnsubscribeInfo, string>({
    params: () => this.token(),
    loader: (token) => this.api.getNewsletterUnsubscribe({ token }),
    isEmpty: () => false,
  });

  protected readonly confirm = runMutation(() => this.api.confirmNewsletterUnsubscribe({ token: this.token() }), {
    onSuccess: () => this.done.set(true),
  });
}
