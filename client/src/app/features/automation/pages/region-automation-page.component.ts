import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Permission, RegionDetail, RegionsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { hasPermission, isAmbassador } from '../../../core/auth/permission.util';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RegionAutomationContext } from '../region-automation.context';

/**
 * `/regions/:regionId/automation` (AU1, AU5, AU7–AU10): region automation overview and sub-pages. Sections are
 * offered by the session's permissions; the API decides (403 → forbidden state).
 */
@Component({
  selector: 'fs-region-automation-page',
  imports: [PageHeaderComponent, RouterLink, RouterLinkActive, RouterOutlet, TPipe],
  providers: [RegionAutomationContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'automation.region.title' | t"
      [subtitle]="region.data()?.name"
      icon="pi pi-bolt"
      [backLink]="['/regions', ctx.regionId()]"
      [backLabel]="'automation.region.back' | t"
    />
    <nav class="subnav" [attr.aria-label]="'automation.region.nav' | t" data-testid="automation.region.nav">
      @for (section of sections(); track section) {
        <a class="subnav__item" [routerLink]="section" routerLinkActive="subnav__item--active" ariaCurrentWhenActive="page" [attr.data-testid]="'automation.region.nav.' + section">
          {{ 'automation.region.section.' + section | t }}
        </a>
      }
    </nav>
    <router-outlet />
  `,
  styles: `
    :host { display: grid; gap: 1rem; width: min(100%, 64rem); margin: 0 auto; }
    .subnav { display: flex; flex-wrap: wrap; gap: 0.35rem; }
    .subnav__item {
      display: inline-flex; align-items: center; min-height: var(--fs-target-sm);
      padding: 0.35rem 0.8rem; border-radius: 999px; border: 1px solid var(--fs-border);
      color: inherit; text-decoration: none; font-size: 0.9rem; font-weight: 600;
    }
    .subnav__item--active { background: var(--fs-primary-soft); border-color: var(--fs-primary); color: var(--fs-primary-strong); }
  `,
})
export class RegionAutomationPageComponent {
  protected readonly ctx = inject(RegionAutomationContext);
  private readonly regionsApi = inject(RegionsService);
  private readonly session = inject(SessionService);

  protected readonly region = loadResource<RegionDetail, string>({
    params: () => this.ctx.regionId(),
    loader: (regionId) => this.regionsApi.getRegion({ regionId }),
    isEmpty: () => false,
  });

  protected readonly sections = computed(() => {
    const s = this.session.session();
    const regionId = this.ctx.regionId();
    const admin = isAmbassador(s, regionId) || hasPermission(s, Permission.CanUseAutomations, regionId);
    const list: string[] = [];
    if (admin) list.push('overview');
    list.push('audits');
    if (admin) list.push('cleaning-exemptions');
    if (admin || hasPermission(s, Permission.CanSeeUserPickupCountGrouping, regionId)) list.push('grouping');
    if (admin) list.push('metrics', 'telegram');
    list.push('holidays');
    return list;
  });
}
