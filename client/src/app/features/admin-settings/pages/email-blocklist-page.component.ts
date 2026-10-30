import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';

import { AdminSettingsService, EmailBlocklistEntry } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { SettingsTabsComponent } from '../components/settings-tabs.component';

const patternControl = () =>
  new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3), Validators.maxLength(320)] });
const reasonControl = () => new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] });

/**
 * `/admin/email-blocklist` (C12, ORGA): blocked email domains and addresses. Sign-ups and email changes to a
 * matching address are refused (EMAIL_DOMAIN_BLOCKED). The server normalises patterns (lower case, no leading
 * `@`, `*` allowed) and refuses duplicates (409 BLOCKLIST_DUPLICATE, shown at the field).
 */
@Component({
  selector: 'fs-email-blocklist-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, Dialog, InputText, PageHeaderComponent, ReactiveFormsModule, SettingsTabsComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminSettings.blocklist.title' | t" [subtitle]="'adminSettings.blocklist.subtitle' | t" icon="pi pi-ban" />
    <fs-settings-tabs />
    <form class="fs-card form" [formGroup]="addForm" (ngSubmit)="add.run()" data-testid="adminSettings.blocklist.addForm">
      <div class="field">
        <label for="blocklist-pattern">{{ 'adminSettings.blocklist.pattern' | t }}</label>
        <input pInputText id="blocklist-pattern" formControlName="pattern" placeholder="example.org" aria-describedby="blocklist-pattern-hint" data-testid="adminSettings.blocklist.pattern" />
        <span class="muted" id="blocklist-pattern-hint">{{ 'adminSettings.blocklist.patternHint' | t }}</span>
        @if (addError(); as error) {
          <p class="field-error" role="alert" data-testid="adminSettings.blocklist.error">{{ error }}</p>
        }
      </div>
      <div class="field">
        <label for="blocklist-reason">{{ 'adminSettings.blocklist.reason' | t }}</label>
        <input pInputText id="blocklist-reason" formControlName="reason" data-testid="adminSettings.blocklist.reason" />
      </div>
      <div class="row__actions">
        <p-button type="submit" [label]="'adminSettings.blocklist.add' | t" icon="pi pi-plus" [loading]="add.busy()" [disabled]="addForm.invalid" data-testid="adminSettings.blocklist.addButton" />
      </div>
    </form>

    <fs-data-state
      [state]="entries"
      skeleton="table"
      [skeletonRows]="5"
      emptyIcon="pi pi-check-circle"
      [emptyTitle]="'adminSettings.blocklist.empty' | t"
      testId="adminSettings.blocklist.state"
      data-testid="adminSettings.blocklist.state"
    >
      <ng-template [fsData]="entries" let-list>
        <div class="table-wrap fs-card">
          <table class="table" data-testid="adminSettings.blocklist.table">
            <thead>
              <tr>
                <th scope="col">{{ 'adminSettings.blocklist.pattern' | t }}</th>
                <th scope="col">{{ 'adminSettings.blocklist.reason' | t }}</th>
                <th scope="col">{{ 'adminSettings.blocklist.created' | t }}</th>
                <th scope="col"><span class="fs-visually-hidden">{{ 'adminSettings.actions' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (entry of list; track entry.id) {
                <tr data-testid="adminSettings.blocklist.row" [attr.data-id]="entry.id" [attr.data-pattern]="entry.pattern">
                  <td><code data-testid="adminSettings.blocklist.rowPattern">{{ entry.pattern }}</code></td>
                  <td data-testid="adminSettings.blocklist.rowReason">{{ entry.reason || '–' }}</td>
                  <td>
                    {{ entry.createdAt | fsDate: 'dateTime' }}
                    @if (entry.createdBy) {
                      <span class="muted">{{ entry.createdBy.displayName }}</span>
                    }
                  </td>
                  <td class="actions">
                    <p-button
                      icon="pi pi-pencil"
                      severity="secondary"
                      [text]="true"
                      [rounded]="true"
                      [ariaLabel]="'adminSettings.blocklist.editFor' | t: { pattern: entry.pattern }"
                      (onClick)="openEdit(entry)"
                      data-testid="adminSettings.blocklist.edit"
                    />
                    <p-button
                      icon="pi pi-trash"
                      severity="danger"
                      [text]="true"
                      [rounded]="true"
                      [ariaLabel]="'adminSettings.blocklist.removeFor' | t: { pattern: entry.pattern }"
                      (onClick)="confirmRemove(entry)"
                      data-testid="adminSettings.blocklist.remove"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </ng-template>
    </fs-data-state>

    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="!!editing()"
      (visibleChange)="!$event && editing.set(null)"
      [modal]="true"
      [draggable]="false"
      [header]="'adminSettings.blocklist.editTitle' | t"
      [style]="{ width: 'min(30rem, 94vw)' }"
      data-testid="adminSettings.blocklist.editDialog"
    >
      <form class="form" [formGroup]="editForm" (ngSubmit)="saveEdit.run()">
        <div class="field">
          <label for="blocklist-edit-pattern">{{ 'adminSettings.blocklist.pattern' | t }}</label>
          <input pInputText id="blocklist-edit-pattern" formControlName="pattern" data-testid="adminSettings.blocklist.editPattern" />
          @if (editError(); as error) {
            <p class="field-error" role="alert" data-testid="adminSettings.blocklist.editError">{{ error }}</p>
          }
        </div>
        <div class="field">
          <label for="blocklist-edit-reason">{{ 'adminSettings.blocklist.reason' | t }}</label>
          <input pInputText id="blocklist-edit-reason" formControlName="reason" data-testid="adminSettings.blocklist.editReason" />
        </div>
        <div class="row__actions">
          <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="editing.set(null)" />
          <p-button type="submit" [label]="'common.save' | t" icon="pi pi-check" [loading]="saveEdit.busy()" [disabled]="editForm.invalid" data-testid="adminSettings.blocklist.editSave" />
        </div>
      </form>
    </p-dialog>
  `,
  styleUrl: '../admin-settings.scss',
})
export class EmailBlocklistPageComponent {
  private readonly api = inject(AdminSettingsService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly addForm = new FormGroup({ pattern: patternControl(), reason: reasonControl() });
  protected readonly editForm = new FormGroup({ pattern: patternControl(), reason: reasonControl() });
  protected readonly editing = signal<EmailBlocklistEntry | null>(null);

  protected readonly entries = loadResource<EmailBlocklistEntry[]>({
    loader: () => this.api.listEmailBlocklist(),
  });

  protected readonly add = runMutation(
    () => {
      const { pattern, reason } = this.addForm.getRawValue();
      return this.api.addEmailBlocklistEntry({ emailBlocklistCreate: { pattern: pattern.trim(), reason: reason.trim() || undefined } });
    },
    {
      errorToast: false,
      successToastKey: 'adminSettings.blocklist.added',
      onSuccess: (entry) => {
        this.addForm.reset();
        this.entries.update((list) => [entry, ...(list ?? [])]);
      },
    },
  );
  protected readonly addError = computed(() => this.errorText(this.add.error()));

  protected readonly saveEdit = runMutation(
    () => {
      const { pattern, reason } = this.editForm.getRawValue();
      return this.api.updateEmailBlocklistEntry({
        entryId: this.editing()!.id,
        emailBlocklistUpdate: { pattern: pattern.trim(), reason: reason.trim() },
      });
    },
    {
      errorToast: false,
      successToastKey: 'adminSettings.blocklist.saved',
      onSuccess: (saved) => {
        this.editing.set(null);
        this.entries.update((list) => (list ?? []).map((item) => (item.id === saved.id ? saved : item)));
      },
    },
  );
  protected readonly editError = computed(() => this.errorText(this.saveEdit.error()));

  protected readonly remove = runMutation((entry: EmailBlocklistEntry) => this.api.deleteEmailBlocklistEntry({ entryId: entry.id }), {
    successToastKey: 'adminSettings.blocklist.removed',
    onSuccess: () => this.entries.reload(),
  });

  protected openEdit(entry: EmailBlocklistEntry): void {
    this.editForm.reset({ pattern: entry.pattern, reason: entry.reason ?? '' });
    this.editing.set(entry);
  }

  protected confirmRemove(entry: EmailBlocklistEntry): void {
    this.confirm.confirm({
      header: this.i18n.t('adminSettings.blocklist.removeTitle'),
      message: this.i18n.t('adminSettings.blocklist.removeText', { pattern: entry.pattern }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('adminSettings.blocklist.remove'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.remove.run(entry),
    });
  }

  private errorText(error: { code: string } | undefined): string | undefined {
    return error ? this.i18n.error(error.code) : undefined;
  }
}
