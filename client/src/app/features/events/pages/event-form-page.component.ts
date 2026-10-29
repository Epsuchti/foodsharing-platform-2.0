import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { RadioButton } from '@openng/optimus-ui/radiobutton';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { of } from 'rxjs';

import { Event, EventLocation, EventType, EventsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { eventErrors, fromLocalInput, toLocalInput } from '../events.resource';

/**
 * /events/new?regionId= and /events/:eventId/edit (E1): the type controls the required fields (OFFLINE: location
 * name, ONLINE: https link), start before end, public switch and "invite all members" on creation.
 */
@Component({
  selector: 'fs-event-form-page',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, InputText, PageHeaderComponent, RadioButton, Select, Textarea, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="(eventId() ? 'events.form.editTitle' : 'events.form.newTitle') | t" icon="pi pi-calendar-plus" [backLink]="backLink()" [backLabel]="'events.title' | t" />
    <fs-data-state [state]="existing" skeleton="form" data-testid="events.form.state">
      <ng-template [fsData]="existing" let-_>
        <form class="form fs-card" (ngSubmit)="submit()" novalidate>
          @if (!eventId()) {
            <label for="event-region">{{ 'events.form.region' | t }}</label>
            <p-select inputId="event-region" name="region" [options]="units()" optionLabel="name" optionValue="id" [ngModel]="unitId()" (ngModelChange)="unitId.set($event)" appendTo="body" data-testid="events.form.region" />
          }
          <fieldset class="types">
            <legend>{{ 'events.form.type' | t }}</legend>
            @for (t of types; track t) {
              <span class="radio">
                <p-radiobutton name="type" [value]="t" [inputId]="'event-type-' + t" [ngModel]="type()" (ngModelChange)="type.set($event)" [attr.data-testid]="'events.form.type.' + t" />
                <label [for]="'event-type-' + t">{{ 'events.type.' + t | t }}</label>
              </span>
            }
          </fieldset>
          <label for="event-name">{{ 'events.form.name' | t }}</label>
          <input pInputText id="event-name" name="name" maxlength="200" [ngModel]="name()" (ngModelChange)="name.set($event)" [attr.aria-invalid]="shown('name')" data-testid="events.form.name" />
          @if (shown('name')) {<small class="error" data-testid="events.form.error.name">{{ 'events.form.error.name' | t }}</small>}
          <div class="row">
            <span class="field">
              <label for="event-start">{{ 'events.form.startsAt' | t }}</label>
              <input pInputText type="datetime-local" id="event-start" name="startsAt" [ngModel]="startsAt()" (ngModelChange)="startsAt.set($event)" [attr.aria-invalid]="shown('startsAt')" data-testid="events.form.startsAt" />
            </span>
            <span class="field">
              <label for="event-end">{{ 'events.form.endsAt' | t }}</label>
              <input pInputText type="datetime-local" id="event-end" name="endsAt" [ngModel]="endsAt()" (ngModelChange)="endsAt.set($event)" [attr.aria-invalid]="shown('endsAt')" data-testid="events.form.endsAt" />
            </span>
          </div>
          @if (shown('endsAt') || shown('startsAt')) {<small class="error" data-testid="events.form.error.endsAt">{{ 'events.form.error.dates' | t }}</small>}

          @if (type() !== 'ONLINE') {
            <fieldset class="location">
              <legend>{{ 'events.form.location' | t }}</legend>
              <label for="event-loc-name">{{ (type() === 'OFFLINE' ? 'events.form.locationNameRequired' : 'events.form.locationName') | t }}</label>
              <input pInputText id="event-loc-name" name="locName" maxlength="200" [ngModel]="locName()" (ngModelChange)="locName.set($event)" [attr.aria-invalid]="shown('location')" data-testid="events.form.locationName" />
              @if (shown('location')) {<small class="error" data-testid="events.form.error.location">{{ 'events.form.error.location' | t }}</small>}
              <label for="event-street">{{ 'events.form.street' | t }}</label>
              <input pInputText id="event-street" name="street" maxlength="200" [ngModel]="street()" (ngModelChange)="street.set($event)" data-testid="events.form.street" />
              <div class="row">
                <span class="field field--zip">
                  <label for="event-zip">{{ 'events.form.zip' | t }}</label>
                  <input pInputText id="event-zip" name="zip" maxlength="20" [ngModel]="zip()" (ngModelChange)="zip.set($event)" data-testid="events.form.zip" />
                </span>
                <span class="field">
                  <label for="event-city">{{ 'events.form.city' | t }}</label>
                  <input pInputText id="event-city" name="city" maxlength="100" [ngModel]="city()" (ngModelChange)="city.set($event)" data-testid="events.form.city" />
                </span>
              </div>
            </fieldset>
          }
          @if (type() !== 'OFFLINE') {
            <label for="event-link">{{ (type() === 'ONLINE' ? 'events.form.linkRequired' : 'events.form.link') | t }}</label>
            <input pInputText type="url" id="event-link" name="link" maxlength="500" placeholder="https://" [ngModel]="link()" (ngModelChange)="link.set($event)" [attr.aria-invalid]="shown('onlineLink')" data-testid="events.form.link" />
            @if (shown('onlineLink')) {<small class="error" data-testid="events.form.error.onlineLink">{{ 'events.form.error.link' | t }}</small>}
          }
          <label for="event-description">{{ 'events.form.description' | t }}</label>
          <textarea pTextarea id="event-description" name="description" rows="5" [ngModel]="description()" (ngModelChange)="description.set($event)" data-testid="events.form.description"></textarea>
          <span class="switch">
            <p-toggleswitch inputId="event-public" name="public" [ngModel]="isPublic()" (ngModelChange)="isPublic.set($event)" data-testid="events.form.public" />
            <label for="event-public">{{ 'events.form.public' | t }}</label>
          </span>
          @if (!eventId()) {
            <span class="switch">
              <p-toggleswitch inputId="event-invite-all" name="inviteAll" [ngModel]="inviteAll()" (ngModelChange)="inviteAll.set($event)" data-testid="events.form.inviteAll" />
              <label for="event-invite-all">{{ 'events.form.inviteAll' | t }}</label>
            </span>
          }
          <div class="actions">
            <p-button type="submit" icon="pi pi-check" [label]="(eventId() ? 'common.save' : 'events.form.create') | t" [loading]="save.busy()" data-testid="events.form.submit" />
          </div>
        </form>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; width: min(100%, 44rem); margin: 0 auto; }
    .form { display: grid; gap: 0.4rem; padding: 1rem; }
    label, legend { margin-top: 0.4rem; font-weight: 600; }
    fieldset { display: grid; gap: 0.4rem; margin: 0; padding: 0; border: 0; }
    .types { display: flex; flex-wrap: wrap; gap: 1rem; }
    .types legend { width: 100%; }
    .radio, .switch { display: inline-flex; align-items: center; gap: 0.5rem; min-height: 2.5rem; }
    .radio label, .switch label { margin: 0; font-weight: 400; }
    .row { display: flex; flex-wrap: wrap; gap: 0.75rem; }
    .field { display: grid; gap: 0.25rem; flex: 1 1 12rem; }
    .field--zip { flex: 0 1 7rem; }
    .error { color: var(--fs-danger); }
    .actions { display: flex; justify-content: flex-end; margin-top: 0.5rem; }
  `,
})
export class EventFormPageComponent {
  private readonly api = inject(EventsService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  readonly eventId = input<string | undefined>(undefined);
  readonly regionId = input<string | undefined>(undefined);

  protected readonly types = [EventType.Offline, EventType.Online, EventType.Other];
  protected readonly units = computed(() => (this.session.session().units ?? []).filter((u) => u.member));
  protected readonly unitId = signal<string | null>(null);
  protected readonly type = signal<EventType>(EventType.Offline);
  protected readonly name = signal('');
  protected readonly description = signal('');
  protected readonly startsAt = signal('');
  protected readonly endsAt = signal('');
  protected readonly locName = signal('');
  protected readonly street = signal('');
  protected readonly zip = signal('');
  protected readonly city = signal('');
  protected readonly link = signal('');
  protected readonly isPublic = signal(false);
  protected readonly inviteAll = signal(true);
  private readonly submitted = signal(false);

  protected readonly errors = computed(() =>
    eventErrors({ type: this.type(), name: this.name(), startsAt: this.startsAt(), endsAt: this.endsAt(), locationName: this.locName(), onlineLink: this.link() }),
  );

  protected readonly backLink = computed(() =>
    this.eventId() ? ['/events', this.eventId()] : this.regionId() ? ['/regions', this.regionId(), 'events'] : ['/events/mine'],
  );

  /** The event being edited; on creation a placeholder so the form renders inside the same DataState. */
  protected readonly existing = loadResource<Event | null, string>({
    params: () => this.eventId() ?? '',
    loader: (eventId) => (eventId ? this.api.getEvent({ eventId }) : of(null)),
    isEmpty: () => false,
  });

  constructor() {
    effect(() => {
      const region = this.regionId();
      if (region) untracked(() => this.unitId.set(region));
    });
    effect(() => {
      const e = this.existing.data();
      if (e) untracked(() => this.fill(e));
    });
  }

  protected shown(field: string): boolean {
    return this.submitted() && this.errors().includes(field);
  }

  private fill(e: Event): void {
    this.type.set(e.type);
    this.name.set(e.name);
    this.description.set(e.description ?? '');
    this.startsAt.set(toLocalInput(e.startsAt));
    this.endsAt.set(toLocalInput(e.endsAt));
    this.locName.set(e.location?.name ?? '');
    this.street.set(e.location?.street ?? '');
    this.zip.set(e.location?.zip ?? '');
    this.city.set(e.location?.city ?? '');
    this.link.set(e.onlineLink ?? '');
    this.isPublic.set(e.public);
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.errors().length === 0 && (this.eventId() || this.unitId())) void this.save.run();
  }

  private location(): EventLocation | undefined {
    if (this.type() === EventType.Online || !this.locName().trim()) return undefined;
    const optional = (value: string) => (value.trim() ? value.trim() : undefined);
    return { name: this.locName().trim(), street: optional(this.street()), zip: optional(this.zip()), city: optional(this.city()) };
  }

  protected readonly save = runMutation(
    () => {
      const common = {
        type: this.type(),
        name: this.name().trim(),
        description: this.description(),
        startsAt: fromLocalInput(this.startsAt()),
        endsAt: fromLocalInput(this.endsAt()),
        public: this.isPublic(),
        location: this.location(),
      };
      const link = this.type() === EventType.Offline ? '' : this.link().trim();
      const eventId = this.eventId();
      return eventId
        ? this.api.updateEvent({ eventId, eventUpdate: { ...common, onlineLink: link } })
        : this.api.createEvent({
            eventCreate: { ...common, regionId: this.unitId()!, inviteAllMembers: this.inviteAll(), ...(link ? { onlineLink: link } : {}) },
          });
    },
    {
      successToastKey: 'events.form.saved',
      onSuccess: (event: Event) => void this.router.navigate(['/events', event.id]),
    },
  );
}
