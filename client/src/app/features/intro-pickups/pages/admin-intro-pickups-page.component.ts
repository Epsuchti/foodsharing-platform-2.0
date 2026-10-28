import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import {
  GuideBooking,
  GuideBookingPage,
  GuideStatus,
  GuideSummary,
  GuideSummaryPage,
  IntroPickupAdminService,
  IntroPickupCategory,
  IntroPickupDetail,
  IntroPickupDetailPage,
  Role,
  UserSummary,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { SessionService } from '../../../core/auth/session.service';
import { RegionContextService } from '../../../core/region/region-context.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { MemberPickDialogComponent } from '../components/member-pick-dialog.component';
import { SlotCommentsDrawerComponent } from '../components/slot-comments-drawer.component';
import { INTRO_CATEGORIES, zonedLocalToIso } from '../intro-category';

type Tab = 'einabs' | 'bookings' | 'guides';

/**
 * `/regions/:regionId/intro-pickups/admin` (EA17, EA18; CAN_MANAGE_USERS): all upcoming EinAbs of the region with
 * delete-with-reason, all bookings with the counted switch and admin comments, intro pickups added afterwards
 * ("historical") for a member, and the guides with enable / disable. 403 → forbidden state.
 */
@Component({
  selector: 'fs-admin-intro-pickups-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Checkbox,
    DateTimePipe,
    Dialog,
    FormsModule,
    InputText,
    MemberPickDialogComponent,
    PageHeaderComponent,
    RequiredMessageDialogComponent,
    RouterLink,
    Select,
    SlotCommentsDrawerComponent,
    Textarea,
    ToggleSwitch,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'introPickups.admin.title' | t" [subtitle]="'introPickups.admin.subtitle' | t" icon="pi pi-shield" [backLink]="['/regions', regionId()]" [backLabel]="'introPickups.admin.backToRegion' | t" />
    <div class="toolbar">
      <div class="switch" role="group" [attr.aria-label]="'introPickups.admin.title' | t">
        @for (t of tabs; track t) {
          <p-button [label]="'introPickups.admin.tab.' + t | t" size="small" [outlined]="tab() !== t" (onClick)="tab.set(t)" [attr.data-testid]="'introPickups.admin.tab.' + t" />
        }
      </div>
      <span class="spacer"></span>
      <a [routerLink]="['/regions', regionId(), 'newcomers']" class="fs-btn fs-btn--outlined fs-btn--sm" data-testid="introPickups.admin.funnel"><i class="pi pi-filter" aria-hidden="true"></i>{{ 'introPickups.admin.funnelLink' | t }}</a>
      <a [routerLink]="['/regions', regionId(), 'intro-pickups', 'import']" class="fs-btn fs-btn--outlined fs-btn--sm" data-testid="introPickups.admin.import"><i class="pi pi-upload" aria-hidden="true"></i>{{ 'introPickups.admin.importLink' | t }}</a>
      <p-button [label]="'introPickups.admin.historical' | t" icon="pi pi-history" size="small" (onClick)="pickOpen.set(true)" data-testid="introPickups.admin.historicalOpen" />
    </div>

    @switch (tab()) {
      @case ('einabs') {
        <fs-data-state [state]="einabs" skeleton="table" emptyIcon="pi pi-calendar" [emptyTitle]="'introPickups.admin.noEinabs' | t" [askWho]="'introPickups.admin.askWho' | t" data-testid="introPickups.admin.einabsState">
          <ng-template [fsData]="einabs" let-page>
            <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'introPickups.scrollTable' | t">
              <table class="table">
                <thead>
                  <tr>
                    <th>{{ 'introPickups.admin.col.date' | t }}</th>
                    <th>{{ 'introPickups.admin.col.einab' | t }}</th>
                    <th>{{ 'introPickups.admin.col.guide' | t }}</th>
                    <th class="num">{{ 'introPickups.admin.col.booked' | t }}</th>
                    <th><span class="fs-visually-hidden">{{ 'introPickups.admin.col.actions' | t }}</span></th>
                  </tr>
                </thead>
                <tbody>
                  @for (einab of page.items; track einab.id) {
                    <tr [attr.data-id]="einab.id" data-testid="introPickups.admin.einab">
                      <td>{{ einab.startAt | fsDate: 'dateTime' }}</td>
                      <td>{{ 'common.introCategory.' + einab.category | t }}@if (einab.businessName) { · {{ einab.businessName }} }</td>
                      <td>{{ einab.guide.displayName }}</td>
                      <td class="num">{{ bookedCount(einab) }} / {{ einab.slots.length }}</td>
                      <td class="row-actions">
                        @if (!einab.booked) {
                          <p-button icon="pi pi-trash" [label]="'introPickups.guide.delete' | t" size="small" [text]="true" severity="danger" (onClick)="deleting.set(einab)" data-testid="introPickups.admin.delete" />
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </ng-template>
        </fs-data-state>
      }
      @case ('bookings') {
        <div class="actions">
          <p-checkbox inputId="adm-past" [binary]="true" [ngModel]="pastBookings()" (ngModelChange)="pastBookings.set($event)" data-testid="introPickups.admin.pastBookings" />
          <label for="adm-past">{{ 'introPickups.admin.showPast' | t }}</label>
        </div>
        <fs-data-state [state]="bookings" skeleton="table" emptyIcon="pi pi-users" [emptyTitle]="'introPickups.admin.noBookings' | t" [askWho]="'introPickups.admin.askWho' | t" data-testid="introPickups.admin.bookingsState">
          <ng-template [fsData]="bookings" let-page>
            <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'introPickups.scrollTable' | t">
              <table class="table">
                <thead>
                  <tr>
                    <th>{{ 'introPickups.admin.col.person' | t }}</th>
                    <th>{{ 'introPickups.admin.col.einab' | t }}</th>
                    <th>{{ 'introPickups.admin.col.status' | t }}</th>
                    <th>{{ 'introPickups.admin.col.counted' | t }}</th>
                    <th><span class="fs-visually-hidden">{{ 'introPickups.admin.col.actions' | t }}</span></th>
                  </tr>
                </thead>
                <tbody>
                  @for (booking of page.items; track booking.slotId) {
                    <tr [attr.data-status]="booking.status" data-testid="introPickups.admin.booking">
                      <td><fs-user-chip [name]="booking.user.displayName" [avatarUrl]="booking.user.avatarUrl" [foodsaverNumber]="booking.user.foodsaverNumber" /></td>
                      <td>
                        {{ 'common.introCategory.' + booking.introPickup.category | t }}
                        <span class="muted">{{ booking.introPickup.startAt | fsDate: 'dateTime' }} · {{ booking.introPickup.guide.displayName }}</span>
                      </td>
                      <td><span [class]="'tag tag--' + booking.status">{{ 'introPickups.status.' + booking.status | t }}</span></td>
                      <td>
                        @if (booking.status === 'DONE' || booking.status === 'CANCELLED') {
                          <p-toggleswitch [ngModel]="!!booking.counted" (ngModelChange)="setCounted.run(booking, $event)" [ariaLabel]="'introPickups.admin.col.counted' | t" data-testid="introPickups.admin.counted" />
                        }
                      </td>
                      <td class="row-actions">
                        <p-button icon="pi pi-comments" [ariaLabel]="('introPickups.slot.comments' | t) + ' (' + (booking.commentCount ?? 0) + ')'" size="small" [text]="true" severity="secondary" (onClick)="openComments(booking)" data-testid="introPickups.admin.comments" />
                        @if (booking.commentCount) {
                          <span class="count" aria-hidden="true">{{ booking.commentCount }}</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </ng-template>
        </fs-data-state>
      }
      @case ('guides') {
        <div class="actions">
          <p-button [label]="'introPickups.admin.addGuide' | t" icon="pi pi-user-plus" size="small" [outlined]="true" (onClick)="guidePickOpen.set(true)" data-testid="introPickups.admin.addGuide" />
        </div>
        <fs-data-state [state]="guides" skeleton="table" emptyIcon="pi pi-compass" [emptyTitle]="'introPickups.admin.noGuides' | t" [askWho]="'introPickups.admin.askWho' | t" data-testid="introPickups.admin.guidesState">
          <ng-template [fsData]="guides" let-page>
            <ul class="list">
              @for (guide of page.items; track guide.user.id) {
                <li class="fs-card guide" [attr.data-active]="guide.active" [attr.data-status]="guide.status" [attr.data-admin]="guide.canManageUsers" data-testid="introPickups.admin.guide">
                  <fs-user-chip [name]="guide.user.displayName" [avatarUrl]="guide.user.avatarUrl" [subtitle]="'introPickups.admin.guideStats' | t: { count: guide.introPickupCount }" />
                  <span class="tags">
                    @if (guide.canManageUsers) {
                      <span class="tag" data-testid="introPickups.admin.managerTag">{{ 'introPickups.admin.manager' | t }}</span>
                    }
                    <span
                      class="tag"
                      [class.tag--ok]="guide.status === 'ACTIVE'"
                      [class.tag--warn]="guide.status === 'PAUSED'"
                      [class.tag--danger]="guide.status === 'DISABLED'"
                      data-testid="introPickups.admin.guideStatus"
                    >
                      {{ 'introPickups.admin.status.' + guide.status | t }}
                    </span>
                  </span>
                  <span class="guide__actions">
                    @if (mayAdministrate()) {
                      <p-button
                        [label]="(guide.canManageUsers ? 'introPickups.admin.revokeAdmin' : 'introPickups.admin.grantAdmin') | t"
                        icon="pi pi-shield"
                        size="small"
                        [text]="true"
                        [loading]="setAdmin.busy() && adminTarget() === guide.user.id"
                        (onClick)="setAdmin.run(guide)"
                        data-testid="introPickups.admin.toggleAdmin"
                      />
                    }
                    <p-button
                      [label]="(guide.status === 'DISABLED' ? 'introPickups.admin.enable' : 'introPickups.admin.disable') | t"
                      size="small"
                      [outlined]="true"
                      [severity]="guide.status === 'DISABLED' ? 'primary' : 'danger'"
                      (onClick)="askEnable(guide)"
                      data-testid="introPickups.admin.toggleGuide"
                    />
                  </span>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      }
    }

    <fs-required-message-dialog
      [visible]="!!deleting()"
      (visibleChange)="!$event && deleting.set(null)"
      [title]="'introPickups.admin.deleteTitle' | t"
      [text]="'introPickups.admin.deleteText' | t"
      [label]="'introPickups.admin.reason' | t"
      [confirmLabel]="'introPickups.guide.delete' | t"
      severity="danger"
      [busy]="remove.busy()"
      testId="introPickups.admin.deleteDialog"
      (confirm)="remove.run($event)"
    />
    <fs-required-message-dialog
      [visible]="!!toggling()"
      (visibleChange)="!$event && toggling.set(null)"
      [title]="(toggling()?.active ? 'introPickups.admin.disable' : 'introPickups.admin.enable') | t"
      [text]="'introPickups.admin.enableText' | t: { name: toggling()?.user?.displayName ?? '' }"
      [label]="'introPickups.admin.reason' | t"
      [busy]="setEnabled.busy()"
      [minLength]="3"
      testId="introPickups.admin.enableDialog"
      (confirm)="setEnabled.run($event)"
    />
    <fs-member-pick-dialog [(visible)]="guidePickOpen" [regionId]="regionId()" [verified]="true" [title]="'introPickups.admin.addGuide' | t" (picked)="addGuide($event)" />
    <fs-member-pick-dialog [(visible)]="pickOpen" [regionId]="regionId()" [title]="'introPickups.admin.historicalPick' | t" (picked)="startHistorical($event)" />
    <fs-slot-comments-drawer [(slotId)]="commentSlot" [userName]="commentUser()" [admin]="true" (changed)="bookings.reload()" />

    <p-dialog
      [visible]="!!historicalFor()"
      (visibleChange)="!$event && historicalFor.set(null)"
      [modal]="true"
      [draggable]="false"
      [header]="'introPickups.admin.historicalTitle' | t: { name: historicalFor()?.displayName ?? '' }"
      [style]="{ width: 'min(32rem, 96vw)' }"
    >
      <div class="form" data-testid="introPickups.admin.historicalForm">
        <div class="form__row">
          <div class="field">
            <label for="hist-cat">{{ 'introPickups.form.category' | t }}</label>
            <p-select inputId="hist-cat" [options]="categoryOptions()" optionLabel="label" optionValue="value" [(ngModel)]="hist.category" appendTo="body" [style]="{ width: '100%' }" data-testid="introPickups.admin.historicalCategory" />
          </div>
          <div class="field">
            <label for="hist-start">{{ 'introPickups.form.start' | t }}</label>
            <input id="hist-start" type="datetime-local" class="native" [(ngModel)]="hist.start" data-testid="introPickups.admin.historicalStart" />
          </div>
        </div>
        <div class="field">
          <label for="hist-business">{{ 'introPickups.form.businessName' | t }}</label>
          <input pInputText id="hist-business" [(ngModel)]="hist.businessName" data-testid="introPickups.admin.historicalBusiness" />
        </div>
        <div class="field field--inline">
          <p-checkbox inputId="hist-ft" [binary]="true" [(ngModel)]="hist.visitFairteiler" />
          <label for="hist-ft">{{ 'introPickups.form.fairteiler' | t }}</label>
        </div>
        <div class="field">
          <label for="hist-comment">{{ 'introPickups.admin.comment' | t }}</label>
          <textarea pTextarea id="hist-comment" rows="2" [(ngModel)]="hist.comment"></textarea>
        </div>
      </div>
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" [text]="true" severity="secondary" (onClick)="historicalFor.set(null)" />
        <p-button [label]="'common.save' | t" icon="pi pi-check" [loading]="createHistorical.busy()" (onClick)="createHistorical.run()" data-testid="introPickups.admin.historicalSave" />
      </ng-template>
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
      flex-wrap: wrap;
      gap: 0.25rem;
    }
    .spacer {
      flex: 1;
    }
    .guide {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
    }
    .guide__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem;
    }
    .count {
      align-self: center;
      color: var(--fs-text-muted);
      font-size: 0.8rem;
      font-weight: 700;
    }
    td .muted {
      display: block;
    }
  `,
})
export class AdminIntroPickupsPageComponent {
  private readonly api = inject(IntroPickupAdminService);
  private readonly i18n = inject(I18nService);
  private readonly context = inject(RegionContextService);
  private readonly session = inject(SessionService);

  readonly regionId = input.required<string>();
  protected readonly tabs: readonly Tab[] = ['einabs', 'bookings', 'guides'];
  protected readonly tab = signal<Tab>('einabs');
  protected readonly pastBookings = signal(false);

  protected readonly einabs = loadResource<IntroPickupDetailPage, string>({
    params: () => this.regionId(),
    loader: (regionId) => this.api.listRegionIntroPickups({ regionId, past: false, size: 200 }),
  });
  protected readonly bookings = loadResource<GuideBookingPage, { regionId: string; past: boolean } | undefined>({
    params: () => (this.tab() === 'bookings' ? { regionId: this.regionId(), past: this.pastBookings() } : undefined),
    loader: (p) => this.api.listRegionBookings({ regionId: p!.regionId, past: p!.past, size: 200 }),
  });
  protected readonly guides = loadResource<GuideSummaryPage, string | undefined>({
    params: () => (this.tab() === 'guides' ? this.regionId() : undefined),
    loader: (regionId) => this.api.listGuides({ regionId: regionId!, size: 200 }),
  });

  protected bookedCount(einab: IntroPickupDetail): number {
    return einab.slots.filter((slot) => slot.status === 'BOOKED' || slot.status === 'DONE').length;
  }

  // delete with reason
  protected readonly deleting = signal<IntroPickupDetail | null>(null);
  protected readonly remove = runMutation(
    (reason: string) =>
      this.api.deleteIntroPickupAsAdmin({ regionId: this.regionId(), introPickupId: this.deleting()!.id, reasonBody: { reason } }),
    {
      successToastKey: 'introPickups.guide.deleted',
      onSuccess: () => {
        this.deleting.set(null);
        this.einabs.reload();
      },
    },
  );

  // counted + admin comments
  protected readonly setCounted = runMutation(
    (booking: GuideBooking, counted: boolean) =>
      this.api.setBookingCounted({ slotId: booking.slotId, bookingCountedUpdate: { counted } }),
    { successToastKey: 'introPickups.admin.countedSaved', onSuccess: () => this.bookings.reload(), onError: () => this.bookings.reload() },
  );
  protected readonly commentSlot = signal<string | null>(null);
  protected readonly commentUser = signal('');
  protected openComments(booking: GuideBooking): void {
    this.commentUser.set(booking.user.displayName);
    this.commentSlot.set(booking.slotId);
  }

  // guides
  protected readonly toggling = signal<GuideSummary | null>(null);
  protected readonly guidePickOpen = signal(false);
  protected addGuide(user: UserSummary): void {
    this.guidePickOpen.set(false);
    this.toggling.set({ user, active: false, status: GuideStatus.Disabled, canManageUsers: false, introPickupCount: 0 });
  }
  /** Region administrators (and ORGA) grant or revoke a guide's user-management right (EA17, `setGuideAdmin`). */
  protected readonly mayAdministrate = computed(
    () =>
      this.session.user()?.role === Role.Orga ||
      this.context.regions().some((region) => region.id === this.regionId() && region.admin),
  );
  protected readonly adminTarget = signal<string | null>(null);
  protected readonly setAdmin = runMutation(
    (guide: GuideSummary) => {
      this.adminTarget.set(guide.user.id);
      return this.api.setGuideAdmin({
        regionId: this.regionId(),
        userId: guide.user.id,
        guideAdminUpdate: { admin: !guide.canManageUsers },
      });
    },
    { successToastKey: 'introPickups.admin.adminSaved', onSuccess: () => this.guides.reload() },
  );
  protected askEnable(guide: GuideSummary): void {
    this.toggling.set(guide);
  }
  protected readonly setEnabled = runMutation(
    (reason: string) => {
      const guide = this.toggling()!;
      return this.api.setGuideEnabled({
        regionId: this.regionId(),
        userId: guide.user.id,
        guideEnabledUpdate: { enabled: guide.status === GuideStatus.Disabled, reason },
      });
    },
    {
      successToastKey: 'introPickups.admin.guideSaved',
      onSuccess: () => {
        this.toggling.set(null);
        this.guides.reload();
      },
    },
  );

  // historical EinAb for a member
  protected readonly pickOpen = signal(false);
  protected readonly historicalFor = signal<UserSummary | null>(null);
  protected hist = { category: IntroPickupCategory.Supermarket as IntroPickupCategory, start: '', businessName: '', visitFairteiler: false, comment: '' };
  protected readonly categoryOptions = computed(() =>
    INTRO_CATEGORIES.map((value) => ({ value, label: this.i18n.t('common.introCategory.' + value) })),
  );

  protected startHistorical(user: UserSummary): void {
    this.pickOpen.set(false);
    this.hist = { category: IntroPickupCategory.Supermarket, start: '', businessName: '', visitFairteiler: false, comment: '' };
    this.historicalFor.set(user);
  }

  protected readonly createHistorical = runMutation(
    () =>
      this.api.createHistoricalIntroPickup({
        regionId: this.regionId(),
        userId: this.historicalFor()!.id,
        historicalIntroPickupCreate: {
          category: this.hist.category,
          startAt: zonedLocalToIso(this.hist.start, this.context.timeZone()),
          businessName: this.hist.businessName.trim(),
          visitFairteiler: this.hist.visitFairteiler,
          comment: this.hist.comment.trim() || undefined,
        },
      }),
    {
      successToastKey: 'introPickups.admin.historicalSaved',
      onSuccess: () => {
        this.historicalFor.set(null);
        this.bookings.reload();
      },
    },
  );
}
