import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { Explainer, PublicOnboardingService, RegionRef } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { isUiLanguage } from '../../../core/i18n/languages';
import { SeoService } from '../../../core/i18n/seo.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { GoogleLogoComponent } from '../../../shared/google-logo/google-logo.component';
import { JourneyStepperComponent } from '../../../shared/journey-stepper/journey-stepper.component';
import { RegionPickerComponent } from '../../../shared/region-picker/region-picker.component';
import { RegionCardComponent } from '../../onboarding/components/region-card.component';
import { CountUpComponent } from '../components/count-up.component';

/** Google sign-up straight from the landing page: CTA → (fake) Google consent → account (D11.5.1, ≤ 2 clicks). */
export const GOOGLE_JOIN_URL = '/oauth2/authorization/google?returnUrl=/onboarding';

/** FAQ entries of the explainer (keys `public.explainer.faq.<id>.q/a`). */
export const EXPLAINER_FAQ = ['what', 'cost', 'time', 'who', 'safe'] as const;

/**
 * Public explainer (`/`, §11.2): hero with "join with Google", three values, live impact counters, the journey
 * preview, "find your region" with the region card (ambassadors, next EinAb dates), FAQ and business contact.
 */
@Component({
  selector: 'fs-landing-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    CountUpComponent,
    GoogleLogoComponent,
    JourneyStepperComponent,
    RegionCardComponent,
    RegionPickerComponent,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero" data-testid="public.landing.hero">
      <div class="hero__text">
        <p class="hero__eyebrow">{{ 'public.landing.eyebrow' | t }}</p>
        <h1 class="hero__title">{{ 'public.landing.title' | t }}</h1>
        <p class="hero__lead">{{ 'public.landing.lead' | t }}</p>
        <div class="hero__actions">
          <a
            class="fs-btn fs-btn--lg fs-btn--google hero__btn"
            [href]="googleUrl"
            data-testid="public.landing.joinGoogle"
          >
            <fs-google-logo />
            <span>{{ 'public.landing.joinGoogle' | t }}</span>
          </a>
          <a
            class="fs-btn fs-btn--lg hero__btn"
            routerLink="/login"
            [queryParams]="{ method: 'email' }"
            data-testid="public.landing.joinEmail"
          >
            <i class="pi pi-envelope" aria-hidden="true"></i>
            <span>{{ 'public.landing.joinEmail' | t }}</span>
          </a>
        </div>
        <p class="hero__login">
          {{ 'public.landing.haveAccount' | t }}
          <a routerLink="/login" data-testid="public.landing.login">{{
            'public.landing.login' | t
          }}</a>
        </p>
      </div>
      <div class="hero__art" aria-hidden="true">
        <div class="art__blob art__blob--a"></div>
        <div class="art__blob art__blob--b"></div>
        <div class="art__card art__card--main">
          <span class="art__example">{{ 'public.landing.artExample' | t }}</span>
          <i class="pi pi-shopping-bag"></i>
          <strong>{{ 'public.landing.artPickup' | t }}</strong>
          <span>{{ 'public.landing.artPickupTime' | t }}</span>
        </div>
        <div class="art__card art__card--side">
          <i class="pi pi-heart-fill"></i>
          <span>{{ 'public.landing.artSaved' | t }}</span>
        </div>
      </div>
    </section>

    <section class="values" [attr.aria-label]="'public.landing.valuesLabel' | t">
      <article class="value">
        <div class="value__icon"><i class="pi pi-shopping-bag" aria-hidden="true"></i></div>
        <h2>{{ 'public.landing.value1Title' | t }}</h2>
        <p>{{ 'public.landing.value1Text' | t }}</p>
      </article>
      <article class="value">
        <div class="value__icon"><i class="pi pi-share-alt" aria-hidden="true"></i></div>
        <h2>{{ 'public.landing.value2Title' | t }}</h2>
        <p>{{ 'public.landing.value2Text' | t }}</p>
      </article>
      <article class="value">
        <div class="value__icon"><i class="pi pi-users" aria-hidden="true"></i></div>
        <h2>{{ 'public.landing.value3Title' | t }}</h2>
        <p>{{ 'public.landing.value3Text' | t }}</p>
      </article>
    </section>
    <fs-data-state
      [state]="explainer"
      skeleton="card"
      [skeletonRows]="2"
      data-testid="public.explainer.state"
    >
      <ng-template [fsData]="explainer" let-e>
        <section class="block" data-testid="public.explainer.counters" aria-labelledby="ex-impact">
          <h2 id="ex-impact" class="block__title">{{ 'public.explainer.impactTitle' | t }}</h2>
          <ul class="counters">
            <li class="counter">
              <fs-count-up
                [value]="e.counters.foodsavers"
                testId="public.explainer.counter.foodsavers"
              /><span>{{ 'public.explainer.counter.foodsavers' | t }}</span>
            </li>
            <li class="counter">
              <fs-count-up
                [value]="e.counters.regions"
                testId="public.explainer.counter.regions"
              /><span>{{ 'public.explainer.counter.regions' | t }}</span>
            </li>
            <li class="counter">
              <fs-count-up
                [value]="e.counters.stores"
                testId="public.explainer.counter.stores"
              /><span>{{ 'public.explainer.counter.stores' | t }}</span>
            </li>
            <li class="counter">
              <fs-count-up
                [value]="e.counters.kgSaved"
                testId="public.explainer.counter.kgSaved"
              /><span>{{ 'public.explainer.counter.kgSaved' | t }}</span>
            </li>
          </ul>
        </section>
        <section class="block" data-testid="public.explainer.journey" aria-labelledby="ex-journey">
          <h2 id="ex-journey" class="block__title">{{ 'public.explainer.journeyTitle' | t }}</h2>
          <p class="block__lead">{{ 'public.explainer.journeyLead' | t }}</p>
          <fs-journey-stepper
            mode="preview"
            [preview]="e.journeyPreview"
            testId="public.explainer.preview"
          />
        </section>
      </ng-template>
    </fs-data-state>

    <section class="block" data-testid="public.explainer.region" aria-labelledby="ex-region">
      <h2 id="ex-region" class="block__title">{{ 'public.explainer.regionTitle' | t }}</h2>
      <p class="block__lead">{{ 'public.explainer.regionLead' | t }}</p>
      @defer (on viewport) {
        <div class="region">
          <fs-region-picker
            (regionSelected)="region.set($event)"
            testId="public.explainer.picker"
          />
          @if (region(); as r) {
            <fs-region-card [regionId]="r.id" testId="public.explainer.regionCard" />
          }
        </div>
      } @placeholder {
        <div class="region region--placeholder"></div>
      }
    </section>

    <section class="block" data-testid="public.explainer.faq" aria-labelledby="ex-faq">
      <h2 id="ex-faq" class="block__title">{{ 'public.explainer.faqTitle' | t }}</h2>
      <div class="faq">
        @for (id of faq; track id) {
          <details class="faq__item" [attr.data-testid]="'public.explainer.faq.' + id">
            <summary>{{ 'public.explainer.faq.' + id + '.q' | t }}</summary>
            <p>{{ 'public.explainer.faq.' + id + '.a' | t }}</p>
          </details>
        }
      </div>
    </section>

    <section
      class="block block--contact"
      data-testid="public.explainer.business"
      aria-labelledby="ex-business"
    >
      <h2 id="ex-business" class="block__title">{{ 'public.explainer.businessTitle' | t }}</h2>
      <p class="block__lead">{{ 'public.explainer.businessText' | t }}</p>
      <a
        class="fs-btn fs-btn--outlined"
        [href]="'mailto:' + businessMail"
        data-testid="public.explainer.businessMail"
      >
        <i class="pi pi-envelope" aria-hidden="true"></i>{{ 'public.explainer.businessCta' | t }}
      </a>
    </section>
  `,
  styles: `
    :host {
      display: grid;
      gap: clamp(2.5rem, 2rem + 3vw, 4.5rem);
    }
    .hero {
      display: grid;
      align-items: center;
      gap: 2.5rem;
      padding-top: clamp(0.5rem, 2vw, 2rem);
    }
    @media (min-width: 56rem) {
      .hero {
        grid-template-columns: 1.15fr 1fr;
      }
    }
    .hero__text {
      display: grid;
      gap: 1.1rem;
    }
    .hero__eyebrow {
      display: inline-flex;
      justify-self: start;
      margin: 0;
      padding: 0.35rem 0.8rem;
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 0.82rem;
      font-weight: 700;
    }
    .hero__title {
      margin: 0;
      font-size: clamp(2.1rem, 1.4rem + 3.2vw, 3.6rem);
      font-weight: 800;
      letter-spacing: -0.035em;
      line-height: 1.05;
      text-wrap: balance;
    }
    .hero__lead {
      max-width: 34rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: clamp(1.02rem, 0.95rem + 0.4vw, 1.2rem);
      line-height: 1.6;
    }
    .hero__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      margin-top: 0.5rem;
    }
    .hero__btn {
      flex: 1 1 15rem;
      justify-content: center;
      gap: 0.6rem;
      text-decoration: none;
    }
    @media (min-width: 40rem) {
      .hero__btn {
        flex: 0 0 auto;
      }
    }
    .hero__login {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.95rem;
    }
    .hero__art {
      position: relative;
      display: none;
      min-height: 22rem;
    }
    @media (min-width: 56rem) {
      .hero__art {
        display: block;
      }
    }
    .art__blob {
      position: absolute;
      border-radius: 50%;
      filter: blur(2px);
    }
    .art__blob--a {
      inset: 8% 10% 6% 12%;
      background: radial-gradient(
        circle at 30% 30%,
        var(--fs-primary-200),
        var(--fs-primary-soft) 70%
      );
      border-radius: 42% 58% 55% 45% / 48% 42% 58% 52%;
    }
    .art__blob--b {
      right: 4%;
      bottom: 4%;
      width: 34%;
      height: 34%;
      background: color-mix(in srgb, var(--fs-accent) 45%, transparent);
    }
    .art__card {
      position: absolute;
      display: grid;
      pointer-events: none;
      user-select: none;
      gap: 0.3rem;
      padding: 1.1rem 1.3rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-lg);
    }
    .art__card .pi {
      color: var(--fs-primary);
      font-size: 1.4rem;
    }
    .art__card span {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    /* an illustration, not a control: tilted, labelled "example", no hover/pointer */
    .art__card--main {
      top: 24%;
      left: 18%;
      width: 15rem;
      transform: rotate(-3deg);
    }
    .art__example {
      justify-self: start;
      padding: 0.1rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
      font-size: 0.7rem !important;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .art__card--side {
      right: 10%;
      bottom: 18%;
      transform: rotate(4deg);
      grid-auto-flow: column;
      align-items: center;
      gap: 0.6rem;
    }
    .art__card--side .pi {
      color: #d0583c;
      font-size: 1.1rem;
    }
    .values {
      display: grid;
      gap: 1rem;
    }
    @media (min-width: 48rem) {
      .values {
        grid-template-columns: repeat(3, 1fr);
        gap: 1.25rem;
      }
    }
    .value {
      display: grid;
      align-content: start;
      gap: 0.5rem;
      padding: 1.5rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-sm);
    }
    .value h2 {
      margin: 0.35rem 0 0;
      font-size: 1.1rem;
    }
    .value p {
      margin: 0;
      color: var(--fs-text-muted);
    }
    .value__icon {
      display: grid;
      width: 2.75rem;
      height: 2.75rem;
      place-items: center;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.2rem;
    }
    .block {
      display: grid;
      gap: 1rem;
    }
    .block__title {
      margin: 0;
      font-size: clamp(1.4rem, 1.1rem + 1vw, 1.9rem);
    }
    .block__lead {
      margin: 0;
      max-width: 46rem;
      color: var(--fs-text-muted);
    }
    .counters {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 10rem), 1fr));
      gap: 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .counter {
      display: grid;
      gap: 0.25rem;
      padding: 1.25rem;
      border-radius: 1rem;
      background: var(--fs-primary-soft);
      color: var(--fs-text);
    }
    .counter fs-count-up {
      color: var(--fs-primary-strong);
      font-size: clamp(1.8rem, 1.4rem + 1.5vw, 2.6rem);
      font-weight: 800;
      font-variant-numeric: tabular-nums;
    }
    .region {
      display: grid;
      gap: 1rem;
    }
    @media (min-width: 56rem) {
      .region {
        grid-template-columns: 1fr 1fr;
        align-items: start;
      }
    }
    .region--placeholder {
      min-height: 12rem;
    }
    .faq {
      display: grid;
      gap: 0.5rem;
    }
    .faq__item {
      padding: 0.85rem 1rem;
      border: 1px solid var(--fs-border);
      border-radius: 0.75rem;
      background: var(--fs-surface);
    }
    .faq__item summary {
      cursor: pointer;
      font-weight: 650;
    }
    .faq__item p {
      margin: 0.6rem 0 0;
      color: var(--fs-text-muted);
    }
    .block--contact {
      justify-items: start;
      padding: 1.5rem;
      border-radius: 1rem;
      background: var(--fs-surface-muted);
    }
  `,
})
export class LandingPageComponent {
  private readonly api = inject(PublicOnboardingService);

  /** Route param of `/de`, `/en`, … (unset on `/`). */
  readonly lang = input<string>();

  protected readonly googleUrl = GOOGLE_JOIN_URL;
  protected readonly faq = EXPLAINER_FAQ;
  protected readonly businessMail = 'info@foodsharing.ch';
  protected readonly region = signal<RegionRef | undefined>(undefined);

  protected readonly explainer = loadResource<Explainer>({
    loader: () => this.api.getExplainer({}),
    isEmpty: () => false,
  });

  constructor() {
    const seo = inject(SeoService);
    effect(() => {
      const lang = this.lang();
      seo.setLandingAlternates(isUiLanguage(lang) ? lang : null);
    });
    inject(DestroyRef).onDestroy(() => seo.clearLandingAlternates());
  }
}
