import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ErrorPageLayoutComponent } from './error-page-layout.component';

@Component({
  selector: 'fs-forbidden-page',
  imports: [ErrorPageLayoutComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-error-page-layout
      code="403"
      icon="pi pi-lock"
      [title]="'errorPages.forbidden.title' | t"
      [text]="'errorPages.forbidden.text' | t"
      data-testid="errorPages.forbidden"
    >
      <a
        class="fs-btn"
        [routerLink]="session.isAuthenticated() ? '/dashboard' : '/'"
        data-testid="errorPages.forbidden.home"
      >
        <i class="pi pi-home" aria-hidden="true"></i>
        <span>{{ 'errorPages.home' | t }}</span>
      </a>
    </fs-error-page-layout>
  `,
})
export class ForbiddenPageComponent {
  protected readonly session = inject(SessionService);
}
