import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';

import {
  ApiErrorCode,
  AutomationService,
  LinkedOption,
  LinkedOptionRule,
  LinkedOptions,
  PickupEligibility,
  PickupsService,
  SignupResult,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimeFormatter, DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { EligibilityReasonsComponent } from '../../../shared/eligibility-reasons/eligibility-reasons.component';
import { bookable, linkWindow, linkedSteps, onlyLinkMissing, uiRuleFormat } from '../rule-sentences';

export interface SignupTarget {
  storeId: string;
  storeName: string;
  pickupAt: string;
  timeZone: string;
  /**
   * Repair mode (decision 167 S3): the person is signed up, their linked-store pickup was signed out; until then they
   * choose a new one here (only the linked pickups are booked).
   */
  linkMissingUntil?: string;
}

/** The sign-up result plus the linked stores booked with it. */
export type SignupDone = SignupResult & { linkedStores?: string[] };

/**
 * Sign up for a pickup (PU3/PU4/PU5): checks eligibility first; eligible → confirm; otherwise the translated reasons
 * and the region rule counters. Emits the result (`confirmed` = preconfirmed, e.g. managers).
 *
 * Decision 167 S3 (proposal §3.4): when a linked-store rule applies, step 2..n per rule that is not yet satisfied:
 * choose one of the linked stores' pickups in the window (pickups the rules there block are listed as "Gesperrt
 * dort"), none free, or not in that team (Team-Beitritt anfragen). A satisfied rule shows its pickup. All sign-ups go
 * in one request and are stored together or not at all; cancel books nothing.
 */
@Component({
  selector: 'fs-pickup-signup-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, Dialog, EligibilityReasonsComponent, NgTemplateOutlet, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [closeAriaLabel]="'common.close' | t"
      [header]="(repair() ? 'pickups.linked.repairTitle' : 'pickups.signup.title') | t"
      [style]="{ width: 'min(34rem, 94vw)' }"
      [breakpoints]="{ '48rem': '100vw' }"
      data-testid="pickups.signup.dialog"
    >
      @if (target(); as t) {
        <p class="when" data-testid="pickups.signup.when">
          <strong>{{ t.storeName }}</strong><br />
          {{ t.pickupAt | fsDate: 'dateTimeLong' : t.timeZone }}
        </p>
        @if (t.linkMissingUntil; as until) {
          <p class="muted" data-testid="pickups.signup.linked.repair">
            {{ 'pickups.linked.repairText' | t: { until: (until | fsDate: 'dateTime' : t.timeZone) } }}
          </p>
          <ng-container *ngTemplateOutlet="linkedBlock" />
        } @else {
          <fs-data-state [state]="eligibility" skeleton="inline" testId="pickups.signup.state" data-testid="pickups.signup.state">
            <ng-template [fsData]="eligibility" let-e>
              @if (e.eligible || linkOnly()) {
                <p class="muted" data-testid="pickups.signup.eligible">{{ 'pickups.signup.text' | t }}</p>
              } @else {
                <p class="muted">{{ 'pickups.signup.notEligible' | t }}</p>
                <fs-eligibility-reasons [eligibility]="e" />
              }
              @if (e.eligible || linkOnly()) {
                <ng-container *ngTemplateOutlet="linkedBlock" />
              }
            </ng-template>
          </fs-data-state>
        }
      }

      <ng-template #linkedBlock>
        <fs-data-state [state]="linked" skeleton="list" testId="pickups.signup.linked.state" data-testid="pickups.signup.linked.state">
          <ng-template [fsData]="linked" let-l>
            @for (rule of satisfied(); track $index) {
              <p class="ok-line" data-testid="pickups.signup.linked.satisfied">
                <i class="pi pi-check" aria-hidden="true"></i>
                <span>{{ 'pickups.linked.satisfied' | t: satisfiedParams(rule) }}</span>
              </p>
            }
            @if (current(); as rule) {
              <p class="steps" data-testid="pickups.signup.linked.step" [attr.data-step]="index() + 2" [attr.data-total]="steps().length + 1">
                {{ 'pickups.linked.step' | t: { step: index() + 2, total: steps().length + 1, store: storeList(rule) } }}
              </p>
              @if (selectable(rule).length) {
                <p class="lead">
                  {{ (rule.stores.length > 1 ? 'pickups.linked.chooseAny' : 'pickups.linked.choose') | t: windowParams(rule) }}
                </p>
                <ul class="opts" role="radiogroup" [attr.aria-label]="storeList(rule)">
                  @for (option of rule.options; track option.store.id + option.pickupAt) {
                    @if (isBookable(option)) {
                      <li>
                        <label class="opt" data-testid="pickups.signup.linked.option" [attr.data-store]="option.store.id" [attr.data-at]="option.pickupAt">
                          <input
                            type="radio"
                            [name]="'linked-' + index()"
                            [checked]="chosen()[index()] === option"
                            (change)="choose(option)"
                          />
                          <span class="opt__when">{{ option.pickupAt | fsDate: 'weekday' : zone() }} {{ option.pickupAt | fsDate: 'time' : zone() }}</span>
                          @if (rule.stores.length > 1) {
                            <span class="opt__store">{{ option.store.name }}</span>
                          }
                          <span class="badge badge--ok">{{ 'pickups.linked.free' | t: { count: option.freeCount } }}</span>
                        </label>
                      </li>
                    } @else {
                      <li class="opt opt--blocked" aria-disabled="true" data-testid="pickups.signup.linked.option" data-blocked="true" [attr.data-store]="option.store.id" [attr.data-at]="option.pickupAt">
                        <input type="radio" disabled [attr.aria-label]="'pickups.linked.blockedThere' | t" />
                        <span class="opt__when">{{ option.pickupAt | fsDate: 'weekday' : zone() }} {{ option.pickupAt | fsDate: 'time' : zone() }}</span>
                        @if (rule.stores.length > 1) {
                          <span class="opt__store">{{ option.store.name }}</span>
                        }
                        <span class="badge"><i class="pi pi-lock" aria-hidden="true"></i>{{ 'pickups.linked.blockedThere' | t }}</span>
                      </li>
                    }
                  }
                </ul>
                <p class="muted small"><i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'pickups.linked.atomic' | t }}</p>
              } @else if (!rule.notMember.length) {
                <p class="banner banner--danger" data-testid="pickups.signup.linked.noneFree">
                  <i class="pi pi-ban" aria-hidden="true"></i>
                  <span>{{ 'pickups.linked.noneFree' | t: noneFreeParams(rule) }}</span>
                </p>
                @for (store of rule.stores; track store.id) {
                  <a class="small" [routerLink]="['/stores', store.id, 'pickups']" (click)="visible.set(false)">
                    {{ 'pickups.linked.openStore' | t: { store: store.name } }}
                  </a>
                }
              }
              @for (store of rule.notMember; track store.id) {
                <div class="banner" data-testid="pickups.signup.linked.notMember">
                  <i class="pi pi-user" aria-hidden="true"></i>
                  <span>{{ 'pickups.linked.notMember' | t: { store: store.name } }}</span>
                </div>
                <a class="small" [routerLink]="['/stores', store.id]" (click)="visible.set(false)" data-testid="pickups.signup.linked.apply">
                  {{ 'pickups.linked.apply' | t }}
                </a>
              }
            }
          </ng-template>
        </fs-data-state>
      </ng-template>

      <ng-template #footer>
        <p-button
          [label]="'common.cancel' | t"
          severity="secondary"
          [text]="true"
          (onClick)="visible.set(false)"
          data-testid="pickups.signup.cancel"
        />
        @if (index() > 0) {
          <p-button [label]="'pickups.linked.back' | t" severity="secondary" [outlined]="true" (onClick)="back()" data-testid="pickups.signup.linked.back" />
        }
        @if (steps().length && index() < steps().length - 1) {
          <p-button
            [label]="'pickups.linked.next' | t"
            icon="pi pi-arrow-right"
            iconPos="right"
            [disabled]="!chosen()[index()]"
            (onClick)="next()"
            data-testid="pickups.signup.linked.next"
          />
        } @else if (steps().length) {
          <p-button
            [label]="(repair() ? 'pickups.linked.confirmRepair' : 'pickups.linked.confirmBoth') | t"
            icon="pi pi-check"
            [disabled]="!complete()"
            [loading]="signUp.busy()"
            (onClick)="signUp.run()"
            data-testid="pickups.signup.linked.confirm"
          />
        } @else if (plainSignUp()) {
          <p-button
            [label]="'pickups.signup.confirm' | t"
            icon="pi pi-check"
            [loading]="signUp.busy()"
            (onClick)="signUp.run()"
            data-testid="pickups.signup.confirm"
          />
        }
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .when { margin: 0 0 0.75rem; }
    .muted { margin: 0 0 0.6rem; color: var(--fs-text-muted); }
    .small { font-size: 0.88rem; }
    .lead { margin: 0 0 0.4rem; }
    .steps { margin: 0.6rem 0 0.4rem; font-weight: 650; }
    .ok-line {
      display: flex; gap: 0.5rem; align-items: baseline; margin: 0 0 0.6rem; padding: 0.6rem 0.8rem;
      border-radius: var(--fs-radius-sm); background: var(--fs-primary-soft); color: var(--fs-primary-strong); font-weight: 600;
    }
    .opts { display: grid; gap: 0.35rem; margin: 0 0 0.6rem; padding: 0; list-style: none; }
    .opt {
      display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; min-height: var(--fs-target-sm, 2.75rem);
      padding: 0.35rem 0.6rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-sm); cursor: pointer;
    }
    .opt--blocked { cursor: default; background: var(--fs-surface-muted); color: var(--fs-text-muted); }
    .opt__when { font-variant-numeric: tabular-nums; font-weight: 600; }
    .opt__store { color: var(--fs-text-muted); }
    .badge {
      display: inline-flex; gap: 0.3rem; align-items: center; margin-left: auto; padding: 0.1rem 0.55rem;
      border-radius: 999px; font-size: 0.78rem; font-weight: 650; background: var(--fs-surface-muted); color: var(--fs-text);
    }
    .badge--ok { background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .banner {
      display: flex; gap: 0.5rem; align-items: baseline; margin: 0 0 0.4rem; padding: 0.6rem 0.8rem;
      border-radius: var(--fs-radius-sm); background: var(--fs-surface-muted);
    }
    .banner--danger { background: var(--fs-danger-soft); color: var(--fs-danger); }
    a.small { display: inline-block; margin: 0 0.8rem 0.6rem 0; color: var(--fs-primary-strong); font-weight: 650; }
  `,
})
export class PickupSignupDialogComponent {
  private readonly api = inject(PickupsService);
  private readonly automation = inject(AutomationService);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);

  readonly visible = model(false);
  readonly target = input<SignupTarget | null>(null);
  readonly signedUp = output<SignupDone>();

  protected readonly repair = computed(() => !!this.target()?.linkMissingUntil);
  protected readonly zone = computed(() => this.target()?.timeZone ?? 'Europe/Zurich');

  protected readonly eligibility = loadResource<PickupEligibility, SignupTarget>({
    params: () => (this.visible() && !this.repair() ? (this.target() ?? undefined) : undefined),
    loader: ({ storeId, pickupAt }) => this.api.checkEligibility({ storeId, pickupAt }),
  });

  protected readonly eligible = computed(() => !!this.eligibility.data()?.eligible);
  protected readonly linkOnly = computed(() => onlyLinkMissing(this.eligibility.data()));

  /** The linked-store step is loaded once the pickup itself is possible (or when repairing). */
  protected readonly linked = loadResource<LinkedOptions, SignupTarget>({
    params: () => {
      const target = this.target();
      if (!this.visible() || !target) return undefined;
      return this.repair() || this.eligible() || this.linkOnly() ? target : undefined;
    },
    loader: ({ storeId, pickupAt }) => this.automation.getLinkedOptions({ storeId, pickupAt }),
    isEmpty: () => false,
  });

  protected readonly satisfied = computed(() => (this.linked.data()?.rules ?? []).filter((rule) => !!rule.satisfiedBy));
  protected readonly steps = computed(() =>
    linkedSteps(this.linked.data()?.rules ?? [], this.eligibility.data()?.ruleBlocks, this.repair()),
  );
  protected readonly index = signal(0);
  protected readonly current = computed<LinkedOptionRule | undefined>(() => this.steps()[this.index()]);
  protected readonly chosen = signal<Record<number, LinkedOption>>({});
  protected readonly complete = computed(() => this.steps().every((_, i) => !!this.chosen()[i]));
  /** Without a linked step: eligible as it is (a link-only block waits for the step). */
  protected readonly plainSignUp = computed(() => this.eligible() && !['idle', 'loading'].includes(this.linked.status()));

  constructor() {
    // a new target or a reopened dialog starts at step 2 with nothing chosen
    effect(() => {
      this.visible();
      this.target();
      this.linked.data();
      this.index.set(0);
      this.chosen.set({});
    });
  }

  protected isBookable(option: LinkedOption): boolean {
    return bookable(option);
  }

  protected selectable(rule: LinkedOptionRule): LinkedOption[] {
    return rule.options.filter(bookable);
  }

  protected choose(option: LinkedOption): void {
    this.chosen.update((chosen) => ({ ...chosen, [this.index()]: option }));
  }

  protected next(): void {
    this.index.update((i) => Math.min(i + 1, this.steps().length - 1));
  }

  protected back(): void {
    this.index.update((i) => Math.max(i - 1, 0));
  }

  private format() {
    return uiRuleFormat(
      (key, params) => this.i18n.t(key, params),
      (value, style, timeZone) => this.dates.format(value, style, timeZone),
      this.i18n.locale(),
      this.zone(),
    );
  }

  protected storeList(rule: LinkedOptionRule): string {
    return this.format().list(
      rule.stores.map((store) => store.name),
      'or',
    );
  }

  protected windowParams(rule: LinkedOptionRule): Record<string, string> {
    const fmt = this.format();
    return { store: this.storeList(rule), from: fmt.day(rule.from), to: fmt.day(rule.to) };
  }

  protected noneFreeParams(rule: LinkedOptionRule): Record<string, string> {
    return this.windowParams(rule);
  }

  protected satisfiedParams(rule: LinkedOptionRule): Record<string, string> {
    const fmt = this.format();
    const by = rule.satisfiedBy!;
    return {
      store: by.store.name,
      date: `${fmt.day(by.pickupAt)} ${this.dates.format(by.pickupAt, 'time', this.zone())}`,
      window: linkWindow(rule, fmt),
    };
  }

  protected readonly signUp = runMutation(
    () => {
      const target = this.target()!;
      const chosen = this.steps().map((_, i) => this.chosen()[i]).filter((option): option is LinkedOption => !!option);
      const linkedPickups = chosen.map((option) => ({ storeId: option.store.id, pickupAt: option.pickupAt }));
      if (this.repair()) {
        // the pickup here stays; the first chosen linked pickup is the sign-up, the others go with it
        const [first, ...rest] = linkedPickups;
        return this.api.signUp({ storeId: first.storeId, pickupAt: first.pickupAt, signupRequest: rest.length ? { linkedPickups: rest } : undefined });
      }
      return this.api.signUp({
        storeId: target.storeId,
        pickupAt: target.pickupAt,
        signupRequest: linkedPickups.length ? { linkedPickups } : undefined,
      });
    },
    {
      onSuccess: (result) => {
        const stores = this.steps()
          .map((_, i) => this.chosen()[i]?.store.name)
          .filter((name): name is string => !!name);
        this.visible.set(false);
        this.signedUp.emit({ ...result, linkedStores: stores });
      },
      // a linked pickup filled up or got blocked in the meantime: show the current options
      onError: (error) => {
        const linkedCodes: string[] = [ApiErrorCode.LinkedPickupFull, ApiErrorCode.LinkedPickupBlocked, ApiErrorCode.LinkedPickupRequired];
        if (linkedCodes.includes(error.code)) {
          this.linked.reload();
        }
      },
    },
  );
}
