import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { switchMap } from 'rxjs';

import { AuthService, FeatureToggleKey, RegionsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { hasPermission } from '../../../core/auth/permission.util';
import { runMutation } from '../../../core/data/mutation';
import { FeatureToggleService } from '../../../core/feature-toggles/feature-toggle.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { M6_PAGES_READY } from '../../../layout/nav.config';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { mayHandleReports } from '../../reports/reports.util';
import { hasResources } from '../../resources/resources.util';
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
const LATER: { id: string; icon: string; route?: string }[] = [
  { id: 'stores', icon: 'pi pi-shopping-bag' },
  // a tab route of its own (plan-m7 Q7), so /regions/:id/wall stays the wall's address
  { id: 'wall', icon: 'pi pi-megaphone', route: 'wall' },
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
  private readonly toggles = inject(FeatureToggleService);

  /** Automation permission holders of this region (M4 F4). */
  private readonly automation = computed(() =>
    ['CAN_USE_AUTOMATIONS', 'CAN_SEE_ALL_AUTOMATION_DECISIONS', 'CAN_SEE_USER_PICKUP_COUNT_GROUPING'].some((permission) =>
      hasPermission(this.session.session(), permission, this.ctx.regionId()),
    ),
  );

  /** Report handlers (A51 rule copied in reports.util until RegionDetail says so; the server decides). */
  private readonly reportHandler = computed(
    () => M6_PAGES_READY.reports && mayHandleReports(this.session.session(), this.ctx.data()),
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
      { id: 'events', route: 'events', icon: 'pi pi-calendar', labelKey: 'regions.later.events', visible: member },
      { id: 'polls', route: 'polls', icon: 'pi pi-chart-bar', labelKey: 'regions.later.polls', visible: member },
      // M6 Q6: /regions/:regionId/food-share-points (features/food-share-points), behind its feature toggle
      {
        id: 'foodSharePoints',
        route: 'food-share-points',
        icon: 'pi pi-box',
        labelKey: 'regions.tab.foodSharePoints',
        visible: this.toggles.isEnabled(FeatureToggleKey.FoodSharePoints),
      },
      // M6 Q12: /regions/:regionId/resources (features/resources), RESOURCES toggle, only unit types with a mosaic
      {
        id: 'resources',
        route: 'resources',
        icon: 'pi pi-th-large',
        labelKey: 'regions.tab.resources',
        visible: member && this.toggles.isEnabled(FeatureToggleKey.Resources) && hasResources(this.ctx.data()?.type),
      },
      // M6 Q8: /regions/:regionId/{reports,report-log,mediation} (features/reports)
      { id: 'reports', route: 'reports', icon: 'pi pi-flag', labelKey: 'regions.tab.reports', visible: this.reportHandler() },
      { id: 'reportLog', route: 'report-log', icon: 'pi pi-list', labelKey: 'regions.tab.reportLog', visible: this.reportHandler() },
      { id: 'mediation', route: 'mediation', icon: 'pi pi-comments', labelKey: 'regions.tab.mediation', visible: M6_PAGES_READY.reports && member },
      // M6 Q9: /regions/:regionId/achievements (features/achievements)
      { id: 'achievements', route: 'achievements', icon: 'pi pi-trophy', labelKey: 'regions.tab.achievements', visible: member },
      // M4 F4: /regions/:regionId/automation (features/automation)
      { id: 'automation', route: 'automation', icon: 'pi pi-bolt', labelKey: 'regions.tab.automation', visible: admin || this.automation() },
    ];
    return tabs.filter((tab) => tab.visible);
  });

  protected readonly laterTabs = computed(() =>
    this.ctx.isMember()
      ? LATER.map((tab) => ({
          ...tab,
          route: tab.route ?? `later/${tab.id}`,
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
