import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { TPipe } from '../../../core/i18n/t.pipe';

/**
 * A freshly generated client secret (create or rotate, A56: shown once, stored only as a hash): read-only value,
 * copy button and the warning that it cannot be shown again.
 */
@Component({
  selector: 'fs-oauth-client-secret',
  imports: [Button, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="secret" data-testid="oauth.clients.secret">
      <p class="warn" role="note">
        <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
        <span>{{ 'oauth.clients.secret.once' | t }}</span>
      </p>
      <dl class="pairs">
        <dt>{{ 'oauth.clients.clientId' | t }}</dt>
        <dd><code data-testid="oauth.clients.secret.clientId">{{ clientId() }}</code></dd>
        <dt>{{ 'oauth.clients.secret.label' | t }}</dt>
        <dd class="value">
          <code data-testid="oauth.clients.secret.value" data-screenshot-mask>{{ secret() }}</code>
          <p-button
            [label]="(copied() ? 'oauth.clients.secret.copied' : 'oauth.clients.secret.copy') | t"
            [icon]="copied() ? 'pi pi-check' : 'pi pi-copy'"
            severity="secondary"
            [outlined]="true"
            size="small"
            (onClick)="copy()"
            data-testid="oauth.clients.secret.copy"
          />
        </dd>
      </dl>
    </div>
  `,
  styles: `
    .secret {
      display: grid;
      gap: 1rem;
    }
    .warn {
      display: flex;
      gap: 0.6rem;
      margin: 0;
      padding: 0.75rem 1rem;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
      font-size: 0.875rem;
    }
    .pairs {
      display: grid;
      gap: 0.35rem;
      margin: 0;
    }
    dt {
      font-size: 0.8125rem;
      font-weight: 650;
      color: var(--fs-text-muted);
    }
    dd {
      margin: 0 0 0.5rem;
    }
    .value {
      display: grid;
      gap: 0.5rem;
      justify-items: start;
    }
    code {
      display: block;
      max-width: 100%;
      padding: 0.5rem 0.65rem;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-surface-muted);
      font-size: 0.875rem;
      overflow-wrap: anywhere;
      user-select: all;
    }
  `,
})
export class ClientSecretComponent {
  private readonly document = inject(DOCUMENT);

  readonly clientId = input.required<string>();
  readonly secret = input.required<string>();

  protected readonly copied = signal(false);

  protected copy(): void {
    const clipboard = this.document.defaultView?.navigator?.clipboard;
    if (clipboard?.writeText) {
      clipboard.writeText(this.secret()).then(
        () => this.copied.set(true),
        () => undefined,
      );
    }
  }
}
