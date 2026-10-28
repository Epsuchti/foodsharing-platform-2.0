import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { FormsModule } from '@angular/forms';
import { concatMap, forkJoin, from, last, map, of, switchMap, tap, toArray } from 'rxjs';

import { AuthService, Language, LegalDocument, LegalService, PublicLegalService } from '../../../api';
import { safeReturnUrl } from '../../../core/auth/return-url';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/**
 * /consent (EA4): every pending legal document (new privacy policy version, legal agreement, …) with summary and
 * full text; accepting all of them refreshes the session and continues to `?returnUrl`. consentGuard sends users
 * here while `SessionInfo.pendingConsents` is not empty.
 */
@Component({
  selector: 'fs-consent-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Checkbox,
    DateTimePipe,
    FormsModule,
    MarkdownComponent,
    PageHeaderComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [eyebrow]="'legal.consent.eyebrow' | t"
      [title]="'legal.consent.title' | t"
      [subtitle]="'legal.consent.subtitle' | t"
      icon="pi pi-file-check"
    />

    <fs-data-state
      [state]="documents"
      skeleton="card"
      emptyIcon="pi pi-check-circle"
      [emptyTitle]="'legal.consent.nothingPending' | t"
      [emptyActionLabel]="'legal.consent.continue' | t"
      (emptyAction)="continue()"
      data-testid="legal.consent.documents"
    >
      <ng-template [fsData]="documents" let-docs>
        <ol class="docs">
          @for (doc of docs; track doc.kind) {
            <li class="fs-card doc" [attr.data-kind]="doc.kind" data-testid="legal.consent.document">
              <header class="doc__head">
                <h2 class="doc__title">{{ 'legal.kind.' + doc.kind | t }}</h2>
                <p class="doc__meta">
                  {{
                    'legal.consent.version'
                      | t: { version: doc.version, date: (doc.publishedAt | fsDate: 'date') }
                  }}
                </p>
              </header>
              @if (doc.summary) {
                <div class="doc__summary">
                  <h3>{{ 'legal.consent.summary' | t }}</h3>
                  <fs-markdown [markdown]="doc.summary" />
                </div>
              }
              <details class="doc__full">
                <summary [attr.data-testid]="'legal.consent.fullText.' + doc.kind">
                  {{ 'legal.consent.fullText' | t }}
                </summary>
                <fs-markdown [markdown]="doc.body" />
              </details>
              <div class="doc__accept">
                <p-checkbox
                  [binary]="true"
                  [inputId]="'consent-' + doc.kind"
                  [ngModel]="checked().has(doc.kind)"
                  (ngModelChange)="toggle(doc.kind, $event)"
                  [attr.data-testid]="'legal.consent.check.' + doc.kind"
                />
                <label [for]="'consent-' + doc.kind">{{
                  'legal.consent.accept' | t: { document: ('legal.kind.' + doc.kind | t) }
                }}</label>
              </div>
            </li>
          }
        </ol>
        <div class="actions">
          <p-button
            [label]="'legal.consent.submit' | t"
            icon="pi pi-check"
            [disabled]="!allChecked()"
            [loading]="accept.busy()"
            (onClick)="accept.run()"
            data-testid="legal.consent.submit"
          />
          @if (!allChecked()) {
            <p class="actions__hint">{{ 'legal.consent.checkAll' | t }}</p>
          }
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 50rem);
      margin: 0 auto;
    }
    .docs {
      display: grid;
      gap: 1.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .doc {
      display: grid;
      gap: 1rem;
      padding: clamp(1.1rem, 0.8rem + 1.5vw, 1.75rem);
    }
    .doc__title {
      margin: 0;
      font-size: 1.25rem;
    }
    .doc__meta {
      margin: 0.2rem 0 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .doc__summary {
      padding: 1rem 1.1rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
    }
    .doc__summary h3 {
      margin: 0 0 0.5rem;
      color: var(--fs-primary-strong);
      font-size: 0.8rem;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .doc__full summary {
      color: var(--fs-primary-strong);
      font-weight: 650;
      cursor: pointer;
    }
    .doc__full fs-markdown {
      max-height: 24rem;
      margin-top: 0.75rem;
      padding: 1rem;
      overflow: auto;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
    }
    .doc__accept {
      display: flex;
      align-items: flex-start;
      gap: 0.6rem;
      padding-top: 0.75rem;
      border-top: 1px solid var(--fs-border);
      font-weight: 600;
    }
    .doc__accept label {
      cursor: pointer;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1rem;
      margin-top: 1.5rem;
    }
    .actions__hint {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
  `,
})
export class ConsentPageComponent {
  private readonly legalApi = inject(LegalService);
  private readonly publicLegalApi = inject(PublicLegalService);
  private readonly authApi = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  /** `?returnUrl=` (component input binding). */
  readonly returnUrl = input<string | undefined>(undefined);

  protected readonly checked = signal<ReadonlySet<string>>(new Set());

  /** The pending documents in the UI language. */
  protected readonly documents = loadResource<LegalDocument[], string>({
    params: () => this.i18n.language(),
    loader: (lang) =>
      this.legalApi.getMyConsents().pipe(
        map((consents) => consents.filter((consent) => consent.pending)),
        switchMap((pending) =>
          pending.length === 0
            ? of([] as LegalDocument[])
            : forkJoin(
                pending.map((consent) =>
                  this.publicLegalApi.getLegalDocument({
                    kind: consent.kind,
                    lang: lang as Language,
                    version: consent.currentVersion,
                  }),
                ),
              ),
        ),
      ),
  });

  protected readonly allChecked = computed(() => {
    const docs = this.documents.data() ?? [];
    return docs.length > 0 && docs.every((doc) => this.checked().has(doc.kind));
  });

  /** Accepts every pending document (exact versions shown), then reloads the session and continues. */
  protected readonly accept = runMutation(
    () =>
      from(this.documents.data() ?? []).pipe(
        concatMap((doc) =>
          this.legalApi.acceptLegalDocument({
            legalConsentRequest: { kind: doc.kind, version: doc.version },
          }),
        ),
        toArray(),
        switchMap(() => this.authApi.getSession()),
        tap((info) => this.session.applySession(info)),
        last(),
      ),
    {
      successToastKey: 'legal.consent.accepted',
      onSuccess: () => this.continue(),
    },
  );

  protected toggle(kind: string, value: boolean): void {
    const next = new Set(this.checked());
    if (value) {
      next.add(kind);
    } else {
      next.delete(kind);
    }
    this.checked.set(next);
  }

  protected continue(): void {
    void this.router.navigateByUrl(safeReturnUrl(this.returnUrl()) ?? '/dashboard');
  }
}
