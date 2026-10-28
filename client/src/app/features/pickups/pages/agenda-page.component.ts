import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { map } from 'rxjs';

import { AgendaDay, PickupsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { addDays } from '../pickups.util';

interface AgendaQuery {
  userId: string;
  date: string;
}

/** /pickups/agenda/:date (PU8): my pickups and intro pickups (EinAbs) of one day, with a day stepper. */
@Component({
  selector: 'fs-agenda-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'pickups.agenda.title' | t" icon="pi pi-list" />
    <nav class="stepper" [attr.aria-label]="'pickups.agenda.stepper' | t">
      <p-button icon="pi pi-chevron-left" [text]="true" [rounded]="true" [ariaLabel]="'pickups.agenda.previous' | t" [routerLink]="['/pickups/agenda', previous()]" data-testid="pickups.agenda.previous" />
      <h2 class="stepper__day" data-testid="pickups.agenda.day">{{ date() + 'T12:00:00Z' | fsDate: 'weekday' }}</h2>
      <p-button icon="pi pi-chevron-right" [text]="true" [rounded]="true" [ariaLabel]="'pickups.agenda.next' | t" [routerLink]="['/pickups/agenda', next()]" data-testid="pickups.agenda.next" />
    </nav>
    <fs-data-state
      [state]="agenda"
      skeleton="list"
      [emptyTitle]="'pickups.agenda.empty' | t"
      testId="pickups.agenda.state"
      data-testid="pickups.agenda.state"
    >
      <ng-template [fsData]="agenda" let-day>
        <ul class="cards">
          @for (pickup of day.pickups; track pickup.store.id + pickup.pickupAt) {
            <li class="fs-card entry" data-testid="pickups.agenda.pickup" [attr.data-store]="pickup.store.name">
              <span class="entry__time">{{ pickup.pickupAt | fsDate: 'time' }}</span>
              <a [routerLink]="['/stores', pickup.store.id, 'pickups']">{{ pickup.store.name }}</a>
              @if (!pickup.confirmed) {
                <span class="muted">{{ 'pickups.tab.waiting' | t }}</span>
              }
            </li>
          }
          @for (booking of day.introPickups; track booking.slotId) {
            <li class="fs-card entry entry--intro" data-testid="pickups.agenda.intro">
              <span class="entry__time">{{ booking.introPickup.startAt | fsDate: 'time' }}</span>
              <span>{{ 'pickups.agenda.intro' | t }} · {{ booking.introPickup.businessName ?? booking.introPickup.publicGeneralLocation }}</span>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; width: min(100%, 48rem); margin: 0 auto; }
    .stepper { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; margin-bottom: 1rem; }
    .stepper__day { margin: 0; font-size: 1.1rem; text-align: center; }
    .cards { display: grid; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
    .entry { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: baseline; padding: 0.8rem 1rem; }
    .entry--intro { border-left: 4px solid var(--fs-primary); }
    .entry__time { font-weight: 700; font-variant-numeric: tabular-nums; }
    .muted { color: var(--fs-text-muted); font-size: 0.88rem; }
  `,
})
export class AgendaPageComponent {
  private readonly api = inject(PickupsService);
  private readonly session = inject(SessionService);

  protected readonly date = toSignal(inject(ActivatedRoute).paramMap.pipe(map((p) => p.get('date') ?? '')), {
    initialValue: '',
  });
  protected readonly previous = computed(() => addDays(this.date(), -1));
  protected readonly next = computed(() => addDays(this.date(), 1));

  protected readonly agenda = loadResource<AgendaDay, AgendaQuery>({
    params: () => {
      const userId = this.session.user()?.id;
      return userId && /^\d{4}-\d{2}-\d{2}$/.test(this.date()) ? { userId, date: this.date() } : undefined;
    },
    loader: (query) => this.api.getAgenda(query),
    isEmpty: (day) => day.pickups.length === 0 && day.introPickups.length === 0,
  });
}
