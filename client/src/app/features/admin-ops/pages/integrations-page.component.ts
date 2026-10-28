import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';

import { AdminIntegrationsService, Integration, IntegrationStatus } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/** Integrations whose test needs a target (chat id / address); the others go to the admin themselves. */
export const TEST_TARGET: Partial<Record<Integration, string>> = { TELEGRAM: 'chatId', SMTP: 'email' };

/**
 * `/admin/integrations` (AU12, ORGA): one card per external integration with mode (LOG/LIVE), circuit breaker
 * state, timestamps and last error; "Probe" and "Send test" (with a target where it needs one).
 */
@Component({
  selector: 'fs-integrations-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, InputText, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminOps.integrations.title' | t" [subtitle]="'adminOps.integrations.subtitle' | t" icon="pi pi-sitemap" />
    <fs-data-state [state]="integrations" skeleton="card" [skeletonRows]="4" data-testid="adminOps.integrations.state">
      <ng-template [fsData]="integrations" let-list>
        <ul class="grid">
          @for (item of list; track item.integration) {
            <li class="fs-card card" data-testid="adminOps.integrations.card" [attr.data-integration]="item.integration" [attr.data-state]="item.state" [attr.data-mode]="item.mode">
              <header class="head">
                <h2>{{ 'adminOps.integration.' + item.integration | t }}</h2>
                <span class="chip" data-testid="adminOps.integrations.mode">{{ 'adminOps.mode.' + item.mode | t }}</span>
                <span class="badge" [class]="'badge badge--' + item.state" data-testid="adminOps.integrations.breaker">{{ 'adminOps.breaker.' + item.state | t }}</span>
              </header>
              <dl class="facts">
                <dt>{{ 'adminOps.integrations.lastSuccess' | t }}</dt>
                <dd>{{ item.lastSuccessAt ? (item.lastSuccessAt | fsDate: 'dateTime') : '–' }}</dd>
                <dt>{{ 'adminOps.integrations.lastFailure' | t }}</dt>
                <dd>{{ item.lastFailureAt ? (item.lastFailureAt | fsDate: 'dateTime') : '–' }}</dd>
                @if (item.downSince) {
                  <dt>{{ 'adminOps.integrations.downSince' | t }}</dt>
                  <dd data-testid="adminOps.integrations.downSince">{{ item.downSince | fsDate: 'dateTime' }}</dd>
                }
                @if (item.nextProbeAt) {
                  <dt>{{ 'adminOps.integrations.nextProbe' | t }}</dt>
                  <dd>{{ item.nextProbeAt | fsDate: 'dateTime' }}</dd>
                }
                <dt>{{ 'adminOps.integrations.failures' | t }}</dt>
                <dd>{{ item.failureCount ?? 0 }}</dd>
              </dl>
              @if (item.lastError) {
                <details class="error">
                  <summary>{{ 'adminOps.integrations.lastError' | t }}</summary>
                  <pre data-testid="adminOps.integrations.lastError">{{ item.lastError }}</pre>
                </details>
              }
              <div class="actions">
                <p-button
                  [label]="'adminOps.integrations.probe' | t"
                  icon="pi pi-refresh"
                  size="small"
                  [outlined]="true"
                  [loading]="probe.busy() && busyFor() === item.integration"
                  (onClick)="runProbe(item)"
                  data-testid="adminOps.integrations.probe"
                />
                @if (targetKind(item.integration); as kind) {
                  <label class="fs-visually-hidden" [for]="'target-' + item.integration">{{ 'adminOps.integrations.target.' + kind | t }}</label>
                  <input
                    pInputText
                    class="target"
                    [id]="'target-' + item.integration"
                    [placeholder]="'adminOps.integrations.target.' + kind | t"
                    [(ngModel)]="targets[item.integration]"
                    data-testid="adminOps.integrations.target"
                  />
                }
                <p-button
                  [label]="'adminOps.integrations.test' | t"
                  icon="pi pi-send"
                  size="small"
                  [loading]="test.busy() && busyFor() === item.integration"
                  (onClick)="runTest(item)"
                  data-testid="adminOps.integrations.test"
                />
              </div>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../admin-ops.scss',
})
export class IntegrationsPageComponent {
  private readonly api = inject(AdminIntegrationsService);
  protected readonly busyFor = signal<Integration | null>(null);
  protected targets: Partial<Record<Integration, string>> = {};

  protected readonly integrations = loadResource<IntegrationStatus[]>({
    loader: () => this.api.listIntegrations(),
  });

  protected targetKind(integration: Integration): string | undefined {
    return TEST_TARGET[integration];
  }

  protected readonly probe = runMutation((integration: Integration) => this.api.probeIntegration({ integration }), {
    successToastKey: 'adminOps.integrations.probed',
    onSuccess: () => this.integrations.reload(),
    onError: () => this.integrations.reload(),
  });

  protected readonly test = runMutation(
    (integration: Integration, target: string | undefined) =>
      this.api.testIntegration({ integration, integrationTest: { target: target || undefined } }),
    { successToastKey: 'adminOps.integrations.tested', onSuccess: () => this.integrations.reload() },
  );

  protected runProbe(item: IntegrationStatus): void {
    this.busyFor.set(item.integration);
    void this.probe.run(item.integration);
  }

  protected runTest(item: IntegrationStatus): void {
    this.busyFor.set(item.integration);
    void this.test.run(item.integration, this.targets[item.integration]?.trim());
  }
}
