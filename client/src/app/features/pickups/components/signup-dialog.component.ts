import { ChangeDetectionStrategy, Component, computed, inject, input, model, output } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';

import { PickupEligibility, PickupsService, SignupResult } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { EligibilityReasonsComponent } from '../../../shared/eligibility-reasons/eligibility-reasons.component';

export interface SignupTarget {
  storeId: string;
  storeName: string;
  pickupAt: string;
  timeZone: string;
}

/**
 * Sign up for a pickup (PU3/PU4/PU5): checks eligibility first; eligible → confirm; otherwise the translated reasons
 * and the region rule counters. Emits the result (`confirmed` = preconfirmed, e.g. managers).
 */
@Component({
  selector: 'fs-pickup-signup-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, Dialog, EligibilityReasonsComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="'pickups.signup.title' | t"
      [style]="{ width: 'min(32rem, 94vw)' }"
      data-testid="pickups.signup.dialog"
    >
      @if (target(); as t) {
        <p class="when" data-testid="pickups.signup.when">
          <strong>{{ t.storeName }}</strong><br />
          {{ t.pickupAt | fsDate: 'dateTimeLong' : t.timeZone }}
        </p>
        <fs-data-state [state]="eligibility" skeleton="inline" testId="pickups.signup.state" data-testid="pickups.signup.state">
          <ng-template [fsData]="eligibility" let-e>
            @if (e.eligible) {
              <p class="muted" data-testid="pickups.signup.eligible">{{ 'pickups.signup.text' | t }}</p>
            } @else {
              <p class="muted">{{ 'pickups.signup.notEligible' | t }}</p>
            }
            <fs-eligibility-reasons [eligibility]="e" />
          </ng-template>
        </fs-data-state>
      }
      <ng-template #footer>
        <p-button
          [label]="'common.cancel' | t"
          severity="secondary"
          [text]="true"
          (onClick)="visible.set(false)"
          data-testid="pickups.signup.cancel"
        />
        @if (eligible()) {
          <p-button
            [label]="'pickups.signup.confirm' | t"
            icon="pi pi-check"
            [loading]="signUp.busy()"
            (onClick)="signUp.run()"
            data-testid="pickups.signup.confirm"
          />
        }
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .when { margin: 0 0 0.75rem; }
    .muted { margin: 0 0 0.6rem; color: var(--fs-text-muted); }
  `,
})
export class PickupSignupDialogComponent {
  private readonly api = inject(PickupsService);

  readonly visible = model(false);
  readonly target = input<SignupTarget | null>(null);
  readonly signedUp = output<SignupResult>();

  protected readonly eligibility = loadResource<PickupEligibility, SignupTarget>({
    params: () => (this.visible() ? (this.target() ?? undefined) : undefined),
    loader: ({ storeId, pickupAt }) => this.api.checkEligibility({ storeId, pickupAt }),
  });

  protected readonly eligible = computed(() => !!this.eligibility.data()?.eligible);

  protected readonly signUp = runMutation(
    () => this.api.signUp({ storeId: this.target()!.storeId, pickupAt: this.target()!.pickupAt }),
    {
      onSuccess: (result) => {
        this.visible.set(false);
        this.signedUp.emit(result);
      },
    },
  );
}
