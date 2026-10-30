import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { Report, ReportOptions, ReportReason, ReportsService, StoreListItem, StoresService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  REASON_TEXT_MAX,
  REPORT_MESSAGE_MAX,
  REPORT_MESSAGE_MIN,
  reasonChoices,
  reasonLabel,
  reportDraftErrors,
  reportableStores,
} from '../reports.util';

/**
 * Report dialog (G1), opened from the profile's report action (`permissions.mayReport`). An optional store of the
 * reporter's own teams decides the region (store region, else the member's home region); the reasons are exactly
 * the ones `getReportOptions` returns for it (plus OTHER with a text when the region allows it), so they reload
 * when the store changes. Message ≥ 10 characters, optional confirmation mail → `createReport`. When the server
 * says the report could not be filed (`buttonEnabled = false`), the dialog explains why and offers no submit.
 *
 * `<fs-report-dialog [(visible)]="open" [userId]="id" [userName]="name" (created)="…" />`
 */
@Component({
  selector: 'fs-report-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, FormsModule, InputText, Select, Textarea, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="'reports.dialog.title' | t: { name: userName() }"
      [style]="{ width: 'min(38rem, 94vw)' }"
      data-testid="reports.dialog"
    >
      <p class="intro">{{ 'reports.dialog.intro' | t }}</p>

      <div class="field">
        <label for="report-store">{{ 'reports.dialog.store' | t }}</label>
        <p-select
          inputId="report-store"
          [options]="storeOptions()"
          optionLabel="name"
          optionValue="id"
          [ngModel]="storeId()"
          (ngModelChange)="storeId.set($event ?? null)"
          [showClear]="true"
          [placeholder]="'reports.dialog.noStore' | t"
          [loading]="stores.status() === 'loading'"
          appendTo="body"
          [style]="{ width: '100%' }"
          data-testid="reports.dialog.store"
        >
          <ng-template #item let-option><span [attr.data-value]="option.id">{{ option.name }}</span></ng-template>
        </p-select>
        <small class="hint">{{ 'reports.dialog.storeHint' | t }}</small>
      </div>

      <fs-data-state [state]="options" skeleton="form" [compact]="true" data-testid="reports.dialog.options">
        <ng-template [fsData]="options" let-o>
          @if (!o.buttonEnabled) {
            <p class="denied" role="alert" data-testid="reports.dialog.denied" [attr.data-code]="o.denialCode">
              <i class="pi pi-ban" aria-hidden="true"></i>
              {{ (o.denialCode ? 'errors.' + o.denialCode : 'reports.dialog.denied') | t }}
            </p>
          } @else {
            <div class="field">
              <label for="report-reason">{{ 'reports.dialog.reason' | t }}</label>
              <p-select
                inputId="report-reason"
                [options]="reasonOptions()"
                optionLabel="label"
                optionValue="value"
                [ngModel]="reason()"
                (ngModelChange)="reason.set($event)"
                [placeholder]="'reports.dialog.reasonPlaceholder' | t"
                appendTo="body"
                [style]="{ width: '100%' }"
                [attr.aria-invalid]="shown('reason')"
                data-testid="reports.dialog.reason"
              >
                <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
              </p-select>
              @if (shown('reason')) {<small class="error" data-testid="reports.dialog.error.reason">{{ errors().reason! | t }}</small>}
            </div>

            @if (reason() === other) {
              <div class="field">
                <label for="report-reason-text">{{ 'reports.dialog.reasonText' | t }}</label>
                <input
                  pInputText
                  id="report-reason-text"
                  [maxlength]="reasonTextMax"
                  [ngModel]="reasonText()"
                  (ngModelChange)="reasonText.set($event)"
                  [attr.aria-invalid]="shown('reasonText')"
                  data-testid="reports.dialog.reasonText"
                />
                @if (shown('reasonText')) {<small class="error" data-testid="reports.dialog.error.reasonText">{{ errors().reasonText! | t: { max: reasonTextMax } }}</small>}
              </div>
            }

            <div class="field">
              <label for="report-message">{{ 'reports.dialog.message' | t }}</label>
              <textarea
                pTextarea
                id="report-message"
                rows="6"
                [maxlength]="messageMax"
                [ngModel]="message()"
                (ngModelChange)="message.set($event)"
                [attr.aria-invalid]="shown('message')"
                aria-describedby="report-message-hint"
                data-testid="reports.dialog.message"
              ></textarea>
              <small id="report-message-hint" class="hint" [class.error]="shown('message')">
                @if (shown('message')) {
                  {{ errors().message! | t: { min: messageMin } }}
                } @else {
                  {{ 'reports.dialog.messageCount' | t: { count: messageLength(), min: messageMin } }}
                }
              </small>
            </div>

            <div class="switch">
              <p-toggleswitch
                inputId="report-confirmation"
                [ngModel]="confirmationMail()"
                (ngModelChange)="confirmationMail.set($event)"
                data-testid="reports.dialog.confirmation"
              />
              <label for="report-confirmation">{{ 'reports.dialog.confirmation' | t }}</label>
            </div>
          }
        </ng-template>
      </fs-data-state>

      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" data-testid="reports.dialog.cancel" />
        <p-button
          [label]="'reports.dialog.submit' | t"
          icon="pi pi-flag"
          severity="danger"
          [disabled]="!options.data()?.buttonEnabled"
          [loading]="create.busy()"
          (onClick)="submit()"
          data-testid="reports.dialog.submit"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .intro { margin: 0 0 1rem; color: var(--fs-text-muted); }
    .field { display: grid; gap: 0.35rem; margin-bottom: 1rem; }
    .field label { font-weight: 650; font-size: 0.9rem; }
    textarea, input { width: 100%; }
    textarea { resize: vertical; }
    .hint { color: var(--fs-text-muted); font-size: 0.82rem; }
    .error { color: var(--fs-danger); font-weight: 600; font-size: 0.82rem; }
    .switch { display: flex; gap: 0.6rem; align-items: center; }
    .denied {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      margin: 0;
      padding: 0.6rem 0.8rem;
      border: 1px solid var(--fs-danger-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-danger-soft);
    }
  `,
})
export class ReportDialogComponent {
  private readonly api = inject(ReportsService);
  private readonly storesApi = inject(StoresService);
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  readonly userId = input.required<string>();
  readonly userName = input.required<string>();
  readonly created = output<Report>();

  protected readonly other = ReportReason.Other;
  protected readonly reasonTextMax = REASON_TEXT_MAX;
  protected readonly messageMin = REPORT_MESSAGE_MIN;
  protected readonly messageMax = REPORT_MESSAGE_MAX;

  protected readonly storeId = signal<string | null>(null);
  protected readonly reason = signal<ReportReason | null>(null);
  protected readonly reasonText = signal('');
  protected readonly message = signal('');
  protected readonly confirmationMail = signal(false);
  private readonly attempted = signal(false);

  /** The reporter's stores load once per opening. */
  protected readonly stores = loadResource<StoreListItem[], true>({
    params: () => (this.visible() ? true : undefined),
    loader: () => this.storesApi.listMyStores({ excludeInactive: true }),
    isEmpty: () => false,
  });
  protected readonly storeOptions = computed(() => reportableStores(this.stores.data() ?? []));

  /** Region options of the report: reload when the store changes (the store's region may have other reasons). */
  protected readonly options = loadResource<ReportOptions, { userId: string; storeId: string | null }>({
    params: () => (this.visible() ? { userId: this.userId(), storeId: this.storeId() } : undefined),
    loader: ({ userId, storeId }) => this.api.getReportOptions({ userId, storeId: storeId ?? undefined }),
    isEmpty: () => false,
  });
  protected readonly choices = computed(() => reasonChoices(this.options.data()));
  protected readonly reasonOptions = computed(() =>
    this.choices().map((value) => {
      const label = reasonLabel(value);
      return { value, label: this.i18n.t(label.key, label.params) };
    }),
  );

  protected readonly messageLength = computed(() => this.message().trim().length);
  protected readonly errors = computed(() =>
    reportDraftErrors({ reason: this.reason(), reasonText: this.reasonText(), message: this.message() }, this.choices()),
  );

  protected readonly create = runMutation(
    () =>
      this.api.createReport({
        userId: this.userId(),
        reportCreate: {
          reason: this.reason()!,
          reasonText: this.reason() === ReportReason.Other ? this.reasonText().trim() : undefined,
          message: this.message().trim(),
          storeId: this.storeId() ?? undefined,
          sendConfirmationMail: this.confirmationMail(),
        },
      }),
    {
      successToastKey: 'reports.toast.created',
      onSuccess: (report: Report) => {
        this.visible.set(false);
        this.created.emit(report);
      },
    },
  );

  constructor() {
    // a fresh form every time the dialog opens
    effect(() => {
      if (this.visible()) {
        untracked(() => {
          this.storeId.set(null);
          this.reason.set(null);
          this.reasonText.set('');
          this.message.set('');
          this.confirmationMail.set(false);
          this.attempted.set(false);
        });
      }
    });
    // a reason the (new) region does not offer is dropped
    effect(() => {
      const choices = this.choices();
      const reason = untracked(() => this.reason());
      if (reason && this.options.data() && !choices.includes(reason)) {
        untracked(() => this.reason.set(null));
      }
    });
  }

  protected shown(field: 'reason' | 'reasonText' | 'message'): boolean {
    return this.attempted() && !!this.errors()[field];
  }

  protected submit(): void {
    this.attempted.set(true);
    if (!this.options.data()?.buttonEnabled || Object.keys(this.errors()).length) return;
    void this.create.run();
  }
}
