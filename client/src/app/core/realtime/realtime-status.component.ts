import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { TPipe } from '../i18n/t.pipe';
import { OnlineStatusService } from '../online/online-status.service';
import { RealtimeService } from './realtime.service';

/** "Reconnecting…" chip while the realtime connection is down for > 5 s and the browser is online (A32). */
@Component({
  selector: 'fs-realtime-status',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (realtime.reconnecting() && online.online()) {
      <div class="chip" role="status" aria-live="polite" data-testid="shell.realtimeReconnecting">
        <i class="pi pi-spin pi-sync" aria-hidden="true"></i>
        {{ 'common.realtime.reconnecting' | t }}
      </div>
    }
  `,
  styles: `
    :host {
      display: contents;
    }
    .chip {
      position: fixed;
      z-index: 1100;
      bottom: 5rem;
      left: 50%;
      transform: translateX(-50%);
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4rem 0.9rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      box-shadow: 0 2px 8px rgb(0 0 0 / 0.2);
      color: var(--fs-text);
      font-size: 0.88rem;
      font-weight: 600;
    }
  `,
})
export class RealtimeStatusComponent {
  protected readonly realtime = inject(RealtimeService);
  protected readonly online = inject(OnlineStatusService);
}
