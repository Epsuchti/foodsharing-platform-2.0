import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { PublicRegion, PublicRegionsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { IntroPickupDatesComponent } from '../shared/intro-pickup-dates.component';

/**
 * /r/:slug (R4): public region page for visitors: description, contact, ambassadors, sub-regions, the region pin
 * and the next intro pickups, with a call to sign up (or, logged in, to the full region page).
 */
@Component({
  selector: 'fs-public-region-page',
  imports: [...DATA_STATE_IMPORTS, IntroPickupDatesComponent, MapViewComponent, MarkdownComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="region" skeleton="card" testId="regions.public.state" data-testid="regions.public.state" backLink="/find-region">
      <ng-template [fsData]="region" let-r>
        <header class="hero fs-card" data-testid="regions.public.hero">
          @if (r.photoUrl) {
            <img class="hero__photo" [src]="r.photoUrl" alt="" width="160" height="160" />
          }
          <div class="hero__body">
            @if (r.parent; as parent) {
              <a class="hero__parent" [routerLink]="['/r', parent.slug]" data-testid="regions.public.parent">
                <i class="pi pi-arrow-up" aria-hidden="true"></i>{{ parent.name }}
              </a>
            }
            <span class="hero__type">{{ 'regions.type.' + r.type | t }}</span>
            <h1 class="hero__name" data-testid="regions.public.name">{{ r.name }}</h1>
            @if (r.teaser) {
              <p class="hero__teaser">{{ r.teaser }}</p>
            }
            <p class="hero__facts">
              <span>{{ 'regions.page.members' | t: { count: r.memberCount } }}</span>
              <span>{{ 'regions.public.stores' | t: { count: r.storeCount } }}</span>
            </p>
            <div class="hero__actions">
              @if (loggedIn()) {
                <a class="fs-btn" [routerLink]="['/regions', r.id]" data-testid="regions.public.open">
                  <i class="pi pi-arrow-right" aria-hidden="true"></i>{{ 'regions.public.open' | t }}
                </a>
              } @else {
                <a class="fs-btn" routerLink="/login" data-testid="regions.public.signup">
                  <i class="pi pi-user-plus" aria-hidden="true"></i>{{ 'regions.public.signup' | t }}
                </a>
              }
            </div>
          </div>
        </header>

        <div class="cols">
          <div class="stack">
            <section class="fs-card card" data-testid="regions.public.about">
              <h2 class="card__title"><i class="pi pi-info-circle" aria-hidden="true"></i>{{ 'regions.overview.about' | t }}</h2>
              @if (r.description) {
                <fs-markdown [markdown]="r.description" />
              } @else {
                <p class="muted">{{ 'regions.overview.noDescription' | t }}</p>
              }
            </section>
            <section class="fs-card card" data-testid="regions.public.intro">
              <h2 class="card__title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'regions.overview.nextIntro' | t }}</h2>
              <fs-intro-pickup-dates [pickups]="r.nextIntroPickups" testId="regions.public.introDates" />
            </section>
            @if (markers().length) {
              <section class="fs-card card">
                <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i>{{ 'regions.public.map' | t }}</h2>
                <fs-map-view [markers]="markers()" [zoom]="12" [ariaLabel]="'regions.public.map' | t" testId="regions.public.map" />
              </section>
            }
          </div>
          <div class="stack">
            <section class="fs-card card" data-testid="regions.public.contact">
              <h2 class="card__title"><i class="pi pi-envelope" aria-hidden="true"></i>{{ 'regions.public.contact' | t }}</h2>
              @if (r.contactEmail) {
                <a [href]="'mailto:' + r.contactEmail" data-testid="regions.public.email">{{ r.contactEmail }}</a>
              }
              @if (r.ambassadorNames.length) {
                <p class="muted">{{ 'regions.overview.ambassadors' | t }}: {{ r.ambassadorNames.join(', ') }}</p>
              } @else if (!r.contactEmail) {
                <p class="muted">{{ 'regions.public.noContact' | t }}</p>
              }
            </section>
            @if (r.children.length) {
              <section class="fs-card card" data-testid="regions.public.children">
                <h2 class="card__title"><i class="pi pi-sitemap" aria-hidden="true"></i>{{ 'regions.public.children' | t }}</h2>
                <ul class="links">
                  @for (child of r.children; track child.id) {
                    <li><a [routerLink]="['/r', child.slug]" data-testid="regions.public.child">{{ child.name }}</a></li>
                  }
                </ul>
              </section>
            }
          </div>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrls: ['../tabs/tab.scss', './public-region-page.component.scss'],
})
export class PublicRegionPageComponent {
  private readonly api = inject(PublicRegionsService);
  private readonly session = inject(SessionService);
  private readonly slug = toSignal(inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('slug') ?? undefined)));

  protected readonly loggedIn = this.session.isAuthenticated;

  protected readonly region = loadResource<PublicRegion, string>({
    params: () => this.slug(),
    loader: (slug) => this.api.getPublicRegion({ slug }),
  });

  protected readonly markers = computed<MapMarker[]>(() => {
    const region = this.region.data();
    const pin = region?.pin;
    return region && pin?.active
      ? [{ id: region.id, kind: 'region-pin', lat: pin.latitude, lon: pin.longitude, label: region.name }]
      : [];
  });
}
