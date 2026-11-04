import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Skeleton } from '@openng/optimus-ui/skeleton';

import { Explainer, PublicOnboardingService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { isUiLanguage } from '../../../core/i18n/languages';
import { SeoService } from '../../../core/i18n/seo.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { GoogleLogoComponent } from '../../../shared/google-logo/google-logo.component';
import { CountUpComponent } from '../components/count-up.component';
import { LandingBubbleComponent } from '../components/landing-bubble.component';
import { LandingJourneyComponent } from '../components/landing-journey.component';
import { LandingMapPreviewComponent } from '../components/landing-map-preview.component';
import { LandingNearComponent } from '../components/landing-near.component';
import { LandingMarkersResource } from '../landing-markers.resource';

/** Google sign-up straight from the landing page: CTA → (fake) Google consent → account (D11.5.1, ≤ 2 clicks). */
export const GOOGLE_JOIN_URL = '/oauth2/authorization/google?returnUrl=/onboarding';

/** FAQ of the landing page in three groups (keys `public.explainer.faq.<id>.q/a`, `…faq.group.<group>`). */
export const LANDING_FAQ_GROUPS = [
  { id: 'join', items: ['what', 'who', 'cost', 'time', 'id'] },
  { id: 'pickup', items: ['duties', 'cancel', 'all', 'safe'] },
  { id: 'share', items: ['fairteiler', 'basket', 'business'] },
] as const;

/** Ways to take part ("Worum es geht"), keys `public.landing.way.<id>.title/text`. */
const WAYS = [
  { id: 'member', icon: 'pi-shopping-bag' },
  { id: 'home', icon: 'pi-home' },
  { id: 'business', icon: 'pi-shop' },
] as const;

/** Benefits for businesses, keys `public.landing.business.benefit.<id>.title/text`. */
const BENEFITS = [
  { id: 'free', icon: 'pi-heart' },
  { id: 'cost', icon: 'pi-trash' },
  { id: 'staff', icon: 'pi-users' },
  { id: 'accept', icon: 'pi-shopping-cart' },
  { id: 'reliable', icon: 'pi-clock' },
  { id: 'liability', icon: 'pi-shield' },
] as const;

/** Other ways to help, keys `public.landing.moreWays.<id>.title/text`. */
const MORE_WAYS = [
  { id: 'basket', icon: 'pi-shopping-cart' },
  { id: 'donate', icon: 'pi-heart' },
  { id: 'it', icon: 'pi-code' },
] as const;

/** What a foodsaver promises / does not have to do, keys `public.landing.duties.<id>…`, `…duties.not.<id>…`. */
const DUTIES = [
  { id: 'reliable', icon: 'pi-clock' },
  { id: 'take', icon: 'pi-shopping-cart' },
  { id: 'nosale', icon: 'pi-heart' },
  { id: 'conduct', icon: 'pi-shop' },
  { id: 'privacy', icon: 'pi-shield' },
] as const;
const NOT_DUTIES = ['min', 'money', 'alone'] as const;

/** Partner request: `/support` prefills the subject for this allowlisted preset only. */
export const PARTNER_SUPPORT_QUERY = { from: '/', subject: 'partner' } as const;

/**
 * Public landing page (`/`, `/de|gws|fr|it|en`; C17, decision 159 = Variant D of
 * docs/design/landing-page-proposal.md): hero with E-Mail first and Google second, "Worum es geht", the live
 * impact counters (the only DataState of the explainer call), four static start steps, "In deiner Nähe" (postcode
 * search and a tile-free map preview, deferred until it scrolls into view), Fairteiler, businesses, other ways
 * to help, duties, FAQ in three groups, a CTA band and on phones a sticky "Jetzt mitmachen".
 * No MapView, no tiles and no third-party host are requested from this page.
 */
@Component({
  selector: 'fs-landing-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    CountUpComponent,
    GoogleLogoComponent,
    LandingBubbleComponent,
    LandingJourneyComponent,
    LandingMapPreviewComponent,
    LandingNearComponent,
    RouterLink,
    Skeleton,
    TPipe,
  ],
  providers: [LandingMarkersResource],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.scss',
})
export class LandingPageComponent {
  private readonly api = inject(PublicOnboardingService);
  private readonly document = inject(DOCUMENT);

  /** Route param of `/de`, `/en`, … (unset on `/`). */
  readonly lang = input<string>();

  protected readonly googleUrl = GOOGLE_JOIN_URL;
  protected readonly faqGroups = LANDING_FAQ_GROUPS;
  protected readonly ways = WAYS;
  protected readonly benefits = BENEFITS;
  protected readonly moreWays = MORE_WAYS;
  protected readonly duties = DUTIES;
  protected readonly notDuties = NOT_DUTIES;
  protected readonly partnerQuery = PARTNER_SUPPORT_QUERY;
  protected readonly rulesUrl = 'https://wiki.foodsharing.network/wiki/Verhaltensregeln';
  protected readonly wikiUrl = 'https://wiki.foodsharing.network/wiki/Foodsharer';

  protected readonly explainer = loadResource<Explainer>({
    loader: () => this.api.getExplainer({}),
    isEmpty: () => false,
  });

  private readonly hero = viewChild.required<ElementRef<HTMLElement>>('hero');
  private readonly heroVisible = signal(true);
  private readonly footerVisible = signal(false);
  /** Phones only (CSS): after the hero has scrolled out, hidden again above the footer. */
  protected readonly showSticky = computed(() => !this.heroVisible() && !this.footerVisible());

  constructor() {
    const seo = inject(SeoService);
    const destroyRef = inject(DestroyRef);
    effect(() => {
      const lang = this.lang();
      seo.setLandingAlternates(isUiLanguage(lang) ? lang : null);
    });
    destroyRef.onDestroy(() => seo.clearLandingAlternates());

    // browser only (afterNextRender never runs while prerendering)
    afterNextRender(() => {
      const Observer = this.document.defaultView?.IntersectionObserver;
      if (!Observer) {
        return;
      }
      const hero = this.hero().nativeElement;
      const footer = this.document.querySelector('fs-footer');
      const observer = new Observer((entries) => {
        for (const entry of entries) {
          if (entry.target === hero) {
            this.heroVisible.set(entry.isIntersecting);
          } else {
            this.footerVisible.set(entry.isIntersecting);
          }
        }
      });
      observer.observe(hero);
      if (footer) {
        observer.observe(footer);
      }
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }
}
