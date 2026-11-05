import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { SelectButton } from '@openng/optimus-ui/selectbutton';

import { LinkedStoreRule, LinkedStoreWindowMode, StoreRef } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { LINK_PLACEHOLDERS } from '../automation.util';
import { TemplateEditorComponent } from './template-editor.component';

/** A warning of the gap card that concerns this rule (decision 151 D7). */
export interface LinkRuleWarning {
  key: string;
  kind: 'NO_AUTOMATION' | 'LINK' | 'CONFLICT';
  text: string;
}

/**
 * The stores of a rule the actor does not edit: they lock "Auch in den verknüpften Betrieben anwenden" (decision 167
 * Q3). A rule that is already bidirectional may still be switched off.
 */
export function lockedStores(rule: Pick<LinkedStoreRule, 'storeIds' | 'stores'>, editable: ReadonlySet<string>): StoreRef[] {
  const names = new Map((rule.stores ?? []).map((s) => [s.id, s]));
  return rule.storeIds.filter((id) => !editable.has(id)).map((id) => names.get(id) ?? { id, name: id });
}

/**
 * One linked-store rule of the Regeln section (decision 167 S4, proposal §3.1): store chips ("oder"), "Betrieb
 * verknüpfen" (opens the picker), window, the member sentence, the bidirectional checkbox (locked with a reason when a
 * store is not the actor's), "Erweitert" with *Frist nach Austragung* and the message, remove. Mirrors are read-only.
 * The rule is edited in place (the section compares its draft on every change detection run).
 */
@Component({
  selector: 'fs-link-rule',
  imports: [Button, Checkbox, FormsModule, InputNumber, SelectButton, TemplateEditorComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = rule();
    @let i = index();
    <div class="card link" [attr.data-testid]="'automation.policy.link.' + i" [class.link--mirror]="!!r.mirrorOf">
      @for (warning of warnings(); track warning.key) {
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
      @if (r.mirrorOf) {
        <p class="muted" data-testid="automation.policy.link.mirror">
          <i class="pi pi-arrows-h" aria-hidden="true"></i>
          {{ 'automation.policy.mirror' | t: { stores: storeNames() } }}
        </p>
        @if (sentence(); as text) {
          <p class="member-line" data-testid="automation.policy.link.member"><i class="pi pi-info-circle" aria-hidden="true"></i><span>{{ text }}</span></p>
        }
      } @else {
        <div class="field">
          <span class="lbl" [id]="'pp-link-stores-' + i">{{ 'automation.policy.linkStores' | t }}</span>
          <ul class="chips" [attr.aria-labelledby]="'pp-link-stores-' + i" [attr.data-testid]="'automation.policy.link.' + i + '.stores'">
            @for (store of stores(); track store.id; let first = $first) {
              @if (!first) {
                <li class="chip chip--or" aria-hidden="true">{{ 'automation.policy.linkStoresOr' | t }}</li>
              }
              <li class="chip chip--on" data-testid="automation.policy.link.store" [attr.data-store]="store.id">
                {{ store.name }}
                @if (mayEdit()) {
                  <button type="button" class="chip-remove" [attr.aria-label]="'automation.picker.remove' | t: { store: store.name }" (click)="removeStore(store.id)">×</button>
                }
              </li>
            }
            @if (mayEdit()) {
              <li>
                <p-button
                  [label]="'automation.policy.pickStore' | t"
                  icon="pi pi-search"
                  [outlined]="true"
                  size="small"
                  (onClick)="pick.emit()"
                  data-testid="automation.policy.pickStore"
                />
              </li>
            }
          </ul>
        </div>
        <div class="form-grid">
          <div class="field">
            <span>{{ 'automation.policy.windowMode' | t }}</span>
            <p-selectbutton
              [options]="windowModes()"
              optionLabel="label"
              optionValue="value"
              [(ngModel)]="r.windowMode"
              [allowEmpty]="false"
              [disabled]="!mayEdit()"
              [attr.data-testid]="'automation.policy.link.' + i + '.mode'"
            />
          </div>
          @if (r.windowMode === 'LAST_DAYS') {
            <div class="field">
              <label [for]="'pp-before-' + i">{{ 'automation.policy.daysBefore' | t }}</label>
              <p-inputnumber [inputId]="'pp-before-' + i" [(ngModel)]="r.windowDaysBefore" [min]="0" [max]="30" [showButtons]="true" [disabled]="!mayEdit()" [attr.data-testid]="'automation.policy.link.' + i + '.before'" />
            </div>
            <div class="field">
              <label [for]="'pp-after-' + i">{{ 'automation.policy.daysAfter' | t }}</label>
              <p-inputnumber [inputId]="'pp-after-' + i" [(ngModel)]="r.windowDaysAfter" [min]="0" [max]="30" [showButtons]="true" [disabled]="!mayEdit()" [attr.data-testid]="'automation.policy.link.' + i + '.after'" />
            </div>
          }
        </div>
        @if (sentence(); as text) {
          <p class="member-line" data-testid="automation.policy.link.member"><i class="pi pi-info-circle" aria-hidden="true"></i><span>{{ text }}</span></p>
        }
        <div class="row">
          <p-checkbox
            [inputId]="'pp-bidi-' + i"
            [binary]="true"
            [(ngModel)]="r.bidirectional"
            [disabled]="!mayEdit() || (locked().length > 0 && !r.bidirectional)"
            [attr.data-testid]="'automation.policy.link.' + i + '.bidirectional'"
          />
          <label [for]="'pp-bidi-' + i">{{ 'automation.policy.bidirectional' | t }}</label>
          @if (locked().length) {
            <span class="badge badge--bad" [attr.data-testid]="'automation.policy.link.' + i + '.bidiLocked'"
              ><i class="pi pi-lock" aria-hidden="true"></i>{{ 'automation.policy.bidiLocked' | t: { store: lockedNames() } }}</span
            >
          }
        </div>
        <p class="muted" [id]="'pp-bidi-hint-' + i">{{ 'automation.policy.bidiHint' | t }}</p>
        <details class="advanced" [attr.data-testid]="'automation.policy.link.' + i + '.advanced'">
          <summary>{{ 'automation.policy.advanced' | t }}</summary>
          <div class="advanced__body">
            <div class="field">
              <label [for]="'pp-grace-' + i">{{ 'automation.policy.lateRemoval' | t }}</label>
              <p-inputnumber
                [inputId]="'pp-grace-' + i"
                [(ngModel)]="r.graceMinutes"
                [min]="20"
                [max]="10080"
                [showButtons]="true"
                [step]="10"
                [disabled]="!mayEdit()"
                [ariaDescribedBy]="'pp-grace-hint-' + i"
                [attr.data-testid]="'automation.policy.link.' + i + '.grace'"
              />
              <p class="muted" [id]="'pp-grace-hint-' + i">{{ 'automation.policy.lateRemovalHint' | t }}</p>
            </div>
            <fs-template-editor
              [(value)]="r.message!"
              [placeholders]="linkPlaceholders"
              [values]="previewValues()"
              [label]="'automation.policy.linkMessage' | t"
              [inputId]="'pp-msg-' + i"
              [testId]="'automation.policy.link.' + i + '.message'"
            />
          </div>
        </details>
        @if (mayEdit()) {
          <div class="actions">
            <p-button
              [label]="'automation.policy.removeLink' | t"
              icon="pi pi-trash"
              severity="danger"
              [text]="true"
              size="small"
              (onClick)="remove.emit()"
              [attr.data-testid]="'automation.policy.link.' + i + '.remove'"
            />
          </div>
        }
      }
    </div>
  `,
  styleUrl: '../automation.scss',
  styles: `
    :host { display: block; }
    .link { border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md); }
    .link--mirror { background: var(--fs-surface-muted); }
    .lbl { font-weight: 600; }
    .chip--or { border-style: dashed; background: none; color: var(--fs-text-muted); cursor: default; }
    .chip-remove { margin-left: 0.25rem; border: 0; background: none; color: inherit; cursor: pointer; font-weight: 700; }
    .badge { display: inline-flex; gap: 0.3rem; align-items: center; white-space: normal; }
    .advanced { border-top: 1px solid var(--fs-border); padding-top: 0.5rem; }
    .advanced summary { min-height: var(--fs-target-sm); display: flex; align-items: center; cursor: pointer; font-weight: 600; }
    .advanced__body { display: grid; gap: 0.75rem; padding-top: 0.5rem; }
    .banner--danger { border-color: var(--fs-danger-border); background: var(--fs-danger-soft); color: var(--fs-danger); }
    .banner--sm { padding: 0.5rem 0.75rem; font-size: 0.85rem; }
  `,
})
export class LinkRuleComponent {
  readonly rule = input.required<LinkedStoreRule>();
  readonly index = input.required<number>();
  readonly mayEdit = input(false);
  /** The member sentence (rule-sentences.ts), already translated. */
  readonly sentence = input<string | undefined>(undefined);
  readonly warnings = input<LinkRuleWarning[]>([]);
  /** Stores the actor edits (saved `editableStoreIds` plus picker results). */
  readonly editable = input<ReadonlySet<string>>(new Set());
  readonly previewValues = input<Record<string, string | number>>({});
  readonly windowModes = input<{ value: LinkedStoreWindowMode; label: string }[]>([]);
  readonly pick = output<void>();
  readonly remove = output<void>();
  readonly storesChange = output<void>();

  protected readonly linkPlaceholders = LINK_PLACEHOLDERS;

  /** Re-evaluated on every run: the rule is edited in place. */
  protected stores(): StoreRef[] {
    const r = this.rule();
    const names = new Map((r.stores ?? []).map((s) => [s.id, s]));
    return r.storeIds.map((id) => names.get(id) ?? { id, name: id });
  }

  protected locked(): StoreRef[] {
    return lockedStores(this.rule(), this.editable());
  }

  protected lockedNames(): string {
    return this.locked().map((s) => s.name).join(', ');
  }

  protected readonly storeNames = computed(() => (this.rule().stores ?? []).map((s) => s.name).join(', '));

  protected removeStore(id: string): void {
    const r = this.rule();
    r.storeIds = r.storeIds.filter((s) => s !== id);
    r.stores = (r.stores ?? []).filter((s) => s.id !== id);
    this.storesChange.emit();
  }
}
