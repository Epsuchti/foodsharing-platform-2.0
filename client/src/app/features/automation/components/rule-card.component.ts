import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';

/**
 * A rule card of the Regeln section (decision 167 S4, proposal §3.1): title and hint left, the switch (projected with
 * the `ruleSwitch` attribute) right, the member sentence while the rule is on, then the card's own content. While the
 * master switch is off, a rule that is on says it takes effect only once the rules are on (review fix A7).
 */
@Component({
  selector: 'fs-rule-card',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card rule" [attr.data-testid]="testId()" [attr.data-enabled]="enabled()">
      <div class="switch-row">
        <label class="switch-row__text" [for]="inputId()">
          <strong>{{ title() }}</strong>
          @if (hint(); as text) {
            <span class="muted">{{ text }}</span>
          }
        </label>
        <ng-content select="[ruleSwitch]" />
      </div>
      @if (enabled() && !masterOn()) {
        <p class="muted" data-testid="automation.policy.inactiveHint" [attr.data-rule]="rule()">{{ 'automation.policy.inactiveHint' | t }}</p>
      } @else if (enabled() && sentence(); as text) {
        <p class="member-line" data-testid="automation.policy.member" [attr.data-rule]="rule()">
          <i class="pi pi-info-circle" aria-hidden="true"></i><span>{{ text }}</span>
        </p>
      }
      <ng-content />
    </section>
  `,
  styleUrl: '../automation.scss',
  styles: `
    :host { display: block; min-width: 0; }
    .rule { height: 100%; box-sizing: border-box; align-content: start; }
  `,
})
export class RuleCardComponent {
  readonly title = input.required<string>();
  readonly hint = input<string | undefined>(undefined);
  readonly inputId = input.required<string>();
  readonly enabled = input(false);
  /** The section's master switch (`pickupPolicy.enabled`); rules apply only while it is on. */
  readonly masterOn = input(true);
  /** The member sentence (rule-sentences.ts), shown while the rule is on and the master switch too. */
  readonly sentence = input<string | undefined>(undefined);
  /** The summary rule of the sentence (`data-rule`). */
  readonly rule = input<string | undefined>(undefined);
  readonly testId = input<string | undefined>(undefined);
}
