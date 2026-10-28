import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';

import { PickupsService, UserPickup } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { dayOf } from '../pickups.util';

/** /pickups/mine (PU3): my upcoming pickups, confirmed or waiting for confirmation, with sign-out. */
@Component({
  selector: 'fs-my-pickups-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'pickups.mine.title' | t" [subtitle]="'pickups.mine.subtitle' | t" icon="pi pi-calendar" />
    <div class="toolbar">
      <p-button [label]="'pickups.options.title' | t" icon="pi pi-calendar-plus" severity="secondary" [outlined]="true" routerLink="/pickups" data-testid="pickups.mine.options" />
      @if (today(); as day) {
        <p-button [label]="'pickups.agenda.title' | t" icon="pi pi-list" severity="secondary" [text]="true" [routerLink]="['/pickups/agenda', day]" data-testid="pickups.mine.agenda" />
      }
    </div>
    <fs-data-state
      [state]="pickups"
      skeleton="list"
      [emptyTitle]="'pickups.mine.empty' | t"
      [emptyText]="'pickups.mine.emptyText' | t"
      testId="pickups.mine.state"
      data-testid="pickups.mine.state"
    >
      <ng-template [fsData]="pickups">
        <ul class="cards">
          @for (pickup of sorted(); track pickup.store.id + pickup.pickupAt) {
            <li class="fs-card item" data-testid="pickups.mine.pickup" [attr.data-store]="pickup.store.name" [attr.data-confirmed]="pickup.confirmed">
              <div class="item__main">
                <a class="item__store" [routerLink]="['/stores', pickup.store.id, 'pickups']">{{ pickup.store.name }}</a>
                <span>{{ pickup.pickupAt | fsDate: 'dateTimeLong' }}</span>
                @if (pickup.confirmed) {
                  <span class="state state--ok" data-testid="pickups.mine.confirmed"><i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'pickups.mine.confirmed' | t }}</span>
                } @else {
                  <span class="state state--wait" data-testid="pickups.mine.unconfirmed"><i class="pi pi-hourglass" aria-hidden="true"></i>{{ 'pickups.tab.waiting' | t }}</span>
                }
              </div>
              <p-button
                [label]="'pickups.signout.confirm' | t"
                icon="pi pi-sign-out"
                severity="danger"
                [text]="true"
                size="small"
                (onClick)="askSignOut(pickup)"
                data-testid="pickups.mine.signOut"
              />
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; width: min(100%, 56rem); margin: 0 auto; }
    .toolbar { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; margin-bottom: 1rem; }
    .cards { display: grid; gap: 0.6rem; margin: 0; padding: 0; list-style: none; }
    .item { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.75rem; padding: 0.9rem 1rem; }
    .item__main { display: grid; gap: 0.2rem; }
    .item__store { font-weight: 700; color: inherit; }
    .state { display: inline-flex; gap: 0.35rem; align-items: center; font-size: 0.88rem; font-weight: 600; }
    .state--ok { color: var(--fs-primary-strong); }
    .state--wait { color: var(--fs-warning-text); }
  `,
})
export class MyPickupsPageComponent {
  private readonly api = inject(PickupsService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly pickups = loadResource<UserPickup[]>({
    loader: () => this.api.listMyPickups(),
    isEmpty: (list) => list.length === 0,
  });

  protected readonly sorted = computed(() => [...(this.pickups.data() ?? [])].sort((a, b) => a.pickupAt.localeCompare(b.pickupAt)));
  /** The agenda opens on the day of the next pickup. */
  protected readonly today = computed(() => {
    const next = this.sorted()[0];
    return next ? dayOf(next.pickupAt, 'Europe/Zurich') : null;
  });

  protected readonly signOut = runMutation(
    (pickup: UserPickup) =>
      this.api.signOut({ storeId: pickup.store.id, pickupAt: pickup.pickupAt, userId: this.session.user()!.id }),
    { successToastKey: 'pickups.signout.done', onSuccess: () => this.pickups.reload() },
  );

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
}
