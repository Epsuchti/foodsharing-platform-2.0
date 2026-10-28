import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

import { safeReturnUrl } from '../../../core/auth/return-url';
import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ErrorPageAction, ErrorPageLayoutComponent } from './error-page-layout.component';

/**
 * /forbidden. Signed in: "no access" + who to ask. Anonymous (a protected link opened while logged out):
 * "log in to see this page" with a login button that comes back (`?returnUrl=` is passed on).
 */
@Component({
  selector: 'fs-forbidden-page',
  imports: [ErrorPageLayoutComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (session.isAuthenticated()) {
      <fs-error-page-layout
        code="403"
        icon="pi pi-lock"
        [title]="'errorPages.forbidden.title' | t"
        [text]="'errorPages.forbidden.askWho.' + (session.role() ?? 'FOODSHARER') | t"
        testId="errorPages.forbidden"
        data-testid="errorPages.forbidden"
      />
    } @else {
      <fs-error-page-layout
        code="403"
        icon="pi pi-sign-in"
        [title]="'errorPages.forbidden.loginTitle' | t"
        [text]="'errorPages.forbidden.loginText' | t"
        [primary]="loginAction()"
        testId="errorPages.forbidden"
        data-testid="errorPages.forbidden"
      />
    }
  `,
})
export class ForbiddenPageComponent {
  protected readonly session = inject(SessionService);
  private readonly returnUrl = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(map((params) => safeReturnUrl(params.get('returnUrl')))),
  );

  protected readonly loginAction = computed<ErrorPageAction>(() => {
    const returnUrl = this.returnUrl();
    return {
      labelKey: 'errorPages.forbidden.login',
      icon: 'pi pi-sign-in',
      link: '/login',
      queryParams: returnUrl ? { returnUrl } : undefined,
    };
  });
}
