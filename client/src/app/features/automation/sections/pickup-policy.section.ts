import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { DatePicker } from '@openng/optimus-ui/datepicker';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { MultiSelect } from '@openng/optimus-ui/multiselect';
import { SelectButton } from '@openng/optimus-ui/selectbutton';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import {
  AutomationRunResult,
  AutomationService,
  LinkedStoreRule,
  LinkedStoreWindowMode,
  PickupPolicy,
  StoreListPage,
  StoresService,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { LINK_PLACEHOLDERS, clone, emptyLinkRule, emptyPickupPolicy, fromIsoDate, isoDate } from '../automation.util';
import { PreviewDialogComponent } from '../components/preview-dialog.component';
import { TemplateEditorComponent } from '../components/template-editor.component';
import { StoreAutomationContext } from '../store-automation.context';

/** Own linked-store rules per policy (server: TOO_MANY_LINKED_STORES). */
export const MAX_LINK_RULES = 2;
/** Stores per rule (server: TOO_MANY_STORES_PER_LINK). */
export const MAX_STORES_PER_LINK = 5;

/**
 * Pickup sign-up automation of a store (AU1): enabled / dry run, rule cards (gap, experience, cleaning, holidays),
 * linked-store rules, excluded dates, save, run now (dry run / live) and the preview dialog.
 */
@Component({
  selector: 'fs-pickup-policy-section',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Checkbox,
    DatePicker,
    DateTimePipe,
    FormsModule,
    InputNumber,
    MultiSelect,
    PreviewDialogComponent,
    RouterLink,
    SelectButton,
    TemplateEditorComponent,
    ToggleSwitch,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (draft(); as p) {
      <section class="card fs-card" data-testid="automation.policy.root" [attr.data-enabled]="p.enabled" [attr.data-dry-run]="p.dryRun" [attr.data-unsaved]="unsaved()">
        <h2>{{ 'automation.policy.title' | t }}</h2>
        <p class="muted">{{ 'automation.policy.intro' | t }}</p>
        <div class="switch-row">
          <label class="switch-row__text" for="pp-enabled">
            <strong>{{ 'automation.policy.enabled' | t }}</strong>
          </label>
          <p-toggleswitch inputId="pp-enabled" [(ngModel)]="p.enabled" [disabled]="!ctx.mayEdit()" data-testid="automation.policy.enabled" />
        </div>
        <div class="switch-row">
          <label class="switch-row__text" for="pp-dry">
            <strong>{{ 'automation.common.dryRun' | t }}</strong>
            <span class="muted">{{ 'automation.common.dryRunHint' | t }}</span>
          </label>
          <p-toggleswitch inputId="pp-dry" [(ngModel)]="p.dryRun" [disabled]="!ctx.mayEdit()" data-testid="automation.policy.dryRun" />
        </div>
        @if (p.enabled && p.dryRun) {
          <p class="banner" data-testid="automation.policy.dryRunBanner">
            <i class="pi pi-eye" aria-hidden="true"></i>{{ 'automation.common.dryRunBanner' | t }}
          </p>
        }
      </section>

      <div class="rules">
        <section class="card fs-card" data-testid="automation.policy.gapCard">
          <div class="switch-row">
            <label class="switch-row__text" for="pp-gap"><strong>{{ 'automation.policy.gap' | t }}</strong>
              <span class="muted">{{ 'automation.policy.gapHint' | t }}</span></label>
            <p-toggleswitch inputId="pp-gap" [(ngModel)]="p.gapRuleEnabled" [disabled]="!ctx.mayEdit()" data-testid="automation.policy.gapEnabled" />
          </div>
          <div class="field">
            <label for="pp-gap-days">{{ 'automation.policy.gapDays' | t }}</label>
            <p-inputnumber inputId="pp-gap-days" [(ngModel)]="p.minimumGapDays" [min]="0" [max]="60" [showButtons]="true" [disabled]="!ctx.mayEdit() || !p.gapRuleEnabled" data-testid="automation.policy.gapDays" />
          </div>
        </section>
        <section class="card fs-card">
          <div class="switch-row">
            <label class="switch-row__text" for="pp-exp"><strong>{{ 'automation.policy.experience' | t }}</strong>
              <span class="muted">{{ 'automation.policy.experienceHint' | t }}</span></label>
            <p-toggleswitch inputId="pp-exp" [(ngModel)]="p.experienceRuleEnabled" [disabled]="!ctx.mayEdit()" data-testid="automation.policy.experience" />
          </div>
        </section>
        <section class="card fs-card">
          <div class="switch-row">
            <label class="switch-row__text" for="pp-clean"><strong>{{ 'automation.policy.cleaning' | t }}</strong>
              <span class="muted">{{ 'automation.policy.cleaningHint' | t }}</span></label>
            <p-toggleswitch inputId="pp-clean" [(ngModel)]="p.cleaningRuleEnabled" [disabled]="!ctx.mayEdit()" data-testid="automation.policy.cleaning" />
          </div>
          @if (ctx.regionId(); as regionId) {
            <a [routerLink]="['/regions', regionId, 'automation', 'cleaning-exemptions']" data-testid="automation.policy.exemptionsLink">{{ 'automation.policy.exemptionsLink' | t }}</a>
          }
        </section>
        <section class="card fs-card">
          <div class="switch-row">
            <label class="switch-row__text" for="pp-hol"><strong>{{ 'automation.policy.holidays' | t }}</strong>
              <span class="muted">{{ 'automation.policy.holidaysHint' | t }}</span></label>
            <p-toggleswitch inputId="pp-hol" [(ngModel)]="p.publicHolidayRuleEnabled" [disabled]="!ctx.mayEdit()" data-testid="automation.policy.holidays" />
          </div>
          @if (ctx.regionId(); as regionId) {
            <a [routerLink]="['/regions', regionId, 'automation', 'holidays']" data-testid="automation.policy.holidaysLink">{{ 'automation.policy.holidaysLink' | t }}</a>
          }
        </section>
      </div>

      <section class="card fs-card" data-testid="automation.policy.links">
        <h3>{{ 'automation.policy.linksTitle' | t }}</h3>
        <p class="muted">{{ 'automation.policy.linksHint' | t }}</p>
        @for (rule of p.linkedStores; track $index; let i = $index) {
          <div class="card link" [attr.data-testid]="'automation.policy.link.' + i" [class.link--mirror]="!!rule.mirrorOf">
            @if (rule.mirrorOf) {
              <p class="muted" data-testid="automation.policy.link.mirror">
                <i class="pi pi-arrows-h" aria-hidden="true"></i>
                {{ 'automation.policy.mirror' | t: { stores: storeNames(rule) } }}
              </p>
            } @else {
              <div class="field">
                <label [for]="'pp-link-stores-' + i">{{ 'automation.policy.linkStores' | t }}</label>
                <p-multiselect
                  #linkStores
                  (onChange)="releaseOpenGuard(linkStores)"
                  [inputId]="'pp-link-stores-' + i"
                  [options]="storeOptions()"
                  optionLabel="name"
                  optionValue="id"
                  [(ngModel)]="rule.storeIds"
                  [selectionLimit]="maxStores"
                  [filter]="true"
                  display="chip"
                  [loading]="stores.status() === 'loading'"
                  [disabled]="!ctx.mayEdit()"
                  [attr.data-testid]="'automation.policy.link.' + i + '.stores'"
                />
              </div>
              <div class="form-grid">
                <div class="field">
                  <span>{{ 'automation.policy.windowMode' | t }}</span>
                  <p-selectbutton
                    [options]="windowModes()"
                    optionLabel="label"
                    optionValue="value"
                    [(ngModel)]="rule.windowMode"
                    [allowEmpty]="false"
                    [disabled]="!ctx.mayEdit()"
                    [attr.data-testid]="'automation.policy.link.' + i + '.mode'"
                  />
                </div>
                @if (rule.windowMode === 'LAST_DAYS') {
                  <div class="field">
                    <label [for]="'pp-before-' + i">{{ 'automation.policy.daysBefore' | t }}</label>
                    <p-inputnumber [inputId]="'pp-before-' + i" [(ngModel)]="rule.windowDaysBefore" [min]="0" [max]="30" [showButtons]="true" [disabled]="!ctx.mayEdit()" />
                  </div>
                  <div class="field">
                    <label [for]="'pp-after-' + i">{{ 'automation.policy.daysAfter' | t }}</label>
                    <p-inputnumber [inputId]="'pp-after-' + i" [(ngModel)]="rule.windowDaysAfter" [min]="0" [max]="30" [showButtons]="true" [disabled]="!ctx.mayEdit()" />
                  </div>
                }
                <div class="field">
                  <label [for]="'pp-grace-' + i">{{ 'automation.policy.grace' | t }}</label>
                  <p-inputnumber [inputId]="'pp-grace-' + i" [(ngModel)]="rule.graceMinutes" [min]="20" [max]="10080" [showButtons]="true" [step]="10" [disabled]="!ctx.mayEdit()" [attr.data-testid]="'automation.policy.link.' + i + '.grace'" />
                </div>
              </div>
              <div class="row">
                <p-checkbox [inputId]="'pp-bidi-' + i" [binary]="true" [(ngModel)]="rule.bidirectional" [disabled]="!ctx.mayEdit()" [attr.data-testid]="'automation.policy.link.' + i + '.bidirectional'" />
                <label [for]="'pp-bidi-' + i">{{ 'automation.policy.bidirectional' | t }}</label>
              </div>
              <fs-template-editor
                [(value)]="rule.message!"
                [placeholders]="linkPlaceholders"
                [values]="linkPreviewValues(rule)"
                [label]="'automation.policy.linkMessage' | t"
                [inputId]="'pp-msg-' + i"
                [testId]="'automation.policy.link.' + i + '.message'"
              />
              @if (ctx.mayEdit()) {
                <div class="actions">
                  <p-button [label]="'automation.policy.removeLink' | t" icon="pi pi-trash" severity="danger" [text]="true" size="small" (onClick)="removeLink(i)" [attr.data-testid]="'automation.policy.link.' + i + '.remove'" />
                </div>
              }
            }
          </div>
        }
        @if (ctx.mayEdit() && ownLinks() < maxLinks) {
          <div class="actions">
            <p-button [label]="'automation.policy.addLink' | t" icon="pi pi-plus" [outlined]="true" size="small" (onClick)="addLink()" data-testid="automation.policy.addLink" />
          </div>
        }
      </section>

      <section class="card fs-card" data-testid="automation.policy.excluded">
        <h3>{{ 'automation.policy.excludedTitle' | t }}</h3>
        <p class="muted">{{ 'automation.policy.excludedHint' | t }}</p>
        @if (ctx.mayEdit()) {
          <p-datepicker
            [ngModel]="excludedDates()"
            (ngModelChange)="setExcluded($event)"
            selectionMode="multiple"
            [showIcon]="true"
            [readonlyInput]="true"
            [minDate]="today"
            [ariaLabel]="'automation.policy.excludedTitle' | t"
            data-testid="automation.policy.excludedPicker"
          />
        }
        <ul class="chips" data-testid="automation.policy.excludedList">
          @for (day of sortedExcluded(); track day) {
            <li class="chip chip--on" data-testid="automation.policy.excludedDate" [attr.data-date]="day">
              {{ day | fsDate: 'date' }}
              @if (ctx.mayEdit()) {
                <button type="button" class="chip-remove" [attr.aria-label]="'automation.policy.removeDate' | t" (click)="removeExcluded(day)">×</button>
              }
            </li>
          } @empty {
            <li class="muted">{{ 'automation.policy.noExcluded' | t }}</li>
          }
        </ul>
      </section>

      @if (save.error(); as e) {
        <p class="error-text" role="alert" data-testid="automation.policy.error" [attr.data-code]="e.code">
          {{ 'errors.' + e.code | t }}
        </p>
      }
      <div class="actions">
        @if (ctx.mayEdit()) {
          <p-button [label]="'common.save' | t" icon="pi pi-save" [loading]="save.busy()" [disabled]="!dirty()" (onClick)="save.run()" data-testid="automation.policy.save" />
          @if (exists()) {
            <p-button [label]="'automation.common.runDry' | t" icon="pi pi-eye" [outlined]="true" [disabled]="unsaved()" [loading]="run.busy()" (onClick)="run.run(true)" data-testid="automation.policy.runDry" />
            <p-button [label]="'automation.common.runNow' | t" icon="pi pi-play" severity="warn" [outlined]="true" [disabled]="p.dryRun || unsaved()" [loading]="run.busy()" (onClick)="run.run(false)" data-testid="automation.policy.run" />
          }
        }
        @if (exists()) {
          <p-button [label]="'automation.preview.open' | t" icon="pi pi-question-circle" [text]="true" (onClick)="previewOpen.set(true)" data-testid="automation.policy.preview" />
        }
      </div>
      @if (ctx.mayEdit() && exists() && unsaved()) {
        <p class="muted" data-testid="automation.policy.saveFirst">{{ 'automation.policy.saveFirst' | t }}</p>
      }
      @if (lastRun(); as r) {
        <p class="banner banner--info" data-testid="automation.policy.runResult">
          {{ 'automation.common.runResult' | t: { evaluated: r.evaluated, confirmed: r.confirmed, declined: r.declined, skipped: r.skipped, deferred: r.deferred, failed: r.failed } }}
          @if (r.dryRun) {
            <span class="badge badge--dry">{{ 'automation.common.dryRunTag' | t }}</span>
          }
        </p>
      }
      <fs-preview-dialog [(visible)]="previewOpen" />
    }
  `,
  styleUrl: '../automation.scss',
  styles: `
    .link { border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md); }
    .link--mirror { background: var(--fs-surface-muted); }
    .chip-remove { margin-left: 0.25rem; border: 0; background: none; color: inherit; cursor: pointer; font-weight: 700; }
    p-multiselect { width: 100%; }
  `,
})
export class PickupPolicySection {
  protected readonly ctx = inject(StoreAutomationContext);
  private readonly api = inject(AutomationService);
  private readonly storesApi = inject(StoresService);
  private readonly i18n = inject(I18nService);

  protected readonly maxLinks = MAX_LINK_RULES;
  protected readonly maxStores = MAX_STORES_PER_LINK;
  protected readonly linkPlaceholders = LINK_PLACEHOLDERS;
  protected readonly today = new Date();

  protected readonly draft = signal<PickupPolicy | null>(null);
  protected readonly lastRun = signal<AutomationRunResult | null>(null);
  protected readonly previewOpen = signal(false);
  protected readonly exists = computed(() => !!this.ctx.data()?.pickupPolicy);

  /** Stores of the region for the linked-store picker (without this store). */
  protected readonly stores = loadResource<StoreListPage, string>({
    params: () => (this.ctx.mayEdit() ? this.ctx.regionId() : undefined),
    loader: (regionId) => this.storesApi.listRegionStores({ regionId, includeSubregions: true, size: 200 }),
    isEmpty: () => false,
  });

  protected readonly storeOptions = computed(() => {
    const own = this.ctx.storeId();
    const listed = (this.stores.data()?.items ?? []).filter((s) => s.id !== own).map((s) => ({ id: s.id, name: s.name }));
    // keep already linked stores selectable even when they are not in the list (other region)
    const known = new Set(listed.map((s) => s.id));
    for (const rule of this.draft()?.linkedStores ?? []) {
      for (const store of rule.stores ?? []) {
        if (!known.has(store.id)) {
          listed.push({ id: store.id, name: store.name });
          known.add(store.id);
        }
      }
    }
    return listed.sort((a, b) => a.name.localeCompare(b.name));
  });

  protected readonly windowModes = computed(() => [
    { value: LinkedStoreWindowMode.SameDay, label: this.i18n.t('automation.policy.sameDay') },
    { value: LinkedStoreWindowMode.LastDays, label: this.i18n.t('automation.policy.lastDays') },
  ]);

  constructor() {
    effect(() => {
      const data = this.ctx.data();
      untracked(() => this.reset(data?.pickupPolicy));
    });
  }

  private reset(policy: PickupPolicy | undefined): void {
    const draft = policy ? clone(policy) : emptyPickupPolicy();
    draft.linkedStores.forEach((rule) => (rule.message ??= ''));
    this.excluded.set([...draft.excludedDates]);
    this.draft.set(draft);
  }

  /** Re-evaluated on every change detection run (the draft is edited in place through ngModel). */
  protected dirty(): boolean {
    const draft = this.draft();
    if (!draft) return false;
    const saved = this.ctx.data()?.pickupPolicy;
    if (!saved) return true;
    return JSON.stringify(this.payload(draft)) !== JSON.stringify(this.payload(saved));
  }

  /**
   * Dry run and run evaluate the saved policy: while the draft differs from the server state, or a save is still in
   * flight (and until the saved state is reloaded), a run would use the old configuration (same rule as the adverts).
   */
  protected unsaved(): boolean {
    return this.save.busy() || this.dirty();
  }

  protected ownLinks(): number {
    return (this.draft()?.linkedStores ?? []).filter((rule) => !rule.mirrorOf).length;
  }

  protected addLink(): void {
    this.draft()!.linkedStores.push(emptyLinkRule());
  }

  protected removeLink(index: number): void {
    this.draft()!.linkedStores.splice(index, 1);
  }

  /**
   * Optimus MultiSelect ignores every click on its field for 150 ms after the click that opened the list (a guard
   * against one click toggling twice, released by a timer). Choosing a store ends that opening click, so the guard is
   * released right away: otherwise "open, choose, close" within 150 ms (or with the timer running late on a busy main
   * thread) left the list open, because the closing click on the dropdown icon was swallowed (AU1 flake).
   */
  protected releaseOpenGuard(select: MultiSelect): void {
    select.clickInProgress = false;
  }

  protected storeNames(rule: LinkedStoreRule): string {
    return (rule.stores ?? []).map((s) => s.name).join(', ');
  }

  protected linkPreviewValues(rule: LinkedStoreRule): Record<string, string | number> {
    const linked = this.storeOptions().filter((s) => rule.storeIds.includes(s.id)).map((s) => s.name);
    return {
      name: this.i18n.t('automation.policy.previewName'),
      storeName: this.ctx.store.data()?.name ?? '',
      linkedStoreName: linked.join(', '),
      pickupDate: new Intl.DateTimeFormat(this.i18n.locale(), { dateStyle: 'medium' }).format(new Date()),
      graceMinutes: rule.graceMinutes,
      requirement: this.i18n.t('automation.policy.previewRequirement'),
    };
  }

  /** Excluded dates as a signal (the date picker needs a stable array reference between change detection runs). */
  private readonly excluded = signal<string[]>([]);
  protected readonly excludedDates = computed(() => this.excluded().map(fromIsoDate));
  protected readonly sortedExcluded = computed(() => [...this.excluded()].sort());

  protected setExcluded(dates: Date[] | null): void {
    this.excluded.set([...new Set((dates ?? []).map(isoDate))]);
    this.draft()!.excludedDates = this.excluded();
  }

  protected removeExcluded(day: string): void {
    this.excluded.update((list) => list.filter((d) => d !== day));
    this.draft()!.excludedDates = this.excluded();
  }

  private payload(policy: PickupPolicy): PickupPolicy {
    return {
      enabled: policy.enabled,
      dryRun: policy.dryRun,
      gapRuleEnabled: policy.gapRuleEnabled,
      minimumGapDays: policy.minimumGapDays,
      cleaningRuleEnabled: policy.cleaningRuleEnabled,
      experienceRuleEnabled: policy.experienceRuleEnabled,
      publicHolidayRuleEnabled: policy.publicHolidayRuleEnabled,
      linkedStores: policy.linkedStores
        .filter((rule) => !rule.mirrorOf)
        .map((rule) => ({
          id: rule.id,
          storeIds: rule.storeIds,
          bidirectional: rule.bidirectional,
          windowMode: rule.windowMode,
          windowDaysBefore: rule.windowMode === LinkedStoreWindowMode.LastDays ? rule.windowDaysBefore : 0,
          windowDaysAfter: rule.windowMode === LinkedStoreWindowMode.LastDays ? rule.windowDaysAfter : 0,
          graceMinutes: rule.graceMinutes,
          message: rule.message?.trim() || undefined,
        })),
      excludedDates: [...policy.excludedDates].sort(),
      version: policy.version,
    };
  }

  protected readonly save = runMutation(
    () => this.api.putPickupPolicy({ storeId: this.ctx.storeId()!, pickupPolicy: this.payload(this.draft()!) }),
    { successToastKey: 'automation.common.saved', errorToast: false, onSuccess: () => this.ctx.reload() },
  );

  protected readonly run = runMutation(
    (dryRun: boolean) => this.api.runPickupPolicy({ storeId: this.ctx.storeId()!, dryRun }),
    { successToastKey: 'automation.common.ran', onSuccess: (result) => this.lastRun.set(result) },
  );
}
