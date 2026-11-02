import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';

import {
  BookableIntroPickup,
  BookableIntroPickupPage,
  IntroPickupsService,
  MyBooking,
  RegionRef,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimeFormatter } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RegionContextService } from '../../../core/region/region-context.service';
import { CalendarExportButtonComponent } from '../../../shared/calendar-export-button/calendar-export-button.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RegionPickerComponent } from '../../../shared/region-picker/region-picker.component';
import { BookedDetailsComponent } from '../components/booked-details.component';
import { EinAbCardComponent } from '../components/einab-card.component';
import { introPickupIcs } from '../intro-pickups.resource';

/**
 * `/intro-pickups` (EA1, EA5, EA6): bookable EinAbs of the active (home) region. Each card is bookable
 * or shows the translated reason; one tap books after a confirmation, the success dialog shows the private details and
 * the calendar export. Users without a region pick one and continue on its public list.
 */
@Component({
  selector: 'fs-bookable-list-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    BookedDetailsComponent,
    Button,
    CalendarExportButtonComponent,
    Dialog,
    EinAbCardComponent,
    PageHeaderComponent,
    RegionPickerComponent,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'introPickups.bookable.title' | t"
      [subtitle]="(regionName() ? 'introPickups.bookable.subtitleRegion' : 'introPickups.bookable.subtitle') | t: { region: regionName() }"
      icon="pi pi-calendar-plus"
    />
    @if (!regionId()) {
      <section class="fs-card card narrow" data-testid="introPickups.bookable.noRegion">
        <h2 class="card__title"><i class="pi pi-map" aria-hidden="true"></i>{{ 'introPickups.bookable.pickRegion' | t }}</h2>
        <p class="muted">{{ 'introPickups.bookable.pickRegionText' | t }}</p>
        <fs-region-picker testId="introPickups.bookable.regionPicker" (regionSelected)="pickRegion($event)" />
      </section>
    } @else {
      <div class="actions">
        <a routerLink="/me/intro-pickups" class="fs-btn fs-btn--outlined fs-btn--sm" data-testid="introPickups.bookable.myBookings">
          <i class="pi pi-calendar" aria-hidden="true"></i>{{ 'introPickups.bookable.myBookings' | t }}
        </a>
      </div>
      <fs-data-state
        [state]="list"
        skeleton="card"
        [skeletonRows]="6"
        emptyIcon="pi pi-calendar-times"
        [emptyTitle]="'introPickups.bookable.empty' | t"
        [emptyText]="'introPickups.bookable.emptyText' | t"
        [emptyActionLabel]="'introPickups.bookable.notifyMe' | t"
        emptyActionIcon="pi pi-bell"
        (emptyAction)="router.navigate(['/me/intro-pickups'])"
        data-testid="introPickups.bookable.state"
      >
        <ng-template [fsData]="list" let-page>
          <ul class="grid">
            @for (einab of page.items; track einab.id) {
              <li>
                <fs-einab-card
                  [einab]="einab"
                  [attr.data-id]="einab.id"
                  [attr.data-bookable]="einab.bookable"
                  data-testid="introPickups.bookable.card"
                >
                  @if (einab.bookable) {
                    <p-button
                      [label]="'introPickups.bookable.book' | t"
                      icon="pi pi-check"
                      [loading]="book.busy() && pending()?.id === einab.id"
                      (onClick)="askBook(einab)"
                      data-testid="introPickups.bookable.book"
                    />
                  } @else {
                    <p class="reason" data-testid="introPickups.bookable.reason" [attr.data-reason]="einab.notBookableReason">
                      <i class="pi pi-lock" aria-hidden="true"></i>
                      {{ 'errors.' + (einab.notBookableReason ?? 'SLOT_NOT_AVAILABLE') | t }}
                    </p>
                  }
                </fs-einab-card>
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    }

    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="!!booked()"
      (visibleChange)="!$event && booked.set(undefined)"
      [modal]="true"
      [draggable]="false"
      [header]="'introPickups.bookable.successTitle' | t"
      [style]="{ width: 'min(32rem, 94vw)' }"
    >
      @if (booked(); as booking) {
        <div class="stack" data-testid="introPickups.bookable.success">
          <p class="muted">
            {{ 'introPickups.bookable.successText' | t: { category: ('common.introCategory.' + booking.introPickup.category | t), date: dateOf(booking.introPickup.startAt) } }}
          </p>
          <fs-booked-details [einab]="booking.introPickup" />
          <div class="actions">
            <fs-calendar-export-button [ics]="ics(booking.introPickup.id)" fileName="einab.ics" testId="introPickups.bookable.calendar" />
            <a routerLink="/me/intro-pickups" class="fs-btn fs-btn--outlined" data-testid="introPickups.bookable.toMyBookings">{{ 'introPickups.bookable.myBookings' | t }}</a>
          </div>
        </div>
      }
    </p-dialog>
  `,
  styleUrl: '../intro-pickups.scss',
})
export class BookableListPageComponent {
  private readonly api = inject(IntroPickupsService);
  private readonly context = inject(RegionContextService);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);
  private readonly confirm = inject(ConfirmationService);
  protected readonly router = inject(Router);
  protected readonly ics = introPickupIcs();

  protected readonly regionId = this.context.activeRegionId;
  protected readonly regionName = computed(() => this.context.activeRegion()?.name ?? '');
  protected readonly list = loadResource<BookableIntroPickupPage, string | undefined>({
    params: () => this.regionId() ?? undefined,
    loader: (regionId) => this.api.listBookableIntroPickups({ regionId: regionId!, size: 100 }),
  });

  protected readonly pending = signal<BookableIntroPickup | undefined>(undefined);
  protected readonly booked = signal<MyBooking | undefined>(undefined);

  protected readonly book = runMutation((slotId: string) => this.api.bookSlot({ slotId }), {
    onSuccess: (booking) => {
      this.booked.set(booking);
      this.list.reload();
    },
    onError: () => this.list.reload(),
  });

  protected dateOf(iso: string): string {
    return this.dates.format(iso, 'dateTimeLong', this.context.timeZone());
  }

  protected askBook(einab: BookableIntroPickup): void {
    if (!einab.slotId) {
      return;
    }
    this.pending.set(einab);
    this.confirm.confirm({
      header: this.i18n.t('introPickups.bookable.confirmTitle'),
      message: this.i18n.t('introPickups.bookable.confirmText', {
        category: einab.online ? this.i18n.t('common.introMode.online') : (einab.city ?? this.i18n.t('common.introMode.onSite')),
        date: this.dateOf(einab.startAt),
        guide: einab.guide.displayName,
      }),
      acceptLabel: this.i18n.t('introPickups.bookable.book'),
      rejectLabel: this.i18n.t('common.cancel'),
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => this.book.run(einab.slotId!),
    });
  }

  protected pickRegion(region: RegionRef): void {
    void this.router.navigate(['/r', region.slug, 'intro-pickups']);
  }
}
