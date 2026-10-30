import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';

import { SessionExpiredService } from '../../core/auth/session-expired.service';
import { TPipe } from '../../core/i18n/t.pipe';

/** Shown when the server no longer knows the session (401 / refresh); leads to /login?returnUrl=… */
@Component({
  selector: 'fs-session-expired-dialog',
  imports: [Button, Dialog, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="expired.visible()"
      (visibleChange)="!$event && expired.close()"
      [modal]="true"
      [closable]="true"
      [draggable]="false"
      [resizable]="false"
      [header]="'shared.sessionExpired.title' | t"
      [style]="{ width: 'min(28rem, calc(100vw - 2rem))' }"
      data-testid="shell.sessionExpired.dialog"
    >
      <div class="se">
        <div class="se__icon" aria-hidden="true"><i class="pi pi-clock"></i></div>
        <p>{{ 'shared.sessionExpired.text' | t }}</p>
      </div>
      <ng-template #footer>
        <p-button
          [label]="'shared.sessionExpired.later' | t"
          severity="secondary"
          [text]="true"
          (onClick)="expired.close()"
          data-testid="shell.sessionExpired.later"
        />
        <p-button
          [label]="'shared.sessionExpired.login' | t"
          icon="pi pi-sign-in"
          (onClick)="expired.goToLogin()"
          data-testid="shell.sessionExpired.login"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .se {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
    }
    .se p {
      margin: 0.25rem 0 0;
      color: var(--fs-text-muted);
    }
    .se__icon {
      display: grid;
      flex: 0 0 auto;
      width: 2.75rem;
      height: 2.75rem;
      place-items: center;
      border-radius: 50%;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.2rem;
    }
  `,
})
export class SessionExpiredDialogComponent {
  protected readonly expired = inject(SessionExpiredService);
}
