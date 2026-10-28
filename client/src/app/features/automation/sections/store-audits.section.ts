import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';
import { AutomationAuditLoaders } from '../automation.resource';
import { AuditLoader, AuditTableComponent } from '../components/audit-table.component';
import { StoreAutomationContext } from '../store-automation.context';

/** All automation decisions of one store (AU8). */
@Component({
  selector: 'fs-store-audits-section',
  imports: [AuditTableComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <h2>{{ 'automation.audits.title' | t }}</h2>
      <fs-automation-audit-table [loader]="loader" testId="automation.audits" />
    </section>
  `,
  styleUrl: '../automation.scss',
})
export class StoreAuditsSection {
  private readonly ctx = inject(StoreAutomationContext);
  protected readonly loader: AuditLoader = inject(AutomationAuditLoaders).store(() => this.ctx.storeId());
}
