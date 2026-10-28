import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { from, switchMap } from 'rxjs';

import { PushService } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { PushClient } from '../../../core/push/push-client.service';
import { readStorage, writeStorage } from '../../../core/storage';

/** `localStorage['fs.pushPrompt']`: visits counted per browser session, or the final answer on this device. */
export const PUSH_PROMPT_KEY = 'fs.pushPrompt';
/** `sessionStorage` marker: this browser session was counted already. */
export const PUSH_PROMPT_SESSION_KEY = 'fs.pushPrompt.counted';

export interface PushPromptState {
  visits: number;
  done?: 'dismissed' | 'enabled';
}

export function readPromptState(raw: string | null): PushPromptState {
  try {
    const value = raw ? (JSON.parse(raw) as Partial<PushPromptState>) : {};
    return { visits: Number(value.visits) || 0, done: value.done };
  } catch {
    return { visits: 0 };
  }
}

/** The card shows from the second session on, once per device, while push is possible but not decided. */
export function shouldPrompt(state: PushPromptState, permission: string, subscribed: boolean): boolean {
  return !state.done && state.visits >= 2 && permission === 'default' && !subscribed;
}

/**
 * Dashboard card "Get notified on this device" (N4): appears once per device from the second session on, "Not now"
 * or "Enable" (permission + subscription) end it for this device.
 */
@Component({
  selector: 'fs-push-opt-in-card',
  imports: [Button, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <section class="fs-card card" aria-labelledby="push-opt-in-title" data-testid="notifications.optIn.card">
        <span class="icon" aria-hidden="true"><i class="pi pi-bell"></i></span>
        <div class="body">
          <h2 id="push-opt-in-title">{{ 'notifications.optIn.title' | t }}</h2>
          <p>{{ 'notifications.optIn.text' | t }}</p>
          <div class="actions">
            <p-button [label]="'notifications.optIn.enable' | t" icon="pi pi-check" size="small" [loading]="enable.busy()" (onClick)="enable.run()" data-testid="notifications.optIn.enable" />
            <p-button [label]="'notifications.optIn.later' | t" size="small" severity="secondary" [text]="true" (onClick)="finish('dismissed')" data-testid="notifications.optIn.later" />
          </div>
        </div>
      </section>
    }
  `,
  styles: `
    .card {
      display: flex;
      gap: 0.85rem;
      align-items: flex-start;
      padding: 1.25rem;
    }
    .icon {
      display: inline-grid;
      place-items: center;
      flex: none;
      width: 2.4rem;
      height: 2.4rem;
      border-radius: 0.75rem;
      background: var(--fs-primary-soft);
      color: var(--fs-primary);
    }
    h2 {
      margin: 0;
      font-size: 1.05rem;
    }
    p {
      margin: 0.2rem 0 0.75rem;
      color: var(--fs-text-muted);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class PushOptInCardComponent {
  private readonly api = inject(PushService);
  private readonly client = inject(PushClient);
  protected readonly visible = signal(false);

  constructor() {
    const state = readPromptState(readStorage(PUSH_PROMPT_KEY));
    if (!state.done && this.client.permission() !== 'unsupported') {
      let counted: boolean;
      try {
        counted = sessionStorage.getItem(PUSH_PROMPT_SESSION_KEY) === '1';
        if (!counted) sessionStorage.setItem(PUSH_PROMPT_SESSION_KEY, '1');
      } catch {
        counted = true;
      }
      if (!counted) {
        state.visits += 1;
        writeStorage(PUSH_PROMPT_KEY, JSON.stringify(state));
      }
    }
    const permission = this.client.permission();
    void this.client.current().then((subscription) => this.visible.set(shouldPrompt(state, permission, !!subscription)));
  }

  protected readonly enable = runMutation(
    () =>
      this.api.getVapidPublicKey().pipe(
        switchMap((key) => from(this.client.subscribe(key.publicKey))),
        switchMap((data) => this.api.subscribePush({ pushSubscriptionCreate: data })),
      ),
    { successToastKey: 'notifications.push.enabled', onSuccess: () => this.finish('enabled') },
  );

  protected finish(done: 'dismissed' | 'enabled'): void {
    const state = readPromptState(readStorage(PUSH_PROMPT_KEY));
    writeStorage(PUSH_PROMPT_KEY, JSON.stringify({ ...state, done }));
    this.visible.set(false);
  }
}
