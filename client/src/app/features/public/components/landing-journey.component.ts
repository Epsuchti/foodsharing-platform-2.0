import { ChangeDetectionStrategy, Component } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';

/** The four steps of the landing page (decision 160, O11 replaced); `/onboarding` keeps its eight. */
export const LANDING_STEPS = [1, 2, 3, 4] as const;

/**
 * "Der Start dauert nur 2 Minuten" (C17): a static overview of the way in — phase labels Online / Vor Ort / Dabei,
 * four numbered steps on a line with a duration chip, and the end node "Du holst selbst ab". No buttons, no
 * detail panel, no `aria-current` (proposal §2, round 3). Horizontal from 900 px of the page container, vertical
 * below (line on the left).
 */
@Component({
  selector: 'fs-landing-journey',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="jp" data-testid="public.explainer.preview">
      <li class="jp__phase jp__phase--online">{{ 'public.landing.phase.online' | t }}</li>
      <li class="jp__phase jp__phase--onsite">{{ 'public.landing.phase.onsite' | t }}</li>
      <li class="jp__phase jp__phase--in">{{ 'public.landing.phase.in' | t }}</li>
      @for (n of steps; track n) {
        <li
          class="jp__step"
          [class.jp__step--last]="n === steps.length"
          [attr.data-testid]="'public.landing.journey.step.' + n"
        >
          <span class="jp__n" aria-hidden="true">{{ n }}</span>
          <span class="jp__title">{{ 'public.landing.step.' + n + '.title' | t }}</span>
          <span class="jp__text">{{ 'public.landing.step.' + n + '.text' | t }}</span>
          <span class="jp__chip">{{ 'public.landing.step.' + n + '.duration' | t }}</span>
        </li>
      }
      <li class="jp__end">
        <span class="jp__n jp__n--end" aria-hidden="true"><i class="pi pi-shopping-bag"></i></span>
        <span class="jp__end-text">{{ 'public.landing.step.end' | t }}</span>
      </li>
    </ol>
  `,
  styles: `
    :host {
      display: block;
    }
    .jp {
      display: grid;
      grid-template-columns: repeat(4, 1fr) auto;
      gap: 0 0.6rem;
      margin: 1.4rem 0 0;
      padding: 0;
      list-style: none;
    }
    .jp__phase {
      grid-row: 1;
      margin-bottom: 1rem;
      padding-bottom: 0.5rem;
      border-bottom: 2px solid var(--fs-primary-200);
      color: var(--fs-primary-strong);
      font-size: 0.78rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .jp__phase--online {
      grid-column: 1 / 4;
    }
    .jp__phase--onsite {
      grid-column: 4;
    }
    .jp__phase--in {
      grid-column: 5;
    }
    .jp__step {
      position: relative;
      display: grid;
      grid-row: 2;
      align-content: start;
      gap: 0.4rem;
      padding: 0 0.2rem;
    }
    .jp__step::before {
      content: '';
      position: absolute;
      top: 1.3rem;
      right: -0.8rem;
      left: 1.3rem;
      height: 2px;
      background: var(--fs-border-strong);
    }
    .jp__step--last::before {
      display: none;
    }
    .jp__n {
      position: relative;
      z-index: 1;
      display: grid;
      width: 2.6rem;
      height: 2.6rem;
      place-items: center;
      border: 2px solid var(--fs-surface);
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-weight: 800;
    }
    .jp__title {
      margin-top: 0.3rem;
      font-weight: 750;
    }
    .jp__text {
      color: var(--fs-text-muted);
      font-size: 0.93rem;
    }
    .jp__chip {
      justify-self: start;
      padding: 0.15rem 0.6rem;
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 0.78rem;
      font-weight: 700;
    }
    .jp__n--end {
      background: var(--fs-primary);
      color: var(--fs-on-primary);
    }
    .jp__end-text {
      color: var(--fs-primary-strong);
      font-weight: 750;
    }
    .jp__end {
      display: grid;
      grid-row: 2;
      align-content: start;
      justify-items: center;
      gap: 0.4rem;
      min-width: 5rem;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
      text-align: center;
    }
    @container landing (max-width: 899px) {
      .jp {
        grid-template-columns: auto 1fr;
        gap: 0.4rem 0.9rem;
      }
      .jp__phase {
        grid-row: auto;
        grid-column: 1 / 3 !important;
        margin: 0;
        padding-top: 0.6rem;
      }
      .jp__step {
        grid-row: auto;
        grid-column: 1 / 3;
        grid-template-columns: 2.6rem 1fr;
        grid-template-areas: 'n t' 'n x' 'n c';
        gap: 0.2rem 0.8rem;
        padding: 0 0 1.4rem;
      }
      .jp__step::before {
        top: 2.6rem;
        right: auto;
        bottom: 0;
        left: 1.3rem;
        width: 2px;
        height: auto;
      }
      /* step 3 ends the "Online" phase: the next phase label sits between it and step 4 */
      .jp__step:nth-child(6)::before {
        display: none;
      }
      .jp__n {
        grid-area: n;
      }
      .jp__title {
        grid-area: t;
        margin: 0;
      }
      .jp__text {
        grid-area: x;
      }
      .jp__chip {
        grid-area: c;
      }
      .jp__end {
        grid-row: auto;
        grid-column: 1 / 3;
        grid-template-columns: 2.6rem 1fr;
        grid-template-areas: 'n t';
        align-items: center;
        justify-items: start;
        gap: 0.2rem 0.8rem;
        min-width: 0;
        font-size: 1rem;
        text-align: left;
      }
      .jp__end-text {
        grid-area: t;
      }
      /* vertical order: phase label, its steps, next phase label */
      .jp__phase--online {
        order: 0;
      }
      .jp__step {
        order: 1;
      }
      .jp__phase--onsite {
        order: 2;
      }
      .jp__step--last {
        order: 3;
      }
      .jp__phase--in {
        order: 4;
      }
      .jp__end {
        order: 5;
      }
    }
  `,
})
export class LandingJourneyComponent {
  protected readonly steps = LANDING_STEPS;
}
