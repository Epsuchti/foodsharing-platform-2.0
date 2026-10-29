import { ChangeDetectionStrategy, Component, Type } from '@angular/core';
import { of } from 'rxjs';

import { Language, Role, SleepStatus, Theme, UserProfile } from '../../../../api';
import { loadResource } from '../../../../core/data/data-state';
import { BounceBannerComponent } from '../../../account/components/bounce-banner.component';
import { DangerZoneCardComponent } from '../../../account/components/danger-zone-card.component';
import { DataExportCardComponent } from '../../../account/components/data-export-card.component';
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

const BOUNCED: UserProfile = { ...BASE, emailBouncedAt: '2026-11-01T10:12:00Z' };

/**
 * Account settings cards (plan-m7 Q3/Q4, A5/A9/A13/A18/A21): photo card without photo, sleep mode card in TEMPORARY
 * mode, the bounce warning, the data export card before its first run and the danger zone.
 */
@Component({
  selector: 'fs-gallery-account-section',
  imports: [BounceBannerComponent, DangerZoneCardComponent, DataExportCardComponent, PhotoCardComponent, SleepModeCardComponent],
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
      <section class="specimen" data-testid="gallery.account.bounce">
        <header class="specimen__head"><code>fs-bounce-banner (address bounces)</code></header>
        <fs-bounce-banner [profile]="bounced" />
      </section>
      <section class="specimen" data-testid="gallery.account.export">
        <header class="specimen__head"><code>fs-data-export-card</code></header>
        <fs-data-export-card />
      </section>
      <section class="specimen" data-testid="gallery.account.danger">
        <header class="specimen__head"><code>fs-danger-zone-card</code></header>
        <fs-danger-zone-card [profile]="noPhoto" />
      </section>
    </div>
  `,
})
export class AccountGallerySection {
  protected readonly now = new Date('2026-11-02T09:00:00Z');
  protected readonly noPhoto = loadResource<UserProfile>({ loader: () => of(BASE), isEmpty: () => false });
  protected readonly sleeping = loadResource<UserProfile>({ loader: () => of(SLEEPING), isEmpty: () => false });
  protected readonly bounced = loadResource<UserProfile>({ loader: () => of(BOUNCED), isEmpty: () => false });
}

export const ACCOUNT_GALLERY_SECTIONS: readonly Type<unknown>[] = [AccountGallerySection];
