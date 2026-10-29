import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { Banana, BuddyRelation, PublicProfile, Role, UnitType, UserSummary } from '../../../../api';
import { BananaListComponent } from '../../../profiles/components/banana-list.component';
import { ProfileCardComponent } from '../../../profiles/components/profile-card.component';
import { ProfileStatsComponent } from '../../../profiles/components/profile-stats.component';

const SARAH: UserSummary = {
  id: 'u6',
  displayName: 'Sarah Müller',
  firstName: 'Sarah',
  role: Role.Foodsaver,
  verified: true,
  foodsaverNumber: 100006,
  homeRegion: { id: 'r1', slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.City },
};
const MARCO: UserSummary = {
  id: 'u8',
  displayName: 'Marco Bernasconi',
  firstName: 'Marco',
  role: Role.Foodsaver,
  verified: true,
  sleepMode: true,
  sleepMessage: 'Bis Ende Monat in den Ferien, danach wieder für Abholungen da.',
};
const NINA: UserSummary = { id: 'u9', displayName: 'Nina Schneider', role: Role.StoreManager, verified: true };

const PERMISSIONS: PublicProfile['permissions'] = {
  mayEditProfile: true,
  maySeeNotes: true,
  mayReport: false,
  mayGiveBanana: false,
  maySeeHistory: true,
  maySeeCommitments: true,
  mayAdminister: true,
  maySeePickups: true,
};

/** Fixed dates far in the past so the screenshots stay stable. */
const ADMIN_VIEW: PublicProfile = {
  user: SARAH,
  aboutMe: 'Ich rette seit 2019 Lebensmittel in Zürich und koordiniere die Abholungen am Wochenmarkt.',
  aboutMeInternal: 'Hat 2025 die Einführung neuer Foodsaver begleitet.',
  regions: [],
  groups: [],
  stats: { pickupCount: 128, kgSaved: 1540.5, postCount: 37, bananaCount: 5, buddyCount: 2, basketsSharedCount: 4 },
  banana: { count: 5, givenByMe: false, mayGive: false },
  buddy: { status: BuddyRelation.None },
  achievements: [],
  kamChains: [],
  registeredAt: '2025-01-15T09:00:00Z',
  permissions: PERMISSIONS,
};
const SLEEPING: PublicProfile = {
  ...ADMIN_VIEW,
  user: MARCO,
  aboutMe: undefined,
  aboutMeInternal: undefined,
  registeredAt: '2024-10-11T09:00:00Z',
  permissions: { ...PERMISSIONS, mayAdminister: false },
};

const BANANAS: Banana[] = [
  {
    sender: NINA,
    recipient: SARAH,
    message: 'Sarah ist immer pünktlich, packt mit an und sorgt dafür, dass bei der Abholung nichts liegen bleibt. Danke!',
    givenAt: '2025-03-02T09:30:00Z',
  },
  {
    sender: MARCO,
    recipient: SARAH,
    message: 'Zuverlässig, freundlich und mit viel Erfahrung – mit Sarah machen Abholungen auch im Regen Spass.',
    givenAt: '2025-02-11T17:05:00Z',
  },
];

/** Profile pieces (plan-m7 Q2, A6/A15): head card (admin view, sleep banner), stat tiles, banana list. */
@Component({
  selector: 'fs-gallery-profiles-section',
  imports: [BananaListComponent, ProfileCardComponent, ProfileStatsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-profiles">Profiles</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.profileCard.admin">
        <header class="specimen__head"><code>fs-profile-card (administrator view)</code></header>
        <fs-profile-card [profile]="admin" />
      </section>
      <section class="specimen" data-testid="gallery.profileCard.sleeping">
        <header class="specimen__head"><code>fs-profile-card (sleep mode)</code></header>
        <fs-profile-card [profile]="sleeping" />
      </section>
      <section class="specimen" data-testid="gallery.profileStats">
        <header class="specimen__head"><code>fs-profile-stats</code></header>
        <fs-profile-stats [stats]="admin.stats" />
      </section>
      <section class="specimen" data-testid="gallery.bananaList">
        <header class="specimen__head"><code>fs-banana-list (received, recipient may delete)</code></header>
        <fs-banana-list [bananas]="bananas" direction="received" viewerId="u6" />
      </section>
    </div>
  `,
})
export class ProfilesGallerySection {
  protected readonly admin = ADMIN_VIEW;
  protected readonly sleeping = SLEEPING;
  protected readonly bananas = BANANAS;
}

export const PROFILES_GALLERY_SECTIONS: readonly Type<unknown>[] = [ProfilesGallerySection];
