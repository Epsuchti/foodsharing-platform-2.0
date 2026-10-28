import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { GoogleLogoComponent } from '../../../shared/google-logo/google-logo.component';
import { TPipe } from '../../../core/i18n/t.pipe';

/** M1 placeholder for the public explainer (M2 replaces it): hero + join buttons + three values. */
@Component({
  selector: 'fs-landing-page',
  imports: [GoogleLogoComponent, RouterLink, TPipe],
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
            routerLink="/login"
            [queryParams]="{ method: 'google' }"
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
      background: color-mix(in srgb, #f2b84b 45%, transparent);
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
  `,
})
export class LandingPageComponent {}
