import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { map, tap } from 'rxjs';

import {
  CleaningShift,
  CleaningTeam,
  FoodSharePointsService,
  Fsp,
  FspCleaningService,
  PickupSlot,
  RegularPickup,
  SignupResult,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { DATE_INPUT_IMPORTS } from '../../../shared/date-input';
import { EligibilityReasonsComponent } from '../../../shared/eligibility-reasons/eligibility-reasons.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PickupSlotComponent } from '../../../shared/pickup-slot/pickup-slot.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import {
  WeekdayTimePickerComponent,
  duplicateRows,
} from '../../../shared/weekday-time-picker/weekday-time-picker.component';
import { ServerClock } from '../../pickups/pickups.resource';
import { addDays, dayOf, zonedToUtc } from '../../pickups/pickups.util';
import { CLEANING_TIME_ZONE, parseDay, shiftsByDay, validTime } from '../cleaning.util';

interface SlotTarget {
  shift: CleaningShift;
  slot: PickupSlot;
}

/**
 * /food-share-points/:fspId/cleaning (FS2, decision 148; the cleaning bells and the cleaning rule's messages link
 * here): the Fairteiler's cleaning shifts ("Putzeinsätze") grouped by day with `fs-pickup-slot`. Any verified
 * foodsaver who isn't sleeping signs up; managers (`Fsp.permissions.mayEdit`) confirm, remove, edit a shift, add a
 * one-time shift and edit the weekly schedule. The team card shows everyone with a sign-up and the managers with
 * their shifts in the last 12 months.
 */
@Component({
  selector: 'fs-fsp-cleaning-tab',
  imports: [
    ...DATA_STATE_IMPORTS,
    ...DATE_INPUT_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    EligibilityReasonsComponent,
    FormsModule,
    InputText,
    PageHeaderComponent,
    PickupSlotComponent,
    RouterLink,
    TPipe,
    UserChipComponent,
    WeekdayTimePickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state
      [state]="fsp"
      skeleton="detail"
      backLink="/food-share-points/mine"
      data-testid="fsp.cleaning.page"
    >
      <ng-template [fsData]="fsp" let-f>
        <fs-page-header
          [title]="'foodSharePoints.cleaning.title' | t"
          [subtitle]="f.name"
          icon="pi pi-sparkles"
          [backLink]="['/food-share-points', f.id]"
          [backLabel]="'foodSharePoints.cleaning.back' | t: { name: f.name }"
        />

        @if (!f.cleaningEnabled) {
          <section class="fs-card card" data-testid="fsp.cleaning.disabled">
            <p class="muted">{{ 'foodSharePoints.cleaning.disabled' | t }}</p>
            @if (f.permissions.mayEdit) {
              <div>
                <p-button
                  [label]="'foodSharePoints.cleaning.enable' | t"
                  icon="pi pi-pencil"
                  severity="secondary"
                  [outlined]="true"
                  [routerLink]="['/food-share-points', f.id, 'edit']"
                  data-testid="fsp.cleaning.enable"
                />
              </div>
            }
          </section>
        } @else {
          <div class="cols">
            <div class="stack">
              @if (f.permissions.mayEdit) {
                <div class="toolbar" data-testid="fsp.cleaning.toolbar">
                  <p-button
                    [label]="'foodSharePoints.cleaning.regular' | t"
                    icon="pi pi-replay"
                    severity="secondary"
                    [outlined]="true"
                    size="small"
                    (onClick)="regularOpen.set(true)"
                    data-testid="fsp.cleaning.regular"
                  />
                  <p-button
                    [label]="'foodSharePoints.cleaning.addOneTime' | t"
                    icon="pi pi-plus"
                    size="small"
                    (onClick)="openAdd()"
                    data-testid="fsp.cleaning.add"
                  />
                </div>
              }

              <fs-data-state
                [state]="shifts"
                skeleton="list"
                [emptyTitle]="'foodSharePoints.cleaning.empty' | t"
                [emptyText]="
                  (f.permissions.mayEdit
                    ? 'foodSharePoints.cleaning.emptyManager'
                    : 'foodSharePoints.cleaning.emptyText'
                  ) | t
                "
                testId="fsp.cleaning.state"
                data-testid="fsp.cleaning.state"
              >
                <ng-template [fsData]="shifts">
                  <div class="stack" data-testid="fsp.cleaning.list">
                    @for (group of days(); track group.day) {
                      <section
                        class="fs-card card"
                        data-testid="fsp.cleaning.day"
                        [attr.data-day]="group.day"
                      >
                        <h2 class="card__title">
                          {{ group.items[0].shiftAt | fsDate: 'weekday' : tz }}
                        </h2>
                        @for (shift of group.items; track shift.shiftAt) {
                          <div
                            class="shift"
                            data-testid="fsp.cleaning.shift"
                            [attr.data-shift-at]="shift.shiftAt"
                            [attr.data-slots]="shift.slotCount"
                          >
                            <div class="shift__head">
                              <span class="shift__time">{{
                                shift.shiftAt | fsDate: 'time' : tz
                              }}</span>
                              @if (shift.isRegular) {
                                <span class="badge" data-testid="fsp.cleaning.regularBadge">{{
                                  'foodSharePoints.cleaning.regularBadge' | t
                                }}</span>
                              }
                              @if (shift.description) {
                                <span class="shift__desc" data-testid="fsp.cleaning.description">{{
                                  shift.description
                                }}</span>
                              }
                              @if (f.permissions.mayEdit) {
                                <p-button
                                  icon="pi pi-pencil"
                                  [text]="true"
                                  [rounded]="true"
                                  size="small"
                                  severity="secondary"
                                  [ariaLabel]="'foodSharePoints.cleaning.edit' | t"
                                  (onClick)="openEdit(shift)"
                                  data-testid="fsp.cleaning.edit"
                                />
                              }
                            </div>
                            @if (shift.slotCount === 0) {
                              <p class="muted" data-testid="fsp.cleaning.cancelled">
                                {{ 'foodSharePoints.cleaning.cancelled' | t }}
                              </p>
                            } @else {
                              <fs-pickup-slot
                                [slotCount]="shift.slotCount"
                                [slots]="shift.occupiedSlots"
                                [myId]="myId()"
                                [freeClickable]="!mySlot(shift)"
                                [slotsClickable]="f.permissions.mayEdit"
                                [confirmable]="f.permissions.mayEdit"
                                (freeClick)="openSignup(shift)"
                                (slotClick)="onSlot(shift, $event, f.permissions.mayEdit)"
                                (confirmClick)="openSlot(shift, $event, true)"
                                testId="fsp.cleaning.slot"
                              />
                            }
                            @if (mySlot(shift); as mine) {
                              @if (!mine.confirmed) {
                                <p class="pending" data-testid="fsp.cleaning.unconfirmed">
                                  <i class="pi pi-hourglass" aria-hidden="true"></i
                                  >{{ 'foodSharePoints.cleaning.waitingMine' | t }}
                                </p>
                              }
                            }
                          </div>
                        }
                      </section>
                    }
                  </div>
                </ng-template>
              </fs-data-state>
            </div>

            <aside class="stack">
              <section
                class="fs-card card"
                aria-labelledby="fsp-cleaning-team"
                data-testid="fsp.cleaning.team"
              >
                <h2 class="card__title" id="fsp-cleaning-team">
                  <i class="pi pi-users" aria-hidden="true"></i>
                  {{ 'foodSharePoints.cleaning.team.title' | t }}
                </h2>
                <fs-data-state
                  [state]="team"
                  skeleton="list"
                  [emptyTitle]="'foodSharePoints.cleaning.team.empty' | t"
                  testId="fsp.cleaning.team.state"
                  data-testid="fsp.cleaning.team.state"
                >
                  <ng-template [fsData]="team" let-t>
                    <p class="muted" data-testid="fsp.cleaning.team.total">
                      {{ 'foodSharePoints.cleaning.team.total' | t: { count: t.totalShifts } }}
                    </p>
                    <ul class="members" data-testid="fsp.cleaning.team.list">
                      @for (member of t.members; track member.user.id) {
                        <li
                          class="member"
                          data-testid="fsp.cleaning.team.member"
                          [attr.data-user-id]="member.user.id"
                          [attr.data-manager]="member.manager"
                        >
                          <fs-user-chip
                            [name]="member.user.displayName"
                            [userId]="member.user.id"
                            [avatarUrl]="member.user.avatarUrl"
                            [verified]="member.user.verified"
                            [link]="['/profile', member.user.id]"
                          />
                          <span class="member__stats">
                            @if (member.manager) {
                              <span class="badge" data-testid="fsp.cleaning.team.managerBadge">{{
                                'foodSharePoints.cleaning.team.manager' | t
                              }}</span>
                            }
                            <span data-testid="fsp.cleaning.team.count">{{
                              'foodSharePoints.cleaning.team.shifts'
                                | t: { count: member.shiftCount }
                            }}</span>
                            @if (member.lastShiftAt; as last) {
                              <span class="muted">{{
                                'foodSharePoints.cleaning.team.last'
                                  | t: { date: (last | fsDate: 'date' : tz) }
                              }}</span>
                            }
                            @if (member.upcomingCount > 0) {
                              <span class="muted">{{
                                'foodSharePoints.cleaning.team.upcoming'
                                  | t: { count: member.upcomingCount }
                              }}</span>
                            }
                          </span>
                        </li>
                      }
                    </ul>
                  </ng-template>
                </fs-data-state>
              </section>
            </aside>
          </div>
        }

        <p-dialog
          [visible]="signupOpen()"
          (visibleChange)="signupOpen.set($event)"
          [modal]="true"
          [draggable]="false"
          [closeAriaLabel]="'common.close' | t"
          [header]="'foodSharePoints.cleaning.signupTitle' | t"
          [style]="{ width: 'min(28rem, 94vw)' }"
          data-testid="fsp.cleaning.signupDialog"
        >
          @if (signupTarget(); as s) {
            <p class="when">{{ s.shiftAt | fsDate: 'dateTimeLong' : tz }}</p>
            @if (s.description) {
              <p class="muted">{{ s.description }}</p>
            }
            <fs-eligibility-reasons [eligibility]="s.eligibility" />
            @if (s.eligibility?.eligible !== false) {
              <p class="muted">
                {{
                  (f.permissions.mayEdit
                    ? 'foodSharePoints.cleaning.signupManager'
                    : 'foodSharePoints.cleaning.signupText'
                  ) | t
                }}
              </p>
            }
          }
          <ng-template #footer>
            <p-button
              [label]="'common.cancel' | t"
              severity="secondary"
              [text]="true"
              (onClick)="signupOpen.set(false)"
            />
            <p-button
              [label]="'foodSharePoints.cleaning.signUp' | t"
              icon="pi pi-check"
              [disabled]="signupTarget()?.eligibility?.eligible === false"
              [loading]="signUp.busy()"
              (onClick)="signUp.run(signupTarget()!)"
              data-testid="fsp.cleaning.signupConfirm"
            />
          </ng-template>
        </p-dialog>

        <p-dialog
          [visible]="slotOpen()"
          (visibleChange)="slotOpen.set($event)"
          [modal]="true"
          [draggable]="false"
          [closeAriaLabel]="'common.close' | t"
          [header]="slotTarget()?.slot?.user?.displayName ?? ''"
          [style]="{ width: 'min(28rem, 94vw)' }"
          [focusOnShow]="!focusConfirm()"
          (onShow)="onSlotDialogShown()"
          data-testid="fsp.cleaning.slotDialog"
        >
          @if (slotTarget(); as s) {
            <p class="muted">{{ s.shift.shiftAt | fsDate: 'dateTimeLong' : tz }}</p>
            <p
              class="slot-state"
              [class.slot-state--unconfirmed]="!s.slot.confirmed"
              [attr.data-confirmed]="s.slot.confirmed"
              data-testid="fsp.cleaning.slotState"
            >
              <i
                class="pi"
                [class.pi-check]="s.slot.confirmed"
                [class.pi-hourglass]="!s.slot.confirmed"
                aria-hidden="true"
              ></i>
              {{
                (s.slot.confirmed
                  ? 'foodSharePoints.cleaning.state.confirmed'
                  : 'foodSharePoints.cleaning.state.unconfirmed'
                ) | t
              }}
            </p>
            <div class="dialog-actions">
              @if (!s.slot.confirmed) {
                <p-button
                  [label]="'foodSharePoints.cleaning.confirmSlot' | t"
                  icon="pi pi-check"
                  [loading]="confirmSlot.busy()"
                  (onClick)="confirmSlot.run(s)"
                  data-testid="fsp.cleaning.confirmSlot"
                />
              }
              <p-button
                [label]="'foodSharePoints.cleaning.removeSlot' | t"
                icon="pi pi-user-minus"
                severity="danger"
                [outlined]="true"
                [loading]="removeSlot.busy()"
                (onClick)="askRemove(s)"
                data-testid="fsp.cleaning.removeSlot"
              />
            </div>
          }
        </p-dialog>

        <p-dialog
          [visible]="editOpen()"
          (visibleChange)="editOpen.set($event)"
          [modal]="true"
          [draggable]="false"
          [closeAriaLabel]="'common.close' | t"
          [header]="
            (editAt() ? 'foodSharePoints.cleaning.editTitle' : 'foodSharePoints.cleaning.addTitle')
              | t
          "
          [style]="{ width: 'min(30rem, 94vw)' }"
          data-testid="fsp.cleaning.editDialog"
        >
          <div class="form">
            @if (!editAt()) {
              <label class="field">
                <span>{{ 'foodSharePoints.cleaning.date' | t }}</span>
                <fs-date-input
                  [required]="true"
                  [ngModel]="newDateText()"
                  (ngModelChange)="newDateText.set($event)"
                  [ariaInvalid]="!newDayValid()"
                  testId="fsp.cleaning.edit.date"
                />
              </label>
              <label class="field">
                <span>{{ 'foodSharePoints.cleaning.time' | t }}</span>
                <fs-time-input
                  [required]="true"
                  [ngModel]="newTimeText()"
                  (ngModelChange)="newTimeText.set($event)"
                  [ariaInvalid]="!newTimeValid()"
                  testId="fsp.cleaning.edit.time"
                />
              </label>
            }
            <div class="field">
              <label for="cleaning-edit-slots">{{ 'foodSharePoints.cleaning.slots' | t }}</label>
              <input
                pInputText
                id="cleaning-edit-slots"
                type="number"
                min="0"
                max="50"
                [(ngModel)]="editSlots"
                data-testid="fsp.cleaning.edit.slots"
              />
              <small class="muted">{{ 'foodSharePoints.cleaning.slotsHint' | t }}</small>
            </div>
            <label class="field">
              <span>{{ 'foodSharePoints.cleaning.description' | t }}</span>
              <input
                pInputText
                maxlength="100"
                [(ngModel)]="editDescription"
                data-testid="fsp.cleaning.edit.description"
              />
            </label>
          </div>
          <ng-template #footer>
            <p-button
              [label]="'common.cancel' | t"
              severity="secondary"
              [text]="true"
              (onClick)="editOpen.set(false)"
            />
            <p-button
              [label]="'foodSharePoints.cleaning.save' | t"
              icon="pi pi-check"
              [disabled]="!editAt() && (!newDayValid() || !newTimeValid())"
              [loading]="saveShift.busy()"
              (onClick)="saveShift.run()"
              data-testid="fsp.cleaning.edit.save"
            />
          </ng-template>
        </p-dialog>

        <p-dialog
          [visible]="regularOpen()"
          (visibleChange)="regularOpen.set($event)"
          [modal]="true"
          [draggable]="false"
          [closeAriaLabel]="'common.close' | t"
          [header]="'foodSharePoints.cleaning.regularTitle' | t"
          [style]="{ width: 'min(46rem, 96vw)' }"
          data-testid="fsp.cleaning.regularDialog"
        >
          <p class="muted">{{ 'foodSharePoints.cleaning.regularIntro' | t }}</p>
          <fs-data-state
            [state]="regular"
            skeleton="form"
            testId="fsp.cleaning.regular.state"
            data-testid="fsp.cleaning.regular.state"
          >
            <ng-template [fsData]="regular">
              <fs-weekday-time-picker [(rows)]="schedule" testId="fsp.cleaning.regular" />
            </ng-template>
          </fs-data-state>
          <ng-template #footer>
            <p-button
              [label]="'common.cancel' | t"
              severity="secondary"
              [text]="true"
              (onClick)="regularOpen.set(false)"
            />
            <p-button
              [label]="'foodSharePoints.cleaning.save' | t"
              icon="pi pi-check"
              [disabled]="hasInvalidRows()"
              [loading]="saveRegular.busy()"
              (onClick)="saveRegular.run()"
              data-testid="fsp.cleaning.regular.save"
            />
          </ng-template>
        </p-dialog>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
    }
    .cols {
      display: grid;
      gap: 1.25rem;
    }
    @media (min-width: 60rem) {
      .cols {
        grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
        align-items: start;
      }
    }
    .stack {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    .card {
      display: grid;
      gap: 0.6rem;
      min-width: 0;
    }
    .card__title {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      margin: 0;
      font-size: 1.05rem;
    }
    .card__title .pi {
      color: var(--fs-primary-strong);
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      justify-content: flex-end;
    }
    @media (max-width: 47.99rem) {
      .toolbar {
        justify-content: flex-start;
      }
      .toolbar p-button[data-testid='fsp.cleaning.add'] {
        order: -1;
      }
    }
    .shift {
      display: grid;
      gap: 0.45rem;
      padding: 0.6rem 0;
      border-top: 1px solid var(--fs-border);
    }
    .shift:first-of-type {
      border-top: 0;
    }
    .shift__head {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
    }
    .shift__head p-button[data-testid='fsp.cleaning.edit'] {
      margin-left: auto;
    }
    .shift__time {
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .shift__desc {
      color: var(--fs-text-muted);
      font-size: 0.88rem;
      overflow-wrap: anywhere;
    }
    .badge {
      padding: 0.05rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.75rem;
      font-weight: 600;
    }
    .pending {
      display: flex;
      gap: 0.4rem;
      align-items: center;
      margin: 0;
      color: var(--fs-warning-text);
      font-size: 0.88rem;
      font-weight: 600;
    }
    .muted {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .when {
      margin: 0 0 0.5rem;
      font-weight: 650;
    }
    .members {
      display: grid;
      gap: 0.6rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .member {
      display: grid;
      gap: 0.25rem;
      min-width: 0;
    }
    .member__stats {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 0.75rem;
      align-items: center;
      font-size: 0.88rem;
    }
    .slot-state {
      display: flex;
      gap: 0.4rem;
      align-items: center;
      margin: 0.35rem 0 0;
      color: var(--fs-primary-strong);
      font-weight: 600;
    }
    .slot-state--unconfirmed {
      color: var(--fs-warning-text);
    }
    .dialog-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-top: 1rem;
    }
    .form {
      display: grid;
      gap: 0.85rem;
    }
    .field {
      display: grid;
      gap: 0.3rem;
      font-weight: 600;
    }
    .field small {
      font-weight: 400;
    }
    .invalid {
      border-color: var(--fs-danger);
    }
  `,
})
export class FspCleaningTab {
  private readonly fspApi = inject(FoodSharePointsService);
  private readonly api = inject(FspCleaningService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);
  private readonly clock = inject(ServerClock);
  private readonly document = inject(DOCUMENT);
  private readonly route = inject(ActivatedRoute);

  /** Shifts show in the Fairteiler's zone (decision 155). */
  protected get tz(): string {
    return this.fsp.data()?.timeZone ?? CLEANING_TIME_ZONE;
  }

  protected readonly myId = computed(() => this.session.user()?.id);
  protected readonly fspId = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('fspId') ?? undefined)),
  );

  protected readonly fsp = loadResource<Fsp, string>({
    params: () => this.fspId(),
    loader: (fspId) => this.fspApi.getFoodSharePoint({ fspId }),
    isEmpty: () => false,
  });

  /** The shown Fairteiler with cleaning on: shifts and team load once per Fairteiler. */
  private readonly cleaningFspId = computed(() => {
    const f = this.fsp.data();
    return f?.cleaningEnabled ? f.id : undefined;
  });

  protected readonly shifts = loadResource<CleaningShift[], string>({
    params: () => this.cleaningFspId(),
    loader: (fspId) => this.api.listCleaningShifts({ fspId }),
    isEmpty: (list) => list.length === 0,
  });

  protected readonly team = loadResource<CleaningTeam, string>({
    params: () => this.cleaningFspId(),
    loader: (fspId) => this.api.getCleaningTeam({ fspId }),
    isEmpty: (team) => team.members.length === 0,
  });

  protected readonly days = computed(() => shiftsByDay(this.shifts.data() ?? [], this.tz));

  protected mySlot(shift: CleaningShift): PickupSlot | undefined {
    return shift.occupiedSlots.find((slot) => slot.user.id === this.myId());
  }

  private reload(): void {
    this.shifts.reload();
    this.team.reload();
  }

  // --- sign up / sign out ----------------------------------------------------------------------------------------

  protected readonly signupOpen = signal(false);
  protected readonly signupTarget = signal<CleaningShift | null>(null);

  protected openSignup(shift: CleaningShift): void {
    this.signupTarget.set(shift);
    this.signupOpen.set(true);
  }

  protected readonly signUp = runMutation(
    (shift: CleaningShift) =>
      this.api.signUpForCleaning({ fspId: this.fspId()!, shiftAt: shift.shiftAt }),
    {
      onSuccess: (result: SignupResult) => {
        this.signupOpen.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t(
            result.confirmed
              ? 'foodSharePoints.cleaning.doneConfirmed'
              : 'foodSharePoints.cleaning.doneUnconfirmed',
          ),
        });
        this.reload();
      },
    },
  );

  protected onSlot(shift: CleaningShift, slot: PickupSlot, manager: boolean): void {
    if (slot.user.id === this.myId()) {
      this.confirm.confirm({
        header: this.i18n.t('foodSharePoints.cleaning.signoutTitle'),
        message: this.i18n.t('foodSharePoints.cleaning.signoutText'),
        acceptLabel: this.i18n.t('foodSharePoints.cleaning.signout'),
        rejectLabel: this.i18n.t('common.cancel'),
        acceptButtonStyleClass: 'p-button-danger',
        rejectButtonStyleClass: 'p-button-text p-button-secondary',
        accept: () => void this.signOut.run(shift),
      });
      return;
    }
    if (manager) this.openSlot(shift, slot);
  }

  protected readonly signOut = runMutation(
    (shift: CleaningShift) =>
      this.api.signOutOfCleaning({
        fspId: this.fspId()!,
        shiftAt: shift.shiftAt,
        userId: this.myId()!,
      }),
    { successToastKey: 'foodSharePoints.cleaning.signedOut', onSuccess: () => this.reload() },
  );

  // --- managers: confirm / remove ------------------------------------------------------------------------------

  protected readonly slotOpen = signal(false);
  protected readonly focusConfirm = signal(false);
  protected readonly slotTarget = signal<SlotTarget | null>(null);

  /** Opens the slot dialog; from the ✓ of a split pill, "Bestätigen" gets the focus (no one-click confirm). */
  protected openSlot(shift: CleaningShift, slot: PickupSlot, focusConfirm = false): void {
    this.focusConfirm.set(focusConfirm);
    this.slotTarget.set({ shift, slot });
    this.slotOpen.set(true);
  }

  protected onSlotDialogShown(): void {
    if (!this.focusConfirm()) return;
    this.document
      .querySelector<HTMLElement>('[data-testid="fsp.cleaning.confirmSlot"] button')
      ?.focus();
  }

  protected readonly confirmSlot = runMutation(
    (target: SlotTarget) =>
      this.api.confirmCleaningSignup({
        fspId: this.fspId()!,
        shiftAt: target.shift.shiftAt,
        userId: target.slot.user.id,
      }),
    {
      successToastKey: 'foodSharePoints.cleaning.confirmed',
      onSuccess: () => {
        this.slotOpen.set(false);
        this.reload();
      },
    },
  );

  protected askRemove(target: SlotTarget): void {
    this.confirm.confirm({
      header: this.i18n.t('foodSharePoints.cleaning.removeTitle', {
        name: target.slot.user.displayName,
      }),
      message: this.i18n.t('foodSharePoints.cleaning.removeText'),
      acceptLabel: this.i18n.t('foodSharePoints.cleaning.removeSlot'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.removeSlot.run(target),
    });
  }

  protected readonly removeSlot = runMutation(
    (target: SlotTarget) =>
      this.api.signOutOfCleaning({
        fspId: this.fspId()!,
        shiftAt: target.shift.shiftAt,
        userId: target.slot.user.id,
      }),
    {
      successToastKey: 'foodSharePoints.cleaning.removed',
      onSuccess: () => {
        this.slotOpen.set(false);
        this.reload();
      },
    },
  );

  // --- managers: edit / add a shift ------------------------------------------------------------------------------

  protected readonly editOpen = signal(false);
  protected readonly editAt = signal<string | null>(null);
  protected editSlots = 2;
  protected editDescription = '';
  protected readonly newDateText = signal('');
  protected readonly newTimeText = signal('10:00');
  protected readonly newDayValid = computed(() => parseDay(this.newDateText()) !== null);
  protected readonly newTimeValid = computed(() => validTime(this.newTimeText()));

  protected openEdit(shift: CleaningShift): void {
    this.editAt.set(shift.shiftAt);
    this.editSlots = shift.slotCount;
    this.editDescription = shift.description ?? '';
    this.editOpen.set(true);
  }

  protected openAdd(): void {
    this.editAt.set(null);
    this.editSlots = 2;
    this.editDescription = '';
    this.newDateText.set(addDays(dayOf(this.clock.now(), this.tz), 1));
    this.newTimeText.set('10:00');
    this.editOpen.set(true);
  }

  protected readonly saveShift = runMutation(
    () => {
      const shiftAt =
        this.editAt() ??
        zonedToUtc(parseDay(this.newDateText())!, this.newTimeText().trim(), this.tz);
      const description = this.editDescription.trim();
      return this.api.editCleaningShift({
        fspId: this.fspId()!,
        shiftAt,
        cleaningShiftEdit: {
          slotCount: Math.min(50, Math.max(0, Number(this.editSlots) || 0)),
          ...(description ? { description } : {}),
        },
      });
    },
    {
      successToastKey: 'foodSharePoints.cleaning.saved',
      onSuccess: () => {
        this.editOpen.set(false);
        this.reload();
      },
    },
  );

  // --- managers: weekly schedule ---------------------------------------------------------------------------------

  protected readonly regularOpen = signal(false);
  protected readonly schedule = signal<RegularPickup[]>([]);
  protected readonly hasInvalidRows = computed(
    () =>
      duplicateRows(this.schedule()).size > 0 ||
      this.schedule().some((row) => !validTime(row.startTime)),
  );

  protected readonly regular = loadResource<RegularPickup[], string>({
    params: () => (this.regularOpen() ? this.cleaningFspId() : undefined),
    // the editor works on a copy (schedule); a reload replaces it
    loader: (fspId) =>
      this.api
        .getRegularCleaningShifts({ fspId })
        .pipe(
          tap((rows) =>
            this.schedule.set(
              rows.map((row) => ({ ...row, startTime: row.startTime.slice(0, 5) })),
            ),
          ),
        ),
  });

  protected readonly saveRegular = runMutation(
    () =>
      this.api.replaceRegularCleaningShifts({
        fspId: this.fspId()!,
        regularPickup: this.schedule().map((row) => ({
          weekday: row.weekday,
          startTime: row.startTime,
          slotCount: row.slotCount,
          ...(row.description?.trim() ? { description: row.description.trim() } : {}),
        })),
      }),
    {
      successToastKey: 'foodSharePoints.cleaning.regularSaved',
      onSuccess: () => {
        this.regularOpen.set(false);
        this.reload();
      },
    },
  );
}
