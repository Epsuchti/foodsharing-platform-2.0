import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TPipe } from '../../core/i18n/t.pipe';
import { formatPhoneNumber, normalizePhoneNumber } from '../../core/validation/phone-number';

/**
 * Call button (`tel:` link, E.164) with the readable number; renders nothing for an empty or invalid number.
 * `<fs-phone-button [phone]="user.mobilePhone" [label]="user.displayName" />`
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
        [href]="tel"
        [attr.aria-label]="'shared.phone.call' | t: { name: label() ?? display(), number: display() }"
        [attr.data-testid]="testId()"
      >
        <i class="pi pi-phone" aria-hidden="true"></i>
        <span class="phone__number">{{ display() }}</span>
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
  `,
})
export class PhoneButtonComponent {
  readonly phone = input<string | null | undefined>(undefined);
  /** Who is called (accessible name). */
  readonly label = input<string | undefined>(undefined);
  readonly testId = input<string | undefined>(undefined);

  protected readonly href = computed(() => {
    const number = normalizePhoneNumber(this.phone());
    return number ? `tel:${number}` : null;
  });
  protected readonly display = computed(() => formatPhoneNumber(this.phone()));
}
