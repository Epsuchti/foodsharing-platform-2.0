import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { tap } from 'rxjs';

import { UserProfile, UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/** Account settings block "Pickup reminders" (PU9): bell + mail 24 h before a pickup, on by default. */
@Component({
  selector: 'fs-pickup-reminder-card',
  imports: [...DATA_STATE_IMPORTS, FormsModule, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="pickup-reminder-title" data-testid="pickups.reminders.card">
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-bell"></i></span>
        <div>
          <h2 id="pickup-reminder-title">{{ 'pickups.reminders.title' | t }}</h2>
          <p>{{ 'pickups.reminders.text' | t }}</p>
        </div>
      </header>
      <fs-data-state [state]="profile" skeleton="form" [skeletonRows]="1" [compact]="true" testId="pickups.reminders.state" data-testid="pickups.reminders.state">
        <ng-template [fsData]="profile">
          <div class="row">
            <p-toggleswitch
              inputId="pickup-reminders"
              [ngModel]="enabled()"
              (ngModelChange)="toggle($event)"
              [disabled]="save.busy()"
              data-testid="pickups.reminders.toggle"
            />
            <label for="pickup-reminders">{{ 'pickups.reminders.label' | t }}</label>
          </div>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styles: `
    .card { display: grid; gap: 1rem; padding: 1.25rem; }
    .card__head { display: flex; gap: 0.85rem; align-items: flex-start; }
    .card__head h2 { margin: 0; font-size: 1.1rem; }
    .card__head p { margin: 0.2rem 0 0; color: var(--fs-text-muted); }
    .card__icon {
      display: inline-grid; place-items: center; flex: none; width: 2.4rem; height: 2.4rem; border-radius: 0.75rem;
      background: var(--fs-surface-muted);
    }
    .row { display: flex; gap: 0.75rem; align-items: center; }
  `,
})
export class PickupReminderCardComponent {
  private readonly api = inject(UsersService);

  protected readonly enabled = signal(true);

  protected readonly profile = loadResource<UserProfile>({
    loader: () => this.api.getCurrentUser().pipe(tap((user) => this.enabled.set(user.pickupRemindersEnabled ?? true))),
    isEmpty: () => false,
  });

  protected toggle(value: boolean): void {
    this.enabled.set(value);
    void this.save.run(value);
  }

  protected readonly save = runMutation(
    (value: boolean) => this.api.updateMySettings({ userSettingsUpdate: { pickupRemindersEnabled: value } }),
    {
      successToastKey: 'pickups.reminders.saved',
      onSuccess: (user) => this.enabled.set(user.pickupRemindersEnabled ?? true),
      // back to the stored value
      onError: () => this.profile.reload(),
    },
  );
}
