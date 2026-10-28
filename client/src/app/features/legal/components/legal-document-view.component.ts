import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { LegalDocument } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';

/**
 * One legal document (Ve1): version and publication date, the key points (summary) highlighted, the full text and a
 * note when the text is not available in the UI language. Used by /legal/:kind and the gallery.
 */
@Component({
  selector: 'fs-legal-document-view',
  imports: [DateTimePipe, MarkdownComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let doc = document();
    <article class="fs-card doc" [attr.data-kind]="doc.kind" data-testid="legal.document.view">
      <p class="doc__meta" data-testid="legal.document.version">
        <i class="pi pi-calendar" aria-hidden="true"></i>
        {{
          'legal.document.version'
            | t: { version: doc.version, date: (doc.publishedAt | fsDate: 'date') }
        }}
      </p>
      @if (doc.fallback) {
        <p class="doc__fallback" data-testid="legal.document.fallback">
          <i class="pi pi-language" aria-hidden="true"></i>
          {{ 'legal.document.fallback' | t }}
        </p>
      }
      @if (doc.summary) {
        <section class="doc__summary" data-testid="legal.document.summary">
          <h2>{{ 'legal.document.summary' | t }}</h2>
          <fs-markdown [markdown]="doc.summary" />
        </section>
      }
      <fs-markdown class="doc__body" [markdown]="doc.body" data-testid="legal.document.body" />
    </article>
  `,
  styles: `
    :host {
      display: block;
    }
    .doc {
      display: grid;
      gap: 1.25rem;
      padding: clamp(1.1rem, 0.6rem + 2vw, 2.25rem);
    }
    .doc__meta,
    .doc__fallback {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
    .doc__fallback {
      padding: 0.6rem 0.8rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted, var(--fs-primary-soft));
    }
    .doc__summary {
      padding: 1rem 1.15rem;
      border-left: 4px solid var(--fs-primary);
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
    }
    .doc__summary h2 {
      margin: 0 0 0.5rem;
      color: var(--fs-primary-strong);
      font-size: 0.8rem;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .doc__body {
      max-width: 44rem;
    }
  `,
})
export class LegalDocumentViewComponent {
  readonly document = input.required<LegalDocument>();
}
