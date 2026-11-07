import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';

import { CleaningShift, Fsp, FspCleaningService, PickupSlot, SignupResult } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { EligibilityReasonsComponent } from '../../../shared/eligibility-reasons/eligibility-reasons.component';
import { PickupSlotComponent } from '../../../shared/pickup-slot/pickup-slot.component';
import { ServerClock } from '../../pickups/pickups.resource';
import { CLEANING_TIME_ZONE, shiftsByDay } from '../cleaning.util';

/** How many upcoming shifts the Fairteiler page shows (decision 171). */
export const NEXT_SHIFTS = 5;

/** The next upcoming shifts that take people: future, not cancelled, in time order. */
export function nextShifts(shifts: readonly CleaningShift[], now: number, count = NEXT_SHIFTS): CleaningShift[] {
  return [...shifts]
    .filter((shift) => shift.slotCount > 0 && Date.parse(shift.shiftAt) > now)
    .sort((a, b) => a.shiftAt.localeCompare(b.shiftAt))
    .slice(0, count);
}

/**
 * Cleaning card of the Fairteiler page (FS2, decision 171 D3): the next five shifts grouped by day with
 * `fs-pickup-slot`; everyone signs themselves up (with the eligibility reasons when that is not possible) or out.
 * Confirming, removing and editing shifts, the weekly schedule and the team stay on `/food-share-points/:id/cleaning`
 * ("All shifts").
 */
@Component({
  selector: 'fs-fsp-cleaning-card',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    EligibilityReasonsComponent,
    PickupSlotComponent,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="fsp-cleaning-card-title" data-testid="foodSharePoints.detail.cleaning">
      <h2 class="card__title" id="fsp-cleaning-card-title">
        <i class="pi pi-sparkles" aria-hidden="true"></i> {{ 'foodSharePoints.cleaning.title' | t }}
      </h2>
      <fs-data-state
        [state]="shifts"
        skeleton="list"
        [emptyTitle]="'foodSharePoints.cleaning.empty' | t"
        [emptyText]="'foodSharePoints.cleaning.nextEmpty' | t"
        testId="fsp.cleaningCard.state"
        data-testid="fsp.cleaningCard.state"
      >
        <ng-template [fsData]="shifts">
          <div class="days" data-testid="fsp.cleaningCard.list">
            @for (group of days(); track group.day) {
              <div class="day" data-testid="fsp.cleaningCard.day" [attr.data-day]="group.day">
                <h3 class="day__title">{{ group.items[0].shiftAt | fsDate: 'weekday' : tz() }}</h3>
                @for (shift of group.items; track shift.shiftAt) {
                  <div class="shift" data-testid="fsp.cleaningCard.shift" [attr.data-shift-at]="shift.shiftAt">
                    <span class="shift__time">{{ shift.shiftAt | fsDate: 'time' : tz() }}</span>
                    <fs-pickup-slot
                      [slotCount]="shift.slotCount"
                      [slots]="shift.occupiedSlots"
                      [myId]="myId()"
                      [freeClickable]="!mySlot(shift)"
                      (freeClick)="openSignup(shift)"
                      (slotClick)="onSlot(shift, $event)"
                      testId="fsp.cleaningCard.slot"
                    />
                    @if (shift.description) {
                      <span class="muted shift__desc">{{ shift.description }}</span>
                    }
                    @if (mySlot(shift); as mine) {
                      @if (!mine.confirmed) {
                        <span class="pending" data-testid="fsp.cleaningCard.unconfirmed">
                          <i class="pi pi-hourglass" aria-hidden="true"></i> {{ 'foodSharePoints.cleaning.waitingMine' | t }}
                        </span>
                      }
                    }
                  </div>
                }
              </div>
            }
          </div>
        </ng-template>
      </fs-data-state>
      <a class="all" [routerLink]="['/food-share-points', fsp().id, 'cleaning']" data-testid="fsp.cleaning.open">
        {{ 'foodSharePoints.cleaning.all' | t }} <i class="pi pi-arrow-right" aria-hidden="true"></i>
      </a>
    </section>

    <p-dialog
      [visible]="signupOpen()"
      (visibleChange)="signupOpen.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="'foodSharePoints.cleaning.signupTitle' | t"
      [style]="{ width: 'min(28rem, 94vw)' }"
      data-testid="fsp.cleaningCard.signupDialog"
    >
      @if (signupTarget(); as s) {
        <p class="when">{{ s.shiftAt | fsDate: 'dateTimeLong' : tz() }}</p>
        @if (s.description) {
          <p class="muted">{{ s.description }}</p>
        }
        <fs-eligibility-reasons [eligibility]="s.eligibility" />
        @if (s.eligibility?.eligible !== false) {
          <p class="muted">
            {{ (fsp().permissions.mayEdit ? 'foodSharePoints.cleaning.signupManager' : 'foodSharePoints.cleaning.signupText') | t }}
          </p>
        }
      }
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="signupOpen.set(false)" />
        <p-button
          [label]="'foodSharePoints.cleaning.signUp' | t"
          icon="pi pi-check"
          [disabled]="signupTarget()?.eligibility?.eligible === false"
          [loading]="signUp.busy()"
          (onClick)="signUp.run(signupTarget()!)"
          data-testid="fsp.cleaningCard.signupConfirm"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    :host { display: block; }
    .card { display: grid; gap: 0.85rem; min-width: 0; }
    .card__title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    .card__title .pi { color: var(--fs-primary-strong); }
    .days { display: grid; gap: 0.85rem; }
    .day { display: grid; gap: 0.5rem; }
    .day__title { margin: 0; font-size: 0.9rem; font-weight: 650; }
    .shift { display: flex; flex-wrap: wrap; gap: 0.35rem 0.75rem; align-items: center; min-width: 0; }
    .shift__time { font-variant-numeric: tabular-nums; font-weight: 600; min-width: 3rem; }
    .shift__desc { flex-basis: 100%; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.85rem; }
    .pending { display: inline-flex; gap: 0.35rem; align-items: center; color: var(--fs-warning-strong, var(--fs-text-muted)); font-size: 0.85rem; }
    .when { margin: 0 0 0.5rem; font-weight: 600; }
    .all { justify-self: start; display: inline-flex; gap: 0.35rem; align-items: center; font-size: 0.9rem; }
  `,
})
export class FspCleaningCardComponent {
  private readonly api = inject(FspCleaningService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);
  private readonly clock = inject(ServerClock);

  /** The Fairteiler, with cleaning shifts on. */
  readonly fsp = input.required<Fsp>();

  protected readonly tz = computed(() => this.fsp().timeZone ?? CLEANING_TIME_ZONE);
  protected readonly myId = computed(() => this.session.user()?.id);

  protected readonly shifts = loadResource<CleaningShift[], string>({
    params: () => this.fsp().id,
    loader: (fspId) => this.api.listCleaningShifts({ fspId }),
    isEmpty: (list) => nextShifts(list, this.clock.now()).length === 0,
  });

  protected readonly days = computed(() => shiftsByDay(nextShifts(this.shifts.data() ?? [], this.clock.now()), this.tz()));

  protected mySlot(shift: CleaningShift): PickupSlot | undefined {
    return shift.occupiedSlots.find((slot) => slot.user.id === this.myId());
  }

  protected readonly signupOpen = signal(false);
  protected readonly signupTarget = signal<CleaningShift | null>(null);

  protected openSignup(shift: CleaningShift): void {
    this.signupTarget.set(shift);
    this.signupOpen.set(true);
  }

  protected readonly signUp = runMutation(
    (shift: CleaningShift) => this.api.signUpForCleaning({ fspId: this.fsp().id, shiftAt: shift.shiftAt }),
    {
      onSuccess: (result: SignupResult) => {
        this.signupOpen.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t(result.confirmed ? 'foodSharePoints.cleaning.doneConfirmed' : 'foodSharePoints.cleaning.doneUnconfirmed'),
        });
        this.shifts.reload();
      },
    },
  );

  /** Only the own slot reacts here: signing out (managers confirm and remove on the cleaning page). */
  protected onSlot(shift: CleaningShift, slot: PickupSlot): void {
    if (slot.user.id !== this.myId()) return;
    this.confirm.confirm({
      header: this.i18n.t('foodSharePoints.cleaning.signoutTitle'),
      message: this.i18n.t('foodSharePoints.cleaning.signoutText'),
      acceptLabel: this.i18n.t('foodSharePoints.cleaning.signout'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.signOut.run(shift),
    });
  }

  protected readonly signOut = runMutation(
    (shift: CleaningShift) =>
      this.api.signOutOfCleaning({ fspId: this.fsp().id, shiftAt: shift.shiftAt, userId: this.myId()! }),
    { successToastKey: 'foodSharePoints.cleaning.signedOut', onSuccess: () => this.shifts.reload() },
  );
}
