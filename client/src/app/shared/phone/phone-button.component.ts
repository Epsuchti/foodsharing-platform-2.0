import { booleanAttribute, ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TPipe } from '../../core/i18n/t.pipe';
import { formatPhoneNumber, normalizePhoneNumber } from '../../core/validation/phone-number';

/**
 * Call button (`tel:` link, E.164) with the readable number; renders nothing for an empty or invalid number.
 * `<fs-phone-button [phone]="user.mobilePhone" [label]="user.displayName" />`
 * `compact` renders a round icon-only button (number in the accessible name and the tooltip), for narrow cards.
 */
@Component({
  selector: 'fs-phone-button',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-phone-button' },
  template: `
    @if (href(); as tel) {
      <a
        class="fs-btn fs-btn--outlined fs-btn--sm phone"
        [class.phone--compact]="compact()"
        [href]="tel"
        [attr.aria-label]="'shared.phone.call' | t: { name: label() ?? display(), number: display() }"
        [attr.title]="compact() ? display() : null"
        [attr.data-testid]="testId()"
      >
        <i class="pi pi-phone" aria-hidden="true"></i>
        @if (!compact()) {
          <span class="phone__number">{{ display() }}</span>
        }
      </a>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
    }
    .phone__number {
      font-variant-numeric: tabular-nums;
    }
    /* same footprint as the rounded icon-only p-button next to it (2.5rem; 44px on touch via the global rule) */
    .phone--compact {
      width: 2.5rem;
      min-width: 2.5rem;
      height: 2.5rem;
      padding: 0;
      border-radius: 50%;
    }
    @media (pointer: coarse) {
      .phone--compact {
        width: 2.75rem;
        min-width: 2.75rem;
        height: 2.75rem;
      }
    }
  `,
})
export class PhoneButtonComponent {
  readonly phone = input<string | null | undefined>(undefined);
  /** Who is called (accessible name). */
  readonly label = input<string | undefined>(undefined);
  readonly testId = input<string | undefined>(undefined);
  /** Icon-only round button; the number stays in the accessible name and the tooltip. */
  readonly compact = input(false, { transform: booleanAttribute });

  protected readonly href = computed(() => {
    const number = normalizePhoneNumber(this.phone());
    return number ? `tel:${number}` : null;
  });
  protected readonly display = computed(() => formatPhoneNumber(this.phone()));
}
