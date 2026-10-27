import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
} from '@angular/core';
import { Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { PasskeyNudgeService } from '../../../core/auth/passkey-nudge.service';
import { TPipe } from '../../../core/i18n/t.pipe';

/**
 * "Log in faster next time" card after a Google / magic-link login (once per device). Rendered by the user menu,
 * i.e. only while logged in with the full chrome. Test ids `auth.passkeyNudge*`.
 */
@Component({
  selector: 'fs-passkey-nudge',
  imports: [Button, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (nudge.visible()) {
      <aside
        class="nudge"
        role="status"
        aria-labelledby="nudge-title"
        data-testid="auth.passkeyNudge"
      >
        <span class="nudge__icon" aria-hidden="true"><i class="pi pi-key"></i></span>
        <div class="nudge__body">
          <h2 id="nudge-title">{{ 'auth.passkeyNudge.title' | t }}</h2>
          <p>{{ 'auth.passkeyNudge.text' | t }}</p>
          <div class="nudge__actions">
            <p-button
              [label]="'auth.passkeyNudge.accept' | t"
              icon="pi pi-key"
              size="small"
              (onClick)="accept()"
              data-testid="auth.passkeyNudge.accept"
            />
            <p-button
              [label]="'auth.passkeyNudge.dismiss' | t"
              severity="secondary"
              [text]="true"
              size="small"
              (onClick)="nudge.dismiss()"
              data-testid="auth.passkeyNudge.dismiss"
            />
          </div>
        </div>
        <button
          type="button"
          class="nudge__close"
          [attr.aria-label]="'common.close' | t"
          (click)="nudge.dismiss()"
        >
          <i class="pi pi-times" aria-hidden="true"></i>
        </button>
      </aside>
    }
  `,
  styles: `
    :host {
      --p-button-text-danger-color: var(--fs-danger);
      --p-button-outlined-danger-color: var(--fs-danger);
      --p-button-text-secondary-color: var(--fs-text-muted);
      --p-button-outlined-secondary-color: var(--fs-text);
    }
    .nudge {
      position: fixed;
      z-index: 45;
      right: max(1rem, env(safe-area-inset-right));
      bottom: calc(1rem + env(safe-area-inset-bottom));
      display: flex;
      align-items: flex-start;
      gap: 0.85rem;
      width: min(24rem, calc(100vw - 2rem));
      padding: 1rem 1rem 1rem 1.1rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-lg);
      text-align: left;
    }
    @media (max-width: 47.99rem) {
      .nudge {
        right: 1rem;
        bottom: calc(var(--fs-tabbar-h) + 0.75rem + env(safe-area-inset-bottom));
      }
    }
    .nudge__icon {
      display: grid;
      width: 2.5rem;
      height: 2.5rem;
      flex: 0 0 auto;
      place-items: center;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .nudge__body {
      display: grid;
      flex: 1 1 auto;
      min-width: 0;
      gap: 0.25rem;
    }
    .nudge__body h2 {
      margin: 0;
      font-size: 1rem;
    }
    .nudge__body p {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.88rem;
      line-height: 1.45;
    }
    .nudge__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
      margin-top: 0.5rem;
    }
    .nudge__close {
      display: grid;
      width: 2rem;
      height: 2rem;
      flex: 0 0 auto;
      place-items: center;
      margin: -0.35rem -0.35rem 0 0;
      border: 0;
      border-radius: 50%;
      background: transparent;
      color: var(--fs-text-muted);
      cursor: pointer;
    }
    .nudge__close:hover {
      background: var(--fs-surface-muted);
      color: var(--fs-text);
    }
  `,
})
export class PasskeyNudgeComponent {
  protected readonly nudge = inject(PasskeyNudgeService);
  private readonly router = inject(Router);

  constructor() {
    // The header uses backdrop-filter, which would make it the containing block of this fixed card: move the host
    // element to <body> so the card sits at the viewport's bottom edge.
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const document = inject(DOCUMENT);
    afterNextRender(() => document.body.appendChild(host));
    inject(DestroyRef).onDestroy(() => host.remove());
  }

  protected accept(): void {
    this.nudge.accept();
    void this.router.navigate(['/account/settings'], { queryParams: { addPasskey: 1 } });
  }
}
