import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { EventSummary, EventsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { CalendarExportButtonComponent } from '../../../shared/calendar-export-button/calendar-export-button.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { EventListComponent } from '../components/event-list.component';
import { myEventsIcs } from '../events.resource';

/** /events/mine (E2, E3): my upcoming events (invited, accepted, maybe) and "Export all" as ICS. */
@Component({
  selector: 'fs-my-events-page',
  imports: [...DATA_STATE_IMPORTS, CalendarExportButtonComponent, EventListComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'events.mine.title' | t" icon="pi pi-calendar">
      <fs-calendar-export-button [ics]="ics" fileName="foodsharing-events.ics" [label]="'events.mine.export' | t" testId="events.mine.export" />
    </fs-page-header>
    <fs-data-state [state]="events" skeleton="list" [emptyTitle]="'events.mine.emptyTitle' | t" [emptyText]="'events.mine.emptyText' | t" emptyIcon="pi pi-calendar" data-testid="events.mine.state">
      <ng-template [fsData]="events" let-list>
        <fs-event-list [events]="list" />
      </ng-template>
    </fs-data-state>
  `,
  styles: `:host { display: grid; gap: 0.75rem; width: min(100%, 52rem); margin: 0 auto; }`,
})
export class MyEventsPageComponent {
  private readonly api = inject(EventsService);
  protected readonly ics = myEventsIcs();
  protected readonly events = loadResource<EventSummary[]>({ loader: () => this.api.listMyEvents({}) });
}
