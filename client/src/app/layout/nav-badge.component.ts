import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { TPipe } from '../core/i18n/t.pipe';
import { NavBadgesService } from './nav-badges.service';
import type { NavItem } from './nav.config';

/** Unread count bubble of a navigation item (`NavItem.badge`); nothing while the count is 0. */
@Component({
  selector: 'fs-nav-badge',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (count() > 0) {
      <span class="badge" [class.badge--overlay]="overlay()" aria-hidden="true" [attr.data-testid]="'nav.badge.' + item().id" [attr.data-count]="count()">{{ count() > 99 ? '99+' : count() }}</span>
      <span class="fs-visually-hidden">{{ 'layout.nav.unread' | t: { count: count() } }}</span>
    }
  `,
  styles: `
    :host { display: contents; }
    .badge {
      display: inline-grid; place-items: center; min-width: 1.15rem; height: 1.15rem; padding: 0 0.3rem;
      border-radius: 999px; background: var(--fs-danger); color: #fff;
      font-size: 0.7rem; font-weight: 700; line-height: 1; box-sizing: border-box;
    }
    .badge--overlay { position: absolute; top: 0.15rem; inset-inline-start: calc(50% + 0.35rem); }
  `,
})
export class NavBadgeComponent {
  private readonly badges = inject(NavBadgesService);
  readonly item = input.required<NavItem>();
  /** Positioned over the icon (mobile tab bar). */
  readonly overlay = input(false);
  protected readonly count = computed(() => {
    const badge = this.item().badge;
    return badge ? this.badges.count(badge) : 0;
  });
}
