import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { SelectButton } from '@openng/optimus-ui/selectbutton';
import { Textarea } from '@openng/optimus-ui/textarea';

import { Event, EventsService, InvitationStatus, UserSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { CalendarExportButtonComponent } from '../../../shared/calendar-export-button/calendar-export-button.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { EVENT_TYPE_ICON, eventIcs } from '../events.resource';

/**
 * /events/:eventId (E1–E3): header, date range, location with map (OFFLINE) or online link, description, RSVP
 * (yes / maybe / no; closed 24 h after the end), participants, invitations (editors), wall, "Add to calendar",
 * edit and delete for editors.
 */
@Component({
  selector: 'fs-event-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    CalendarExportButtonComponent,
    DateTimePipe,
    FormsModule,
    MapViewComponent,
    MarkdownComponent,
    PageHeaderComponent,
    RouterLink,
    SelectButton,
    Textarea,
    TPipe,
    UserChipComponent,
    UserPickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="event" skeleton="detail" data-testid="events.event.state">
      <ng-template [fsData]="event" let-e>
        <fs-page-header [title]="e.name" [subtitle]="e.region.name" [icon]="icon[e.type]" [backLink]="['/regions', e.regionId, 'events']" [backLabel]="'events.title' | t">
          <fs-calendar-export-button [ics]="ics(e.id)" fileName="event.ics" testId="events.event.ics" />
          @if (e.permissions.mayEdit) {
            <p-button icon="pi pi-pencil" [label]="'events.edit' | t" severity="secondary" [outlined]="true" [routerLink]="['/events', e.id, 'edit']" data-testid="events.event.edit" />
            <p-button icon="pi pi-trash" severity="danger" [text]="true" [ariaLabel]="'events.delete' | t" (onClick)="confirmDelete(e)" data-testid="events.event.delete" />
          }
        </fs-page-header>

        <section class="facts fs-card">
          <p class="when" data-testid="events.event.when">
            <i class="pi pi-clock" aria-hidden="true"></i>
            <time [attr.datetime]="e.startsAt">{{ e.startsAt | fsDate: 'dateTimeLong' }}</time> –
            <time [attr.datetime]="e.endsAt">{{ e.endsAt | fsDate: 'dateTime' }}</time>
          </p>
          <p><i class="pi pi-tag" aria-hidden="true"></i> {{ 'events.type.' + e.type | t }} @if (e.public) {· <span data-testid="events.event.public">{{ 'events.public' | t }}</span>}</p>
          @if (e.location; as loc) {
            <p data-testid="events.event.location"><i class="pi pi-map-marker" aria-hidden="true"></i> <strong>{{ loc.name }}</strong>@if (loc.street) {, {{ loc.street }}}@if (loc.zip || loc.city) {, {{ loc.zip }} {{ loc.city }}}</p>
          }
          @if (e.onlineLink) {
            <a class="p-button p-button-outlined link" [href]="e.onlineLink" target="_blank" rel="noopener noreferrer" data-testid="events.event.onlineLink"><i class="pi pi-video" aria-hidden="true"></i> {{ 'events.join' | t }}</a>
          }
          @if (markers(e).length) {
            @defer (on viewport) {
              <fs-map-view [markers]="markers(e)" [zoom]="15" height="14rem" [ariaLabel]="'events.map' | t" testId="events.event.map" />
            } @placeholder {
              <div class="map-placeholder"></div>
            }
          }
        </section>

        @if (e.description) {
          <section class="fs-card block"><fs-markdown [markdown]="e.description" data-testid="events.event.description" /></section>
        }

        <section class="fs-card block" [attr.aria-label]="'events.rsvp.title' | t">
          <h2 id="rsvp-title">{{ 'events.rsvp.title' | t }}</h2>
          @if (e.permissions.mayRsvp) {
          <p-selectbutton
            [options]="rsvpOptions()"
            optionLabel="label"
            optionValue="value"
            [ngModel]="e.myStatus === 'INVITED' ? null : e.myStatus"
            (ngModelChange)="$event && rsvp.run($event)"
            [disabled]="rsvp.busy()"
            [allowEmpty]="false"
            [ariaLabelledBy]="'rsvp-title'"
            data-testid="events.event.rsvp"
          />
          }
          @if (!e.permissions.mayRsvp) {
            <p class="muted" data-testid="events.event.rsvpClosed">{{ 'events.rsvp.closed' | t }}@if (e.myStatus) { · {{ 'events.status.' + e.myStatus | t }}}</p>
          } @else if (e.myStatus === 'INVITED') {
            <p class="muted" data-testid="events.event.invited">{{ 'events.rsvp.invited' | t }}</p>
          }
        </section>

        <section class="fs-card block" data-testid="events.event.participants">
          <h2>{{ 'events.participants.title' | t }}</h2>
          <h3>{{ 'events.participants.accepted' | t: { count: e.participants.accepted.length } }}</h3>
          <div class="chips" data-testid="events.event.accepted">
            @for (u of e.participants.accepted; track u.id) {<fs-user-chip [name]="u.displayName" [userId]="u.id" [avatarUrl]="u.avatarUrl" />}
          </div>
          <h3>{{ 'events.participants.maybe' | t: { count: e.participants.maybe.length } }}</h3>
          <div class="chips" data-testid="events.event.maybe">
            @for (u of e.participants.maybe; track u.id) {<fs-user-chip [name]="u.displayName" [userId]="u.id" [avatarUrl]="u.avatarUrl" />}
          </div>
          <p class="muted" data-testid="events.event.invitedCount">{{ 'events.participants.invited' | t: { count: e.participants.invitedCount } }}</p>
          @if (e.permissions.mayEdit) {
            <div class="invite">
              <fs-user-picker [regionId]="e.regionId" [excludeIds]="pickedIds()" [label]="'events.invite.label' | t" (picked)="pick($event)" testId="events.invite.picker" />
              @if (picked().length) {
                <div class="chips">
                  @for (u of picked(); track u.id) {<fs-user-chip [name]="u.displayName" [userId]="u.id" />}
                </div>
                <p-button icon="pi pi-send" [label]="'events.invite.submit' | t: { count: picked().length }" [loading]="invite.busy()" (onClick)="invite.run()" data-testid="events.invite.submit" />
              }
            </div>
          }
        </section>

        <section class="fs-card block" data-testid="events.event.wall">
          <h2>{{ 'events.wall.title' | t }}</h2>
          @if (e.permissions.mayComment) {
            <form class="wall-form" (ngSubmit)="comment.run()">
              <label for="event-wall">{{ 'events.wall.label' | t }}</label>
              <textarea pTextarea id="event-wall" name="body" rows="3" maxlength="4000" [(ngModel)]="draft" (ngModelChange)="draftLength.set($event.trim().length)" data-testid="events.wall.body"></textarea>
              <p-button type="submit" icon="pi pi-send" [label]="'events.wall.send' | t" [disabled]="draftLength() === 0" [loading]="comment.busy()" data-testid="events.wall.send" />
            </form>
          }
          <ul class="wall">
            @for (p of e.wallPosts; track p.id) {
              <li data-testid="events.wall.post" [attr.data-post-id]="p.id">
                <div class="wall__head">
                  <fs-user-chip [name]="p.author.displayName" [userId]="p.author.id" [avatarUrl]="p.author.avatarUrl" />
                  <time class="muted" [attr.datetime]="p.createdAt">{{ p.createdAt | fsDate: 'relative' }}</time>
                  @if (p.mayDelete) {
                    <p-button icon="pi pi-trash" severity="danger" [text]="true" [ariaLabel]="'events.wall.delete' | t" (onClick)="removePost.run(p.id)" data-testid="events.wall.delete" />
                  }
                </div>
                <p class="wall__body">{{ p.body }}</p>
              </li>
            } @empty {
              <li class="muted">{{ 'events.wall.empty' | t }}</li>
            }
          </ul>
        </section>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; width: min(100%, 52rem); margin: 0 auto; }
    .facts, .block { display: grid; gap: 0.5rem; padding: 1rem; }
    .facts p { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; margin: 0; }
    .link { justify-self: start; display: inline-flex; gap: 0.4rem; text-decoration: none; }
    .map-placeholder { height: 14rem; border-radius: var(--fs-radius-md); background: var(--fs-surface-muted); }
    h2 { margin: 0; font-size: 1.1rem; }
    h3 { margin: 0.4rem 0 0; font-size: 0.95rem; }
    .chips { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .muted { color: var(--fs-text-muted); margin: 0; }
    .invite, .wall-form { display: grid; gap: 0.5rem; justify-items: start; }
    .wall-form textarea { width: 100%; }
    .wall-form label { font-weight: 600; }
    .wall { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.75rem; }
    .wall__head { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; }
    .wall__body { margin: 0.25rem 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
  `,
})
export class EventPageComponent {
  private readonly api = inject(EventsService);
  private readonly router = inject(Router);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  readonly eventId = input.required<string>();
  protected readonly icon = EVENT_TYPE_ICON;
  protected readonly ics = eventIcs();
  protected draft = '';
  protected readonly draftLength = signal(0);
  protected readonly picked = signal<UserSummary[]>([]);
  protected readonly pickedIds = computed(() => this.picked().map((u) => u.id));

  protected readonly rsvpOptions = computed(() => {
    this.i18n.language();
    return [InvitationStatus.Accepted, InvitationStatus.Maybe, InvitationStatus.WontJoin].map((value) => ({
      value,
      label: this.i18n.t(`events.rsvp.${value}`),
    }));
  });

  protected readonly event = loadResource<Event, string>({
    params: () => this.eventId(),
    loader: (eventId) => this.api.getEvent({ eventId }),
    isEmpty: () => false,
  });

  protected markers(e: Event): MapMarker[] {
    const loc = e.location;
    return loc?.lat != null && loc?.lon != null ? [{ id: e.id, kind: 'event', lat: loc.lat, lon: loc.lon, label: loc.name }] : [];
  }

  protected pick(user: UserSummary): void {
    this.picked.update((list) => [...list, user]);
  }

  protected readonly rsvp = runMutation(
    (status: InvitationStatus) => this.api.respondToEvent({ eventId: this.eventId(), eventRsvp: { status } }),
    { successToastKey: 'events.rsvp.saved', onSuccess: () => this.event.reload(), onError: () => this.event.reload() },
  );

  protected readonly invite = runMutation(
    () => this.api.inviteToEvent({ eventId: this.eventId(), eventInvite: { userIds: this.pickedIds() } }),
    {
      successToastKey: 'events.invite.sent',
      onSuccess: () => {
        this.picked.set([]);
        this.event.reload();
      },
    },
  );

  protected readonly comment = runMutation(
    () => this.api.createEventWallPost({ eventId: this.eventId(), eventWallPostCreate: { body: this.draft.trim() } }),
    {
      onSuccess: () => {
        this.draft = '';
        this.draftLength.set(0);
        this.event.reload();
      },
    },
  );

  protected readonly removePost = runMutation(
    (postId: string) => this.api.deleteEventWallPost({ eventId: this.eventId(), postId }),
    { successToastKey: 'events.wall.deleted', onSuccess: () => this.event.reload() },
  );

  protected readonly remove = runMutation(() => this.api.deleteEvent({ eventId: this.eventId() }), {
    successToastKey: 'events.deleted',
    onSuccess: () => {
      const e = this.event.data();
      void this.router.navigate(e ? ['/regions', e.regionId, 'events'] : ['/events/mine']);
    },
  });

  protected confirmDelete(e: Event): void {
    this.confirm.confirm({
      header: this.i18n.t('events.deleteTitle'),
      message: this.i18n.t('events.deleteText', { name: e.name }),
      acceptLabel: this.i18n.t('events.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(),
    });
  }
}
