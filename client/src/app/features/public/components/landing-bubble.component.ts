import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';

export type LandingBubbleKind = 'hero' | 'mission' | 'fsp' | 'business' | 'ways';

interface BubbleCards {
  readonly mainIcon: string;
  readonly mainKey: string;
  readonly mainSubKey: string;
  readonly sideIcon: string;
  readonly sideKey: string;
}

/** The Fairteiler glyph of the map (`KIND_ICON.fsp` in map-view), copied so map-view stays untouched. */
const FSP_ICON = 'pi-box';

const CARDS: Readonly<Record<LandingBubbleKind, BubbleCards>> = {
  hero: {
    mainIcon: 'pi-shopping-bag',
    mainKey: 'public.landing.artPickup',
    mainSubKey: 'public.landing.artPickupTime',
    sideIcon: 'pi-heart-fill',
    sideKey: 'public.landing.artSaved',
  },
  mission: {
    mainIcon: 'pi-shop',
    mainKey: 'public.landing.art.mission.main',
    mainSubKey: 'public.landing.art.mission.mainSub',
    sideIcon: 'pi-users',
    sideKey: 'public.landing.art.mission.side',
  },
  fsp: {
    mainIcon: FSP_ICON,
    mainKey: 'public.landing.art.fsp.main',
    mainSubKey: 'public.landing.art.fsp.mainSub',
    sideIcon: 'pi-shopping-cart',
    sideKey: 'public.landing.art.fsp.side',
  },
  business: {
    mainIcon: 'pi-truck',
    mainKey: 'public.landing.art.business.main',
    mainSubKey: 'public.landing.art.business.mainSub',
    sideIcon: 'pi-shield',
    sideKey: 'public.landing.art.business.side',
  },
  ways: {
    mainIcon: 'pi-shopping-cart',
    mainKey: 'public.landing.art.ways.main',
    mainSubKey: 'public.landing.art.ways.mainSub',
    sideIcon: 'pi-code',
    sideKey: 'public.landing.art.ways.side',
  },
};

/**
 * Bubble illustration of the landing page (C17, proposal §2 "Bubbles"): a green blob, a warm blob (`--fs-accent`
 * at 45 %, the only place the accent appears besides the Fairteiler pins), one main card tilted −3° and one side
 * card tilted +4°. Decorative only: `aria-hidden`, no pointer events, every card is labelled "Beispiel".
 * Below 900 px of the page container it is 14 rem high and clipped.
 */
@Component({
  selector: 'fs-landing-bubble',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[class.bub--sm]': "size() === 'sm'",
    '[attr.data-kind]': 'kind()',
  },
  template: `
    <div class="bub__blob bub__blob--a"></div>
    <div class="bub__blob bub__blob--b"></div>
    <div class="bub__card bub__card--main">
      <span class="bub__example">{{ 'public.landing.artExample' | t }}</span>
      <i class="pi {{ cards().mainIcon }}"></i>
      <strong>{{ cards().mainKey | t }}</strong>
      <span>{{ cards().mainSubKey | t }}</span>
    </div>
    <div class="bub__card bub__card--side">
      <i class="pi {{ cards().sideIcon }}"></i>
      <span>{{ cards().sideKey | t }}</span>
    </div>
  `,
  styles: `
    :host {
      position: relative;
      display: block;
      min-height: 22rem;
      pointer-events: none;
      user-select: none;
    }
    :host(.bub--sm) {
      min-height: 13rem;
    }
    .bub__blob {
      position: absolute;
      border-radius: 50%;
      filter: blur(2px);
    }
    .bub__blob--a {
      inset: 8% 10% 6% 12%;
      background: radial-gradient(
        circle at 30% 30%,
        var(--fs-primary-200),
        var(--fs-primary-soft) 70%
      );
      border-radius: 42% 58% 55% 45% / 48% 42% 58% 52%;
    }
    .bub__blob--b {
      right: 4%;
      bottom: 4%;
      width: 34%;
      height: 34%;
      background: color-mix(in srgb, var(--fs-accent) 45%, transparent);
    }
    .bub__card {
      position: absolute;
      display: grid;
      gap: 0.3rem;
      padding: 1rem 1.2rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-lg);
      font-size: 0.95rem;
    }
    .bub__card .pi {
      color: var(--fs-primary);
      font-size: 1.4rem;
    }
    .bub__card span {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .bub__card strong {
      font-weight: 750;
    }
    .bub__example {
      justify-self: start;
      padding: 0.1rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.7rem !important;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .bub__card--main {
      top: 22%;
      left: 14%;
      width: 15rem;
      transform: rotate(-3deg);
    }
    .bub__card--side {
      right: 8%;
      bottom: 16%;
      grid-auto-flow: column;
      align-items: center;
      gap: 0.6rem;
      transform: rotate(4deg);
    }
    .bub__card--side .pi {
      font-size: 1.1rem;
    }
    :host([data-kind='hero']) .bub__card--side .pi {
      color: #d0583c;
    }
    :host(.bub--sm) .bub__card--main {
      top: 14%;
      left: 6%;
      width: 13rem;
    }
    :host(.bub--sm) .bub__card--side {
      right: 4%;
      bottom: 10%;
    }
    /* phone: the bubble shrinks, is clipped and sits below (or above) the text; nothing overflows */
    @container landing (max-width: 899px) {
      :host,
      :host(.bub--sm) {
        min-height: 14rem;
        overflow: hidden;
        border-radius: var(--fs-radius-xl);
      }
      .bub__card {
        padding: 0.7rem 0.9rem;
        font-size: 0.85rem;
      }
      .bub__card--main,
      :host(.bub--sm) .bub__card--main {
        top: 16%;
        left: 4%;
        width: 12rem;
      }
      .bub__card--side,
      :host(.bub--sm) .bub__card--side {
        right: 3%;
        bottom: 8%;
      }
      .bub__blob--a {
        inset: 6%;
      }
    }
  `,
})
export class LandingBubbleComponent {
  readonly kind = input.required<LandingBubbleKind>();
  readonly size = input<'lg' | 'sm'>('lg');

  protected readonly cards = computed(() => CARDS[this.kind()]);
}
