import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';

import { PickupOption, PickupsService, SignupResult } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RegionContextService } from '../../../core/region/region-context.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PickupSignupDialogComponent, SignupTarget } from '../components/signup-dialog.component';

/** /pickups (PU7): "where can I help" – pickups with free slots in my stores, sorted by time, sign up inline. */
@Component({
  selector: 'fs-pickup-options-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, PickupSignupDialogComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- secondary page actions sit in the page header (design review M7 R2: consistent action placement) -->
    <fs-page-header [title]="'pickups.options.title' | t" [subtitle]="'pickups.options.subtitle' | t" icon="pi pi-calendar-plus">
      <p-button [label]="'pickups.mine.title' | t" icon="pi pi-calendar" severity="secondary" [outlined]="true" routerLink="/pickups/mine" data-testid="pickups.options.mine" />
    </fs-page-header>
    <fs-data-state
      [state]="options"
      skeleton="list"
      [emptyTitle]="'pickups.options.empty' | t"
      [emptyText]="'pickups.options.emptyText' | t"
      testId="pickups.options.state"
      data-testid="pickups.options.state"
    >
      <ng-template [fsData]="options">
        <ul class="cards">
          @for (option of sorted(); track option.store.id + option.pickupAt) {
            <li class="fs-card option" data-testid="pickups.options.option" [attr.data-store]="option.store.name" [attr.data-pickup-at]="option.pickupAt">
              <div class="option__main">
                <a class="option__store" [routerLink]="['/stores', option.store.id, 'pickups']">{{ option.store.name }}</a>
                <span class="option__when">{{ option.pickupAt | fsDate: 'dateTimeLong' }}</span>
                @if (option.description) {
                  <span class="muted">{{ option.description }}</span>
                }
                <span class="muted" data-testid="pickups.options.free">
                  {{ 'pickups.options.free' | t: { free: option.slotCount - option.occupiedSlots.length, total: option.slotCount } }}
                </span>
              </div>
              <p-button
                [label]="'pickups.options.signUp' | t"
                icon="pi pi-plus"
                size="small"
                (onClick)="open(option)"
                data-testid="pickups.options.signUp"
              />
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
    <fs-pickup-signup-dialog [(visible)]="signupOpen" [target]="target()" (signedUp)="done($event)" />
  `,
  styles: `
    :host { display: block; width: min(100%, 56rem); margin: 0 auto; }
    .cards { display: grid; gap: 0.6rem; margin: 0; padding: 0; list-style: none; }
    /* the action stays beside the text on phones too (one row per pickup instead of three, M7 R2 list density) */
    .option {
      display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 0.75rem; padding: 0.85rem 1rem;
    }
    .option__main { display: grid; gap: 0.15rem; min-width: 0; }
    .option__store { font-weight: 700; color: inherit; }
    .option__when { font-variant-numeric: tabular-nums; }
    .muted { color: var(--fs-text-muted); font-size: 0.88rem; }
  `,
})
export class PickupOptionsPageComponent {
  private readonly api = inject(PickupsService);
  private readonly region = inject(RegionContextService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);

  protected readonly options = loadResource<PickupOption[]>({
    loader: () => this.api.listPickupOptions(),
    isEmpty: (list) => list.length === 0,
  });

  /** Full pickups are never offered (PU7). */
  protected readonly sorted = computed(() =>
    [...(this.options.data() ?? [])]
      .filter((option) => option.occupiedSlots.length < option.slotCount)
      .sort((a, b) => a.pickupAt.localeCompare(b.pickupAt)),
  );

  protected readonly signupOpen = signal(false);
  protected readonly target = signal<SignupTarget | null>(null);

  protected open(option: PickupOption): void {
    this.target.set({
      storeId: option.store.id,
      storeName: option.store.name,
      pickupAt: option.pickupAt,
      timeZone: this.region.timeZone(),
    });
    this.signupOpen.set(true);
  }

  protected done(result: SignupResult): void {
    this.messages.add({
      severity: 'success',
      summary: this.i18n.t(result.confirmed ? 'pickups.signup.doneConfirmed' : 'pickups.signup.doneUnconfirmed'),
    });
    this.options.reload();
  }
}
