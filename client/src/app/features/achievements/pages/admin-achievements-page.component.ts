import { ChangeDetectionStrategy, Component } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AchievementsBoardComponent } from '../components/achievements-board.component';

/**
 * /admin/achievements (AC1, ORGA): the global achievements, including the system ones (hygiene certificate, KAM
 * certificate) that are awarded automatically and cannot be deleted.
 */
@Component({
  selector: 'fs-admin-achievements-page',
  imports: [AchievementsBoardComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'achievements.admin.title' | t" [subtitle]="'achievements.admin.subtitle' | t" icon="pi pi-trophy" />
    <fs-achievements-board [regionId]="null" [mayDefine]="true" />
  `,
  styles: `
    :host { display: block; width: min(100%, 72rem); margin: 0 auto; }
  `,
})
export class AdminAchievementsPageComponent {}
