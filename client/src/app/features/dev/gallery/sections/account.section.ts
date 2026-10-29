import { ChangeDetectionStrategy, Component, Type } from '@angular/core';
import { of } from 'rxjs';

import { Language, Role, SleepStatus, Theme, UserProfile } from '../../../../api';
import { loadResource } from '../../../../core/data/data-state';
import { PhotoCardComponent } from '../../../account/components/photo-card.component';
import { SleepModeCardComponent } from '../../../account/components/sleep-mode-card.component';

const BASE: UserProfile = {
  id: 'u6',
  email: 'sarah.mueller@local.test',
  emailVerifiedAt: '2025-12-19T18:20:00Z',
  displayName: 'Sarah Müller',
  language: Language.De,
  theme: Theme.System,
  role: Role.Foodsaver,
  verified: true,
  registeredAt: '2025-12-19T18:18:00Z',
  infoMailMessage: true,
  mentionNotifications: true,
};

const SLEEPING: UserProfile = {
  ...BASE,
  sleepStatus: SleepStatus.Temporary,
  sleepFrom: '2026-11-05',
  sleepUntil: '2026-11-20',
  sleepMessage: 'Ferien im Tessin, ab dem 21. wieder dabei.',
};

/** Account settings cards (plan-m7 Q3, A5/A9): photo card without photo, sleep mode card in TEMPORARY mode. */
@Component({
  selector: 'fs-gallery-account-section',
  imports: [PhotoCardComponent, SleepModeCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-account">Account</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.account.photo">
        <header class="specimen__head"><code>fs-photo-card (no photo yet)</code></header>
        <fs-photo-card [profile]="noPhoto" />
      </section>
      <section class="specimen" data-testid="gallery.account.sleep">
        <header class="specimen__head"><code>fs-sleep-mode-card (temporary)</code></header>
        <fs-sleep-mode-card [profile]="sleeping" [now]="now" />
      </section>
    </div>
  `,
})
export class AccountGallerySection {
  protected readonly now = new Date('2026-11-02T09:00:00Z');
  protected readonly noPhoto = loadResource<UserProfile>({ loader: () => of(BASE), isEmpty: () => false });
  protected readonly sleeping = loadResource<UserProfile>({ loader: () => of(SLEEPING), isEmpty: () => false });
}

export const ACCOUNT_GALLERY_SECTIONS: readonly Type<unknown>[] = [AccountGallerySection];
