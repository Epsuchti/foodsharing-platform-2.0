import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { IntroPickupSlot } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

export type SlotAction = 'bookFor' | 'done' | 'cancel' | 'count' | 'comments';

/** The slots of one EinAb with the actions a guide (or user manager) may take on each (EA12–EA14). */
@Component({
  selector: 'fs-einab-slots',
  imports: [Button, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="slots">
      @for (slot of slots(); track slot.id; let i = $index) {
        <li class="slot" [attr.data-status]="slot.status" data-testid="introPickups.slot">
          <span class="slot__no" aria-hidden="true">{{ i + 1 }}</span>
          <span class="slot__who">
            @if (slot.bookingUser; as user) {
              <fs-user-chip [name]="user.displayName" [avatarUrl]="user.avatarUrl" [foodsaverNumber]="user.foodsaverNumber" [subtitle]="user.phone" [verified]="user.verified" />
            } @else {
              <span class="muted">{{ 'introPickups.slot.free' | t }}</span>
            }
          </span>
          <span [class]="'tag tag--' + slot.status" data-testid="introPickups.slot.status">{{ 'introPickups.status.' + slot.status | t }}</span>
          <span class="row-actions">
            @if (slot.status === 'AVAILABLE' && !past()) {
              <p-button icon="pi pi-user-plus" [label]="'introPickups.slot.bookFor' | t" size="small" [text]="true" (onClick)="act.emit({ action: 'bookFor', slot })" data-testid="introPickups.slot.bookFor" />
            }
            @if (slot.status === 'BOOKED' && past()) {
              <p-button icon="pi pi-check" [label]="'introPickups.slot.done' | t" size="small" [outlined]="true" (onClick)="act.emit({ action: 'done', slot })" data-testid="introPickups.slot.done" />
            }
            @if (slot.status === 'BOOKED' && !past()) {
              <p-button icon="pi pi-times" [label]="'introPickups.slot.cancel' | t" size="small" [text]="true" severity="danger" (onClick)="act.emit({ action: 'cancel', slot })" data-testid="introPickups.slot.cancel" />
            }
            @if (slot.status === 'CANCELLED' && slot.bookingUser && past()) {
              <p-button icon="pi pi-plus-circle" [label]="'introPickups.slot.count' | t" size="small" [text]="true" (onClick)="act.emit({ action: 'count', slot })" data-testid="introPickups.slot.count" />
            }
            @if (slot.bookingUser) {
              <p-button
                icon="pi pi-comments"
                [ariaLabel]="('introPickups.slot.comments' | t) + ' (' + (slot.commentCount ?? 0) + ')'"
                size="small"
                [text]="true"
                severity="secondary"
                (onClick)="act.emit({ action: 'comments', slot })"
                data-testid="introPickups.slot.comments"
              />
              @if (slot.commentCount) {
                <span class="count" aria-hidden="true" data-testid="introPickups.slot.commentCount">{{ slot.commentCount }}</span>
              }
            }
          </span>
        </li>
      }
    </ul>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    :host {
      display: block;
      width: auto;
      margin: 0;
    }
    .slots {
      display: grid;
      gap: 0.4rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .slot {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.4rem 0.75rem;
      padding: 0.5rem 0.6rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
    }
    .slot__no {
      display: grid;
      place-items: center;
      width: 1.6rem;
      height: 1.6rem;
      border-radius: 50%;
      background: var(--fs-surface);
      color: var(--fs-text-muted);
      font-size: 0.8rem;
      font-weight: 700;
    }
    .slot__who {
      min-width: 0;
    }
    .count {
      align-self: center;
      margin-left: -0.35rem;
      color: var(--fs-text-muted);
      font-size: 0.8rem;
      font-weight: 700;
    }
    .slot .row-actions {
      grid-column: 1 / -1;
      justify-content: flex-start;
    }
    @media (min-width: 48rem) {
      .slot {
        grid-template-columns: auto minmax(0, 1fr) auto auto;
      }
      .slot .row-actions {
        grid-column: auto;
        justify-content: flex-end;
      }
    }
  `,
})
export class EinAbSlotsComponent {
  readonly slots = input.required<IntroPickupSlot[]>();
  readonly past = input(false);
  readonly act = output<{ action: SlotAction; slot: IntroPickupSlot }>();
}
