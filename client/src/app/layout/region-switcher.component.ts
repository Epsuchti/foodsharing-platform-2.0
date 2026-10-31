import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Popover } from '@openng/optimus-ui/popover';

import { UnitMembership } from '../api';
import { SessionService } from '../core/auth/session.service';
import { TPipe } from '../core/i18n/t.pipe';
import { RegionContextService } from '../core/region/region-context.service';
import { LazyPopover, PopoverLike } from './lazy-popover';
import { SidenavCardDirective, SidenavPopovers } from './sidenav-popover';

/** From this many regions and groups the side menu switcher shows a filter field (proposal §3.5). */
export const REGION_FILTER_FROM = 8;

/** Units whose name contains the filter text (case and accents ignored). */
export function filterUnits(units: readonly UnitMembership[], text: string): UnitMembership[] {
  const fold = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
  const wanted = fold(text.trim());
  return wanted ? units.filter((unit) => fold(unit.name).includes(wanted)) : [...units];
}

/**
 * Region switcher (M2 T17): the user's regions from `SessionInfo.units` (home region first). The choice sets
 * the active region of RegionContextService (remembered on this device); pages under `/regions/:regionId` still use
 * their own region. Hidden for users without regions.
 * - `header` (phones): icon button with a lazily loaded `p-popover`.
 * - `sidenav` (desktop side menu, decision 133): a full-width row with a floating card that also lists the working
 *   groups (links to their pages) and a filter field from 8 entries.
 */
@Component({
  selector: 'fs-region-switcher',
  imports: [Popover, RouterLink, SidenavCardDirective, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (context.regions().length > 0) {
      @if (variant() === 'sidenav') {
        <button
          type="button"
          class="sn-row"
          #sideTrigger
          (click)="popovers?.toggle('region', sideTrigger)"
          aria-haspopup="true"
          [attr.aria-expanded]="cardOpen()"
          aria-controls="sn-card-region"
          [attr.aria-label]="'layout.header.region' | t: { name: context.activeRegion()?.name ?? '' }"
          [attr.data-label]="context.activeRegion()?.name"
          data-testid="shell.regionSwitcher"
        >
          <i class="pi pi-map-marker" aria-hidden="true"></i>
          <span class="sn-row__label sn-row__ellipsis">{{ context.activeRegion()?.name }}</span>
          <i class="pi pi-chevron-down sn-row__chev" aria-hidden="true"></i>
        </button>
        <div id="sn-card-region" fsSidenavCard="region" data-testid="shell.regionSwitcher.card">
          <div class="sn-card__body">
            @if (manyUnits()) {
              <input
                type="search"
                class="sn-card__filter"
                [value]="filter()"
                (input)="filter.set($any($event.target).value)"
                [attr.aria-label]="'layout.header.regionFilter' | t"
                [placeholder]="'layout.header.regionFilter' | t"
                data-testid="shell.regionSwitcher.filter"
              />
            }
            <ul class="sn__list" role="menu" [attr.aria-label]="'layout.header.regions' | t">
              @for (region of shownRegions(); track region.id) {
                <li role="none">
                  <button
                    type="button"
                    role="menuitemradio"
                    class="sn-row"
                    [attr.aria-checked]="region.id === context.activeRegionId()"
                    (click)="chooseInCard(region.id)"
                    [attr.data-testid]="'shell.region.' + region.slug"
                  >
                    <i class="pi pi-map-marker" aria-hidden="true"></i>
                    <span class="sn-row__label">
                      {{ region.name }}
                      @if (region.homeRegion) {
                        <span class="sn-row__small">{{ 'layout.header.homeRegion' | t }}</span>
                      }
                    </span>
                    @if (region.id === context.activeRegionId()) {
                      <i class="pi pi-check sn-card__check" aria-hidden="true"></i>
                    }
                  </button>
                </li>
              }
              @if (shownGroups().length) {
                <li role="separator" class="sn-card__sep"></li>
                <!-- a visual heading: menus allow only items, groups and separators as accessible children -->
                <li role="none" aria-hidden="true" class="sn-card__h">{{ 'layout.header.groups' | t }}</li>
                @for (group of shownGroups(); track group.id) {
                  <li role="none">
                    <a
                      role="menuitem"
                      class="sn-row"
                      [routerLink]="['/regions', group.id]"
                      (click)="popovers?.close(false)"
                      [attr.data-testid]="'shell.group.' + group.slug"
                    >
                      <i class="pi pi-users" aria-hidden="true"></i>
                      <span class="sn-row__label">{{ group.name }}</span>
                    </a>
                  </li>
                }
              }
            </ul>
          </div>
        </div>
      } @else {
        <button
          type="button"
          class="hdr-btn hdr-btn--labelled region-btn"
          #menuTrigger
          (click)="menu.toggle($event)"
          aria-haspopup="true"
          [attr.aria-label]="'layout.header.region' | t: { name: context.activeRegion()?.name ?? '' }"
          data-testid="shell.regionSwitcher"
        >
          <i class="pi pi-map-marker" aria-hidden="true"></i>
          <span class="region-btn__name">{{ context.activeRegion()?.name }}</span>
          <i class="pi pi-chevron-down region-btn__chevron" aria-hidden="true"></i>
        </button>
        <!-- code prefetched after the first page, rendered on the first click (LazyPopover, D11.5.3) -->
        @defer (on interaction(menuTrigger); prefetch when menu.prefetch()) {
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
      }
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
  private readonly popover = viewChild<PopoverLike>('op');
  protected readonly menu = new LazyPopover(this.popover);
  protected readonly context = inject(RegionContextService);
  private readonly session = inject(SessionService);
  /** Only inside fs-sidenav. */
  protected readonly popovers = inject(SidenavPopovers, { optional: true });
  readonly variant = input<'header' | 'sidenav'>('header');

  protected readonly cardOpen = computed(() => this.popovers?.openId() === 'region');
  protected readonly filter = signal('');
  /** Working groups the user is a member of (side menu only). */
  private readonly groups = computed(() =>
    (this.session.session()?.units ?? [])
      .filter((unit) => unit.member && unit.type === 'WORKING_GROUP')
      .sort((a, b) => a.name.localeCompare(b.name)),
  );
  protected readonly manyUnits = computed(() => this.context.regions().length + this.groups().length >= REGION_FILTER_FROM);
  protected readonly shownRegions = computed(() => filterUnits(this.context.regions(), this.filter()));
  protected readonly shownGroups = computed(() => filterUnits(this.groups(), this.filter()));

  protected chooseInCard(regionId: string): void {
    this.context.choose(regionId);
    this.popovers?.close(true);
    this.filter.set('');
  }
}
