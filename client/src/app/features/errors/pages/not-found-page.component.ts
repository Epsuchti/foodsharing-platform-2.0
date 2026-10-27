import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ErrorPageLayoutComponent } from './error-page-layout.component';

@Component({
  selector: 'fs-not-found-page',
  imports: [Button, ErrorPageLayoutComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-error-page-layout
      code="404"
      icon="pi pi-map"
      [title]="'errorPages.notFound.title' | t"
      [text]="'errorPages.notFound.text' | t"
      data-testid="errorPages.notFound"
    >
      <a
        class="fs-btn"
        [routerLink]="session.isAuthenticated() ? '/dashboard' : '/'"
        data-testid="errorPages.notFound.home"
      >
        <i class="pi pi-home" aria-hidden="true"></i>
        <span>{{ 'errorPages.home' | t }}</span>
      </a>
      <p-button
        [label]="'errorPages.back' | t"
        icon="pi pi-arrow-left"
        severity="secondary"
        [outlined]="true"
        (onClick)="location.back()"
        data-testid="errorPages.notFound.back"
      />
    </fs-error-page-layout>
  `,
})
export class NotFoundPageComponent {
  protected readonly session = inject(SessionService);
  protected readonly location = inject(Location);
}
