import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { AppInfoService } from '../core/app-info.service';
import { TPipe } from '../core/i18n/t.pipe';

/**
 * Red "test site" banner on every page of the public test environment (`AppInfo.demoBanner`, staging profile,
 * decision 173). Never shown in production.
 */
@Component({
  selector: 'fs-demo-banner',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (appInfo.demoBanner()) {
      <div class="demo" role="note" data-testid="shell.demoBanner">
        <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ 'layout.demoBanner.text' | t }}</span>
      </div>
    }
  `,
  styles: `
    .demo {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.45rem 1rem;
      background: var(--fs-danger);
      color: var(--fs-on-danger);
      font-size: 0.85rem;
      font-weight: 650;
      line-height: 1.35;
      text-align: center;
    }
  `,
})
export class DemoBannerComponent {
  protected readonly appInfo = inject(AppInfoService);
}
