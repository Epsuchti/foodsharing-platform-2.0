import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { Banana } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { mayDeleteBanana } from '../profiles.util';

/**
 * Trust bananas (A15/G4): the other person (sender of a received banana, recipient of a sent one), the message and
 * the date; delete for recipient, sender and ORGA (`mayDeleteBanana`). The host confirms and calls the API.
 */
@Component({
  selector: 'fs-banana-list',
  imports: [Button, DateTimePipe, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="list" data-testid="profiles.bananas.list">
      @for (b of bananas(); track b.sender.id + b.recipient.id) {
        @let other = direction() === 'sent' ? b.recipient : b.sender;
        <li class="fs-card item" data-testid="profiles.bananas.item" [attr.data-sender-id]="b.sender.id">
          <div class="item__head">
            <fs-user-chip
              [name]="other.displayName"
              [userId]="other.id"
              [avatarUrl]="other.avatarUrl"
              [role]="other.role"
              [sleepMode]="other.sleepMode"
              [link]="['/profile', other.id]"
            />
            <time class="muted" [attr.datetime]="b.givenAt">{{ b.givenAt | fsDate: 'date' }}</time>
          </div>
          <p class="item__text" data-testid="profiles.bananas.message">{{ b.message }}</p>
          @if (mayDelete(b)) {
            <p-button
              class="item__delete"
              icon="pi pi-trash"
              severity="danger"
              [text]="true"
              [rounded]="true"
              size="small"
              [ariaLabel]="'profiles.bananas.delete' | t"
              (onClick)="delete.emit(b)"
              data-testid="profiles.bananas.delete"
            />
          }
        </li>
      }
    </ol>
  `,
  styles: `
    :host { display: block; }
    .list { display: grid; gap: 0.6rem; margin: 0; padding: 0; list-style: none; }
    .item { position: relative; display: grid; gap: 0.4rem; padding: 0.9rem 3rem 0.9rem 1rem; margin: 0; min-width: 0; }
    .item__head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.25rem 0.75rem; }
    .item__text { margin: 0; white-space: pre-line; overflow-wrap: anywhere; }
    .item__delete { position: absolute; top: 0.5rem; right: 0.5rem; }
    .muted { color: var(--fs-text-muted); font-size: 0.85rem; }
  `,
})
export class BananaListComponent {
  readonly bananas = input.required<readonly Banana[]>();
  readonly direction = input<'received' | 'sent'>('received');
  readonly viewerId = input<string | undefined>(undefined);
  readonly orga = input(false);
  readonly delete = output<Banana>();

  protected mayDelete(banana: Banana): boolean {
    return mayDeleteBanana(banana, this.viewerId(), this.orga());
  }
}
