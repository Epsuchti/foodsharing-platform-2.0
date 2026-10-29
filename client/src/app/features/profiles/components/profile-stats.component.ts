import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ProfileStats } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';

/** Stat tiles of a profile (A6 `ProfileStats`): pickups, kg saved, posts, bananas, buddies, shared baskets. */
@Component({
  selector: 'fs-profile-stats',
  imports: [DecimalPipe, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let s = stats();
    <ul class="tiles" [attr.aria-label]="'profiles.stats.title' | t" data-testid="profiles.page.stats">
      <li class="fs-card tile" data-testid="profiles.page.stat.pickups">
        <i class="pi pi-shopping-bag" aria-hidden="true"></i><strong>{{ s.pickupCount | number }}</strong
        ><span>{{ 'profiles.stats.pickups' | t }}</span>
      </li>
      <li class="fs-card tile" data-testid="profiles.page.stat.kgSaved">
        <i class="pi pi-box" aria-hidden="true"></i><strong>{{ s.kgSaved | number: '1.0-0' }}</strong
        ><span>{{ 'profiles.stats.kgSaved' | t }}</span>
      </li>
      <li class="fs-card tile" data-testid="profiles.page.stat.posts">
        <i class="pi pi-comments" aria-hidden="true"></i><strong>{{ s.postCount | number }}</strong
        ><span>{{ 'profiles.stats.posts' | t }}</span>
      </li>
      <li class="fs-card tile" data-testid="profiles.page.stat.bananas">
        <i class="pi pi-thumbs-up" aria-hidden="true"></i><strong>{{ s.bananaCount | number }}</strong
        ><span>{{ 'profiles.stats.bananas' | t }}</span>
      </li>
      <li class="fs-card tile" data-testid="profiles.page.stat.buddies">
        <i class="pi pi-users" aria-hidden="true"></i><strong>{{ s.buddyCount | number }}</strong
        ><span>{{ 'profiles.stats.buddies' | t }}</span>
      </li>
      <li class="fs-card tile" data-testid="profiles.page.stat.baskets">
        <i class="pi pi-gift" aria-hidden="true"></i><strong>{{ s.basketsSharedCount | number }}</strong
        ><span>{{ 'profiles.stats.baskets' | t }}</span>
      </li>
    </ul>
  `,
  styles: `
    :host { display: block; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(8.5rem, 1fr)); gap: 0.6rem; margin: 0; padding: 0; list-style: none; }
    .tile { display: grid; justify-items: start; gap: 0.15rem; padding: 0.8rem 0.9rem; margin: 0; }
    .tile .pi { color: var(--fs-primary); }
    .tile strong { font-size: 1.35rem; font-variant-numeric: tabular-nums; }
    .tile span { color: var(--fs-text-muted); font-size: 0.85rem; }
  `,
})
export class ProfileStatsComponent {
  readonly stats = input.required<ProfileStats>();
}
