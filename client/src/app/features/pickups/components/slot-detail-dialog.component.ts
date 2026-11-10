import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, model, output } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';

import { Conversation, MessagingService, PickupSignupDetail, PickupSlot, PickupsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';

/** Rich: managers and co-pickups (loads the sign-up detail); short: the rest of the team (no request). */
export type SlotDetailMode = 'rich' | 'short';

interface DetailParams {
  storeId: string;
  pickupAt: string;
  userId: string;
}

/**
 * PU3 slot dialog (decision 165, docs/design/ui-feedback-batch-2-proposal.md §3): a taken slot of someone else.
 * Rich mode shows the person with phone (S9, only when the server sends it), quick actions (profile, message, call,
 * copy), the sign-up state and time, counts at this store and the upcoming slots here; managers get Bestätigen /
 * Austragen in the footer. Short mode: avatar, name, state and a profile link.
 */
@Component({
  selector: 'fs-slot-detail-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, Dialog, NgTemplateOutlet, RouterLink, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [dismissableMask]="true"
      [closeAriaLabel]="'common.close' | t"
      [header]="pickupAt() | fsDate: 'dateTime' : timeZone()"
      [style]="{ width: 'min(28rem, 94vw)' }"
      [focusOnShow]="!focusConfirm()"
      (onShow)="shown.emit()"
      [attr.data-mode]="mode()"
      data-testid="stores.pickups.slotDialog"
    >
      @if (slot(); as s) {
        <div class="body">
          <div class="person">
            <fs-user-avatar [name]="s.user.displayName" [avatarUrl]="s.user.avatarUrl" [role]="s.user.role" size="lg" />
            <div class="person__text">
              <div class="person__name" data-testid="stores.pickups.slotDetail.name">{{ s.user.displayName }}</div>
              @if (mode() === 'short') {
                <ng-container [ngTemplateOutlet]="stateLine" [ngTemplateOutletContext]="{ confirmed: s.confirmed }" />
              } @else if (detail.data()?.phone; as phone) {
                <a class="person__phone" [href]="'tel:' + tel(phone)" data-testid="stores.pickups.slotDetail.phone">{{ phone }}</a>
              }
            </div>
          </div>

          <ul class="quick" [attr.aria-label]="'pickups.slotDetail.actions' | t">
            <li>
              <a class="quick__btn" [routerLink]="['/profile', s.user.id]" (click)="visible.set(false)" data-testid="stores.pickups.slotDetail.profile">
                <i class="pi pi-user" aria-hidden="true"></i>{{ 'pickups.slotDetail.profile' | t }}
              </a>
            </li>
            @if (mode() === 'rich') {
              <li>
                <button type="button" class="quick__btn" [disabled]="message.busy()" (click)="message.run(s.user.id)" data-testid="stores.pickups.slotDetail.message">
                  <i class="pi pi-envelope" aria-hidden="true"></i>{{ 'pickups.slotDetail.message' | t }}
                </button>
              </li>
              @if (detail.data()?.phone; as phone) {
                <li>
                  <a class="quick__btn" [href]="'tel:' + tel(phone)" data-testid="stores.pickups.slotDetail.call">
                    <i class="pi pi-phone" aria-hidden="true"></i>{{ 'pickups.slotDetail.call' | t }}
                  </a>
                </li>
                <li>
                  <button type="button" class="quick__btn" (click)="copy(phone)" data-testid="stores.pickups.slotDetail.copy">
                    <i class="pi pi-copy" aria-hidden="true"></i>{{ 'pickups.slotDetail.copy' | t }}
                  </button>
                </li>
              }
            }
          </ul>

          @if (mode() === 'rich') {
            <fs-data-state [state]="detail" skeleton="detail" [skeletonRows]="2" [compact]="true" testId="stores.pickups.slotDetail.state" data-testid="stores.pickups.slotDetail.state">
              <ng-template [fsData]="detail" let-d>
                <div class="rich">
                  <p class="state" [class.state--unconfirmed]="!d.confirmed" [attr.data-confirmed]="d.confirmed" data-testid="stores.pickups.slotState">
                    <i class="pi" [class.pi-check]="d.confirmed" [class.pi-hourglass]="!d.confirmed" aria-hidden="true"></i>
                    {{ (d.confirmed ? 'pickups.tab.state.confirmed' : 'pickups.tab.state.unconfirmed') | t }}
                    <small>· {{ 'pickups.slotDetail.signedUpAt' | t: { date: (d.signedUpAt | fsDate: 'dateTime' : timeZone()) } }}</small>
                  </p>
                  <dl class="facts">
                    <div class="fact" data-testid="stores.pickups.slotDetail.pickupCount">
                      <i class="pi pi-shopping-bag" aria-hidden="true"></i>
                      <dt>{{ 'pickups.slotDetail.pickupCount' | t }}</dt>
                      <dd>{{ d.pickupCount }}</dd>
                    </div>
                    <div class="fact" data-testid="stores.pickups.slotDetail.lastPickup">
                      <i class="pi pi-calendar" aria-hidden="true"></i>
                      <dt>{{ 'pickups.slotDetail.lastPickup' | t }}</dt>
                      <dd>{{ d.lastPickupAt ? (d.lastPickupAt | fsDate: 'date' : timeZone()) : ('pickups.slotDetail.never' | t) }}</dd>
                    </div>
                  </dl>
                  <details class="upcoming" data-testid="stores.pickups.slotDetail.upcoming">
                    <summary>
                      <i class="pi pi-calendar-clock" aria-hidden="true"></i>
                      {{ 'pickups.slotDetail.upcoming' | t: { count: d.upcomingSlots.length } }}
                      <i class="pi pi-chevron-down upcoming__chev" aria-hidden="true"></i>
                    </summary>
                    <ul>
                      @for (at of d.upcomingSlots; track at) {
                        <li [class.this]="at === pickupAt()" data-testid="stores.pickups.slotDetail.upcomingItem">
                          {{ at | fsDate: 'dateTime' : timeZone() }}
                          @if (at === pickupAt()) {
                            – {{ 'pickups.slotDetail.thisOne' | t }}
                          }
                        </li>
                      }
                    </ul>
                  </details>
                </div>
              </ng-template>
            </fs-data-state>
          }
        </div>
      }
      <ng-template #footer>
        <div class="foot">
          <p-button [label]="'common.close' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" data-testid="stores.pickups.slotDetail.close" />
          @if (mode() === 'rich' && slot(); as s) {
            @if (mayRemove()) {
              <p-button
                [label]="'pickups.tab.removeSlot' | t"
                icon="pi pi-user-minus"
                severity="danger"
                [outlined]="true"
                (onClick)="remove.emit()"
                data-testid="stores.pickups.removeSlot"
              />
            }
            @if (mayConfirm() && !s.confirmed) {
              <p-button
                [label]="'pickups.tab.confirmSlot' | t"
                icon="pi pi-check"
                [loading]="confirmBusy()"
                (onClick)="confirmed.emit()"
                data-testid="stores.pickups.confirmSlot"
              />
            }
          }
        </div>
      </ng-template>
    </p-dialog>

    <ng-template #stateLine let-confirmed="confirmed">
      <p class="state state--small" [class.state--unconfirmed]="!confirmed" [attr.data-confirmed]="confirmed" data-testid="stores.pickups.slotState">
        <i class="pi" [class.pi-check]="confirmed" [class.pi-hourglass]="!confirmed" aria-hidden="true"></i>
        {{ (confirmed ? 'pickups.tab.state.confirmed' : 'pickups.tab.state.unconfirmed') | t }}
      </p>
    </ng-template>
  `,
  styles: `
    .body { display: grid; gap: 0.85rem; }
    .person { display: flex; align-items: center; gap: 0.85rem; }
    .person__text { display: grid; gap: 0.15rem; min-width: 0; }
    .person__name { font-size: 1.1rem; font-weight: 650; line-height: 1.2; overflow-wrap: anywhere; }
    .person__phone { color: var(--fs-text-muted); font-variant-numeric: tabular-nums; text-decoration: none; }
    .person__phone:hover, .person__phone:focus-visible { text-decoration: underline; }
    .quick { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0; padding: 0; list-style: none; }
    .quick__btn {
      display: inline-flex; align-items: center; gap: 0.35rem; min-height: var(--fs-target-sm);
      padding: 0.3rem 0.75rem; border: 1px solid var(--fs-border-strong); border-radius: 999px;
      background: var(--fs-surface); color: inherit; font: inherit; font-size: 0.82rem; font-weight: 600;
      text-decoration: none; cursor: pointer;
    }
    .quick__btn:hover { background: var(--fs-surface-muted); }
    .quick__btn .pi { font-size: 0.9rem; color: var(--fs-text-muted); }
    .rich { display: grid; gap: 0.85rem; }
    .state { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; margin: 0; color: var(--fs-primary-strong); font-weight: 650; }
    .state--unconfirmed { color: var(--fs-warning-text); }
    .state--small { font-size: 0.9rem; }
    .state small { color: var(--fs-text-muted); font-weight: 400; }
    .facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.4rem; margin: 0; }
    .fact {
      display: grid; grid-template-columns: auto minmax(0, 1fr); column-gap: 0.5rem; align-items: center;
      padding: 0.4rem 0.65rem; border-radius: var(--fs-radius-sm); background: var(--fs-surface-muted);
    }
    .fact .pi { grid-row: 1 / span 2; color: var(--fs-text-muted); }
    .fact dd { grid-column: 2; grid-row: 1; margin: 0; font-weight: 650; font-variant-numeric: tabular-nums; line-height: 1.2; }
    .fact dt { grid-column: 2; grid-row: 2; font-size: 0.72rem; color: var(--fs-text-muted); line-height: 1.2; }
    .upcoming { border: 1px solid var(--fs-border); border-radius: var(--fs-radius-sm); }
    .upcoming summary {
      display: flex; align-items: center; gap: 0.5rem; min-height: var(--fs-target-sm); padding: 0.4rem 0.75rem;
      font-weight: 600; font-size: 0.9rem; cursor: pointer; list-style: none;
    }
    .upcoming summary::-webkit-details-marker { display: none; }
    .upcoming__chev { margin-left: auto; transition: transform 150ms; }
    .upcoming[open] .upcoming__chev { transform: rotate(180deg); }
    .upcoming ul { margin: 0; padding: 0 0.75rem 0.6rem 2.25rem; font-size: 0.9rem; font-variant-numeric: tabular-nums; }
    .upcoming li { padding: 0.15rem 0; }
    .upcoming .this { font-weight: 650; color: var(--fs-primary-strong); }
    .foot { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; width: 100%; }
    @media (max-width: 47.99rem) {
      .foot p-button { flex: 1 1 auto; }
      .foot p-button ::ng-deep button { width: 100%; }
    }
  `,
})
export class SlotDetailDialogComponent {
  private readonly api = inject(PickupsService);
  private readonly messaging = inject(MessagingService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  readonly storeId = input<string | undefined>(undefined);
  readonly pickupAt = input<string | undefined>(undefined);
  readonly slot = input<PickupSlot | undefined>(undefined);
  readonly mode = input<SlotDetailMode>('short');
  readonly timeZone = input<string | undefined>(undefined);
  /** Managers: Bestätigen (unconfirmed slots) and Austragen in the rich footer. */
  readonly mayConfirm = input(false);
  readonly mayRemove = input(false);
  readonly confirmBusy = input(false);
  /** From the ✓ of a split pill: the dialog leaves the focus to the caller (Bestätigen). */
  readonly focusConfirm = input(false);

  readonly confirmed = output<void>();
  readonly remove = output<void>();
  readonly shown = output<void>();

  protected readonly detail = loadResource<PickupSignupDetail, DetailParams>({
    params: () => {
      const storeId = this.storeId();
      const pickupAt = this.pickupAt();
      const userId = this.slot()?.user.id;
      return this.visible() && this.mode() === 'rich' && storeId && pickupAt && userId
        ? { storeId, pickupAt, userId }
        : undefined;
    },
    loader: (p) => this.api.getPickupSignupDetail(p),
    isEmpty: () => false,
  });

  protected readonly message = runMutation(
    (userId: string) => this.messaging.lookupConversation({ conversationLookup: { memberIds: [userId] } }),
    {
      onSuccess: (conversation: Conversation) => {
        this.visible.set(false);
        void this.router.navigate(['/messages', conversation.id]);
      },
    },
  );

  protected tel(phone: string): string {
    return phone.replace(/[^\d+]/g, '');
  }

  protected copy(phone: string): void {
    void navigator.clipboard?.writeText(phone).then(() =>
      this.messages.add({ severity: 'success', summary: this.i18n.t('pickups.slotDetail.copied'), life: 3000 }),
    );
  }
}
