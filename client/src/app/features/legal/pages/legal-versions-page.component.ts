import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';
import { forkJoin, map } from 'rxjs';

import {
  Language,
  LegalDocument,
  LegalDocumentKind,
  LegalService,
  LegalVersionView,
  PublicLegalService,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { LANGUAGES } from '../../../core/i18n/languages';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  LanguageTabsEditorComponent,
  TranslatedText,
} from '../../../shared/language-tabs-editor/language-tabs-editor.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { LEGAL_ICONS, LEGAL_KINDS, legalSlug } from '../legal-kind';

/**
 * /admin/legal (Ve1, ORGA): published versions per legal document and a form to publish a new one in five languages.
 * "Everyone must consent again" sends every user who has to accept the document back to /consent.
 */
@Component({
  selector: 'fs-legal-versions-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Checkbox,
    DateTimePipe,
    FormsModule,
    InputText,
    LanguageTabsEditorComponent,
    PageHeaderComponent,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'legal.admin.title' | t"
      [subtitle]="'legal.admin.subtitle' | t"
      icon="pi pi-file-edit"
      backLink="/admin/content"
      [backLabel]="'legal.admin.backToContent' | t"
    />

    <div class="kinds" role="tablist" [attr.aria-label]="'legal.admin.documents' | t">
      @for (k of kinds; track k) {
        <button
          type="button"
          role="tab"
          class="kinds__tab"
          [class.kinds__tab--active]="k === kind()"
          [attr.aria-selected]="k === kind()"
          (click)="select(k)"
          [attr.data-testid]="'legal.admin.kind.' + k"
        >
          <i [class]="icons[k]" aria-hidden="true"></i>
          {{ 'legal.kind.' + k | t }}
        </button>
      }
    </div>

    <div class="layout">
      <section class="fs-card versions" [attr.aria-label]="'legal.admin.versions' | t">
        <header class="versions__head">
          <h2>{{ 'legal.admin.versions' | t }}</h2>
          <a
            class="versions__view"
            [routerLink]="['/legal', slug(kind())]"
            data-testid="legal.admin.viewCurrent"
          >
            {{ 'legal.admin.viewCurrent' | t }}
            <i class="pi pi-external-link" aria-hidden="true"></i>
          </a>
        </header>
        <fs-data-state
          [state]="versions"
          skeleton="list"
          [skeletonRows]="3"
          emptyIcon="pi pi-file"
          [emptyTitle]="'legal.admin.noVersions' | t"
          [askWho]="'legal.admin.askWho' | t"
          data-testid="legal.admin.versions"
        >
          <ng-template [fsData]="versions" let-list>
            <ol class="versions__list">
              @for (v of list; track v.version; let first = $first) {
                <li
                  class="version"
                  [attr.data-version]="v.version"
                  data-testid="legal.admin.version"
                >
                  <span class="version__name">{{ v.version }}</span>
                  <span class="version__date">{{ v.publishedAt | fsDate: 'date' }}</span>
                  <span class="version__chips">
                    @if (first) {
                      <span class="chip chip--current">{{ 'legal.admin.current' | t }}</span>
                    }
                    @if (v.requireReconsent) {
                      <span class="chip chip--reconsent" data-testid="legal.admin.reconsentChip">
                        <i class="pi pi-refresh" aria-hidden="true"></i
                        >{{ 'legal.admin.reconsent' | t }}
                      </span>
                    }
                  </span>
                </li>
              }
            </ol>
          </ng-template>
        </fs-data-state>
      </section>

      <form class="fs-card publish" (ngSubmit)="askPublish()" data-testid="legal.admin.form">
        <header class="publish__head">
          <h2>{{ 'legal.admin.newVersion' | t: { document: ('legal.kind.' + kind() | t) } }}</h2>
          <p-button
            type="button"
            [label]="'legal.admin.prefill' | t"
            icon="pi pi-copy"
            severity="secondary"
            [text]="true"
            size="small"
            [loading]="current.status() === 'loading'"
            (onClick)="prefillRequested.set(kind())"
            data-testid="legal.admin.prefill"
          />
        </header>
        @if (current.status() === 'error') {
          <p class="publish__error" role="alert">{{ 'legal.admin.prefillFailed' | t }}</p>
        }
        <div class="field">
          <label for="legal-version">{{ 'legal.admin.version' | t }}</label>
          <input
            pInputText
            id="legal-version"
            name="version"
            maxlength="32"
            [ngModel]="version()"
            (ngModelChange)="version.set($event)"
            [placeholder]="'legal.admin.versionPlaceholder' | t"
            data-testid="legal.admin.version.input"
          />
          <small>{{ 'legal.admin.versionHint' | t }}</small>
        </div>
        <fs-language-tabs-editor
          name="summaries"
          [ngModel]="summaries()"
          (ngModelChange)="summaries.set($event)"
          [label]="'legal.admin.summaries' | t"
          [multiline]="true"
          [markdown]="true"
          [rows]="4"
          [requiredLanguages]="[]"
          testId="legal.admin.summaries"
        />
        <fs-language-tabs-editor
          name="bodies"
          [ngModel]="bodies()"
          (ngModelChange)="bodies.set($event)"
          [label]="'legal.admin.bodies' | t"
          [multiline]="true"
          [markdown]="true"
          [rows]="12"
          testId="legal.admin.bodies"
        />
        <div class="reconsent">
          <p-checkbox
            [binary]="true"
            inputId="legal-reconsent"
            name="reconsent"
            [ngModel]="reconsent()"
            (ngModelChange)="reconsent.set($event)"
            data-testid="legal.admin.reconsent"
          />
          <label for="legal-reconsent">
            <strong>{{ 'legal.admin.reconsentLabel' | t }}</strong>
            <span>{{ 'legal.admin.reconsentHint' | t }}</span>
          </label>
        </div>
        <div class="publish__actions">
          <p-button
            type="submit"
            [label]="'legal.admin.publish' | t"
            icon="pi pi-send"
            [disabled]="!valid()"
            [loading]="publish.busy()"
            data-testid="legal.admin.publish"
          />
          @if (!valid()) {
            <span class="publish__hint">{{ 'legal.admin.publishHint' | t }}</span>
          }
        </div>
      </form>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .kinds {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 1.25rem;
    }
    .kinds__tab {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.5rem 0.95rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
    }
    .kinds__tab--active {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 1.25rem;
      align-items: start;
    }
    @media (min-width: 64rem) {
      .layout {
        grid-template-columns: minmax(16rem, 22rem) minmax(0, 1fr);
      }
    }
    .versions,
    .publish {
      display: grid;
      gap: 1rem;
      padding: clamp(1rem, 0.7rem + 1.2vw, 1.5rem);
    }
    .versions__head,
    .publish__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    h2 {
      margin: 0;
      font-size: 1.1rem;
    }
    .versions__view {
      color: var(--fs-primary-strong);
      font-size: 0.85rem;
      font-weight: 600;
    }
    .versions__list {
      display: grid;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .version {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.15rem 0.75rem;
      padding: 0.7rem 0;
      border-top: 1px solid var(--fs-border);
    }
    .version:first-child {
      border-top: 0;
    }
    .version__name {
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .version__date {
      color: var(--fs-text-muted);
      font-size: 0.88rem;
      text-align: right;
    }
    .version__chips {
      display: flex;
      flex-wrap: wrap;
      grid-column: 1 / -1;
      gap: 0.35rem;
    }
    .version__chips:empty {
      display: none;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.1rem 0.55rem;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 650;
    }
    .chip--current {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .chip--reconsent {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .chip .pi {
      font-size: 0.7rem;
    }
    .field {
      display: grid;
      gap: 0.35rem;
      max-width: 20rem;
    }
    .field label {
      font-weight: 600;
    }
    .field small,
    .reconsent span,
    .publish__hint {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .reconsent {
      display: flex;
      align-items: flex-start;
      gap: 0.65rem;
      padding: 0.85rem 1rem;
      border: 1px solid var(--fs-warning-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
    }
    .reconsent label {
      display: grid;
      gap: 0.15rem;
      cursor: pointer;
    }
    .publish__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem;
    }
    .publish__error {
      margin: 0;
      color: var(--fs-danger);
      font-size: 0.88rem;
    }
  `,
})
export class LegalVersionsPageComponent {
  private readonly api = inject(LegalService);
  private readonly publicApi = inject(PublicLegalService);
  private readonly i18n = inject(I18nService);
  private readonly confirm = inject(ConfirmationService);

  protected readonly kinds = LEGAL_KINDS;
  protected readonly icons = LEGAL_ICONS;
  protected readonly kind = signal<LegalDocumentKind>(LegalDocumentKind.PrivacyPolicy);

  protected readonly version = signal('');
  protected readonly summaries = signal<TranslatedText>({});
  protected readonly bodies = signal<TranslatedText>({});
  protected readonly reconsent = signal(false);
  protected readonly valid = computed(() => !!this.version().trim() && !!this.bodies().de?.trim());

  protected readonly versions = loadResource<LegalVersionView[], LegalDocumentKind>({
    params: () => this.kind(),
    loader: (kind) => this.api.listLegalVersions({ kind }),
  });

  /** "Start from the current text": the current version in every language (fallbacks skipped). */
  protected readonly prefillRequested = signal<LegalDocumentKind | undefined>(undefined);
  protected readonly current = loadResource<LegalDocument[], LegalDocumentKind | undefined>({
    params: () => this.prefillRequested(),
    loader: (kind) =>
      forkJoin(
        LANGUAGES.map((lang) =>
          this.publicApi.getLegalDocument({ kind: kind!, lang: lang as Language }),
        ),
      ).pipe(map((docs) => docs.filter((doc) => !doc.fallback))),
  });

  constructor() {
    effect(() => {
      const docs = this.current.data();
      if (!docs || this.current.status() !== 'success') {
        return;
      }
      const summaries: TranslatedText = {};
      const bodies: TranslatedText = {};
      for (const doc of docs) {
        summaries[doc.language] = doc.summary ?? '';
        bodies[doc.language] = doc.body;
      }
      this.summaries.set(summaries);
      this.bodies.set(bodies);
      this.prefillRequested.set(undefined);
    });
  }

  protected slug(kind: LegalDocumentKind): string {
    return legalSlug(kind);
  }

  protected select(kind: LegalDocumentKind): void {
    if (kind !== this.kind()) {
      this.kind.set(kind);
      this.reset();
    }
  }

  protected readonly publish = runMutation(
    () =>
      this.api.publishLegalVersion({
        kind: this.kind(),
        legalVersionCreate: {
          version: this.version().trim(),
          summaries: trimmed(this.summaries()),
          bodies: trimmed(this.bodies()),
          requireReconsent: this.reconsent(),
        },
      }),
    {
      successToastKey: 'legal.admin.published',
      onSuccess: () => {
        this.reset();
        this.versions.reload();
      },
    },
  );

  protected askPublish(): void {
    if (!this.valid()) {
      return;
    }
    const document = this.i18n.t('legal.kind.' + this.kind());
    this.confirm.confirm({
      header: this.i18n.t('legal.admin.confirmTitle'),
      message: this.i18n.t(
        this.reconsent() ? 'legal.admin.confirmReconsent' : 'legal.admin.confirmText',
        {
          document,
          version: this.version().trim(),
        },
      ),
      acceptLabel: this.i18n.t('legal.admin.publish'),
      rejectLabel: this.i18n.t('common.cancel'),
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.publish.run(),
    });
  }

  private reset(): void {
    this.version.set('');
    this.summaries.set({});
    this.bodies.set({});
    this.reconsent.set(false);
  }
}

function trimmed(texts: TranslatedText): Record<string, string> {
  return Object.fromEntries(
    Object.entries(texts)
      .map(([lang, text]) => [lang, (text ?? '').trim()])
      .filter(([, text]) => text),
  );
}
