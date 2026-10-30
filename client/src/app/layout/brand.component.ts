import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { TPipe } from '../core/i18n/t.pipe';

/** Logo mark + wordmark, links home. */
@Component({
  selector: 'fs-brand',
  imports: [RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a
      class="brand"
      [routerLink]="link()"
      [attr.aria-label]="'layout.brand.home' | t"
      data-testid="shell.brand"
    >
      <svg class="brand__mark" viewBox="0 0 64 64" aria-hidden="true">
        <defs>
          <linearGradient id="fs-brand-gradient" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#2f9a58" />
            <stop offset="1" stop-color="#195d35" />
          </linearGradient>
        </defs>
        <rect width="64" height="64" rx="18" fill="url(#fs-brand-gradient)" />
        <path
          d="M32 50c-9.5-5.6-16-12.2-16-20.2C16 23.3 20.6 19 26 19c2.6 0 4.6 1 6 2.8C33.4 20 35.4 19 38 19c5.4 0 10 4.3 10 10.8C48 37.8 41.5 44.4 32 50z"
          fill="#fff"
        />
        <path
          d="M32 21.5c.4-3.6 2.4-6.6 6-8.3"
          stroke="#d8f3df"
          stroke-width="3"
          stroke-linecap="round"
          fill="none"
        />
      </svg>
      <span class="brand__word">food<span>sharing</span></span>
    </a>
  `,
  styles: `
    /* never squeezed by a long header nav (design review M7 R1 #12: the logo shrank to a dot) */
    :host {
      display: block;
      flex: none;
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      color: var(--fs-text);
      text-decoration: none;
    }
    @media (pointer: coarse) {
      .brand {
        min-height: 2.75rem;
      }
    }
    .brand__mark {
      flex: none;
      width: 2.1rem;
      height: 2.1rem;
      filter: drop-shadow(0 4px 10px rgb(25 93 53 / 0.22));
    }
    .brand__word {
      font-size: 1.15rem;
      font-weight: 800;
      letter-spacing: -0.03em;
    }
    /*
     * phones (360–480px): the mark alone, so the header actions fit; the link keeps its name (aria-label). With the
     * phone search toggle the signed-in actions need ~260px: the wordmark beside them widened the page by ~36px on a
     * 412px phone, which shifted the fixed tab bar and every tap target against the layout viewport.
     */
    @media (max-width: 29.99rem) {
      .brand__word {
        display: none;
      }
    }
    .brand__word span {
      color: var(--fs-primary);
    }
  `,
})
export class BrandComponent {
  readonly link = input<string>('/');
}
