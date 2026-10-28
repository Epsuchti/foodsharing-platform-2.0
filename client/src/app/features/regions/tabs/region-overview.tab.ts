import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PublicRegion, PublicRegionsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { RegionPageContext } from '../region-page.context';
import { IntroPickupDatesComponent } from '../shared/intro-pickup-dates.component';

/**
 * Overview tab (R3): description, ambassadors, welcome team, function groups, conference link and the next intro
 * pickups (public data of the region).
 */
@Component({
  selector: 'fs-region-overview-tab',
  imports: [
    ...DATA_STATE_IMPORTS,
    IntroPickupDatesComponent,
    MarkdownComponent,
    RouterLink,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (ctx.data(); as region) {
      <div class="cols">
        <div class="stack">
          <section class="fs-card card" data-testid="regions.overview.about">
            <h2 class="card__title"><i class="pi pi-info-circle" aria-hidden="true"></i>{{ 'regions.overview.about' | t }}</h2>
            @if (region.description) {
              <fs-markdown [markdown]="region.description" />
            } @else {
              <p class="muted">{{ 'regions.overview.noDescription' | t }}</p>
            }
          </section>
          <section class="fs-card card" data-testid="regions.overview.intro">
            <h2 class="card__title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'regions.overview.nextIntro' | t }}</h2>
            <fs-data-state [state]="publicRegion" skeleton="list" [skeletonRows]="2" [compact]="true" testId="regions.overview.introState" data-testid="regions.overview.introState">
              <ng-template [fsData]="publicRegion" let-pub>
                <fs-intro-pickup-dates [pickups]="pub.nextIntroPickups" testId="regions.overview.intro" />
              </ng-template>
            </fs-data-state>
          </section>
        </div>
        <div class="stack">
          <section class="fs-card card" data-testid="regions.overview.ambassadors">
            <h2 class="card__title"><i class="pi pi-star" aria-hidden="true"></i>{{ 'regions.overview.ambassadors' | t }}</h2>
            @if (region.ambassadors.length) {
              <ul class="people">
                @for (person of region.ambassadors; track person.id) {
                  <li>
                    <fs-user-chip [name]="person.displayName" [userId]="person.id" [avatarUrl]="person.avatarUrl" [role]="person.role" [verified]="person.verified" [showRole]="true" />
                  </li>
                }
              </ul>
            } @else {
              <p class="muted">{{ 'regions.overview.noAmbassadors' | t }}</p>
            }
          </section>
          @if (region.welcomeTeam || region.functions.length) {
            <section class="fs-card card" data-testid="regions.overview.functions">
              <h2 class="card__title"><i class="pi pi-sitemap" aria-hidden="true"></i>{{ 'regions.overview.functions' | t }}</h2>
              <ul class="links">
                @if (region.welcomeTeam; as welcome) {
                  <li>
                    <a [routerLink]="['/groups', welcome.id]" data-testid="regions.overview.welcomeTeam">
                      <span class="links__label">{{ 'regions.function.WELCOME' | t }}</span>
                      <span>{{ welcome.name }}</span>
                    </a>
                  </li>
                }
                @for (assignment of region.functions; track assignment.function) {
                  @if (assignment.function !== 'WELCOME') {
                    <li>
                      <a [routerLink]="['/groups', assignment.group.id]">
                        <span class="links__label">{{ 'regions.function.' + assignment.function | t }}</span>
                        <span>{{ assignment.group.name }}</span>
                      </a>
                    </li>
                  }
                }
              </ul>
            </section>
          }
          @if (region.conferenceUrl && ctx.isMember()) {
            <section class="fs-card card">
              <h2 class="card__title"><i class="pi pi-video" aria-hidden="true"></i>{{ 'regions.overview.conference' | t }}</h2>
              <p class="muted">{{ 'regions.overview.conferenceText' | t }}</p>
              <a class="fs-btn fs-btn--outlined" [href]="region.conferenceUrl" target="_blank" rel="noopener" data-testid="regions.overview.conference">
                <i class="pi pi-external-link" aria-hidden="true"></i>{{ 'regions.overview.conferenceOpen' | t }}
              </a>
            </section>
          }
        </div>
      </div>
    }
  `,
  styleUrl: './tab.scss',
  styles: `
    .links {
      display: grid;
      gap: 0.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .links a {
      display: grid;
      gap: 0.05rem;
      padding: 0.5rem 0.65rem;
      border-radius: var(--fs-radius-sm);
      color: var(--fs-text);
      text-decoration: none;
    }
    .links a:hover {
      background: var(--fs-surface-muted);
    }
    .links__label {
      color: var(--fs-text-muted);
      font-size: 0.78rem;
      font-weight: 650;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
  `,
})
export class RegionOverviewTab {
  protected readonly ctx = inject(RegionPageContext);
  private readonly publicApi = inject(PublicRegionsService);

  protected readonly publicRegion = loadResource<PublicRegion, string>({
    params: () => this.ctx.data()?.slug,
    loader: (slug) => this.publicApi.getPublicRegion({ slug }),
  });
}
