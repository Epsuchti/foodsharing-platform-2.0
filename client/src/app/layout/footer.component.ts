import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AppInfoService } from '../core/app-info.service';
import { TPipe } from '../core/i18n/t.pipe';
import { NavContextService } from './nav-context.service';

/** Footer: mission line, links (CMS pages arrive in M6) and the running version. */
@Component({
  selector: 'fs-footer',
  imports: [RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ftr">
      <p class="ftr__claim">
        <i class="pi pi-heart-fill" aria-hidden="true"></i>
        {{ 'layout.footer.claim' | t }}
      </p>
      <nav class="ftr__links" [attr.aria-label]="'layout.footer.label' | t">
        @for (item of nav.footer(); track item.id) {
          <a [routerLink]="item.route" [attr.data-testid]="'nav.footer.' + item.id">{{
            item.labelKey | t
          }}</a>
        }
      </nav>
      @if (appInfo.version(); as version) {
        <p class="ftr__version" data-testid="shell.version" data-screenshot-mask>
          {{ 'layout.footer.version' | t: { version } }}
        </p>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
      margin-top: 3rem;
      border-top: 1px solid var(--fs-border);
    }
    .ftr {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1.5rem;
      width: min(100%, var(--fs-page-max));
      margin: 0 auto;
      padding: 1.5rem var(--fs-gutter);
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .ftr__claim {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      margin: 0;
      font-weight: 600;
    }
    .ftr__claim .pi {
      color: var(--fs-primary);
    }
    .ftr__links {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 1rem;
    }
    .ftr__links a {
      color: inherit;
      font-weight: 500;
      text-decoration: none;
    }
    .ftr__links a:hover {
      color: var(--fs-primary-strong);
      text-decoration: underline;
    }
    .ftr__version {
      margin: 0 0 0 auto;
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class FooterComponent {
  protected readonly nav = inject(NavContextService);
  protected readonly appInfo = inject(AppInfoService);
}
