import { ChangeDetectionStrategy, Component, computed, inject, input, model, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';

import { PickupEligibility, PickupEligibilityReason, PickupsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimeFormatter, DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { EligibilityReasonsComponent } from '../../../shared/eligibility-reasons/eligibility-reasons.component';
import { ServerClock } from '../pickups.resource';
import { BlockReason, blockReason, uiRuleFormat } from '../rule-sentences';
import { SignupTarget } from './signup-dialog.component';

/**
 * "Warum kannst du dich hier nicht eintragen?" (decision 165 S2, proposal §3.3): opened from a "Gesperrt" chip. Loads
 * the person's current eligibility for the pickup; one block per store rule with the person's own dates and what makes
 * it possible (jump to the next possible day, a cleaning shift, the linked-store step of the sign-up dialog, S3), plus
 * the eligibility reasons (team, hygiene, region rule) in the same dialog. Texts come from structured data, never from the server.
 */
@Component({
  selector: 'fs-blocked-reason-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, Dialog, EligibilityReasonsComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="'pickups.blocked.title' | t"
      [style]="{ width: 'min(32rem, 94vw)' }"
      [breakpoints]="{ '48rem': '100vw' }"
      data-testid="pickups.blocked.dialog"
    >
      @if (target(); as t) {
        <p class="when" data-testid="pickups.blocked.when">
          <strong>{{ t.storeName }}</strong><br />
          {{ t.pickupAt | fsDate: 'dateTimeLong' : t.timeZone }}
        </p>
        <fs-data-state [state]="eligibility" skeleton="inline" testId="pickups.blocked.state" data-testid="pickups.blocked.state">
          <ng-template [fsData]="eligibility" let-e>
            @if (e.eligible) {
              <p class="ok-line" data-testid="pickups.blocked.nowEligible">
                <i class="pi pi-check" aria-hidden="true"></i>{{ 'pickups.blocked.nowEligible' | t }}
              </p>
            }
            @if (reasons().length) {
              <ul class="reasons">
                @for (reason of reasons(); track $index) {
                  <li class="reason" data-testid="pickups.blocked.reason" [attr.data-rule]="reason.rule">
                    <span class="badge badge--bad"><i [class]="'pi ' + reason.icon" aria-hidden="true"></i>{{ reason.chip | t }}</span>
                    <p>{{ reason.text.key | t: reason.text.params }}</p>
                    @if (reason.fix || reason.action) {
                      <div class="reason__fix">
                        <i class="pi pi-arrow-right" aria-hidden="true"></i>
                        <span>
                          {{ 'pickups.blocked.makesPossible' | t }}
                          @if (reason.fix) {
                            {{ reason.fix.key | t: reason.fix.params }}
                          }
                          @if (reason.action; as action) {
                            @switch (action.kind) {
                              @case ('jump') {
                                <button type="button" class="link" (click)="onJump(action.at)" data-testid="pickups.blocked.action">
                                  {{ action.label.key | t: action.label.params }}
                                </button>
                              }
                              @case ('cleaning') {
                                <a [routerLink]="['/food-share-points', action.fspId, 'cleaning']" data-testid="pickups.blocked.action">
                                  {{ action.label.key | t: action.label.params }}
                                </a>
                              }
                              @case ('linked') {
                                <button type="button" class="link" (click)="onLinked()" data-testid="pickups.blocked.action">
                                  {{ action.label.key | t: action.label.params }}
                                </button>
                              }
                            }
                          }
                        </span>
                      </div>
                    }
                  </li>
                }
              </ul>
            }
            <fs-eligibility-reasons [eligibility]="withoutRules(e)" />
          </ng-template>
        </fs-data-state>
        @if (reasons().length) {
          <p class="muted all">
            {{ 'pickups.blocked.allRules' | t }}
            <button type="button" class="link" (click)="onSeeRules()" data-testid="pickups.blocked.seeRules">
              {{ 'pickups.blocked.seeRules' | t }}
            </button>
          </p>
        }
      }
      <ng-template #footer>
        @if (eligibility.data()?.eligible) {
          <p-button
            [label]="'pickups.signup.confirm' | t"
            icon="pi pi-check"
            [outlined]="true"
            (onClick)="onSignUp()"
            data-testid="pickups.blocked.signUp"
          />
        }
        <p-button [label]="'pickups.blocked.ok' | t" (onClick)="visible.set(false)" data-testid="pickups.blocked.ok" />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .when { margin: 0 0 0.75rem; }
    .reasons { display: grid; gap: 0.6rem; margin: 0 0 0.6rem; padding: 0; list-style: none; }
    .reason {
      display: grid; gap: 0.35rem; justify-items: start; padding: 0.6rem 0.8rem;
      border-left: 3px solid var(--fs-danger-border); border-radius: 0 var(--fs-radius-sm) var(--fs-radius-sm) 0;
      background: var(--fs-surface-muted);
    }
    .reason p { margin: 0; font-size: 0.92rem; }
    .badge {
      display: inline-flex; gap: 0.35rem; align-items: center; padding: 0.1rem 0.55rem; border-radius: 999px;
      font-size: 0.78rem; font-weight: 650;
    }
    .badge--bad { background: var(--fs-danger-soft); color: var(--fs-danger); }
    .reason__fix { display: flex; gap: 0.4rem; align-items: baseline; font-size: 0.88rem; color: var(--fs-primary-strong); }
    .link {
      padding: 0; border: 0; background: none; color: var(--fs-primary-strong); font: inherit; font-weight: 650;
      text-decoration: underline; cursor: pointer;
    }
    .reason__fix a { color: var(--fs-primary-strong); font-weight: 650; }
    .link:focus-visible, .reason__fix a:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
    .ok-line {
      display: flex; gap: 0.5rem; align-items: baseline; margin: 0 0 0.6rem; padding: 0.6rem 0.8rem;
      border-radius: var(--fs-radius-sm); background: var(--fs-primary-soft); color: var(--fs-primary-strong); font-weight: 600;
    }
    .muted { margin: 0.6rem 0 0; color: var(--fs-text-muted); font-size: 0.88rem; }
  `,
})
export class BlockedReasonDialogComponent {
  private readonly api = inject(PickupsService);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);
  private readonly clock = inject(ServerClock);

  readonly visible = model(false);
  readonly target = input<SignupTarget | null>(null);
  /** "Zum … springen": the start of the day to show in the list. */
  readonly jump = output<string>();
  /** "Alle Regeln dieses Betriebs": open the summary card. */
  readonly seeRules = output<void>();
  /** The reasons are gone in the meantime: open the sign-up instead. */
  readonly signUp = output<void>();
  /** "Abholung bei … wählen": open the sign-up with its linked-store step (decision 165 S3). */
  readonly linked = output<void>();

  protected readonly eligibility = loadResource<PickupEligibility, SignupTarget>({
    params: () => (this.visible() ? (this.target() ?? undefined) : undefined),
    loader: ({ storeId, pickupAt }) => this.api.checkEligibility({ storeId, pickupAt }),
  });

  protected readonly reasons = computed<BlockReason[]>(() => {
    const target = this.target();
    const blocks = this.eligibility.data()?.ruleBlocks ?? [];
    if (!target || !blocks.length) return [];
    const fmt = uiRuleFormat(
      (key, params) => this.i18n.t(key, params),
      (value, style, timeZone) => this.dates.format(value, style, timeZone),
      this.i18n.locale(),
      target.timeZone,
    );
    const now = new Date(this.clock.now());
    return blocks.map((block) => blockReason(block, fmt, now));
  });

  /** The eligibility reasons besides the store rules (those have their own blocks above). */
  protected withoutRules(e: PickupEligibility): PickupEligibility {
    return { ...e, reasons: e.reasons.filter((reason) => reason !== PickupEligibilityReason.AutomationRule) };
  }

  protected onJump(at: string): void {
    this.visible.set(false);
    this.jump.emit(at);
  }

  protected onSeeRules(): void {
    this.visible.set(false);
    this.seeRules.emit();
  }

  protected onLinked(): void {
    this.visible.set(false);
    this.linked.emit();
  }

  protected onSignUp(): void {
    this.visible.set(false);
    this.signUp.emit();
  }
}
