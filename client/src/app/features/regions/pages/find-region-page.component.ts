import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { RegionRef } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RegionPickerComponent } from '../../../shared/region-picker/region-picker.component';

/**
 * /find-region (R2/R4): find a region by postcode, location or the tree. Logged-in users land on the region page
 * (join there), visitors on the public region page.
 */
@Component({
  selector: 'fs-find-region-page',
  imports: [PageHeaderComponent, RegionPickerComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'regions.find.title' | t" [subtitle]="'regions.find.subtitle' | t" icon="pi pi-search" />
    <section class="fs-card card">
      <fs-region-picker (regionSelected)="open($event)" testId="regions.find.picker" />
    </section>
  `,
  styles: `
    :host {
      display: grid;
      gap: 1.25rem;
      width: min(100%, 48rem);
      margin: 0 auto;
    }
    .card {
      padding: clamp(1rem, 0.8rem + 1vw, 1.5rem);
    }
  `,
})
export class FindRegionPageComponent {
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  protected open(region: RegionRef): void {
    void this.router.navigate(this.session.isAuthenticated() ? ['/regions', region.id] : ['/r', region.slug]);
  }
}
