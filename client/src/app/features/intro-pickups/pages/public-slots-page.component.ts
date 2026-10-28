import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { PublicIntroPickupPage, PublicIntroPickupsService, PublicRegion, PublicRegionsService } from '../../../api';
import { isMemberOf } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RegionContextService } from '../../../core/region/region-context.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { EinAbCardComponent } from '../components/einab-card.component';

/**
 * `/r/:slug/intro-pickups` (EA2, public): next EinAb dates of a region with the general area only (no address, no
 * guide). "Notify me" leads to the login and then to the subscription card of `/me/intro-pickups`.
 */
@Component({
  selector: 'fs-public-slots-page',
  imports: [...DATA_STATE_IMPORTS, Button, EinAbCardComponent, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="region" skeleton="detail" data-testid="introPickups.public.regionState">
      <ng-template [fsData]="region" let-r>
        <fs-page-header
          [title]="'introPickups.public.title' | t: { region: r.name }"
          [subtitle]="'introPickups.public.subtitle' | t"
          icon="pi pi-calendar"
          [backLink]="['/r', r.slug]"
          [backLabel]="r.name"
        />
        <section class="fs-card card notify" data-testid="introPickups.public.notify">
          <div>
            <h2 class="card__title"><i class="pi pi-bell" aria-hidden="true"></i>{{ 'introPickups.public.notifyTitle' | t }}</h2>
            <p class="muted">{{ 'introPickups.public.notifyText' | t }}</p>
          </div>
          <div class="actions">
            <p-button [label]="'introPickups.public.notifyMe' | t" icon="pi pi-bell" (onClick)="notifyMe(r)" data-testid="introPickups.public.notifyMe" />
            @if (member()) {
              <a routerLink="/intro-pickups" class="fs-btn fs-btn--outlined" data-testid="introPickups.public.book">{{ 'introPickups.public.bookNow' | t }}</a>
            }
          </div>
        </section>
        <fs-data-state
          [state]="slots"
          skeleton="card"
          [skeletonRows]="6"
          emptyIcon="pi pi-calendar-times"
          [emptyTitle]="'introPickups.public.empty' | t"
          [emptyText]="'introPickups.public.emptyText' | t"
          data-testid="introPickups.public.state"
        >
          <ng-template [fsData]="slots" let-page>
            <ul class="grid">
              @for (einab of page.items; track einab.id) {
                <li>
                  <fs-einab-card [einab]="einab" [timeZone]="r.timeZone" [attr.data-category]="einab.category" data-testid="introPickups.public.card" />
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    .notify {
      grid-template-columns: minmax(0, 1fr);
      align-items: center;
    }
    @media (min-width: 48rem) {
      .notify {
        grid-template-columns: minmax(0, 1fr) auto;
      }
    }
  `,
})
export class PublicSlotsPageComponent {
  private readonly regionsApi = inject(PublicRegionsService);
  private readonly api = inject(PublicIntroPickupsService);
  private readonly session = inject(SessionService);
  private readonly context = inject(RegionContextService);
  private readonly router = inject(Router);

  readonly slug = input.required<string>();

  protected readonly region = loadResource<PublicRegion, string>({
    params: () => this.slug(),
    loader: (slug) => this.regionsApi.getPublicRegion({ slug }),
  });
  protected readonly slots = loadResource<PublicIntroPickupPage, string>({
    params: () => this.slug(),
    loader: (slug) => this.api.listPublicIntroPickups({ slug, size: 100 }),
  });
  protected readonly member = computed(() => {
    const id = this.region.data()?.id;
    return !!id && isMemberOf(this.session.session(), id);
  });

  constructor() {
    effect(() => {
      const region = this.region.data();
      if (region) {
        this.context.registerTimeZone(region.id, region.timeZone);
      }
    });
  }

  protected notifyMe(region: PublicRegion): void {
    const target = `/me/intro-pickups?subscribe=${region.id}`;
    if (this.session.isAuthenticated()) {
      void this.router.navigateByUrl(target);
    } else {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: target } });
    }
  }
}
