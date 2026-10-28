import { ChangeDetectionStrategy, Component, computed, inject, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { Select } from '@openng/optimus-ui/select';

import { AutomationService, Pickup, PickupsService, PolicyDecisionPreview, UserSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimeFormatter } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { decisionTone } from '../automation.util';
import { StoreAutomationContext } from '../store-automation.context';

/** "Preview decision" (AU1): what the pickup policy would decide for a user and a pickup, without acting. */
@Component({
  selector: 'fs-preview-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, FormsModule, Select, TPipe, UserChipComponent, UserPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [header]="'automation.preview.title' | t"
      [(visible)]="visible"
      [modal]="true"
      [style]="{ width: 'min(36rem, 96vw)' }"
      [closeAriaLabel]="'common.close' | t"
      data-testid="automation.preview.dialog"
    >
      <div class="body">
        <div class="field">
          <span>{{ 'automation.preview.user' | t }}</span>
          @if (user(); as u) {
            <div class="row">
              <fs-user-chip [name]="u.displayName" [avatarUrl]="u.avatarUrl" [userId]="u.id" />
              <p-button icon="pi pi-times" [text]="true" size="small" [ariaLabel]="'automation.common.remove' | t" (onClick)="user.set(null)" />
            </div>
          } @else {
            <fs-user-picker [regionId]="ctx.regionId()" (picked)="user.set($event)" testId="automation.preview.user" />
          }
        </div>
        <div class="field">
          <label for="pv-pickup">{{ 'automation.preview.pickup' | t }}</label>
          <p-select
            inputId="pv-pickup"
            [options]="pickupOptions()"
            optionLabel="label"
            optionValue="value"
            [(ngModel)]="pickupAt"
            [placeholder]="'automation.preview.pickupPlaceholder' | t"
            [loading]="pickups.status() === 'loading'"
            appendTo="body"
            data-testid="automation.preview.pickup"
          />
        </div>
        <p-button
          [label]="'automation.preview.run' | t"
          icon="pi pi-question-circle"
          [disabled]="!user() || !pickupAt()"
          [loading]="preview.busy()"
          (onClick)="preview.run()"
          data-testid="automation.preview.run"
        />
        @if (result(); as r) {
          <div class="result" data-testid="automation.preview.result" [attr.data-decision]="r.decision">
            <span [class]="'badge badge--' + tone(r.decision)">{{ 'automation.decision.' + r.decision | t }}</span>
            <ul class="list">
              @for (reason of r.reasons; track $index) {
                <li class="row"><span class="badge">{{ 'automation.rule.' + reason.rule | t }}</span><span>{{ reason.text }}</span></li>
              }
            </ul>
            @if (r.userMessage) {
              <p class="preview">{{ r.userMessage }}</p>
            }
          </div>
        }
      </div>
    </p-dialog>
  `,
  styleUrl: '../automation.scss',
  styles: `
    .body { display: grid; gap: 0.9rem; }
    .result { display: grid; gap: 0.5rem; }
    p-select { width: 100%; }
  `,
})
export class PreviewDialogComponent {
  protected readonly ctx = inject(StoreAutomationContext);
  private readonly api = inject(AutomationService);
  private readonly pickupsApi = inject(PickupsService);
  private readonly date = inject(DateTimeFormatter);

  readonly visible = model(false);
  protected readonly user = signal<UserSummary | null>(null);
  protected readonly pickupAt = signal<string | null>(null);
  protected readonly result = signal<PolicyDecisionPreview | null>(null);

  protected readonly pickups = loadResource<Pickup[], string>({
    params: () => (this.visible() ? this.ctx.storeId() : undefined),
    loader: (storeId) => this.pickupsApi.listPickups({ storeId }),
    isEmpty: () => false,
  });

  protected readonly pickupOptions = computed(() =>
    (this.pickups.data() ?? [])
      .filter((p) => p.slotCount > 0)
      .map((p) => ({ value: p.pickupAt, label: this.date.format(p.pickupAt, 'dateTimeLong', this.ctx.timeZone()) })),
  );

  protected tone = decisionTone;

  protected readonly preview = runMutation(
    () =>
      this.api.previewPickupPolicy({
        storeId: this.ctx.storeId()!,
        policyPreviewRequest: { userId: this.user()!.id, pickupAt: this.pickupAt()! },
      }),
    { onSuccess: (result) => this.result.set(result) },
  );
}
