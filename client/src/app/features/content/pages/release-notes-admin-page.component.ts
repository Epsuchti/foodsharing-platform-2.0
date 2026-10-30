import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';

import { ContentService, ReleaseNote, ReleaseNoteCreate, ReleaseNoteUpdate } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  LanguageTabsEditorComponent,
  TranslatedText,
} from '../../../shared/language-tabs-editor/language-tabs-editor.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { releaseNoteRequest, zurichDay } from '../content.util';

/** Saved translations of a note as one language → text map for the language tabs. */
function mapTexts(note: ReleaseNote, field: 'title' | 'body'): TranslatedText {
  return Object.fromEntries(Object.entries(note.translations ?? {}).map(([language, text]) => [language, text[field]]));
}

/**
 * /admin/release-notes (C4, ORGA): the release notes (German texts) with create, edit and delete. German title and
 * text are required; other languages are optional translations. An edit opens with the saved translations; a
 * language emptied completely is removed (`removeTranslations`).
 */
@Component({
  selector: 'fs-release-notes-admin-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    InputText,
    LanguageTabsEditorComponent,
    PageHeaderComponent,
    ReactiveFormsModule,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.releaseAdmin.title' | t"
      [subtitle]="'content.releaseAdmin.subtitle' | t"
      icon="pi pi-gift"
    >
      <p-button
        [label]="'content.releaseAdmin.new' | t"
        icon="pi pi-plus"
        (onClick)="open(null)"
        data-testid="content.releaseAdmin.new"
      />
    </fs-page-header>

    <fs-data-state
      [state]="notes"
      skeleton="list"
      emptyIcon="pi pi-gift"
      [emptyTitle]="'content.releaseAdmin.empty' | t"
      [askWho]="'content.releaseAdmin.askWho' | t"
      data-testid="content.releaseAdmin.state"
    >
      <ng-template [fsData]="notes" let-items>
        <ul class="fs-card list">
          @for (note of items; track note.id) {
            <li
              class="row"
              [attr.data-version]="note.version"
              data-testid="content.releaseAdmin.note"
            >
              <span class="row__version">{{ note.version }}</span>
              <span class="row__main">
                <span class="row__title">{{ note.title }}</span>
                <span class="row__date">{{ note.releasedAt | fsDate: 'date' }}</span>
              </span>
              <span class="row__actions">
                <p-button
                  icon="pi pi-pencil"
                  [text]="true"
                  [rounded]="true"
                  severity="secondary"
                  [ariaLabel]="'content.releaseAdmin.edit' | t: { version: note.version }"
                  (onClick)="open(note)"
                  data-testid="content.releaseAdmin.edit"
                />
                <p-button
                  icon="pi pi-trash"
                  [text]="true"
                  [rounded]="true"
                  severity="danger"
                  [ariaLabel]="'content.releaseAdmin.delete' | t: { version: note.version }"
                  (onClick)="confirmDelete(note)"
                  data-testid="content.releaseAdmin.delete"
                />
              </span>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>

    <p-dialog
      [visible]="dialogOpen()"
      (visibleChange)="dialogOpen.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="
        (editing() ? 'content.releaseAdmin.editTitle' : 'content.releaseAdmin.createTitle') | t
      "
      [style]="{ width: 'min(46rem, 94vw)' }"
      data-testid="content.releaseAdmin.dialog"
    >
      <form class="form" [formGroup]="form" (ngSubmit)="save()">
        <div class="grid">
          <div class="field">
            <label for="rn-version">{{ 'content.releaseAdmin.version' | t }}</label>
            <input
              pInputText
              id="rn-version"
              formControlName="version"
              maxlength="40"
              placeholder="5.0.0"
              data-testid="content.releaseAdmin.version"
            />
          </div>
          <div class="field">
            <label for="rn-date">{{ 'content.releaseAdmin.releasedAt' | t }}</label>
            <input
              pInputText
              id="rn-date"
              type="date"
              formControlName="releasedAt"
              data-testid="content.releaseAdmin.releasedAt"
            />
          </div>
        </div>
        <fs-language-tabs-editor
          formControlName="titles"
          [label]="'content.releaseAdmin.noteTitle' | t"
          testId="content.releaseAdmin.titles"
        />
        <fs-language-tabs-editor
          formControlName="bodies"
          [label]="'content.releaseAdmin.body' | t"
          [multiline]="true"
          [markdown]="true"
          [rows]="6"
          testId="content.releaseAdmin.bodies"
        />
        @if (editing()) {
          <p class="hint" data-testid="content.releaseAdmin.translationsHint">
            <i class="pi pi-info-circle" aria-hidden="true"></i
            >{{ 'content.releaseAdmin.translationsHint' | t }}
          </p>
        }
        @if (incomplete().length) {
          <p class="error" role="alert" data-testid="content.releaseAdmin.incomplete">
            {{ 'content.releaseAdmin.incomplete' | t: { languages: incompleteNames() } }}
          </p>
        }
        <div class="actions">
          <p-button
            [label]="'common.cancel' | t"
            severity="secondary"
            [text]="true"
            (onClick)="dialogOpen.set(false)"
          />
          <p-button
            type="submit"
            [label]="'common.save' | t"
            icon="pi pi-check"
            [disabled]="form.invalid || incomplete().length > 0"
            [loading]="saving()"
            data-testid="content.releaseAdmin.save"
          />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    :host {
      display: block;
    }
    .list {
      display: grid;
      margin: 0;
      padding: 0.35rem;
      list-style: none;
    }
    .row {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.5rem 1rem;
      padding: 0.6rem 0.8rem;
    }
    .row + .row {
      border-top: 1px solid var(--fs-border);
    }
    .row__version {
      padding: 0.1rem 0.55rem;
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 0.85rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .row__main {
      display: grid;
      min-width: 0;
    }
    .row__title {
      overflow: hidden;
      font-weight: 650;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .row__date {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .row__actions {
      display: flex;
      gap: 0.25rem;
    }
    .form {
      display: grid;
      gap: 1rem;
    }
    .grid {
      display: grid;
      gap: 1rem;
      @media (min-width: 36rem) {
        grid-template-columns: 1fr 1fr;
      }
    }
    .field {
      display: grid;
      gap: 0.35rem;
    }
    .field label {
      font-size: 0.85rem;
      font-weight: 650;
    }
    .field input {
      width: 100%;
    }
    .hint,
    .error {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      margin: 0;
      font-size: 0.85rem;
    }
    .hint {
      color: var(--fs-text-muted);
    }
    .error {
      color: var(--fs-danger);
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
    }
  `,
})
export class ReleaseNotesAdminPageComponent {
  private readonly api = inject(ContentService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly notes = loadResource<ReleaseNote[]>({
    loader: () => this.api.listReleaseNotesForEdit(),
  });

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<ReleaseNote | null>(null);

  protected readonly form = new FormGroup({
    version: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(40)],
    }),
    releasedAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    titles: new FormControl<TranslatedText>({}, { nonNullable: true }),
    bodies: new FormControl<TranslatedText>({}, { nonNullable: true }),
  });

  private readonly formValue = signal(this.form.getRawValue());
  protected readonly incomplete = computed(() => releaseNoteRequest(this.formValue()).incomplete);
  protected readonly incompleteNames = computed(() => this.incomplete().join(', ').toUpperCase());

  private readonly create = runMutation(
    (body: ReleaseNoteCreate) => this.api.createReleaseNote({ releaseNoteCreate: body }),
    {
      form: this.form,
      successToastKey: 'content.releaseAdmin.saved',
      onSuccess: () => this.done(),
    },
  );
  private readonly update = runMutation(
    (id: string, body: ReleaseNoteUpdate) =>
      this.api.updateReleaseNote({ releaseNoteId: id, releaseNoteUpdate: body }),
    {
      form: this.form,
      successToastKey: 'content.releaseAdmin.saved',
      onSuccess: () => this.done(),
    },
  );
  private readonly remove = runMutation(
    (id: string) => this.api.deleteReleaseNote({ releaseNoteId: id }),
    { successToastKey: 'content.releaseAdmin.deleted', onSuccess: () => this.notes.reload() },
  );
  protected readonly saving = computed(() => this.create.busy() || this.update.busy());

  constructor() {
    this.form.valueChanges.subscribe(() => this.formValue.set(this.form.getRawValue()));
  }

  protected open(note: ReleaseNote | null): void {
    this.editing.set(note);
    this.form.reset({
      version: note?.version ?? '',
      releasedAt: note?.releasedAt ?? zurichDay(new Date()),
      titles: note ? { de: note.title, ...mapTexts(note, 'title') } : {},
      bodies: note ? { de: note.body, ...mapTexts(note, 'body') } : {},
    });
    this.dialogOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      return;
    }
    const note = this.editing();
    const { body, incomplete } = releaseNoteRequest(this.form.getRawValue(), Object.keys(note?.translations ?? {}));
    if (incomplete.length) {
      return;
    }
    void (note ? this.update.run(note.id, body) : this.create.run(body));
  }

  protected confirmDelete(note: ReleaseNote): void {
    this.confirm.confirm({
      header: this.i18n.t('content.releaseAdmin.deleteTitle'),
      message: this.i18n.t('content.releaseAdmin.deleteText', { version: note.version }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('content.releaseAdmin.deleteConfirm'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.remove.run(note.id),
    });
  }

  private done(): void {
    this.dialogOpen.set(false);
    this.notes.reload();
  }
}
