import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { BookedIntroPickup } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';

/** Private details only booked users see: address, call link, private info (EA6/EA7). */
@Component({
  selector: 'fs-booked-details',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (einab().privateAddress || einab().onlineCallLink || einab().privateInfo) {
      <div class="private" data-testid="introPickups.booked.private">
        @if (einab().privateAddress) {
          <span><strong>{{ 'introPickups.booked.address' | t }}:</strong> {{ einab().privateAddress }}</span>
        }
        @if (einab().onlineCallLink) {
          <span>
            <strong>{{ 'introPickups.booked.callLink' | t }}:</strong>
            <a [href]="einab().onlineCallLink" target="_blank" rel="noopener" data-testid="introPickups.booked.callLink">{{ einab().onlineCallLink }}</a>
          </span>
        }
        @if (einab().privateInfo) {
          <span>{{ einab().privateInfo }}</span>
        }
      </div>
    }
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    :host {
      display: block;
      width: auto;
      margin: 0;
    }
  `,
})
export class BookedDetailsComponent {
  readonly einab = input.required<BookedIntroPickup>();
}
