import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { Bell } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';

/** `pi pi-*` class of a bell icon (`icon` is a PrimeIcons name without prefix; GENERIC falls back to the bell). */
export function bellIconClass(bell: Bell): string {
  const name = bell.icon?.replace(/^pi[- ]+/, '').replace(/^pi-/, '');
  return `pi pi-${name || 'bell'}`;
}

/** Translation key of a bell's title: `bell.<TYPE>` (N3; every NotificationType has one in features/bells/i18n). */
export function bellTitleKey(bell: Bell): string {
  return bell.titleKey?.startsWith('bell.') ? bell.titleKey : `bell.${bell.type}`;
}

/** One bell row (N3): icon per type, translated title with params, relative date, unread marker, optional delete. */
@Component({
  selector: 'fs-bell-item',
  imports: [DateTimePipe, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'bell-item',
    '[class.bell-item--unread]': '!bell().readAt',
    '[attr.data-unread]': '!bell().readAt',
    '[attr.data-type]': 'bell().type',
    '[attr.data-id]': 'bell().id',
  },
  template: `
    <button type="button" class="bell-item__main" (click)="open.emit(bell())" [attr.data-testid]="testId() + '.open'">
      <span class="bell-item__icon" aria-hidden="true"><i [class]="icon()"></i></span>
      <span class="bell-item__text">
        <span class="bell-item__title" [attr.data-testid]="testId() + '.title'">{{ titleKey() | t: bell().params }}</span>
        <span class="bell-item__date">{{ bell().createdAt | fsDate: 'relative' }}</span>
      </span>
      @if (!bell().readAt) {
        <span class="bell-item__dot" role="img" [attr.aria-label]="'bell.ui.unread' | t"></span>
      }
    </button>
    @if (deletable()) {
      <button
        type="button"
        class="bell-item__delete fs-btn fs-btn--text fs-btn--sm"
        [attr.aria-label]="'bell.ui.delete' | t"
        [disabled]="busy()"
        (click)="remove.emit(bell())"
        [attr.data-testid]="testId() + '.delete'"
      >
        <i class="pi pi-trash" aria-hidden="true"></i>
      </button>
    }
  `,
  styles: `
    :host {
      display: flex;
      align-items: stretch;
      gap: 0.25rem;
      border-radius: 0.5rem;
    }
    :host(.bell-item--unread) {
      background: var(--fs-primary-soft);
    }
    .bell-item__main {
      display: flex;
      flex: 1;
      align-items: flex-start;
      gap: 0.75rem;
      min-width: 0;
      padding: 0.625rem 0.75rem;
      border: 0;
      border-radius: inherit;
      background: none;
      color: inherit;
      font: inherit;
      text-align: start;
      cursor: pointer;
    }
    .bell-item__main:hover,
    .bell-item__main:focus-visible {
      background: var(--fs-surface-muted);
    }
    .bell-item__icon {
      display: grid;
      place-items: center;
      flex: none;
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      background: var(--fs-surface-muted);
      color: var(--fs-primary);
    }
    .bell-item__text {
      display: grid;
      flex: 1;
      gap: 0.125rem;
      min-width: 0;
    }
    .bell-item__title {
      overflow-wrap: anywhere;
    }
    :host(.bell-item--unread) .bell-item__title {
      font-weight: 600;
    }
    .bell-item__date {
      color: var(--fs-text-muted);
      font-size: 0.8125rem;
    }
    .bell-item__dot {
      flex: none;
      width: 0.5rem;
      height: 0.5rem;
      margin-top: 0.5rem;
      border-radius: 50%;
      background: var(--fs-primary);
    }
    .bell-item__delete {
      align-self: center;
    }
  `,
})
export class BellItemComponent {
  readonly bell = input.required<Bell>();
  readonly deletable = input(false);
  readonly busy = input(false);
  readonly testId = input('bells.item');
  readonly open = output<Bell>();
  readonly remove = output<Bell>();

  protected readonly icon = computed(() => bellIconClass(this.bell()));
  protected readonly titleKey = computed(() => bellTitleKey(this.bell()));
}
