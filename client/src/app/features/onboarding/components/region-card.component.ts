import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Explainer, PublicOnboardingService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/**
 * Region card of the journey (§11.2 "find your region", §11.1 step 2): name, ambassadors, members and the next
 * EinAb dates with free places, from the public explainer endpoint (`getExplainer?regionId=`).
 */
@Component({
  selector: 'fs-region-card',
  imports: [...DATA_STATE_IMPORTS, DateTimePipe, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="explainer" skeleton="card" [skeletonRows]="1" [compact]="true" [attr.data-testid]="testId() + 'State'" [testId]="testId() + 'State'">
      <ng-template [fsData]="explainer" let-e>
        @if (e.region; as r) {
          <article class="region" [attr.data-testid]="testId()" [attr.data-region]="r.id">
            <h3 class="region__name">{{ r.name }}</h3>
            @if (r.teaser) {
              <p class="muted">{{ r.teaser }}</p>
            }
            <p class="muted">
              <i class="pi pi-users" aria-hidden="true"></i>
              {{ 'onboarding.region.members' | t: { count: r.memberCount } }}
              @if (r.ambassadorNames.length) {
                · {{ 'onboarding.region.ambassadors' | t: { names: r.ambassadorNames.join(', ') } }}
              }
            </p>
            <h4 class="region__sub">{{ 'onboarding.region.nextIntroPickups' | t }}</h4>
            @if (r.nextIntroPickups.length) {
              <ul class="dates">
                @for (p of r.nextIntroPickups.slice(0, 4); track p.id) {
                  <li [attr.data-testid]="testId() + '.date'">
                    <strong>{{ p.startAt | fsDate: 'dateTime' }}</strong>
                    <span class="muted">{{ 'common.introCategory.' + p.category | t }} · {{ 'onboarding.region.freeSlots' | t: { count: p.freeSlots } }}</span>
                  </li>
                }
              </ul>
            } @else {
              <p class="muted" [attr.data-testid]="testId() + '.noDates'">{{ 'onboarding.region.noDates' | t }}</p>
            }
            <a [routerLink]="['/r', r.slug, 'intro-pickups']" class="region__link" [attr.data-testid]="testId() + '.allDates'">{{ 'onboarding.region.allDates' | t }}</a>
          </article>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    .region {
      display: grid;
      gap: 0.5rem;
      padding: 1rem;
      border: 1px solid var(--fs-border);
      border-radius: 0.75rem;
      background: var(--fs-surface);
    }
    .region__name,
    .region__sub {
      margin: 0;
    }
    .region__name {
      font-size: 1.15rem;
    }
    .region__sub {
      font-size: 0.95rem;
    }
    .muted {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .dates {
      display: grid;
      gap: 0.35rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .dates li {
      display: grid;
      gap: 0.1rem;
    }
    .region__link {
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
  `,
})
export class RegionCardComponent {
  private readonly api = inject(PublicOnboardingService);

  readonly regionId = input.required<string>();
  readonly testId = input('onboarding.regionCard');

  protected readonly explainer = loadResource<Explainer, string>({
    params: () => this.regionId(),
    loader: (regionId) => this.api.getExplainer({ regionId }),
    isEmpty: (explainer) => !explainer.region,
  });
}
