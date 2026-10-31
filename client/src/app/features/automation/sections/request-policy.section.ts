import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { AutomationRunResult, AutomationService, RequestPolicy } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { clone, emptyRequestPolicy } from '../automation.util';
import { StoreAutomationContext } from '../store-automation.context';

/** Team request automation (AU3): auto-accept applications, distance rule (km from home), dry run, run now. */
@Component({
  selector: 'fs-request-policy-section',
  imports: [Button, FormsModule, InputNumber, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (draft(); as p) {
      <section class="card fs-card" data-testid="automation.request.root" [attr.data-enabled]="p.enabled" [attr.data-unsaved]="unsaved()">
        <h2>{{ 'automation.request.title' | t }}</h2>
        <p class="muted">{{ 'automation.request.intro' | t }}</p>
        <div class="switch-row">
          <label class="switch-row__text" for="rp-enabled"><strong>{{ 'automation.request.enabled' | t }}</strong></label>
          <p-toggleswitch inputId="rp-enabled" [(ngModel)]="p.enabled" [disabled]="!ctx.mayEdit()" data-testid="automation.request.enabled" />
        </div>
        <div class="switch-row">
          <label class="switch-row__text" for="rp-dry">
            <strong>{{ 'automation.common.dryRun' | t }}</strong>
            <span class="muted">{{ 'automation.common.dryRunHint' | t }}</span>
          </label>
          <p-toggleswitch inputId="rp-dry" [(ngModel)]="p.dryRun" [disabled]="!ctx.mayEdit()" data-testid="automation.request.dryRun" />
        </div>
        @if (p.enabled && p.dryRun) {
          <p class="banner" data-testid="automation.request.dryRunBanner"><i class="pi pi-eye" aria-hidden="true"></i>{{ 'automation.common.dryRunBanner' | t }}</p>
        }
        <div class="switch-row">
          <label class="switch-row__text" for="rp-distance"><strong>{{ 'automation.request.distance' | t }}</strong>
            <span class="muted">{{ 'automation.request.distanceHint' | t }}</span></label>
          <p-toggleswitch inputId="rp-distance" [(ngModel)]="p.distanceRuleEnabled" [disabled]="!ctx.mayEdit()" data-testid="automation.request.distanceEnabled" />
        </div>
        <div class="field">
          <label for="rp-km">{{ 'automation.request.km' | t }}</label>
          <p-inputnumber inputId="rp-km" [(ngModel)]="p.maximumDistanceKm" [min]="0" [max]="200" [minFractionDigits]="0" [maxFractionDigits]="1" [showButtons]="true" suffix=" km" [disabled]="!ctx.mayEdit() || !p.distanceRuleEnabled" data-testid="automation.request.km" />
        </div>
      </section>
      @if (save.error(); as e) {
        <p class="error-text" role="alert" data-testid="automation.request.error">{{ 'errors.' + e.code | t }}</p>
      }
      @if (ctx.mayEdit()) {
        <div class="actions">
          <p-button [label]="'common.save' | t" icon="pi pi-save" [loading]="save.busy()" [disabled]="!dirty()" (onClick)="save.run()" data-testid="automation.request.save" />
          @if (ctx.data()?.requestPolicy) {
            <p-button [label]="'automation.common.runDry' | t" icon="pi pi-eye" [outlined]="true" [disabled]="unsaved()" [loading]="run.busy()" (onClick)="run.run(true)" data-testid="automation.request.runDry" />
            <p-button [label]="'automation.common.runNow' | t" icon="pi pi-play" severity="warn" [outlined]="true" [disabled]="p.dryRun || unsaved()" [loading]="run.busy()" (onClick)="run.run(false)" data-testid="automation.request.run" />
          }
        </div>
        @if (ctx.data()?.requestPolicy && unsaved()) {
          <p class="muted" data-testid="automation.request.saveFirst">{{ 'automation.request.saveFirst' | t }}</p>
        }
      }
      @if (lastRun(); as r) {
        <p class="banner banner--info" data-testid="automation.request.runResult">
          {{ 'automation.request.runResult' | t: { evaluated: r.evaluated, approved: r.confirmed, declined: r.declined, skipped: r.skipped } }}
        </p>
      }
    }
  `,
  styleUrl: '../automation.scss',
})
export class RequestPolicySection {
  protected readonly ctx = inject(StoreAutomationContext);
  private readonly api = inject(AutomationService);

  protected readonly draft = signal<RequestPolicy | null>(null);
  protected readonly lastRun = signal<AutomationRunResult | null>(null);

  constructor() {
    effect(() => {
      const policy = this.ctx.data()?.requestPolicy;
      untracked(() => this.draft.set(policy ? clone(policy) : emptyRequestPolicy()));
    });
  }

  protected dirty(): boolean {
    const draft = this.draft();
    const saved = this.ctx.data()?.requestPolicy;
    return !!draft && (!saved || JSON.stringify(draft) !== JSON.stringify(saved));
  }

  /** Dry run and run evaluate the saved policy, so they wait for unsaved changes and a running save (see adverts). */
  protected unsaved(): boolean {
    return this.save.busy() || this.dirty();
  }

  protected readonly save = runMutation(
    () => this.api.putRequestPolicy({ storeId: this.ctx.storeId()!, requestPolicy: this.draft()! }),
    { successToastKey: 'automation.common.saved', errorToast: false, onSuccess: () => this.ctx.reload() },
  );

  protected readonly run = runMutation(
    (dryRun: boolean) => this.api.runRequestPolicy({ storeId: this.ctx.storeId()!, dryRun }),
    { successToastKey: 'automation.common.ran', onSuccess: (result) => this.lastRun.set(result) },
  );
}
