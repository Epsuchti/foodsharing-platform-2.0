import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { tap } from 'rxjs';

import { AutomationService, Pickup, PickupSlot, PickupsService, RegularPickup, SignupResult } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PickupSlotComponent } from '../../../shared/pickup-slot/pickup-slot.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { WeekdayTimePickerComponent, duplicateRows } from '../../../shared/weekday-time-picker/weekday-time-picker.component';
import { PickupSignupDialogComponent, SignupTarget } from '../components/signup-dialog.component';
import { ServerClock } from '../pickups.resource';
import { addDays, dayOf, groupByDay, zonedToUtc } from '../pickups.util';
import { StorePageContext } from '../../stores/store-page.context';

type Range = 'twoWeeks' | 'all';

interface SlotTarget {
  pickup: Pickup;
  slot: PickupSlot;
}

/**
 * /stores/:storeId/pickups (PU1–PU5): pickups grouped by day with their slots. Free slot → eligibility + sign-up;
 * own slot → sign out; managers: confirm/remove others (message), edit a pickup (slots/description), add a one-time
 * pickup and edit the regular schedule.
 */
@Component({
  selector: 'fs-store-pickups-tab',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    FormsModule,
    InputText,
    PickupSignupDialogComponent,
    PickupSlotComponent,
    RequiredMessageDialogComponent,
    RouterLink,
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
          @for (group of days(); track group.day) {
            <section class="fs-card card day" data-testid="stores.pickups.day" [attr.data-day]="group.day">
              <h2 class="card__title">{{ group.items[0].pickupAt | fsDate: 'weekday' : tz() }}</h2>
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
                      <span class="badge" data-testid="stores.pickups.regularBadge">{{ 'pickups.tab.regularBadge' | t }}</span>
                    }
                    @if (pickup.description) {
                      <span class="pickup__desc" data-testid="stores.pickups.description">{{ pickup.description }}</span>
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
                    <p class="muted" data-testid="stores.pickups.cancelled">{{ 'pickups.tab.cancelled' | t }}</p>
                  } @else {
                    <fs-pickup-slot
                      [slotCount]="pickup.slotCount"
                      [slots]="pickup.occupiedSlots"
                      [myId]="myId()"
                      [freeClickable]="!isSignedUp(pickup)"
                      [slotsClickable]="ctx.may('mayConfirmPickups') || ctx.may('mayEditPickups')"
                      (freeClick)="openSignup(pickup)"
                      (slotClick)="onSlot(pickup, $event)"
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
                  @if (pickup.extraSlot; as extra) {
                    <p class="muted" data-testid="stores.pickups.extraSlot">{{ 'pickups.tab.extraSlotBy' | t: { name: extra.byUser.displayName } }}</p>
                  }
                  @if (mySlot(pickup); as mine) {
                    @if (!mine.confirmed) {
                      <p class="pending" data-testid="stores.pickups.unconfirmed">
                        <i class="pi pi-hourglass" aria-hidden="true"></i>{{ 'pickups.tab.waiting' | t }}
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

    <fs-pickup-signup-dialog [(visible)]="signupOpen" [target]="signupTarget()" (signedUp)="afterSignup($event)" />

    <p-dialog
      [visible]="slotOpen()"
      (visibleChange)="slotOpen.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="slotTarget()?.slot?.user?.displayName ?? ''"
      [style]="{ width: 'min(28rem, 94vw)' }"
      data-testid="stores.pickups.slotDialog"
    >
      @if (slotTarget(); as s) {
        <p class="muted">{{ s.pickup.pickupAt | fsDate: 'dateTimeLong' : tz() }}</p>
        <div class="dialog-actions">
          @if (!s.slot.confirmed && ctx.may('mayConfirmPickups')) {
            <p-button
              [label]="'pickups.tab.confirmSlot' | t"
              icon="pi pi-check"
              [loading]="confirmSlot.busy()"
              (onClick)="confirmSlot.run(s)"
              data-testid="stores.pickups.confirmSlot"
            />
          }
          <p-button
            [label]="'pickups.tab.removeSlot' | t"
            icon="pi pi-user-minus"
            severity="danger"
            [outlined]="true"
            (onClick)="askRemove()"
            data-testid="stores.pickups.removeSlot"
          />
        </div>
      }
    </p-dialog>

    <fs-required-message-dialog
      [(visible)]="removeOpen"
      [title]="'pickups.tab.removeTitle' | t: { name: slotTarget()?.slot?.user?.displayName ?? '' }"
      [text]="'pickups.tab.removeText' | t"
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
            <input pInputText type="date" [(ngModel)]="newDate" data-testid="stores.pickups.edit.date" />
          </label>
          <label class="field">
            <span>{{ 'pickups.tab.time' | t }} ({{ tz() }})</span>
            <input pInputText type="time" [(ngModel)]="newTime" data-testid="stores.pickups.edit.time" />
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
    .chips, .actions { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .chip {
      padding: 0.35rem 0.85rem; border: 1px solid var(--fs-border); border-radius: 999px; background: var(--fs-surface);
      color: inherit; font: inherit; font-size: 0.88rem; cursor: pointer;
    }
    .chip--active { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); font-weight: 650; }
    .pickup { display: grid; gap: 0.45rem; padding: 0.6rem 0; border-top: 1px solid var(--fs-border); }
    .pickup:first-of-type { border-top: 0; }
    .pickup__head { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
    .pickup__time { font-weight: 700; font-variant-numeric: tabular-nums; }
    .pickup__desc { color: var(--fs-text-muted); font-size: 0.88rem; }
    .badge { padding: 0.05rem 0.5rem; border-radius: 999px; background: var(--fs-surface-muted); font-size: 0.75rem; font-weight: 600; }
    .pending { display: flex; gap: 0.4rem; align-items: center; margin: 0; color: var(--fs-warning-text); font-size: 0.88rem; font-weight: 600; }
    .muted { margin: 0; color: var(--fs-text-muted); }
    .dialog-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 1rem; }
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

  protected readonly ranges: Range[] = ['twoWeeks', 'all'];
  protected readonly range = signal<Range>('all');
  protected readonly myId = computed(() => this.session.user()?.id);
  protected readonly tz = computed(() => this.ctx.timeZone());

  protected readonly pickups = loadResource<Pickup[], string>({
    params: () => (this.ctx.may('maySeePickups') ? (this.ctx.storeId() ?? undefined) : undefined),
    loader: (storeId) => this.api.listPickups({ storeId }),
    isEmpty: (list) => list.length === 0,
  });

  /** Day groups; "next 2 weeks" counts from the server's today in the store's time zone. */
  protected readonly days = computed(() => {
    const groups = groupByDay(this.pickups.data() ?? [], this.tz());
    if (this.range() === 'all' || !groups.length) return groups;
    const until = addDays(dayOf(this.clock.now(), this.tz()), 14);
    return groups.filter((group) => group.day < until);
  });

  protected mySlot(pickup: Pickup): PickupSlot | undefined {
    return pickup.occupiedSlots.find((slot) => slot.user.id === this.myId());
  }

  protected isSignedUp(pickup: Pickup): boolean {
    return !!this.mySlot(pickup);
  }

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

  protected afterSignup(result: SignupResult): void {
    this.messages.add({
      severity: 'success',
      summary: this.i18n.t(result.confirmed ? 'pickups.signup.doneConfirmed' : 'pickups.signup.doneUnconfirmed'),
    });
    this.reload();
  }

  protected onSlot(pickup: Pickup, slot: PickupSlot): void {
    if (slot.user.id === this.myId()) {
      this.confirm.confirm({
        header: this.i18n.t('pickups.signout.title'),
        message: this.i18n.t('pickups.signout.text'),
        acceptLabel: this.i18n.t('pickups.signout.confirm'),
        rejectLabel: this.i18n.t('common.cancel'),
        acceptButtonStyleClass: 'p-button-danger',
        rejectButtonStyleClass: 'p-button-text p-button-secondary',
        accept: () => void this.signOut.run(pickup),
      });
      return;
    }
    this.slotTarget.set({ pickup, slot });
    this.slotOpen.set(true);
  }

  protected readonly signOut = runMutation(
    (pickup: Pickup) =>
      this.api.signOut({ storeId: this.ctx.storeId()!, pickupAt: pickup.pickupAt, userId: this.myId()! }),
    { successToastKey: 'pickups.signout.done', onSuccess: () => this.reload() },
  );

  // --- manager: confirm / remove a slot -------------------------------------------------------------------------

  protected readonly slotOpen = signal(false);
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
