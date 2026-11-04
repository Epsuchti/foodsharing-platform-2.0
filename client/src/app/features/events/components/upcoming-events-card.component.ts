import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { EventSummary, EventsService, EventType, InvitationStatus } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { DATE_TILE } from '../../../shared/date-tile/date-tile.component';
import { upcomingEvents } from '../../dashboard/dashboard.util';

/**
 * Home card "Upcoming events" (owner feedback 2026-11-04): my next three events (`listMyEvents`, not "won't join")
 * as date-tile rows with time, place or "Online" and my answer (icon + word); hidden when there are none.
 */
@Component({
  selector: 'fs-upcoming-events-card',
  imports: [...DATA_STATE_IMPORTS, ...DATE_TILE, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (events.status() !== 'empty') {
      <section class="fs-card card" data-testid="dashboard.events" aria-labelledby="dashboard-events-title">
        <h2 class="title" id="dashboard-events-title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'events.dashboard.title' | t }}</h2>
        <fs-data-state [state]="events" skeleton="list" [skeletonRows]="2" [compact]="true" testId="dashboard.events.state" data-testid="dashboard.events.state">
          <ng-template [fsData]="events">
            <ul class="list" data-testid="dashboard.events.list">
              @for (event of next(); track event.id) {
                <li class="row" data-testid="dashboard.events.event" [attr.data-event]="event.name" [attr.data-status]="event.myStatus">
                  <fs-date-tile [at]="event.startsAt" />
                  <div class="row__body">
                    <a class="row__title" [routerLink]="['/events', event.id]">{{ event.name }}</a>
                    <span class="row__meta">
                      <fs-tile-time [at]="event.startsAt" />
                      @if (event.type === online) {
                        <span>{{ 'events.online' | t }}</span>
                      } @else if (event.location; as place) {
                        <span>{{ place.name }}</span>
                      }
                      @if (event.myStatus) {
                        <span class="state" [class.state--ok]="event.myStatus !== invited" [class.state--wait]="event.myStatus === invited">
                          <i class="pi" [class.pi-check]="event.myStatus !== invited" [class.pi-hourglass]="event.myStatus === invited" aria-hidden="true"></i>{{ 'events.status.' + event.myStatus | t }}
                        </span>
                      }
                    </span>
                  </div>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
        <div class="links">
          <a routerLink="/events/mine" data-testid="dashboard.events.all">{{ 'events.dashboard.all' | t }}</a>
        </div>
      </section>
    }
  `,
  styleUrls: ['../../dashboard/components/home-card.scss', '../../../shared/date-tile/date-rows.scss'],
})
export class UpcomingEventsCardComponent {
  private readonly api = inject(EventsService);

  protected readonly online = EventType.Online;
  protected readonly invited = InvitationStatus.Invited;

  protected readonly events = loadResource<EventSummary[]>({
    loader: () => this.api.listMyEvents({}),
    isEmpty: (list) => upcomingEvents(list, new Date()).length === 0,
  });

  protected readonly next = computed(() => upcomingEvents(this.events.data() ?? [], new Date()));
}
