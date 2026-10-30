import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';

import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { SidenavCardDirective, SidenavPopovers } from '../../../layout/sidenav-popover';
import { BellStore } from '../bells.resource';
import { BellPanelComponent } from './bell-panel.component';

/** Badge text: the count, capped at "99+". */
export function badgeText(count: number): string {
  return count > 99 ? '99+' : String(count);
}

/**
 * Bell (N3): button with the unread badge from `BellStore`; toggles the lazily loaded `fs-bell-panel`.
 * - `header` (phones): icon button, the panel opens below the header.
 * - `sidenav` (desktop side menu, decision 133): a labelled item ("Glocke") whose panel opens as a floating card next
 *   to it; no page change.
 */
@Component({
  selector: 'fs-bell-button',
  imports: [BellPanelComponent, SidenavCardDirective, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'bell-button', '[class.bell-button--sidenav]': "variant() === 'sidenav'" },
  template: `
    @if (variant() === 'sidenav') {
      <button
        type="button"
        class="sn-row"
        #sideTrigger
        [attr.aria-expanded]="open()"
        aria-haspopup="dialog"
        aria-controls="sn-card-bells"
        [attr.data-label]="'nav.bells' | t"
        (click)="popovers?.toggle('bells', sideTrigger)"
        data-testid="bells.button"
        [attr.data-unread]="store.unreadCount()"
      >
        <i class="pi pi-bell" aria-hidden="true"></i>
        <!-- the name is the visible label plus a hidden count, like the side menu's links (WCAG 2.5.3) -->
        <span class="sn-row__label">{{ 'nav.bells' | t }}</span>
        @if (store.unreadCount() > 0) {
          <span class="sn-badge" aria-hidden="true" data-testid="bells.badge">{{ badge() }}</span>
          <span class="fs-visually-hidden">{{ 'bell.ui.button.itemCount' | t: { count: store.unreadCount() } }}</span>
        }
      </button>
      <div id="sn-card-bells" class="sn-card--panel" fsSidenavCard="bells" [focusFirst]="false">
        @if (open()) {
          @defer (on immediate) {
            <fs-bell-panel [embedded]="true" (closed)="closeCard()" />
          }
        }
      </div>
    } @else {
      <button
        type="button"
        class="hdr-icon-btn"
        [attr.aria-label]="label()"
        [attr.aria-expanded]="open()"
        aria-haspopup="dialog"
        (click)="headerOpen.set(!headerOpen())"
        data-testid="bells.button"
        [attr.data-unread]="store.unreadCount()"
      >
        <i class="pi pi-bell" aria-hidden="true"></i>
        @if (store.unreadCount() > 0) {
          <span class="badge" aria-hidden="true" data-testid="bells.badge">{{ badge() }}</span>
        }
      </button>
      @if (open()) {
        <!-- on immediate, not the default "on idle": opened by a click, the panel must not wait for an idle browser
             (right after the first page the main thread can stay busy for seconds) -->
        @defer (on immediate) {
          <fs-bell-panel (closed)="headerOpen.set(false)" />
        }
      }
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-flex;
    }
    :host(.bell-button--sidenav) {
      display: block;
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
  /** Only inside fs-sidenav. */
  protected readonly popovers = inject(SidenavPopovers, { optional: true });
  readonly variant = input<'header' | 'sidenav'>('header');
  protected readonly headerOpen = signal(false);
  protected readonly open = computed(() =>
    this.variant() === 'sidenav' ? this.popovers?.openId() === 'bells' : this.headerOpen(),
  );
  protected readonly badge = computed(() => badgeText(this.store.unreadCount()));

  /**
   * The panel asks to close (Escape, a click outside, a chosen bell). A click on another side menu trigger has already
   * opened that card: then only the bell card is gone, the new card stays.
   */
  protected closeCard(): void {
    // no refocus here: Escape is handled (with refocus) by the side menu, an outside click must keep its target's focus
    if (this.popovers?.openId() === 'bells') this.popovers.close(false);
  }
  protected readonly label = computed(() => {
    const count = this.store.unreadCount();
    return count ? this.i18n.t('bell.ui.button.unread', { count }) : this.i18n.t('bell.ui.button.label');
  });
}
