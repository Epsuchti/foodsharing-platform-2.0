import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputNumber } from '@openng/optimus-ui/inputnumber';

import { AuditKind, AutomationService } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { AutomationAuditLoaders } from '../automation.resource';
import { AuditLoader, AuditTableComponent } from '../components/audit-table.component';
import { StoreAutomationContext } from '../store-automation.context';

/** Contract maximum of ExtraSlotSetting.maxExtraSlots; the server still caps every pickup at maxSlotsPerPickup. */
export const MAX_EXTRA_SLOTS = 50;

/** Extra slots (AU6, decision 164): how many extra slots the first person of a pickup may add (0 = off), and the audit. */
@Component({
  selector: 'fs-extra-slot-section',
  imports: [AuditTableComponent, Button, FormsModule, InputNumber, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card" data-testid="automation.extraSlot.root">
      <h2>{{ 'automation.extraSlot.title' | t }}</h2>
      <div class="field">
        <label for="xs-max"><strong>{{ 'automation.extraSlot.allowed' | t }}</strong></label>
        <p-inputnumber
          inputId="xs-max"
          [ngModel]="draft()"
          (ngModelChange)="draft.set($event ?? 0)"
          [min]="0"
          [max]="max"
          [showButtons]="true"
          [disabled]="!ctx.mayEdit() || set.busy()"
          data-testid="automation.extraSlot.max"
        />
        <span class="muted">{{ 'automation.extraSlot.maxHint' | t }}</span>
      </div>
      @if (ctx.mayEdit()) {
        <div class="actions">
          <p-button
            [label]="'common.save' | t"
            icon="pi pi-save"
            [loading]="set.busy()"
            [disabled]="!dirty()"
            (onClick)="set.run(draft())"
            data-testid="automation.extraSlot.save"
          />
        </div>
      }
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
  protected readonly max = MAX_EXTRA_SLOTS;
  protected readonly draft = signal(0);

  protected readonly loader: AuditLoader = inject(AutomationAuditLoaders).store(() => this.ctx.storeId(), AuditKind.ExtraSlot);

  constructor() {
    effect(() => {
      const saved = this.ctx.data()?.maxExtraSlots ?? 0;
      untracked(() => this.draft.set(saved));
    });
  }

  protected dirty(): boolean {
    return this.draft() !== (this.ctx.data()?.maxExtraSlots ?? 0);
  }

  protected readonly set = runMutation(
    (maxExtraSlots: number) =>
      this.api.setMaxExtraSlots({ storeId: this.ctx.storeId()!, extraSlotSetting: { maxExtraSlots } }),
    { successToastKey: 'automation.common.saved', onSuccess: () => this.ctx.reload(), onError: () => this.ctx.reload() },
  );
}
