import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';

/**
 * /account/profile — placeholder until profile editing (A4–A6) arrives in M6: shows the account basics
 * (GET /api/users/me) and links to the settings.
 */
@Component({
  selector: 'fs-profile-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    DateTimePipe,
    PageHeaderComponent,
    RouterLink,
    TPipe,
    UserAvatarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'account.profile.title' | t" icon="pi pi-user" />
    <fs-data-state [state]="profile" skeleton="detail" data-testid="account.profile.state">
      <ng-template [fsData]="profile" let-user>
        <section class="fs-card profile">
          <fs-user-avatar
            [name]="user.displayName"
            [avatarUrl]="user.avatarUrl"
            [role]="user.role"
            [colorSeed]="user.id"
            size="xl"
          />
          <div class="profile__who">
            <h2 class="profile__name" data-testid="account.profile.name">{{ user.displayName }}</h2>
            <p class="profile__email">{{ user.email }}</p>
            <span class="profile__role">{{ 'common.role.' + user.role | t }}</span>
          </div>
          <dl class="profile__facts">
            <div>
              <dt>{{ 'account.profile.memberSince' | t }}</dt>
              <dd>{{ user.registeredAt | fsDate: 'dateLong' }}</dd>
            </div>
            @if (user.lastLoginAt) {
              <div>
                <dt>{{ 'account.profile.lastLogin' | t }}</dt>
                <dd>{{ user.lastLoginAt | fsDate: 'dateTime' }}</dd>
              </div>
            }
          </dl>
        </section>
        <p class="soon">
          <i class="pi pi-sparkles" aria-hidden="true"></i>
          <span>
            {{ 'account.profile.comingSoon' | t }}
            <a routerLink="/account/settings" data-testid="account.profile.settingsLink">{{
              'account.profile.toSettings' | t
            }}</a>
          </span>
        </p>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 48rem);
      margin: 0 auto;
    }
    .profile {
      display: grid;
      justify-items: center;
      gap: 1rem;
      text-align: center;
    }
    .profile__who {
      display: grid;
      gap: 0.2rem;
    }
    .profile__name {
      margin: 0;
      font-size: 1.5rem;
    }
    .profile__email {
      margin: 0;
      color: var(--fs-text-muted);
      overflow-wrap: anywhere;
    }
    .profile__role {
      color: var(--fs-primary-strong);
      font-weight: 700;
    }
    .profile__facts {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 0.75rem 2rem;
      margin: 0;
      padding-top: 1rem;
      border-top: 1px solid var(--fs-border);
      width: 100%;
    }
    .profile__facts dt {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
      font-weight: 650;
    }
    .profile__facts dd {
      margin: 0;
      font-weight: 650;
    }
    .soon {
      display: flex;
      align-items: flex-start;
      gap: 0.6rem;
      margin: 1.25rem 0 0;
      padding: 0.9rem 1rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
    }
    .soon .pi {
      margin-top: 0.25rem;
      color: var(--fs-primary);
    }
  `,
})
export class ProfilePageComponent {
  private readonly usersApi = inject(UsersService);
  protected readonly profile = loadResource({ loader: () => this.usersApi.getCurrentUser() });
}
