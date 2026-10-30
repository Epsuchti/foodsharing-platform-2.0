import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { Select } from '@openng/optimus-ui/select';

import {
  IcalCandidate,
  IntroPickupDetail,
  IntroPickupDetailPage,
  IntroPickupGuideService,
  IntroPickupSlot,
  PrivateInfoTemplate,
  RegionRef,
  UserSummary,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimeFormatter, DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RegionContextService } from '../../../core/region/region-context.service';
import { CalendarExportButtonComponent } from '../../../shared/calendar-export-button/calendar-export-button.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { EinAbFormDialogComponent, EinAbPrefill } from '../components/einab-form-dialog.component';
import { EinAbSlotsComponent, SlotAction } from '../components/einab-slots.component';
import { MemberPickDialogComponent } from '../components/member-pick-dialog.component';
import { SlotCommentsDrawerComponent } from '../components/slot-comments-drawer.component';
import { CATEGORY_ICONS } from '../intro-category';
import { introPickupIcs } from '../intro-pickups.resource';

/**
 * `/guide` (EA10–EA16): the guide's EinAbs (upcoming / past) with create, edit, delete, assign another guide,
 * calendar export, book on behalf, cancel, mark done, count a cancelled booking and comments; plus dates from the
 * guide's own calendar (iCal) that can be turned into an EinAb.
 */
@Component({
  selector: 'fs-guide-dashboard-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    CalendarExportButtonComponent,
    DateTimePipe,
    Dialog,
    EinAbFormDialogComponent,
    EinAbSlotsComponent,
    FormsModule,
    MemberPickDialogComponent,
    PageHeaderComponent,
    RouterLink,
    Select,
    SlotCommentsDrawerComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'introPickups.guide.title' | t" [subtitle]="'introPickups.guide.subtitle' | t" icon="pi pi-compass" />

    <fs-data-state [state]="regions" skeleton="card" [skeletonRows]="2" emptyIcon="pi pi-lock" [emptyTitle]="'introPickups.guide.noRegions' | t" [emptyText]="'introPickups.guide.noRegionsText' | t" data-testid="introPickups.guide.regionsState">
      <ng-template [fsData]="regions" let-list>
        <div class="toolbar">
          @if (list.length > 1) {
            <p-select [options]="list" optionLabel="name" optionValue="id" [ngModel]="regionId()" (ngModelChange)="chosenRegion.set($event)" [ariaLabel]="'introPickups.guide.region' | t" data-testid="introPickups.guide.region" />
          }
          <div class="switch" role="group" [attr.aria-label]="'introPickups.guide.when' | t">
            <p-button [label]="'introPickups.guide.upcoming' | t" size="small" [outlined]="past()" (onClick)="past.set(false)" data-testid="introPickups.guide.showUpcoming" />
            <p-button [label]="'introPickups.guide.past' | t" size="small" [outlined]="!past()" (onClick)="past.set(true)" data-testid="introPickups.guide.showPast" />
          </div>
          <span class="spacer"></span>
          @if (regionId(); as rid) {
            <a [routerLink]="['/regions', rid, 'newcomers']" class="fs-btn fs-btn--outlined fs-btn--sm" data-testid="introPickups.guide.funnel"><i class="pi pi-filter" aria-hidden="true"></i>{{ 'introPickups.guide.funnelLink' | t }}</a>
          }
          <a routerLink="/guide/settings" class="fs-btn fs-btn--outlined fs-btn--sm" data-testid="introPickups.guide.settings"><i class="pi pi-cog" aria-hidden="true"></i>{{ 'introPickups.guide.settingsLink' | t }}</a>
          <p-button [label]="'introPickups.guide.create' | t" icon="pi pi-plus" (onClick)="openCreate()" data-testid="introPickups.guide.create" />
        </div>

        @if (!past() && candidates.status() !== 'empty') {
          <fs-data-state [state]="candidates" skeleton="list" [skeletonRows]="1" [compact]="true" data-testid="introPickups.guide.icalState">
            <ng-template [fsData]="candidates" let-items>
              <section class="fs-card card" data-testid="introPickups.guide.ical">
                <h2 class="card__title"><i class="pi pi-calendar-clock" aria-hidden="true"></i>{{ 'introPickups.guide.icalTitle' | t }}</h2>
                <p class="muted">{{ 'introPickups.guide.icalText' | t }}</p>
                <ul class="list">
                  @for (candidate of items; track candidate.startAt + candidate.summary) {
                    <li class="candidate" data-testid="introPickups.guide.icalItem">
                      <span class="candidate__main">
                        <strong>{{ candidate.summary }}</strong>
                        <span class="muted">{{ candidate.startAt | fsDate: 'dateTime' }}@if (candidate.location) { · {{ candidate.location }} }</span>
                      </span>
                      <p-button [label]="'introPickups.guide.icalUse' | t" icon="pi pi-plus" size="small" [outlined]="true" (onClick)="useCandidate(candidate)" data-testid="introPickups.guide.icalUse" />
                    </li>
                  }
                </ul>
              </section>
            </ng-template>
          </fs-data-state>
        }

        <fs-data-state
          [state]="einabs"
          skeleton="card"
          [skeletonRows]="3"
          emptyIcon="pi pi-calendar-plus"
          [emptyTitle]="(past() ? 'introPickups.guide.emptyPast' : 'introPickups.guide.empty') | t"
          [emptyText]="past() ? undefined : ('introPickups.guide.emptyText' | t)"
          [emptyActionLabel]="past() ? undefined : ('introPickups.guide.create' | t)"
          (emptyAction)="openCreate()"
          data-testid="introPickups.guide.state"
        >
          <ng-template [fsData]="einabs" let-page>
            <ul class="list">
              @for (einab of page.items; track einab.id) {
                <li class="fs-card einab" [attr.data-category]="einab.category" [attr.data-id]="einab.id" data-testid="introPickups.guide.einab">
                  <div class="head">
                    <div class="einab__head">
                      <span class="einab__icon" aria-hidden="true"><i [class]="icons[einab.category]"></i></span>
                      <span class="einab__title">
                        <strong>{{ 'common.introCategory.' + einab.category | t }}@if (einab.businessName) { · {{ einab.businessName }} }</strong>
                        <span class="muted" data-testid="introPickups.guide.date">{{ einab.startAt | fsDate: 'dateTimeLong' }}</span>
                      </span>
                    </div>
                    <span class="row-actions">
                      <fs-calendar-export-button [ics]="ics(einab.id)" fileName="einab.ics" [small]="true" testId="introPickups.guide.calendar" />
                      @if (!past()) {
                        <p-button icon="pi pi-pencil" [ariaLabel]="'introPickups.guide.edit' | t" [text]="true" size="small" (onClick)="openEdit(einab)" data-testid="introPickups.guide.edit" />
                        <p-button icon="pi pi-user-edit" [ariaLabel]="'introPickups.guide.assign' | t" [text]="true" size="small" (onClick)="openAssign(einab)" data-testid="introPickups.guide.assign" />
                        <p-button icon="pi pi-trash" [ariaLabel]="'introPickups.guide.delete' | t" [text]="true" size="small" severity="danger" (onClick)="askDelete(einab)" data-testid="introPickups.guide.delete" />
                      }
                    </span>
                  </div>
                  <div class="tags">
                    @if (einab.visitFairteiler) {
                      <span class="tag tag--ok">{{ 'introPickups.card.fairteiler' | t }}</span>
                    }
                    @if (einab.minimumPickupCount) {
                      <span class="tag">{{ 'introPickups.card.minimumPickups' | t: { count: einab.minimumPickupCount } }}</span>
                    }
                    @if (einab.imported) {
                      <span class="tag">{{ 'introPickups.guide.imported' | t }}</span>
                    }
                    @if (einab.publicGeneralLocation) {
                      <span class="tag"><i class="pi pi-map-marker" aria-hidden="true"></i>{{ einab.publicGeneralLocation }}</span>
                    }
                  </div>
                  <fs-einab-slots [slots]="einab.slots" [past]="past()" (act)="onSlot(einab, $event.action, $event.slot)" />
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </ng-template>
    </fs-data-state>

    <fs-einab-form-dialog
      [(visible)]="formOpen"
      [regionId]="regionId()"
      [timeZone]="timeZone()"
      [einab]="editing()"
      [prefill]="prefill()"
      [templates]="templates.data() ?? []"
      (saved)="einabs.reload(); templates.reload()"
    />

    <fs-member-pick-dialog
      [(visible)]="pickOpen"
      [regionId]="regionId()"
      [title]="'introPickups.guide.bookForTitle' | t"
      [busy]="bookFor.busy()"
      (picked)="pickUser($event)"
    />

    <fs-slot-comments-drawer [(slotId)]="commentSlot" [userName]="commentUser()" (changed)="einabs.reload()" />

    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="!!assigning()"
      (visibleChange)="!$event && assigning.set(null)"
      [modal]="true"
      [draggable]="false"
      [header]="'introPickups.guide.assignTitle' | t"
      [style]="{ width: 'min(28rem, 96vw)' }"
    >
      <div class="stack" data-testid="introPickups.assign">
        <p class="muted">{{ 'introPickups.guide.assignText' | t }}</p>
        <fs-data-state [state]="assignable" skeleton="form" [skeletonRows]="1" [compact]="true" data-testid="introPickups.assign.state">
          <ng-template [fsData]="assignable" let-guides>
            <p-select [options]="guides" optionLabel="displayName" optionValue="id" [ngModel]="assignTo()" (ngModelChange)="assignTo.set($event)" [placeholder]="'introPickups.guide.assignPick' | t" appendTo="body" [style]="{ width: '100%' }" [ariaLabel]="'introPickups.guide.assignPick' | t" data-testid="introPickups.assign.guide" />
          </ng-template>
        </fs-data-state>
        <div class="actions">
          <p-button [label]="'introPickups.guide.assign' | t" icon="pi pi-check" [disabled]="!assignTo()" [loading]="assign.busy()" (onClick)="assign.run()" data-testid="introPickups.assign.save" />
        </div>
      </div>
    </p-dialog>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    .switch {
      display: inline-flex;
      gap: 0.25rem;
    }
    .spacer {
      flex: 1;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .candidate {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      padding: 0.5rem 0;
      border-top: 1px solid var(--fs-border);
    }
    .candidate__main {
      display: grid;
      min-width: 0;
    }
  `,
})
export class GuideDashboardPageComponent {
  private readonly api = inject(IntroPickupGuideService);
  private readonly context = inject(RegionContextService);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);
  private readonly confirm = inject(ConfirmationService);
  protected readonly icons = CATEGORY_ICONS;
  protected readonly ics = introPickupIcs();
  protected readonly timeZone = this.context.timeZone;

  protected readonly regions = loadResource<RegionRef[]>({ loader: () => this.api.listGuideRegions() });
  protected readonly chosenRegion = signal<string | null>(null);
  protected readonly regionId = computed(() => {
    const list = this.regions.data() ?? [];
    const chosen = this.chosenRegion();
    const active = this.context.activeRegionId();
    return (
      (chosen && list.some((r) => r.id === chosen) && chosen) ||
      (active && list.some((r) => r.id === active) && active) ||
      list[0]?.id ||
      null
    );
  });
  protected readonly past = signal(false);

  protected readonly einabs = loadResource<IntroPickupDetailPage, { regionId: string; past: boolean } | undefined>({
    params: () => {
      const regionId = this.regionId();
      return regionId ? { regionId, past: this.past() } : undefined;
    },
    loader: (p) => this.api.listGuideIntroPickups({ regionId: p!.regionId, past: p!.past, size: 100 }),
  });
  protected readonly candidates = loadResource<IcalCandidate[]>({ loader: () => this.api.getIcalCandidates() });
  protected readonly templates = loadResource<PrivateInfoTemplate[]>({ loader: () => this.api.listPrivateInfoTemplates() });

  // create / edit
  protected readonly formOpen = signal(false);
  protected readonly editing = signal<IntroPickupDetail | null>(null);
  protected readonly prefill = signal<EinAbPrefill | null>(null);

  protected openCreate(): void {
    this.editing.set(null);
    this.prefill.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(einab: IntroPickupDetail): void {
    this.prefill.set(null);
    this.editing.set(einab);
    this.formOpen.set(true);
  }

  protected useCandidate(candidate: IcalCandidate): void {
    this.editing.set(null);
    this.prefill.set({ startAt: candidate.startAt, businessName: candidate.summary, privateAddress: candidate.location });
    this.formOpen.set(true);
  }

  // delete
  protected readonly remove = runMutation((introPickupId: string) => this.api.deleteIntroPickup({ introPickupId }), {
    successToastKey: 'introPickups.guide.deleted',
    onSuccess: () => this.einabs.reload(),
  });

  protected askDelete(einab: IntroPickupDetail): void {
    this.confirm.confirm({
      header: this.i18n.t('introPickups.guide.deleteTitle'),
      message: this.i18n.t('introPickups.guide.deleteText', { date: this.date(einab.startAt) }),
      acceptLabel: this.i18n.t('introPickups.guide.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => this.remove.run(einab.id),
    });
  }

  // assign another guide
  protected readonly assigning = signal<IntroPickupDetail | null>(null);
  protected readonly assignTo = signal<string | null>(null);
  protected readonly assignable = loadResource<UserSummary[], string | undefined>({
    params: () => (this.assigning() ? (this.regionId() ?? undefined) : undefined),
    loader: (regionId) => this.api.listAssignableGuides({ regionId: regionId! }),
  });

  protected openAssign(einab: IntroPickupDetail): void {
    this.assignTo.set(null);
    this.assigning.set(einab);
  }

  protected readonly assign = runMutation(
    () => this.api.assignGuide({ introPickupId: this.assigning()!.id, assignGuideRequest: { guideId: this.assignTo()! } }),
    {
      successToastKey: 'introPickups.guide.assigned',
      onSuccess: () => {
        this.assigning.set(null);
        this.einabs.reload();
      },
    },
  );

  // slot actions
  protected readonly pickOpen = signal(false);
  private readonly pickSlot = signal<string | null>(null);
  protected readonly commentSlot = signal<string | null>(null);
  protected readonly commentUser = signal('');

  protected readonly bookFor = runMutation(
    (slotId: string, userId: string) => this.api.bookSlotForUser({ slotId, bookForUserRequest: { userId } }),
    {
      successToastKey: 'introPickups.guide.bookedFor',
      onSuccess: () => {
        this.pickOpen.set(false);
        this.einabs.reload();
      },
    },
  );
  protected readonly markDone = runMutation((slotId: string) => this.api.markBookingDone({ slotId }), {
    successToastKey: 'introPickups.guide.markedDone',
    onSuccess: () => this.einabs.reload(),
  });
  protected readonly cancel = runMutation((slotId: string) => this.api.cancelBookingAsGuide({ slotId, optionalReasonBody: {} }), {
    successToastKey: 'introPickups.guide.cancelled',
    onSuccess: () => this.einabs.reload(),
  });
  protected readonly count = runMutation((slotId: string) => this.api.countCancelledBooking({ slotId }), {
    successToastKey: 'introPickups.guide.counted',
    onSuccess: () => this.einabs.reload(),
  });

  protected pickUser(user: UserSummary): void {
    const slotId = this.pickSlot();
    if (slotId) {
      this.bookFor.run(slotId, user.id);
    }
  }

  protected onSlot(einab: IntroPickupDetail, action: SlotAction, slot: IntroPickupSlot): void {
    const name = slot.bookingUser?.displayName ?? '';
    switch (action) {
      case 'bookFor':
        this.pickSlot.set(slot.id);
        this.pickOpen.set(true);
        break;
      case 'done':
        this.markDone.run(slot.id);
        break;
      case 'count':
        this.count.run(slot.id);
        break;
      case 'comments':
        this.commentUser.set(name);
        this.commentSlot.set(slot.id);
        break;
      case 'cancel':
        this.confirm.confirm({
          header: this.i18n.t('introPickups.guide.cancelTitle'),
          message: this.i18n.t('introPickups.guide.cancelText', { name, date: this.date(einab.startAt) }),
          acceptLabel: this.i18n.t('introPickups.slot.cancel'),
          rejectLabel: this.i18n.t('common.back'),
          acceptButtonStyleClass: 'p-button-danger',
          rejectButtonStyleClass: 'p-button-text p-button-secondary',
          accept: () => this.cancel.run(slot.id),
        });
        break;
    }
  }

  private date(iso: string): string {
    return this.dates.format(iso, 'dateTimeLong', this.timeZone());
  }
}
