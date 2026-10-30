import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PublicContentService, TeamGroup } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { TeamGroupCardComponent } from '../components/team-group-card.component';

/**
 * /team (C16, public): the working groups flagged for the team page (`RegionAdminUpdate.teamPage`, ORGA) with their
 * active members, sorted by the server. Links to the team text (`/about/team`).
 */
@Component({
  selector: 'fs-team-page',
  imports: [...DATA_STATE_IMPORTS, PageHeaderComponent, RouterLink, TPipe, TeamGroupCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.team.title' | t"
      [subtitle]="'content.team.subtitle' | t"
      icon="pi pi-users"
    >
      <a
        class="fs-btn fs-btn--outlined fs-btn--sm about"
        routerLink="/about/team"
        data-testid="content.team.about"
      >
        <i class="pi pi-info-circle" aria-hidden="true"></i>{{ 'content.team.about' | t }}
      </a>
    </fs-page-header>
    <fs-data-state
      [state]="team"
      skeleton="card"
      emptyIcon="pi pi-users"
      [emptyTitle]="'content.team.empty' | t"
      data-testid="content.team.state"
    >
      <ng-template [fsData]="team" let-groups>
        <div class="groups">
          @for (group of groups; track group.id) {
            <fs-team-group-card [group]="group" />
          }
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
    }
    .about {
      gap: 0.45rem;
      text-decoration: none;
    }
    .groups {
      display: grid;
      gap: 1rem;
    }
  `,
})
export class TeamPageComponent {
  private readonly api = inject(PublicContentService);

  protected readonly team = loadResource<TeamGroup[]>({
    loader: () => this.api.getPublicTeam(),
  });
}
