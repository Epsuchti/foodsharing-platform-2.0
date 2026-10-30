import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { RegionPageContext } from '../../regions/region-page.context';
import { AchievementsBoardComponent } from '../components/achievements-board.component';

/**
 * /regions/:regionId/achievements (AC1, plan-m7 Q9): the region's achievements. Ambassadors (and ORGA) define, award
 * and revoke; members see every definition except HIDDEN ones (server rule, A52).
 */
@Component({
  selector: 'fs-region-achievements-tab',
  imports: [AchievementsBoardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<fs-achievements-board [regionId]="regionId()" [mayDefine]="ctx.isAdmin()" />`,
})
export class RegionAchievementsTab {
  protected readonly ctx = inject(RegionPageContext);
  protected readonly regionId = computed(() => this.ctx.regionId() ?? undefined);
}
