import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { PublicStatistics } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { AnimatedCounterComponent } from '../../../shared/animated-counter/animated-counter.component';
import { statisticsTiles } from '../content.util';

/** The seven platform counters of the statistics page (C3), counting up with `fs-animated-counter`. */
@Component({
  selector: 'fs-statistics-tiles',
  imports: [AnimatedCounterComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="tiles">
      @for (tile of tiles(); track tile.key) {
        <li class="fs-card tile" [attr.data-key]="tile.key" data-testid="content.statistics.tile">
          <i [class]="tile.icon + ' tile__icon'" aria-hidden="true"></i>
          <fs-animated-counter
            class="tile__value"
            [value]="tile.value"
            [fractionDigits]="tile.fractionDigits"
            [testId]="'content.statistics.' + tile.key"
          />
          <span class="tile__label">{{ 'content.statistics.label.' + tile.key | t }}</span>
        </li>
      }
    </ul>
  `,
  styles: `
    :host {
      display: block;
    }
    .tiles {
      display: grid;
      /* phones: two columns (one tall tile per number wasted a screen); wider: rows that fill the width */
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 9.5rem), 1fr));
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .tile {
      display: grid;
      justify-items: start;
      gap: 0.35rem;
      padding: 0.9rem 1rem;
    }
    @media (min-width: 40rem) {
      .tiles {
        grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
        gap: 0.9rem;
      }
      .tile {
        padding: 1.1rem 1.2rem;
      }
    }
    /* phones: the first number (kg saved) is the headline across both columns */
    @media (max-width: 30rem) {
      .tile:first-child {
        grid-column: 1 / -1;
      }
    }
    .tile__icon {
      display: grid;
      place-items: center;
      width: 2.4rem;
      height: 2.4rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .tile__value {
      font-size: clamp(1.35rem, 1rem + 1.6vw, 2.2rem);
      font-variant-numeric: tabular-nums;
      font-weight: 800;
      line-height: 1.15;
    }
    .tile__label {
      color: var(--fs-text-muted);
      font-size: 0.9rem;
      font-weight: 600;
    }
  `,
})
export class StatisticsTilesComponent {
  readonly stats = input.required<PublicStatistics>();
  protected readonly tiles = computed(() => statisticsTiles(this.stats()));
}
