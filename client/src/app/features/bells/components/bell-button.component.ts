import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { I18nService } from '../../../core/i18n/i18n.service';
import { BellStore } from '../bells.resource';
import { BellPanelComponent } from './bell-panel.component';

/** Badge text: the count, capped at "99+". */
export function badgeText(count: number): string {
  return count > 99 ? '99+' : String(count);
}

/**
 * Header bell (N3): icon button with the unread badge from `BellStore`; toggles the lazily loaded `fs-bell-panel`.
 */
@Component({
  selector: 'fs-bell-button',
  imports: [BellPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'bell-button' },
  template: `
    <button
      type="button"
      class="hdr-icon-btn"
      [attr.aria-label]="label()"
      [attr.aria-expanded]="open()"
      aria-haspopup="dialog"
      (click)="open.set(!open())"
      data-testid="bells.button"
      [attr.data-unread]="store.unreadCount()"
    >
      <i class="pi pi-bell" aria-hidden="true"></i>
      @if (store.unreadCount() > 0) {
        <span class="badge" aria-hidden="true" data-testid="bells.badge">{{ badge() }}</span>
      }
    </button>
    @if (open()) {
      @defer {
        <fs-bell-panel (closed)="open.set(false)" />
      }
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-flex;
    }
    .hdr-icon-btn {
      position: relative;
      display: grid;
      place-items: center;
      width: 2.5rem;
      height: 2.5rem;
      border: 0;
      border-radius: 50%;
      background: none;
      color: inherit;
      cursor: pointer;
    }
    .hdr-icon-btn:hover,
    .hdr-icon-btn:focus-visible,
    .hdr-icon-btn[aria-expanded='true'] {
      background: var(--fs-surface-muted);
    }
    .hdr-icon-btn .pi {
      font-size: 1.15rem;
    }
    .badge {
      position: absolute;
      top: 0.125rem;
      inset-inline-end: 0.125rem;
      min-width: 1.125rem;
      height: 1.125rem;
      padding: 0 0.25rem;
      border-radius: 0.5625rem;
      background: var(--fs-danger);
      color: #fff;
      font-size: 0.6875rem;
      font-weight: 700;
      line-height: 1.125rem;
      text-align: center;
    }
  `,
})
export class BellButtonComponent {
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(BellStore);
  protected readonly open = signal(false);
  protected readonly badge = computed(() => badgeText(this.store.unreadCount()));
  protected readonly label = computed(() => {
    const count = this.store.unreadCount();
    return count ? this.i18n.t('bell.ui.button.unread', { count }) : this.i18n.t('bell.ui.button.label');
  });
}
