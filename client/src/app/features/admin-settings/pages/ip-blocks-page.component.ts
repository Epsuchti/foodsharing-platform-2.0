import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';

import { AdminSettingsService, IpBlockContext, IpBlockEntry } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { IP_BLOCK_DURATIONS } from '../admin-settings.util';
import { SettingsTabsComponent } from '../components/settings-tabs.component';

/** Select value of "permanent" (the select needs a non-null value). */
const PERMANENT = 0;

/**
 * `/admin/ip-blocks` (C12, ORGA): blocked addresses or CIDR ranges for login, registration or both, with an
 * optional duration. Login checks LOGIN+ALL, registration REGISTRATION+ALL, magic links any (A53).
 */
@Component({
  selector: 'fs-ip-blocks-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, InputText, PageHeaderComponent, ReactiveFormsModule, Select, SettingsTabsComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminSettings.ipBlocks.title' | t" [subtitle]="'adminSettings.ipBlocks.subtitle' | t" icon="pi pi-shield" />
    <fs-settings-tabs />
    <form class="fs-card form" [formGroup]="form" (ngSubmit)="add.run()" data-testid="adminSettings.ipBlocks.addForm">
      <div class="field">
        <label for="ip-block-ip">{{ 'adminSettings.ipBlocks.ip' | t }}</label>
        <input pInputText id="ip-block-ip" formControlName="ip" placeholder="203.0.113.7" aria-describedby="ip-block-ip-hint" data-testid="adminSettings.ipBlocks.ip" />
        <span class="muted" id="ip-block-ip-hint">{{ 'adminSettings.ipBlocks.ipHint' | t }}</span>
        @if (addError(); as error) {
          <p class="field-error" role="alert" data-testid="adminSettings.ipBlocks.error">{{ error }}</p>
        }
      </div>
      <div class="field">
        <label for="ip-block-context">{{ 'adminSettings.ipBlocks.context' | t }}</label>
        <p-select
          inputId="ip-block-context"
          formControlName="context"
          [options]="contexts()"
          optionLabel="label"
          optionValue="value"
          appendTo="body"
          [style]="{ width: '100%' }"
          data-testid="adminSettings.ipBlocks.contextSelect"
        >
          <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
        </p-select>
      </div>
      <div class="field">
        <label for="ip-block-duration">{{ 'adminSettings.ipBlocks.duration' | t }}</label>
        <p-select
          inputId="ip-block-duration"
          formControlName="duration"
          [options]="durations()"
          optionLabel="label"
          optionValue="value"
          appendTo="body"
          [style]="{ width: '100%' }"
          data-testid="adminSettings.ipBlocks.durationSelect"
        >
          <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
        </p-select>
      </div>
      <div class="row__actions">
        <p-button type="submit" [label]="'adminSettings.ipBlocks.add' | t" icon="pi pi-plus" [loading]="add.busy()" [disabled]="form.invalid" data-testid="adminSettings.ipBlocks.addButton" />
      </div>
    </form>

    <fs-data-state
      [state]="blocks"
      skeleton="table"
      [skeletonRows]="4"
      emptyIcon="pi pi-check-circle"
      [emptyTitle]="'adminSettings.ipBlocks.empty' | t"
      testId="adminSettings.ipBlocks.state"
      data-testid="adminSettings.ipBlocks.state"
    >
      <ng-template [fsData]="blocks" let-list>
        <div class="table-wrap fs-card">
          <table class="table" data-testid="adminSettings.ipBlocks.table">
            <thead>
              <tr>
                <th scope="col">{{ 'adminSettings.ipBlocks.ip' | t }}</th>
                <th scope="col">{{ 'adminSettings.ipBlocks.context' | t }}</th>
                <th scope="col">{{ 'adminSettings.ipBlocks.created' | t }}</th>
                <th scope="col">{{ 'adminSettings.ipBlocks.until' | t }}</th>
                <th scope="col"><span class="fs-visually-hidden">{{ 'adminSettings.actions' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (block of list; track block.id) {
                <tr data-testid="adminSettings.ipBlocks.row" [attr.data-id]="block.id" [attr.data-ip]="block.ip" [attr.data-context]="block.context">
                  <td><code>{{ block.ip }}</code></td>
                  <td><span class="chip">{{ 'adminSettings.ipBlocks.context.' + block.context | t }}</span></td>
                  <td>
                    {{ block.createdAt | fsDate: 'dateTime' }}
                    @if (block.createdBy) {
                      <span class="muted">{{ block.createdBy.displayName }}</span>
                    }
                  </td>
                  <td>{{ block.expiresAt ? (block.expiresAt | fsDate: 'dateTime') : ('adminSettings.ipBlocks.permanent' | t) }}</td>
                  <td class="actions">
                    <p-button
                      icon="pi pi-trash"
                      severity="danger"
                      [text]="true"
                      [rounded]="true"
                      [ariaLabel]="'adminSettings.ipBlocks.removeFor' | t: { ip: block.ip }"
                      (onClick)="confirmRemove(block)"
                      data-testid="adminSettings.ipBlocks.remove"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../admin-settings.scss',
})
export class IpBlocksPageComponent {
  private readonly api = inject(AdminSettingsService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly form = new FormGroup({
    ip: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3), Validators.maxLength(64)] }),
    context: new FormControl<IpBlockContext>(IpBlockContext.All, { nonNullable: true }),
    duration: new FormControl<number>(PERMANENT, { nonNullable: true }),
  });

  protected readonly contexts = computed(() => {
    this.i18n.language();
    return Object.values(IpBlockContext).map((value) => ({ value, label: this.i18n.t(`adminSettings.ipBlocks.context.${value}`) }));
  });

  protected readonly durations = computed(() => {
    this.i18n.language();
    return IP_BLOCK_DURATIONS.map((minutes) => ({
      value: minutes ?? PERMANENT,
      label: minutes ? this.i18n.t(`adminSettings.ipBlocks.duration.${minutes}`) : this.i18n.t('adminSettings.ipBlocks.permanent'),
    }));
  });

  protected readonly blocks = loadResource<IpBlockEntry[]>({
    loader: () => this.api.listIpBlocks(),
  });

  protected readonly add = runMutation(
    () => {
      const { ip, context, duration } = this.form.getRawValue();
      return this.api.addIpBlock({ ipBlockCreate: { ip: ip.trim(), context, durationMinutes: duration || undefined } });
    },
    {
      errorToast: false,
      successToastKey: 'adminSettings.ipBlocks.added',
      onSuccess: (block) => {
        this.form.reset();
        this.blocks.update((list) => [block, ...(list ?? [])]);
      },
    },
  );
  protected readonly addError = computed(() => {
    const error = this.add.error();
    if (!error) {
      return undefined;
    }
    // the server answers 400 VALIDATION_FAILED (field ip) for anything that is not an address or CIDR range
    return error.code === 'VALIDATION_FAILED' ? this.i18n.t('adminSettings.ipBlocks.invalid') : this.i18n.error(error.code);
  });

  protected readonly remove = runMutation((block: IpBlockEntry) => this.api.deleteIpBlock({ blockId: block.id }), {
    successToastKey: 'adminSettings.ipBlocks.removed',
    onSuccess: () => this.blocks.reload(),
  });

  protected confirmRemove(block: IpBlockEntry): void {
    this.confirm.confirm({
      header: this.i18n.t('adminSettings.ipBlocks.removeTitle'),
      message: this.i18n.t('adminSettings.ipBlocks.removeText', { ip: block.ip }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('adminSettings.ipBlocks.remove'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.remove.run(block),
    });
  }
}
