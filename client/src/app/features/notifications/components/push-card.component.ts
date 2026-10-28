import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { from, switchMap } from 'rxjs';

import { PushMode, PushService, VapidPublicKey } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { PushClient, PushPermission } from '../../../core/push/push-client.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/** What the push card shows for this device. */
export type DevicePushState = 'unsupported' | 'denied' | 'off' | 'on';

export function devicePushState(permission: PushPermission, subscribed: boolean): DevicePushState {
  if (permission === 'unsupported') return 'unsupported';
  if (permission === 'denied') return 'denied';
  return subscribed ? 'on' : 'off';
}

/**
 * Push on this device (N4): state unsupported / blocked / off / on; enable → permission + subscription with the
 * server's VAPID key → `POST /api/push/subscriptions`; send a test push; disable on this device. Locally the server
 * only logs pushes (mode LOG, shown as a hint).
 */
@Component({
  selector: 'fs-push-card',
  imports: [...DATA_STATE_IMPORTS, Button, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="ns-push-title" data-testid="notifications.push.card" [attr.data-state]="state()">
      <header class="card__head">
        <h2 id="ns-push-title">{{ 'notifications.push.title' | t }}</h2>
        <p>{{ 'notifications.push.text' | t }}</p>
      </header>
      <fs-data-state [state]="vapid" skeleton="form" [skeletonRows]="1" [compact]="true" data-testid="notifications.push.keyState">
        <ng-template [fsData]="vapid" let-key>
          <p class="status" data-testid="notifications.push.status">
            <i [class]="icon()" aria-hidden="true"></i>{{ 'notifications.push.state.' + state() | t }}
          </p>
          @if (key.mode === logMode) {
            <p class="muted" data-testid="notifications.push.logMode">{{ 'notifications.push.logMode' | t }}</p>
          }
          <div class="actions">
            @switch (state()) {
              @case ('off') {
                <p-button
                  [label]="'notifications.push.enable' | t"
                  icon="pi pi-bell"
                  [loading]="enable.busy()"
                  (onClick)="enable.run(key.publicKey)"
                  data-testid="notifications.push.enable"
                />
              }
              @case ('on') {
                <p-button
                  [label]="'notifications.push.test' | t"
                  icon="pi pi-send"
                  [outlined]="true"
                  [loading]="test.busy()"
                  (onClick)="test.run()"
                  data-testid="notifications.push.test"
                />
                <p-button
                  [label]="'notifications.push.disable' | t"
                  icon="pi pi-times"
                  severity="secondary"
                  [text]="true"
                  [loading]="disable.busy()"
                  (onClick)="disable.run()"
                  data-testid="notifications.push.disable"
                />
              }
            }
          </div>
          @if (deviceError()) {
            <p class="error" role="alert" data-testid="notifications.push.error">{{ 'notifications.push.error.' + deviceError() | t }}</p>
          }
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../notifications.scss',
})
export class PushCardComponent {
  private readonly api = inject(PushService);
  private readonly client = inject(PushClient);
  protected readonly logMode = PushMode.Log;

  /** The server's push subscriptions changed. */
  readonly changed = output<void>();

  private readonly permission = signal<PushPermission>(this.client.permission());
  private readonly subscribed = signal(false);
  protected readonly deviceError = signal<'PUSH_DENIED' | 'PUSH_UNSUPPORTED' | null>(null);
  protected readonly state = computed(() => devicePushState(this.permission(), this.subscribed()));
  protected readonly icon = computed(
    () =>
      ({
        unsupported: 'pi pi-ban',
        denied: 'pi pi-lock',
        off: 'pi pi-bell-slash',
        on: 'pi pi-check-circle',
      })[this.state()],
  );

  protected readonly vapid = loadResource<VapidPublicKey>({
    loader: () => this.api.getVapidPublicKey(),
    isEmpty: () => false,
  });

  constructor() {
    void this.client.current().then((subscription) => this.subscribed.set(!!subscription));
  }

  protected readonly enable = runMutation(
    (publicKey: string) =>
      from(this.subscribeDevice(publicKey)).pipe(
        switchMap((data) => this.api.subscribePush({ pushSubscriptionCreate: { ...data, userAgent: navigator.userAgent.slice(0, 250) } })),
      ),
    {
      successToastKey: 'notifications.push.enabled',
      onSuccess: () => {
        this.subscribed.set(true);
        this.changed.emit();
      },
    },
  );

  protected readonly test = runMutation(() => this.api.sendTestPush(), { successToastKey: 'notifications.push.testSent' });

  protected readonly disable = runMutation(
    () =>
      from(this.client.unsubscribe()).pipe(
        switchMap((endpoint) => this.api.unsubscribePush({ pushSubscriptionRemove: { endpoint: endpoint ?? '' } })),
      ),
    {
      successToastKey: 'notifications.push.disabled',
      onSuccess: () => {
        this.subscribed.set(false);
        this.changed.emit();
      },
    },
  );

  private async subscribeDevice(publicKey: string) {
    this.deviceError.set(null);
    try {
      return await this.client.subscribe(publicKey);
    } catch (error) {
      const code = error instanceof Error && error.message === 'PUSH_DENIED' ? 'PUSH_DENIED' : 'PUSH_UNSUPPORTED';
      this.deviceError.set(code);
      this.permission.set(this.client.permission());
      throw error;
    }
  }
}
