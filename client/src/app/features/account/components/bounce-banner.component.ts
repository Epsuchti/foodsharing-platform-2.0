import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { UserProfile, UsersService } from '../../../api';
import { DataState, DataStatus, loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/**
 * Bounce warning (A18, A48): mails to the own address bounced (`UserProfile.emailBouncedAt`), so the server holds
 * back every mail except login links. The banner explains it and offers "Address works again" (`clearEmailBounce`)
 * and, outside the settings, a link to change the address. On the settings page it uses the page's profile; on
 * the dashboard it loads the profile itself. Nothing is shown while loading or when the address is fine.
 */
@Component({
  selector: 'fs-bounce-banner',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="state()" [compact]="true" data-testid="account.bounce.state">
      <ng-template fsSkeleton></ng-template>
      <ng-template fsEmpty></ng-template>
      <ng-template [fsData]="state()" let-user>
        <div class="banner" role="alert" data-testid="account.bounce.banner">
          <i class="pi pi-exclamation-triangle banner__icon" aria-hidden="true"></i>
          <div class="banner__text">
            <strong>{{ 'account.bounce.title' | t }}</strong>
            <span>{{ 'account.bounce.text' | t: { email: user.email, date: (user.emailBouncedAt | fsDate: 'date') } }}</span>
          </div>
          <div class="banner__actions">
            @if (showChangeLink()) {
              <a class="fs-btn fs-btn--outlined" routerLink="/account/settings" fragment="email-title" data-testid="account.bounce.change">
                {{ 'account.bounce.change' | t }}
              </a>
            }
            <p-button
              [label]="'account.bounce.clear' | t"
              icon="pi pi-check"
              size="small"
              [loading]="clear.busy()"
              (onClick)="clear.run(user)"
              data-testid="account.bounce.clear"
            />
          </div>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  host: { '[class.shown]': 'shown()' },
  styles: `
    /* hidden while loading or when the address is fine, so the banner never takes space in the page's grid */
    :host {
      display: none;
    }
    :host(.shown) {
      display: block;
    }
    .banner {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1rem;
      padding: 0.9rem 1.1rem;
      border: 1px solid var(--fs-warning-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-text);
    }
    .banner__icon {
      color: var(--fs-warning-text);
      font-size: 1.2rem;
    }
    .banner__text {
      display: grid;
      flex: 1 1 18rem;
      gap: 0.2rem;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .banner__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class BounceBannerComponent {
  private readonly usersApi = inject(UsersService);

  /** The settings page's profile; without it the banner loads the profile itself (dashboard). */
  readonly profile = input<DataState<UserProfile> | undefined>(undefined);
  /** Offer the link to the email card (not needed on the settings page itself). */
  readonly showChangeLink = input(true);

  private readonly own = loadResource<UserProfile, true>({
    params: () => (this.profile() ? undefined : true),
    loader: () => this.usersApi.getCurrentUser(),
    isEmpty: (user) => !user.emailBouncedAt,
  });

  /**
   * The page's profile counts as empty here unless the address bounced; its loading and error states belong to the
   * page's cards (they show skeleton and retry), so the banner stays out of them.
   */
  private readonly shared = computed<DataState<UserProfile> | undefined>(() => {
    const profile = this.profile();
    if (!profile) {
      return undefined;
    }
    const status = computed<DataStatus>(() => {
      const user = profile.data();
      return (profile.status() === 'success' || profile.status() === 'refreshing') && user?.emailBouncedAt ? 'success' : 'empty';
    });
    return { ...profile, status };
  });

  protected readonly state = computed<DataState<UserProfile>>(() => this.shared() ?? this.own);
  protected readonly shown = computed(() => !['idle', 'loading', 'empty'].includes(this.state().status()));

  protected readonly clear = runMutation(
    (user: UserProfile) => this.usersApi.clearEmailBounce({ userId: user.id }),
    {
      successToastKey: 'account.bounce.cleared',
      onSuccess: () => {
        const profile = this.profile();
        if (profile) {
          profile.reload();
        } else {
          this.own.reload();
        }
      },
    },
  );
}
