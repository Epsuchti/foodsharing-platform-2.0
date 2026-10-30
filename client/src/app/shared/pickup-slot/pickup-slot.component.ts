import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { PickupSlot } from '../../api';
import { TPipe } from '../../core/i18n/t.pipe';
import { UserAvatarComponent } from '../user-chip/user-avatar.component';

/**
 * The slots of one pickup (PU3): an avatar per taken slot (confirmed solid, unconfirmed dashed), a "+" per free
 * slot. `slotClick` fires for a taken slot when `slotsClickable` (own slot, or managers), `freeClick` for a free one
 * when `freeClickable`.
 *
 * ```html
 * <fs-pickup-slot [slotCount]="p.slotCount" [slots]="p.occupiedSlots" [myId]="me" (freeClick)="signUp(p)" />
 * ```
 */
@Component({
  selector: 'fs-pickup-slot',
  imports: [TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="slots" [attr.aria-label]="'shared.pickupSlot.label' | t: { taken: slots().length, total: slotCount() }">
      @for (slot of slots(); track slot.user.id) {
        <li>
          <button
            type="button"
            class="slot slot--taken"
            [class.slot--unconfirmed]="!slot.confirmed"
            [class.slot--mine]="slot.user.id === myId()"
            [disabled]="!slotsClickable() && slot.user.id !== myId()"
            [attr.title]="slot.user.displayName"
            [attr.aria-label]="
              (slot.confirmed ? 'shared.pickupSlot.confirmed' : 'shared.pickupSlot.unconfirmed') | t: { name: slot.user.displayName }
            "
            [attr.data-user]="slot.user.displayName"
            [attr.data-confirmed]="slot.confirmed"
            (click)="slotClick.emit(slot)"
            [attr.data-testid]="testId() + '.taken'"
          >
            <fs-user-avatar [name]="slot.user.displayName" [avatarUrl]="slot.user.avatarUrl" size="sm" />
          </button>
        </li>
      }
      @for (free of freeSlots(); track $index; let first = $first) {
        <li>
          <!-- the first free slot names itself ("Eintragen" / "frei"), the others stay compact (M7 R2 #16) -->
          <button
            type="button"
            class="slot slot--free"
            [class.slot--labelled]="first"
            [disabled]="!freeClickable()"
            [attr.aria-label]="
              freeClickable()
                ? ('shared.pickupSlot.free' | t)
                : first
                  ? ('shared.pickupSlot.freeShort' | t: { count: freeSlots().length })
                  : ('shared.pickupSlot.freeOnly' | t)
            "
            (click)="freeClick.emit()"
            [attr.data-testid]="testId() + '.free'"
          >
            @if (freeClickable()) {
              <i class="pi pi-plus" aria-hidden="true"></i>
            }
            @if (first) {
              <span class="slot__label" aria-hidden="true">{{ (freeClickable() ? 'shared.pickupSlot.signUp' : 'shared.pickupSlot.freeShort') | t: { count: freeSlots().length } }}</span>
            }
          </button>
        </li>
      }
    </ul>
  `,
  styles: `
    .slots { display: flex; flex-wrap: wrap; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .slot {
      display: inline-grid; place-items: center; width: 2.4rem; height: 2.4rem; padding: 0; border-radius: 999px;
      border: 2px solid transparent; background: none; cursor: pointer;
    }
    .slot:disabled { cursor: default; }
    .slot--taken { border-color: var(--fs-primary); }
    .slot--unconfirmed { border-style: dashed; border-color: var(--fs-warning-border, var(--fs-border)); }
    .slot--mine { box-shadow: 0 0 0 3px var(--fs-primary-soft), 0 0 0 4px var(--fs-primary); }
    .slot--free {
      border: 2px dashed var(--fs-border); color: var(--fs-text-muted); background: var(--fs-surface);
    }
    .slot--free:not(:disabled):hover { border-color: var(--fs-primary); color: var(--fs-primary-strong); }
    .slot--free:not(:disabled) { color: var(--fs-primary-strong); border-color: color-mix(in srgb, var(--fs-primary) 55%, var(--fs-border)); }
    .slot--labelled {
      display: inline-flex; gap: 0.35rem; width: auto; padding: 0 0.8rem; font: inherit; font-size: 0.85rem; font-weight: 650;
    }
    .slot--labelled:disabled { color: var(--fs-text-muted); }
    .slot__label { white-space: nowrap; }
    @media (pointer: coarse) {
      .slot { width: 2.75rem; height: 2.75rem; }
      .slot--labelled { width: auto; }
    }
    .slot:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
  `,
})
export class PickupSlotComponent {
  readonly slotCount = input.required<number>();
  readonly slots = input<PickupSlot[]>([]);
  readonly myId = input<string | null | undefined>(undefined);
  readonly freeClickable = input(false);
  readonly slotsClickable = input(false);
  readonly testId = input('shared.pickupSlot');
  readonly freeClick = output<void>();
  readonly slotClick = output<PickupSlot>();

  protected readonly freeSlots = computed(() => Array.from({ length: Math.max(0, this.slotCount() - this.slots().length) }));
}
