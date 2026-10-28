import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { catchError, of, throwError } from 'rxjs';

import { IntroPickupSubscription, IntroPickupsService, Language, SubscriptionChannel } from '../../../api';
import { memberRegions } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/**
 * EA9: "Tell me when a new intro pickup can be booked" — region, channel (in-app or e-mail, optional other address)
 * and language; unsubscribe. Not shown to verified users (they no longer need EinAbs).
 */
@Component({
  selector: 'fs-subscription-card',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, InputText, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" [class.highlight]="!!preselect()" id="notify" data-testid="introPickups.subscription">
      <h2 class="card__title"><i class="pi pi-bell" aria-hidden="true"></i>{{ 'introPickups.subscription.title' | t }}</h2>
      <fs-data-state [state]="subscription" skeleton="form" [compact]="true" data-testid="introPickups.subscription.state">
        <ng-template [fsData]="subscription" let-sub>
          @if (sub?.active) {
            <p class="active" data-testid="introPickups.subscription.active">
              <i class="pi pi-check-circle" aria-hidden="true"></i>
              {{ 'introPickups.subscription.activeText' | t: { region: sub.region?.name ?? '', channel: ('introPickups.subscription.channel.' + sub.channel | t) } }}
              @if (sub.deliveryEmail) {
                <span class="muted">({{ sub.deliveryEmail }})</span>
              }
            </p>
            <div class="actions">
              <p-button
                [label]="'introPickups.subscription.unsubscribe' | t"
                icon="pi pi-bell-slash"
                severity="secondary"
                [outlined]="true"
                [loading]="unsubscribe.busy()"
                (onClick)="unsubscribe.run()"
                data-testid="introPickups.subscription.unsubscribe"
              />
            </div>
          } @else {
            <p class="muted">{{ 'introPickups.subscription.text' | t }}</p>
            <div class="form__row">
              <div class="field">
                <label for="sub-region">{{ 'introPickups.subscription.region' | t }}</label>
                <p-select inputId="sub-region" [options]="regions()" optionLabel="name" optionValue="id" [ngModel]="regionId()" (ngModelChange)="regionId.set($event)" appendTo="body" [style]="{ width: '100%' }" data-testid="introPickups.subscription.region" />
              </div>
              <div class="field">
                <label for="sub-channel">{{ 'introPickups.subscription.channelLabel' | t }}</label>
                <p-select inputId="sub-channel" [options]="channels()" optionLabel="label" optionValue="value" [ngModel]="channel()" (ngModelChange)="channel.set($event)" appendTo="body" [style]="{ width: '100%' }" data-testid="introPickups.subscription.channel" />
              </div>
              @if (channel() === 'EMAIL') {
                <div class="field">
                  <label for="sub-email">{{ 'introPickups.subscription.email' | t }}</label>
                  <input pInputText id="sub-email" type="email" [ngModel]="email()" (ngModelChange)="email.set($event)" [placeholder]="ownEmail()" data-testid="introPickups.subscription.email" />
                </div>
              }
            </div>
            <div class="actions">
              <p-button [label]="'introPickups.subscription.subscribe' | t" icon="pi pi-bell" [disabled]="!regionId()" [loading]="subscribe.busy()" (onClick)="subscribe.run()" data-testid="introPickups.subscription.subscribe" />
            </div>
          }
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    :host {
      display: block;
      width: auto;
      margin: 0;
    }
    .highlight {
      border-color: var(--fs-primary);
      box-shadow: 0 0 0 3px var(--fs-primary-soft);
    }
    .active {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
      margin: 0;
    }
    .active .pi {
      color: var(--fs-primary-strong);
    }
  `,
})
export class SubscriptionCardComponent {
  private readonly api = inject(IntroPickupsService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);

  /** Region id from `?subscribe=` (public "notify me" button). */
  readonly preselect = input<string | undefined>(undefined);

  protected readonly regions = computed(() => memberRegions(this.session.session()));
  protected readonly ownEmail = computed(() => this.session.user()?.email ?? '');
  protected readonly regionId = signal<string | null>(null);
  protected readonly channel = signal<SubscriptionChannel>(SubscriptionChannel.InApp);
  protected readonly email = signal('');
  protected readonly channels = computed(() =>
    Object.values(SubscriptionChannel).map((value) => ({
      value,
      label: this.i18n.t('introPickups.subscription.channel.' + value),
    })),
  );

  protected readonly subscription = loadResource<IntroPickupSubscription | null>({
    loader: () =>
      this.api
        .getMySubscription()
        .pipe(catchError((error: { status?: number }) => (error?.status === 404 ? of(null) : throwError(() => error)))),
    isEmpty: () => false,
  });

  constructor() {
    effect(() => {
      const wanted = this.preselect();
      const regions = this.regions();
      const current = this.subscription.data();
      if (this.regionId()) {
        return;
      }
      const id =
        (wanted && regions.some((region) => region.id === wanted) && wanted) ||
        current?.regionId ||
        regions.find((region) => region.homeRegion)?.id ||
        regions[0]?.id;
      if (id) {
        this.regionId.set(id);
      }
    });
  }

  protected readonly subscribe = runMutation(
    () =>
      this.api.subscribe({
        subscriptionUpdate: {
          regionId: this.regionId()!,
          channel: this.channel(),
          deliveryEmail: this.channel() === 'EMAIL' && this.email().trim() ? this.email().trim() : undefined,
          language: (this.session.user()?.language ?? this.i18n.language()) as Language,
        },
      }),
    { successToastKey: 'introPickups.subscription.subscribed', onSuccess: () => this.subscription.reload() },
  );

  protected readonly unsubscribe = runMutation(() => this.api.unsubscribeMine(), {
    successToastKey: 'introPickups.subscription.unsubscribed',
    onSuccess: () => this.subscription.reload(),
  });
}
