import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';

import { PickupOption, PickupsService, Role, SignupResult } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RegionContextService } from '../../../core/region/region-context.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { HELP_NEEDED_SHOWN } from '../../dashboard/dashboard.util';
import { isSignedUp, openOptions } from '../pickups.util';
import { PickupSignupDialogComponent, SignupTarget } from './signup-dialog.component';

/**
 * Home card "Help needed soon" (owner feedback 2026-11-04): the next three pickups with free slots in my stores
 * that I may take (`listPickupOptions`), each with Sign up (the dialog of /pickups) or, when I am in, a "Signed up" chip
 * (review fix A2), and a link to all of them (`/pickups#options`).
 * Verified foodsavers and above only; hidden when nothing is free.
 */
@Component({
  selector: 'fs-help-needed-card',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PickupSignupDialogComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible() && options.status() !== 'empty') {
      <section class="fs-card card" data-testid="dashboard.help" aria-labelledby="dashboard-help-title">
        <h2 class="title" id="dashboard-help-title"><i class="pi pi-plus" aria-hidden="true"></i>{{ 'pickups.dashboard.help' | t }}</h2>
        <fs-data-state [state]="options" skeleton="list" [skeletonRows]="3" [compact]="true" testId="dashboard.help.state" data-testid="dashboard.help.state">
          <ng-template [fsData]="options">
            <ul class="list" data-testid="dashboard.help.list">
              @for (option of next(); track option.store.id + option.pickupAt) {
                <li class="row row--action" data-testid="dashboard.help.option" [attr.data-store]="option.store.name" [attr.data-pickup-at]="option.pickupAt">
                  <div class="row__body">
                    <a class="row__title" [routerLink]="['/stores', option.store.id, 'pickups']">{{ option.store.name }}</a>
                    <span class="row__meta">
                      <time [attr.datetime]="option.pickupAt">{{ option.pickupAt | fsDate: 'dateTime' }}</time>
                      <span>{{ 'pickups.options.free' | t: { free: option.slotCount - option.occupiedSlots.length, total: option.slotCount } }}</span>
                    </span>
                  </div>
                  @if (isSignedUp(option)) {
                    <span
                      class="signed-up"
                      [class.signed-up--wait]="!option.isConfirmed"
                      [attr.data-confirmed]="option.isConfirmed"
                      data-testid="dashboard.help.signedUp"
                    >
                      <i class="pi" [class.pi-check-circle]="option.isConfirmed" [class.pi-hourglass]="!option.isConfirmed" aria-hidden="true"></i>
                      <span>{{ (option.isConfirmed ? 'pickups.options.signedUpConfirmed' : 'pickups.options.signedUpUnconfirmed') | t }}</span>
                    </span>
                  } @else {
                    <p-button
                      [label]="'pickups.options.signUp' | t"
                      [ariaLabel]="'pickups.options.signUpLabel' | t: { store: option.store.name, date: (option.pickupAt | fsDate: 'dateTime') }"
                      icon="pi pi-plus"
                      size="small"
                      (onClick)="open(option)"
                      data-testid="dashboard.help.signUp"
                    />
                  }
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
        @if (all().length > 0) {
          <div class="links">
            <a routerLink="/pickups" fragment="options" data-testid="dashboard.help.all">{{ 'pickups.dashboard.helpAll' | t: { count: all().length } }}</a>
          </div>
        }
      </section>
      <fs-pickup-signup-dialog [(visible)]="signupOpen" [target]="target()" (signedUp)="done($event)" />
    }
  `,
  styleUrls: ['../../dashboard/components/home-card.scss', '../../../shared/date-tile/date-rows.scss'],
  styles: `
    /* own sign-up (review fix A2): a state, not an action; same colours as "My pickups" */
    .signed-up { display: inline-flex; gap: 0.35rem; align-items: center; font-size: 0.88rem; font-weight: 600; color: var(--fs-primary-strong); white-space: nowrap; }
    /* one line where there is room; only narrow phones wrap it, the icon stays right beside the text */
    @media (max-width: 30rem) {
      .signed-up { white-space: normal; max-width: 9.5rem; }
    }
    .signed-up--wait { color: var(--fs-warning-text); }
  `,
})
export class HelpNeededCardComponent {
  private readonly api = inject(PickupsService);
  private readonly session = inject(SessionService);
  private readonly region = inject(RegionContextService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);

  /** Same rule as the pickups navigation: verified foodsavers and above. */
  protected readonly visible = computed(() => this.session.hasRole(Role.Foodsaver) && !!this.session.user()?.verified);

  protected readonly options = loadResource<PickupOption[], boolean>({
    params: () => (this.visible() ? true : undefined),
    loader: () => this.api.listPickupOptions(),
    isEmpty: (list) => openOptions(list).length === 0,
  });

  protected readonly all = computed(() => openOptions(this.options.data() ?? []));
  protected readonly next = computed(() => this.all().slice(0, HELP_NEEDED_SHOWN));

  protected readonly isSignedUp = isSignedUp;
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

  protected done(result: SignupResult): void {
    this.messages.add({
      severity: 'success',
      summary: this.i18n.t(result.confirmed ? 'pickups.signup.doneConfirmed' : 'pickups.signup.doneUnconfirmed'),
    });
    this.options.reload();
  }
}
