import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { StoreDetail } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { NavigateButtonComponent } from '../../../shared/navigate-button/navigate-button.component';
import { NavigationLinks, navigationLinks } from '../../../shared/navigate-button/navigate.util';
import { PhoneButtonComponent } from '../../../shared/phone/phone-button.component';
import { ScrollHintDirective } from '../../../shared/scroll-hint/scroll-hint.directive';
import { StickyOffsetDirective } from '../../../shared/sticky/sticky-offset.directive';
import {
  CooperationStatusBadgeComponent,
  MembershipBadgeComponent,
  TeamStatusBadgeComponent,
} from '../../../shared/store-badges/store-badges.component';
import { StorePageContext } from '../store-page.context';

interface StoreTab {
  id: string;
  icon: string;
  visible: boolean;
  /** Route relative to the store page (default: the id). */
  link?: string;
}

/** Tabs whose page exists (F2 overview; F3 team; F4 pickups; F5 wall, log, statistics). */
export const STORE_TABS_READY = new Set<string>(['overview', 'team', 'pickups', 'wall', 'statistics', 'log', 'chat', 'automation']);

/**
 * /stores/:storeId (S1, S15, S16): header with name, region, badges, navigation and contact buttons, the tab
 * navigation (gated by StorePermissions, A19) and the active tab. Tabs read the store from StorePageContext.
 */
@Component({
  selector: 'fs-store-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    CooperationStatusBadgeComponent,
    MembershipBadgeComponent,
    NavigateButtonComponent,
    PhoneButtonComponent,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    ScrollHintDirective,
    StickyOffsetDirective,
    TeamStatusBadgeComponent,
    TPipe,
  ],
  providers: [StorePageContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state
      [state]="ctx.store"
      skeleton="detail"
      [askWho]="'stores.page.askWho' | t"
      backLink="/stores/mine"
      testId="stores.page.state"
      data-testid="stores.page.state"
    >
      <ng-template [fsData]="ctx.store" let-store>
        <header class="hero" data-testid="stores.page.header">
          <div class="hero__body">
            <a class="hero__parent" [routerLink]="['/regions', store.region.id, 'stores']" data-testid="stores.page.region">
              <i class="pi pi-map-marker" aria-hidden="true"></i>{{ store.region.name }}
            </a>
            @if (store.category) {
              <p class="hero__type">{{ store.category.name }}</p>
            }
            <h1 class="hero__name" data-testid="stores.page.name">{{ store.name }}</h1>
            <p class="hero__teaser" data-testid="stores.page.address">
              {{ store.address.street }}, {{ store.address.postcode }} {{ store.address.city }}
            </p>
            <ul class="hero__facts">
              <li><fs-cooperation-status-badge [status]="store.cooperationStatus" /></li>
              <li><fs-team-status-badge [status]="store.teamStatus" /></li>
              @if (store.membership !== 'NO_MEMBER') {
                <li data-testid="stores.page.membership"><fs-membership-badge [status]="store.membership" /></li>
              }
              @if (store.permissions.isKam) {
                <li class="hero__badge hero__badge--admin">
                  <i class="pi pi-briefcase" aria-hidden="true"></i>{{ 'stores.page.kam' | t }}
                </li>
              }
            </ul>
          </div>
          <div class="hero__actions">
            @if (links(store); as nav) {
              <fs-navigate-button [links]="nav" testId="stores.page.navigate" [small]="true" />
            }
            @if (store.permissions.mayReadWall && store.contact; as contact) {
              @if (contact.phone) {
                <fs-phone-button [phone]="contact.phone" [label]="contact.person ?? store.name" testId="stores.page.call" />
              }
            }
            @if (store.permissions.mayEditStore) {
              <p-button
                [label]="'stores.page.edit' | t"
                icon="pi pi-pencil"
                size="small"
                [routerLink]="['/stores', store.id, 'edit']"
                data-testid="stores.page.edit"
              />
            }
          </div>
        </header>

        <nav class="tabs" fsScrollHint fsStickyOffset [attr.aria-label]="'stores.page.tabs' | t" data-testid="stores.page.tabs">
          @for (tab of tabs(); track tab.id) {
            <a
              class="tabs__tab"
              [routerLink]="tab.link ?? tab.id"
              routerLinkActive="tabs__tab--active"
              ariaCurrentWhenActive="page"
              [attr.data-testid]="'stores.page.tab.' + tab.id"
            >
              <i [class]="tab.icon" aria-hidden="true"></i>
              <span>{{ 'stores.tab.' + tab.id | t }}</span>
            </a>
          }
        </nav>

        <div class="content">
          <router-outlet />
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: './store-page.component.scss',
  styles: `
    .hero__facts li:has(fs-cooperation-status-badge),
    .hero__facts li:has(fs-team-status-badge),
    .hero__facts li:has(fs-membership-badge) {
      display: contents;
    }
    .hero__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
    }
  `,
})
export class StorePageComponent {
  protected readonly ctx = inject(StorePageContext);

  protected readonly tabs = computed<StoreTab[]>(() => {
    const p = this.ctx.permissions();
    if (!p) return [];
    const teamVisible = p.mayAccess;
    const tabs: StoreTab[] = [
      { id: 'overview', icon: 'pi pi-info-circle', visible: true },
      { id: 'team', icon: 'pi pi-users', visible: teamVisible },
      {
        id: 'pickups',
        icon: 'pi pi-calendar',
        visible: p.maySeePickups || p.maySeePickupHistory,
        // KAMs and coordinators outside the team only see the pickup history (PU6)
        link: p.maySeePickups ? 'pickups' : 'pickups/history',
      },
      { id: 'wall', icon: 'pi pi-comments', visible: p.mayReadWall },
      { id: 'statistics', icon: 'pi pi-chart-bar', visible: p.mayAccess },
      { id: 'log', icon: 'pi pi-history', visible: p.maySeeStoreLog },
      // M5 S10: locked team / jumper chat
      { id: 'chat', icon: 'pi pi-comment', visible: p.mayChatTeam || p.mayChatJumper },
      // M4 F3: store editors only (decision 165: no automation permission any more)
      { id: 'automation', icon: 'pi pi-bolt', visible: p.mayEditStore },
    ];
    return tabs.filter((tab) => tab.visible && STORE_TABS_READY.has(tab.id));
  });

  protected links(store: StoreDetail): NavigationLinks | null {
    return navigationLinks({ lat: store.latitude, lon: store.longitude, ...store.address, label: store.name });
  }
}
