import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { TPipe } from '../../core/i18n/t.pipe';
import { OnlineStatusService } from '../../core/online/online-status.service';

/** Sticky banner while offline or while the server cannot be reached; data reloads automatically afterwards. */
@Component({
  selector: 'fs-offline-banner',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!online.online()) {
      <div class="offline" role="status" aria-live="polite" data-testid="shell.offlineBanner">
        <i class="pi pi-wifi" aria-hidden="true"></i>
        <span class="offline__text">
          <strong>{{
            (online.serverUnreachable() ? 'shared.offline.serverTitle' : 'shared.offline.title') | t
          }}</strong>
          <span class="offline__long">{{ 'shared.offline.text' | t }}</span>
          <span class="offline__short">{{ 'shared.offline.short' | t }}</span>
        </span>
        <button
          type="button"
          class="offline__retry"
          (click)="online.probe()"
          data-testid="shell.offlineBanner.retry"
        >
          <i class="pi pi-refresh" aria-hidden="true"></i>
          {{ 'shared.offline.retry' | t }}
        </button>
      </div>
    }
  `,
  styles: `
    .offline__short {
      display: none;
    }
    @media (max-width: 40rem) {
      .offline__long {
        display: none;
      }
      .offline__short {
        display: inline;
      }
    }
    .offline {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.6rem max(1rem, env(safe-area-inset-left));
      border-bottom: 1px solid var(--fs-warning-border);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
      font-size: 0.9rem;
    }
    .offline > .pi {
      font-size: 1.1rem;
    }
    .offline__text {
      display: flex;
      flex: 1;
      flex-wrap: wrap;
      gap: 0.1rem 0.5rem;
    }
    .offline__retry {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.35rem 0.75rem;
      border: 1px solid currentColor;
      border-radius: 999px;
      background: transparent;
      color: inherit;
      font: inherit;
      font-weight: 650;
      cursor: pointer;
    }
  `,
})
export class OfflineBannerComponent {
  protected readonly online = inject(OnlineStatusService);
}
