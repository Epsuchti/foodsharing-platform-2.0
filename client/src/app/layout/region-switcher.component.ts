import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Popover } from '@openng/optimus-ui/popover';

import { TPipe } from '../core/i18n/t.pipe';
import { RegionContextService } from '../core/region/region-context.service';

/**
 * Header region switcher (M2 T17): the user's regions from `SessionInfo.units` (home region first). The choice sets
 * the active region of RegionContextService (remembered on this device); pages under `/regions/:regionId` still use
 * their own region. Hidden for users without regions.
 */
@Component({
  selector: 'fs-region-switcher',
  imports: [Popover, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (context.regions().length > 0) {
      <button
        type="button"
        class="hdr-btn hdr-btn--labelled region-btn"
        (click)="op.toggle($event)"
        aria-haspopup="true"
        [attr.aria-label]="'layout.header.region' | t: { name: context.activeRegion()?.name ?? '' }"
        data-testid="shell.regionSwitcher"
      >
        <i class="pi pi-map-marker" aria-hidden="true"></i>
        <span class="region-btn__name">{{ context.activeRegion()?.name }}</span>
        <i class="pi pi-chevron-down region-btn__chevron" aria-hidden="true"></i>
      </button>
      <p-popover #op>
        <ul class="menu-list" role="menu" [attr.aria-label]="'layout.header.regions' | t">
          @for (region of context.regions(); track region.id) {
            <li role="none">
              <button
                type="button"
                role="menuitemradio"
                class="menu-list__item"
                [class.menu-list__item--active]="region.id === context.activeRegionId()"
                [attr.aria-checked]="region.id === context.activeRegionId()"
                (click)="context.choose(region.id); op.hide()"
                [attr.data-testid]="'shell.region.' + region.slug"
              >
                <span class="item">
                  <span>{{ region.name }}</span>
                  @if (region.homeRegion) {
                    <small>{{ 'layout.header.homeRegion' | t }}</small>
                  }
                </span>
                @if (region.id === context.activeRegionId()) {
                  <i class="pi pi-check" aria-hidden="true"></i>
                }
              </button>
            </li>
          }
        </ul>
      </p-popover>
    }
  `,
  styles: `
    .region-btn {
      max-width: 14rem;
    }
    .region-btn__name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .region-btn__chevron {
      font-size: 0.65rem;
    }
    @media (max-width: 40rem) {
      .region-btn__name,
      .region-btn__chevron {
        display: none;
      }
    }
    .item {
      display: grid;
      text-align: left;
    }
    .item small {
      color: var(--fs-text-muted);
      font-size: 0.75rem;
    }
  `,
})
export class RegionSwitcherComponent {
  protected readonly context = inject(RegionContextService);
}
