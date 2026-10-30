import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { combineLatest, map } from 'rxjs';

import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EmptyStateComponent } from '../../../shared/data-state/empty-state.component';

/** Region features of later milestones (forum, events, polls, stores, wall, resources, achievements). */
export const LATER_FEATURES = ['forum', 'events', 'polls', 'stores', 'wall', 'resources', 'achievements'] as const;

/**
 * Placeholder tab for region features that ship in a later release: `later/:feature`, or a tab route of its own with
 * `data.feature` (`wall`, `resources`; plan-m7 Q12 and the wall task replace them).
 */
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
  private readonly route = inject(ActivatedRoute);
  private readonly feature = toSignal(
    combineLatest([this.route.paramMap, this.route.data]).pipe(
      map(([params, data]) => params.get('feature') ?? (data['feature'] as string | undefined) ?? null),
    ),
  );

  protected readonly title = computed(() => {
    this.i18n.language();
    const feature = this.feature();
    const known = (LATER_FEATURES as readonly (string | null | undefined)[]).includes(feature);
    return this.i18n.t('regions.later.title', { feature: this.i18n.t(known ? `regions.later.${feature}` : 'regions.later.generic') });
  });
}
