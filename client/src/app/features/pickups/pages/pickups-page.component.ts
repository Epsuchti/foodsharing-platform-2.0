import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { forkJoin, map } from 'rxjs';

import { FspCleaningService, PickupOption, PickupsService, SignupResult, UserCleaningShift, UserPickup } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RegionContextService } from '../../../core/region/region-context.service';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { MyPickups, MyPickupsListComponent } from '../components/my-pickups-list.component';
import { PickupOptionsListComponent } from '../components/pickup-options-list.component';
import { PickupSignupDialogComponent, SignupTarget } from '../components/signup-dialog.component';
import { dayOf, mergeByTime } from '../pickups.util';

/**
 * /pickups (PU3, PU7, owner feedback 2026-11-04, docs/design/ui-polish-batch-proposal.md §3): one page with "My
 * pickups" on top (`#mine`, first 5) and "Where can I help?" below (`#options`, first 10). History and the day
 * overview are header actions. Each section has its own resource and `fs-data-state`; sign-out and sign-up keep
 * their flows (confirm dialog, `PickupSignupDialogComponent`). `/pickups/mine` redirects to `/pickups#mine`.
 */
@Component({
  selector: 'fs-pickups-page',
  imports: [Button, MyPickupsListComponent, PageHeaderComponent, PickupOptionsListComponent, PickupSignupDialogComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'pickups.page.title' | t" [subtitle]="'pickups.page.subtitle' | t" icon="pi pi-calendar">
      <p-button
        [label]="'pickups.history.link' | t"
        icon="pi pi-history"
        severity="secondary"
        [text]="true"
        routerLink="/me/pickups/history"
        data-testid="pickups.mine.history"
      />
      @if (today(); as day) {
        <p-button
          [label]="'pickups.agenda.title' | t"
          icon="pi pi-list"
          severity="secondary"
          [text]="true"
          [routerLink]="['/pickups/agenda', day]"
          data-testid="pickups.mine.agenda"
        />
      }
    </fs-page-header>
    <div class="sections">
      <fs-my-pickups-list
        [state]="mine"
        (signOut)="askSignOut($event)"
        (cleaningSignOut)="askCleaningSignOut($event)"
        (findOptions)="optionsList()?.focusSection()"
      />
      <fs-pickup-options-list [state]="options" (signUp)="open($event)" />
    </div>
    <fs-pickup-signup-dialog [(visible)]="signupOpen" [target]="target()" (signedUp)="done($event)" />
  `,
  styles: `
    :host { display: block; width: min(100%, 56rem); margin: 0 auto; }
    .sections { display: grid; gap: 2rem; }
  `,
})
export class PickupsPageComponent {
  private readonly api = inject(PickupsService);
  private readonly cleaningApi = inject(FspCleaningService);
  private readonly session = inject(SessionService);
  private readonly region = inject(RegionContextService);
  private readonly confirm = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);

  protected readonly optionsList = viewChild(PickupOptionsListComponent);

  protected readonly mine = loadResource<MyPickups>({
    loader: () =>
      forkJoin([this.api.listMyPickups(), this.cleaningApi.listMyCleaningShifts()]).pipe(
        map(([pickups, cleaning]) => ({ pickups, cleaning })),
      ),
    isEmpty: (mine) => mine.pickups.length === 0 && mine.cleaning.length === 0,
  });

  protected readonly options = loadResource<PickupOption[]>({
    loader: () => this.api.listPickupOptions(),
    isEmpty: (list) => !list.some((option) => option.occupiedSlots.length < option.slotCount),
  });

  /** The day overview opens on the day of the next pickup. */
  protected readonly today = computed(() => {
    const next = mergeByTime(this.mine.data()?.pickups ?? [], this.mine.data()?.cleaning ?? [])[0];
    return next ? dayOf(next.at, 'Europe/Zurich') : null;
  });

  protected readonly signOut = runMutation(
    (pickup: UserPickup) =>
      this.api.signOut({ storeId: pickup.store.id, pickupAt: pickup.pickupAt, userId: this.session.user()!.id }),
    {
      successToastKey: 'pickups.signout.done',
      onSuccess: () => {
        this.mine.reload();
        this.options.reload();
      },
    },
  );

  protected readonly signOutOfCleaning = runMutation(
    (shift: UserCleaningShift) =>
      this.cleaningApi.signOutOfCleaning({ fspId: shift.fsp.id, shiftAt: shift.shiftAt, userId: this.session.user()!.id }),
    { successToastKey: 'pickups.cleaning.signedOut', onSuccess: () => this.mine.reload() },
  );

  protected askCleaningSignOut(shift: UserCleaningShift): void {
    this.confirm.confirm({
      header: this.i18n.t('pickups.signout.title'),
      message: this.i18n.t('pickups.cleaning.signoutText'),
      acceptLabel: this.i18n.t('pickups.signout.confirm'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.signOutOfCleaning.run(shift),
    });
  }

  protected askSignOut(pickup: UserPickup): void {
    this.confirm.confirm({
      header: this.i18n.t('pickups.signout.title'),
      message: this.i18n.t('pickups.signout.text'),
      acceptLabel: this.i18n.t('pickups.signout.confirm'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.signOut.run(pickup),
    });
  }

  protected readonly signupOpen = signal(false);
  protected readonly target = signal<SignupTarget | null>(null);

  protected open(option: PickupOption): void {
    this.target.set({
      storeId: option.store.id,
      storeName: option.store.name,
      pickupAt: option.pickupAt,
      timeZone: this.region.timeZone(),
    });
    this.signupOpen.set(true);
  }

  /** A sign-up adds a pickup to "My pickups" and takes a slot from the options: both reload. */
  protected done(result: SignupResult): void {
    this.messages.add({
      severity: 'success',
      summary: this.i18n.t(result.confirmed ? 'pickups.signup.doneConfirmed' : 'pickups.signup.doneUnconfirmed'),
    });
    this.mine.reload();
    this.options.reload();
  }
}
