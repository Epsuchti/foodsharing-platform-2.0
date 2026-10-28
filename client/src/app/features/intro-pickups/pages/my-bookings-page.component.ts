import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { forkJoin, map } from 'rxjs';

import { IntroPickupsService, Journey, MyBooking, OnboardingService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimeFormatter, DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RegionContextService } from '../../../core/region/region-context.service';
import { CalendarExportButtonComponent } from '../../../shared/calendar-export-button/calendar-export-button.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { FoodsaverIdHelpComponent } from '../../../shared/foodsaver-id-help/foodsaver-id-help.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { BookedDetailsComponent } from '../components/booked-details.component';
import { EinAbCardComponent } from '../components/einab-card.component';
import { SubscriptionCardComponent } from '../components/subscription-card.component';
import { introPickupIcs } from '../intro-pickups.resource';

interface MyBookings {
  upcoming: MyBooking[];
  past: MyBooking[];
}

type NextStep = 'BOOK' | 'ATTEND' | 'WAIT' | 'FAIRTEILER';

/**
 * `/me/intro-pickups` (EA7, EA8, EA9): progress "x of N" with the next step, upcoming bookings with private details,
 * calendar export and cancel, past bookings with done/counted status, the notification subscription card and the
 * own foodsaver ID with help (EA3).
 */
@Component({
  selector: 'fs-my-bookings-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    BookedDetailsComponent,
    Button,
    CalendarExportButtonComponent,
    DateTimePipe,
    EinAbCardComponent,
    FoodsaverIdHelpComponent,
    PageHeaderComponent,
    RouterLink,
    SubscriptionCardComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'introPickups.my.title' | t" [subtitle]="'introPickups.my.subtitle' | t" icon="pi pi-calendar" />

    <div class="top">
      <fs-data-state [state]="journey" skeleton="card" [skeletonRows]="1" [compact]="true" data-testid="introPickups.my.progressState">
        <ng-template [fsData]="journey" let-j>
          @if (progressOf(j); as progress) {
            <section class="fs-card card" data-testid="introPickups.my.progress" [attr.data-done]="progress.done" [attr.data-required]="progress.required">
              <h2 class="card__title"><i class="pi pi-flag" aria-hidden="true"></i>{{ 'introPickups.my.progressTitle' | t }}</h2>
              <p class="progress__count" data-testid="introPickups.my.progressCount">
                {{ 'introPickups.my.progressCount' | t: { done: progress.done, required: progress.required } }}
              </p>
              <div class="meter" role="meter" [attr.aria-valuenow]="progress.done" aria-valuemin="0" [attr.aria-valuemax]="progress.required" [attr.aria-label]="'introPickups.my.progressTitle' | t">
                @for (step of steps(progress.required); track $index) {
                  <span class="meter__seg" [class.meter__seg--done]="$index < progress.done" [class.meter__seg--booked]="$index >= progress.done && $index < progress.done + progress.booked"></span>
                }
              </div>
              <p class="muted" data-testid="introPickups.my.nextStep" [attr.data-step]="nextStep(progress)">
                {{ 'introPickups.my.next.' + nextStep(progress) | t }}
              </p>
              @if (nextStep(progress) === 'BOOK' || nextStep(progress) === 'FAIRTEILER') {
                <div class="actions">
                  <a routerLink="/intro-pickups" class="fs-btn fs-btn--sm" data-testid="introPickups.my.bookMore">
                    <i class="pi pi-calendar-plus" aria-hidden="true"></i>{{ 'introPickups.my.bookMore' | t }}
                  </a>
                </div>
              }
            </section>
          }
        </ng-template>
      </fs-data-state>
      @if (user()?.foodsaverNumber) {
        <section class="fs-card card" data-testid="introPickups.my.foodsaverId">
          <h2 class="card__title"><i class="pi pi-id-card" aria-hidden="true"></i>{{ 'introPickups.my.foodsaverId' | t }}</h2>
          <p class="fsid"><strong data-testid="introPickups.my.foodsaverNumber">{{ user()!.foodsaverNumber }}</strong><fs-foodsaver-id-help [number]="user()!.foodsaverNumber" /></p>
          <p class="muted">{{ 'introPickups.my.foodsaverIdText' | t }}</p>
        </section>
      }
    </div>

    <fs-data-state
      [state]="bookings"
      skeleton="card"
      [skeletonRows]="3"
      emptyIcon="pi pi-calendar-plus"
      [emptyTitle]="'introPickups.my.empty' | t"
      [emptyText]="'introPickups.my.emptyText' | t"
      [emptyActionLabel]="'introPickups.my.bookMore' | t"
      (emptyAction)="goBook()"
      data-testid="introPickups.my.state"
    >
      <ng-template [fsData]="bookings" let-b>
        <h2 class="section-title"><i class="pi pi-clock" aria-hidden="true"></i>{{ 'introPickups.my.upcoming' | t }}</h2>
        @if (b.upcoming.length === 0) {
          <p class="muted" data-testid="introPickups.my.noUpcoming">
            {{ 'introPickups.my.noUpcoming' | t }} <a routerLink="/intro-pickups">{{ 'introPickups.my.bookMore' | t }}</a>
          </p>
        }
        <ul class="grid">
          @for (booking of b.upcoming; track booking.slotId) {
            <li>
              <fs-einab-card [einab]="booking.introPickup" [attr.data-category]="booking.introPickup.category" data-testid="introPickups.my.upcomingCard">
                <div class="stack upcoming">
                  <fs-booked-details [einab]="booking.introPickup" />
                  <div class="actions">
                    <fs-calendar-export-button [ics]="ics(booking.introPickup.id)" fileName="einab.ics" [small]="true" testId="introPickups.my.calendar" />
                    <p-button
                      [label]="'introPickups.my.cancel' | t"
                      icon="pi pi-times"
                      severity="danger"
                      [text]="true"
                      size="small"
                      [loading]="cancel.busy()"
                      (onClick)="askCancel(booking)"
                      data-testid="introPickups.my.cancel"
                    />
                  </div>
                </div>
              </fs-einab-card>
            </li>
          }
        </ul>
        @if (b.past.length) {
          <h2 class="section-title"><i class="pi pi-history" aria-hidden="true"></i>{{ 'introPickups.my.past' | t }}</h2>
          <ul class="list">
            @for (booking of b.past; track booking.slotId) {
              <li class="fs-card past" data-testid="introPickups.my.pastRow" [attr.data-status]="booking.status">
                <span class="past__main">
                  <strong>{{ 'common.introCategory.' + booking.introPickup.category | t }}</strong>
                  <span class="muted">{{ booking.introPickup.startAt | fsDate: 'dateTime' }} · {{ booking.introPickup.guide.displayName }}</span>
                </span>
                <span class="tags">
                  <span [class]="'tag tag--' + booking.status" data-testid="introPickups.my.pastStatus">{{ 'introPickups.status.' + booking.status | t }}</span>
                  @if (booking.counted) {
                    <span class="tag tag--ok" data-testid="introPickups.my.counted"><i class="pi pi-check" aria-hidden="true"></i>{{ 'introPickups.my.counted' | t }}</span>
                  }
                </span>
              </li>
            }
          </ul>
        }
      </ng-template>
    </fs-data-state>

    @if (!user()?.verified) {
      <fs-subscription-card [preselect]="subscribe()" />
    }
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    .top {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr));
      gap: 1rem;
    }
    .progress__count {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 750;
    }
    .meter {
      display: flex;
      gap: 0.35rem;
    }
    .meter__seg {
      flex: 1;
      height: 0.6rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      box-shadow: inset 0 0 0 1px var(--fs-border);
    }
    .meter__seg--done {
      background: var(--fs-primary);
      box-shadow: none;
    }
    .meter__seg--booked {
      background: var(--fs-primary-soft);
      box-shadow: inset 0 0 0 1px var(--fs-primary);
    }
    .fsid {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      margin: 0;
      font-size: 1.4rem;
      font-variant-numeric: tabular-nums;
    }
    .upcoming {
      width: 100%;
      gap: 0.6rem;
    }
    .past {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
    }
    .past__main {
      display: grid;
      gap: 0.1rem;
      min-width: 0;
    }
  `,
})
export class MyBookingsPageComponent {
  private readonly api = inject(IntroPickupsService);
  private readonly onboarding = inject(OnboardingService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);
  private readonly context = inject(RegionContextService);
  private readonly confirm = inject(ConfirmationService);
  private readonly router = inject(Router);
  protected readonly ics = introPickupIcs();

  /** `?subscribe=<regionId>` from the public "notify me" button. */
  readonly subscribe = input<string | undefined>(undefined);
  protected readonly user = this.session.user;

  protected readonly bookings = loadResource<MyBookings>({
    loader: () =>
      forkJoin([this.api.listMyBookings({ past: false }), this.api.listMyBookings({ past: true })]).pipe(
        map(([upcoming, past]) => ({
          upcoming: [...upcoming].sort((a, b) => a.introPickup.startAt.localeCompare(b.introPickup.startAt)),
          past: [...past].sort((a, b) => b.introPickup.startAt.localeCompare(a.introPickup.startAt)),
        })),
      ),
    isEmpty: (value) => value.upcoming.length === 0 && value.past.length === 0,
  });

  protected readonly journey = loadResource<Journey>({
    loader: () => this.onboarding.getMyJourney(),
    isEmpty: () => false,
  });

  protected readonly cancel = runMutation((slotId: string) => this.api.cancelMyBooking({ slotId }), {
    successToastKey: 'introPickups.my.cancelled',
    onSuccess: () => {
      this.bookings.reload();
      this.journey.reload();
    },
  });

  protected readonly progressOf = (journey: Journey) =>
    journey.steps.find((step) => step.key === 'INTRO_PICKUPS')?.progress;

  protected steps(required: number): number[] {
    return Array.from({ length: Math.max(required, 1) });
  }

  protected nextStep(progress: { done: number; booked: number; required: number; fairteilerDone?: boolean }): NextStep {
    if (progress.done >= progress.required) {
      return 'WAIT';
    }
    if (progress.done + progress.booked >= progress.required) {
      return 'ATTEND';
    }
    if (progress.fairteilerDone === false && progress.done + progress.booked === progress.required - 1) {
      return 'FAIRTEILER';
    }
    return 'BOOK';
  }

  protected goBook(): void {
    void this.router.navigateByUrl('/intro-pickups');
  }

  protected askCancel(booking: MyBooking): void {
    this.confirm.confirm({
      header: this.i18n.t('introPickups.my.cancelTitle'),
      message: this.i18n.t('introPickups.my.cancelText', {
        category: this.i18n.t('common.introCategory.' + booking.introPickup.category),
        date: this.dates.format(booking.introPickup.startAt, 'dateTimeLong', this.context.timeZone()),
      }),
      acceptLabel: this.i18n.t('introPickups.my.cancel'),
      rejectLabel: this.i18n.t('common.back'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => this.cancel.run(booking.slotId),
    });
  }
}
