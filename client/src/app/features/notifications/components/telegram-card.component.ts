import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { Subscription, interval } from 'rxjs';

import { TelegramLinkToken, TelegramService } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';

/** While a link token is open the page re-reads the settings every 3 s until `telegramLinked` (N5). */
export const TELEGRAM_POLL_MS = 3_000;

/**
 * Telegram linking (N5, A28): "Connect" creates a 15-minute token and shows the bot deep link (`/start <token>`),
 * then waits until the bot reports the chat (the parent reloads the settings via `changed`). Linked: send a test
 * message or disconnect.
 */
@Component({
  selector: 'fs-telegram-card',
  imports: [Button, DateTimePipe, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="ns-telegram-title" data-testid="notifications.telegram.card" [attr.data-linked]="linked()">
      <header class="card__head">
        <h2 id="ns-telegram-title">{{ 'notifications.telegram.title' | t }}</h2>
        <p>{{ 'notifications.telegram.text' | t }}</p>
      </header>
      @if (linked()) {
        <p class="status" data-testid="notifications.telegram.linked">
          <i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'notifications.telegram.linkedText' | t: { bot: botName() ?? 'Telegram' } }}
        </p>
        <div class="actions">
          <p-button
            [label]="'notifications.telegram.test' | t"
            icon="pi pi-send"
            [outlined]="true"
            [loading]="test.busy()"
            (onClick)="test.run()"
            data-testid="notifications.telegram.test"
          />
          <p-button
            [label]="'notifications.telegram.disconnect' | t"
            icon="pi pi-times"
            severity="danger"
            [text]="true"
            [loading]="unlink.busy()"
            (onClick)="unlink.run()"
            data-testid="notifications.telegram.disconnect"
          />
        </div>
      } @else if (token(); as t) {
        <div class="waiting" data-testid="notifications.telegram.waiting">
          <p>{{ 'notifications.telegram.step' | t }}</p>
          <a class="fs-btn" [href]="t.deepLink" target="_blank" rel="noopener" data-testid="notifications.telegram.deepLink">
            <i class="pi pi-telegram" aria-hidden="true"></i>{{ 'notifications.telegram.open' | t }}
          </a>
          <p class="muted">
            {{ 'notifications.telegram.manual' | t }} <code data-testid="notifications.telegram.token">/start {{ t.token }}</code>
          </p>
          <p class="muted" aria-live="polite">
            <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
            {{ 'notifications.telegram.waitingText' | t: { time: (t.expiresAt | fsDate: 'time') } }}
          </p>
        </div>
      } @else {
        <div class="actions">
          <p-button
            [label]="'notifications.telegram.connect' | t"
            icon="pi pi-telegram"
            [loading]="connect.busy()"
            (onClick)="connect.run()"
            data-testid="notifications.telegram.connect"
          />
        </div>
      }
    </section>
  `,
  styleUrl: '../notifications.scss',
  styles: `
    .waiting {
      display: grid;
      gap: 0.5rem;
      justify-items: start;
    }
    .waiting p {
      margin: 0;
    }
  `,
})
export class TelegramCardComponent {
  private readonly api = inject(TelegramService);

  readonly linked = input(false);
  readonly botName = input<string | undefined>(undefined);
  /** Ask the parent to reload the settings (link state). */
  readonly changed = output<void>();

  protected readonly token = signal<TelegramLinkToken | null>(null);
  private poll?: Subscription;

  constructor() {
    effect(() => {
      if (this.linked()) {
        this.token.set(null);
        this.stopPolling();
      }
    });
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  protected readonly connect = runMutation(() => this.api.createTelegramLinkToken(), {
    onSuccess: (token) => {
      this.token.set(token);
      this.stopPolling();
      this.poll = interval(TELEGRAM_POLL_MS).subscribe(() => {
        if (Date.parse(token.expiresAt) < Date.now()) {
          this.token.set(null);
          this.stopPolling();
          return;
        }
        this.changed.emit();
      });
    },
  });

  protected readonly test = runMutation(() => this.api.sendTelegramTest(), { successToastKey: 'notifications.telegram.testSent' });

  protected readonly unlink = runMutation(() => this.api.unlinkTelegram(), {
    successToastKey: 'notifications.telegram.disconnected',
    onSuccess: () => this.changed.emit(),
  });

  private stopPolling(): void {
    this.poll?.unsubscribe();
    this.poll = undefined;
  }
}
