import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EmptyStateComponent } from '../../../shared/data-state/empty-state.component';

/** Region features of later milestones (forum, events, polls, stores, food-share points, wall). */
export const LATER_FEATURES = ['forum', 'events', 'polls', 'stores', 'fsps', 'wall'] as const;

/** Placeholder tab for region features that ship in a later release. */
@Component({
  selector: 'fs-region-later-tab',
  imports: [EmptyStateComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-empty-state
      icon="pi pi-hourglass"
      [title]="title()"
      [text]="'regions.later.text' | t"
      data-testid="regions.later.empty"
    />
  `,
})
export class RegionLaterTab {
  private readonly i18n = inject(I18nService);
  private readonly feature = toSignal(inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('feature'))));

  protected readonly title = computed(() => {
    this.i18n.language();
    const feature = this.feature();
    const known = (LATER_FEATURES as readonly (string | null | undefined)[]).includes(feature);
    return this.i18n.t('regions.later.title', { feature: this.i18n.t(known ? `regions.later.${feature}` : 'regions.later.generic') });
  });
}
