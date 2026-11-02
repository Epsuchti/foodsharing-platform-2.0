import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
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
  GapCandidate,
  GapCandidateLink,
  LinkedStoreRule,
  LinkedStoreWindowMode,
  PickupPolicy,
  StoreListPage,
  StoresService,
} from '../../../api';
import { ApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  GapLinkRule,
  LINK_PLACEHOLDERS,
  MAX_GAP_STORES,
  addDays,
  clone,
  emptyLinkRule,
  emptyPickupPolicy,
  fromIsoDate,
  gapLinkFindings,
  isoDate,
  joinAll,
  todayIn,
} from '../automation.util';
import { GapPreviewComponent } from '../components/gap-preview.component';
import { PreviewDialogComponent } from '../components/preview-dialog.component';
import { TemplateEditorComponent } from '../components/template-editor.component';
import { StoreAutomationContext } from '../store-automation.context';

interface GapInfo {
  id: string;
  name: string;
  automationActive: boolean;
  inOtherList: boolean;
  mayEdit: boolean;
  links: GapCandidateLink[];
}

interface GapWarning {
  key: string;
  kind: 'NO_AUTOMATION' | 'LINK' | 'CONFLICT';
  store?: string;
  linkIndex?: number;
  text: string;
}

/** Own linked-store rules per policy (server: TOO_MANY_LINKED_STORES). */
export const MAX_LINK_RULES = 2;
/** Stores per rule (server: TOO_MANY_STORES_PER_LINK). */
export const MAX_STORES_PER_LINK = 5;

/**
 * Pickup sign-up automation of a store (AU1): enabled / dry run, rule cards (gap with its own part and the group part
 * of decision 152, experience, cleaning, holidays),
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
    GapPreviewComponent,
    InputNumber,
    MultiSelect,
    NgTemplateOutlet,
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
        <section class="card fs-card gap-card" data-testid="automation.policy.gapCard">
          <div class="gap-head">
            <h3>{{ 'automation.policy.gap' | t }}</h3>
            <span
              class="badge"
              [class.badge--ok]="gapActive()"
              data-testid="automation.policy.gapStatus"
              [attr.data-status]="gapActive() ? 'on' : 'off'"
            >{{ (gapActive() ? 'automation.policy.gapStatus.on' : 'automation.policy.gapStatus.off') | t }}</span>
          </div>
          <div class="gap-part" data-testid="automation.policy.gapOwn">
            <h4 class="gap-part__title">{{ 'automation.policy.gapOwnTitle' | t }}</h4>
            <div class="switch-row">
              <label class="switch-row__text" for="pp-gap"><strong>{{ 'automation.policy.gapOwnEnabled' | t }}</strong>
                <span class="muted">{{ 'automation.policy.gapHint' | t }}</span></label>
              <p-toggleswitch inputId="pp-gap" [(ngModel)]="p.gapRuleEnabled" [disabled]="!ctx.mayEdit()" data-testid="automation.policy.gapEnabled" />
            </div>
            <div class="field">
              <label for="pp-gap-days">{{ 'automation.policy.gapDays' | t }}</label>
              <p-inputnumber
                class="gap-days"
                inputId="pp-gap-days"
                [(ngModel)]="p.minimumGapDays"
                [min]="0"
                [max]="60"
                [showButtons]="true"
                [disabled]="!ctx.mayEdit() || !p.gapRuleEnabled"
                data-testid="automation.policy.gapDays"
              />
            </div>
            @if (p.gapRuleEnabled) {
              <fs-gap-preview [pickup]="examplePickup()" [gap]="p.minimumGapDays" [storeNames]="[selfName()]" testId="automation.policy.gapPreview" />
            }
          </div>
          <div class="gap-part" data-testid="automation.policy.gapGroup">
            <h4 class="gap-part__title">{{ 'automation.policy.gapGroupTitle' | t }}</h4>
            <div class="field">
              <label for="pp-gap-stores">{{ 'automation.policy.gapStores' | t }}</label>
              <p class="muted" id="pp-gap-stores-hint">{{ 'automation.policy.gapStoresHint' | t }}</p>
              @if (ctx.mayEdit()) {
                <fs-data-state [state]="gapCandidates" skeleton="form" [skeletonRows]="1" testId="automation.policy.gapStores" data-testid="automation.policy.gapStores.state" [compact]="true">
                  <ng-template [fsData]="gapCandidates">
                    <p-multiselect
                      #gapPicker
                      (onChange)="releaseOpenGuard(gapPicker)"
                      inputId="pp-gap-stores"
                      [options]="gapOptions()"
                      optionLabel="name"
                      optionValue="id"
                      optionDisabled="disabled"
                      [ngModel]="gapMembers()"
                      (ngModelChange)="setGapMembers($event)"
                      [selectionLimit]="maxGapStores"
                      [filter]="true"
                      display="comma"
                      [maxSelectedLabels]="0"
                      [selectedItemsLabel]="'automation.policy.gapStoresSelected' | t"
                      [placeholder]="'automation.policy.gapStoresPlaceholder' | t"
                      [showToggleAll]="false"
                      appendTo="body"
                      ariaLabelledBy="pp-gap-stores-hint"
                      data-testid="automation.policy.gapStores"
                    >
                      <ng-template #item let-option>
                        <span class="gap-option" [attr.data-store]="option.id">
                          <span class="gap-option__name">{{ option.name }}</span>
                          <ng-container *ngTemplateOutlet="badge; context: { $implicit: option }" />
                        </span>
                      </ng-template>
                    </p-multiselect>
                  </ng-template>
                </fs-data-state>
              }
            </div>
            @if (gapMembers().length) {
              <ul class="members" [attr.aria-label]="'automation.policy.gapStoresLabel' | t">
                @for (member of memberRows(); track member.id) {
                  <li
                    class="member"
                    data-testid="automation.policy.gapMember"
                    [attr.data-store]="member.id"
                    [attr.data-locked]="member.locked"
                    [attr.data-automation]="member.automationActive"
                    [attr.data-error]="member.error"
                  >
                    <i class="pi pi-shop member__icon" aria-hidden="true"></i>
                    <span class="member__main">
                      <span class="member__name">{{ member.name }}</span>
                      <ng-container *ngTemplateOutlet="badge; context: { $implicit: member }" />
                    </span>
                    <span class="member__end">
                      @if (member.locked) {
                        <i class="pi pi-lock member__lock" aria-hidden="true"></i>
                      } @else if (ctx.mayEdit()) {
                        <p-button
                          icon="pi pi-times"
                          [text]="true"
                          [rounded]="true"
                          severity="secondary"
                          [ariaLabel]="'automation.policy.gapRemove' | t: { store: member.name }"
                          (onClick)="removeGapMember(member.id)"
                          data-testid="automation.policy.gapMember.remove"
                        />
                      }
                    </span>
                  </li>
                }
              </ul>
              <div class="field">
                <label for="pp-group-gap-days">{{ 'automation.policy.groupGapDays' | t }}</label>
                <p-inputnumber
                  class="gap-days"
                  inputId="pp-group-gap-days"
                  [(ngModel)]="p.groupMinimumGapDays"
                  [min]="1"
                  [max]="60"
                  [showButtons]="true"
                  [disabled]="!ctx.mayEdit() || lockedMembers().length > 0"
                  ariaDescribedBy="pp-group-gap-days-hint"
                  data-testid="automation.policy.groupGapDays"
                />
                @if (lockedMembers().length) {
                  <p class="muted" id="pp-group-gap-days-hint" data-testid="automation.policy.gapLockedGap">
                    {{ 'automation.policy.gapLockedGap' | t: { stores: names(lockedMembers()) } }}
                  </p>
                } @else {
                  <p class="muted" id="pp-group-gap-days-hint" [class.error-text]="(p.groupMinimumGapDays ?? 0) < 1">{{ 'automation.policy.gapMinWithList' | t }}</p>
                }
              </div>
              @if (lockedMembers().length && ctx.mayEdit()) {
                <p class="muted" data-testid="automation.policy.gapLockedLeave"><i class="pi pi-lock" aria-hidden="true"></i> {{ 'automation.policy.gapLockedLeave' | t }}</p>
              }
              <p class="banner banner--info" data-testid="automation.policy.gapNote">
                <i class="pi pi-info-circle" aria-hidden="true"></i
                ><span>{{ 'automation.policy.gapNote' | t: { stores: names(gapNoteStores()) } }} {{ 'automation.policy.gapOwnAlso' | t }}</span>
              </p>
            }
            @for (warning of gapWarnings(); track warning.key; let wi = $index) {
              <p
                class="banner"
                [class.banner--danger]="warning.kind === 'CONFLICT'"
                [attr.role]="warning.kind === 'CONFLICT' ? 'alert' : 'status'"
                [id]="'pp-gap-warning-' + wi"
                data-testid="automation.policy.gapWarning"
                [attr.data-kind]="warning.kind"
                [attr.data-store]="warning.store"
              >
                <i class="pi" [class.pi-ban]="warning.kind === 'CONFLICT'" [class.pi-exclamation-triangle]="warning.kind !== 'CONFLICT'" aria-hidden="true"></i>{{ warning.text }}
              </p>
            }
            @if (showLeave()) {
              <div class="actions">
                <p-button
                  [label]="'automation.policy.gapLeave' | t"
                  icon="pi pi-sign-out"
                  severity="danger"
                  [outlined]="true"
                  size="small"
                  (onClick)="leaveGroup()"
                  data-testid="automation.policy.gapLeave"
                />
              </div>
            }
            @if (gapMembers().length) {
              <fs-gap-preview [pickup]="examplePickup()" [gap]="p.groupMinimumGapDays ?? 0" [storeNames]="gapNoteStores()" testId="automation.policy.groupGapPreview" />
            }
          </div>
        </section>
        <ng-template #badge let-entry>
          @if (entry.locked) {
            <span class="badge badge--bad"><i class="pi pi-lock" aria-hidden="true"></i>{{ 'automation.policy.gapBadge.locked' | t }}</span>
          } @else if (entry.inOtherList) {
            <span class="badge"><i class="pi pi-list" aria-hidden="true"></i>{{ 'automation.policy.gapBadge.taken' | t }}</span>
          } @else if (entry.automationActive) {
            <span class="badge badge--ok"><i class="pi pi-check" aria-hidden="true"></i>{{ 'automation.policy.gapBadge.on' | t }}</span>
          } @else {
            <span class="badge badge--dry"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i>{{ 'automation.policy.gapBadge.off' | t }}</span>
          }
        </ng-template>
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
          <!-- FS2: the region's cleaning Fairteiler, or a hint linking to the "Putzen" section -->
          @if (ctx.data()?.cleaningFoodSharePoints?.length) {
            <p class="muted">{{ 'automation.policy.cleaningFsps' | t }}</p>
            <ul class="cleaning-fsps" data-testid="automation.policy.cleaningFsps">
              @for (fsp of ctx.data()!.cleaningFoodSharePoints; track fsp.id) {
                <li><a [routerLink]="['/food-share-points', fsp.id, 'cleaning']" data-testid="automation.policy.cleaningFsp">{{ fsp.name }}</a></li>
              }
            </ul>
          } @else {
            <p class="muted" data-testid="automation.policy.noCleaningFsps">
              {{ 'automation.policy.noCleaningFsps' | t }}
              @if (ctx.regionId(); as regionId) {
                <a [routerLink]="['/regions', regionId, 'automation', 'cleaning-fsps']" data-testid="automation.policy.cleaningFspsLink">{{ 'automation.policy.cleaningFspsLink' | t }}</a>
              }
            </p>
          }
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
            @for (warning of linkGapWarnings(i); track warning.key) {
              <p
                class="banner banner--sm"
                [class.banner--danger]="warning.kind === 'CONFLICT'"
                [attr.role]="warning.kind === 'CONFLICT' ? 'alert' : 'status'"
                [attr.data-testid]="'automation.policy.link.' + i + '.gapWarning'"
                [attr.data-kind]="warning.kind"
              >
                <i class="pi" [class.pi-ban]="warning.kind === 'CONFLICT'" [class.pi-exclamation-triangle]="warning.kind !== 'CONFLICT'" aria-hidden="true"></i>{{ warning.text }}
              </p>
            }
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
          {{ saveErrorText(e) }}
        </p>
      }
      <div class="actions">
        @if (ctx.mayEdit()) {
          <p-button
            [label]="'common.save' | t"
            icon="pi pi-save"
            [loading]="save.busy()"
            [disabled]="!dirty() || gapBlocked()"
            (onClick)="save.run()"
            data-testid="automation.policy.save"
          />
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
    .rules .gap-card { grid-column: 1 / -1; }
    .gap-days { width: 9rem; }
    .gap-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.4rem 0.75rem; }
    .gap-head h3 { margin: 0; }
    .gap-part { display: grid; gap: 0.75rem; padding-top: 0.75rem; border-top: 1px solid var(--fs-border); }
    .gap-part__title { margin: 0; font-size: 1rem; font-weight: 650; }
    .gap-option { display: flex; flex-wrap: wrap; align-items: center; gap: 0.25rem 0.6rem; min-width: 0; }
    .members { display: grid; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .member {
      display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 0.2rem 0.6rem; align-items: center;
      min-height: var(--fs-target-sm); padding: 0.35rem 0.4rem 0.35rem 0.7rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md);
    }
    .member[data-error='true'] { border-color: var(--fs-danger-border); background: var(--fs-danger-soft); }
    .member__icon, .member__lock { color: var(--fs-text-muted); }
    .member__main { display: flex; flex-wrap: wrap; align-items: center; gap: 0.25rem 0.6rem; min-width: 0; }
    .member__name { font-weight: 650; overflow-wrap: anywhere; }
    .member__end { display: inline-flex; align-items: center; gap: 0.4rem; }
    .banner--danger { border-color: var(--fs-danger-border); background: var(--fs-danger-soft); color: var(--fs-danger); }
    .banner--sm { padding: 0.5rem 0.75rem; font-size: 0.85rem; }
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

  protected readonly maxGapStores = MAX_GAP_STORES;

  /** Stores that can join the gap list (decision 151), with their link rules for the D7 warnings. */
  protected readonly gapCandidates = loadResource<GapCandidate[], string>({
    params: () => (this.ctx.mayEdit() ? this.ctx.storeId() : undefined),
    loader: (storeId) => this.api.listGapCandidates({ storeId }),
    isEmpty: () => false,
  });

  /** The other stores of the gap list as a signal (the multiselect needs a stable array between change detection runs). */
  private readonly gapList = signal<string[]>([]);
  protected readonly gapMembers = computed(() => this.gapList());

  /** What the page knows about a store: candidates plus the saved list (which carries mayEdit for locked members). */
  private readonly gapInfo = computed(() => {
    const info = new Map<string, GapInfo>();
    for (const c of this.gapCandidates.data() ?? []) {
      info.set(c.store.id, { id: c.store.id, name: c.store.name, automationActive: c.automationActive, inOtherList: c.inOtherList, mayEdit: c.mayEdit, links: c.links });
    }
    for (const g of this.ctx.data()?.pickupPolicy?.gapStores ?? []) {
      const known = info.get(g.store.id);
      info.set(g.store.id, {
        id: g.store.id,
        name: g.store.name,
        automationActive: g.automationActive,
        inOtherList: false,
        mayEdit: g.mayEdit,
        links: known?.links ?? [],
      });
    }
    return info;
  });

  protected readonly gapOptions = computed(() =>
    [...this.gapInfo().values()]
      .map((s) => ({ ...s, locked: !s.mayEdit, disabled: s.inOtherList || !s.mayEdit }))
      .sort((a, b) => a.name.localeCompare(b.name)),
  );

  protected readonly memberRows = computed(() => {
    const errorStore = this.save.error()?.args?.['storeId'];
    return this.gapMembers().map((id) => {
      const s = this.gapInfo().get(id);
      return {
        id,
        name: s?.name ?? id,
        automationActive: s?.automationActive ?? false,
        inOtherList: false,
        locked: s ? !s.mayEdit : false,
        error: errorStore === id,
      };
    });
  });

  protected readonly lockedMembers = computed(() => this.memberRows().filter((m) => m.locked).map((m) => m.name));
  protected readonly selfName = computed(() => this.ctx.store.data()?.name ?? '');
  protected readonly gapNoteStores = computed(() => [this.selfName(), ...this.memberRows().map((m) => m.name)]);
  /** Example pickup: today + 7 in the region's time zone (owner). */
  protected readonly examplePickup = computed(() => addDays(todayIn(this.ctx.timeZone() ?? undefined), 7));

  protected setGapMembers(ids: string[] | null): void {
    const draft = this.draft()!;
    const wasEmpty = !this.gapList().length;
    this.gapList.set([...(ids ?? [])]);
    draft.gapStoreIds = this.gapList();
    // a new group starts with this store's own gap, at least 1 day (decision 152, D8)
    if (wasEmpty && this.gapList().length) {
      draft.groupMinimumGapDays = Math.max(1, draft.minimumGapDays ?? 0);
    }
  }

  /** Header badge: on while the own rule is on or the store is in a group (decision 152, D8). */
  protected gapActive(): boolean {
    const p = this.draft();
    return !!p && (p.gapRuleEnabled || this.gapMembers().length > 0);
  }

  /**
   * "Remove this store from the group" (decision 152, D5): shown while the store is in a saved group and still listed
   * in the draft, when its own rule is off or a member is locked (that member cannot be removed one by one).
   */
  protected showLeave(): boolean {
    const p = this.draft();
    const saved = this.ctx.data()?.pickupPolicy?.gapStoreIds ?? [];
    return !!p && this.ctx.mayEdit() && saved.length > 0 && this.gapMembers().length > 0
      && (!p.gapRuleEnabled || this.lockedMembers().length > 0);
  }

  /** Clears the list in the draft; saving sends an empty list and leaves the group (always allowed). */
  protected leaveGroup(): void {
    this.setGapMembers([]);
  }

  protected removeGapMember(id: string): void {
    this.setGapMembers(this.gapList().filter((m) => m !== id));
  }

  protected names(list: readonly string[]): string {
    return joinAll(list, this.i18n.t('automation.policy.gapAnd'));
  }

  private days(count: number): string {
    return this.i18n.t('automation.policy.gapDayCount', { count });
  }

  private storeName(id: string | undefined): string | undefined {
    if (!id) return undefined;
    if (id === this.ctx.storeId()) return this.selfName();
    const known = this.gapInfo().get(id)?.name;
    if (known) return known;
    for (const rule of this.draft()?.linkedStores ?? []) {
      const store = (rule.stores ?? []).find((s) => s.id === id);
      if (store) return store.name;
    }
    return this.storeOptions().find((s) => s.id === id)?.name;
  }

  /** The link rules for the D7 check: this store's (as edited, with the mirrors they create) first, then the listed stores'. */
  private gapRules(policy: PickupPolicy): GapLinkRule[] {
    const self = this.ctx.storeId() ?? '';
    const rules: GapLinkRule[] = [];
    policy.linkedStores.forEach((rule, linkIndex) => {
      const lastDays = rule.windowMode === LinkedStoreWindowMode.LastDays;
      const before = lastDays ? rule.windowDaysBefore : 0;
      const after = lastDays ? rule.windowDaysAfter : 0;
      rules.push({ owner: self, storeIds: rule.storeIds ?? [], windowMode: rule.windowMode, windowDaysBefore: before, windowDaysAfter: after, linkIndex });
      if (!rule.mirrorOf && rule.bidirectional) {
        for (const target of rule.storeIds ?? []) {
          rules.push({ owner: target, storeIds: [self], windowMode: rule.windowMode, windowDaysBefore: after, windowDaysAfter: before, linkIndex });
        }
      }
    });
    for (const id of this.gapMembers()) {
      for (const link of this.gapInfo().get(id)?.links ?? []) {
        rules.push({ owner: id, storeIds: link.storeIds, windowMode: link.windowMode, windowDaysBefore: link.windowDaysBefore, windowDaysAfter: link.windowDaysAfter });
      }
    }
    return rules;
  }

  /** Warnings of the gap card (re-evaluated on every change detection run: the draft is edited in place). */
  protected gapWarnings(): GapWarning[] {
    const p = this.draft();
    if (!p || !this.gapMembers().length) return [];
    const warnings: GapWarning[] = [];
    const noAutomation = this.memberRows().filter((m) => !m.automationActive).map((m) => m.name);
    if (noAutomation.length) {
      warnings.push({
        key: 'NO_AUTOMATION',
        kind: 'NO_AUTOMATION',
        text: this.i18n.t('automation.policy.gapWarnNoAutomation', { stores: this.names(noAutomation) }),
      });
    }
    const gap = p.groupMinimumGapDays ?? 0;
    for (const finding of gapLinkFindings(this.ctx.storeId() ?? '', this.gapMembers(), gap, this.gapRules(p))) {
      const rule = finding.rule;
      const window =
        rule.windowMode === LinkedStoreWindowMode.LastDays
          ? this.i18n.t('automation.policy.gapWindow.days', { before: rule.windowDaysBefore, after: rule.windowDaysAfter })
          : this.i18n.t('automation.policy.gapWindow.sameDay');
      const params = { store: this.storeName(finding.store) ?? '', window, gap: this.days(gap) };
      warnings.push({
        key: `${finding.blocking ? 'CONFLICT' : 'LINK'}:${rule.owner}:${rule.storeIds.join()}:${rule.linkIndex ?? ''}`,
        kind: finding.blocking ? 'CONFLICT' : 'LINK',
        store: finding.store,
        linkIndex: rule.linkIndex,
        text: this.i18n.t(finding.blocking ? 'automation.policy.gapConflict' : 'automation.policy.gapWarnLink', params),
      });
    }
    return warnings;
  }

  protected linkGapWarnings(index: number): GapWarning[] {
    return this.gapWarnings().filter((w) => w.linkIndex === index);
  }

  /** Save is disabled while a link conflict blocks or the group gap is below 1 with a list. */
  protected gapBlocked(): boolean {
    const p = this.draft();
    if (!p || !this.gapMembers().length) return false;
    return (p.groupMinimumGapDays ?? 0) < 1 || this.gapWarnings().some((w) => w.kind === 'CONFLICT');
  }

  /** The save error with the store's name where the server named one (403 / taken / link conflict). */
  protected saveErrorText(error: ApiErrorView): string {
    const self = this.ctx.storeId();
    const args = error.args ?? {};
    const named = (key: string, id: string | undefined) => {
      const store = this.storeName(id);
      return store ? this.i18n.t(key, { store }) : this.i18n.t(`errors.${error.code}`);
    };
    switch (error.code) {
      case 'ACCESS_DENIED':
        return args['storeId'] ? named('automation.policy.gapForbidden', args['storeId']) : this.i18n.t('errors.ACCESS_DENIED');
      case 'GAP_LIST_STORE_TAKEN':
        return named('automation.policy.gapTaken', args['storeId']);
      case 'GAP_LINK_CONFLICT':
        return named('automation.policy.gapLinkConflict', args['storeId'] === self ? args['otherStoreId'] : args['storeId']);
      default:
        return this.i18n.t(`errors.${error.code}`);
    }
  }

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
    draft.gapStoreIds = [...(draft.gapStoreIds ?? [])];
    this.gapList.set(draft.gapStoreIds);
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
      // decision 152: the list is sent whatever the own switch says (empty = leave); the order does not matter
      gapStoreIds: [...(policy.gapStoreIds ?? [])].sort(),
      ...(policy.gapStoreIds?.length ? { groupMinimumGapDays: policy.groupMinimumGapDays } : {}),
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
