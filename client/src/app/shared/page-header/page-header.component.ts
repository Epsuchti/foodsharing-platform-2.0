import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Page title block: optional back link, eyebrow, title, subtitle and projected actions (right on desktop,
 * below on mobile). `<fs-page-header [title]="…" [subtitle]="…"><p-button … /></fs-page-header>`
 */
@Component({
  selector: 'fs-page-header',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-page-header' },
  template: `
    @if (backLink()) {
      <a class="ph__back" [routerLink]="backLink()" data-testid="shared.pageHeader.back">
        <i class="pi pi-arrow-left" aria-hidden="true"></i>
        <span>{{ backLabel() }}</span>
      </a>
    }
    <div class="ph__row">
      @if (icon()) {
        <div class="ph__icon" aria-hidden="true"><i [class]="icon()"></i></div>
      }
      <div class="ph__text">
        @if (eyebrow()) {
          <p class="ph__eyebrow">{{ eyebrow() }}</p>
        }
        <h1 class="ph__title">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="ph__subtitle">{{ subtitle() }}</p>
        }
      </div>
      <div class="ph__actions"><ng-content /></div>
    </div>
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.75rem;
      margin-bottom: 1.5rem;
    }
    .ph__back {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      justify-self: start;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
      font-weight: 600;
      text-decoration: none;
    }
    .ph__back:hover {
      color: var(--fs-primary-strong);
    }
    /* touch: 44px tap target without moving the title (design review M7 R1) */
    @media (pointer: coarse) {
      .ph__back {
        min-height: 2.75rem;
        margin-block: -0.6rem;
      }
    }
    .ph__row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 1rem 1.25rem;
    }
    .ph__icon {
      display: grid;
      width: 3rem;
      height: 3rem;
      flex: 0 0 auto;
      place-items: center;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.3rem;
    }
    .ph__text {
      display: grid;
      flex: 1 1 16rem;
      min-width: 0;
      gap: 0.25rem;
    }
    .ph__eyebrow {
      margin: 0;
      color: var(--fs-primary-strong);
      font-size: 0.78rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .ph__title {
      margin: 0;
      font-size: clamp(1.5rem, 1.2rem + 1.4vw, 2.1rem);
      font-weight: 750;
      letter-spacing: -0.025em;
      line-height: 1.15;
    }
    .ph__subtitle {
      margin: 0;
      max-width: 44rem;
      color: var(--fs-text-muted);
      font-size: 1rem;
    }
    .ph__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.6rem;
    }
    .ph__actions:empty {
      display: none;
    }
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | undefined>(undefined);
  readonly eyebrow = input<string | undefined>(undefined);
  readonly icon = input<string | undefined>(undefined);
  readonly backLink = input<string | unknown[] | undefined>(undefined);
  readonly backLabel = input<string | undefined>(undefined);
}
