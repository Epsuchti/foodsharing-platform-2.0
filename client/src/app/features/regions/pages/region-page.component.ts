import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { switchMap } from 'rxjs';

import { AuthService, RegionsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { hasPermission } from '../../../core/auth/permission.util';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { RegionPageContext } from '../region-page.context';
import { isAccessibleType } from '../shared/unit-types';

interface RegionTab {
  id: string;
  route: string;
  icon: string;
  labelKey: string;
  visible: boolean;
}

/** Features of later milestones, shown as tabs with an "available in a later release" placeholder (R3 partial). */
const LATER = [
  { id: 'events', icon: 'pi pi-calendar' },
  { id: 'polls', icon: 'pi pi-chart-bar' },
  { id: 'stores', icon: 'pi pi-shopping-bag' },
  { id: 'fsps', icon: 'pi pi-box' },
  { id: 'wall', icon: 'pi pi-megaphone' },
];

/**
 * /regions/:regionId (R3): header with photo, teaser, member count and membership actions (join, leave, set as
 * home region), tab navigation (child routes) and the active tab. Tabs read the region from RegionPageContext.
 */
@Component({
  selector: 'fs-region-page',
  imports: [...DATA_STATE_IMPORTS, Button, RouterLink, RouterLinkActive, RouterOutlet, TPipe],
  providers: [RegionPageContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './region-page.component.html',
  styleUrl: './region-page.component.scss',
})
export class RegionPageComponent {
  protected readonly ctx = inject(RegionPageContext);
  private readonly regionsApi = inject(RegionsService);
  private readonly authApi = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  /** Automation permission holders of this region (M4 F4). */
  private readonly automation = computed(() =>
    ['CAN_USE_AUTOMATIONS', 'CAN_SEE_ALL_AUTOMATION_DECISIONS', 'CAN_SEE_USER_PICKUP_COUNT_GROUPING'].some((permission) =>
      hasPermission(this.session.session(), permission, this.ctx.regionId()),
    ),
  );

  protected readonly tabs = computed<RegionTab[]>(() => {
    const member = this.ctx.isMember() || this.ctx.isOrga();
    const admin = this.ctx.isAdmin();
    const tabs: RegionTab[] = [
      { id: 'overview', route: 'overview', icon: 'pi pi-home', labelKey: 'regions.tab.overview', visible: true },
      { id: 'members', route: 'members', icon: 'pi pi-users', labelKey: 'regions.tab.members', visible: member },
      { id: 'groups', route: 'groups', icon: 'pi pi-sitemap', labelKey: 'regions.tab.groups', visible: true },
      { id: 'statistics', route: 'statistics', icon: 'pi pi-chart-line', labelKey: 'regions.tab.statistics', visible: true },
      { id: 'options', route: 'options', icon: 'pi pi-sliders-h', labelKey: 'regions.tab.options', visible: member },
      { id: 'pin', route: 'pin', icon: 'pi pi-map-marker', labelKey: 'regions.tab.pin', visible: member },
      { id: 'settings', route: 'settings', icon: 'pi pi-cog', labelKey: 'regions.tab.settings', visible: admin },
      { id: 'history', route: 'history', icon: 'pi pi-history', labelKey: 'regions.tab.history', visible: admin },
      // M5 F4: /regions/:regionId/forum (features/forum)
      { id: 'forum', route: 'forum', icon: 'pi pi-comments', labelKey: 'regions.later.forum', visible: member },
      // M4 F4: /regions/:regionId/automation (features/automation)
      { id: 'automation', route: 'automation', icon: 'pi pi-bolt', labelKey: 'regions.tab.automation', visible: admin || this.automation() },
    ];
    return tabs.filter((tab) => tab.visible);
  });

  protected readonly laterTabs = computed(() =>
    this.ctx.isMember()
      ? LATER.map((tab) => ({
          ...tab,
          route: `later/${tab.id}`,
          labelKey: `regions.later.${tab.id}`,
        }))
      : [],
  );

  /** After a membership change the session (units, permissions) and the region are reloaded. */
  private readonly afterMembershipChange = () => {
    this.ctx.reload();
  };

  protected readonly join = runMutation(
    () =>
      this.regionsApi
        .joinRegion({ regionId: this.ctx.regionId()! })
        .pipe(switchMap(() => this.authApi.getSession())),
    {
      successToastKey: 'regions.page.joined',
      onSuccess: (info) => {
        this.session.applySession(info);
        this.afterMembershipChange();
      },
    },
  );

  protected readonly leave = runMutation(
    () =>
      this.regionsApi
        .leaveRegion({ regionId: this.ctx.regionId()! })
        .pipe(switchMap(() => this.authApi.getSession())),
    {
      successToastKey: 'regions.page.left',
      onSuccess: (info) => {
        this.session.applySession(info);
        this.afterMembershipChange();
      },
    },
  );

  protected readonly makeHome = runMutation(
    () =>
      this.regionsApi
        .setHomeRegion({ homeRegionUpdate: { regionId: this.ctx.regionId()! } })
        .pipe(switchMap(() => this.authApi.getSession())),
    {
      successToastKey: 'regions.page.homeSet',
      onSuccess: (info) => {
        this.session.applySession(info);
        this.afterMembershipChange();
      },
    },
  );

  protected canJoin(type: string): boolean {
    return isAccessibleType(type);
  }

  protected confirmLeave(name: string): void {
    this.confirm.confirm({
      header: this.i18n.t('regions.page.leaveTitle', { name }),
      message: this.i18n.t('regions.page.leaveText'),
      acceptLabel: this.i18n.t('regions.page.leave'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.leave.run(),
    });
  }
}
