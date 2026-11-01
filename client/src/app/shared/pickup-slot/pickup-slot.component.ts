import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { PickupSlot } from '../../api';
import { TPipe } from '../../core/i18n/t.pipe';
import { UserAvatarComponent } from '../user-chip/user-avatar.component';

type TakenKind = 'compact' | 'mine' | 'split';

interface TakenSlot {
  slot: PickupSlot;
  kind: TakenKind;
  mine: boolean;
  firstName: string;
  labelKey: string;
}

/** `displayName` up to the first space ("Marco Bernasconi" → "Marco"); the full name stays in `title` and the a11y name. */
export function firstName(displayName: string): string {
  const trimmed = displayName.trim();
  const space = trimmed.indexOf(' ');
  return space > 0 ? trimmed.slice(0, space) : trimmed;
}

/**
 * The slots of one pickup (PU3, docs/design/pickup-slots-proposal.md). Every taken slot carries two cues: the ring
 * (solid = confirmed, dashed = unconfirmed) and a badge icon (check / hourglass). Your own slot is a pill with the
 * word "Du"; for `confirmable` viewers (managers) an unconfirmed slot is a split pill: first name + a ✓ button.
 * `slotClick` fires for a taken slot when `slotsClickable` (own slot, or managers), `confirmClick` for the ✓,
 * `freeClick` for a free slot when `freeClickable`.
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
      @for (item of taken(); track item.slot.user.id) {
        <li>
          @switch (item.kind) {
            @case ('mine') {
              <button
                type="button"
                class="slot slot--taken slot--pill slot--mine"
                [class.slot--unconfirmed]="!item.slot.confirmed"
                [attr.title]="item.slot.user.displayName"
                [attr.aria-label]="item.labelKey | t: { name: item.slot.user.displayName }"
                [attr.data-user]="item.slot.user.displayName"
                [attr.data-confirmed]="item.slot.confirmed"
                data-mine="true"
                (click)="slotClick.emit(item.slot)"
                [attr.data-testid]="testId() + '.taken'"
              >
                <fs-user-avatar [name]="item.slot.user.displayName" [avatarUrl]="item.slot.user.avatarUrl" size="xs" />
                <span class="slot__label" aria-hidden="true">{{ 'shared.pickupSlot.you' | t }}</span>
                <i class="pi slot__icon" [class.pi-check]="item.slot.confirmed" [class.pi-hourglass]="!item.slot.confirmed" aria-hidden="true"></i>
              </button>
            }
            @case ('split') {
              <div class="split" role="group" [attr.aria-label]="item.labelKey | t: { name: item.slot.user.displayName }">
                <button
                  type="button"
                  class="slot slot--taken slot--pill slot--unconfirmed split__pill"
                  [disabled]="!slotsClickable()"
                  [attr.title]="item.slot.user.displayName"
                  [attr.aria-label]="item.labelKey | t: { name: item.slot.user.displayName }"
                  [attr.data-user]="item.slot.user.displayName"
                  [attr.data-confirmed]="item.slot.confirmed"
                  (click)="slotClick.emit(item.slot)"
                  [attr.data-testid]="testId() + '.taken'"
                >
                  <fs-user-avatar [name]="item.slot.user.displayName" [avatarUrl]="item.slot.user.avatarUrl" size="xs" />
                  <span class="slot__label" aria-hidden="true">{{ item.firstName }}</span>
                  <i class="pi pi-hourglass slot__icon" aria-hidden="true"></i>
                </button>
                <button
                  type="button"
                  class="split__confirm"
                  [attr.title]="'shared.pickupSlot.confirmOne' | t: { name: item.slot.user.displayName }"
                  [attr.aria-label]="'shared.pickupSlot.confirmOne' | t: { name: item.slot.user.displayName }"
                  [attr.data-user]="item.slot.user.displayName"
                  (click)="confirmClick.emit(item.slot)"
                  [attr.data-testid]="testId() + '.confirm'"
                >
                  <i class="pi pi-check" aria-hidden="true"></i>
                </button>
              </div>
            }
            @default {
              <button
                type="button"
                class="slot slot--taken"
                [class.slot--unconfirmed]="!item.slot.confirmed"
                [disabled]="!slotsClickable()"
                [attr.title]="item.slot.user.displayName"
                [attr.aria-label]="item.labelKey | t: { name: item.slot.user.displayName }"
                [attr.data-user]="item.slot.user.displayName"
                [attr.data-confirmed]="item.slot.confirmed"
                (click)="slotClick.emit(item.slot)"
                [attr.data-testid]="testId() + '.taken'"
              >
                <fs-user-avatar [name]="item.slot.user.displayName" [avatarUrl]="item.slot.user.avatarUrl" size="sm" />
                <span class="slot__state" aria-hidden="true">
                  <i class="pi" [class.pi-check]="item.slot.confirmed" [class.pi-hourglass]="!item.slot.confirmed"></i>
                </span>
              </button>
            }
          }
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
    .slots { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; margin: 0; padding: 0; list-style: none; }
    .slot {
      position: relative; display: inline-grid; place-items: center; width: var(--fs-slot-size); height: var(--fs-slot-size);
      padding: 0; border-radius: 999px; border: var(--fs-slot-ring) solid transparent; background: none; color: inherit;
      cursor: pointer;
    }
    .slot:disabled { cursor: default; }
    .slot--taken { border-color: var(--fs-primary); }
    .slot--unconfirmed { border-style: dashed; border-color: var(--fs-warning); }

    /* state badge: two cues per state, never colour alone (proposal §3.1) */
    .slot__state {
      position: absolute; right: -0.2rem; bottom: -0.2rem; display: grid; place-items: center;
      width: var(--fs-slot-badge); height: var(--fs-slot-badge); border-radius: 999px;
      border: 2px solid var(--fs-surface); background: var(--fs-primary); color: var(--fs-on-primary);
    }
    .slot__state .pi { font-size: 0.62rem; font-weight: 700; }
    .slot--unconfirmed .slot__state { background: var(--fs-warning); color: var(--fs-on-warning); }

    /* labelled pills: "Du" and the manager's unconfirmed slot (proposal §3.2) */
    .slot--pill {
      display: inline-flex; gap: 0.35rem; align-items: center; width: auto; padding: 0 0.7rem 0 0.2rem;
      font: inherit; font-size: 0.85rem; font-weight: 650;
    }
    .slot__icon { font-size: 0.95rem; }
    .slot--mine { background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .slot--mine.slot--unconfirmed { background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .split { display: inline-flex; }
    .split__pill { color: var(--fs-warning-text); border-top-right-radius: 0; border-bottom-right-radius: 0; border-right-width: 0; }
    .split__confirm {
      display: inline-grid; place-items: center; width: var(--fs-target-sm); height: var(--fs-slot-size); padding: 0;
      border: var(--fs-slot-ring) dashed var(--fs-warning); border-radius: 0 999px 999px 0; background: var(--fs-surface);
      color: var(--fs-primary-strong); cursor: pointer;
    }
    .split__confirm:hover { background: var(--fs-primary-soft); }

    .slot--free {
      border: var(--fs-slot-ring) dashed var(--fs-border); color: var(--fs-text-muted); background: var(--fs-surface);
    }
    .slot--free:not(:disabled):hover { border-color: var(--fs-primary); color: var(--fs-primary-strong); }
    .slot--free:not(:disabled) { color: var(--fs-primary-strong); border-color: color-mix(in srgb, var(--fs-primary) 55%, var(--fs-border)); }
    .slot--labelled {
      display: inline-flex; gap: 0.35rem; width: auto; padding: 0 0.8rem; font: inherit; font-size: 0.85rem; font-weight: 650;
    }
    .slot--labelled:disabled { color: var(--fs-text-muted); }
    .slot__label { white-space: nowrap; }
    .slot:focus-visible, .split__confirm:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
  `,
})
export class PickupSlotComponent {
  readonly slotCount = input.required<number>();
  readonly slots = input<PickupSlot[]>([]);
  readonly myId = input<string | null | undefined>(undefined);
  readonly freeClickable = input(false);
  readonly slotsClickable = input(false);
  /** Managers (`mayConfirmPickups`): unconfirmed slots become split pills with a ✓ button. */
  readonly confirmable = input(false);
  readonly testId = input('shared.pickupSlot');
  readonly freeClick = output<void>();
  readonly slotClick = output<PickupSlot>();
  readonly confirmClick = output<PickupSlot>();

  protected readonly taken = computed<TakenSlot[]>(() =>
    this.slots().map((slot) => {
      const mine = !!this.myId() && slot.user.id === this.myId();
      const kind: TakenKind = mine ? 'mine' : !slot.confirmed && this.confirmable() ? 'split' : 'compact';
      const labelKey = mine
        ? slot.confirmed
          ? 'shared.pickupSlot.mineConfirmed'
          : 'shared.pickupSlot.mineUnconfirmed'
        : slot.confirmed
          ? 'shared.pickupSlot.confirmed'
          : 'shared.pickupSlot.unconfirmed';
      return { slot, kind, mine, firstName: firstName(slot.user.displayName), labelKey };
    }),
  );

  protected readonly freeSlots = computed(() => Array.from({ length: Math.max(0, this.slotCount() - this.slots().length) }));
}
