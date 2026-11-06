import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { concat, tap } from 'rxjs';

import {
  AutomationService,
  Pickup,
  PickupDayConversation,
  PickupEligibilityReason,
  PickupSlot,
  PickupsService,
  RegularPickup,
  UserTeamStatus,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimeFormatter, DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { DATE_INPUT_IMPORTS } from '../../../shared/date-input';
import { PickupSlotComponent } from '../../../shared/pickup-slot/pickup-slot.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { WeekdayTimePickerComponent, duplicateRows } from '../../../shared/weekday-time-picker/weekday-time-picker.component';
import { BlockedReasonDialogComponent } from '../components/blocked-reason-dialog.component';
import { RuleSummaryCardComponent } from '../components/rule-summary-card.component';
import { PickupSignupDialogComponent, SignupDone, SignupTarget } from '../components/signup-dialog.component';
import { PickupSummaryComponent } from '../components/pickup-summary.component';
import { SlotDetailDialogComponent, SlotDetailMode } from '../components/slot-detail-dialog.component';
import { ServerClock } from '../pickups.resource';
import { lockingBlocks } from '../rule-sentences';
import { SlotSummary, addDays, dayOf, groupByDay, pickupSummary, unconfirmedCount, zonedToUtc } from '../pickups.util';
import { StorePageContext } from '../../stores/store-page.context';

type Range = 'twoWeeks' | 'all';

interface SlotTarget {
  pickup: Pickup;
  slot: PickupSlot;
}

/**
 * /stores/:storeId/pickups (PU1–PU5): pickups grouped by compact day cards (date tile, one column, decision 165) with
 * their slots. Free slot → eligibility + sign-up; own slot → sign out; another person's slot → slot dialog (rich for
 * managers and co-pickups, short for the team); managers: confirm/remove others (message), edit a pickup
 * (slots/description), add a one-time pickup and edit the regular schedule. "Chat öffnen" finds or creates the named
 * day chat when someone else is signed up that day.
 */
@Component({
  selector: 'fs-store-pickups-tab',
  imports: [
    ...DATE_INPUT_IMPORTS,
    ...DATA_STATE_IMPORTS,
    BlockedReasonDialogComponent,
    Button,
    DateTimePipe,
    Dialog,
    FormsModule,
    InputText,
    PickupSignupDialogComponent,
    PickupSlotComponent,
    PickupSummaryComponent,
    RequiredMessageDialogComponent,
    RouterLink,
    RuleSummaryCardComponent,
    SlotDetailDialogComponent,
    TPipe,
    WeekdayTimePickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toolbar">
      <div class="chips" role="group" [attr.aria-label]="'pickups.tab.range' | t">
        @for (r of ranges; track r) {
          <button
            type="button"
            class="chip"
            [class.chip--active]="range() === r"
            [attr.aria-pressed]="range() === r"
            (click)="range.set(r)"
            [attr.data-testid]="'stores.pickups.range.' + r"
          >
            {{ 'pickups.tab.range.' + r | t }}
          </button>
        }
        <!-- managers: only the pickups with unconfirmed sign-ups; hidden at 0 unless pressed -->
        @if (ctx.may('mayConfirmPickups') && (pendingCount() > 0 || pendingOnly())) {
          <button
            type="button"
            class="chip chip--pending"
            [class.chip--active]="pendingOnly()"
            [attr.aria-pressed]="pendingOnly()"
            [attr.data-count]="pendingCount()"
            (click)="pendingOnly.set(!pendingOnly())"
            data-testid="stores.pickups.range.pending"
          >
            <i class="pi pi-hourglass" aria-hidden="true"></i>{{ 'pickups.tab.range.pending' | t: { count: pendingCount() } }}
          </button>
        }
      </div>
      <div class="actions">
        @if (ctx.may('maySeePickupHistory')) {
          <p-button
            [label]="'pickups.history.link' | t"
            icon="pi pi-history"
            severity="secondary"
            [text]="true"
            size="small"
            routerLink="history"
            data-testid="stores.pickups.history"
          />
        }
        @if (ctx.may('mayEditPickups')) {
          <p-button
            [label]="'pickups.tab.regular' | t"
            icon="pi pi-replay"
            severity="secondary"
            [outlined]="true"
            size="small"
            (onClick)="openRegular()"
            data-testid="stores.pickups.regular"
          />
          <p-button
            [label]="'pickups.tab.addOneTime' | t"
            icon="pi pi-plus"
            size="small"
            (onClick)="openAdd()"
            data-testid="stores.pickups.add"
          />
        }
      </div>
    </div>

    @if (ctx.may('maySeePickups') && ctx.storeId(); as storeId) {
      <!-- decision 167 S2: the store's active rules for members (hidden while they are off) -->
      <fs-rule-summary-card [storeId]="storeId" [timeZone]="tz()" />
    }

    <fs-data-state
      [state]="pickups"
      skeleton="list"
      [emptyTitle]="'pickups.tab.empty' | t"
      [emptyText]="'pickups.tab.emptyText' | t"
      testId="stores.pickups.state"
      data-testid="stores.pickups.state"
    >
      <ng-template [fsData]="pickups">
        <div class="stack" data-testid="stores.pickups.list">
          @if (pendingOnly() && !days().length) {
            <p class="muted" data-testid="stores.pickups.pendingEmpty">{{ 'pickups.tab.pendingEmpty' | t }}</p>
          }
          @for (group of days(); track group.day) {
            @let first = group.items[0];
            <section
              class="day"
              [class.day--today]="group.day === today()"
              data-testid="stores.pickups.day"
              [attr.data-day]="group.day"
              [attr.aria-labelledby]="'stores-pickups-day-' + group.day"
            >
              <div class="date" aria-hidden="true">
                <span class="date__wd">{{ first.pickupAt | fsDate: 'weekdayShort' : tz() }}</span>
                <span class="date__d">{{ first.pickupAt | fsDate: 'dayNumber' : tz() }}</span>
                <span class="date__m">{{ first.pickupAt | fsDate: 'monthShort' : tz() }}</span>
              </div>
              <div class="day__body">
              <div class="day__head">
                <h2 class="day__title" tabindex="-1" [id]="'stores-pickups-day-' + group.day">{{ first.pickupAt | fsDate: 'weekday' : tz() }}</h2>
                @if (mayOpenDayChat(group.items)) {
                  <!-- decision 165: text on desktop, icon only on phones; the text is the accessible name -->
                  <button
                    type="button"
                    class="day__chat"
                    [attr.aria-label]="'pickups.dayChat.open' | t"
                    [attr.title]="'pickups.dayChat.open' | t"
                    [attr.aria-describedby]="'stores-pickups-day-' + group.day"
                    [disabled]="openDayChat.busy()"
                    (click)="openDayChat.run(group.day, first.pickupAt)"
                    data-testid="stores.pickups.dayChat"
                  >
                    <i class="pi pi-comments" aria-hidden="true"></i>
                    <span class="day__chatLabel" aria-hidden="true">{{ 'pickups.dayChat.open' | t }}</span>
                  </button>
                }
              </div>
              @for (pickup of group.items; track pickup.pickupAt) {
                <div
                  class="pickup"
                  data-testid="stores.pickups.pickup"
                  [attr.data-pickup-at]="pickup.pickupAt"
                  [attr.data-slots]="pickup.slotCount"
                >
                  <div class="pickup__head">
                    <span class="pickup__time">{{ pickup.pickupAt | fsDate: 'time' : tz() }}</span>
                    @if (pickup.isRegular) {
                      <span
                        class="pickup__reg"
                        role="img"
                        [attr.aria-label]="'pickups.tab.regularBadge' | t"
                        [attr.title]="'pickups.tab.regularBadge' | t"
                        data-testid="stores.pickups.regularBadge"
                      ><i class="pi pi-replay" aria-hidden="true"></i></span>
                    }
                    @if (pickup.description) {
                      <span class="pickup__desc" [attr.title]="pickup.description" data-testid="stores.pickups.description">{{ pickup.description }}</span>
                    }
                    @if (pickup.slotCount > 0 && pickup.occupiedSlots.length > 0) {
                      @let summary = summaryOf(pickup);
                      <fs-pickup-summary
                        [confirmed]="summary.confirmed"
                        [unconfirmed]="summary.unconfirmed"
                        [total]="summary.total"
                        testId="stores.pickups.summary"
                      >
                        @if (ctx.may('mayConfirmPickups') && summary.unconfirmed >= 2) {
                          <p-button
                            [label]="'pickups.tab.confirmAll' | t: { count: summary.unconfirmed }"
                            icon="pi pi-check-circle"
                            [text]="true"
                            size="small"
                            [loading]="confirmAll.busy()"
                            (onClick)="askConfirmAll(pickup)"
                            data-testid="stores.pickups.confirmAll"
                          />
                        }
                      </fs-pickup-summary>
                    }
                    @if (ctx.may('mayEditPickups')) {
                      <p-button
                        icon="pi pi-pencil"
                        [text]="true"
                        [rounded]="true"
                        size="small"
                        severity="secondary"
                        [ariaLabel]="'pickups.tab.edit' | t"
                        (onClick)="openEdit(pickup)"
                        data-testid="stores.pickups.edit"
                      />
                    }
                  </div>
                  @if (pickup.slotCount === 0) {
                    <p class="pickup__cancelled" data-testid="stores.pickups.cancelled">{{ 'pickups.tab.cancelled' | t }}</p>
                  } @else {
                    <fs-pickup-slot
                      [slotCount]="pickup.slotCount"
                      [slots]="pickup.occupiedSlots"
                      [myId]="myId()"
                      [freeClickable]="!isSignedUp(pickup)"
                      [slotsClickable]="slotsClickable(pickup)"
                      [confirmable]="ctx.may('mayConfirmPickups')"
                      [blocked]="isBlocked(pickup)"
                      [blockedRuleCount]="blockedCount(pickup)"
                      (blockedClick)="openBlocked(pickup)"
                      (freeClick)="openSignup(pickup)"
                      (slotClick)="onSlot(pickup, $event)"
                      (confirmClick)="openSlot(pickup, $event, true)"
                      testId="stores.pickups.slot"
                    />
                  }
                  @if (pickup.mayAddExtraSlot) {
                    <!-- AU6: the first person of the pickup may add one extra slot -->
                    <p-button
                      [label]="'pickups.tab.addExtraSlot' | t"
                      icon="pi pi-plus"
                      size="small"
                      [outlined]="true"
                      [loading]="addExtraSlot.busy()"
                      (onClick)="addExtraSlot.run(pickup)"
                      data-testid="stores.pickups.addExtraSlot"
                    />
                  }
                  @for (extra of pickup.extraSlots ?? []; track extra.addedAt) {
                    <p class="pickup__extra" data-testid="stores.pickups.extraSlot">{{ 'pickups.tab.extraSlotBy' | t: { name: extra.byUser.displayName } }}</p>
                  }
                  @if (mySlot(pickup); as mine) {
                    @if (!mine.confirmed) {
                      <p class="pending" data-testid="stores.pickups.unconfirmed">
                        <i class="pi pi-hourglass" aria-hidden="true"></i>{{ 'pickups.tab.waitingMine' | t }}
                      </p>
                    }
                    @if (mine.linkMissingUntil; as until) {
                      <!-- decision 167 S3: the linked pickup was signed out; choose a new one before the deadline -->
                      <button
                        type="button"
                        class="link-missing"
                        aria-haspopup="dialog"
                        [attr.aria-label]="'pickups.linked.missingLabel' | t: { until: (until | fsDate: 'dateTime' : tz()) }"
                        (click)="openRepair(pickup, until)"
                        data-testid="stores.pickups.linkMissing"
                      >
                        <i class="pi pi-hourglass" aria-hidden="true"></i>
                        <span aria-hidden="true">{{ 'pickups.linked.missing' | t: { until: (until | fsDate: 'dateTime' : tz()) } }}</span>
                      </button>
                    }
                  }
                  @for (slot of othersMissingLink(pickup); track slot.user.id) {
                    <p class="pending" data-testid="stores.pickups.linkMissingOther">
                      <i class="pi pi-hourglass" aria-hidden="true"></i>
                      {{ 'pickups.linked.missingOther' | t: { name: slot.user.displayName, until: (slot.linkMissingUntil! | fsDate: 'dateTime' : tz()) } }}
                    </p>
                  }
                </div>
              }
              </div>
            </section>
          }
        </div>
      </ng-template>
    </fs-data-state>

    <fs-pickup-signup-dialog [(visible)]="signupOpen" [target]="signupTarget()" (signedUp)="afterSignup($event)" />

    <fs-blocked-reason-dialog
      [(visible)]="blockedOpen"
      [target]="signupTarget()"
      (jump)="jumpTo($event)"
      (seeRules)="ruleCard()?.reveal()"
      (signUp)="signupOpen.set(true)"
      (linked)="signupOpen.set(true)"
    />

    <fs-slot-detail-dialog
      [(visible)]="slotOpen"
      [storeId]="ctx.storeId() ?? undefined"
      [pickupAt]="slotTarget()?.pickup?.pickupAt"
      [slot]="slotTarget()?.slot"
      [mode]="slotMode()"
      [timeZone]="tz()"
      [mayConfirm]="ctx.may('mayConfirmPickups')"
      [mayRemove]="ctx.may('mayEditPickups')"
      [confirmBusy]="confirmSlot.busy()"
      [focusConfirm]="focusConfirm()"
      (confirmed)="confirmSlot.run(slotTarget()!)"
      (remove)="askRemove()"
      (shown)="onSlotDialogShown()"
    />

    <fs-required-message-dialog
      [(visible)]="removeOpen"
      [title]="'pickups.tab.removeTitle' | t: { name: slotTarget()?.slot?.user?.displayName ?? '' }"
      [text]="removeText()"
      [confirmLabel]="'pickups.tab.removeSlot' | t"
      severity="danger"
      [busy]="removeSlot.busy()"
      (confirm)="removeSlot.run($event)"
      testId="stores.pickups.removeDialog"
    />

    <p-dialog
      [visible]="editOpen()"
      (visibleChange)="editOpen.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="(editAt() ? 'pickups.tab.editTitle' : 'pickups.tab.addTitle') | t"
      [style]="{ width: 'min(30rem, 94vw)' }"
      data-testid="stores.pickups.editDialog"
    >
      <div class="form">
        @if (!editAt()) {
          <label class="field">
            <span>{{ 'pickups.tab.date' | t }}</span>
            <fs-date-input [(ngModel)]="newDate" testId="stores.pickups.edit.date" />
          </label>
          <label class="field">
            <span>{{ 'pickups.tab.time' | t }} ({{ tz() }})</span>
            <fs-time-input [(ngModel)]="newTime" testId="stores.pickups.edit.time" />
          </label>
        }
        <div class="field">
          <label for="pickup-edit-slots">{{ 'pickups.tab.slots' | t }}</label>
          <input
            pInputText
            id="pickup-edit-slots"
            type="number"
            min="0"
            max="50"
            [(ngModel)]="editSlots"
            data-testid="stores.pickups.edit.slots"
          />
          <small class="muted">{{ 'pickups.tab.slotsHint' | t }}</small>
        </div>
        <label class="field">
          <span>{{ 'pickups.tab.description' | t }}</span>
          <input pInputText maxlength="100" [(ngModel)]="editDescription" data-testid="stores.pickups.edit.description" />
        </label>
      </div>
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="editOpen.set(false)" />
        <p-button
          [label]="'pickups.tab.save' | t"
          icon="pi pi-check"
          [loading]="savePickup.busy()"
          (onClick)="savePickup.run()"
          data-testid="stores.pickups.edit.save"
        />
      </ng-template>
    </p-dialog>

    <p-dialog
      [visible]="regularOpen()"
      (visibleChange)="regularOpen.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="'pickups.regular.title' | t"
      [style]="{ width: 'min(46rem, 96vw)' }"
      data-testid="stores.pickups.regularDialog"
    >
      <p class="muted">{{ 'pickups.regular.intro' | t: { days: ctx.data()?.prefetchDays ?? 14 } }}</p>
      <fs-data-state [state]="regular" skeleton="form" testId="stores.regular.state" data-testid="stores.regular.state">
        <ng-template [fsData]="regular">
          <fs-weekday-time-picker [(rows)]="schedule" testId="stores.regular" />
        </ng-template>
      </fs-data-state>
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="regularOpen.set(false)" />
        <p-button
          [label]="'pickups.tab.save' | t"
          icon="pi pi-check"
          [disabled]="hasDuplicates()"
          [loading]="saveRegular.busy()"
          (onClick)="saveRegular.run()"
          data-testid="stores.regular.save"
        />
      </ng-template>
    </p-dialog>
  `,
  styleUrl: '../../stores/stores.scss',
  styles: `
    .toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.75rem; margin-bottom: 1rem; }
    .chips, .actions { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; }
    /* phones: the actions start left under the range chips, the primary one first (M7 R2 action placement) */
    @media (max-width: 47.99rem) {
      .actions { width: 100%; }
      .actions p-button[data-testid='stores.pickups.add'] { order: -1; }
    }
    .chip {
      min-height: var(--fs-target-sm);
      padding: 0.35rem 0.85rem; border: 1px solid var(--fs-border); border-radius: 999px; background: var(--fs-surface);
      color: inherit; font: inherit; font-size: 0.88rem; cursor: pointer;
    }
    .chip--active { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); font-weight: 650; }
    .chip--pending { display: inline-flex; align-items: center; gap: 0.35rem; }
    .chip--pending .pi { color: var(--fs-warning-text); font-size: 0.85em; }
    /* compact day cards (decision 165, ui-feedback-batch-2-proposal §3): one column on all widths */
    .stack { display: grid; gap: 0.6rem; }
    .day {
      --fs-date-tile: 3.25rem;
      display: grid; grid-template-columns: var(--fs-date-tile) minmax(0, 1fr); gap: 0 0.75rem;
      padding: 0.55rem 0.65rem 0.55rem 0.55rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md);
      background: var(--fs-surface); box-shadow: var(--fs-shadow-sm);
    }
    .day--today { border-color: color-mix(in srgb, var(--fs-primary) 45%, var(--fs-border)); }
    .date {
      display: grid; justify-items: center; align-content: start; align-self: start; width: var(--fs-date-tile);
      padding: 0.3rem 0 0.35rem; border-radius: var(--fs-radius-sm); background: var(--fs-surface-muted); line-height: 1.05;
    }
    .date__wd { font-size: 0.68rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--fs-text-muted); }
    .date__d { font-size: 1.35rem; font-weight: 750; letter-spacing: -0.02em; font-variant-numeric: tabular-nums; }
    .date__m { font-size: 0.68rem; font-weight: 600; color: var(--fs-text-muted); }
    .day--today .date { background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .day--today .date__wd, .day--today .date__m { color: var(--fs-primary-strong); }
    .day__body { display: grid; min-width: 0; }
    .day__head { display: flex; align-items: center; gap: 0.5rem; min-height: 1.9rem; }
    .day__title { margin: 0; font-size: 0.95rem; font-weight: 650; }
    .day__chat {
      display: inline-flex; align-items: center; gap: 0.35rem; margin-left: auto; min-height: 1.9rem;
      padding: 0.2rem 0.65rem 0.2rem 0.5rem; border: 0; border-radius: 999px; background: transparent;
      color: var(--fs-primary-strong); font: inherit; font-size: 0.82rem; font-weight: 600; cursor: pointer;
    }
    .day__chat:hover { background: var(--fs-primary-soft); }
    .day__chat:disabled { opacity: 0.6; cursor: default; }
    .pickup { display: grid; gap: 0.3rem; padding: 0.35rem 0; border-top: 1px solid var(--fs-border); }
    .pickup:first-of-type { border-top: 0; }
    .pickup__head { display: flex; flex-wrap: wrap; gap: 0.2rem 0.6rem; align-items: center; min-height: 1.9rem; }
    .pickup__head fs-pickup-summary { margin-left: auto; }
    .pickup__head p-button[data-testid='stores.pickups.edit'] { margin-left: auto; }
    .pickup__head fs-pickup-summary + p-button[data-testid='stores.pickups.edit'] { margin-left: 0; }
    .pickup__reg {
      display: inline-grid; place-items: center; width: 1.3rem; height: 1.3rem; border-radius: 999px;
      background: var(--fs-surface-muted); color: var(--fs-text-muted);
    }
    .pickup__reg .pi { font-size: 0.7rem; }
    .pickup__cancelled { margin: 0; color: var(--fs-text-muted); font-size: 0.85rem; font-style: italic; }
    .pickup__extra { margin: 0; color: var(--fs-text-muted); font-size: 0.78rem; }
    @media (max-width: 47.99rem) {
      .day { --fs-date-tile: 2.75rem; gap: 0 0.6rem; }
      .date__d { font-size: 1.15rem; }
      .day__chat { justify-content: center; min-width: var(--fs-target-sm); min-height: var(--fs-target-sm); padding: 0; }
      .day__chatLabel { display: none; }
      .pickup__desc { white-space: normal; }
    }
    /* phones: the summary takes its own line under the time, the pencil stays on the first line */
    @media (max-width: 47.99rem) {
      .pickup__head fs-pickup-summary { flex-basis: 100%; order: 1; }
    }
    .pickup__time { font-weight: 700; font-variant-numeric: tabular-nums; }
    .pickup__desc { min-width: 0; max-width: 100%; overflow: hidden; color: var(--fs-text-muted); font-size: 0.85rem; text-overflow: ellipsis; white-space: nowrap; }
    .pending { display: flex; gap: 0.35rem; align-items: center; margin: 0; color: var(--fs-warning-text); font-size: 0.82rem; font-weight: 600; }
    .link-missing {
      display: inline-flex; gap: 0.35rem; align-items: center; align-self: start; min-height: var(--fs-target-sm, 2.75rem);
      padding: 0.2rem 0.75rem; border: 1px solid var(--fs-warning-border, var(--fs-border-strong)); border-radius: 999px;
      background: var(--fs-surface-muted); color: var(--fs-warning-text); font: inherit; font-size: 0.82rem; font-weight: 600;
      cursor: pointer;
    }
    .link-missing:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
    .muted { margin: 0; color: var(--fs-text-muted); }
    .form { display: grid; gap: 0.85rem; }
    .field { display: grid; gap: 0.3rem; font-weight: 600; }
    .field small { font-weight: 400; }
  `,
})
export class StorePickupsTab {
  protected readonly ctx = inject(StorePageContext);
  private readonly api = inject(PickupsService);
  private readonly automationApi = inject(AutomationService);

  protected readonly addExtraSlot = runMutation(
    (pickup: Pickup) => this.automationApi.addExtraSlot({ storeId: this.ctx.storeId()!, pickupAt: pickup.pickupAt }),
    { successToastKey: 'pickups.tab.extraSlotAdded', onSuccess: () => this.reload() },
  );
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly messages = inject(MessageService);
  private readonly clock = inject(ServerClock);
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly dates = inject(DateTimeFormatter);

  protected readonly ranges: Range[] = ['twoWeeks', 'all'];
  protected readonly range = signal<Range>('all');
  protected readonly myId = computed(() => this.session.user()?.id);
  protected readonly tz = computed(() => this.ctx.timeZone());
  /** Today in the store's time zone (the "today" day card). */
  protected readonly today = computed(() => dayOf(this.clock.now(), this.tz()));
  /** Team member of the store (managers included): may open slots (short dialog) and the day chat. */
  protected readonly isTeam = computed(() => {
    const status = this.ctx.permissions()?.teamStatus;
    return status === UserTeamStatus.Member || status === UserTeamStatus.Coordinator;
  });

  protected readonly pickups = loadResource<Pickup[], string>({
    params: () => (this.ctx.may('maySeePickups') ? (this.ctx.storeId() ?? undefined) : undefined),
    loader: (storeId) => this.api.listPickups({ storeId }),
    isEmpty: (list) => list.length === 0,
  });

  /** Managers: show only pickups with unconfirmed sign-ups ("Unbestätigt (n)" chip). */
  protected readonly pendingOnly = signal(false);

  /** Day groups of the selected range; "next 2 weeks" counts from the server's today in the store's time zone. */
  private readonly rangeDays = computed(() => {
    const groups = groupByDay(this.pickups.data() ?? [], this.tz());
    if (this.range() === 'all' || !groups.length) return groups;
    const until = addDays(dayOf(this.clock.now(), this.tz()), 14);
    return groups.filter((group) => group.day < until);
  });

  /** Unconfirmed sign-ups in the selected range (the chip's count). */
  protected readonly pendingCount = computed(() => unconfirmedCount(this.rangeDays().flatMap((group) => group.items)));

  protected readonly days = computed(() => {
    const groups = this.rangeDays();
    if (!this.pendingOnly()) return groups;
    return groups
      .map((group) => ({ ...group, items: group.items.filter((pickup) => unconfirmedCount([pickup]) > 0) }))
      .filter((group) => group.items.length > 0);
  });

  protected summaryOf(pickup: Pickup): SlotSummary {
    return pickupSummary(pickup.occupiedSlots, pickup.slotCount);
  }

  protected mySlot(pickup: Pickup): PickupSlot | undefined {
    return pickup.occupiedSlots.find((slot) => slot.user.id === this.myId());
  }

  protected isSignedUp(pickup: Pickup): boolean {
    return !!this.mySlot(pickup);
  }

  private isManager(): boolean {
    return this.ctx.may('mayConfirmPickups') || this.ctx.may('mayEditPickups');
  }

  /** Slots open the dialog for managers, team members and people signed up for this pickup (decision 165). */
  protected slotsClickable(pickup: Pickup): boolean {
    return this.isManager() || this.isTeam() || this.isSignedUp(pickup);
  }

  // --- day chat (PU3, decision 165) ------------------------------------------------------------------------------

  /** Someone other than me is signed up that day, and I'm in the team or signed up that day myself. */
  protected mayOpenDayChat(items: Pickup[]): boolean {
    const me = this.myId();
    const others = items.some((pickup) => pickup.occupiedSlots.some((slot) => slot.user.id !== me));
    return others && (this.isTeam() || items.some((pickup) => this.isSignedUp(pickup)));
  }

  protected readonly openDayChat = runMutation(
    (day: string, pickupAt: string) =>
      this.api.openPickupDayConversation({
        storeId: this.ctx.storeId()!,
        date: day,
        pickupDayConversationOpen: {
          name: this.i18n
            .t('pickups.dayChat.name', {
              store: this.ctx.data()?.name ?? '',
              date: this.dates.format(pickupAt, 'dayShort', this.tz()),
            })
            .slice(0, 200),
        },
      }),
    {
      onSuccess: (chat: PickupDayConversation) => {
        this.messages.add({ severity: 'success', summary: this.i18n.t('pickups.dayChat.opened', { name: chat.name }), life: 3000 });
        void this.router.navigate(['/messages', chat.conversationId]);
      },
    },
  );

  private reload(): void {
    this.pickups.reload();
  }

  // --- sign up / sign out --------------------------------------------------------------------------------------

  protected readonly signupOpen = signal(false);
  protected readonly signupTarget = signal<SignupTarget | null>(null);

  protected openSignup(pickup: Pickup): void {
    const store = this.ctx.data();
    if (!store) return;
    this.signupTarget.set({ storeId: store.id, storeName: store.name, pickupAt: pickup.pickupAt, timeZone: this.tz() });
    this.signupOpen.set(true);
  }

  /** "Verknüpfung fehlt": the same dialog books a new linked pickup for the own sign-up (decision 167 S3). */
  protected openRepair(pickup: Pickup, until: string): void {
    const store = this.ctx.data();
    if (!store) return;
    this.signupTarget.set({
      storeId: store.id,
      storeName: store.name,
      pickupAt: pickup.pickupAt,
      timeZone: this.tz(),
      linkMissingUntil: until,
    });
    this.signupOpen.set(true);
  }

  /** Other people's sign-ups waiting for a new linked pickup (store managers see them). */
  protected othersMissingLink(pickup: Pickup): PickupSlot[] {
    return pickup.occupiedSlots.filter((slot) => slot.user.id !== this.myId() && !!slot.linkMissingUntil);
  }

  // --- blocked for this person (decision 167 S2) -------------------------------------------------------------------

  protected readonly blockedOpen = signal(false);
  protected readonly ruleCard = viewChild(RuleSummaryCardComponent);

  /** Reasons that block a free place for the viewer (none for managers or when already signed up / full). */
  private blockingReasons(pickup: Pickup): number {
    const e = pickup.eligibility;
    if (!e || e.eligible || this.ctx.may('mayConfirmPickups') || this.isSignedUp(pickup)) return 0;
    const others = e.reasons.filter(
      (reason) =>
        reason !== PickupEligibilityReason.AutomationRule &&
        reason !== PickupEligibilityReason.AlreadySignedUp &&
        reason !== PickupEligibilityReason.NoFreeSlot,
    ).length;
    // decision 167 S3: a missing linked pickup alone is no lock, the sign-up dialog books it in the same step
    const rules = e.reasons.includes(PickupEligibilityReason.AutomationRule) ? lockingBlocks(e.ruleBlocks).length : 0;
    return others + rules;
  }

  protected isBlocked(pickup: Pickup): boolean {
    return this.blockingReasons(pickup) > 0;
  }

  protected blockedCount(pickup: Pickup): number {
    return this.blockingReasons(pickup);
  }

  protected openBlocked(pickup: Pickup): void {
    const store = this.ctx.data();
    if (!store) return;
    this.signupTarget.set({ storeId: store.id, storeName: store.name, pickupAt: pickup.pickupAt, timeZone: this.tz() });
    this.blockedOpen.set(true);
  }

  /** "Zum … springen": shows all pickups and scrolls to the first day on or after `at`. */
  protected jumpTo(at: string): void {
    this.range.set('all');
    this.pendingOnly.set(false);
    const day = dayOf(at, this.tz());
    setTimeout(() => {
      const target = [...this.document.querySelectorAll<HTMLElement>('[data-testid="stores.pickups.day"]')].find(
        (element) => (element.dataset['day'] ?? '') >= day,
      );
      target?.scrollIntoView({ block: 'start', behavior: 'smooth' });
      target?.querySelector<HTMLElement>('h2')?.focus();
    });
  }

  protected afterSignup(result: SignupDone): void {
    const store = this.ctx.data();
    const linked = result.linkedStores ?? [];
    this.messages.add({
      severity: 'success',
      summary: linked.length
        ? this.i18n.t('pickups.linked.done', { stores: [...(store && !this.signupTarget()?.linkMissingUntil ? [store.name] : []), ...linked].join(', ') })
        : this.i18n.t(result.confirmed ? 'pickups.signup.doneConfirmed' : 'pickups.signup.doneUnconfirmed'),
    });
    this.reload();
  }

  protected onSlot(pickup: Pickup, slot: PickupSlot): void {
    if (slot.user.id === this.myId()) {
      this.confirm.confirm({
        header: this.i18n.t('pickups.signout.title'),
        message: this.withLinkedWarning(this.i18n.t('pickups.signout.text'), slot),
        acceptLabel: this.i18n.t('pickups.signout.confirm'),
        rejectLabel: this.i18n.t('common.cancel'),
        acceptButtonStyleClass: 'p-button-danger',
        rejectButtonStyleClass: 'p-button-text p-button-secondary',
        accept: () => void this.signOut.run(pickup),
      });
      return;
    }
    this.openSlot(pickup, slot);
  }

  /**
   * Opens the slot dialog: rich for managers and co-pickups, short for the rest of the team; from the ✓ of a split
   * pill, "Bestätigen" gets the focus (no one-click confirm).
   */
  protected openSlot(pickup: Pickup, slot: PickupSlot, focusConfirm = false): void {
    this.slotMode.set(this.isManager() || this.isSignedUp(pickup) ? 'rich' : 'short');
    this.focusConfirm.set(focusConfirm);
    this.slotTarget.set({ pickup, slot });
    this.slotOpen.set(true);
  }

  /** `p-dialog` (onShow) fires after the enter animation, so the focus does not race it. */
  protected onSlotDialogShown(): void {
    if (!this.focusConfirm()) return;
    this.document.querySelector<HTMLElement>('[data-testid="stores.pickups.confirmSlot"] button')?.focus();
  }

  protected readonly signOut = runMutation(
    (pickup: Pickup) =>
      this.api.signOut({ storeId: this.ctx.storeId()!, pickupAt: pickup.pickupAt, userId: this.myId()! }),
    { successToastKey: 'pickups.signout.done', onSuccess: () => this.reload() },
  );

  // --- manager: confirm / remove a slot -------------------------------------------------------------------------

  protected readonly slotOpen = signal(false);
  protected readonly slotMode = signal<SlotDetailMode>('short');
  protected readonly focusConfirm = signal(false);
  protected readonly removeOpen = signal(false);
  protected readonly slotTarget = signal<SlotTarget | null>(null);

  protected readonly confirmSlot = runMutation(
    (target: SlotTarget) =>
      this.api.confirmSignup({
        storeId: this.ctx.storeId()!,
        pickupAt: target.pickup.pickupAt,
        userId: target.slot.user.id,
      }),
    {
      successToastKey: 'pickups.tab.confirmed',
      onSuccess: () => {
        this.slotOpen.set(false);
        this.reload();
      },
    },
  );

  /** "Alle n bestätigen": one dialog naming the people, then one confirm call per person, one after another. */
  protected askConfirmAll(pickup: Pickup): void {
    const pending = pickup.occupiedSlots.filter((slot) => !slot.confirmed);
    this.confirm.confirm({
      header: this.i18n.t('pickups.tab.confirmAllTitle'),
      message: this.i18n.t('pickups.tab.confirmAllText', {
        names: pending.map((slot) => slot.user.displayName).join(', '),
      }),
      acceptLabel: this.i18n.t('pickups.tab.confirmAll', { count: pending.length }),
      rejectLabel: this.i18n.t('common.cancel'),
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.confirmAll.run(pickup, pending),
    });
  }

  protected readonly confirmAll = runMutation(
    (pickup: Pickup, pending: PickupSlot[]) =>
      concat(
        ...pending.map((slot) =>
          this.api.confirmSignup({ storeId: this.ctx.storeId()!, pickupAt: pickup.pickupAt, userId: slot.user.id }),
        ),
      ).pipe(tap({ complete: () => this.confirmedAll.set(pending.length) })),
    {
      onSuccess: () => {
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t('pickups.tab.confirmedAll', { count: this.confirmedAll() }),
          life: 4000,
        });
        this.reload();
      },
      // a failure partway: the error toast shows, the reload keeps the people still unconfirmed visible
      onError: () => this.reload(),
    },
  );
  private readonly confirmedAll = signal(0);

  /** The remove dialog's text, with the EA16 warning when the sign-up carries a linked EinAb. */
  protected readonly removeText = computed(() => this.withLinkedWarning(this.i18n.t('pickups.tab.removeText'), this.slotTarget()?.slot));

  /** EA16 (decision 169): signing out of / removing a sign-up with a linked EinAb cancels it; booked newcomers are told. */
  private withLinkedWarning(text: string, slot: PickupSlot | undefined): string {
    const linked = slot?.linkedIntroPickup;
    return linked ? `${text} ${this.i18n.t('pickups.signout.linkedIntroPickup', { count: linked.bookedCount })}` : text;
  }

  protected askRemove(): void {
    this.slotOpen.set(false);
    this.removeOpen.set(true);
  }

  protected readonly removeSlot = runMutation(
    (message: string) => {
      const target = this.slotTarget()!;
      return this.api.signOut({
        storeId: this.ctx.storeId()!,
        pickupAt: target.pickup.pickupAt,
        userId: target.slot.user.id,
        optionalMessage: { message, sendMessage: true },
      });
    },
    {
      successToastKey: 'pickups.tab.removed',
      onSuccess: () => {
        this.removeOpen.set(false);
        this.reload();
      },
    },
  );

  // --- manager: edit / add a pickup ------------------------------------------------------------------------------

  protected readonly editOpen = signal(false);
  protected readonly editAt = signal<string | null>(null);
  protected editSlots = 2;
  protected editDescription = '';
  protected newDate = '';
  protected newTime = '18:00';

  protected openEdit(pickup: Pickup): void {
    this.editAt.set(pickup.pickupAt);
    this.editSlots = pickup.slotCount;
    this.editDescription = pickup.description ?? '';
    this.editOpen.set(true);
  }

  protected openAdd(): void {
    this.editAt.set(null);
    this.editSlots = 2;
    this.editDescription = '';
    this.newDate = addDays(dayOf(this.clock.now(), this.tz()), 1);
    this.newTime = '18:00';
    this.editOpen.set(true);
  }

  protected readonly savePickup = runMutation(
    () => {
      const pickupAt = this.editAt() ?? zonedToUtc(this.newDate, this.newTime, this.tz());
      const description = this.editDescription.trim();
      return this.api.editPickup({
        storeId: this.ctx.storeId()!,
        pickupAt,
        pickupEdit: { slotCount: Math.min(50, Math.max(0, Number(this.editSlots) || 0)), ...(description ? { description } : {}) },
      });
    },
    {
      successToastKey: 'pickups.tab.saved',
      onSuccess: () => {
        this.editOpen.set(false);
        this.reload();
      },
    },
  );

  // --- manager: regular schedule (PU1) ---------------------------------------------------------------------------

  protected readonly regularOpen = signal(false);
  protected readonly schedule = signal<RegularPickup[]>([]);
  protected readonly hasDuplicates = computed(
    () =>
      duplicateRows(this.schedule()).size > 0 ||
      this.schedule().some((row) => !/^([01]\d|2[0-3]):[0-5]\d$/.test(row.startTime)),
  );

  protected readonly regular = loadResource<RegularPickup[], string>({
    params: () => (this.regularOpen() ? (this.ctx.storeId() ?? undefined) : undefined),
    // the editor works on a copy (schedule); a reload replaces it
    loader: (storeId) =>
      this.api
        .getRegularPickups({ storeId })
        .pipe(tap((rows) => this.schedule.set(rows.map((row) => ({ ...row, startTime: row.startTime.slice(0, 5) }))))),
  });

  protected openRegular(): void {
    this.regularOpen.set(true);
  }

  protected readonly saveRegular = runMutation(
    () =>
      this.api.replaceRegularPickups({
        storeId: this.ctx.storeId()!,
        regularPickup: this.schedule().map((row) => ({
          weekday: row.weekday,
          startTime: row.startTime,
          slotCount: row.slotCount,
          ...(row.description?.trim() ? { description: row.description.trim() } : {}),
        })),
      }),
    {
      successToastKey: 'pickups.regular.saved',
      onSuccess: () => {
        this.regularOpen.set(false);
        this.reload();
      },
    },
  );
}
