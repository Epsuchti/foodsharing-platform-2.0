import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';

import {
  IntroPickupCategory,
  IntroPickupCreate,
  IntroPickupDetail,
  IntroPickupGuideService,
  PrivateInfoTemplate,
} from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { INTRO_CATEGORIES, isOnline, isoToZonedLocal, zonedLocalToIso } from '../intro-category';

/** Prefill for a new EinAb (e.g. from an iCal candidate). */
export interface EinAbPrefill {
  startAt?: string;
  businessName?: string;
  privateAddress?: string;
}

interface FormModel {
  category: IntroPickupCategory;
  start: string;
  slotCount: number;
  minimumPickupCount: number | null;
  visitFairteiler: boolean;
  businessName: string;
  privateAddress: string;
  publicGeneralLocation: string;
  onlineCallLink: string;
  privateInfo: string;
  publicInfo: string;
  saveTemplate: boolean;
}

const EMPTY: FormModel = {
  category: IntroPickupCategory.Supermarket,
  start: '',
  slotCount: 2,
  minimumPickupCount: null,
  visitFairteiler: false,
  businessName: '',
  privateAddress: '',
  publicGeneralLocation: '',
  onlineCallLink: '',
  privateInfo: '',
  publicInfo: '',
  saveTemplate: false,
};

/**
 * Create / edit an EinAb (EA10, EA11): category-driven (ONLINE → call link, otherwise address + public area), 1–3
 * slots, minimum pickups, Fairteiler flag, private info with the guide's template for the category and "save as
 * template". Server validation errors are shown inline.
 */
@Component({
  selector: 'fs-einab-form-dialog',
  imports: [Button, Checkbox, Dialog, FormsModule, InputNumber, InputText, Select, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="(einab() ? 'introPickups.form.editTitle' : 'introPickups.form.createTitle') | t"
      [style]="{ width: 'min(42rem, 96vw)' }"
    >
      <form class="form" (ngSubmit)="save.run()" id="einab-form" data-testid="introPickups.form">
        <div class="form__row">
          <div class="field">
            <label for="ea-category">{{ 'introPickups.form.category' | t }}</label>
            <p-select inputId="ea-category" name="category" [options]="categoryOptions()" optionLabel="label" optionValue="value" [(ngModel)]="m.category" (ngModelChange)="touch()" appendTo="body" [style]="{ width: '100%' }" data-testid="introPickups.form.category" />
          </div>
          <div class="field">
            <label for="ea-start">{{ 'introPickups.form.start' | t }}</label>
            <input id="ea-start" name="start" type="datetime-local" class="native" [(ngModel)]="m.start" (ngModelChange)="touch()" required data-testid="introPickups.form.start" />
          </div>
        </div>
        @if (!online()) {
          <div class="form__row">
            <div class="field">
              <label for="ea-slots">{{ 'introPickups.form.slotCount' | t }}</label>
              <p-inputnumber inputId="ea-slots" name="slotCount" [(ngModel)]="m.slotCount" [min]="einab()?.slotCount ?? 1" [max]="3" [showButtons]="true" data-testid="introPickups.form.slotCount" />
            </div>
            <div class="field">
              <label for="ea-min">{{ 'introPickups.form.minimumPickups' | t }}</label>
              <p-inputnumber inputId="ea-min" name="minimumPickupCount" [(ngModel)]="m.minimumPickupCount" [min]="0" [max]="1000" data-testid="introPickups.form.minimumPickups" />
            </div>
          </div>
          <div class="field field--inline">
            <p-checkbox inputId="ea-fairteiler" name="visitFairteiler" [binary]="true" [(ngModel)]="m.visitFairteiler" data-testid="introPickups.form.fairteiler" />
            <label for="ea-fairteiler">{{ 'introPickups.form.fairteiler' | t }}</label>
          </div>
          <div class="field">
            <label for="ea-business">{{ 'introPickups.form.businessName' | t }}</label>
            <input pInputText id="ea-business" name="businessName" [(ngModel)]="m.businessName" data-testid="introPickups.form.businessName" />
          </div>
        }
        @if (online()) {
          <p class="muted" data-testid="introPickups.form.onlineHint">{{ 'introPickups.form.onlineHint' | t }}</p>
          <div class="field">
            <label for="ea-call">{{ 'introPickups.form.callLink' | t }}</label>
            <input pInputText id="ea-call" name="onlineCallLink" type="url" placeholder="https://meet.jit.si/…" [(ngModel)]="m.onlineCallLink" data-testid="introPickups.form.callLink" />
          </div>
        } @else {
          <div class="form__row">
            <div class="field">
              <label for="ea-address">{{ 'introPickups.form.address' | t }}</label>
              <input pInputText id="ea-address" name="privateAddress" [(ngModel)]="m.privateAddress" data-testid="introPickups.form.address" />
            </div>
            <div class="field">
              <label for="ea-area">{{ 'introPickups.form.area' | t }}</label>
              <input pInputText id="ea-area" name="publicGeneralLocation" [(ngModel)]="m.publicGeneralLocation" [placeholder]="'introPickups.form.areaPlaceholder' | t" data-testid="introPickups.form.area" />
            </div>
          </div>
        }
        <div class="field">
          <span class="label-row">
            <label for="ea-private">{{ 'introPickups.form.privateInfo' | t }}</label>
            @if (template(); as tpl) {
              <p-button [label]="'introPickups.form.useTemplate' | t" icon="pi pi-file-import" [text]="true" size="small" (onClick)="m.privateInfo = tpl.content; touch()" data-testid="introPickups.form.useTemplate" />
            }
          </span>
          <textarea pTextarea id="ea-private" name="privateInfo" rows="3" [(ngModel)]="m.privateInfo" data-testid="introPickups.form.privateInfo"></textarea>
          <span class="muted">{{ 'introPickups.form.privateInfoHint' | t }}</span>
        </div>
        <div class="field field--inline">
          <p-checkbox inputId="ea-tpl" name="saveTemplate" [binary]="true" [(ngModel)]="m.saveTemplate" data-testid="introPickups.form.saveTemplate" />
          <label for="ea-tpl">{{ 'introPickups.form.saveTemplate' | t }}</label>
        </div>
        <div class="field">
          <label for="ea-public">{{ 'introPickups.form.publicInfo' | t }}</label>
          <textarea pTextarea id="ea-public" name="publicInfo" rows="2" [(ngModel)]="m.publicInfo" data-testid="introPickups.form.publicInfo"></textarea>
        </div>
        @if (problem()) {
          <p class="error-text" role="alert" data-testid="introPickups.form.error">{{ problem() }}</p>
        }
      </form>
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" [text]="true" severity="secondary" (onClick)="visible.set(false)" />
        <p-button (onClick)="save.run()" [label]="'common.save' | t" icon="pi pi-check" [loading]="save.busy()" data-testid="introPickups.form.save" />
      </ng-template>
    </p-dialog>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    :host {
      display: contents;
    }
    .label-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .label-row label {
      font-size: 0.85rem;
      font-weight: 650;
    }
  `,
})
export class EinAbFormDialogComponent {
  private readonly api = inject(IntroPickupGuideService);
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  readonly regionId = input<string | null>(null);
  readonly timeZone = input('Europe/Zurich');
  /** Edit this EinAb; null creates a new one. */
  readonly einab = input<IntroPickupDetail | null>(null);
  readonly prefill = input<EinAbPrefill | null>(null);
  readonly templates = input<readonly PrivateInfoTemplate[]>([]);
  readonly saved = output<IntroPickupDetail>();

  protected m: FormModel = { ...EMPTY };
  private readonly version = signal(0);
  protected readonly problem = signal('');

  protected readonly categoryOptions = computed(() =>
    INTRO_CATEGORIES.map((value) => ({ value, label: this.i18n.t('common.introCategory.' + value) })),
  );
  protected readonly online = computed(() => {
    this.version();
    return isOnline(this.m.category);
  });
  protected readonly template = computed(() => {
    this.version();
    return this.templates().find((tpl) => tpl.category === this.m.category);
  });

  constructor() {
    effect(() => {
      if (!this.visible()) {
        return;
      }
      const einab = this.einab();
      const prefill = this.prefill();
      const zone = this.timeZone();
      this.problem.set('');
      this.m = einab
        ? {
            category: einab.category,
            start: isoToZonedLocal(einab.startAt, zone),
            slotCount: einab.slotCount,
            minimumPickupCount: einab.minimumPickupCount ?? null,
            visitFairteiler: einab.visitFairteiler,
            businessName: einab.businessName ?? '',
            privateAddress: einab.privateAddress ?? '',
            publicGeneralLocation: einab.publicGeneralLocation ?? '',
            onlineCallLink: einab.onlineCallLink ?? '',
            privateInfo: einab.privateInfo ?? '',
            publicInfo: einab.publicInfo ?? '',
            saveTemplate: false,
          }
        : {
            ...EMPTY,
            start: isoToZonedLocal(prefill?.startAt, zone),
            businessName: prefill?.businessName ?? '',
            privateAddress: prefill?.privateAddress ?? '',
          };
      this.version.update((v) => v + 1);
    });
  }

  protected touch(): void {
    this.version.update((v) => v + 1);
  }

  private body(): IntroPickupCreate {
    const m = this.m;
    const online = isOnline(m.category);
    const text = (value: string) => value.trim() || undefined;
    return {
      category: m.category,
      startAt: zonedLocalToIso(m.start, this.timeZone()),
      slotCount: m.slotCount,
      minimumPickupCount: m.minimumPickupCount ?? undefined,
      visitFairteiler: m.visitFairteiler,
      businessName: text(m.businessName),
      privateAddress: online ? undefined : text(m.privateAddress),
      publicGeneralLocation: online ? undefined : text(m.publicGeneralLocation),
      onlineCallLink: online ? text(m.onlineCallLink) : undefined,
      privateInfo: text(m.privateInfo),
      publicInfo: text(m.publicInfo),
      savePrivateInfoAsTemplate: m.saveTemplate || undefined,
    };
  }

  protected readonly save = runMutation(
    () => {
      const body = this.body();
      const einab = this.einab();
      return einab
        ? this.api.updateIntroPickup({ introPickupId: einab.id, introPickupUpdate: body })
        : this.api.createIntroPickup({ regionId: this.regionId()!, introPickupCreate: body });
    },
    {
      successToastKey: 'introPickups.form.saved',
      errorToast: false,
      onSuccess: (detail) => {
        this.visible.set(false);
        this.saved.emit(detail);
      },
      onError: (error) => this.problem.set(this.i18n.t('errors.' + error.code)),
    },
  );
}
