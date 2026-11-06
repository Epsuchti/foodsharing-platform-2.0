import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { Textarea } from '@openng/optimus-ui/textarea';

import { Pickup, PickupsService } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATE_INPUT_IMPORTS } from '../../../shared/date-input';
import { dayOf, moveSummary, timeOf, zonedToUtc } from '../pickups.util';

/**
 * PU10 (decision 170): a store manager moves one pickup (one-time or one regular occurrence) to another date and time
 * of the same store. The summary says who is told and how many linked EinAbs move along; an optional message goes to
 * everyone signed up. A time that is already taken (409 PICKUP_TIME_TAKEN) or in the past is shown in the dialog.
 */
@Component({
  selector: 'fs-move-pickup-dialog',
  imports: [...DATE_INPUT_IMPORTS, Button, DateTimePipe, Dialog, FormsModule, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="'pickups.move.title' | t"
      [style]="{ width: 'min(30rem, 94vw)' }"
    >
      @if (pickup(); as current) {
        <!-- the test id sits on the content: the p-dialog host is in the DOM while the dialog is closed, too -->
        <div class="form" data-testid="pickups.move.dialog">
          <p class="from" data-testid="pickups.move.from">
            {{ 'pickups.move.from' | t: { when: (current.pickupAt | fsDate: 'dateTime' : timeZone()) } }}
          </p>
          <label class="field">
            <span>{{ 'pickups.tab.date' | t }}</span>
            <fs-date-input [ngModel]="date()" (ngModelChange)="date.set($event); clearError()" testId="pickups.move.newDate" />
          </label>
          <label class="field">
            <span>{{ 'pickups.tab.time' | t }} ({{ timeZone() }})</span>
            <fs-time-input [ngModel]="time()" (ngModelChange)="time.set($event); clearError()" testId="pickups.move.newTime" />
          </label>
          <label class="field">
            <span>{{ 'pickups.move.message' | t }}</span>
            <textarea
              pTextarea
              rows="3"
              maxlength="500"
              [ngModel]="message()"
              (ngModelChange)="message.set($event)"
              [attr.aria-describedby]="'pickup-move-message-hint'"
              data-testid="pickups.move.message"
            ></textarea>
            <small class="muted" id="pickup-move-message-hint">{{ 'pickups.move.messageHint' | t }}</small>
          </label>
          <div class="summary" data-testid="pickups.move.summary">
            <p>{{ 'pickups.move.notified' | t: { count: summary().notified } }}</p>
            @if (summary().introPickups > 0) {
              <p data-testid="pickups.move.introPickups">
                {{ 'pickups.move.introPickups' | t: { count: summary().introPickups, booked: summary().booked } }}
              </p>
            }
            @if (current.isRegular) {
              <p data-testid="pickups.move.regularHint">{{ 'pickups.move.regularHint' | t }}</p>
            }
          </div>
          @if (errorCode(); as code) {
            <p class="error" role="alert" data-testid="pickups.move.error">{{ 'errors.' + code | t }}</p>
          }
        </div>
      }
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
        <p-button
          [label]="'pickups.move.save' | t"
          icon="pi pi-arrow-right-arrow-left"
          [disabled]="!date() || !time()"
          [loading]="save.busy()"
          (onClick)="save.run()"
          data-testid="pickups.move.save"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    /* out of the flow: the store tab host is a grid with a row gap, and a closed dialog must not add a row (+20px) */
    :host { position: absolute; }
    .form { display: grid; gap: 0.85rem; }
    .field { display: grid; gap: 0.3rem; font-weight: 600; }
    .field small { font-weight: 400; }
    .from { margin: 0; font-weight: 600; }
    .summary { display: grid; gap: 0.25rem; padding: 0.6rem 0.75rem; border-radius: var(--fs-radius-sm); background: var(--fs-surface-muted); }
    .summary p { margin: 0; font-size: 0.88rem; }
    .muted { margin: 0; color: var(--fs-text-muted); }
    .error { margin: 0; color: var(--fs-danger); font-weight: 600; }
  `,
})
export class MovePickupDialogComponent {
  private readonly api = inject(PickupsService);

  readonly visible = model(false);
  readonly storeId = input<string | undefined>();
  readonly pickup = input<Pickup | undefined>();
  readonly timeZone = input('Europe/Zurich');
  readonly myId = input<string | undefined>();
  /** The moved pickup at its new time. */
  readonly moved = output<Pickup>();

  protected readonly date = signal('');
  protected readonly time = signal('');
  protected readonly message = signal('');
  protected readonly errorCode = signal<string | null>(null);

  protected readonly summary = computed(() => moveSummary(this.pickup()?.occupiedSlots ?? [], this.myId()));

  constructor() {
    // every opening starts from the pickup's current date and time
    effect(() => {
      const current = this.pickup();
      if (!this.visible() || !current) return;
      this.date.set(dayOf(current.pickupAt, this.timeZone()));
      this.time.set(timeOf(current.pickupAt, this.timeZone()));
      this.message.set('');
      this.errorCode.set(null);
    });
  }

  protected clearError(): void {
    this.errorCode.set(null);
  }

  protected readonly save = runMutation(
    () => {
      const message = this.message().trim();
      return this.api.movePickup({
        storeId: this.storeId()!,
        pickupAt: this.pickup()!.pickupAt,
        pickupMove: { newPickupAt: zonedToUtc(this.date(), this.time(), this.timeZone()), ...(message ? { message } : {}) },
      });
    },
    {
      successToastKey: 'pickups.move.done',
      errorToast: false,
      onSuccess: (pickup) => {
        this.visible.set(false);
        this.moved.emit(pickup);
      },
      // shown in the dialog (errors.<code>), e.g. PICKUP_TIME_TAKEN or PICKUP_IN_PAST; the values stay
      onError: (error) => this.errorCode.set(error.code),
    },
  );
}
