import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { Achievement, AchievementSystemKey, AchievementVisibility, DuplicateMode } from '../../../../api';
import { AchievementCardComponent } from '../../../achievements/components/achievement-card.component';

const PUTZHELD: Achievement = {
  id: 'g3',
  name: 'Fairteiler-Putzheld',
  description: 'Hat beim grossen Fairteiler-Putztag mitgeholfen.',
  icon: 'sparkles',
  visibility: AchievementVisibility.Scope,
  duplicateMode: DuplicateMode.Multiple,
  awardedCount: 3,
  mayAdminister: true,
};
const HYGIENE: Achievement = {
  id: 'g1',
  name: 'Hygiene-Zertifikat',
  description: 'Hat das Hygiene-Quiz bestanden.',
  icon: 'shield',
  validityDays: 365,
  visibility: AchievementVisibility.Global,
  duplicateMode: DuplicateMode.Override,
  systemKey: AchievementSystemKey.HygieneCertificate,
  awardedCount: 8,
  mayAdminister: true,
};
const HIDDEN: Achievement = {
  id: 'g5',
  name: 'Vertrauensperson',
  icon: 'handshake',
  visibility: AchievementVisibility.Hidden,
  duplicateMode: DuplicateMode.Override,
  awardedCount: 0,
  mayAdminister: false,
};

/** Achievement cards (plan-m7 Q9, AC1): administrator view, automatic system achievement, member view. */
@Component({
  selector: 'fs-gallery-achievements-section',
  imports: [AchievementCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-achievements">Achievements</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.achievementCard.admin">
        <header class="specimen__head"><code>fs-achievement-card (administrator / system)</code></header>
        <fs-achievement-card [achievement]="putzheld" />
        <fs-achievement-card [achievement]="hygiene" />
      </section>
      <section class="specimen" data-testid="gallery.achievementCard.member">
        <header class="specimen__head"><code>fs-achievement-card (member, hidden, not awarded)</code></header>
        <fs-achievement-card [achievement]="hidden" />
      </section>
    </div>
  `,
  styles: `
    .specimen fs-achievement-card + fs-achievement-card { margin-top: 0.75rem; }
  `,
})
export class AchievementsGallerySection {
  protected readonly putzheld = PUTZHELD;
  protected readonly hygiene = HYGIENE;
  protected readonly hidden = HIDDEN;
}

export const ACHIEVEMENTS_GALLERY_SECTIONS: readonly Type<unknown>[] = [AchievementsGallerySection];
