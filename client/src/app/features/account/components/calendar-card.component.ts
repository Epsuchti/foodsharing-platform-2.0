import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { catchError, of, throwError } from 'rxjs';

import { ApiErrorCode, CalendarService, CalendarToken } from '../../../api';
import { toApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  CALENDAR_EVENT_FILTERS,
  CALENDAR_FEED_DEFAULTS,
  CALENDAR_REMINDER_CHOICES,
  CALENDAR_REMINDERS_MAX,
  CalendarEventFilter,
  CalendarFeedOptions,
  calendarFeedUrl,
  webcalUrl,
} from '../account.util';

/**
 * Calendar subscription (A11, A49): a personal iCal feed of pickups, intro pickups and events. Without a token the
 * card offers to create one; with a token it shows the feed link built from the filters (`events`, `pickups`,
 * `introPickups`, `history`, `reminders`), a `webcal://` subscribe link, copy, regenerate (the old link stops
 * working) and delete.
 */
@Component({
  selector: 'fs-calendar-card',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, FormsModule, InputText, Select, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="calendar-title" data-testid="account.settings.calendar">
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-calendar"></i></span>
        <div class="card__head-text">
          <h2 id="calendar-title">{{ 'account.calendar.title' | t }}</h2>
          <p>{{ 'account.calendar.text' | t }}</p>
        </div>
      </header>

      <fs-data-state [state]="token" skeleton="form" [skeletonRows]="2" data-testid="account.settings.calendar.state">
        <ng-template fsEmpty>
          <div class="none" data-testid="account.settings.calendar.none">
            <p class="hint">
              <i class="pi pi-info-circle" aria-hidden="true"></i>
              <span>{{ 'account.calendar.none' | t }}</span>
            </p>
            <p-button
              [label]="'account.calendar.create' | t"
              icon="pi pi-plus"
              [loading]="regenerate.busy()"
              (onClick)="regenerate.run()"
              data-testid="account.settings.calendar.create"
            />
          </div>
        </ng-template>
        <ng-template [fsData]="token" let-feed>
          <fieldset class="filters" data-testid="account.settings.calendar.filters">
            <legend>{{ 'account.calendar.filters' | t }}</legend>
            <div class="switches">
              @for (key of switches; track key) {
                <label class="switch" [for]="'cal-' + key">
                  <p-toggleswitch
                    [inputId]="'cal-' + key"
                    [ngModel]="options()[key]"
                    (ngModelChange)="setSwitch(key, $event)"
                    [attr.data-testid]="'account.settings.calendar.' + key"
                  />
                  <span>{{ 'account.calendar.' + key | t }}</span>
                </label>
              }
            </div>
            <div class="field">
              <label for="cal-events">{{ 'account.calendar.events' | t }}</label>
              <p-select
                inputId="cal-events"
                [options]="eventOptions()"
                optionLabel="label"
                optionValue="value"
                [ngModel]="options().events"
                (ngModelChange)="patch({ events: $event })"
                appendTo="body"
                data-testid="account.settings.calendar.events"
              />
            </div>
            <div class="field">
              <span class="field__label" id="cal-reminders-label">{{ 'account.calendar.reminders' | t }}</span>
              <div class="reminders" role="group" aria-labelledby="cal-reminders-label">
                @for (minutes of reminderChoices; track minutes) {
                  <label class="reminder" [for]="'cal-reminder-' + minutes">
                    <p-checkbox
                      [inputId]="'cal-reminder-' + minutes"
                      [binary]="true"
                      [ngModel]="options().reminders.includes(minutes)"
                      (ngModelChange)="toggleReminder(minutes, $event)"
                      [attr.data-testid]="'account.settings.calendar.reminder.' + minutes"
                    />
                    <span>{{ reminderLabel(minutes) }}</span>
                  </label>
                }
              </div>
            </div>
          </fieldset>

          <div class="field">
            <label for="cal-url">{{ 'account.calendar.url' | t }}</label>
            <div class="url">
              <input
                pInputText
                id="cal-url"
                readonly
                [value]="feedUrl(feed)"
                (focus)="$any($event.target).select()"
                data-testid="account.settings.calendar.url"
              />
              <p-button
                [label]="'account.calendar.copy' | t"
                icon="pi pi-copy"
                severity="secondary"
                [outlined]="true"
                (onClick)="copy(feed)"
                data-testid="account.settings.calendar.copy"
              />
            </div>
            <small class="muted">{{ 'account.calendar.urlHint' | t }}</small>
          </div>

          <div class="actions">
            <a
              class="fs-btn"
              [href]="webcal(feed)"
              data-testid="account.settings.calendar.subscribe"
            >
              <i class="pi pi-calendar-plus" aria-hidden="true"></i>
              <span>{{ 'account.calendar.subscribe' | t }}</span>
            </a>
            <p-button
              [label]="'account.calendar.regenerate' | t"
              icon="pi pi-refresh"
              severity="secondary"
              [outlined]="true"
              [loading]="regenerate.busy()"
              (onClick)="confirmRegenerate()"
              data-testid="account.settings.calendar.regenerate"
            />
            <p-button
              [label]="'account.calendar.delete' | t"
              icon="pi pi-trash"
              severity="danger"
              [text]="true"
              [loading]="remove.busy()"
              (onClick)="confirmDelete()"
              data-testid="account.settings.calendar.delete"
            />
          </div>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .none {
      display: grid;
      gap: 0.85rem;
      justify-items: start;
    }
    .filters {
      display: grid;
      gap: 0.9rem;
      margin: 0;
      padding: 0;
      border: 0;
    }
    .filters legend {
      margin-bottom: 0.4rem;
      padding: 0;
      font-weight: 700;
    }
    .switches {
      display: flex;
      flex-wrap: wrap;
      gap: 0.6rem 1.4rem;
    }
    .switch,
    .reminder {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      cursor: pointer;
    }
    .reminders {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1.1rem;
    }
    .field {
      display: grid;
      gap: 0.35rem;
    }
    .field label,
    .field__label {
      font-weight: 600;
    }
    .field p-select {
      max-width: 22rem;
    }
    .url {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .url input {
      flex: 1 1 16rem;
      min-width: 0;
      font-family: var(--fs-font-mono, monospace);
      font-size: 0.85rem;
    }
    .muted {
      color: var(--fs-text-muted);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
  `,
})
export class CalendarCardComponent {
  private readonly api = inject(CalendarService);
  private readonly confirm = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly document = inject(DOCUMENT);
  private readonly i18n = inject(I18nService);

  protected readonly switches = ['pickups', 'introPickups', 'history'] as const;
  protected readonly reminderChoices = CALENDAR_REMINDER_CHOICES;
  protected readonly options = signal<CalendarFeedOptions>({ ...CALENDAR_FEED_DEFAULTS });

  /** No token yet (404 CALENDAR_TOKEN_NOT_FOUND) is the empty state, not an error. */
  protected readonly token = loadResource<CalendarToken | null>({
    loader: () =>
      this.api.getCalendarToken().pipe(
        catchError((error: unknown) =>
          toApiErrorView(error).code === ApiErrorCode.CalendarTokenNotFound ? of(null) : throwError(() => error),
        ),
      ),
    isEmpty: (token) => !token,
  });

  protected readonly eventOptions = computed(() => {
    this.i18n.language();
    return CALENDAR_EVENT_FILTERS.map((value: CalendarEventFilter) => ({
      value,
      label: this.i18n.t(`account.calendar.eventFilter.${value}`),
    }));
  });

  protected patch(changes: Partial<CalendarFeedOptions>): void {
    this.options.update((options) => ({ ...options, ...changes }));
  }

  protected setSwitch(key: 'pickups' | 'introPickups' | 'history', value: boolean): void {
    this.patch({ [key]: value });
  }

  protected toggleReminder(minutes: number, on: boolean): void {
    this.options.update((options) => {
      const others = options.reminders.filter((value) => value !== minutes);
      const reminders = on ? [...others, minutes].slice(-CALENDAR_REMINDERS_MAX) : others;
      return { ...options, reminders: [...reminders].sort((a, b) => a - b) };
    });
  }

  protected reminderLabel(minutes: number): string {
    if (minutes % 1440 === 0) {
      return this.i18n.t('account.calendar.reminderDays', { count: minutes / 1440 });
    }
    if (minutes % 60 === 0) {
      return this.i18n.t('account.calendar.reminderHours', { count: minutes / 60 });
    }
    return this.i18n.t('account.calendar.reminderMinutes', { count: minutes });
  }

  /** `feed` is never null inside the data template (null is the empty state). */
  protected feedUrl(feed: CalendarToken | null): string {
    return feed ? calendarFeedUrl(feed.url, this.options()) : '';
  }

  protected webcal(feed: CalendarToken | null): string {
    return webcalUrl(this.feedUrl(feed));
  }

  protected copy(feed: CalendarToken | null): void {
    const url = this.feedUrl(feed);
    const clipboard = this.document.defaultView?.navigator?.clipboard;
    const done = () => this.messages.add({ severity: 'success', summary: this.i18n.t('account.calendar.copied'), life: 3000 });
    if (clipboard?.writeText) {
      clipboard.writeText(url).then(done, () => undefined);
    }
  }

  protected readonly regenerate = runMutation(() => this.api.regenerateCalendarToken(), {
    successToastKey: 'account.calendar.created',
    onSuccess: (token) => this.token.set(token),
  });

  protected readonly remove = runMutation(() => this.api.deleteCalendarToken(), {
    successToastKey: 'account.calendar.deleted',
    onSuccess: () => this.token.set(null),
  });

  protected confirmRegenerate(): void {
    this.confirm.confirm({
      header: this.i18n.t('account.calendar.regenerateTitle'),
      message: this.i18n.t('account.calendar.regenerateText'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('account.calendar.regenerate'),
      rejectLabel: this.i18n.t('common.cancel'),
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.regenerate.run(),
    });
  }

  protected confirmDelete(): void {
    this.confirm.confirm({
      header: this.i18n.t('account.calendar.deleteTitle'),
      message: this.i18n.t('account.calendar.deleteText'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('account.calendar.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.remove.run(),
    });
  }
}
