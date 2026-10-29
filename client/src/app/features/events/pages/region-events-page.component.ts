import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { EventSummary, EventsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { EventListComponent } from '../components/event-list.component';

/** /regions/:regionId/events (E1): upcoming events grouped by month, "show past" switch, "New event" for members. */
@Component({
  selector: 'fs-region-events-page',
  imports: [...DATA_STATE_IMPORTS, Button, EventListComponent, FormsModule, PageHeaderComponent, RouterLink, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'events.title' | t" [subtitle]="regionName()" icon="pi pi-calendar" [backLink]="['/regions', regionId()]" [backLabel]="regionName() ?? ('events.region' | t)">
      @if (member()) {
        <p-button [label]="'events.new' | t" icon="pi pi-plus" [routerLink]="['/events/new']" [queryParams]="{ regionId: regionId() }" data-testid="events.list.new" />
      }
    </fs-page-header>
    <div class="bar">
      <p-toggleswitch inputId="events-past" [ngModel]="includePast()" (ngModelChange)="includePast.set($event)" data-testid="events.list.past" />
      <label for="events-past">{{ 'events.showPast' | t }}</label>
    </div>
    <fs-data-state [state]="events" skeleton="list" [emptyTitle]="'events.emptyTitle' | t" [emptyText]="'events.emptyText' | t" emptyIcon="pi pi-calendar" data-testid="events.list.state">
      <ng-template [fsData]="events" let-list>
        <fs-event-list [events]="list" />
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; width: min(100%, 52rem); margin: 0 auto; }
    .bar { display: flex; align-items: center; gap: 0.5rem; min-height: 2.5rem; }
  `,
})
export class RegionEventsPageComponent {
  private readonly api = inject(EventsService);
  private readonly session = inject(SessionService);

  readonly regionId = input.required<string>();
  protected readonly includePast = signal(false);
  private readonly unit = computed(() => (this.session.session().units ?? []).find((u) => u.id === this.regionId()));
  protected readonly regionName = computed(() => this.unit()?.name);
  protected readonly member = computed(() => !!this.unit()?.member);

  protected readonly events = loadResource<EventSummary[], { regionId: string; includePast: boolean }>({
    params: () => ({ regionId: this.regionId(), includePast: this.includePast() }),
    loader: (p) => this.api.listRegionEvents(p),
  });
}
