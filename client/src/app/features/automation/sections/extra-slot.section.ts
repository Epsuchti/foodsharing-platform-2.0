import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { AuditKind, AutomationService } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { AutomationAuditLoaders } from '../automation.resource';
import { AuditLoader, AuditTableComponent } from '../components/audit-table.component';
import { StoreAutomationContext } from '../store-automation.context';

/** Extra slot (AU6): store switch "first person of a pickup may add one extra slot" and the extra-slot audit. */
@Component({
  selector: 'fs-extra-slot-section',
  imports: [AuditTableComponent, FormsModule, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card" data-testid="automation.extraSlot.root">
      <h2>{{ 'automation.extraSlot.title' | t }}</h2>
      <div class="switch-row">
        <label class="switch-row__text" for="xs-allowed"><strong>{{ 'automation.extraSlot.allowed' | t }}</strong>
          <span class="muted">{{ 'automation.extraSlot.hint' | t }}</span></label>
        <p-toggleswitch
          inputId="xs-allowed"
          [ngModel]="ctx.data()?.extraSlotAllowed"
          (ngModelChange)="set.run($event)"
          [disabled]="!ctx.mayEdit() || set.busy()"
          data-testid="automation.extraSlot.allowed"
        />
      </div>
    </section>
    <section class="card fs-card">
      <h3>{{ 'automation.extraSlot.audit' | t }}</h3>
      <fs-automation-audit-table [loader]="loader" [showKinds]="false" testId="automation.extraSlot.audits" />
    </section>
  `,
  styleUrl: '../automation.scss',
})
export class ExtraSlotSection {
  protected readonly ctx = inject(StoreAutomationContext);
  private readonly api = inject(AutomationService);

  protected readonly loader: AuditLoader = inject(AutomationAuditLoaders).store(() => this.ctx.storeId(), AuditKind.ExtraSlot);

  protected readonly set = runMutation(
    (allowed: boolean) => this.api.setExtraSlotAllowed({ storeId: this.ctx.storeId()!, extraSlotSetting: { allowed } }),
    { successToastKey: 'automation.common.saved', onSuccess: () => this.ctx.reload(), onError: () => this.ctx.reload() },
  );
}
